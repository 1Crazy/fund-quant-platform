package org.dromara.fund.domain.dto;

import lombok.Data;

import java.time.OffsetDateTime;

/**
 * fund-quant 返回的数据质量问题。
 */
@Data
public class FundProviderQualityIssueDto {

    private String dataset;
    private String batchId;
    private String recordKey;
    private String reasonCode;
    private String message;
    private String rawDigest;
    private OffsetDateTime discoveredAt;
}
