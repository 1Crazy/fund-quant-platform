package org.dromara.fund.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.common.redis.utils.RedisUtils;
import org.dromara.fund.client.FundDataProviderClient;
import org.dromara.fund.constant.FundCacheConstants;
import org.dromara.fund.domain.FundInfo;
import org.dromara.fund.domain.bo.FundQueryBo;
import org.dromara.fund.domain.vo.FundDetailVo;
import org.dromara.fund.domain.vo.FundEstimateVo;
import org.dromara.fund.domain.vo.FundHoldingVo;
import org.dromara.fund.domain.vo.FundListVo;
import org.dromara.fund.domain.vo.FundNavPointVo;
import org.dromara.fund.mapper.FundInfoMapper;
import org.dromara.fund.mapper.FundNavMapper;
import org.dromara.fund.service.IFundDataSyncService;
import org.dromara.fund.service.IFundEstimateService;
import org.dromara.fund.service.IFundQueryService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 基金列表与详情查询。
 */
@Service
@RequiredArgsConstructor
public class FundQueryServiceImpl implements IFundQueryService {

    private final FundInfoMapper fundInfoMapper;
    private final FundNavMapper fundNavMapper;
    private final FundDataProviderClient fundDataProviderClient;
    private final IFundDataSyncService fundDataSyncService;
    private final IFundEstimateService estimateService;

    @Override
    public TableDataInfo<FundListVo> queryPage(FundQueryBo bo, PageQuery pageQuery) {
        String fundCode = normalizeFundCode(bo.getFundCode());
        String fundName = normalizeText(bo.getFundName());
        bo.setFundCode(fundCode);
        bo.setFundName(fundName);
        if (isExactFundCode(fundCode)) {
            // 精确代码查询采用读穿透：本地未收录时由 Java 统一向 fund-quant 回源并落库。
            fundDataSyncService.ensureAvailable(fundCode, 1);
        } else if (fundName != null) {
            // 名称搜索先同步轻量基金目录，净值与完整档案在进入详情时按需加载。
            fundDataSyncService.syncCatalogMatches(fundName);
        }
        Page<FundListVo> page = fundInfoMapper.selectFundPage(pageQuery.build(), bo);
        for (FundListVo row : page.getRecords()) {
            // 分页 SQL 已一次性加载数据库最新估值；这里只用 Redis 热点值覆盖，避免逐行查库形成 N+1。
            FundEstimateVo estimate = RedisUtils.getCacheObject(
                FundCacheConstants.ESTIMATE_KEY_PREFIX + row.getFundCode());
            if (estimate != null) {
                row.setEstimateNav(estimate.getEstimateNav());
                row.setEstimateGrowthRate(estimate.getEstimateGrowthRate());
                row.setEstimateTime(estimate.getEstimateTime());
                row.setStale(estimate.isStale());
            }
        }
        return TableDataInfo.build(page);
    }

    @Override
    public FundDetailVo queryDetail(String fundCode, String period) {
        NavPeriod navPeriod = NavPeriod.from(period);
        if (isExactFundCode(fundCode)) {
            fundDataSyncService.ensureAvailable(fundCode, navPeriod.syncDays());
        }
        FundInfo fund = fundInfoMapper.selectOne(Wrappers.<FundInfo>lambdaQuery()
            .eq(FundInfo::getFundCode, fundCode)
            .eq(FundInfo::getStatus, "0"));
        if (fund == null) {
            throw new ServiceException("基金代码 {} 不存在或已停用", fundCode);
        }
        FundNavPointVo latest = fundNavMapper.selectLatest(fundCode);
        List<FundNavPointVo> series = fundNavMapper.selectSeries(fundCode, navPeriod.startDate());

        FundDetailVo detail = new FundDetailVo();
        detail.setFundCode(fund.getFundCode());
        detail.setFundName(fund.getFundName());
        detail.setFundType(fund.getFundType());
        detail.setManagerName(fund.getManagerName());
        detail.setCustodianName(fund.getCustodianName());
        detail.setEstablishDate(fund.getEstablishDate());
        detail.setBenchmark(fund.getBenchmark());
        detail.setRiskLevel(fund.getRiskLevel());
        detail.setFundScale(fund.getFundScale());
        detail.setLatestNav(latest == null ? null : latest.getUnitNav());
        detail.setNavDate(latest == null ? null : latest.getDate());
        detail.setEstimate(estimateService.queryCachedOrSnapshot(fundCode));
        detail.setNavSeries(series);
        try {
            List<FundHoldingVo> holdings = fundDataProviderClient.fetchHoldings(fundCode).stream()
                .map(source -> {
                    FundHoldingVo target = new FundHoldingVo();
                    target.setStockCode(source.getStockCode());
                    target.setStockName(source.getStockName());
                    target.setWeight(source.getWeight());
                    target.setReportPeriod(source.getReportPeriod());
                    return target;
                })
                .toList();
            detail.setHoldings(holdings);
            BigDecimal holdingCoverageRate = holdings.stream()
                .map(FundHoldingVo::getWeight)
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
            detail.setHoldingCoverageRate(holdingCoverageRate);
            if (holdings.isEmpty()) {
                detail.setHoldingNote("最新报告期未披露直接股票持仓；ETF 联接基金可能主要持有目标 ETF。");
            } else if (holdingCoverageRate.compareTo(new BigDecimal("10")) < 0) {
                detail.setHoldingNote("直接股票披露占基金净值比例较低，基金可能主要持有目标 ETF；下表不是完整底层资产持仓。");
                // 旧快照可能由低覆盖持仓计算而来，详情页不得继续展示为可信盘中估值。
                detail.setEstimate(null);
            } else {
                detail.setHoldingNote("持仓为最近公开报告期数据，不代表当前实时仓位。");
            }
        } catch (ServiceException e) {
            // 持仓是详情页补充信息，上游暂时不可用时不应阻断净值与基础档案展示。
            detail.setHoldings(List.of());
            detail.setHoldingCoverageRate(BigDecimal.ZERO);
            detail.setHoldingNote("基金持仓数据暂时不可用，请稍后刷新。");
        }
        return detail;
    }

    private String normalizeFundCode(String fundCode) {
        return normalizeText(fundCode);
    }

    private String normalizeText(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private boolean isExactFundCode(String fundCode) {
        return fundCode != null && fundCode.matches("^\\d{6}$");
    }

    /** 自然时间周期与上游同步容量的统一映射。 */
    private record NavPeriod(LocalDate startDate, int syncDays) {

        private static NavPeriod from(String value) {
            LocalDate today = LocalDate.now();
            return switch (value) {
                case "1m" -> new NavPeriod(today.minusMonths(1), 32);
                case "3m" -> new NavPeriod(today.minusMonths(3), 93);
                case "6m" -> new NavPeriod(today.minusMonths(6), 186);
                case "1y" -> new NavPeriod(today.minusYears(1), 366);
                case "3y" -> new NavPeriod(today.minusYears(3), 1096);
                case "5y" -> new NavPeriod(today.minusYears(5), 1827);
                case "all" -> new NavPeriod(null, 0);
                default -> throw new ServiceException("不支持的净值周期: {}", value);
            };
        }
    }
}
