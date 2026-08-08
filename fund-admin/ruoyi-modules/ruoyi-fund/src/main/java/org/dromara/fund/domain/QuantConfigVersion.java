package org.dromara.fund.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;

import java.time.OffsetDateTime;

/** 量化配置草稿或已验证的不可变版本。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("quant_config_version")
public class QuantConfigVersion extends BaseEntity {
    @TableId
    private Long id;
    private String configCode;
    private Integer configVersion;
    private Integer schemaVersion;
    private String status;
    private String configJson;
    private String checksum;
    private OffsetDateTime effectiveFrom;
    private Long revision;
    private String remark;
}
