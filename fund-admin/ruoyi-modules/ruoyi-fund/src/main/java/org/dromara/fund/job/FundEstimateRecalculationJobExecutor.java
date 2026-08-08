package org.dromara.fund.job;

import com.aizuda.snailjob.client.job.core.annotation.JobExecutor;
import com.aizuda.snailjob.client.job.core.dto.JobArgs;
import com.aizuda.snailjob.common.log.SnailJobLog;
import com.aizuda.snailjob.model.dto.ExecuteResult;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.fund.domain.dto.QuantConfigTaskContext;
import org.dromara.fund.domain.vo.FundEstimateVo;
import org.dromara.fund.service.IFundEstimateService;
import org.dromara.fund.service.QuantConfigTaskContextResolver;
import org.springframework.stereotype.Component;

/**
 * 显式历史重算。SnailJob 参数为 {@code fundCode,releaseVersion,releaseChecksum}，
 * 没有任何活动发布或最新发布替代路径。
 */
@Component
@RequiredArgsConstructor
@JobExecutor(name = "fundEstimateRecalculationJob")
public class FundEstimateRecalculationJobExecutor {

    private final IFundEstimateService fundEstimateService;
    private final QuantConfigTaskContextResolver quantConfigTaskContextResolver;

    public ExecuteResult jobExecute(JobArgs jobArgs) {
        RecalculationRequest request = parseRequest(jobArgs);
        QuantConfigTaskContext context = quantConfigTaskContextResolver.pinRelease(
            request.releaseVersion(),
            request.releaseChecksum()
        );
        FundEstimateVo result = fundEstimateService.recalculateEstimate(request.fundCode(), context);
        SnailJobLog.REMOTE.info(
            "fundEstimateRecalculationJob finished. fundCode={}, releaseVersion={}, checksum={}, status={}",
            request.fundCode(),
            context.getConfigReleaseVersion(),
            context.getConfigReleaseChecksum(),
            result.getSourceStatus()
        );
        return ExecuteResult.success(
            "fundCode=" + request.fundCode()
                + ",releaseVersion=" + context.getConfigReleaseVersion()
                + ",sourceStatus=" + result.getSourceStatus()
        );
    }

    private RecalculationRequest parseRequest(JobArgs jobArgs) {
        String params = jobArgs.getJobParams() == null ? "" : String.valueOf(jobArgs.getJobParams()).trim();
        String[] parts = params.split(",", -1);
        if (parts.length != 3 || !parts[0].trim().matches("^\\d{6}$")
            || !parts[2].trim().matches("^[0-9a-f]{64}$")) {
            throw new ServiceException(
                "fundEstimateRecalculationJob 参数必须为 fundCode,releaseVersion,releaseChecksum"
            );
        }
        try {
            long releaseVersion = Long.parseLong(parts[1].trim());
            if (releaseVersion < 1) {
                throw new NumberFormatException("releaseVersion must be positive");
            }
            return new RecalculationRequest(parts[0].trim(), releaseVersion, parts[2].trim());
        } catch (NumberFormatException error) {
            throw new ServiceException(
                "fundEstimateRecalculationJob 参数中的 releaseVersion 必须为正整数"
            );
        }
    }

    private record RecalculationRequest(String fundCode, long releaseVersion, String releaseChecksum) {
    }
}
