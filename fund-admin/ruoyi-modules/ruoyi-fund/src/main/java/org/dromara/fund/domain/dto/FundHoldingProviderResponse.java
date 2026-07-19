package org.dromara.fund.domain.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.math.BigDecimal;

/**
 * fund-quant 基金股票持仓响应。
 */
@Data
public class FundHoldingProviderResponse {

    /** 基金代码。 */
    @JsonProperty("fund_code")
    private String fundCode;
    /** 股票代码。 */
    @JsonProperty("stock_code")
    private String stockCode;
    /** 股票名称。 */
    @JsonProperty("stock_name")
    private String stockName;
    /** 占基金净值比例，百分数口径。 */
    private BigDecimal weight;
    /** 公开披露报告期。 */
    @JsonProperty("report_period")
    private String reportPeriod;
}
