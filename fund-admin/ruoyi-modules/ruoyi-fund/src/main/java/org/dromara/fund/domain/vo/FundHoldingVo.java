package org.dromara.fund.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

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
    /** 占基金净值比例，百分数口径。 */
    private BigDecimal disclosedWeight;
    /** 持仓市值。 */
    private BigDecimal marketValue;
    /** 披露排名。 */
    private Integer rankNo;
    /** 披露排名。 */
    private Integer holdingRank;
    /** 公开披露报告期。 */
    private String reportPeriod;
    /** 披露报告日期。 */
    private LocalDate reportDate;
    /** 数据来源。 */
    private String source;
    /** 来源数据时间。 */
    private OffsetDateTime sourceTime;
    /** 数据版本。 */
    private String dataVersion;
    /** 数据质量状态。 */
    private String qualityStatus;
}
