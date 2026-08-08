package org.dromara.fund.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/** 发布清单中一项配置版本的不可变引用。 */
@Data
@TableName("quant_config_release_item")
public class QuantConfigReleaseItem {
    @TableId
    private Long id;
    private Long releaseId;
    private String configCode;
    private Long configVersionId;
    private Integer configVersion;
    private String configChecksum;
    private Integer schemaVersion;
}
