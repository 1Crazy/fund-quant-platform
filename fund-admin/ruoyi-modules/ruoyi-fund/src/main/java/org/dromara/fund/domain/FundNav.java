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
 * 基金历史净值。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("fund_nav")
public class FundNav extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键。 */
    @TableId
    private Long id;
    /** 基金代码。 */
    private String fundCode;
    /** 净值日期。 */
    private LocalDate navDate;
    /** 单位净值。 */
    private BigDecimal unitNav;
    /** 累计净值。 */
    private BigDecimal accumulatedNav;
    /** 单日增长率，百分数口径。 */
    private BigDecimal dailyGrowthRate;
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
