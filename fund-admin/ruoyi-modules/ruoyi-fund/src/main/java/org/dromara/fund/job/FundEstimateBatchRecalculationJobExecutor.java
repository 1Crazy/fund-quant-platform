package org.dromara.fund.job;

import com.aizuda.snailjob.client.job.core.annotation.JobExecutor;
import com.aizuda.snailjob.client.job.core.dto.JobArgs;
import com.aizuda.snailjob.common.log.SnailJobLog;
import com.aizuda.snailjob.model.dto.ExecuteResult;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.fund.config.FundEstimateRuntimeSettings;
import org.dromara.fund.domain.dto.QuantConfigTaskContext;
import org.dromara.fund.domain.vo.FundEstimateVo;
import org.dromara.fund.mapper.FundInfoMapper;
import org.dromara.fund.service.IFundEstimateService;
import org.dromara.fund.service.QuantConfigTaskContextResolver;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * SnailJob 分片历史重算。参数为 {@code releaseVersion,releaseChecksum,shardIndex,shardTotal}。
 *
 * <p>每个分片按基金代码游标分批扫描；单基金异常只计入该分片失败数，发布版本或作业参数异常则直接失败，
 * 由 SnailJob 的重试策略处理。</p>
 */
@Component
@RequiredArgsConstructor
@JobExecutor(name = "fundEstimateBatchRecalculationJob")
public class FundEstimateBatchRecalculationJobExecutor {

    private final IFundEstimateService fundEstimateService;
    private final FundInfoMapper fundInfoMapper;
    private final FundEstimateRuntimeSettings runtimeSettings;
    private final QuantConfigTaskContextResolver quantConfigTaskContextResolver;

    public ExecuteResult jobExecute(JobArgs jobArgs) {
        BatchRecalculationRequest request = parseRequest(jobArgs);
        QuantConfigTaskContext context = quantConfigTaskContextResolver.pinRelease(
            request.releaseVersion(), request.releaseChecksum()
        );
        int batchSize = runtimeSettings.getScheduleBatchSize();
        BatchProgress progress = new BatchProgress();
        String lastFundCode = "";
        while (true) {
            List<String> fundCodes = fundInfoMapper.selectReadyEstimateFundCodesForShard(
                lastFundCode, request.shardIndex(), request.shardTotal(), batchSize
            );
            if (fundCodes.isEmpty()) {
                break;
            }
            for (String fundCode : fundCodes) {
                recalculateOne(fundCode, context, progress);
            }
            lastFundCode = fundCodes.get(fundCodes.size() - 1);
            SnailJobLog.REMOTE.info(
                "fundEstimateBatchRecalculationJob progress. shard={}/{}, cursor={}, processed={}, normal={}, partial={}, unsupported={}, failed={}, releaseVersion={}, checksum={}",
                request.shardIndex() + 1,
                request.shardTotal(),
                lastFundCode,
                progress.processed(),
                progress.normal(),
                progress.partial(),
                progress.unsupported(),
                progress.failed(),
                context.getConfigReleaseVersion(),
                context.getConfigReleaseChecksum()
            );
            if (fundCodes.size() < batchSize) {
                break;
            }
        }
        String summary = "shard=" + (request.shardIndex() + 1) + "/" + request.shardTotal()
            + ",processed=" + progress.processed()
            + ",normal=" + progress.normal()
            + ",partial=" + progress.partial()
            + ",unsupported=" + progress.unsupported()
            + ",failed=" + progress.failed()
            + ",releaseVersion=" + context.getConfigReleaseVersion();
        SnailJobLog.REMOTE.info("fundEstimateBatchRecalculationJob finished. {}, checksum={}",
            summary, context.getConfigReleaseChecksum());
        return ExecuteResult.success(summary);
    }

    private void recalculateOne(String fundCode, QuantConfigTaskContext context, BatchProgress progress) {
        try {
            FundEstimateVo result = fundEstimateService.recalculateEstimate(fundCode, context);
            progress.recordStatus(result.getSourceStatus());
        } catch (RuntimeException error) {
            progress.recordFailure();
            SnailJobLog.REMOTE.warn(
                "fundEstimateBatchRecalculationJob fund failed. fundCode={}, releaseVersion={}, checksum={}, message={}",
                fundCode,
                context.getConfigReleaseVersion(),
                context.getConfigReleaseChecksum(),
                error.getMessage()
            );
        }
    }

    private BatchRecalculationRequest parseRequest(JobArgs jobArgs) {
        String params = jobArgs.getJobParams() == null ? "" : String.valueOf(jobArgs.getJobParams()).trim();
        String[] parts = params.split(",", -1);
        if (parts.length != 4 || !parts[1].trim().matches("^[0-9a-f]{64}$")) {
            throw new ServiceException(
                "fundEstimateBatchRecalculationJob 参数必须为 releaseVersion,releaseChecksum,shardIndex,shardTotal"
            );
        }
        try {
            long releaseVersion = Long.parseLong(parts[0].trim());
            int shardIndex = Integer.parseInt(parts[2].trim());
            int shardTotal = Integer.parseInt(parts[3].trim());
            if (releaseVersion < 1 || shardTotal < 1 || shardIndex < 0 || shardIndex >= shardTotal) {
                throw new NumberFormatException("invalid release version or shard range");
            }
            return new BatchRecalculationRequest(releaseVersion, parts[1].trim(), shardIndex, shardTotal);
        } catch (NumberFormatException error) {
            throw new ServiceException(
                "fundEstimateBatchRecalculationJob 参数中的 releaseVersion、shardIndex、shardTotal 无效"
            );
        }
    }

    private record BatchRecalculationRequest(
        long releaseVersion,
        String releaseChecksum,
        int shardIndex,
        int shardTotal
    ) {
    }

    private static final class BatchProgress {
        private int processed;
        private int normal;
        private int partial;
        private int unsupported;
        private int failed;

        private void recordStatus(String sourceStatus) {
            processed++;
            if ("NORMAL".equals(sourceStatus)) {
                normal++;
            } else if ("PARTIAL".equals(sourceStatus)) {
                partial++;
            } else if ("UNSUPPORTED".equals(sourceStatus)) {
                unsupported++;
            } else {
                failed++;
            }
        }

        private void recordFailure() {
            processed++;
            failed++;
        }

        private int processed() {
            return processed;
        }

        private int normal() {
            return normal;
        }

        private int partial() {
            return partial;
        }

        private int unsupported() {
            return unsupported;
        }

        private int failed() {
            return failed;
        }
    }
}
