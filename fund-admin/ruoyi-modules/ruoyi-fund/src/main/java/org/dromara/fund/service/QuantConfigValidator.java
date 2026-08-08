package org.dromara.fund.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.dromara.fund.domain.enums.QuantConfigCodeEnum;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/** 配置结构的服务端校验，不提供任何算法参数默认值。 */
@Component
public class QuantConfigValidator {

    public List<String> validate(String configCode, int schemaVersion, JsonNode root) {
        List<String> errors = new ArrayList<>();
        if (!QuantConfigCodeEnum.supports(configCode)) {
            errors.add("不支持的配置分组: " + configCode);
            return errors;
        }
        if (!supportsSchema(configCode, schemaVersion)) {
            errors.add("不支持的配置结构版本: " + schemaVersion);
            return errors;
        }
        switch (QuantConfigCodeEnum.valueOf(configCode)) {
            case GLOBAL_CONVENTIONS -> validateGlobal(root, errors);
            case ESTIMATE -> validateEstimate(root, schemaVersion, errors);
            case TREND -> validateTrend(root, errors);
            case MOVING_AVERAGE -> validateMovingAverage(root, errors);
            case RSI_MACD -> validateRsiMacd(root, errors);
            case NAV_POSITION -> validateNavPosition(root, errors);
            case FACTOR -> validateFactor(root, errors);
            case FUND_RISK -> validateRisk(root, errors, "window", "grade_thresholds");
            case PORTFOLIO_RISK -> validatePortfolioRisk(root, errors);
            case BACKTEST -> validateBacktest(root, errors);
        }
        return errors;
    }

    private void validateGlobal(JsonNode root, List<String> errors) {
        requireText(root, "timezone", errors);
        requireText(root, "percentage_unit", errors);
        requireText(root, "drawdown_sign", errors);
        requireText(root, "risk_free_rate_source", errors);
        positiveInteger(root, "annual_trading_days", errors);
        number(root, "risk_free_rate", errors);
        integerIn(root, "ddof", 0, 1, errors);
        positiveInteger(root, "decimal_scale", errors);
        requireText(root, "rounding_mode", errors);
        validateMarketConventions(root, errors);
    }

    private boolean supportsSchema(String configCode, int schemaVersion) {
        return schemaVersion == 1 || ("ESTIMATE".equals(configCode) && schemaVersion == 2);
    }

    private void validateEstimate(JsonNode root, int schemaVersion, List<String> errors) {
        Set<String> required = schemaVersion == 1
            ? Set.of("min_holding_coverage_percent", "max_quote_age_seconds")
            : Set.of(
                "min_holding_coverage_percent",
                "max_quote_age_seconds",
                "nav_decimal_scale",
                "percentage_decimal_scale"
            );
        validateExactFields(root, required, errors, "ESTIMATE");
        percent(root, "min_holding_coverage_percent", errors);
        positiveInteger(root, "max_quote_age_seconds", errors);
        if (schemaVersion == 2) {
            positiveInteger(root, "nav_decimal_scale", errors);
            positiveInteger(root, "percentage_decimal_scale", errors);
        }
    }

    private void validateTrend(JsonNode root, List<String> errors) {
        validateWindows(root, errors, "windows", 2);
        percent(root, "signal_threshold_percent", errors);
    }

    private void validateMovingAverage(JsonNode root, List<String> errors) {
        validateWindows(root, errors, "windows", 3);
        percent(root, "deviation_threshold_percent", errors);
    }

    private void validateWindows(JsonNode root, List<String> errors, String field, int minimumSize) {
        JsonNode values = root.get(field);
        if (values == null || !values.isArray() || values.size() < minimumSize) {
            errors.add("QUANT_CONFIG_WINDOWS_INVALID:" + field);
            return;
        }
        int previous = 0;
        Set<Integer> unique = new HashSet<>();
        for (JsonNode value : values) {
            if (!value.canConvertToInt() || value.asInt() <= previous || !unique.add(value.asInt())) {
                errors.add("QUANT_CONFIG_WINDOWS_INVALID:" + field);
                return;
            }
            previous = value.asInt();
        }
    }

    private void validateRsiMacd(JsonNode root, List<String> errors) {
        positiveInteger(root, "rsi_period", errors);
        number(root, "rsi_oversold", errors);
        number(root, "rsi_overbought", errors);
        positiveInteger(root, "macd_fast", errors);
        positiveInteger(root, "macd_slow", errors);
        positiveInteger(root, "macd_signal", errors);
        if (numberValue(root, "rsi_oversold") != null && numberValue(root, "rsi_overbought") != null
            && (numberValue(root, "rsi_oversold").compareTo(BigDecimal.ZERO) < 0
            || numberValue(root, "rsi_overbought").compareTo(new BigDecimal("100")) > 0
            || numberValue(root, "rsi_oversold").compareTo(numberValue(root, "rsi_overbought")) >= 0)) {
            errors.add("RSI 阈值必须在 0 到 100 之间且严格递增");
        }
        Integer fast = integerValue(root, "macd_fast");
        Integer slow = integerValue(root, "macd_slow");
        if (fast != null && slow != null && fast >= slow) {
            errors.add("QUANT_CONFIG_MACD_WINDOW_INVALID:macd_fast");
        }
    }

    private void validateNavPosition(JsonNode root, List<String> errors) {
        positiveInteger(root, "history_window", errors);
        positiveInteger(root, "min_sample_size", errors);
        validateThresholds(root, errors, "region_thresholds", 3, new BigDecimal("100"));
        Integer window = integerValue(root, "history_window");
        Integer sample = integerValue(root, "min_sample_size");
        if (window != null && sample != null && sample > window) {
            errors.add("min_sample_size 不能大于 history_window");
        }
    }

    private void validateFactor(JsonNode root, List<String> errors) {
        requireText(root, "standardization", errors);
        requireText(root, "missing_value_policy", errors);
        JsonNode weights = root.get("weights");
        if (weights == null || !weights.isObject() || weights.isEmpty()) {
            errors.add("weights 必须是非空对象");
            return;
        }
        BigDecimal total = BigDecimal.ZERO;
        Iterator<JsonNode> iterator = weights.elements();
        while (iterator.hasNext()) {
            JsonNode weight = iterator.next();
            if (!weight.isNumber() || weight.decimalValue().compareTo(BigDecimal.ZERO) < 0) {
                errors.add("因子权重必须为非负数");
                return;
            }
            total = total.add(weight.decimalValue());
        }
        if (total.compareTo(new BigDecimal("100")) != 0) {
            errors.add("QUANT_CONFIG_FACTOR_WEIGHTS_INVALID:weights");
        }
    }

    private void validateRisk(JsonNode root, List<String> errors, String windowField, String thresholdField) {
        requireText(root, "risk_unit", errors);
        positiveInteger(root, windowField, errors);
        positiveInteger(root, "min_sample_size", errors);
        validateThresholds(root, errors, thresholdField, 2, null);
        Integer window = integerValue(root, windowField);
        Integer sample = integerValue(root, "min_sample_size");
        if (window != null && sample != null && sample > window) {
            errors.add("min_sample_size 不能大于 " + windowField);
        }
    }

    private void validatePortfolioRisk(JsonNode root, List<String> errors) {
        positiveInteger(root, "window", errors);
        positiveInteger(root, "var_horizon_days", errors);
        requireText(root, "var_method", errors);
        number(root, "var_confidence", errors);
        percent(root, "max_missing_percent", errors);
        BigDecimal confidence = numberValue(root, "var_confidence");
        if (confidence != null && (confidence.compareTo(BigDecimal.ZERO) <= 0 || confidence.compareTo(BigDecimal.ONE) >= 0)) {
            errors.add("var_confidence 必须在 0 和 1 之间");
        }
    }

    private void validateBacktest(JsonNode root, List<String> errors) {
        percent(root, "fee_rate_percent", errors);
        percent(root, "slippage_percent", errors);
        requireText(root, "execution_cost_mode", errors);
        validateMarketCosts(root, errors);
        if (root.has("win_rate") || root.has("win_rate_definition")) {
            errors.add("QUANT_CONFIG_D-010_WIN_RATE_BLOCKED:win_rate");
        }
    }

    private void validateMarketConventions(JsonNode root, List<String> errors) {
        JsonNode conventions = root.get("market_conventions");
        if (conventions == null || !conventions.isObject()) {
            errors.add("market_conventions 必须包含 CN、HK、US 三个市场");
            return;
        }
        for (String market : List.of("CN", "HK", "US")) {
            JsonNode convention = conventions.get(market);
            if (convention == null || !convention.isObject()) {
                errors.add("market_conventions." + market + " 缺失");
                continue;
            }
            requireText(convention, "timezone", errors);
            requireText(convention, "close_time", errors);
            positiveInteger(convention, "annual_trading_days", errors);
        }
    }

    private void validateExactFields(JsonNode root, Set<String> required, List<String> errors, String group) {
        if (root == null || !root.isObject()) {
            errors.add(group + " 必须是 JSON 对象");
            return;
        }
        Set<String> actual = new HashSet<>();
        root.fieldNames().forEachRemaining(actual::add);
        Set<String> missing = new HashSet<>(required);
        missing.removeAll(actual);
        if (!missing.isEmpty()) {
            errors.add(group + " 缺少字段: " + String.join(",", missing.stream().sorted().toList()));
        }
        actual.removeAll(required);
        if (!actual.isEmpty()) {
            errors.add(group + " 包含未知字段: " + String.join(",", actual.stream().sorted().toList()));
        }
    }

    private void validateMarketCosts(JsonNode root, List<String> errors) {
        JsonNode costs = root.get("market_costs");
        if (costs == null || !costs.isObject()) {
            errors.add("market_costs 必须包含 CN、HK、US 三个市场");
            return;
        }
        for (String market : List.of("CN", "HK", "US")) {
            JsonNode cost = costs.get(market);
            if (cost == null || !cost.isObject()) {
                errors.add("market_costs." + market + " 缺失");
                continue;
            }
            percent(cost, "buy_fee_percent", errors);
            percent(cost, "sell_fee_percent", errors);
            percent(cost, "slippage_percent", errors);
        }
    }

    private void validateThresholds(JsonNode root, List<String> errors, String field, int expectedSize, BigDecimal max) {
        JsonNode values = root.get(field);
        if (values == null || !values.isArray() || values.size() != expectedSize) {
            errors.add("QUANT_CONFIG_THRESHOLDS_INVALID:" + field);
            return;
        }
        BigDecimal previous = null;
        for (JsonNode value : values) {
            if (!value.isNumber() || (max != null && (value.decimalValue().compareTo(BigDecimal.ZERO) < 0
                || value.decimalValue().compareTo(max) > 0))
                || (previous != null && value.decimalValue().compareTo(previous) <= 0)) {
                errors.add("QUANT_CONFIG_THRESHOLDS_INVALID:" + field);
                return;
            }
            previous = value.decimalValue();
        }
    }

    private void requireText(JsonNode root, String field, List<String> errors) {
        if (!root.path(field).isTextual() || root.path(field).asText().isBlank()) {
            errors.add("QUANT_CONFIG_TEXT_REQUIRED:" + field);
        }
    }

    private void positiveInteger(JsonNode root, String field, List<String> errors) {
        Integer value = integerValue(root, field);
        if (value == null || value <= 0) {
            errors.add(field + " 必须为正整数");
        }
    }

    private void integerIn(JsonNode root, String field, int minimum, int maximum, List<String> errors) {
        Integer value = integerValue(root, field);
        if (value == null || value < minimum || value > maximum) {
            errors.add(field + " 超出允许范围");
        }
    }

    private void percent(JsonNode root, String field, List<String> errors) {
        BigDecimal value = numberValue(root, field);
        if (value == null || value.compareTo(BigDecimal.ZERO) < 0 || value.compareTo(new BigDecimal("100")) > 0) {
            errors.add(field + " 必须在 0 到 100 之间");
        }
    }

    private void number(JsonNode root, String field, List<String> errors) {
        if (numberValue(root, field) == null) {
            errors.add(field + " 必须为数值");
        }
    }

    private Integer integerValue(JsonNode root, String field) {
        JsonNode value = root.get(field);
        return value != null && value.canConvertToInt() && value.isIntegralNumber() ? value.asInt() : null;
    }

    private BigDecimal numberValue(JsonNode root, String field) {
        JsonNode value = root.get(field);
        return value != null && value.isNumber() ? value.decimalValue() : null;
    }
}
