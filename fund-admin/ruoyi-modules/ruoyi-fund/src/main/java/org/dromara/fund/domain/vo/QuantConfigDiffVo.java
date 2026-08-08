package org.dromara.fund.domain.vo;

import lombok.Data;

import java.util.List;

/** 服务端生成的字段级差异，不在浏览器端解释量化公式。 */
@Data
public class QuantConfigDiffVo {
    private Long baseId;
    private Long targetId;
    private String baseChecksum;
    private String targetChecksum;
    private List<QuantConfigDiffEntryVo> changes;
}
