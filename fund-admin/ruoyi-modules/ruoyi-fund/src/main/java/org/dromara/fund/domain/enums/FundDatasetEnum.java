package org.dromara.fund.domain.enums;

import lombok.Getter;

/**
 * 基金数据中心数据集。
 */
@Getter
public enum FundDatasetEnum {

    FUND_INFO("FUND_INFO", "基金主数据"),
    FUND_NAV("FUND_NAV", "历史净值"),
    FUND_HOLDING("FUND_HOLDING", "披露持仓"),
    FUND_CATALOG("FUND_CATALOG", "基金目录");

    private final String code;
    private final String desc;

    FundDatasetEnum(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }
}
