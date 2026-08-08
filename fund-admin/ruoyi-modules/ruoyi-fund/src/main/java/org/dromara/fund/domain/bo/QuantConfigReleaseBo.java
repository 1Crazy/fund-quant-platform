package org.dromara.fund.domain.bo;

import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.List;

/** 原子发布已验证配置版本的输入。 */
@Data
public class QuantConfigReleaseBo {
    /** 发布时提供；回滚时由目标发布版本的不可变条目确定。 */
    private List<Long> configVersionIds;

    @NotNull(message = "生效时间不能为空")
    @FutureOrPresent(message = "生效时间不能早于当前时间")
    private OffsetDateTime effectiveFrom;

    @jakarta.validation.constraints.Size(max = 500, message = "发布说明不能超过 500 个字符")
    private String changeSummary;
}
