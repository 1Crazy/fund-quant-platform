from datetime import date, datetime
from decimal import Decimal
from enum import Enum

from pydantic import Field

from app.schemas.common import ApiModel


class EstimateSourceStatus(str, Enum):
    """盘中估值的可用性状态。"""

    NORMAL = "NORMAL"
    PARTIAL = "PARTIAL"
    UNSUPPORTED = "UNSUPPORTED"
    STALE = "STALE"
    FAILED = "FAILED"
    UPSTREAM_FAILED = "UPSTREAM_FAILED"


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
    estimateTime: datetime
    sourceStatus: EstimateSourceStatus
    configReleaseVersion: int
    configReleaseChecksum: str
    estimateConfigVersion: int
    estimateConfigChecksum: str
    algorithmVersion: str
    holdingCoverageRate: Decimal | None = None
    quoteCoverageRate: Decimal | None = None
    missingQuoteCount: int = 0
    statusReason: str | None = None
    estimateNav: Decimal | None = None
    estimateGrowthRate: Decimal | None = None
    previousNav: Decimal | None = None
    previousNavDate: date | None = None
    quoteTime: datetime | None = None
    holdingReportDate: date | None = None
    reportPeriod: str | None = None
    inputDataVersion: str | None = None
    tradeDate: date | None = None
    source: str = "FUND_DATA_CENTER_HOLDING_ESTIMATE"
    contributions: list[HoldingContribution] = Field(default_factory=list)


class HealthData(ApiModel):
    status: str
    redis: str
    service: str = "fund-quant"
