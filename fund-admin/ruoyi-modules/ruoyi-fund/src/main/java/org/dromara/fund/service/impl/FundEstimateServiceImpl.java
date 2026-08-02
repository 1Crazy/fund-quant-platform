package org.dromara.fund.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.redis.utils.RedisUtils;
import org.dromara.fund.client.FundEstimateProviderClient;
import org.dromara.fund.config.FundEstimateProperties;
import org.dromara.fund.constant.FundCacheConstants;
import org.dromara.fund.domain.FundEstimate;
import org.dromara.fund.domain.FundInfo;
import org.dromara.fund.domain.dto.EstimateHoldingContributionResponse;
import org.dromara.fund.domain.dto.EstimateProviderResponse;
import org.dromara.fund.domain.vo.FundEstimateContributionVo;
import org.dromara.fund.domain.vo.FundEstimateVo;
import org.dromara.fund.mapper.FundEstimateMapper;
import org.dromara.fund.mapper.FundInfoMapper;
import org.dromara.fund.service.IFundDataSyncService;
import org.dromara.fund.service.IFundEstimateService;
import org.redisson.api.RLock;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Cache Aside 基金实时估值服务。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FundEstimateServiceImpl implements IFundEstimateService {

    private static final long LOCK_WAIT_MILLIS = 800L;
    private static final long LOCK_LEASE_MILLIS = 5_000L;

    private final FundEstimateMapper estimateMapper;
    private final FundInfoMapper fundInfoMapper;
    private final IFundDataSyncService fundDataSyncService;
    private final FundEstimateProviderClient providerClient;
    private final FundEstimateProperties properties;

    @Override
    public FundEstimateVo queryEstimate(String fundCode) {
        // 估值快照依赖 fund_info 外键，直接访问估值接口时也必须先完成基金主数据读穿透。
        if (fundCode != null && fundCode.matches("^\\d{6}$")) {
            fundDataSyncService.ensureAvailable(fundCode, 1);
        }
        String cacheKey = FundCacheConstants.ESTIMATE_KEY_PREFIX + fundCode;
        FundEstimateVo cached = RedisUtils.getCacheObject(cacheKey);
        if (cached != null) {
            return cached;
        }

        RLock lock = RedisUtils.getClient().getLock(FundCacheConstants.ESTIMATE_LOCK_PREFIX + fundCode);
        boolean locked = false;
        try {
            locked = lock.tryLock(LOCK_WAIT_MILLIS, LOCK_LEASE_MILLIS, TimeUnit.MILLISECONDS);
            if (!locked) {
                return staleFallback(fundCode, "估值刷新繁忙");
            }
            cached = RedisUtils.getCacheObject(cacheKey);
            if (cached != null) {
                return cached;
            }

            FundEstimateVo fresh = fromProvider(providerClient.fetch(fundCode));
            validate(fundCode, fresh);
            RedisUtils.setCacheObject(cacheKey, fresh, properties.getCacheTtl());
            // 详情缓存内嵌盘中估值；不清除会使用户刷新估值后重新打开页面仍读到旧空值或旧快照。
            RedisUtils.deleteKeys(FundCacheConstants.INFO_KEY_PREFIX + fundCode + ":detail:*");
            try {
                persistSnapshot(fresh);
            } catch (RuntimeException e) {
                log.warn("基金 {} 估值快照落库失败，本次仍返回实时估值: {}", fundCode, e.getMessage());
            }
            return fresh;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return staleFallback(fundCode, "估值刷新被中断");
        } catch (ServiceException e) {
            log.warn("基金 {} 实时估值回源失败: {}", fundCode, e.getMessage());
            return staleFallback(fundCode, e.getMessage());
        } finally {
            if (locked && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    @Override
    public FundEstimateVo queryCachedOrSnapshot(String fundCode) {
        FundEstimateVo cached = RedisUtils.getCacheObject(FundCacheConstants.ESTIMATE_KEY_PREFIX + fundCode);
        if (cached != null) {
            return cached;
        }
        return fromEntity(estimateMapper.selectLatest(fundCode), true);
    }

    @Override
    public int refreshActiveFunds() {
        RLock scheduleLock = RedisUtils.getClient().getLock(FundCacheConstants.ESTIMATE_SCHEDULE_LOCK);
        boolean locked = false;
        try {
            locked = scheduleLock.tryLock(0, 4, TimeUnit.MINUTES);
            if (!locked) {
                log.info("基金实时估值刷新任务已在其他节点执行，本节点跳过");
                return 0;
            }
            return doRefreshActiveFunds();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("基金实时估值刷新任务获取分布式锁时被中断");
            return 0;
        } finally {
            if (locked && scheduleLock.isHeldByCurrentThread()) {
                scheduleLock.unlock();
            }
        }
    }

    private int doRefreshActiveFunds() {
        List<Object> rawFundCodes = fundInfoMapper.selectObjs(
            com.baomidou.mybatisplus.core.toolkit.Wrappers.<FundInfo>lambdaQuery()
                .select(FundInfo::getFundCode)
                .eq(FundInfo::getStatus, "0")
        );
        List<String> fundCodes = rawFundCodes.stream()
            .map(String::valueOf)
            .toList();
        int successCount = 0;
        for (String fundCode : fundCodes) {
            try {
                // 先删除热点值，确保调度触发真实回源而不是重复读取 45 秒缓存。
                RedisUtils.deleteObject(FundCacheConstants.ESTIMATE_KEY_PREFIX + fundCode);
                queryEstimate(fundCode);
                successCount++;
            } catch (RuntimeException e) {
                log.warn("定时刷新基金 {} 估值失败: {}", fundCode, e.getMessage());
            }
        }
        return successCount;
    }

    private FundEstimateVo staleFallback(String fundCode, String reason) {
        FundEstimateVo fallback = queryCachedOrSnapshot(fundCode);
        if (fallback == null) {
            throw new ServiceException("基金 {} 暂无可用估值：{}", fundCode, reason);
        }
        fallback.setStale(true);
        fallback.setSourceStatus("STALE");
        return fallback;
    }

    private FundEstimateVo fromProvider(EstimateProviderResponse response) {
        FundEstimateVo vo = new FundEstimateVo();
        vo.setFundCode(response.getFundCode());
        vo.setEstimateNav(response.getEstimateNav());
        vo.setEstimateGrowthRate(response.getEstimateGrowthRate());
        vo.setPreviousNav(response.getPreviousNav());
        vo.setPreviousNavDate(response.getPreviousNavDate());
        vo.setEstimateTime(response.getEstimateTime() == null ? null : response.getEstimateTime().toLocalDateTime());
        vo.setSource(response.getSource());
        vo.setHoldingCoverageRate(response.getHoldingCoverageRate());
        vo.setReportPeriod(response.getReportPeriod());
        List<EstimateHoldingContributionResponse> contributions = response.getContributions() == null
            ? List.of() : response.getContributions();
        vo.setContributions(contributions.stream()
            .map(this::toContribution)
            .toList());
        vo.setSourceStatus("NORMAL");
        return vo;
    }

    private FundEstimateContributionVo toContribution(EstimateHoldingContributionResponse response) {
        FundEstimateContributionVo vo = new FundEstimateContributionVo();
        vo.setStockCode(response.getStockCode());
        vo.setStockName(response.getStockName());
        vo.setWeight(response.getWeight());
        vo.setChangePercent(response.getChangePercent());
        vo.setContribution(response.getContribution());
        vo.setQuoteTime(response.getQuoteTime() == null ? null : response.getQuoteTime().toLocalDateTime());
        return vo;
    }

    private FundEstimateVo fromEntity(FundEstimate entity, boolean fallback) {
        if (entity == null) {
            return null;
        }
        FundEstimateVo vo = new FundEstimateVo();
        vo.setFundCode(entity.getFundCode());
        vo.setEstimateNav(entity.getEstimateNav());
        vo.setEstimateGrowthRate(entity.getEstimateGrowthRate());
        vo.setPreviousNav(entity.getPreviousNav());
        vo.setPreviousNavDate(entity.getPreviousNavDate());
        vo.setEstimateTime(entity.getEstimateTime() == null ? null : entity.getEstimateTime().toLocalDateTime());
        vo.setSource(entity.getSource());
        vo.setStale(fallback || isExpired(entity.getEstimateTime() == null
            ? null : entity.getEstimateTime().toLocalDateTime()) || !"NORMAL".equals(entity.getSourceStatus()));
        vo.setSourceStatus(vo.isStale() ? "STALE" : entity.getSourceStatus());
        return vo;
    }

    private boolean isExpired(LocalDateTime estimateTime) {
        return estimateTime == null || estimateTime.plus(properties.getStaleAfter()).isBefore(LocalDateTime.now());
    }

    private void validate(String requestedCode, FundEstimateVo estimate) {
        if (!requestedCode.equals(estimate.getFundCode())) {
            throw new ServiceException("估值基金代码不匹配");
        }
        if (estimate.getEstimateNav() == null || estimate.getEstimateNav().signum() <= 0) {
            throw new ServiceException("估值净值必须大于 0");
        }
        if (estimate.getEstimateTime() == null
            || estimate.getEstimateTime().isAfter(LocalDateTime.now().plusMinutes(1))) {
            throw new ServiceException("估值时间无效");
        }
    }

    private void persistSnapshot(FundEstimateVo estimate) {
        FundEstimate latest = estimateMapper.selectLatest(estimate.getFundCode());
        if (latest != null && latest.getEstimateTime() != null
            && Math.abs(ChronoUnit.MINUTES.between(
                latest.getEstimateTime().toLocalDateTime(), estimate.getEstimateTime())) < 5) {
            return;
        }
        FundEstimate entity = new FundEstimate();
        entity.setFundCode(estimate.getFundCode());
        entity.setEstimateNav(estimate.getEstimateNav());
        entity.setEstimateGrowthRate(estimate.getEstimateGrowthRate());
        entity.setPreviousNav(estimate.getPreviousNav());
        entity.setPreviousNavDate(estimate.getPreviousNavDate());
        entity.setEstimateTime(estimate.getEstimateTime()
            .atZone(ZoneId.of(properties.getZoneId()))
            .toOffsetDateTime());
        entity.setSource(estimate.getSource() == null || estimate.getSource().isBlank()
            ? "UPSTREAM" : estimate.getSource());
        entity.setSourceStatus("NORMAL");
        estimateMapper.insert(entity);
    }
}
