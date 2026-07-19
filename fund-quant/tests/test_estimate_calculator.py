from datetime import datetime
from decimal import Decimal
from zoneinfo import ZoneInfo

import pytest

from app.calculators.estimate_calculator import EstimateCalculator
from app.core.exceptions import DataNotFoundError
from app.schemas.market import FundHolding, FundNavPoint, StockQuote


def _quote(code: str, change_percent: str) -> StockQuote:
    return StockQuote(
        stock_code=code,
        stock_name=code,
        latest_price=Decimal("100"),
        change_percent=Decimal(change_percent),
        volume=Decimal("100000"),
        update_time=datetime(2026, 7, 19, 10, 30, tzinfo=ZoneInfo("Asia/Shanghai")),
    )


def test_calculate_estimate_with_percentage_units() -> None:
    holdings = [
        FundHolding(
            fund_code="000001",
            stock_code="600519",
            stock_name="贵州茅台",
            weight=Decimal("8.5"),
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

    result = EstimateCalculator().calculate("000001", holdings, quotes, nav)

    assert result.estimateGrowthRate == Decimal("0.108000")
    assert result.estimateNav == Decimal("1.235333")
    assert result.holdingCoverageRate == Decimal("14.7000")
    assert result.previousNavDate.isoformat() == "2026-07-18"


def test_calculate_rejects_when_no_holding_matches_quote() -> None:
    holding = FundHolding(
        fund_code="000001",
        stock_code="600519",
        stock_name="贵州茅台",
        weight=Decimal("8.5"),
        report_period="2026年2季度",
    )
    nav = FundNavPoint(
        fund_code="000001",
        date="2026-07-18",
        nav=Decimal("1.234"),
    )

    with pytest.raises(DataNotFoundError, match="均未匹配到实时行情"):
        EstimateCalculator().calculate("000001", [holding], {}, nav)


def test_calculate_rejects_low_coverage_link_fund() -> None:
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

    with pytest.raises(DataNotFoundError, match="覆盖率仅 0.69%"):
        EstimateCalculator().calculate(
            "010990",
            [holding],
            {"601899": _quote("601899", "2")},
            nav,
        )
