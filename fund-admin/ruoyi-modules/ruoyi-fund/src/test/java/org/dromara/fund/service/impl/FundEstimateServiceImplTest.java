package org.dromara.fund.service.impl;

import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.SpringUtils;
import org.dromara.common.redis.utils.RedisUtils;
import org.dromara.fund.client.FundEstimateProviderClient;
import org.dromara.fund.config.FundEstimateRuntimeSettings;
import org.dromara.fund.constant.FundCacheConstants;
import org.dromara.fund.domain.FundEstimate;
import org.dromara.fund.domain.dto.EstimateProviderResponse;
import org.dromara.fund.domain.dto.QuantConfigReleaseReference;
import org.dromara.fund.domain.dto.QuantConfigTaskContext;
import org.dromara.fund.domain.vo.FundEstimateVo;
import org.dromara.fund.mapper.FundEstimateMapper;
import org.dromara.fund.mapper.FundInfoMapper;
import org.dromara.fund.service.FundEstimateMetrics;
import org.dromara.fund.service.FundEstimateScheduleTracker;
import org.dromara.fund.service.QuantConfigTaskContextResolver;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** 估值服务的配置血缘、降级结果与手动刷新缓存边界。 */
@Tag("dev")
final class FundEstimateServiceImplTest {

    @BeforeAll
    static void installRedisClientForStaticUtility() {
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        beanFactory.registerSingleton("redissonClient", mock(RedissonClient.class));
        new SpringUtils().postProcessBeanFactory(beanFactory);
    }

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

    @Test
    void missingDataCenterInputsReturnControlledUnsupportedResponse() {
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
        QuantConfigTaskContext context = estimateContext();
        when(contextResolver.pinActiveRelease()).thenReturn(context);
        when(fundInfoMapper.hasReadyEstimateInputs("000001")).thenReturn(false);

        FundEstimateVo result = service.queryEstimate("000001");

        assertEquals("UNSUPPORTED", result.getSourceStatus());
        assertEquals("DATA_CENTER_INPUT_UNAVAILABLE", result.getStatusReason());
        assertEquals(2L, result.getConfigReleaseVersion());
        assertEquals(15L, result.getEstimateConfigVersion());
        verifyNoInteractions(estimateMapper, providerClient);
    }

    @Test
    void snapshotFallbackIsAlwaysMarkedStale() {
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
        QuantConfigTaskContext context = estimateContext();
        FundEstimate snapshot = new FundEstimate();
        snapshot.setFundCode("000001");
        snapshot.setSourceStatus("NORMAL");
        snapshot.setEstimateNav(java.math.BigDecimal.ONE);
        snapshot.setEstimateTime(java.time.OffsetDateTime.now());
        when(estimateMapper.selectLatestForRelease("000001", 2L, "a".repeat(64))).thenReturn(snapshot);
        String cacheKey = FundCacheConstants.estimateCacheKey(
            "000001", "holding-estimate-v2", 2L, "a".repeat(64)
        );

        try (MockedStatic<RedisUtils> redis = Mockito.mockStatic(RedisUtils.class)) {
            redis.when(() -> RedisUtils.getCacheObject(cacheKey)).thenReturn(null);

            FundEstimateVo result = service.queryCachedOrSnapshot("000001", context);

            assertTrue(result.isStale());
            assertEquals("STALE", result.getSourceStatus());
            assertEquals(java.math.BigDecimal.ONE, result.getEstimateNav());
        }
    }

    @Test
    void providerFailureReturnsTheLatestSnapshotAsStaleFallback() throws InterruptedException {
        ServiceFixture fixture = fixture();
        QuantConfigTaskContext context = estimateContext();
        String cacheKey = FundCacheConstants.estimateCacheKey(
            "000001", "holding-estimate-v2", 2L, "a".repeat(64)
        );
        RedissonClient client = mock(RedissonClient.class);
        RLock lock = mock(RLock.class);
        FundEstimate snapshot = new FundEstimate();
        snapshot.setFundCode("000001");
        snapshot.setSourceStatus("NORMAL");
        snapshot.setEstimateNav(java.math.BigDecimal.ONE);
        snapshot.setEstimateTime(java.time.OffsetDateTime.now());
        when(fixture.contextResolver().pinActiveRelease()).thenReturn(context);
        when(fixture.fundInfoMapper().hasReadyEstimateInputs("000001")).thenReturn(true);
        when(fixture.runtimeSettings().getLockWaitMillis()).thenReturn(800L);
        when(fixture.runtimeSettings().getLockLeaseMillis()).thenReturn(5_000L);
        when(client.getLock(FundCacheConstants.estimateLockKey("000001"))).thenReturn(lock);
        when(lock.tryLock(800L, 5_000L, java.util.concurrent.TimeUnit.MILLISECONDS)).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);
        when(fixture.providerClient().fetch("000001", context))
            .thenThrow(new ServiceException("基金估值上游请求失败"));
        when(fixture.estimateMapper().selectLatestForRelease("000001", 2L, "a".repeat(64)))
            .thenReturn(snapshot);

        try (MockedStatic<RedisUtils> redis = Mockito.mockStatic(RedisUtils.class)) {
            redis.when(() -> RedisUtils.getCacheObject(cacheKey)).thenReturn(null);
            redis.when(RedisUtils::getClient).thenReturn(client);

            FundEstimateVo result = fixture.service().queryEstimate("000001");

            assertTrue(result.isStale());
            assertEquals("STALE", result.getSourceStatus());
            assertEquals("基金估值上游请求失败", result.getStatusReason());
        }
    }

    @Test
    void manualRefreshDeletesTheVersionedHotCacheBeforeLoadingInputs() {
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
        QuantConfigTaskContext context = estimateContext();
        when(contextResolver.pinActiveRelease()).thenReturn(context);
        when(fundInfoMapper.hasReadyEstimateInputs("000001")).thenReturn(false);
        String cacheKey = FundCacheConstants.estimateCacheKey(
            "000001", "holding-estimate-v2", 2L, "a".repeat(64)
        );

        try (MockedStatic<RedisUtils> redis = Mockito.mockStatic(RedisUtils.class)) {
            FundEstimateVo result = service.refreshEstimate("000001");

            assertFalse(result.isStale());
            assertEquals("UNSUPPORTED", result.getSourceStatus());
            redis.verify(() -> RedisUtils.deleteObject(cacheKey));
        }
    }

    @Test
    void retentionCleanupUsesConfiguredRetentionAndBatchLimit() {
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
        when(runtimeSettings.getScheduleZoneId()).thenReturn(java.time.ZoneId.of("Asia/Shanghai"));
        when(runtimeSettings.getRetentionDays()).thenReturn(180);
        when(runtimeSettings.getScheduleBatchSize()).thenReturn(50);
        when(estimateMapper.deleteExpiredPreservingLatest(
            org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(50)
        )).thenReturn(3);

        assertEquals(3, service.cleanupExpiredSnapshots());

        verify(estimateMapper).deleteExpiredPreservingLatest(
            org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(50)
        );
        verify(metrics).recordCleanup(3);
    }

    @Test
    void secondCacheReadCollapsesConcurrentMissesAfterLockAcquisition() throws InterruptedException {
        ServiceFixture fixture = fixture();
        QuantConfigTaskContext context = estimateContext();
        String cacheKey = FundCacheConstants.estimateCacheKey(
            "000001", "holding-estimate-v2", 2L, "a".repeat(64)
        );
        FundEstimateVo refreshedByAnotherRequest = new FundEstimateVo();
        refreshedByAnotherRequest.setFundCode("000001");
        refreshedByAnotherRequest.setSourceStatus("NORMAL");
        RedissonClient client = mock(RedissonClient.class);
        RLock lock = mock(RLock.class);
        when(fixture.contextResolver().pinActiveRelease()).thenReturn(context);
        when(fixture.fundInfoMapper().hasReadyEstimateInputs("000001")).thenReturn(true);
        when(fixture.runtimeSettings().getLockWaitMillis()).thenReturn(800L);
        when(fixture.runtimeSettings().getLockLeaseMillis()).thenReturn(5_000L);
        when(client.getLock(FundCacheConstants.estimateLockKey("000001"))).thenReturn(lock);
        when(lock.tryLock(800L, 5_000L, java.util.concurrent.TimeUnit.MILLISECONDS)).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);

        try (MockedStatic<RedisUtils> redis = Mockito.mockStatic(RedisUtils.class)) {
            redis.when(() -> RedisUtils.getCacheObject(cacheKey))
                .thenReturn(null, refreshedByAnotherRequest);
            redis.when(RedisUtils::getClient).thenReturn(client);

            FundEstimateVo result = fixture.service().queryEstimate("000001");

            assertSame(refreshedByAnotherRequest, result);
            verifyNoInteractions(fixture.providerClient());
        }
    }

    @Test
    void lockContentionReturnsTheLatestSnapshotAsStaleFallback() throws InterruptedException {
        ServiceFixture fixture = fixture();
        QuantConfigTaskContext context = estimateContext();
        String cacheKey = FundCacheConstants.estimateCacheKey(
            "000001", "holding-estimate-v2", 2L, "a".repeat(64)
        );
        RedissonClient client = mock(RedissonClient.class);
        RLock lock = mock(RLock.class);
        FundEstimate snapshot = new FundEstimate();
        snapshot.setFundCode("000001");
        snapshot.setSourceStatus("NORMAL");
        snapshot.setEstimateNav(java.math.BigDecimal.ONE);
        snapshot.setEstimateTime(java.time.OffsetDateTime.now());
        when(fixture.contextResolver().pinActiveRelease()).thenReturn(context);
        when(fixture.fundInfoMapper().hasReadyEstimateInputs("000001")).thenReturn(true);
        when(fixture.runtimeSettings().getLockWaitMillis()).thenReturn(800L);
        when(fixture.runtimeSettings().getLockLeaseMillis()).thenReturn(5_000L);
        when(client.getLock(FundCacheConstants.estimateLockKey("000001"))).thenReturn(lock);
        when(lock.tryLock(800L, 5_000L, java.util.concurrent.TimeUnit.MILLISECONDS)).thenReturn(false);
        when(fixture.estimateMapper().selectLatestForRelease("000001", 2L, "a".repeat(64))).thenReturn(snapshot);

        try (MockedStatic<RedisUtils> redis = Mockito.mockStatic(RedisUtils.class)) {
            redis.when(() -> RedisUtils.getCacheObject(cacheKey)).thenReturn(null);
            redis.when(RedisUtils::getClient).thenReturn(client);

            FundEstimateVo result = fixture.service().queryEstimate("000001");

            assertTrue(result.isStale());
            assertEquals("STALE", result.getSourceStatus());
            verifyNoInteractions(fixture.providerClient());
        }
    }

    @Test
    void normalEstimateUsesConfiguredCacheTtl() throws InterruptedException {
        ServiceFixture fixture = fixture();
        QuantConfigTaskContext context = estimateContext();
        String cacheKey = FundCacheConstants.estimateCacheKey(
            "000001", "holding-estimate-v2", 2L, "a".repeat(64)
        );
        Duration cacheTtl = Duration.ofSeconds(45);
        RedissonClient client = mock(RedissonClient.class);
        RLock lock = mock(RLock.class);
        when(fixture.contextResolver().pinActiveRelease()).thenReturn(context);
        when(fixture.fundInfoMapper().hasReadyEstimateInputs("000001")).thenReturn(true);
        when(fixture.runtimeSettings().getLockWaitMillis()).thenReturn(800L);
        when(fixture.runtimeSettings().getLockLeaseMillis()).thenReturn(5_000L);
        when(fixture.runtimeSettings().getScheduleZoneId()).thenReturn(java.time.ZoneId.of("Asia/Shanghai"));
        when(fixture.runtimeSettings().getCacheTtl(org.mockito.ArgumentMatchers.any())).thenReturn(cacheTtl);
        when(client.getLock(FundCacheConstants.estimateLockKey("000001"))).thenReturn(lock);
        when(lock.tryLock(800L, 5_000L, java.util.concurrent.TimeUnit.MILLISECONDS)).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);
        when(fixture.providerClient().fetch("000001", context)).thenReturn(normalProviderResponse());

        try (MockedStatic<RedisUtils> redis = Mockito.mockStatic(RedisUtils.class)) {
            redis.when(() -> RedisUtils.getCacheObject(cacheKey)).thenReturn(null);
            redis.when(RedisUtils::getClient).thenReturn(client);

            FundEstimateVo result = fixture.service().queryEstimate("000001");

            assertFalse(result.isStale());
            assertEquals("NORMAL", result.getSourceStatus());
            redis.verify(() -> RedisUtils.setCacheObject(cacheKey, result, cacheTtl));
        }
    }

    private EstimateProviderResponse normalProviderResponse() {
        EstimateProviderResponse response = new EstimateProviderResponse();
        response.setFundCode("000001");
        response.setEstimateNav(java.math.BigDecimal.ONE);
        response.setEstimateGrowthRate(java.math.BigDecimal.ZERO);
        response.setPreviousNav(java.math.BigDecimal.ONE);
        response.setPreviousNavDate(java.time.LocalDate.now());
        response.setEstimateTime(java.time.OffsetDateTime.now());
        response.setQuoteTime(java.time.OffsetDateTime.now());
        response.setSource("FUND_QUANT");
        response.setSourceStatus("NORMAL");
        response.setHoldingCoverageRate(java.math.BigDecimal.valueOf(80));
        response.setQuoteCoverageRate(java.math.BigDecimal.valueOf(80));
        response.setMissingQuoteCount(0);
        response.setHoldingReportDate(java.time.LocalDate.now().minusDays(30));
        response.setReportPeriod("2026Q2");
        response.setInputDataVersion("nav-v3");
        response.setAlgorithmVersion("holding-estimate-v2");
        response.setTradeDate(java.time.LocalDate.now());
        response.setConfigReleaseVersion(2L);
        response.setConfigReleaseChecksum("a".repeat(64));
        response.setEstimateConfigVersion(15L);
        response.setEstimateConfigChecksum("b".repeat(64));
        return response;
    }

    private ServiceFixture fixture() {
        FundEstimateMapper estimateMapper = mock(FundEstimateMapper.class);
        FundInfoMapper fundInfoMapper = mock(FundInfoMapper.class);
        FundEstimateProviderClient providerClient = mock(FundEstimateProviderClient.class);
        FundEstimateRuntimeSettings runtimeSettings = mock(FundEstimateRuntimeSettings.class);
        QuantConfigTaskContextResolver contextResolver = mock(QuantConfigTaskContextResolver.class);
        FundEstimateScheduleTracker scheduleTracker = mock(FundEstimateScheduleTracker.class);
        FundEstimateMetrics metrics = mock(FundEstimateMetrics.class);
        return new ServiceFixture(
            new FundEstimateServiceImpl(
                estimateMapper,
                fundInfoMapper,
                providerClient,
                runtimeSettings,
                contextResolver,
                scheduleTracker,
                metrics
            ),
            estimateMapper,
            fundInfoMapper,
            providerClient,
            runtimeSettings,
            contextResolver
        );
    }

    private record ServiceFixture(
        FundEstimateServiceImpl service,
        FundEstimateMapper estimateMapper,
        FundInfoMapper fundInfoMapper,
        FundEstimateProviderClient providerClient,
        FundEstimateRuntimeSettings runtimeSettings,
        QuantConfigTaskContextResolver contextResolver
    ) {
    }

    private QuantConfigTaskContext estimateContext() {
        QuantConfigReleaseReference.GroupReference estimateGroup = new QuantConfigReleaseReference.GroupReference();
        estimateGroup.setConfigVersion(15);
        estimateGroup.setSchemaVersion(2);
        estimateGroup.setChecksum("b".repeat(64));
        QuantConfigTaskContext context = new QuantConfigTaskContext();
        context.setConfigReleaseVersion(2L);
        context.setConfigReleaseChecksum("a".repeat(64));
        context.setGroups(java.util.Map.of("ESTIMATE", estimateGroup));
        return context;
    }
}
