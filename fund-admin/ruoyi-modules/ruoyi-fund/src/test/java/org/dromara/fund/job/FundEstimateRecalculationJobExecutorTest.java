package org.dromara.fund.job;

import com.aizuda.snailjob.client.job.core.dto.JobArgs;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.fund.domain.dto.QuantConfigTaskContext;
import org.dromara.fund.domain.vo.FundEstimateVo;
import org.dromara.fund.service.IFundEstimateService;
import org.dromara.fund.service.QuantConfigTaskContextResolver;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** SnailJob 历史重算必须有明确目标发布版本，不能隐式转向活动版本。 */
@Tag("dev")
final class FundEstimateRecalculationJobExecutorTest {

    @Test
    void jobPinsTheRequestedReleaseBeforeRecalculating() {
        IFundEstimateService estimateService = mock(IFundEstimateService.class);
        QuantConfigTaskContextResolver contextResolver = mock(QuantConfigTaskContextResolver.class);
        JobArgs jobArgs = mock(JobArgs.class);
        QuantConfigTaskContext context = new QuantConfigTaskContext();
        context.setConfigReleaseVersion(2L);
        context.setConfigReleaseChecksum("a".repeat(64));
        FundEstimateVo result = new FundEstimateVo();
        result.setSourceStatus("NORMAL");
        when(jobArgs.getJobParams()).thenReturn("000001,2," + "a".repeat(64));
        when(contextResolver.pinRelease(2L, "a".repeat(64))).thenReturn(context);
        when(estimateService.recalculateEstimate("000001", context)).thenReturn(result);

        new FundEstimateRecalculationJobExecutor(estimateService, contextResolver).jobExecute(jobArgs);

        verify(contextResolver).pinRelease(2L, "a".repeat(64));
        verify(estimateService).recalculateEstimate("000001", context);
    }

    @Test
    void jobRejectsMissingOrAmbiguousReleaseParameters() {
        IFundEstimateService estimateService = mock(IFundEstimateService.class);
        QuantConfigTaskContextResolver contextResolver = mock(QuantConfigTaskContextResolver.class);
        JobArgs jobArgs = mock(JobArgs.class);
        when(jobArgs.getJobParams()).thenReturn("000001,2");

        assertThrows(ServiceException.class,
            () -> new FundEstimateRecalculationJobExecutor(estimateService, contextResolver).jobExecute(jobArgs));

        verifyNoInteractions(estimateService, contextResolver);
    }
}
