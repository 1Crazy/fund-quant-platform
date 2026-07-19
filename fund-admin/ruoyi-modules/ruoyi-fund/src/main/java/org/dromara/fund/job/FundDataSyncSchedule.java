package org.dromara.fund.job;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.fund.domain.vo.FundSyncRunVo;
import org.dromara.fund.service.IFundDataSyncService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 基金数据中心短周期增量触发入口。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "fund.data", name = {"enabled", "schedule-enabled"}, havingValue = "true")
public class FundDataSyncSchedule {

    private final IFundDataSyncService fundDataSyncService;

    @Scheduled(
        cron = "${fund.data.incremental-cron:0 30 18 * * MON-FRI}",
        zone = "${fund.data.zone-id:Asia/Shanghai}"
    )
    public void incrementalSync() {
        FundSyncRunVo run = fundDataSyncService.runIncremental();
        log.info("基金数据中心增量同步触发完成，batch={}, state={}", run.getFetchBatchId(), run.getState());
    }
}
