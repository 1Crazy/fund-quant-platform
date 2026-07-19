package org.dromara.fund.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;

import java.io.Serial;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * 基金基础信息。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("fund_info")
public class FundInfo extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键。 */
    @TableId
    private Long id;
    /** 基金代码。 */
    private String fundCode;
    /** 基金名称。 */
    private String fundName;
    /** 基金类型。 */
    private String fundType;
    /** 基金名称拼音缩写。 */
    private String pinyinAbbr;
    /** 基金管理人名称。 */
    private String managerName;
    /** 基金托管人名称。 */
    private String custodianName;
    /** 基金成立日期。 */
    private LocalDate establishDate;
    /** 业绩比较基准。 */
    private String benchmark;
    /** 风险等级。 */
    private String riskLevel;
    /** 基金规模，单位亿元。 */
    private BigDecimal fundScale;
    /** 状态：0 正常，1 停用。 */
    private String status;
    /** 数据来源。 */
    private String source;
    /** 上游数据更新时间（现有 fund_info.source_updated_at 列）。 */
    private OffsetDateTime sourceUpdatedAt;
    /** 数据业务日期。 */
    private LocalDate businessDate;
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
    /** 逻辑删除标志：0 存在，1 删除。 */
    @TableLogic
    private Long delFlag;
}
