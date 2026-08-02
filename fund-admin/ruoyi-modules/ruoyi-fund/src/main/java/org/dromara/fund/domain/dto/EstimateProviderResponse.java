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
    private String source;
    /** 已匹配实时行情的直接股票持仓覆盖率，百分数口径。 */
    private BigDecimal holdingCoverageRate;
    private String reportPeriod;
    /** 本次估值使用的逐持仓实时行情与贡献。 */
    private List<EstimateHoldingContributionResponse> contributions = List.of();
}
