package org.dromara.fund.job;

import org.dromara.fund.config.FundEstimateRuntimeSettings;
import org.dromara.fund.service.IFundEstimateService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.time.ZoneId;
import java.time.ZonedDateTime;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Spring Scheduler 的交易时段和收盘强制快照门禁。 */
@Tag("dev")
final class FundEstimateScheduleTest {

    @Test
    void disabledScheduleDoesNotTriggerRefresh() {
        IFundEstimateService service = mock(IFundEstimateService.class);
        FundEstimateRuntimeSettings settings = mock(FundEstimateRuntimeSettings.class);
        when(settings.isScheduleEnabled()).thenReturn(false);

        new FundEstimateSchedule(service, settings).pollRefreshEstimate();

        verifyNoInteractions(service);
    }

    @Test
    void configuredCloseSnapshotForcesSnapshotPersistence() {
        IFundEstimateService service = mock(IFundEstimateService.class);
        FundEstimateRuntimeSettings settings = mock(FundEstimateRuntimeSettings.class);
        ZoneId zone = ZoneId.of("Asia/Shanghai");
        ZonedDateTime close = ZonedDateTime.of(2026, 8, 14, 15, 0, 0, 0, zone);
        when(settings.isScheduleEnabled()).thenReturn(true);
        when(settings.getScheduleZoneId()).thenReturn(zone);
        when(settings.isCloseSnapshotSlot(close)).thenReturn(true);

        try (MockedStatic<ZonedDateTime> time = Mockito.mockStatic(
            ZonedDateTime.class, Mockito.CALLS_REAL_METHODS)) {
            time.when(() -> ZonedDateTime.now(zone)).thenReturn(close);

            new FundEstimateSchedule(service, settings).pollRefreshEstimate();
        }

        verify(service).refreshActiveFunds(true);
    }

    @Test
    void activeTradingCronSlotTriggersRefresh() {
        IFundEstimateService service = mock(IFundEstimateService.class);
        FundEstimateRuntimeSettings settings = mock(FundEstimateRuntimeSettings.class);
        ZoneId zone = ZoneId.of("Asia/Shanghai");
        ZonedDateTime slot = ZonedDateTime.of(2026, 8, 14, 10, 0, 0, 0, zone);
        when(settings.isScheduleEnabled()).thenReturn(true);
        when(settings.getScheduleZoneId()).thenReturn(zone);
        when(settings.isCloseSnapshotSlot(slot)).thenReturn(false);
        when(settings.isActiveTradingSession(slot)).thenReturn(true);
        when(settings.getScheduleCron()).thenReturn("0 * * * * *");

        try (MockedStatic<ZonedDateTime> time = Mockito.mockStatic(
            ZonedDateTime.class, Mockito.CALLS_REAL_METHODS)) {
            time.when(() -> ZonedDateTime.now(zone)).thenReturn(slot);

            new FundEstimateSchedule(service, settings).pollRefreshEstimate();
        }

        verify(service).refreshActiveFunds(false);
    }

    @Test
    void inactiveTradingSessionDoesNotTriggerRefresh() {
        IFundEstimateService service = mock(IFundEstimateService.class);
        FundEstimateRuntimeSettings settings = mock(FundEstimateRuntimeSettings.class);
        ZoneId zone = ZoneId.of("Asia/Shanghai");
        ZonedDateTime beforeOpen = ZonedDateTime.of(2026, 8, 14, 8, 30, 0, 0, zone);
        when(settings.isScheduleEnabled()).thenReturn(true);
        when(settings.getScheduleZoneId()).thenReturn(zone);
        when(settings.isCloseSnapshotSlot(beforeOpen)).thenReturn(false);
        when(settings.isActiveTradingSession(beforeOpen)).thenReturn(false);

        try (MockedStatic<ZonedDateTime> time = Mockito.mockStatic(
            ZonedDateTime.class, Mockito.CALLS_REAL_METHODS)) {
            time.when(() -> ZonedDateTime.now(zone)).thenReturn(beforeOpen);

            new FundEstimateSchedule(service, settings).pollRefreshEstimate();
        }

        verifyNoInteractions(service);
    }
}
