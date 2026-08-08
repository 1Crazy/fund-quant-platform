package org.dromara.fund.domain.vo;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 实时估值视图。
 */
@Data
public class FundEstimateVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String fundCode;
    private BigDecimal estimateNav;
    private BigDecimal estimateGrowthRate;
    private BigDecimal previousNav;
    private LocalDate previousNavDate;
    private LocalDateTime estimateTime;
    private LocalDateTime quoteTime;
    private String source;
    /** 公开披露股票持仓的总覆盖率，百分数口径。 */
    private BigDecimal holdingCoverageRate;
    /** 有可接受实时行情的披露股票权重，百分数口径。 */
    private BigDecimal quoteCoverageRate;
    private Integer missingQuoteCount;
    private String statusReason;
    private LocalDate holdingReportDate;
    private String reportPeriod;
    private String inputDataVersion;
    private String algorithmVersion;
    private LocalDate tradeDate;
    private Long configReleaseVersion;
    private String configReleaseChecksum;
    private Long estimateConfigVersion;
    private String estimateConfigChecksum;
    /** 本次估值使用的逐持仓实时行情与贡献；历史快照不包含该瞬时数据。 */
    private List<FundEstimateContributionVo> contributions = List.of();
    @JsonProperty("isStale")
    private boolean stale;
    private String sourceStatus;
}
