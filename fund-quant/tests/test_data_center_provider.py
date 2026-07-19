from datetime import date
from decimal import Decimal

import pandas as pd
import pytest

from app.core.cache import NullCache
from app.core.config import Settings
from app.core.exceptions import ProviderSchemaChangedError
from app.repositories.fund_repository import FundRepository
from app.schemas.data_center import QualityStatus


class FakeAkShareClient:
    def fund_catalog(self) -> pd.DataFrame:
        return pd.DataFrame(
            [
                {
                    "基金代码": 8280,
                    "基金简称": "国泰中证煤炭ETF联接C",
                    "基金类型": "指数型-股票",
                    "拼音缩写": "GTZZMTETFLJC",
                }
            ]
        )

    def fund_basic_info(self, fund_code: str) -> pd.DataFrame:
        return pd.DataFrame(
            [
                {"item": "基金管理人", "value": "国泰基金"},
                {"item": "基金经理", "value": "张三"},
                {"item": "成立时间", "value": "2020-01-16"},
                {"item": "最新规模", "value": "8.72亿"},
            ]
        )

    def fund_profile(self, fund_code: str) -> dict:
        return {
            "fund_company": "国泰基金",
            "manager_name": "张三",
            "risk_level": "4",
        }

    def fund_nav(self, fund_code: str) -> pd.DataFrame:
        return pd.DataFrame(
            [
                {"净值日期": "2026-01-02", "单位净值": "1.0100", "日增长率": "1.00"},
                {"净值日期": "2026-01-03", "单位净值": "0", "日增长率": "-100.00"},
            ]
        )

    def fund_accumulated_nav(self, fund_code: str) -> pd.DataFrame:
        return pd.DataFrame(
            [
                {"净值日期": "2026-01-02", "累计净值": "1.1100"},
                {"净值日期": "2026-01-03", "累计净值": "1.1100"},
            ]
        )

    def fund_holdings_xq(self, fund_code: str, report_date: str) -> dict:
        return {"source": report_date, "stock_list": []}


class DriftedAkShareClient(FakeAkShareClient):
    def fund_catalog(self) -> pd.DataFrame:
        return pd.DataFrame([{"代码": "008280", "名称": "字段漂移基金"}])


def _repository(client: object | None = None) -> FundRepository:
    return FundRepository(
        client or FakeAkShareClient(),
        NullCache(),
        Settings(upstream_max_retries=0),
    )


def test_catalog_sync_normalizes_public_fund_catalog() -> None:
    envelope = _repository().sync_catalog(page=1, page_size=10, batch_id="batch-catalog")

    assert envelope.meta.batchId == "batch-catalog"
    assert envelope.meta.dataset == "FUND_CATALOG"
    assert envelope.meta.qualityStatus == QualityStatus.NORMAL
    assert envelope.records[0].fundCode == "008280"
    assert envelope.records[0].source == "AKSHARE_CATALOG"
    assert envelope.records[0].sourceTime == envelope.meta.sourceTime


def test_fund_profile_sync_maps_company_and_source_metadata() -> None:
    envelope = _repository().sync_fund_profile("008280", batch_id="batch-profile")

    assert envelope.records[0].companyName == "国泰基金"
    assert envelope.records[0].managerName == "张三"
    assert envelope.records[0].riskLevel == "中高风险"
    assert envelope.records[0].checksum
    assert envelope.meta.dataVersion.startswith("fund_profile:")


def test_checksum_is_stable_for_equivalent_payloads() -> None:
    left = {"nav": Decimal("1.2300"), "code": "008280", "dates": [date(2026, 1, 2)]}
    right = {"dates": [date(2026, 1, 2)], "code": "008280", "nav": Decimal("1.23")}

    assert FundRepository._checksum(left) == FundRepository._checksum(right)


def test_nav_sync_rejects_non_positive_unit_nav() -> None:
    envelope = _repository().sync_nav(
        "008280",
        start_date=date(2026, 1, 1),
        end_date=date(2026, 1, 31),
        batch_id="batch-nav",
    )

    assert [record.navDate for record in envelope.records] == [date(2026, 1, 2)]
    assert envelope.records[0].accumulatedNav == Decimal("1.1100")
    assert envelope.issues[0].batchId == "batch-nav"
    assert envelope.issues[0].reasonCode == "NON_POSITIVE_UNIT_NAV"
    assert envelope.meta.qualityStatus == QualityStatus.PARTIAL


def test_holding_sync_returns_empty_status_for_empty_disclosure() -> None:
    envelope = _repository().sync_holdings(
        "008280",
        report_date=date(2026, 6, 30),
        batch_id="batch-holding",
    )

    assert envelope.records == []
    assert envelope.issues == []
    assert envelope.meta.qualityStatus == QualityStatus.EMPTY
    assert envelope.meta.checksum == FundRepository._checksum([])


def test_provider_schema_drift_returns_contract_error() -> None:
    with pytest.raises(ProviderSchemaChangedError) as captured:
        _repository(DriftedAkShareClient()).sync_catalog(page=1, page_size=10)

    assert captured.value.code == "DATA_PROVIDER_SCHEMA_CHANGED"
    assert captured.value.dataset == "FUND_CATALOG"
    assert captured.value.retryable is False
