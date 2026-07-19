package org.dromara.fund.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 基金股票持仓视图。
 */
@Data
public class FundHoldingVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 股票代码。 */
    private String stockCode;
    /** 股票名称。 */
    private String stockName;
    /** 占基金净值比例，百分数口径。 */
    private BigDecimal weight;
    /** 公开披露报告期。 */
    private String reportPeriod;
}
