from datetime import datetime, timedelta, timezone
from decimal import Decimal

import pytest

from app.calculators.estimate_calculator import EstimateCalculationConfig, EstimateCalculator
from app.core.exceptions import QuantConfigChecksumMismatchError, QuantConfigSchemaUnsupportedError
from app.core.config import Settings
from app.schemas.estimate import EstimateSourceStatus
from app.schemas.market import FundHolding, FundNavPoint, StockQuote
from app.schemas.quant_config import QuantConfigGroup, QuantConfigRelease
from app.services.estimate_service import EstimateService


CALCULATION_TIME = datetime(2026, 7, 19, 10, 30, tzinfo=timezone.utc)


def _config() -> EstimateCalculationConfig:
    return EstimateCalculationConfig(
        config_release_version=1,
        config_release_checksum="a" * 64,
        nav_decimal_scale=6,
        percentage_decimal_scale=4,
        rounding_mode="HALF_UP",
        min_holding_coverage_percent=Decimal("60"),
        max_quote_age_seconds=90,
        estimate_config_version=1,
        estimate_config_checksum="e" * 64,
        algorithm_version="holding-estimate-v1",
    )


def _quote(code: str, change_percent: str, update_time: datetime = CALCULATION_TIME) -> StockQuote:
    return StockQuote(
        stock_code=code,
        stock_name=code,
        latest_price=Decimal("100"),
        change_percent=Decimal(change_percent),
        volume=Decimal("100000"),
        update_time=update_time,
    )


def _release(rounding_mode: str = "HALF_UP", estimate_schema_version: int = 1) -> QuantConfigRelease:
    checksum = "b" * 64
    return QuantConfigRelease(
        releaseVersion=8,
        checksum=checksum,
        groups={
            "GLOBAL_CONVENTIONS": QuantConfigGroup(
                configCode="GLOBAL_CONVENTIONS",
                configVersion=1,
                schemaVersion=1,
                checksum="c" * 64,
                config={
                    "timezone": "UTC",
                    "percentage_unit": "PERCENT_POINT",
                    "decimal_scale": 6,
                    "rounding_mode": rounding_mode,
                },
            ),
            "ESTIMATE": QuantConfigGroup(
                configCode="ESTIMATE",
                configVersion=estimate_schema_version,
                schemaVersion=estimate_schema_version,
                checksum="d" * 64,
                config=(
                    {
                        "min_holding_coverage_percent": 60,
                        "max_quote_age_seconds": 90,
                    }
                    if estimate_schema_version == 1
                    else {
                        "min_holding_coverage_percent": 60,
                        "max_quote_age_seconds": 90,
                        "nav_decimal_scale": 6,
                        "percentage_decimal_scale": 4,
                    }
                ),
            ),
        },
    )


def test_calculate_estimate_with_percentage_units() -> None:
    holdings = [
        FundHolding(
            fund_code="000001",
            stock_code="600519",
            stock_name="贵州茅台",
            weight=Decimal("65.5"),
            report_period="2026年2季度",
        ),
        FundHolding(
            fund_code="000001",
            stock_code="300750",
            stock_name="宁德时代",
            weight=Decimal("6.2"),
            report_period="2026年2季度",
        ),
    ]
    quotes = {
        "600519": _quote("600519", "2"),
        "300750": _quote("300750", "-1"),
    }
    nav = FundNavPoint(
        fund_code="000001",
        date="2026-07-18",
        nav=Decimal("1.234"),
    )

    result = EstimateCalculator().calculate("000001", holdings, quotes, nav, _config(), CALCULATION_TIME)

    assert result.estimateGrowthRate == Decimal("1.2480")
    assert result.estimateNav == Decimal("1.249400")
    assert result.holdingCoverageRate == Decimal("71.7000")
    assert result.quoteCoverageRate == Decimal("71.7000")
    assert result.sourceStatus == EstimateSourceStatus.NORMAL
    assert result.previousNavDate.isoformat() == "2026-07-18"
    assert result.configReleaseVersion == 1
    assert result.configReleaseChecksum == "a" * 64


def test_calculate_marks_unsupported_when_no_holding_matches_quote() -> None:
    holding = FundHolding(
        fund_code="000001",
        stock_code="600519",
        stock_name="贵州茅台",
        weight=Decimal("65.5"),
        report_period="2026年2季度",
    )
    nav = FundNavPoint(
        fund_code="000001",
        date="2026-07-18",
        nav=Decimal("1.234"),
    )

    result = EstimateCalculator().calculate("000001", [holding], {}, nav, _config(), CALCULATION_TIME)

    assert result.sourceStatus == EstimateSourceStatus.UNSUPPORTED
    assert result.statusReason == "NO_ACCEPTABLE_QUOTES"
    assert result.estimateNav is None
    assert result.quoteCoverageRate == Decimal("0.0000")


def test_calculate_marks_unsupported_when_disclosed_holding_coverage_is_low() -> None:
    holding = FundHolding(
        fund_code="010990",
        stock_code="601899",
        stock_name="紫金矿业",
        weight=Decimal("0.69"),
        report_period="2026-03-31",
    )
    nav = FundNavPoint(
        fund_code="010990",
        date="2026-07-18",
        nav=Decimal("1.6049"),
    )

    result = EstimateCalculator().calculate(
        "010990",
        [holding],
        {"601899": _quote("601899", "2")},
        nav,
        _config(),
        CALCULATION_TIME,
    )

    assert result.sourceStatus == EstimateSourceStatus.UNSUPPORTED
    assert result.statusReason.startswith("INSUFFICIENT_HOLDING_COVERAGE")
    assert result.estimateNav is None


def test_calculate_marks_unsupported_when_quotes_are_older_than_published_maximum_age() -> None:
    holding = FundHolding(
        fund_code="000001",
        stock_code="600519",
        stock_name="贵州茅台",
        weight=Decimal("65.5"),
        report_period="2026年2季度",
    )
    nav = FundNavPoint(fund_code="000001", date="2026-07-18", nav=Decimal("1.234"))

    result = EstimateCalculator().calculate(
        "000001",
        [holding],
        {"600519": _quote("600519", "2", CALCULATION_TIME - timedelta(seconds=91))},
        nav,
        _config(),
        CALCULATION_TIME,
    )

    assert result.sourceStatus == EstimateSourceStatus.UNSUPPORTED
    assert result.missingQuoteCount == 1


def test_calculate_marks_partial_when_quote_coverage_is_below_required_threshold() -> None:
    holdings = [
        FundHolding(
            fund_code="000001",
            stock_code="600519",
            stock_name="贵州茅台",
            weight=Decimal("55"),
            report_period="2026-03-31",
        ),
        FundHolding(
            fund_code="000001",
            stock_code="300750",
            stock_name="宁德时代",
            weight=Decimal("45"),
            report_period="2026-03-31",
        ),
    ]
    nav = FundNavPoint(fund_code="000001", date="2026-07-18", nav=Decimal("1.234"))

    result = EstimateCalculator().calculate(
        "000001",
        holdings,
        {"600519": _quote("600519", "2")},
        nav,
        _config(),
        CALCULATION_TIME,
    )

    assert result.sourceStatus == EstimateSourceStatus.PARTIAL
    assert result.statusReason.startswith("INSUFFICIENT_QUOTE_COVERAGE")
    assert result.estimateGrowthRate is None
    assert result.quoteCoverageRate == Decimal("55.0000")


def test_calculate_rejects_non_normal_data_center_quality_before_requesting_normal_estimate() -> None:
    holding = FundHolding(
        fund_code="000001",
        stock_code="600519",
        stock_name="贵州茅台",
        weight=Decimal("65.5"),
        report_period="2026-06-30",
        quality_status="STALE",
    )
    nav = FundNavPoint(
        fund_code="000001",
        date="2026-07-18",
        nav=Decimal("1.234"),
        quality_status="NORMAL",
    )

    result = EstimateCalculator().calculate(
        "000001",
        [holding],
        {"600519": _quote("600519", "2")},
        nav,
        _config(),
        CALCULATION_TIME,
        input_data_version="input-version-fingerprint",
    )

    assert result.sourceStatus == EstimateSourceStatus.UNSUPPORTED
    assert result.statusReason == "HOLDING_QUALITY_STALE"
    assert result.inputDataVersion == "input-version-fingerprint"


def test_estimate_service_derives_calculation_parameters_from_exact_release() -> None:
    config = EstimateService._to_calculation_config(_release())

    assert config.config_release_version == 8
    assert config.config_release_checksum == "b" * 64
    assert config.nav_decimal_scale == 6
    assert config.percentage_decimal_scale == 6
    assert config.rounding_mode == "HALF_UP"
    assert config.min_holding_coverage_percent == Decimal("60")
    assert config.max_quote_age_seconds == 90
    assert config.estimate_config_version == 1
    assert config.estimate_config_checksum == "d" * 64
    assert config.algorithm_version == "holding-estimate-v1"


def test_estimate_service_uses_explicit_v2_output_precision() -> None:
    config = EstimateService._to_calculation_config(_release(estimate_schema_version=2))

    assert config.nav_decimal_scale == 6
    assert config.percentage_decimal_scale == 4
    assert config.algorithm_version == "holding-estimate-v2"


def test_estimate_service_rejects_unsupported_release_conventions() -> None:
    with pytest.raises(QuantConfigSchemaUnsupportedError):
        EstimateService._to_calculation_config(_release(rounding_mode="HALF_EVEN"))


class _TrackingCache:
    def __init__(self) -> None:
        self.operations: list[str] = []

    def get_json(self, key: str) -> None:
        self.operations.append(f"get:{key}")
        return None

    def set_json(self, key: str, value: dict, ttl_seconds: int) -> None:
        self.operations.append(f"set:{key}")


class _RejectingConfigRepository:
    def load_release(self, release_version: int, release_checksum: str) -> QuantConfigRelease:
        raise QuantConfigChecksumMismatchError()


def test_estimate_service_rejects_invalid_config_before_any_result_cache_access() -> None:
    cache = _TrackingCache()
    service = EstimateService(
        None,  # type: ignore[arg-type]
        None,  # type: ignore[arg-type]
        EstimateCalculator(),
        _RejectingConfigRepository(),  # type: ignore[arg-type]
        cache,  # type: ignore[arg-type]
        Settings(),
    )

    with pytest.raises(QuantConfigChecksumMismatchError):
        service.estimate("000001", 7, "a" * 64, 15, 15)

    assert cache.operations == []
