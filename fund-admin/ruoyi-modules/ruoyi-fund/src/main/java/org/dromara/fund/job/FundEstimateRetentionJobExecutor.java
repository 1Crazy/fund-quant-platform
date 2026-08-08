package org.dromara.fund.job;

import com.aizuda.snailjob.client.job.core.annotation.JobExecutor;
import com.aizuda.snailjob.client.job.core.dto.JobArgs;
import com.aizuda.snailjob.common.log.SnailJobLog;
import com.aizuda.snailjob.model.dto.ExecuteResult;
import lombok.RequiredArgsConstructor;
import org.dromara.fund.service.IFundEstimateService;
import org.dromara.fund.service.QuantConfigTaskContextResolver;
import org.springframework.stereotype.Component;

/**
 * 估值保留清理由 SnailJob 调度；每次仅删除一个有界批次，避免影响实时刷新。
 */
@Component
@RequiredArgsConstructor
@JobExecutor(name = "fundEstimateRetentionJob")
public class FundEstimateRetentionJobExecutor {

    private final IFundEstimateService fundEstimateService;
    private final QuantConfigTaskContextResolver quantConfigTaskContextResolver;

    public ExecuteResult jobExecute(JobArgs jobArgs) {
        var context = quantConfigTaskContextResolver.pinActiveRelease();
        int deleted = fundEstimateService.cleanupExpiredSnapshots();
        SnailJobLog.REMOTE.info(
            "fundEstimateRetentionJob finished. deleted={}, releaseVersion={}, checksum={}",
            deleted,
            context.getConfigReleaseVersion(),
            context.getConfigReleaseChecksum()
        );
        return ExecuteResult.success("deleted=" + deleted + ",releaseVersion=" + context.getConfigReleaseVersion());
    }
}
