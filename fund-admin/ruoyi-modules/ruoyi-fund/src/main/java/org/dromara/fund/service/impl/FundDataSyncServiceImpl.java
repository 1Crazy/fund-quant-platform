package org.dromara.fund.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.mybatis.utils.IdGeneratorUtil;
import org.dromara.common.redis.utils.RedisUtils;
import org.dromara.fund.client.FundDataProviderClient;
import org.dromara.fund.config.FundDataProperties;
import org.dromara.fund.domain.FundInfo;
import org.dromara.fund.domain.FundNav;
import org.dromara.fund.domain.dto.FundNavProviderResponse;
import org.dromara.fund.domain.dto.FundProviderResponse;
import org.dromara.fund.mapper.FundInfoMapper;
import org.dromara.fund.mapper.FundNavMapper;
import org.dromara.fund.service.IFundDataSyncService;
import org.redisson.api.RLock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 精确基金代码的懒加载同步实现。
 */
@Service
@RequiredArgsConstructor
public class FundDataSyncServiceImpl implements IFundDataSyncService {

    private static final String SYNC_LOCK_PREFIX = "fund:data:sync:lock:";
    private static final long LOCK_WAIT_SECONDS = 125L;
    private static final long LOCK_LEASE_SECONDS = 180L;

    private final FundInfoMapper fundInfoMapper;
    private final FundNavMapper fundNavMapper;
    private final FundDataProviderClient providerClient;
    private final FundDataProperties properties;
    private final TransactionTemplate transactionTemplate;

    @Override
    public boolean ensureAvailable(String fundCode, int days) {
        int requestedDays = normalizeDays(days);
        if (hasCompleteLocalData(fundCode, requestedDays)) {
            return false;
        }
        RLock lock = RedisUtils.getClient().getLock(SYNC_LOCK_PREFIX + fundCode);
        boolean locked = false;
        try {
            // 等待并发中的首次同步完成，避免第二个请求直接返回空列表。
            locked = lock.tryLock(LOCK_WAIT_SECONDS, LOCK_LEASE_SECONDS, TimeUnit.SECONDS);
            if (!locked) {
                if (hasCompleteLocalData(fundCode, requestedDays)) {
                    return false;
                }
                throw new ServiceException("基金 {} 首次同步繁忙，请稍后重试", fundCode);
            }
            if (hasCompleteLocalData(fundCode, requestedDays)) {
                return false;
            }
            return syncFromProvider(fundCode, requestedDays);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ServiceException("基金 {} 首次同步被中断", fundCode);
        } finally {
            if (locked && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    @Override
    public int syncCatalogMatches(String keyword) {
        String normalized = keyword == null ? "" : keyword.trim();
        if (normalized.isEmpty()) {
            return 0;
        }
        List<FundProviderResponse> matches = providerClient.searchFunds(
            normalized,
            properties.getSearchLimit()
        );
        transactionTemplate.executeWithoutResult(status -> matches.stream()
            .filter(item -> item.getFundCode() != null && item.getFundCode().matches("^\\d{6}$"))
            .map(this::toFundInfo)
            .forEach(fundInfoMapper::upsert));
        return matches.size();
    }

    private boolean syncFromProvider(String fundCode, int days) {
        // 先完成慢速网络调用，再开启短事务写库，避免 AkShare 延迟长期占用数据库连接。
        FundProviderResponse providerFund = providerClient.fetchFund(fundCode);
        if (!fundCode.equals(providerFund.getFundCode())) {
            throw new ServiceException("基金数据中心返回的基金代码不匹配");
        }
        List<FundNavProviderResponse> providerNav = providerClient.fetchNav(
            fundCode,
            days
        );
        FundInfo fund = toFundInfo(providerFund);
        List<FundNav> navItems = providerNav.stream()
            .filter(item -> fundCode.equals(item.getFundCode()))
            .filter(item -> item.getDate() != null && item.getNav() != null)
            .map(item -> toFundNav(item, days == 0 ? "AKSHARE_ALL" : "AKSHARE_DAYS_" + days))
            .toList();
        return Boolean.TRUE.equals(transactionTemplate.execute(status -> {
            fundInfoMapper.upsert(fund);
            if (!navItems.isEmpty()) {
                fundNavMapper.upsertBatch(navItems);
            }
            return true;
        }));
    }

    private boolean hasCompleteLocalData(String fundCode, int requestedDays) {
        boolean fundExists = fundInfoMapper.exists(Wrappers.<FundInfo>lambdaQuery()
            .eq(FundInfo::getFundCode, fundCode)
            .eq(FundInfo::getStatus, "0")
            // 目录搜索只写入基本名称；详情需以档案数据源为准才算完整。
            .eq(FundInfo::getSource, "AKSHARE_XQ")
            // 修复历史半成品记录：来源已标记完成但核心档案字段仍为空时强制重新同步。
            .isNotNull(FundInfo::getManagerName)
            .isNotNull(FundInfo::getEstablishDate)
            .isNotNull(FundInfo::getBenchmark)
            .isNotNull(FundInfo::getFundScale));
        if (!fundExists) {
            return false;
        }
        int coverage = fundNavMapper.selectSyncCoverage(fundCode);
        return requestedDays == 0 ? coverage >= 5000 : coverage >= requestedDays;
    }

    private int normalizeDays(int days) {
        if (days < 0 || days > 5000) {
            throw new ServiceException("净值查询天数必须在 0 到 5000 之间");
        }
        return days;
    }

    private FundInfo toFundInfo(FundProviderResponse source) {
        FundInfo target = new FundInfo();
        target.setId(IdGeneratorUtil.nextLongId());
        target.setFundCode(source.getFundCode());
        target.setFundName(source.getFundName());
        target.setFundType(source.getFundType() == null || source.getFundType().isBlank()
            ? "未知类型" : source.getFundType());
        target.setPinyinAbbr(source.getPinyinAbbr());
        target.setManagerName(source.getManagerName());
        target.setCustodianName(source.getCustodianName());
        target.setEstablishDate(source.getEstablishDate());
        target.setBenchmark(source.getBenchmark());
        target.setRiskLevel(source.getRiskLevel());
        target.setFundScale(source.getFundScale());
        target.setStatus("0");
        target.setSource(source.getSource() == null || source.getSource().isBlank()
            ? "AKSHARE" : source.getSource());
        target.setSourceUpdatedAt(OffsetDateTime.now());
        target.setDelFlag(0L);
        return target;
    }

    private FundNav toFundNav(FundNavProviderResponse source, String dataSource) {
        FundNav target = new FundNav();
        target.setId(IdGeneratorUtil.nextLongId());
        target.setFundCode(source.getFundCode());
        target.setNavDate(source.getDate());
        target.setUnitNav(source.getNav());
        target.setAccumulatedNav(source.getAccumulatedNav());
        target.setDailyGrowthRate(source.getGrowthRate());
        target.setSource(dataSource);
        return target;
    }
}
