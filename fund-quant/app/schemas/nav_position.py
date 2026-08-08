from datetime import date, datetime
from decimal import Decimal
from enum import Enum

from pydantic import Field

from app.schemas.common import ApiModel


class NavPositionStatus(str, Enum):
    NORMAL = "NORMAL"
    UNAVAILABLE = "UNAVAILABLE"


class NavPositionRegion(str, Enum):
    LOW_VALUATION = "LOW_VALUATION"
    NORMAL = "NORMAL"
    HIGH_VALUATION = "HIGH_VALUATION"
    RISK = "RISK"


class NavPositionReason(ApiModel):
    code: str
    message: str
    actual: Decimal | int | None = None
    required: Decimal | int | None = None


class NavPositionIndicator(ApiModel):
    code: str
    value: Decimal | None = None
    available: bool
    reasonCode: str | None = None


class NavPositionData(ApiModel):
    """历史 NAV 位置，不表示内在价值或交易建议。"""

    fundCode: str
    tradeDate: date | None = None
    calculatedAt: datetime
    status: NavPositionStatus
    algorithmVersion: str
    configReleaseVersion: int
    configReleaseChecksum: str
    navPositionConfigVersion: int
    navPositionConfigChecksum: str
    inputDataVersion: str | None = None
    navPercentile: Decimal | None = None
    currentDrawdown: Decimal | None = None
    ma60Deviation: Decimal | None = None
    ma120Deviation: Decimal | None = None
    ma250Deviation: Decimal | None = None
    navPositionScore: Decimal | None = None
    navPositionRegion: NavPositionRegion | None = None
    sampleCount: int = 0
    effectiveStartDate: date | None = None
    effectiveEndDate: date | None = None
    reasons: list[NavPositionReason] = Field(default_factory=list)
    indicators: list[NavPositionIndicator] = Field(default_factory=list)
