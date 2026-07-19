package org.dromara.fund.domain.enums;

import lombok.Getter;

/**
 * 基金同步类型。
 */
@Getter
public enum FundSyncTypeEnum {

    FULL_INIT("FULL_INIT", "全量初始化"),
    INCREMENTAL("INCREMENTAL", "增量同步"),
    LAZY_LOAD("LAZY_LOAD", "按需懒加载"),
    NAV_BACKFILL("NAV_BACKFILL", "历史净值回填"),
    HOLDING_BACKFILL("HOLDING_BACKFILL", "持仓回填");

    private final String code;
    private final String desc;

    FundSyncTypeEnum(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }
}
