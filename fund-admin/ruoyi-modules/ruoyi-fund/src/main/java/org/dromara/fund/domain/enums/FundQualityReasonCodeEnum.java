package org.dromara.fund.domain.enums;

import lombok.Getter;

/**
 * 基金数据质量问题原因码。
 */
@Getter
public enum FundQualityReasonCodeEnum {

    INVALID_FUND_CODE("INVALID_FUND_CODE", "基金代码无效"),
    INVALID_NAV("INVALID_NAV", "净值无效"),
    FUTURE_BUSINESS_DATE("FUTURE_BUSINESS_DATE", "业务日期晚于当前日期"),
    DUPLICATE_CONFLICT("DUPLICATE_CONFLICT", "重复业务键数据冲突"),
    INVALID_HOLDING_RATIO("INVALID_HOLDING_RATIO", "持仓权重超出范围"),
    DATA_PROVIDER_SCHEMA_CHANGED("DATA_PROVIDER_SCHEMA_CHANGED", "上游字段结构漂移"),
    EMPTY_DATA("EMPTY_DATA", "上游无可用数据"),
    UPSTREAM_FAILED("UPSTREAM_FAILED", "上游调用失败");

    private final String code;
    private final String desc;

    FundQualityReasonCodeEnum(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }
}
