package org.dromara.fund.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/** 当前节点的实时估值调度运行摘要。 */
@Data
public class FundEstimateScheduleStatusVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private boolean scheduleEnabled;
    private boolean activeTradingSession;
    private boolean scheduleLockHeld;
    private String scheduleCron;
    private String scheduleZoneId;
    private LocalDateTime lastStartedAt;
    private LocalDateTime lastCompletedAt;
    private Integer requestedCount;
    private Integer normalCount;
    private Integer partialCount;
    private Integer unsupportedCount;
    private Integer failedCount;
    private String lastError;
    private Long configReleaseVersion;
    private String configReleaseChecksum;
}
