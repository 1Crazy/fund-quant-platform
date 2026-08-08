package org.dromara.fund.domain.bo;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 创建或编辑量化配置草稿的输入。 */
@Data
public class QuantConfigDraftBo {
    @NotBlank(message = "配置分组不能为空")
    @Pattern(regexp = "^(GLOBAL_CONVENTIONS|ESTIMATE|TREND|MOVING_AVERAGE|RSI_MACD|NAV_POSITION|FACTOR|FUND_RISK|PORTFOLIO_RISK|BACKTEST)$", message = "不支持的配置分组")
    private String configCode;

    @NotNull(message = "结构版本不能为空")
    @Min(value = 1, message = "结构版本必须大于 0")
    private Integer schemaVersion;

    @NotBlank(message = "配置 JSON 不能为空")
    @Size(max = 20_000, message = "配置 JSON 不能超过 20000 个字符")
    private String configJson;

    @NotNull(message = "草稿修订版本不能为空")
    @Min(value = 0, message = "草稿修订版本不能为负数")
    private Long revision = 0L;

    @Size(max = 500, message = "备注不能超过 500 个字符")
    private String remark;
}
