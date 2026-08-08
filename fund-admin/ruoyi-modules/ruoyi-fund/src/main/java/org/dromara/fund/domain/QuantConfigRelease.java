package org.dromara.fund.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;

import java.time.OffsetDateTime;

/** 已发布的原子量化配置清单。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("quant_config_release")
public class QuantConfigRelease extends BaseEntity {
    @TableId
    private Long id;
    private Long releaseVersion;
    private String status;
    private String checksum;
    private OffsetDateTime effectiveFrom;
    private Long publishedBy;
    private OffsetDateTime publishedAt;
    private Long rollbackOfReleaseVersion;
    private String changeSummary;
}
