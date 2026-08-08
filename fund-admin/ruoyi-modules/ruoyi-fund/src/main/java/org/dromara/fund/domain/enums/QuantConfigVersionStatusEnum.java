package org.dromara.fund.domain.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 配置版本生命周期；VALIDATED 与 PUBLISHED 均不可原地修改。 */
@Getter
@RequiredArgsConstructor
public enum QuantConfigVersionStatusEnum {
    DRAFT("DRAFT"),
    VALIDATED("VALIDATED"),
    PUBLISHED("PUBLISHED");

    private final String code;

    public static boolean supports(String code) {
        for (QuantConfigVersionStatusEnum value : values()) {
            if (value.code.equals(code)) {
                return true;
            }
        }
        return false;
    }
}
