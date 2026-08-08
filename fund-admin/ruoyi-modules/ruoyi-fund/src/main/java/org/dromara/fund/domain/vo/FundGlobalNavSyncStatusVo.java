package org.dromara.fund.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.OffsetDateTime;

/** 面向控制台的全量确认净值同步执行状态。 */
@Data
public class FundGlobalNavSyncStatusVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;
    private String runId;
    private String syncType;
    /** IDLE、RUNNING、PAUSED、SUCCESS、PARTIAL_SUCCESS、FAILED 或 INTERRUPTED。 */
    private String state;
    private int totalFundCount;
    private int processedFundCount;
    private String cursorValue;
    private Integer successCount;
    private Integer rejectedCount;
    private Integer failedCount;
    private OffsetDateTime startedAt;
    private OffsetDateTime finishedAt;
    private String errorMessage;
    /** 后台执行器已经不持有锁，可安全继续。 */
    private boolean resumable;
}
