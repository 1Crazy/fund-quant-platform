package org.dromara.fund.domain.dto;

import lombok.Data;

import java.time.OffsetDateTime;

/**
 * fund-quant 批量同步元数据。
 */
@Data
public class FundSyncBatchMetaDto {

    private String batchId;
    private String dataset;
    private String source;
    private OffsetDateTime sourceTime;
    private OffsetDateTime fetchedAt;
    private String qualityStatus;
    private String checksum;
    private String dataVersion;
    private Integer successCount;
    private Integer rejectedCount;
    private Integer failedCount;
    private Integer totalCount;
    private Integer page;
    private Integer pageSize;
    private Boolean hasMore;
    private Integer nextPage;
}
