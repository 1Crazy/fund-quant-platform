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
 * 基金盘中估值快照。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("fund_estimate")
public class FundEstimate extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键。 */
    @TableId
    private Long id;
    /** 基金代码。 */
    private String fundCode;
    /** 估值时间，包含时区。 */
    private OffsetDateTime estimateTime;
    /** 盘中估算净值。 */
    private BigDecimal estimateNav;
    /** 盘中估算涨跌幅，百分数口径。 */
    private BigDecimal estimateGrowthRate;
    /** 上一公布日单位净值。 */
    private BigDecimal previousNav;
    /** 上一公布净值日期。 */
    private LocalDate previousNavDate;
    /** 数据来源。 */
    private String source;
    /** 数据状态：NORMAL、STALE、FAILED。 */
    private String sourceStatus;
}
