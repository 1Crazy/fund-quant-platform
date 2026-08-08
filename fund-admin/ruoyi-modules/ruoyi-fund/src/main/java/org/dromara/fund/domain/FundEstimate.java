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
    /** 数据状态：NORMAL、PARTIAL、UNSUPPORTED、STALE、FAILED、UPSTREAM_FAILED。 */
    private String sourceStatus;
    /** 状态原因码或受控摘要。 */
    private String statusReason;
    /** 公开披露股票持仓的总覆盖率，百分数口径。 */
    private BigDecimal holdingCoverageRate;
    /** 有可接受实时行情的披露股票权重，百分数口径。 */
    private BigDecimal quoteCoverageRate;
    /** 缺少或过期行情的披露持仓数量。 */
    private Integer missingQuoteCount;
    /** 最早可接受成分报价时间，包含时区。 */
    private OffsetDateTime quoteTime;
    /** 持仓披露报告日期。 */
    private LocalDate holdingReportDate;
    /** 上游持仓报告期原始标签。 */
    private String holdingReportPeriod;
    /** 输入共享基金数据版本。 */
    private String inputDataVersion;
    /** 估值算法实现版本。 */
    private String algorithmVersion;
    /** 估值所属交易日。 */
    private LocalDate tradeDate;
    /** 生成该快照的不可变量化配置发布版本。 */
    private Long configReleaseVersion;
    /** 生成该快照的量化配置发布校验和。 */
    private String configReleaseChecksum;
    /** 生成该快照的 ESTIMATE 配置组版本。 */
    private Long estimateConfigVersion;
    /** 生成该快照的 ESTIMATE 配置组校验和。 */
    private String estimateConfigChecksum;
}
