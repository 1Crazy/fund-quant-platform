package org.dromara.fund.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 页面展示的单只持仓盘中行情与估值贡献。
 */
@Data
public class FundEstimateContributionVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String stockCode;
    private String stockName;
    private BigDecimal weight;
    private BigDecimal changePercent;
    private BigDecimal contribution;
    private LocalDateTime quoteTime;
}
