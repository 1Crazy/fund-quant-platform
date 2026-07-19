package org.dromara.fund.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;

import java.io.Serial;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * 基金披露持仓当前投影。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("fund_holding")
public class FundHolding extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键。 */
    @TableId
    private Long id;
    /** 基金代码。 */
    private String fundCode;
    /** 披露报告日期。 */
    private LocalDate reportDate;
    /** 股票代码。 */
    private String stockCode;
    /** 股票名称。 */
    private String stockName;
    /** 占基金净值比例，百分数口径。 */
    private BigDecimal disclosedWeight;
    /** 持仓市值。 */
    private BigDecimal marketValue;
    /** 披露排名。 */
    private Integer holdingRank;
    /** 数据来源。 */
    private String source;
    /** 上游来源数据时间。 */
    private OffsetDateTime sourceTime;
    /** 抓取批次 ID。 */
    private String fetchBatchId;
    /** 数据版本。 */
    private String dataVersion;
    /** 当前记录校验和。 */
    private String checksum;
    /** 数据质量状态。 */
    private String qualityStatus;
    /** 数据质量原因摘要。 */
    private String qualityReason;
}
