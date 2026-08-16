package org.dromara.fund.service;

import org.dromara.common.redis.utils.RedisUtils;
import org.dromara.common.core.utils.SpringUtils;
import org.dromara.fund.config.FundEstimateRuntimeSettings;
import org.dromara.fund.constant.FundCacheConstants;
import org.dromara.fund.domain.dto.QuantConfigTaskContext;
import org.dromara.fund.domain.vo.FundEstimateScheduleStatusVo;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;

import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 定时刷新进度计数器的状态快照契约。 */
@Tag("dev")
final class FundEstimateScheduleTrackerTest {

    @BeforeAll
    static void installRedisClientForStaticUtility() {
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        beanFactory.registerSingleton("redissonClient", mock(RedissonClient.class));
        new SpringUtils().postProcessBeanFactory(beanFactory);
    }

    @Test
    void snapshotExposesProgressCountersAndScheduleState() {
        FundEstimateScheduleTracker tracker = new FundEstimateScheduleTracker();
        QuantConfigTaskContext context = new QuantConfigTaskContext();
        context.setConfigReleaseVersion(2L);
        context.setConfigReleaseChecksum("a".repeat(64));
        tracker.start(context, 4);
        tracker.recordStatus("NORMAL");
        tracker.recordStatus("PARTIAL");
        tracker.recordStatus("UNSUPPORTED");
        tracker.recordFailure("provider timeout");
        tracker.complete();

        FundEstimateRuntimeSettings settings = mock(FundEstimateRuntimeSettings.class);
        when(settings.isScheduleEnabled()).thenReturn(true);
        when(settings.getScheduleCron()).thenReturn("0 * * * * *");
        when(settings.getScheduleZoneId()).thenReturn(ZoneId.of("Asia/Shanghai"));
        when(settings.isActiveTradingSession(Mockito.any())).thenReturn(true);
        RedissonClient client = mock(RedissonClient.class);
        RLock lock = mock(RLock.class);
        when(client.getLock(FundCacheConstants.ESTIMATE_SCHEDULE_LOCK)).thenReturn(lock);
        when(lock.isLocked()).thenReturn(false);

        try (MockedStatic<RedisUtils> redis = Mockito.mockStatic(RedisUtils.class)) {
            redis.when(RedisUtils::getClient).thenReturn(client);

            FundEstimateScheduleStatusVo status = tracker.snapshot(settings);

            assertEquals(4, status.getRequestedCount());
            assertEquals(1, status.getNormalCount());
            assertEquals(1, status.getPartialCount());
            assertEquals(1, status.getUnsupportedCount());
            assertEquals(1, status.getFailedCount());
            assertEquals("provider timeout", status.getLastError());
            assertEquals(2L, status.getConfigReleaseVersion());
            assertNotNull(status.getLastStartedAt());
            assertNotNull(status.getLastCompletedAt());
        }
    }
}
