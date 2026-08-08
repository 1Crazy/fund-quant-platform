package org.dromara.fund.domain.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;

/**
 * 盘中估值的来源可用性状态。
 */
@Getter
@RequiredArgsConstructor
public enum FundEstimateSourceStatusEnum {

    NORMAL("NORMAL"),
    PARTIAL("PARTIAL"),
    UNSUPPORTED("UNSUPPORTED"),
    STALE("STALE"),
    FAILED("FAILED"),
    UPSTREAM_FAILED("UPSTREAM_FAILED");

    private final String code;

    public static boolean isSupported(String code) {
        return Arrays.stream(values()).anyMatch(status -> status.code.equals(code));
    }
}
