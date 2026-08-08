package org.dromara.fund.service.impl;

import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.redis.utils.RedisUtils;
import org.dromara.fund.client.FundEstimateProviderClient;
import org.dromara.fund.config.FundEstimateRuntimeSettings;
import org.dromara.fund.constant.FundCacheConstants;
import org.dromara.fund.domain.FundEstimate;
import org.dromara.fund.domain.dto.EstimateHoldingContributionResponse;
import org.dromara.fund.domain.dto.EstimateProviderResponse;
import org.dromara.fund.domain.dto.QuantConfigReleaseReference;
import org.dromara.fund.domain.dto.QuantConfigTaskContext;
import org.dromara.fund.domain.enums.FundEstimateSourceStatusEnum;
import org.dromara.fund.domain.vo.FundEstimateContributionVo;
import org.dromara.fund.domain.vo.FundEstimateScheduleStatusVo;
import org.dromara.fund.domain.vo.FundEstimateVo;
import org.dromara.fund.mapper.FundEstimateMapper;
import org.dromara.fund.mapper.FundInfoMapper;
import org.dromara.fund.service.FundEstimateMetrics;
import org.dromara.fund.service.FundEstimateScheduleTracker;
import org.dromara.fund.service.IFundEstimateService;
import org.dromara.fund.service.QuantConfigTaskContextResolver;
import org.redisson.api.RLock;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * Cache Aside 基金实时估值服务。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FundEstimateServiceImpl implements IFundEstimateService {

    private final FundEstimateMapper estimateMapper;
    private final FundInfoMapper fundInfoMapper;
    private final FundEstimateProviderClient providerClient;
    private final FundEstimateRuntimeSettings runtimeSettings;
    private final QuantConfigTaskContextResolver quantConfigTaskContextResolver;
    private final FundEstimateScheduleTracker scheduleTracker;
    private final FundEstimateMetrics estimateMetrics;

    @Override
    public FundEstimateVo queryEstimate(String fundCode) {
        QuantConfigTaskContext configContext = quantConfigTaskContextResolver.pinActiveRelease();
        return queryEstimate(fundCode, configContext);
    }

    @Override
    public FundEstimateVo refreshEstimate(String fundCode) {
        QuantConfigTaskContext configContext = quantConfigTaskContextResolver.pinActiveRelease();
        return refreshEstimate(fundCode, configContext);
    }

    @Override
    public FundEstimateVo recalculateEstimate(String fundCode, QuantConfigTaskContext configContext) {
        validateRecalculationContext(configContext);
        return refreshEstimate(fundCode, configContext);
    }

    private FundEstimateVo refreshEstimate(String fundCode, QuantConfigTaskContext configContext) {
        RedisUtils.deleteObject(estimateCacheKey(fundCode, configContext));
        return queryEstimate(fundCode, configContext);
    }

    private FundEstimateVo queryEstimate(String fundCode, QuantConfigTaskContext configContext) {
        if (!fundInfoMapper.hasReadyEstimateInputs(fundCode)) {
            FundEstimateVo unavailable = unavailableForMissingInputs(fundCode, configContext);
            estimateMetrics.recordResult(unavailable);
            return unavailable;
        }
        String cacheKey = estimateCacheKey(fundCode, configContext);
        FundEstimateVo cached = RedisUtils.getCacheObject(cacheKey);
        if (cached != null) {
            estimateMetrics.recordCache(true);
            return cached;
        }
        estimateMetrics.recordCache(false);

        RLock lock = RedisUtils.getClient().getLock(estimateLockKey(fundCode));
        boolean locked = false;
        try {
            locked = lock.tryLock(
                runtimeSettings.getLockWaitMillis(),
                runtimeSettings.getLockLeaseMillis(),
                TimeUnit.MILLISECONDS
            );
            if (!locked) {
                return staleFallback(fundCode, configContext, "估值刷新繁忙");
            }
            cached = RedisUtils.getCacheObject(cacheKey);
            if (cached != null) {
                estimateMetrics.recordCache(true);
                return cached;
            }

            Timer.Sample providerTimer = estimateMetrics.startProviderRequest();
            FundEstimateVo fresh;
            try {
                fresh = fromProvider(providerClient.fetch(fundCode, configContext));
                estimateMetrics.recordProviderRequest(providerTimer, "success");
            } catch (RuntimeException error) {
                estimateMetrics.recordProviderRequest(providerTimer, "failure");
                throw error;
            }
            validate(fundCode, fresh, configContext);
            estimateMetrics.recordResult(fresh);
            if (!FundEstimateSourceStatusEnum.NORMAL.getCode().equals(fresh.getSourceStatus())) {
                return fresh;
            }
            RedisUtils.setCacheObject(cacheKey, fresh, runtimeSettings.getCacheTtl());
            // 详情缓存内嵌盘中估值；不清除会使用户刷新估值后重新打开页面仍读到旧空值或旧快照。
            RedisUtils.deleteKeys(FundCacheConstants.INFO_KEY_PREFIX + fundCode + ":detail:*");
            try {
                estimateMetrics.recordSnapshot(persistSnapshot(fresh, configContext));
            } catch (RuntimeException e) {
                log.warn("基金 {} 估值快照落库失败，本次仍返回实时估值: {}", fundCode, e.getMessage());
            }
            return fresh;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return staleFallback(fundCode, configContext, "估值刷新被中断");
        } catch (ServiceException e) {
            log.warn("基金 {} 实时估值回源失败: {}", fundCode, e.getMessage());
            if (isQuantConfigFailure(e)) {
                throw e;
            }
            return staleFallback(fundCode, configContext, e.getMessage());
        } finally {
            if (locked && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    @Override
    public FundEstimateVo queryCachedOrSnapshot(String fundCode, QuantConfigTaskContext configContext) {
        FundEstimateVo cached = RedisUtils.getCacheObject(estimateCacheKey(fundCode, configContext));
        if (cached != null) {
            return cached;
        }
        return fromEntity(estimateMapper.selectLatestForRelease(
            fundCode,
            configContext.getConfigReleaseVersion(),
            configContext.getConfigReleaseChecksum()
        ), true);
    }

    @Override
    public int refreshActiveFunds() {
        Timer.Sample scheduleTimer = estimateMetrics.startScheduleRun();
        RLock scheduleLock = null;
        boolean locked = false;
        try {
            QuantConfigTaskContext configContext = quantConfigTaskContextResolver.pinActiveRelease();
            scheduleLock = RedisUtils.getClient().getLock(FundCacheConstants.ESTIMATE_SCHEDULE_LOCK);
            locked = scheduleLock.tryLock(0, runtimeSettings.getScheduleLockLease().toMillis(), TimeUnit.MILLISECONDS);
            if (!locked) {
                log.info("基金实时估值刷新任务已在其他节点执行，本节点跳过");
                scheduleTracker.markLockContention();
                return 0;
            }
            return doRefreshActiveFunds(configContext);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("基金实时估值刷新任务获取分布式锁时被中断");
            return 0;
        } finally {
            if (locked && scheduleLock != null && scheduleLock.isHeldByCurrentThread()) {
                scheduleLock.unlock();
            }
            estimateMetrics.recordScheduleRun(scheduleTimer);
        }
    }

    private int doRefreshActiveFunds(QuantConfigTaskContext configContext) {
        List<String> configuredCodes = runtimeSettings.getHotFundCodes();
        if (configuredCodes.isEmpty()) {
            log.info("基金实时估值刷新跳过：未配置热点基金范围");
            scheduleTracker.start(configContext, 0);
            scheduleTracker.complete();
            return 0;
        }
        List<String> fundCodes = fundInfoMapper.selectReadyEstimateFundCodes(
            configuredCodes,
            runtimeSettings.getScheduleBatchSize()
        );
        scheduleTracker.start(configContext, fundCodes.size());
        int successCount = 0;
        for (String fundCode : fundCodes) {
            try {
                FundEstimateVo result = refreshEstimate(fundCode, configContext);
                scheduleTracker.recordStatus(result.getSourceStatus());
                if (FundEstimateSourceStatusEnum.NORMAL.getCode().equals(result.getSourceStatus())) {
                    successCount++;
                } else {
                    log.info("定时刷新基金 {} 未生成正常估值，状态: {}", fundCode, result.getSourceStatus());
                }
            } catch (RuntimeException e) {
                log.warn("定时刷新基金 {} 估值失败: {}", fundCode, e.getMessage());
                scheduleTracker.recordFailure(e.getMessage());
            }
        }
        scheduleTracker.complete();
        return successCount;
    }

    @Override
    public int cleanupExpiredSnapshots() {
        int deleted = estimateMapper.deleteExpiredPreservingLatest(
            LocalDateTime.now(runtimeSettings.getScheduleZoneId())
                .minusDays(runtimeSettings.getRetentionDays())
                .atZone(runtimeSettings.getScheduleZoneId())
                .toOffsetDateTime(),
            runtimeSettings.getScheduleBatchSize()
        );
        estimateMetrics.recordCleanup(deleted);
        return deleted;
    }

    @Override
    public FundEstimateScheduleStatusVo queryScheduleStatus() {
        return scheduleTracker.snapshot(runtimeSettings);
    }

    private FundEstimateVo staleFallback(String fundCode, QuantConfigTaskContext configContext, String reason) {
        FundEstimateVo fallback = queryCachedOrSnapshot(fundCode, configContext);
        if (fallback == null) {
            estimateMetrics.recordStaleFallback(false);
            FundEstimateVo failed = upstreamFailed(fundCode, configContext, reason);
            estimateMetrics.recordResult(failed);
            return failed;
        }
        estimateMetrics.recordStaleFallback(true);
        fallback.setStale(true);
        fallback.setSourceStatus("STALE");
        fallback.setStatusReason(reason);
        estimateMetrics.recordResult(fallback);
        return fallback;
    }

    private FundEstimateVo unavailableForMissingInputs(String fundCode, QuantConfigTaskContext configContext) {
        FundEstimateVo unavailable = new FundEstimateVo();
        unavailable.setFundCode(fundCode);
        unavailable.setSource("FUND_DATA_CENTER");
        unavailable.setSourceStatus(FundEstimateSourceStatusEnum.UNSUPPORTED.getCode());
        unavailable.setStatusReason("DATA_CENTER_INPUT_UNAVAILABLE");
        unavailable.setConfigReleaseVersion(configContext.getConfigReleaseVersion());
        unavailable.setConfigReleaseChecksum(configContext.getConfigReleaseChecksum());
        QuantConfigReleaseReference.GroupReference estimateGroup = configContext.getGroups() == null
            ? null : configContext.getGroups().get("ESTIMATE");
        if (estimateGroup != null && estimateGroup.getConfigVersion() != null) {
            unavailable.setEstimateConfigVersion(estimateGroup.getConfigVersion().longValue());
            unavailable.setEstimateConfigChecksum(estimateGroup.getChecksum());
        }
        return unavailable;
    }

    private FundEstimateVo upstreamFailed(String fundCode, QuantConfigTaskContext configContext, String reason) {
        FundEstimateVo failed = new FundEstimateVo();
        failed.setFundCode(fundCode);
        failed.setSource("FUND_QUANT");
        failed.setSourceStatus(FundEstimateSourceStatusEnum.UPSTREAM_FAILED.getCode());
        failed.setStatusReason("UPSTREAM_FAILED: " + reason);
        failed.setStale(true);
        failed.setConfigReleaseVersion(configContext.getConfigReleaseVersion());
        failed.setConfigReleaseChecksum(configContext.getConfigReleaseChecksum());
        failed.setAlgorithmVersion(estimateAlgorithmVersion(configContext));
        QuantConfigReleaseReference.GroupReference estimateGroup = configContext.getGroups() == null
            ? null : configContext.getGroups().get("ESTIMATE");
        if (estimateGroup != null && estimateGroup.getConfigVersion() != null) {
            failed.setEstimateConfigVersion(estimateGroup.getConfigVersion().longValue());
            failed.setEstimateConfigChecksum(estimateGroup.getChecksum());
        }
        return failed;
    }

    private FundEstimateVo fromProvider(EstimateProviderResponse response) {
        FundEstimateVo vo = new FundEstimateVo();
        vo.setFundCode(response.getFundCode());
        vo.setEstimateNav(response.getEstimateNav());
        vo.setEstimateGrowthRate(response.getEstimateGrowthRate());
        vo.setPreviousNav(response.getPreviousNav());
        vo.setPreviousNavDate(response.getPreviousNavDate());
        vo.setEstimateTime(response.getEstimateTime() == null ? null : response.getEstimateTime().toLocalDateTime());
        vo.setQuoteTime(response.getQuoteTime() == null ? null : response.getQuoteTime().toLocalDateTime());
        vo.setSource(response.getSource());
        vo.setHoldingCoverageRate(response.getHoldingCoverageRate());
        vo.setQuoteCoverageRate(response.getQuoteCoverageRate());
        vo.setMissingQuoteCount(response.getMissingQuoteCount());
        vo.setStatusReason(response.getStatusReason());
        vo.setHoldingReportDate(response.getHoldingReportDate());
        vo.setReportPeriod(response.getReportPeriod());
        vo.setInputDataVersion(response.getInputDataVersion());
        vo.setAlgorithmVersion(response.getAlgorithmVersion());
        vo.setTradeDate(response.getTradeDate());
        vo.setConfigReleaseVersion(response.getConfigReleaseVersion());
        vo.setConfigReleaseChecksum(response.getConfigReleaseChecksum());
        vo.setEstimateConfigVersion(response.getEstimateConfigVersion());
        vo.setEstimateConfigChecksum(response.getEstimateConfigChecksum());
        List<EstimateHoldingContributionResponse> contributions = response.getContributions() == null
            ? List.of() : response.getContributions();
        vo.setContributions(contributions.stream()
            .map(this::toContribution)
            .toList());
        vo.setSourceStatus(response.getSourceStatus());
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
        vo.setQuoteTime(entity.getQuoteTime() == null ? null : entity.getQuoteTime().toLocalDateTime());
        vo.setSource(entity.getSource());
        vo.setHoldingCoverageRate(entity.getHoldingCoverageRate());
        vo.setQuoteCoverageRate(entity.getQuoteCoverageRate());
        vo.setMissingQuoteCount(entity.getMissingQuoteCount());
        vo.setStatusReason(entity.getStatusReason());
        vo.setHoldingReportDate(entity.getHoldingReportDate());
        vo.setReportPeriod(entity.getHoldingReportPeriod());
        vo.setInputDataVersion(entity.getInputDataVersion());
        vo.setAlgorithmVersion(entity.getAlgorithmVersion());
        vo.setTradeDate(entity.getTradeDate());
        vo.setConfigReleaseVersion(entity.getConfigReleaseVersion());
        vo.setConfigReleaseChecksum(entity.getConfigReleaseChecksum());
        vo.setEstimateConfigVersion(entity.getEstimateConfigVersion());
        vo.setEstimateConfigChecksum(entity.getEstimateConfigChecksum());
        vo.setStale(fallback || isExpired(entity.getEstimateTime() == null
            ? null : entity.getEstimateTime().toLocalDateTime()) || !"NORMAL".equals(entity.getSourceStatus()));
        vo.setSourceStatus(vo.isStale() ? "STALE" : entity.getSourceStatus());
        return vo;
    }

    private boolean isExpired(LocalDateTime estimateTime) {
        return estimateTime == null || estimateTime.plus(runtimeSettings.getStaleAfter())
            .isBefore(LocalDateTime.now(runtimeSettings.getScheduleZoneId()));
    }

    private void validate(String requestedCode, FundEstimateVo estimate, QuantConfigTaskContext configContext) {
        if (!requestedCode.equals(estimate.getFundCode())) {
            throw new ServiceException("估值基金代码不匹配");
        }
        if (!FundEstimateSourceStatusEnum.isSupported(estimate.getSourceStatus())) {
            throw new ServiceException("估值状态不受支持");
        }
        quantConfigTaskContextResolver.assertMatches(
            configContext,
            estimate.getConfigReleaseVersion(),
            estimate.getConfigReleaseChecksum()
        );
        assertEstimateGroupMatches(configContext, estimate);
        if (!FundEstimateSourceStatusEnum.NORMAL.getCode().equals(estimate.getSourceStatus())) {
            if (estimate.getStatusReason() == null || estimate.getStatusReason().isBlank()) {
                throw new ServiceException("非正常估值缺少状态原因");
            }
            return;
        }
        if (estimate.getEstimateNav() == null || estimate.getEstimateNav().signum() <= 0) {
            throw new ServiceException("估值净值必须大于 0");
        }
        if (estimate.getEstimateTime() == null
            || estimate.getEstimateTime().isAfter(LocalDateTime.now(runtimeSettings.getScheduleZoneId()).plusMinutes(1))) {
            throw new ServiceException("估值时间无效");
        }
        if (estimate.getTradeDate() == null
            || estimate.getTradeDate().isAfter(LocalDateTime.now(runtimeSettings.getScheduleZoneId()).toLocalDate())) {
            throw new ServiceException("估值交易日无效");
        }
        if (estimate.getInputDataVersion() == null || estimate.getInputDataVersion().isBlank()) {
            throw new ServiceException("正常估值缺少输入数据版本");
        }
        if (!Objects.equals(estimateAlgorithmVersion(configContext), estimate.getAlgorithmVersion())) {
            throw new ServiceException("估值算法版本不匹配");
        }
        validateCoverageRate(estimate.getHoldingCoverageRate(), "持仓覆盖率");
        validateCoverageRate(estimate.getQuoteCoverageRate(), "行情覆盖率");
    }

    private void validateCoverageRate(BigDecimal coverageRate, String fieldName) {
        if (coverageRate == null || coverageRate.signum() < 0
            || coverageRate.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new ServiceException(fieldName + "必须在 0 到 100 之间");
        }
    }

    private void assertEstimateGroupMatches(QuantConfigTaskContext configContext, FundEstimateVo estimate) {
        QuantConfigReleaseReference.GroupReference expected = configContext.getGroups() == null
            ? null : configContext.getGroups().get("ESTIMATE");
        if (expected == null || expected.getConfigVersion() == null
            || expected.getChecksum() == null || expected.getChecksum().isBlank()
            || !Objects.equals(expected.getConfigVersion().longValue(), estimate.getEstimateConfigVersion())
            || !Objects.equals(expected.getChecksum(), estimate.getEstimateConfigChecksum())) {
            throw new ServiceException("QUANT_CONFIG_VERSION_MISMATCH");
        }
    }

    private boolean persistSnapshot(FundEstimateVo estimate, QuantConfigTaskContext configContext) {
        FundEstimate latest = estimateMapper.selectLatestForRelease(
            estimate.getFundCode(),
            configContext.getConfigReleaseVersion(),
            configContext.getConfigReleaseChecksum()
        );
        if (latest != null && latest.getEstimateTime() != null
            && Math.abs(ChronoUnit.SECONDS.between(
                latest.getEstimateTime().toLocalDateTime(), estimate.getEstimateTime()))
                < runtimeSettings.getSnapshotThrottleSeconds()) {
            return false;
        }
        FundEstimate entity = new FundEstimate();
        entity.setFundCode(estimate.getFundCode());
        entity.setEstimateNav(estimate.getEstimateNav());
        entity.setEstimateGrowthRate(estimate.getEstimateGrowthRate());
        entity.setPreviousNav(estimate.getPreviousNav());
        entity.setPreviousNavDate(estimate.getPreviousNavDate());
        entity.setEstimateTime(estimate.getEstimateTime()
            .atZone(runtimeSettings.getScheduleZoneId())
            .toOffsetDateTime());
        entity.setSource(estimate.getSource() == null || estimate.getSource().isBlank()
            ? "UPSTREAM" : estimate.getSource());
        entity.setSourceStatus("NORMAL");
        entity.setHoldingCoverageRate(estimate.getHoldingCoverageRate());
        entity.setQuoteCoverageRate(estimate.getQuoteCoverageRate());
        entity.setMissingQuoteCount(estimate.getMissingQuoteCount());
        entity.setQuoteTime(estimate.getQuoteTime() == null ? null : estimate.getQuoteTime()
            .atZone(runtimeSettings.getScheduleZoneId()).toOffsetDateTime());
        entity.setHoldingReportDate(estimate.getHoldingReportDate());
        entity.setHoldingReportPeriod(estimate.getReportPeriod());
        entity.setInputDataVersion(estimate.getInputDataVersion());
        entity.setAlgorithmVersion(estimate.getAlgorithmVersion());
        entity.setTradeDate(estimate.getTradeDate());
        entity.setConfigReleaseVersion(configContext.getConfigReleaseVersion());
        entity.setConfigReleaseChecksum(configContext.getConfigReleaseChecksum());
        entity.setEstimateConfigVersion(estimate.getEstimateConfigVersion());
        entity.setEstimateConfigChecksum(estimate.getEstimateConfigChecksum());
        estimateMapper.insert(entity);
        return true;
    }

    private String estimateCacheKey(String fundCode, QuantConfigTaskContext configContext) {
        return FundCacheConstants.estimateCacheKey(
            fundCode,
            estimateAlgorithmVersion(configContext),
            configContext.getConfigReleaseVersion(),
            configContext.getConfigReleaseChecksum()
        );
    }

    private String estimateLockKey(String fundCode) {
        return FundCacheConstants.estimateLockKey(fundCode);
    }

    private String estimateAlgorithmVersion(QuantConfigTaskContext configContext) {
        QuantConfigReleaseReference.GroupReference estimateGroup = configContext.getGroups() == null
            ? null : configContext.getGroups().get("ESTIMATE");
        if (estimateGroup == null || estimateGroup.getSchemaVersion() == null) {
            throw new ServiceException("QUANT_CONFIG_VERSION_MISMATCH");
        }
        return "holding-estimate-v" + estimateGroup.getSchemaVersion();
    }

    private void validateRecalculationContext(QuantConfigTaskContext configContext) {
        if (configContext == null || configContext.getConfigReleaseVersion() == null
            || configContext.getConfigReleaseVersion() < 1
            || configContext.getConfigReleaseChecksum() == null
            || !configContext.getConfigReleaseChecksum().matches("^[0-9a-f]{64}$")) {
            throw new ServiceException("QUANT_CONFIG_VERSION_MISMATCH");
        }
        QuantConfigReleaseReference.GroupReference estimateGroup = configContext.getGroups().get("ESTIMATE");
        if (estimateGroup == null || estimateGroup.getConfigVersion() == null
            || estimateGroup.getSchemaVersion() == null || estimateGroup.getChecksum() == null
            || !estimateGroup.getChecksum().matches("^[0-9a-f]{64}$")) {
            throw new ServiceException("QUANT_CONFIG_VERSION_MISMATCH");
        }
    }

    private boolean isQuantConfigFailure(ServiceException error) {
        return error.getMessage() != null && error.getMessage().startsWith("QUANT_CONFIG_");
    }
}
