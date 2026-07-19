package org.dromara.fund.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * 基金同步运行视图。
 */
@Data
public class FundSyncRunVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;
    private String runId;
    private String fetchBatchId;
    private String dataset;
    private String source;
    private OffsetDateTime sourceTime;
    private LocalDate businessDate;
    private String scopeType;
    private String scopeValue;
    private String syncType;
    private String syncScope;
    private String fundCode;
    private String partitionKey;
    private String state;
    private String status;
    private String qualityStatus;
    private String cursorValue;
    private String dataVersion;
    private String checksum;
    private Integer successCount;
    private Integer rejectedCount;
    private Integer failedCount;
    private Integer retryCount;
    private OffsetDateTime startedAt;
    private OffsetDateTime finishedAt;
    private Long durationMillis;
    private Long durationMs;
    private Long upstreamLatencyMs;
    private Integer staleCount;
    private Integer cacheInvalidatedCount;
    private String errorCode;
    private String errorMessage;
    private String errorSummary;
}
