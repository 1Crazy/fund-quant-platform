package org.dromara.fund.domain.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 支持原子发布的量化配置分组。 */
@Getter
@RequiredArgsConstructor
public enum QuantConfigCodeEnum {
    GLOBAL_CONVENTIONS("GLOBAL_CONVENTIONS", "全局口径"),
    ESTIMATE("ESTIMATE", "盘中估值"),
    TREND("TREND", "趋势参数"),
    MOVING_AVERAGE("MOVING_AVERAGE", "均线参数"),
    RSI_MACD("RSI_MACD", "RSI / MACD"),
    NAV_POSITION("NAV_POSITION", "历史位置"),
    FACTOR("FACTOR", "多因子权重"),
    FUND_RISK("FUND_RISK", "基金风险"),
    PORTFOLIO_RISK("PORTFOLIO_RISK", "组合风险"),
    BACKTEST("BACKTEST", "回测参数");

    private final String code;
    private final String description;

    public static boolean supports(String code) {
        for (QuantConfigCodeEnum value : values()) {
            if (value.code.equals(code)) {
                return true;
            }
        }
        return false;
    }
}
