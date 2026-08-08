package org.dromara.fund.domain.vo;

import lombok.Data;

/** 两个版本之间的单个 JSON 字段差异。 */
@Data
public class QuantConfigDiffEntryVo {
    private String fieldPath;
    private String before;
    private String after;
    private String type;
}
