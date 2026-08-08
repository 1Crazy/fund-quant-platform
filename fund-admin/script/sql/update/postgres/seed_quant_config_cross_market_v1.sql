-- 跨市场基金首版量化配置种子（A 股、港股、纳斯达克及标普 500 暴露）。
-- 前置：已执行 update_quant_config_center_v1.sql；本脚本只创建 DRAFT，绝不直接发布。
-- 发布名：cross-market-fund-v1；建议生效时间：2026-08-10T09:30:00+08:00。
-- 执行后由 Java 管理 API 逐项校验，再调用 Python 兼容性接口并原子发布。

BEGIN;

INSERT INTO quant_config_version (
    id, config_code, config_version, schema_version, status, config_json, checksum,
    effective_from, revision, create_dept, create_by, create_time, update_time, remark
) VALUES
    (1702001, 'GLOBAL_CONVENTIONS', 1, 1, 'DRAFT',
     '{"annual_trading_days":252,"ddof":1,"decimal_scale":6,"drawdown_sign":"NEGATIVE_PERCENT","market_conventions":{"CN":{"annual_trading_days":242,"close_time":"15:00","timezone":"Asia/Shanghai"},"HK":{"annual_trading_days":250,"close_time":"16:10","timezone":"Asia/Hong_Kong"},"US":{"annual_trading_days":252,"close_time":"16:00","timezone":"America/New_York"}},"percentage_unit":"PERCENT_POINT","risk_free_rate":0,"risk_free_rate_source":"ZERO_EXCESS_RETURN_BASELINE_FOR_UNHEDGED_MULTI_CURRENCY_V1","rounding_mode":"HALF_UP","timezone":"UTC"}'::jsonb,
     '8e590cd5ae981fbc4fd82fc5a4941e70a28c0170f327c2e62d7b0e09a9503d55',
     '2026-08-10T09:30:00+08:00', 0, 103, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP,
     'D-011：跨市场时区、交易日与零超额收益基线'),
    (1702002, 'ESTIMATE', 1, 1, 'DRAFT',
     '{"max_quote_age_seconds":90,"min_holding_coverage_percent":60}'::jsonb,
     '378fc93712ed1db03972f1cebba6c5fc7c58e839d724203e491d8ef6301568cb',
     '2026-08-10T09:30:00+08:00', 0, 103, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP,
     'D-005：至少 60% 公开持仓覆盖率，90 秒行情年龄上限'),
    (1702003, 'TREND', 1, 1, 'DRAFT',
     '{"signal_threshold_percent":1,"windows":[20,60,120]}'::jsonb,
     '23ed7c0b995c19a61be94adfae887f9a13607d5a766ac78457de359941eb9b50',
     '2026-08-10T09:30:00+08:00', 0, 103, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP,
     '20/60/120 交易日趋势窗口，1 个百分点确认阈值'),
    (1702004, 'MOVING_AVERAGE', 1, 1, 'DRAFT',
     '{"deviation_threshold_percent":5,"windows":[20,60,120,250]}'::jsonb,
     '0ac9df24e2bb096f4ab508547358fe2746e313800bdb59ce2d22ab3163b77614',
     '2026-08-10T09:30:00+08:00', 0, 103, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP,
     '20/60/120/250 交易日均线，5 个百分点偏离阈值'),
    (1702005, 'RSI_MACD', 1, 1, 'DRAFT',
     '{"macd_fast":12,"macd_signal":9,"macd_slow":26,"rsi_overbought":70,"rsi_oversold":30,"rsi_period":14}'::jsonb,
     'e34ce927291a6dfdc593a5e1b428a8788a008c55a152662faacace2387ca5c83',
     '2026-08-10T09:30:00+08:00', 0, 103, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP,
     'RSI 14 与 MACD 12/26/9'),
    (1702006, 'NAV_POSITION', 1, 1, 'DRAFT',
     '{"history_window":756,"min_sample_size":252,"region_thresholds":[25,50,75]}'::jsonb,
     '2ca65c8300d1a8b1a8447233ed4678feb1edfc9cf7fa9d361ae9927c87491691',
     '2026-08-10T09:30:00+08:00', 0, 103, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP,
     '三年历史位置窗口，四分位区间阈值'),
    (1702007, 'FACTOR', 1, 1, 'DRAFT',
     '{"missing_value_policy":"MEDIAN_BY_CATEGORY","standardization":"Z_SCORE_WINSORIZED","weights":{"max_drawdown":20,"nav_position":15,"return_12m":25,"risk_adjusted_return":25,"trend":15}}'::jsonb,
     '99b88c65eb2edb1836f44fde1eb3b8be4ce06f0f5e7b0480b7a70e129ab59fa9',
     '2026-08-10T09:30:00+08:00', 0, 103, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP,
     '收益与风险调整收益各 25%，回撤 20%，趋势与历史位置各 15%'),
    (1702008, 'FUND_RISK', 1, 1, 'DRAFT',
     '{"grade_thresholds":[12,25],"min_sample_size":60,"risk_unit":"ANNUALIZED_VOLATILITY_PERCENT","window":252}'::jsonb,
     'f67a9c1488b29a4a8a89f4bf0d106c208892b99b67f59754737594cc41a24e40',
     '2026-08-10T09:30:00+08:00', 0, 103, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP,
     '252 日年化波动率；低/中/高风险分界为 12% 和 25%'),
    (1702009, 'PORTFOLIO_RISK', 1, 1, 'DRAFT',
     '{"max_missing_percent":5,"var_confidence":0.99,"var_horizon_days":1,"var_method":"HISTORICAL","window":252}'::jsonb,
     '9ebff0fec4a6625768a1f14cb980635b937212826cf3e467cd006d31ae964477',
     '2026-08-10T09:30:00+08:00', 0, 103, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP,
     '99% 单日历史 VaR，252 日窗口，数据缺失不超过 5%'),
    (1702010, 'BACKTEST', 1, 1, 'DRAFT',
     '{"execution_cost_mode":"MARKET_SPECIFIC_REQUIRED","fee_rate_percent":0,"market_costs":{"CN":{"buy_fee_percent":0.03,"sell_fee_percent":0.08,"slippage_percent":0.05},"HK":{"buy_fee_percent":0.11,"sell_fee_percent":0.11,"slippage_percent":0.08},"US":{"buy_fee_percent":0,"sell_fee_percent":0.0021,"slippage_percent":0.03}},"slippage_percent":0}'::jsonb,
     '59c81aa957f5a590707982055ce3166aaa95f9227acee86a4011ccd7c57f6d33',
     '2026-08-10T09:30:00+08:00', 0, 103, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP,
     '仅适用于底层股票/ETF 执行；必须按市场成本，基金申赎费另行建模')
ON CONFLICT (config_code, config_version) DO NOTHING;

COMMIT;
