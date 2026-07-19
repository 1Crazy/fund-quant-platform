package org.dromara.fund.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * 基金详情视图。
 */
@Data
public class FundDetailVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String fundCode;
    private String fundName;
    private String fundType;
    private String managerName;
    /** 基金托管人名称。 */
    private String custodianName;
    private LocalDate establishDate;
    private String benchmark;
    private String riskLevel;
    private BigDecimal fundScale;
    private BigDecimal latestNav;
    private LocalDate navDate;
    private String source;
    private OffsetDateTime sourceUpdatedAt;
    private LocalDate asOfDate;
    private String dataVersion;
    private String qualityStatus;
    private String qualityReason;
    private String latestNavDataVersion;
    private String latestNavQualityStatus;
    private String latestNavQualityReason;
    private LocalDate latestHoldingReportDate;
    private String latestHoldingDataVersion;
    private String latestHoldingQualityStatus;
    private String syncState;
    private String syncStatus;
    private String syncFetchBatchId;
    private FundEstimateVo estimate;
    private List<FundNavPointVo> navSeries = List.of();
    /** 最近同步过程中隔离的数据质量问题。 */
    private List<FundDataQualityIssueVo> qualityIssues = List.of();
    /** 最新公开报告期的股票持仓。 */
    private List<FundHoldingVo> holdings = List.of();
    /** 持仓披露或数据可用性说明。 */
    private String holdingNote;
    /** 当前展示的直接股票持仓合计比例，百分数口径。 */
    private BigDecimal holdingCoverageRate;
}
