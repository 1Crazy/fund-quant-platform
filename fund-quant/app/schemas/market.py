from datetime import date, datetime
from decimal import Decimal

from app.schemas.common import ApiModel


class StockQuote(ApiModel):
    stock_code: str
    stock_name: str
    latest_price: Decimal
    change_percent: Decimal
    volume: Decimal
    update_time: datetime


class FundHolding(ApiModel):
    fund_code: str
    stock_code: str
    stock_name: str
    weight: Decimal
    report_period: str
    data_version: str | None = None
    quality_status: str | None = None
    quality_reason: str | None = None


class FundNavPoint(ApiModel):
    fund_code: str
    date: str
    nav: Decimal
    accumulated_nav: Decimal | None = None
    growth_rate: Decimal | None = None
    data_version: str | None = None
    quality_status: str | None = None
    quality_reason: str | None = None


class FundInfo(ApiModel):
    fund_code: str
    fund_name: str
    fund_type: str | None = None
    pinyin_abbr: str | None = None
    manager_name: str | None = None
    custodian_name: str | None = None
    establish_date: date | None = None
    benchmark: str | None = None
    risk_level: str | None = None
    fund_scale: Decimal | None = None
    source: str = "AKSHARE"
