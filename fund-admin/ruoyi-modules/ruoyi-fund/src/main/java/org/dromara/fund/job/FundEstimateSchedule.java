package org.dromara.fund.job;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.fund.config.FundEstimateRuntimeSettings;
import org.dromara.fund.service.IFundEstimateService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Component;

import java.time.ZonedDateTime;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 基金盘中估值刷新任务。
 *
 * <p>任务只负责调度，分布式防重与单基金降级由估值 Service 负责。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FundEstimateSchedule {

    private final IFundEstimateService fundEstimateService;
    private final FundEstimateRuntimeSettings runtimeSettings;
    private final AtomicReference<String> lastTriggeredSlot = new AtomicReference<>();

    /** 每秒轮询 sys_config；真实 cron、交易时段和开关不再来自 application.yml。 */
    @Scheduled(fixedDelay = 1_000L)
    public void pollRefreshEstimate() {
        try {
            if (!runtimeSettings.isScheduleEnabled()) {
                return;
            }
            ZonedDateTime now = ZonedDateTime.now(runtimeSettings.getScheduleZoneId());
            ZonedDateTime slotTime = now.withNano(0);
            if (!runtimeSettings.isActiveTradingSession(now)
                || !isCronSlot(slotTime)) {
                return;
            }
            String slot = slotTime.toOffsetDateTime().toString();
            if (!lastTriggeredSlot.compareAndSet(null, slot) && slot.equals(lastTriggeredSlot.get())) {
                return;
            }
            lastTriggeredSlot.set(slot);
            int successCount = fundEstimateService.refreshActiveFunds();
            log.info("基金实时估值定时刷新完成，成功基金数: {}", successCount);
        } catch (RuntimeException error) {
            // 配置异常不能终止 Spring 调度线程；修复 sys_config 后下一轮会自动恢复。
            log.error("基金实时估值调度跳过: {}", error.getMessage());
        }
    }

    private boolean isCronSlot(ZonedDateTime slotTime) {
        CronExpression cron = CronExpression.parse(runtimeSettings.getScheduleCron());
        ZonedDateTime next = cron.next(slotTime.minusSeconds(1));
        return slotTime.equals(next);
    }
}
