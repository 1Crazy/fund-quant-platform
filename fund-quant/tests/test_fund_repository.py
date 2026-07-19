from datetime import date
from decimal import Decimal

import pandas as pd

from app.repositories.fund_repository import FundRepository


def test_only_six_digit_a_share_code_is_accepted() -> None:
    assert FundRepository._stock_code("600519") == "600519"
    assert FundRepository._stock_code("00700") == ""
    assert FundRepository._stock_code("HK00700") == ""


def test_report_period_sorting() -> None:
    periods = ["2025年4季度股票投资明细", "2026年1季度股票投资明细"]
    assert max(periods, key=FundRepository._period_sort_key) == "2026年1季度股票投资明细"


def test_parse_fund_archive_values() -> None:
    assert FundRepository._optional_date("2020-01-16").isoformat() == "2020-01-16"
    assert FundRepository._optional_scale("8.72亿") == Decimal("8.72")
    assert FundRepository._optional_rating("暂无评级") is None


def test_catalog_row_preserves_leading_zero() -> None:
    fund = FundRepository._catalog_row(
        {
            "基金代码": 8280,
            "基金简称": "国泰中证煤炭ETF联接C",
            "基金类型": "指数型-股票",
            "拼音缩写": "GTZZMTETFLJC",
        },
    )
    assert fund.fund_code == "008280"
    assert fund.source == "AKSHARE_CATALOG"


def test_optional_text_treats_missing_values_as_none() -> None:
    assert FundRepository._optional_text(pd.NA) is None
    assert FundRepository._optional_text("--") is None


def test_risk_level_is_converted_to_display_text() -> None:
    assert FundRepository._risk_level("4") == "中高风险"
    assert FundRepository._risk_level(None) is None


def test_recent_report_dates_do_not_include_future_quarter() -> None:
    values = FundRepository._recent_report_dates()
    assert values == sorted(values, reverse=True)
    assert all(date.fromisoformat(value) <= date.today() for value in values)
