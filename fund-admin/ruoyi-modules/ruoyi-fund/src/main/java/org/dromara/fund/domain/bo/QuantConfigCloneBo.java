package org.dromara.fund.domain.bo;

import jakarta.validation.constraints.Size;
import lombok.Data;

/** 从既有版本复制新的可编辑草稿，不修改原版本。 */
@Data
public class QuantConfigCloneBo {
    @Size(max = 500, message = "草稿说明不能超过 500 个字符")
    private String remark;
}
