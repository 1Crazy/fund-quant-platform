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
    private String source;
    /** 已匹配实时行情的直接股票持仓覆盖率，百分数口径。 */
    private BigDecimal holdingCoverageRate;
    private String reportPeriod;
    /** 本次估值使用的逐持仓实时行情与贡献；历史快照不包含该瞬时数据。 */
    private List<FundEstimateContributionVo> contributions = List.of();
    @JsonProperty("isStale")
    private boolean stale;
    private String sourceStatus;
}
