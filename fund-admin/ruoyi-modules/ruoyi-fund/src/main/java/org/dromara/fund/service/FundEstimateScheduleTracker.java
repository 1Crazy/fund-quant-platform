package org.dromara.fund.service;

import org.dromara.common.redis.utils.RedisUtils;
import org.dromara.fund.config.FundEstimateRuntimeSettings;
import org.dromara.fund.constant.FundCacheConstants;
import org.dromara.fund.domain.dto.QuantConfigTaskContext;
import org.dromara.fund.domain.vo.FundEstimateScheduleStatusVo;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZonedDateTime;

/** 记录当前节点的估值刷新摘要，供运维接口和日志读取。 */
@Component
public class FundEstimateScheduleTracker {

    private LocalDateTime lastStartedAt;
    private LocalDateTime lastCompletedAt;
    private int requestedCount;
    private int normalCount;
    private int partialCount;
    private int unsupportedCount;
    private int failedCount;
    private String lastError;
    private Long configReleaseVersion;
    private String configReleaseChecksum;

    public synchronized void start(QuantConfigTaskContext context, int requestedCount) {
        this.lastStartedAt = LocalDateTime.now();
        this.lastCompletedAt = null;
        this.requestedCount = requestedCount;
        this.normalCount = 0;
        this.partialCount = 0;
        this.unsupportedCount = 0;
        this.failedCount = 0;
        this.lastError = null;
        this.configReleaseVersion = context.getConfigReleaseVersion();
        this.configReleaseChecksum = context.getConfigReleaseChecksum();
    }

    public synchronized void recordStatus(String sourceStatus) {
        if ("NORMAL".equals(sourceStatus)) {
            normalCount++;
        } else if ("PARTIAL".equals(sourceStatus)) {
            partialCount++;
        } else if ("UNSUPPORTED".equals(sourceStatus)) {
            unsupportedCount++;
        } else {
            failedCount++;
        }
    }

    public synchronized void recordFailure(String error) {
        failedCount++;
        lastError = error;
    }

    public synchronized void complete() {
        lastCompletedAt = LocalDateTime.now();
    }

    public synchronized void markLockContention() {
        lastError = "SCHEDULE_LOCK_CONTENDED";
        lastCompletedAt = LocalDateTime.now();
    }

    public synchronized FundEstimateScheduleStatusVo snapshot(FundEstimateRuntimeSettings settings) {
        FundEstimateScheduleStatusVo status = new FundEstimateScheduleStatusVo();
        status.setScheduleEnabled(settings.isScheduleEnabled());
        status.setScheduleCron(settings.getScheduleCron());
        status.setScheduleZoneId(settings.getScheduleZoneId().getId());
        status.setActiveTradingSession(settings.isActiveTradingSession(
            ZonedDateTime.now(settings.getScheduleZoneId())
        ));
        status.setScheduleLockHeld(RedisUtils.getClient()
            .getLock(FundCacheConstants.ESTIMATE_SCHEDULE_LOCK).isLocked());
        status.setLastStartedAt(lastStartedAt);
        status.setLastCompletedAt(lastCompletedAt);
        status.setRequestedCount(requestedCount);
        status.setNormalCount(normalCount);
        status.setPartialCount(partialCount);
        status.setUnsupportedCount(unsupportedCount);
        status.setFailedCount(failedCount);
        status.setLastError(lastError);
        status.setConfigReleaseVersion(configReleaseVersion);
        status.setConfigReleaseChecksum(configReleaseChecksum);
        return status;
    }
}
