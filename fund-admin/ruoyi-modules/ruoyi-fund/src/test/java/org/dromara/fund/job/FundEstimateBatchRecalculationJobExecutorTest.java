package org.dromara.fund.job;

import com.aizuda.snailjob.client.job.core.dto.JobArgs;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.fund.config.FundEstimateRuntimeSettings;
import org.dromara.fund.domain.dto.QuantConfigTaskContext;
import org.dromara.fund.domain.vo.FundEstimateVo;
import org.dromara.fund.mapper.FundInfoMapper;
import org.dromara.fund.service.IFundEstimateService;
import org.dromara.fund.service.QuantConfigTaskContextResolver;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** SnailJob 批量重算必须固定发布血缘，并按稳定分片游标推进。 */
@Tag("dev")
final class FundEstimateBatchRecalculationJobExecutorTest {

    @Test
    void jobPinsReleaseAndContinuesTheAssignedShardCursor() {
        IFundEstimateService estimateService = mock(IFundEstimateService.class);
        FundInfoMapper fundInfoMapper = mock(FundInfoMapper.class);
        FundEstimateRuntimeSettings runtimeSettings = mock(FundEstimateRuntimeSettings.class);
        QuantConfigTaskContextResolver contextResolver = mock(QuantConfigTaskContextResolver.class);
        JobArgs jobArgs = mock(JobArgs.class);
        QuantConfigTaskContext context = new QuantConfigTaskContext();
        context.setConfigReleaseVersion(2L);
        context.setConfigReleaseChecksum("a".repeat(64));
        FundEstimateVo result = new FundEstimateVo();
        result.setSourceStatus("NORMAL");
        when(jobArgs.getJobParams()).thenReturn("2," + "a".repeat(64) + ",0,2");
        when(contextResolver.pinRelease(2L, "a".repeat(64))).thenReturn(context);
        when(runtimeSettings.getScheduleBatchSize()).thenReturn(1);
        when(fundInfoMapper.selectReadyEstimateFundCodesForShard("", 0, 2, 1))
            .thenReturn(List.of("000001"));
        when(fundInfoMapper.selectReadyEstimateFundCodesForShard("000001", 0, 2, 1))
            .thenReturn(List.of());
        when(estimateService.recalculateEstimate("000001", context)).thenReturn(result);

        new FundEstimateBatchRecalculationJobExecutor(
            estimateService, fundInfoMapper, runtimeSettings, contextResolver
        ).jobExecute(jobArgs);

        verify(contextResolver).pinRelease(2L, "a".repeat(64));
        verify(fundInfoMapper).selectReadyEstimateFundCodesForShard("", 0, 2, 1);
        verify(fundInfoMapper).selectReadyEstimateFundCodesForShard("000001", 0, 2, 1);
        verify(estimateService).recalculateEstimate("000001", context);
    }

    @Test
    void jobRejectsInvalidShardBeforeAnyDatabaseOrProviderAccess() {
        IFundEstimateService estimateService = mock(IFundEstimateService.class);
        FundInfoMapper fundInfoMapper = mock(FundInfoMapper.class);
        FundEstimateRuntimeSettings runtimeSettings = mock(FundEstimateRuntimeSettings.class);
        QuantConfigTaskContextResolver contextResolver = mock(QuantConfigTaskContextResolver.class);
        JobArgs jobArgs = mock(JobArgs.class);
        when(jobArgs.getJobParams()).thenReturn("2," + "a".repeat(64) + ",2,2");

        assertThrows(ServiceException.class, () -> new FundEstimateBatchRecalculationJobExecutor(
            estimateService, fundInfoMapper, runtimeSettings, contextResolver
        ).jobExecute(jobArgs));

        verifyNoInteractions(estimateService, fundInfoMapper, runtimeSettings, contextResolver);
    }
}
