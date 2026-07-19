package org.dromara.fund.domain.enums;

import lombok.Getter;

/**
 * 基金同步运行状态。
 */
@Getter
public enum FundSyncStatusEnum {

    PENDING("PENDING", "待执行"),
    RUNNING("RUNNING", "运行中"),
    SUCCESS("SUCCESS", "成功"),
    PARTIAL_SUCCESS("PARTIAL_SUCCESS", "部分成功"),
    FAILED("FAILED", "失败"),
    CANCELLED("CANCELLED", "已取消");

    private final String code;
    private final String desc;

    FundSyncStatusEnum(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }
}
