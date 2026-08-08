package org.dromara.fund.domain.vo;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;

/**
 * 基金列表视图。
 */
@Data
public class FundListVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String fundCode;
    private String fundName;
    private String fundType;
    private String source;
    private OffsetDateTime sourceUpdatedAt;
    private LocalDate asOfDate;
    private LocalDate businessDate;
    private String dataVersion;
    private String qualityStatus;
    private String qualityReason;
    private BigDecimal latestNav;
    private LocalDate navDate;
    private String latestNavDataVersion;
    private String latestNavQualityStatus;
    private String latestNavQualityReason;
    private LocalDate latestHoldingReportDate;
    private String latestHoldingDataVersion;
    private String latestHoldingQualityStatus;
    private String syncState;
    private String syncStatus;
    private String syncFetchBatchId;
    private BigDecimal estimateNav;
    private BigDecimal estimateGrowthRate;
    private LocalDateTime estimateTime;
    private String estimateSourceStatus;
    private BigDecimal estimateHoldingCoverageRate;
    private BigDecimal estimateQuoteCoverageRate;
    private Integer estimateMissingQuoteCount;
    private String estimateStatusReason;
    /** 当前量化发布版本下已缓存的历史 NAV 位置；列表不会逐行触发计算。 */
    private String navPositionStatus;
    private BigDecimal navPositionScore;
    private String navPositionRegion;
    private LocalDate navPositionTradeDate;
    private LocalDateTime navPositionCalculatedAt;
    @JsonProperty("isStale")
    private boolean stale;
}
