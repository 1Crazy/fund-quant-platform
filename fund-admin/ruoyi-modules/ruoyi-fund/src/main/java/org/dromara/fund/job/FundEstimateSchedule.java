package org.dromara.fund.job;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.fund.service.IFundEstimateService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 基金盘中估值刷新任务。
 *
 * <p>任务只负责调度，分布式防重与单基金降级由估值 Service 负责。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "fund.estimate", name = "schedule-enabled", havingValue = "true")
public class FundEstimateSchedule {

    private final IFundEstimateService fundEstimateService;

    @Scheduled(
        cron = "${fund.estimate.schedule-cron:0 */5 9-15 * * MON-FRI}",
        zone = "${fund.estimate.zone-id:Asia/Shanghai}"
    )
    public void refreshEstimate() {
        int successCount = fundEstimateService.refreshActiveFunds();
        log.info("基金实时估值定时刷新完成，成功基金数: {}", successCount);
    }
}
