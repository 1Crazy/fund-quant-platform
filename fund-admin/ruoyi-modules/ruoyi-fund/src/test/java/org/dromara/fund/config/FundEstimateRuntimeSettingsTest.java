package org.dromara.fund.config;

import org.dromara.common.core.service.ConfigService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 估值运维配置的交易时段和收盘规则。 */
@Tag("dev")
final class FundEstimateRuntimeSettingsTest {

    @Test
    void cacheTtlUsesTradingAndClosedMarketValuesFromSysConfig() {
        FundEstimateRuntimeSettings settings = settings(Map.of(
            FundEstimateRuntimeSettings.CACHE_TTL_SECONDS, "45",
            FundEstimateRuntimeSettings.CLOSED_CACHE_TTL_SECONDS, "1800",
            FundEstimateRuntimeSettings.SCHEDULE_TRADING_SESSIONS, "09:30-11:30,13:00-15:00",
            FundEstimateRuntimeSettings.SCHEDULE_HOLIDAYS, ""
        ));
        ZoneId zone = ZoneId.of("Asia/Shanghai");

        assertEquals(Duration.ofSeconds(45), settings.getCacheTtl(
            ZonedDateTime.of(2026, 8, 14, 10, 0, 0, 0, zone)
        ));
        assertEquals(Duration.ofMinutes(30), settings.getCacheTtl(
            ZonedDateTime.of(2026, 8, 14, 15, 0, 0, 0, zone)
        ));
    }

    @Test
    void closeSnapshotOnlyRunsAtConfiguredTimeOnTradingDays() {
        FundEstimateRuntimeSettings settings = settings(Map.of(
            FundEstimateRuntimeSettings.SNAPSHOT_CLOSE_TIME, "15:00",
            FundEstimateRuntimeSettings.SCHEDULE_HOLIDAYS, "2026-10-01"
        ));
        ZoneId zone = ZoneId.of("Asia/Shanghai");

        assertTrue(settings.isCloseSnapshotSlot(
            ZonedDateTime.of(2026, 8, 14, 15, 0, 0, 0, zone)
        ));
        assertFalse(settings.isCloseSnapshotSlot(
            ZonedDateTime.of(2026, 8, 14, 14, 59, 59, 0, zone)
        ));
        assertFalse(settings.isCloseSnapshotSlot(
            ZonedDateTime.of(2026, 8, 15, 15, 0, 0, 0, zone)
        ));
        assertFalse(settings.isCloseSnapshotSlot(
            ZonedDateTime.of(2026, 10, 1, 15, 0, 0, 0, zone)
        ));
    }

    private FundEstimateRuntimeSettings settings(Map<String, String> values) {
        ConfigService configService = mock(ConfigService.class);
        when(configService.getConfigValue(org.mockito.ArgumentMatchers.anyString()))
            .thenAnswer(invocation -> values.get(invocation.getArgument(0)));
        return new FundEstimateRuntimeSettings(configService);
    }
}
