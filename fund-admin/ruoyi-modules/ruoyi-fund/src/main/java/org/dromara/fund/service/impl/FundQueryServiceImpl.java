package org.dromara.fund.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.common.redis.utils.RedisUtils;
import org.dromara.fund.constant.FundCacheConstants;
import org.dromara.fund.domain.FundInfo;
import org.dromara.fund.domain.bo.FundQueryBo;
import org.dromara.fund.domain.vo.FundDetailVo;
import org.dromara.fund.domain.vo.FundEstimateVo;
import org.dromara.fund.domain.vo.FundListVo;
import org.dromara.fund.domain.vo.FundNavPointVo;
import org.dromara.fund.mapper.FundInfoMapper;
import org.dromara.fund.mapper.FundNavMapper;
import org.dromara.fund.service.IFundEstimateService;
import org.dromara.fund.service.IFundQueryService;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 基金列表与详情查询。
 */
@Service
@RequiredArgsConstructor
public class FundQueryServiceImpl implements IFundQueryService {

    private final FundInfoMapper fundInfoMapper;
    private final FundNavMapper fundNavMapper;
    private final IFundEstimateService estimateService;

    @Override
    public TableDataInfo<FundListVo> queryPage(FundQueryBo bo, PageQuery pageQuery) {
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
    public FundDetailVo queryDetail(String fundCode, int days) {
        FundInfo fund = fundInfoMapper.selectOne(Wrappers.<FundInfo>lambdaQuery()
            .eq(FundInfo::getFundCode, fundCode)
            .eq(FundInfo::getStatus, "0"));
        if (fund == null) {
            throw new ServiceException("基金代码 {} 不存在或已停用", fundCode);
        }
        FundNavPointVo latest = fundNavMapper.selectLatest(fundCode);
        List<FundNavPointVo> series = fundNavMapper.selectSeries(fundCode, days);

        FundDetailVo detail = new FundDetailVo();
        detail.setFundCode(fund.getFundCode());
        detail.setFundName(fund.getFundName());
        detail.setFundType(fund.getFundType());
        detail.setManagerName(fund.getManagerName());
        detail.setEstablishDate(fund.getEstablishDate());
        detail.setBenchmark(fund.getBenchmark());
        detail.setRiskLevel(fund.getRiskLevel());
        detail.setFundScale(fund.getFundScale());
        detail.setLatestNav(latest == null ? null : latest.getUnitNav());
        detail.setNavDate(latest == null ? null : latest.getDate());
        detail.setEstimate(estimateService.queryCachedOrSnapshot(fundCode));
        detail.setNavSeries(series);
        return detail;
    }
}
