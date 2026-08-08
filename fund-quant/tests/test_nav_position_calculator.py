from datetime import date, datetime, timedelta, timezone
from decimal import Decimal

from app.calculators.nav_position_calculator import (
    NavPositionCalculationConfig,
    NavPositionCalculator,
)
from app.schemas.market import FundNavPoint
from app.schemas.nav_position import NavPositionRegion, NavPositionStatus


CALCULATION_TIME = datetime(2026, 8, 8, 9, 30, tzinfo=timezone.utc)


def _config() -> NavPositionCalculationConfig:
    return NavPositionCalculationConfig(
        config_release_version=3,
        config_release_checksum="a" * 64,
        nav_position_config_version=1,
        nav_position_config_checksum="b" * 64,
        history_window=756,
        min_sample_size=252,
        region_thresholds=(Decimal("25"), Decimal("50"), Decimal("75")),
        decimal_scale=6,
        rounding_mode="HALF_UP",
    )


def _points(values: list[int]) -> list[FundNavPoint]:
    return [
        FundNavPoint(
            fund_code="000001",
            date=(date(2025, 1, 1) + timedelta(days=index)).isoformat(),
            nav=Decimal(value),
            quality_status="NORMAL",
            data_version=f"nav-{index}",
        )
        for index, value in enumerate(values)
    ]


def test_calculate_uses_published_thresholds_for_low_normal_high_and_risk_regions() -> None:
    calculator = NavPositionCalculator()
    values = list(range(1, 253))

    low = calculator.calculate("000001", _points(values[:-1] + [1]), _config(), now=CALCULATION_TIME)
    normal = calculator.calculate("000001", _points(values[:-1] + [64]), _config(), now=CALCULATION_TIME)
    high = calculator.calculate("000001", _points(values[:-1] + [127]), _config(), now=CALCULATION_TIME)
    risk = calculator.calculate("000001", _points(values), _config(), now=CALCULATION_TIME)

    assert low.navPositionRegion == NavPositionRegion.LOW_VALUATION
    assert normal.navPositionRegion == NavPositionRegion.NORMAL
    assert high.navPositionRegion == NavPositionRegion.HIGH_VALUATION
    assert risk.navPositionRegion == NavPositionRegion.RISK
    assert risk.navPositionScore == risk.navPercentile == Decimal("100.000000")
    assert risk.currentDrawdown == Decimal("0.000000")


def test_calculate_returns_unavailable_without_published_minimum_sample_size() -> None:
    result = NavPositionCalculator().calculate(
        "000001", _points(list(range(1, 121))), _config(), now=CALCULATION_TIME
    )

    assert result.status == NavPositionStatus.UNAVAILABLE
    assert result.navPositionScore is None
    assert result.reasons[0].code == "INSUFFICIENT_SAMPLE"
    assert result.reasons[0].actual == 120
    assert result.reasons[0].required == 252
    assert result.ma60Deviation is None
    assert result.indicators[1].available is True
    assert result.indicators[3].reasonCode == "INSUFFICIENT_SAMPLE"


def test_calculate_uses_observations_without_synthesizing_missing_calendar_dates() -> None:
    points = _points(list(range(1, 253)))
    points.pop(1)
    points.append(
        FundNavPoint(
            fund_code="000001",
            date="2025-09-10",
            nav=Decimal("253"),
            quality_status="NORMAL",
        )
    )

    result = NavPositionCalculator().calculate("000001", points, _config(), now=CALCULATION_TIME)

    assert result.status == NavPositionStatus.NORMAL
    assert result.sampleCount == 252
    assert result.effectiveStartDate.isoformat() == "2025-01-01"
