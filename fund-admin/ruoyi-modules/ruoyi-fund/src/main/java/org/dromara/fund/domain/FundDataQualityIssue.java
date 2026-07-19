package org.dromara.fund.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;

import java.io.Serial;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * 基金数据质量问题记录。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("fund_data_quality_issue")
public class FundDataQualityIssue extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键。 */
    @TableId
    private Long id;
    /** 数据集。 */
    private String dataset;
    /** 数据来源。 */
    private String source;
    /** 来源记录时间。 */
    private OffsetDateTime sourceTime;
    /** 业务日期。 */
    private LocalDate businessDate;
    /** 关联同步运行 ID。 */
    private Long syncRunId;
    /** 抓取批次 ID。 */
    private String fetchBatchId;
    /** 关联数据版本。 */
    private String dataVersion;
    /** 问题记录标准化摘要校验和。 */
    private String checksum;
    /** 业务记录键。 */
    private String recordKey;
    /** 问题导致的数据质量状态。 */
    private String qualityStatus;
    /** 原因码。 */
    private String reasonCode;
    /** 已脱敏原始值摘要。 */
    private String rawSummary;
    /** 问题状态。 */
    private String issueStatus;
    /** 发现时间。 */
    private OffsetDateTime detectedAt;
}
