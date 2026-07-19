package org.dromara.fund.domain.dto;

import lombok.Data;

import java.time.OffsetDateTime;

/**
 * 数据质量问题写入载荷。
 */
@Data
public class FundQualityIssueRecordDto {

    private Long syncRunId;
    private String dataset;
    private String fetchBatchId;
    private String recordKey;
    private String reasonCode;
    private String rawSummary;
    private String issueStatus;
    private OffsetDateTime detectedAt;
}
