package org.dromara.fund.domain.enums;

import lombok.Getter;

/**
 * 数据质量问题处理状态。
 */
@Getter
public enum FundQualityIssueStatusEnum {

    OPEN("OPEN", "待处理"),
    IGNORED("IGNORED", "已忽略"),
    RESOLVED("RESOLVED", "已解决");

    private final String code;
    private final String desc;

    FundQualityIssueStatusEnum(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }
}
