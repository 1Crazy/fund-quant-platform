package org.dromara.fund.service.impl;

import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.redis.utils.RedisUtils;
import org.dromara.fund.client.FundEstimateProviderClient;
import org.dromara.fund.config.FundEstimateRuntimeSettings;
import org.dromara.fund.mapper.FundEstimateMapper;
import org.dromara.fund.mapper.FundInfoMapper;
import org.dromara.fund.service.FundEstimateMetrics;
import org.dromara.fund.service.FundEstimateScheduleTracker;
import org.dromara.fund.service.QuantConfigTaskContextResolver;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** 配置血缘无效时，估值服务不得读取或写入任意结果缓存、快照或上游数据。 */
@Tag("dev")
final class FundEstimateServiceImplTest {

    @Test
    void missingConfigReleaseFailsBeforeAnyResultCacheOrSnapshotAccess() {
        FundEstimateMapper estimateMapper = mock(FundEstimateMapper.class);
        FundInfoMapper fundInfoMapper = mock(FundInfoMapper.class);
        FundEstimateProviderClient providerClient = mock(FundEstimateProviderClient.class);
        FundEstimateRuntimeSettings runtimeSettings = mock(FundEstimateRuntimeSettings.class);
        QuantConfigTaskContextResolver contextResolver = mock(QuantConfigTaskContextResolver.class);
        FundEstimateScheduleTracker scheduleTracker = mock(FundEstimateScheduleTracker.class);
        FundEstimateMetrics metrics = mock(FundEstimateMetrics.class);
        FundEstimateServiceImpl service = new FundEstimateServiceImpl(
            estimateMapper,
            fundInfoMapper,
            providerClient,
            runtimeSettings,
            contextResolver,
            scheduleTracker,
            metrics
        );
        when(contextResolver.pinActiveRelease()).thenThrow(new ServiceException("QUANT_CONFIG_NOT_PUBLISHED"));

        try (MockedStatic<RedisUtils> redis = Mockito.mockStatic(RedisUtils.class)) {
            ServiceException error = assertThrows(ServiceException.class, () -> service.queryEstimate("000001"));

            assertEquals("QUANT_CONFIG_NOT_PUBLISHED", error.getMessage());
            redis.verifyNoInteractions();
        }
        verifyNoInteractions(
            estimateMapper,
            fundInfoMapper,
            providerClient,
            runtimeSettings,
            scheduleTracker,
            metrics
        );
    }

    @Test
    void malformedHistoricalTaskContextFailsBeforeAnyResultCacheOrSnapshotAccess() {
        FundEstimateMapper estimateMapper = mock(FundEstimateMapper.class);
        FundInfoMapper fundInfoMapper = mock(FundInfoMapper.class);
        FundEstimateProviderClient providerClient = mock(FundEstimateProviderClient.class);
        FundEstimateRuntimeSettings runtimeSettings = mock(FundEstimateRuntimeSettings.class);
        QuantConfigTaskContextResolver contextResolver = mock(QuantConfigTaskContextResolver.class);
        FundEstimateScheduleTracker scheduleTracker = mock(FundEstimateScheduleTracker.class);
        FundEstimateMetrics metrics = mock(FundEstimateMetrics.class);
        FundEstimateServiceImpl service = new FundEstimateServiceImpl(
            estimateMapper,
            fundInfoMapper,
            providerClient,
            runtimeSettings,
            contextResolver,
            scheduleTracker,
            metrics
        );

        try (MockedStatic<RedisUtils> redis = Mockito.mockStatic(RedisUtils.class)) {
            ServiceException error = assertThrows(ServiceException.class,
                () -> service.recalculateEstimate("000001", new org.dromara.fund.domain.dto.QuantConfigTaskContext()));

            assertEquals("QUANT_CONFIG_VERSION_MISMATCH", error.getMessage());
            redis.verifyNoInteractions();
        }
        verifyNoInteractions(
            estimateMapper,
            fundInfoMapper,
            providerClient,
            runtimeSettings,
            contextResolver,
            scheduleTracker,
            metrics
        );
    }
}
