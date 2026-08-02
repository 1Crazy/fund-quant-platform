package org.dromara.fund.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 详情页手动刷新时展示的持仓实时行情。
 */
@Data
public class FundHoldingQuoteVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String stockCode;
    private String stockName;
    private BigDecimal weight;
    private BigDecimal changePercent;
    private LocalDateTime quoteTime;
}
