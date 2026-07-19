package org.dromara.fund.domain.bo;

import lombok.Data;

import java.time.OffsetDateTime;

/**
 * 基金数据质量问题查询条件。
 */
@Data
public class FundDataQualityIssueQueryBo {

    private String dataset;
    private Long syncRunId;
    private String fetchBatchId;
    private String reasonCode;
    private String issueStatus;
    private OffsetDateTime detectedAtStart;
    private OffsetDateTime detectedAtEnd;
}
