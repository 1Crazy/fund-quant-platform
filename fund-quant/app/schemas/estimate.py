from datetime import date, datetime
from decimal import Decimal

from app.schemas.common import ApiModel


class HoldingContribution(ApiModel):
    stockCode: str
    stockName: str
    weight: Decimal
    changePercent: Decimal
    contribution: Decimal
    quoteTime: datetime


class HoldingRealtimeQuote(ApiModel):
    """最新公开持仓对应的实时行情；不代表基金完整资产配置。"""

    stockCode: str
    stockName: str
    weight: Decimal
    changePercent: Decimal | None = None
    quoteTime: datetime | None = None


class EstimateData(ApiModel):
    """字段名称与 Java EstimateProviderResponse 保持一致。"""

    fundCode: str
    estimateNav: Decimal
    estimateGrowthRate: Decimal
    previousNav: Decimal
    previousNavDate: date
    estimateTime: datetime
    source: str = "AKSHARE_HOLDING_ESTIMATE"
    holdingCoverageRate: Decimal
    reportPeriod: str
    contributions: list[HoldingContribution]


class HealthData(ApiModel):
    status: str
    redis: str
    service: str = "fund-quant"
