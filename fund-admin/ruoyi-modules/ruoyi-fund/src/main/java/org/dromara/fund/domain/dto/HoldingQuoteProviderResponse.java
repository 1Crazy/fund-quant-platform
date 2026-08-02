package org.dromara.fund.domain.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * fund-quant 持仓实时行情响应；仅描述披露持仓，不等于基金完整资产。
 */
@Data
public class HoldingQuoteProviderResponse {

    private String stockCode;
    private String stockName;
    private BigDecimal weight;
    private BigDecimal changePercent;
    private OffsetDateTime quoteTime;
}
