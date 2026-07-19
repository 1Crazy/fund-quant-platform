package org.dromara.fund.domain.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * fund-quant 历史净值响应。
 */
@Data
public class FundNavProviderResponse {

    @JsonProperty("fund_code")
    private String fundCode;
    private LocalDate date;
    private BigDecimal nav;
    @JsonProperty("accumulated_nav")
    private BigDecimal accumulatedNav;
    @JsonProperty("growth_rate")
    private BigDecimal growthRate;
}
