package org.dromara.fund.domain.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * 上游实时估值标准响应。
 */
@Data
public class EstimateProviderResponse {

    private String fundCode;
    private BigDecimal estimateNav;
    private BigDecimal estimateGrowthRate;
    private BigDecimal previousNav;
    private LocalDate previousNavDate;
    private OffsetDateTime estimateTime;
    private OffsetDateTime quoteTime;
    private String source;
    /** 公开披露股票持仓的总覆盖率，百分数口径。 */
    private BigDecimal holdingCoverageRate;
    /** 有可接受实时行情的披露股票权重，百分数口径。 */
    private BigDecimal quoteCoverageRate;
    private Integer missingQuoteCount;
    private String sourceStatus;
    private String statusReason;
    private LocalDate holdingReportDate;
    private String reportPeriod;
    private String inputDataVersion;
    private String algorithmVersion;
    private LocalDate tradeDate;
    /** 本次估值固定使用的量化配置发布版本。 */
    private Long configReleaseVersion;
    /** 本次估值固定使用的量化配置发布版本校验和。 */
    private String configReleaseChecksum;
    /** 本次估值固定使用的 ESTIMATE 配置组版本。 */
    private Long estimateConfigVersion;
    /** 本次估值固定使用的 ESTIMATE 配置组校验和。 */
    private String estimateConfigChecksum;
    /** 本次估值使用的逐持仓实时行情与贡献。 */
    private List<EstimateHoldingContributionResponse> contributions = List.of();
}
