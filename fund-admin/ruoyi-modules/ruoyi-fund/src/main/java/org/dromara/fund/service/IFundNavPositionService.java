package org.dromara.fund.service;

import org.dromara.fund.domain.vo.FundNavPositionBatchStatusVo;
import org.dromara.fund.domain.vo.FundNavPositionVo;

/** 历史 NAV 位置查询服务。 */
public interface IFundNavPositionService {

    /** 当前发布版本没有已持久化结果时，向 fund-quant 计算并保存。 */
    FundNavPositionVo queryNavPosition(String fundCode);

    /** 异步计算所有已有确认净值基金的历史位置。 */
    FundNavPositionBatchStatusVo submitBatchCalculation();

    /** 查询全量历史位置计算进度。 */
    FundNavPositionBatchStatusVo queryBatchCalculationStatus();
}
