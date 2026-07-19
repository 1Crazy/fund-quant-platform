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
 * SnailJob 全量目录初始化分区任务。
 */
@Component
@RequiredArgsConstructor
@JobExecutor(name = "fundFullInitJob")
public class FundFullInitJobExecutor {

    private final IFundDataSyncService fundDataSyncService;

    public ExecuteResult jobExecute(JobArgs jobArgs) {
        String cursor = jobArgs.getJobParams() == null ? null : String.valueOf(jobArgs.getJobParams());
        FundSyncRunVo run = fundDataSyncService.runFullInitPartition(cursor);
        SnailJobLog.REMOTE.info("fundFullInitJob finished. batch={}, state={}, cursor={}",
            run.getFetchBatchId(), run.getState(), run.getCursorValue());
        return ExecuteResult.success("batch=" + run.getFetchBatchId() + ",state=" + run.getState());
    }
}
