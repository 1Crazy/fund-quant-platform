package org.dromara.fund.service;

import org.dromara.fund.domain.vo.FundEstimateVo;

/**
 * 基金实时估值服务。
 */
public interface IFundEstimateService {

    FundEstimateVo queryEstimate(String fundCode);

    FundEstimateVo queryCachedOrSnapshot(String fundCode);

    /**
     * 定时刷新所有启用基金的估值。
     * 单只基金失败不会中断整批刷新。
     *
     * @return 刷新成功的基金数量
     */
    int refreshActiveFunds();
}
