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
    /** 上游数据更新时间。 */
    private OffsetDateTime sourceUpdatedAt;
    /** 逻辑删除标志：0 存在，1 删除。 */
    @TableLogic
    private Long delFlag;
}
