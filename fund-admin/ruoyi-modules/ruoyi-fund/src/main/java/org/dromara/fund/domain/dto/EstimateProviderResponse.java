package org.dromara.fund.domain.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

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
}
