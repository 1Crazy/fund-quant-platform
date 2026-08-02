package org.dromara.fund.domain.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * fund-quant 返回的单只持仓实时行情及对基金估算涨跌的贡献。
 */
@Data
public class EstimateHoldingContributionResponse {

    private String stockCode;
    private String stockName;
    private BigDecimal weight;
    private BigDecimal changePercent;
    private BigDecimal contribution;
    private OffsetDateTime quoteTime;
}
