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
 * 基金历史 NAV 位置的当前计算结果。
 *
 * <p>同一量化发布版本下，每只基金只有一条结果；新发布版本保留独立的结果集。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("fund_nav_position")
public class FundNavPosition extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId
    private Long id;
    private String fundCode;
    private LocalDate tradeDate;
    private OffsetDateTime calculatedAt;
    private String status;
    private String algorithmVersion;
    private Long configReleaseVersion;
    private String configReleaseChecksum;
    private Long navPositionConfigVersion;
    private String navPositionConfigChecksum;
    private String inputDataVersion;
    private BigDecimal navPercentile;
    private BigDecimal currentDrawdown;
    private BigDecimal ma60Deviation;
    private BigDecimal ma120Deviation;
    private BigDecimal ma250Deviation;
    private BigDecimal navPositionScore;
    private String navPositionRegion;
    private Integer sampleCount;
    private LocalDate effectiveStartDate;
    private LocalDate effectiveEndDate;
    private String reasonsJson;
    private String indicatorsJson;
}
