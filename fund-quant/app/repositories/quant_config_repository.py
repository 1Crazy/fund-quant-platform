from __future__ import annotations

import hashlib
import json
from contextlib import contextmanager
from threading import Lock
from typing import Iterator

from psycopg.rows import dict_row
from psycopg_pool import ConnectionPool

from app.core.config import Settings
from app.core.exceptions import (
    QuantConfigChecksumMismatchError,
    QuantConfigNotPublishedError,
    QuantConfigSchemaUnsupportedError,
    QuantConfigVersionMismatchError,
)
from app.schemas.quant_config import (
    SUPPORTED_CONFIG_CODES,
    QuantConfigGroup,
    QuantConfigRelease,
    QuantConfigValidationItem,
)


class QuantConfigRepository:
    """只读加载精确发布版本；缓存绝不替换为当前或最新发布版本。"""

    def __init__(self, settings: Settings, pool: ConnectionPool | None = None) -> None:
        self._settings = settings
        self._pool = pool
        self._cache: dict[tuple[int, str], QuantConfigRelease] = {}
        self._cache_lock = Lock()

    def load_release(self, release_version: int, checksum: str) -> QuantConfigRelease:
        cache_key = (release_version, checksum)
        with self._cache_lock:
            cached = self._cache.get(cache_key)
        if cached is not None:
            return cached

        with self._readonly_connection() as connection:
            with connection.transaction():
                with connection.cursor(row_factory=dict_row) as cursor:
                    cursor.execute("SET TRANSACTION READ ONLY")
                    cursor.execute("SELECT set_config('statement_timeout', %s, true)", (str(self._settings.quant_config_statement_timeout_ms),))
                    cursor.execute(
                        """
                        SELECT r.release_version, r.checksum AS release_checksum,
                               i.config_code, i.config_version, i.schema_version,
                               i.config_checksum, v.config_json::text AS config_json,
                               v.checksum AS stored_checksum
                        FROM quant_config_release r
                        JOIN quant_config_release_item i ON i.release_id = r.id
                        JOIN quant_config_version v ON v.id = i.config_version_id
                        WHERE r.release_version = %s
                        ORDER BY i.config_code
                        """,
                        (release_version,),
                    )
                    rows = cursor.fetchall()

        if not rows:
            raise QuantConfigNotPublishedError()
        if rows[0]["release_checksum"] != checksum:
            raise QuantConfigChecksumMismatchError()
        release_parts = [
            f"{row['config_code']}:{row['config_version']}:{row['config_checksum']}"
            for row in rows
        ]
        if len(rows) != len(SUPPORTED_CONFIG_CODES) or sha256("\n".join(release_parts)) != checksum:
            raise QuantConfigChecksumMismatchError()

        groups: dict[str, QuantConfigGroup] = {}
        for row in rows:
            code = row["config_code"]
            schema_version = row["schema_version"]
            if not supports_schema(code, schema_version):
                raise QuantConfigSchemaUnsupportedError(code, schema_version)
            if row["stored_checksum"] != row["config_checksum"]:
                raise QuantConfigVersionMismatchError()
            config = json.loads(row["config_json"])
            canonical_json = canonicalize_json(config)
            if sha256(canonical_json) != row["config_checksum"]:
                raise QuantConfigChecksumMismatchError()
            groups[code] = QuantConfigGroup(
                configCode=code,
                configVersion=row["config_version"],
                schemaVersion=schema_version,
                checksum=row["config_checksum"],
                config=config,
            )

        if len(groups) != len(rows) or set(groups) != SUPPORTED_CONFIG_CODES:
            raise QuantConfigVersionMismatchError()
        result = QuantConfigRelease(releaseVersion=release_version, checksum=checksum, groups=groups)
        with self._cache_lock:
            self._cache[cache_key] = result
        return result

    def validate_item(self, item: QuantConfigValidationItem) -> list[str]:
        if not supports_schema(item.configCode, item.schemaVersion):
            return [f"unsupported schema version for {item.configCode}"]
        try:
            config = json.loads(item.configJson)
        except json.JSONDecodeError:
            return [f"{item.configCode}: invalid JSON"]
        if not isinstance(config, dict):
            return [f"{item.configCode}: configuration must be an object"]
        if sha256(canonicalize_json(config)) != item.checksum:
            return [f"{item.configCode}: checksum mismatch"]
        return validate_schema(item.configCode, item.schemaVersion, config)

    @contextmanager
    def _readonly_connection(self) -> Iterator:
        if not self._settings.quant_config_readonly_dsn:
            raise QuantConfigNotPublishedError()
        if self._pool is None:
            self._pool = ConnectionPool(
                conninfo=self._settings.quant_config_readonly_dsn,
                min_size=self._settings.quant_config_pool_min_size,
                max_size=self._settings.quant_config_pool_max_size,
                kwargs={"application_name": self._settings.quant_config_application_name},
            )
        with self._pool.connection() as connection:
            yield connection


def canonicalize_json(value: object) -> str:
    return json.dumps(value, ensure_ascii=False, separators=(",", ":"), sort_keys=True)


def sha256(canonical_json: str) -> str:
    return hashlib.sha256(canonical_json.encode("utf-8")).hexdigest()


def supports_schema(config_code: str, schema_version: int) -> bool:
    return config_code in SUPPORTED_CONFIG_CODES and (
        schema_version == 1 or (config_code == "ESTIMATE" and schema_version == 2)
    )


def validate_schema(config_code: str, schema_version: int, config: dict) -> list[str]:
    if not supports_schema(config_code, schema_version):
        return [f"unsupported schema version for {config_code}"]
    errors: list[str] = []
    required_fields = {
        "GLOBAL_CONVENTIONS": {"timezone", "percentage_unit", "drawdown_sign", "annual_trading_days", "risk_free_rate_source", "risk_free_rate", "ddof", "decimal_scale", "rounding_mode", "market_conventions"},
        "ESTIMATE": (
            {"min_holding_coverage_percent", "max_quote_age_seconds"}
            if schema_version == 1
            else {
                "min_holding_coverage_percent",
                "max_quote_age_seconds",
                "nav_decimal_scale",
                "percentage_decimal_scale",
            }
        ),
        "TREND": {"windows", "signal_threshold_percent"},
        "MOVING_AVERAGE": {"windows", "deviation_threshold_percent"},
        "RSI_MACD": {"rsi_period", "rsi_oversold", "rsi_overbought", "macd_fast", "macd_slow", "macd_signal"},
        "NAV_POSITION": {"history_window", "min_sample_size", "region_thresholds"},
        "FACTOR": {"weights", "standardization", "missing_value_policy"},
        "FUND_RISK": {"window", "min_sample_size", "grade_thresholds", "risk_unit"},
        "PORTFOLIO_RISK": {"window", "var_confidence", "max_missing_percent", "var_horizon_days", "var_method"},
        "BACKTEST": {"fee_rate_percent", "slippage_percent", "execution_cost_mode", "market_costs"},
    }
    required = required_fields.get(config_code)
    if required is None:
        return [f"unsupported config code {config_code}"]
    missing = required - set(config)
    if missing:
        errors.append(f"{config_code}: missing required fields {','.join(sorted(missing))}")
        return errors
    unknown = set(config) - required
    if config_code == "ESTIMATE" and unknown:
        errors.append(f"{config_code}: unknown fields {','.join(sorted(unknown))}")
        return errors
    if config_code == "GLOBAL_CONVENTIONS":
        for field in ("timezone", "percentage_unit", "drawdown_sign", "risk_free_rate_source", "rounding_mode"):
            if not isinstance(config[field], str) or not config[field].strip():
                errors.append(f"GLOBAL_CONVENTIONS: {field} must be a nonempty string")
        if _positive_int(config["annual_trading_days"]) is None or _positive_int(config["decimal_scale"]) is None:
            errors.append("GLOBAL_CONVENTIONS: annual_trading_days and decimal_scale must be positive integers")
        if not _number_between(config["risk_free_rate"], float("-inf"), None):
            errors.append("GLOBAL_CONVENTIONS: risk_free_rate must be numeric")
        if config["ddof"] not in (0, 1) or isinstance(config["ddof"], bool):
            errors.append("GLOBAL_CONVENTIONS: ddof must be 0 or 1")
        if not _market_conventions_are_valid(config["market_conventions"]):
            errors.append("GLOBAL_CONVENTIONS: market_conventions must define CN, HK, and US local sessions")
    if config_code == "ESTIMATE":
        if not _number_between(config["min_holding_coverage_percent"], 0, 100):
            errors.append("ESTIMATE: min_holding_coverage_percent must be between 0 and 100")
        if _positive_int(config["max_quote_age_seconds"]) is None:
            errors.append("ESTIMATE: max_quote_age_seconds must be a positive integer")
        if schema_version == 2:
            if _positive_int(config["nav_decimal_scale"]) is None:
                errors.append("ESTIMATE: nav_decimal_scale must be a positive integer")
            if _positive_int(config["percentage_decimal_scale"]) is None:
                errors.append("ESTIMATE: percentage_decimal_scale must be a positive integer")
    if config_code in {"TREND", "MOVING_AVERAGE"}:
        windows = config["windows"]
        minimum_size = 3 if config_code == "MOVING_AVERAGE" else 2
        if not _strict_positive_ints(windows, minimum_size):
            errors.append("QUANT_CONFIG_WINDOWS_INVALID:windows")
    if config_code == "TREND" and not _number_between(config["signal_threshold_percent"], 0, 100):
        errors.append("TREND: signal_threshold_percent must be between 0 and 100")
    if config_code == "MOVING_AVERAGE" and not _number_between(config["deviation_threshold_percent"], 0, 100):
        errors.append("MOVING_AVERAGE: deviation_threshold_percent must be between 0 and 100")
    if config_code == "RSI_MACD":
        if _positive_int(config["rsi_period"]) is None or _positive_int(config["macd_signal"]) is None:
            errors.append("RSI_MACD: RSI and MACD periods must be positive integers")
        if _positive_int(config["macd_fast"]) is None or _positive_int(config["macd_slow"]) is None:
            errors.append("RSI_MACD: MACD periods must be positive integers")
        elif config["macd_fast"] >= config["macd_slow"]:
            errors.append("QUANT_CONFIG_MACD_WINDOW_INVALID:macd_fast")
        if not _ordered_numbers(config["rsi_oversold"], config["rsi_overbought"], 0, 100):
            errors.append("RSI_MACD: invalid RSI bounds")
    if config_code == "NAV_POSITION":
        history_window = _positive_int(config["history_window"])
        min_sample_size = _positive_int(config["min_sample_size"])
        if history_window is None or min_sample_size is None:
            errors.append("NAV_POSITION: window values must be positive integers")
        elif min_sample_size > history_window:
            errors.append("NAV_POSITION: min_sample_size exceeds history_window")
        if not _strict_thresholds(config["region_thresholds"], 3, 100):
            errors.append("QUANT_CONFIG_THRESHOLDS_INVALID:region_thresholds")
    if config_code == "FACTOR":
        weights = config["weights"]
        if not isinstance(weights, dict) or any(not isinstance(value, (int, float)) or value < 0 for value in weights.values()) or sum(weights.values()) != 100:
            errors.append("QUANT_CONFIG_FACTOR_WEIGHTS_INVALID:weights")
        for field in ("standardization", "missing_value_policy"):
            if not isinstance(config[field], str) or not config[field].strip():
                errors.append(f"FACTOR: {field} must be a nonempty string")
    if config_code == "FUND_RISK":
        risk_window = _positive_int(config["window"])
        risk_sample = _positive_int(config["min_sample_size"])
        if risk_window is None or risk_sample is None:
            errors.append("FUND_RISK: window values must be positive integers")
        elif risk_sample > risk_window:
            errors.append("FUND_RISK: min_sample_size exceeds window")
        if not _strict_thresholds(config["grade_thresholds"], 2, None):
            errors.append("FUND_RISK: invalid grade thresholds")
        if not isinstance(config["risk_unit"], str) or not config["risk_unit"].strip():
            errors.append("QUANT_CONFIG_TEXT_REQUIRED:risk_unit")
    if config_code == "PORTFOLIO_RISK":
        confidence = config["var_confidence"]
        missing_percent = config["max_missing_percent"]
        if _positive_int(config["window"]) is None:
            errors.append("PORTFOLIO_RISK: window must be a positive integer")
        if _positive_int(config["var_horizon_days"]) is None:
            errors.append("PORTFOLIO_RISK: var_horizon_days must be a positive integer")
        if not isinstance(config["var_method"], str) or not config["var_method"].strip():
            errors.append("PORTFOLIO_RISK: var_method must be a nonempty string")
        if not _number_between(confidence, 0, 1, exclusive=True):
            errors.append("PORTFOLIO_RISK: var_confidence must be between 0 and 1")
        if not _number_between(missing_percent, 0, 100):
            errors.append("PORTFOLIO_RISK: max_missing_percent must be between 0 and 100")
    if config_code == "BACKTEST" and {"win_rate", "win_rate_definition"} & set(config):
        errors.append("QUANT_CONFIG_D-010_WIN_RATE_BLOCKED:win_rate")
    if config_code == "BACKTEST":
        for field in ("fee_rate_percent", "slippage_percent"):
            if not _number_between(config[field], 0, 100):
                errors.append(f"BACKTEST: {field} must be between 0 and 100")
        if not isinstance(config["execution_cost_mode"], str) or not config["execution_cost_mode"].strip():
            errors.append("BACKTEST: execution_cost_mode must be a nonempty string")
        if not _market_costs_are_valid(config["market_costs"]):
            errors.append("BACKTEST: market_costs must define CN, HK, and US execution costs")
    return errors


def validate_schema_v1(config_code: str, config: dict) -> list[str]:
    """兼容既有测试和历史发布版本的 V1 校验入口。"""
    return validate_schema(config_code, 1, config)


def _strict_positive_ints(values: object, minimum_size: int) -> bool:
    return isinstance(values, list) and len(values) >= minimum_size and all(
        isinstance(value, int) and not isinstance(value, bool) and value > 0 for value in values
    ) and values == sorted(set(values))


def _strict_thresholds(values: object, length: int, maximum: int | None) -> bool:
    return isinstance(values, list) and len(values) == length and all(
        _number_between(value, 0, maximum) for value in values
    ) and values == sorted(set(values))


def _positive_int(value: object) -> int | None:
    return value if isinstance(value, int) and not isinstance(value, bool) and value > 0 else None


def _ordered_numbers(lower: object, upper: object, minimum: int | float, maximum: int | float) -> bool:
    return _number_between(lower, minimum, maximum) and _number_between(upper, minimum, maximum) and lower < upper


def _number_between(value: object, minimum: int | float, maximum: int | float | None, *, exclusive: bool = False) -> bool:
    if not isinstance(value, (int, float)) or isinstance(value, bool):
        return False
    if maximum is None:
        return value >= minimum
    if exclusive:
        return minimum < value < maximum
    return minimum <= value <= maximum


def _market_conventions_are_valid(value: object) -> bool:
    if not isinstance(value, dict) or set(value) != {"CN", "HK", "US"}:
        return False
    return all(
        isinstance(item, dict)
        and isinstance(item.get("timezone"), str)
        and bool(item["timezone"].strip())
        and isinstance(item.get("close_time"), str)
        and bool(item["close_time"].strip())
        and _positive_int(item.get("annual_trading_days")) is not None
        for item in value.values()
    )


def _market_costs_are_valid(value: object) -> bool:
    if not isinstance(value, dict) or set(value) != {"CN", "HK", "US"}:
        return False
    return all(
        isinstance(item, dict)
        and _number_between(item.get("buy_fee_percent"), 0, 100)
        and _number_between(item.get("sell_fee_percent"), 0, 100)
        and _number_between(item.get("slippage_percent"), 0, 100)
        for item in value.values()
    )
