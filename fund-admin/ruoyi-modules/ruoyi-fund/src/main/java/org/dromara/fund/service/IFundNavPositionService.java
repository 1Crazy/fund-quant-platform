package org.dromara.fund.service;

import org.dromara.fund.domain.dto.QuantConfigTaskContext;
import org.dromara.fund.domain.vo.FundNavPositionBatchStatusVo;
import org.dromara.fund.domain.vo.FundNavPositionVo;

/** 历史 NAV 位置查询服务。 */
public interface IFundNavPositionService {

    /** 缓存未命中时向 fund-quant 计算。 */
    FundNavPositionVo queryNavPosition(String fundCode);

    /** 列表只读取已有缓存，避免分页逐行触发跨服务计算。 */
    FundNavPositionVo queryCached(String fundCode, QuantConfigTaskContext configContext);

    /** 异步计算所有已有确认净值基金的历史位置。 */
    FundNavPositionBatchStatusVo submitBatchCalculation();

    /** 查询全量历史位置计算进度。 */
    FundNavPositionBatchStatusVo queryBatchCalculationStatus();
}
