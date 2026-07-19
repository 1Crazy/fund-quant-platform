package org.dromara.fund.domain.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * 基金同步状态摘要。
 */
@Data
public class FundSyncStatusSummaryVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String dataset;
    private String source;
    private OffsetDateTime sourceTime;
    private LocalDate businessDate;
    private String scopeType;
    private String scopeValue;
    private String state;
    private String qualityStatus;
    private String fetchBatchId;
    private String dataVersion;
    private String checksum;
    private OffsetDateTime startedAt;
    private OffsetDateTime finishedAt;
    private Integer successCount;
    private Integer rejectedCount;
    private Integer failedCount;
    private Integer retryCount;
    private Long upstreamLatencyMs;
    private Integer staleCount;
    private Integer cacheInvalidatedCount;
    private String errorCode;
    private String errorMessage;
}
