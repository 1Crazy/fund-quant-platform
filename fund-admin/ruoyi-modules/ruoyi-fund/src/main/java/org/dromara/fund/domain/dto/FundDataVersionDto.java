package org.dromara.fund.domain.dto;

import lombok.Data;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * 基金数据版本元数据。
 */
@Data
public class FundDataVersionDto {

    private String dataset;
    private String fundCode;
    private LocalDate businessDate;
    private String fetchBatchId;
    private String dataVersion;
    private String checksum;
    private String qualityStatus;
    private String qualityReason;
    private String source;
    private OffsetDateTime sourceTime;
}
