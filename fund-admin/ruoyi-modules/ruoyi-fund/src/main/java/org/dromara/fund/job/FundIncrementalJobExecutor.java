package org.dromara.fund.job;

import com.aizuda.snailjob.client.job.core.annotation.JobExecutor;
import com.aizuda.snailjob.client.job.core.dto.JobArgs;
import com.aizuda.snailjob.common.log.SnailJobLog;
import com.aizuda.snailjob.model.dto.ExecuteResult;
import lombok.RequiredArgsConstructor;
import org.dromara.fund.domain.vo.FundSyncRunVo;
import org.dromara.fund.service.IFundDataSyncService;
import org.springframework.stereotype.Component;

/**
 * SnailJob 日常增量同步任务。
 */
@Component
@RequiredArgsConstructor
@JobExecutor(name = "fundIncrementalSyncJob")
public class FundIncrementalJobExecutor {

    private final IFundDataSyncService fundDataSyncService;

    public ExecuteResult jobExecute(JobArgs jobArgs) {
        FundSyncRunVo run = fundDataSyncService.runIncremental();
        SnailJobLog.REMOTE.info("fundIncrementalSyncJob finished. batch={}, state={}",
            run.getFetchBatchId(), run.getState());
        return ExecuteResult.success("batch=" + run.getFetchBatchId() + ",state=" + run.getState());
    }
}
