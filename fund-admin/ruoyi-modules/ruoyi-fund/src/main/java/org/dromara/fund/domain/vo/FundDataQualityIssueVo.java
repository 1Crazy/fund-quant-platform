package org.dromara.fund.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * 基金数据质量问题视图。
 */
@Data
public class FundDataQualityIssueVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;
    private Long syncRunId;
    private String dataset;
    private String source;
    private OffsetDateTime sourceTime;
    private LocalDate businessDate;
    private String fetchBatchId;
    private String dataVersion;
    private String checksum;
    private String recordKey;
    private String qualityStatus;
    private String reasonCode;
    private String rawSummary;
    private String issueStatus;
    private OffsetDateTime detectedAt;
}
