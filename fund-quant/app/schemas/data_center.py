from datetime import date, datetime
from decimal import Decimal
from enum import StrEnum
from typing import Generic, TypeVar

from pydantic import Field

from app.schemas.common import ApiModel

T = TypeVar("T")


class DataSet(StrEnum):
    FUND_CATALOG = "FUND_CATALOG"
    FUND_PROFILE = "FUND_PROFILE"
    FUND_NAV = "FUND_NAV"
    FUND_HOLDING = "FUND_HOLDING"


class QualityStatus(StrEnum):
    NORMAL = "NORMAL"
    PARTIAL = "PARTIAL"
    EMPTY = "EMPTY"
    STALE = "STALE"
    FAILED = "FAILED"


class SharedFundContext(ApiModel):
    """Java 传入或 Python 只读查询得到的共享基金数据上下文。

    该结构只描述跨租户共享基金事实，不承载用户、租户、组合等私有业务数据。
    """

    fundCode: str = Field(pattern=r"^\d{6}$")
    latestDataVersion: str | None = None
    latestNavDate: date | None = None
    latestHoldingReportDate: date | None = None
    qualityStatus: QualityStatus | None = None


class ProviderScope(ApiModel):
    """Java 编排侧显式传入的供应方范围参数。"""

    batchId: str | None = None
    requestedBy: str | None = None
    sharedContext: list[SharedFundContext] = Field(default_factory=list)


class QualityIssue(ApiModel):
    dataset: DataSet
    batchId: str | None = None
    recordKey: str
    reasonCode: str
    message: str
    rawDigest: str
    discoveredAt: datetime


class SyncBatchMeta(ApiModel):
    batchId: str
    dataset: DataSet
    source: str
    sourceTime: datetime
    fetchedAt: datetime
    qualityStatus: QualityStatus
    checksum: str
    dataVersion: str
    successCount: int
    rejectedCount: int
    failedCount: int = 0
    totalCount: int | None = None
    page: int | None = None
    pageSize: int | None = None
    hasMore: bool | None = None
    nextPage: int | None = None


class SyncEnvelope(ApiModel, Generic[T]):
    meta: SyncBatchMeta
    records: list[T]
    issues: list[QualityIssue] = []


class CatalogSyncRequest(ProviderScope):
    page: int = Field(default=1, ge=1)
    pageSize: int = Field(default=200, ge=1, le=5000)


class FundProfileSyncRequest(ProviderScope):
    fundCodes: list[str] = Field(min_length=1, max_length=500)


class NavSyncRequest(ProviderScope):
    fundCode: str = Field(pattern=r"^\d{6}$")
    startDate: date | None = None
    endDate: date | None = None


class HoldingSyncRequest(ProviderScope):
    fundCode: str = Field(pattern=r"^\d{6}$")
    reportDate: date | None = None


class FundCatalogRecord(ApiModel):
    fundCode: str
    fundName: str
    fundType: str | None = None
    pinyinAbbr: str | None = None
    status: str | None = None
    source: str
    sourceTime: datetime
    qualityStatus: QualityStatus
    checksum: str


class FundProfileRecord(FundCatalogRecord):
    companyName: str | None = None
    managerName: str | None = None
    custodianName: str | None = None
    establishDate: date | None = None
    benchmark: str | None = None
    riskLevel: str | None = None
    fundScale: Decimal | None = None


class FundNavRecord(ApiModel):
    fundCode: str
    navDate: date
    unitNav: Decimal
    accumulatedNav: Decimal | None = None
    dailyReturn: Decimal | None = None
    source: str
    sourceTime: datetime
    qualityStatus: QualityStatus
    checksum: str


class FundHoldingRecord(ApiModel):
    fundCode: str
    reportDate: date
    stockCode: str
    stockName: str
    weight: Decimal
    rank: int
    source: str
    sourceTime: datetime
    qualityStatus: QualityStatus
    checksum: str
