package org.dromara.fund.service;

import org.dromara.fund.domain.dto.QuantConfigTaskContext;
import org.dromara.fund.domain.vo.FundEstimateVo;
import org.dromara.fund.domain.vo.FundEstimateScheduleStatusVo;

/**
 * 基金实时估值服务。
 */
public interface IFundEstimateService {

    FundEstimateVo queryEstimate(String fundCode);

    /** 授权人工刷新，绕过该基金当前 Redis 热缓存，但仍使用锁与快照降级。 */
    FundEstimateVo refreshEstimate(String fundCode);

    /** SnailJob 显式历史重算使用创建任务时固定的目标发布版本。 */
    FundEstimateVo recalculateEstimate(String fundCode, QuantConfigTaskContext configContext);

    FundEstimateVo queryCachedOrSnapshot(String fundCode, QuantConfigTaskContext configContext);

    /**
     * 定时刷新所有启用基金的估值。
     * 单只基金失败不会中断整批刷新。
     *
     * @return 刷新成功的基金数量
     */
    int refreshActiveFunds();

    /** 收盘时强制写入一次正常快照，不受常规快照节流影响。 */
    int refreshActiveFunds(boolean forceSnapshot);

    /** 由 SnailJob 调用，删除超过保留期且不再是同版本最新快照的一个有界批次。 */
    int cleanupExpiredSnapshots();

    FundEstimateScheduleStatusVo queryScheduleStatus();
}
