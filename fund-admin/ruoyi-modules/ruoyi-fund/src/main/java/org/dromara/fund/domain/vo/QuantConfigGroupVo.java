package org.dromara.fund.domain.vo;

import lombok.Data;

/** 配置分组目录。数学参数本身不在目录中提供默认值。 */
@Data
public class QuantConfigGroupVo {
    private String configCode;
    private String displayName;
    private Integer schemaVersion;
}
