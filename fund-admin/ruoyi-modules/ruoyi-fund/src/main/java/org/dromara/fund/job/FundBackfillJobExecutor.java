package org.dromara.fund.job;

import com.aizuda.snailjob.client.job.core.annotation.JobExecutor;
import com.aizuda.snailjob.client.job.core.dto.JobArgs;
import com.aizuda.snailjob.common.log.SnailJobLog;
import com.aizuda.snailjob.model.dto.ExecuteResult;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.fund.domain.vo.FundSyncRunVo;
import org.dromara.fund.service.IFundDataSyncService;
import org.springframework.stereotype.Component;

/**
 * SnailJob 单基金历史 NAV 与持仓回填任务。
 */
@Component
@RequiredArgsConstructor
@JobExecutor(name = "fundBackfillJob")
public class FundBackfillJobExecutor {

    private final IFundDataSyncService fundDataSyncService;

    public ExecuteResult jobExecute(JobArgs jobArgs) {
        String params = jobArgs.getJobParams() == null ? "" : String.valueOf(jobArgs.getJobParams()).trim();
        if (params.isEmpty()) {
            throw new ServiceException("fundBackfillJob 参数必须包含基金代码，可选格式：000001 或 000001,5000");
        }
        String[] parts = params.split(",");
        String fundCode = parts[0].trim();
        int days = parts.length > 1 ? Integer.parseInt(parts[1].trim()) : 5000;
        FundSyncRunVo run = fundDataSyncService.triggerFundSync(fundCode, days);
        SnailJobLog.REMOTE.info("fundBackfillJob finished. fundCode={}, batch={}, state={}",
            fundCode, run.getFetchBatchId(), run.getState());
        return ExecuteResult.success("fundCode=" + fundCode + ",batch=" + run.getFetchBatchId());
    }
}
