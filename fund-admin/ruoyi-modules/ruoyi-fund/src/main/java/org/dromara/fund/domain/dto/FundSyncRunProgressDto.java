package org.dromara.fund.domain.dto;

import lombok.Data;

import java.time.OffsetDateTime;

/**
 * 同步运行进度更新载荷。
 */
@Data
public class FundSyncRunProgressDto {

    private String runId;
    private Long syncRunId;
    private String fetchBatchId;
    private String state;
    private String cursorValue;
    private String dataVersion;
    private Integer successCount;
    private Integer rejectedCount;
    private Integer failedCount;
    private Integer retryCount;
    private OffsetDateTime finishedAt;
    private Long durationMillis;
    private String errorCode;
    private String errorMessage;
}
