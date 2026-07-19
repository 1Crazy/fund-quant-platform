package org.dromara.fund.domain.enums;

import lombok.Getter;

/**
 * 基金数据质量状态。
 */
@Getter
public enum FundDataQualityStatusEnum {

    NORMAL("NORMAL", "正常"),
    PARTIAL("PARTIAL", "部分可用"),
    EMPTY("EMPTY", "空数据"),
    STALE("STALE", "过期"),
    FAILED("FAILED", "失败"),
    REJECTED("REJECTED", "已拒绝");

    private final String code;
    private final String desc;

    FundDataQualityStatusEnum(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }
}
