from dataclasses import dataclass
from datetime import date, datetime, timezone
from decimal import ROUND_HALF_UP, Decimal
from zoneinfo import ZoneInfo

from app.schemas.market import FundNavPoint
from app.schemas.nav_position import (
    NavPositionData,
    NavPositionIndicator,
    NavPositionReason,
    NavPositionRegion,
    NavPositionStatus,
)


ONE_HUNDRED = Decimal("100")
SHANGHAI_ZONE = ZoneInfo("Asia/Shanghai")
ROUNDING_BY_CODE = {"HALF_UP": ROUND_HALF_UP}


@dataclass(frozen=True)
class NavPositionCalculationConfig:
    """从精确发布版本派生的历史 NAV 位置语义。"""

    config_release_version: int
    config_release_checksum: str
    nav_position_config_version: int
    nav_position_config_checksum: str
    history_window: int
    min_sample_size: int
    region_thresholds: tuple[Decimal, Decimal, Decimal]
    decimal_scale: int
    rounding_mode: str
    algorithm_version: str = "nav-position-v1"

    @property
    def quantum(self) -> Decimal:
        return Decimal(1).scaleb(-self.decimal_scale)

    @property
    def decimal_rounding(self) -> str:
        return ROUNDING_BY_CODE[self.rounding_mode]


class NavPositionCalculator:
    """只基于已确认 NAV 的可重放历史位置计算器。"""

    def calculate(
        self,
        fund_code: str,
        nav_points: list[FundNavPoint],
        config: NavPositionCalculationConfig,
        *,
        now: datetime | None = None,
        input_data_version: str | None = None,
    ) -> NavPositionData:
        calculated_at = (now or datetime.now(timezone.utc)).astimezone(SHANGHAI_ZONE)
        points = self._usable_points(nav_points, config.history_window)
        if not points:
            return self._unavailable(
                fund_code,
                config,
                calculated_at,
                input_data_version,
                0,
                None,
                None,
                "NAV_NOT_FOUND",
                "没有可用的已确认 NAV 历史",
            )

        current = points[-1]
        sample_count = len(points)
        ma60 = self._moving_average_deviation(points, 60, config)
        ma120 = self._moving_average_deviation(points, 120, config)
        ma250 = self._moving_average_deviation(points, 250, config)
        indicators = self._indicators(points, ma60, ma120, ma250, config)
        if sample_count < config.min_sample_size:
            return self._unavailable(
                fund_code,
                config,
                calculated_at,
                input_data_version,
                sample_count,
                date.fromisoformat(points[0].date),
                date.fromisoformat(current.date),
                "INSUFFICIENT_SAMPLE",
                "已确认 NAV 样本不足，不能生成历史位置分数",
                actual=sample_count,
                required=config.min_sample_size,
                indicators=indicators,
            )

        percentile = self._percentile(points, config)
        drawdown = self._drawdown(points, config)
        region = self._region(percentile, config.region_thresholds)
        return NavPositionData(
            fundCode=fund_code,
            tradeDate=date.fromisoformat(current.date),
            calculatedAt=calculated_at,
            status=NavPositionStatus.NORMAL,
            algorithmVersion=config.algorithm_version,
            configReleaseVersion=config.config_release_version,
            configReleaseChecksum=config.config_release_checksum,
            navPositionConfigVersion=config.nav_position_config_version,
            navPositionConfigChecksum=config.nav_position_config_checksum,
            inputDataVersion=input_data_version,
            navPercentile=percentile,
            currentDrawdown=drawdown,
            ma60Deviation=ma60,
            ma120Deviation=ma120,
            ma250Deviation=ma250,
            # D-012：首版分数即历史 NAV 分位数；其他指标仅解释当前位置。
            navPositionScore=percentile,
            navPositionRegion=region,
            sampleCount=sample_count,
            effectiveStartDate=date.fromisoformat(points[0].date),
            effectiveEndDate=date.fromisoformat(current.date),
            indicators=indicators,
        )

    @staticmethod
    def _usable_points(
        nav_points: list[FundNavPoint], history_window: int
    ) -> list[FundNavPoint]:
        points = [
            point
            for point in nav_points
            if point.nav > 0 and point.quality_status in {None, "NORMAL"}
        ]
        points.sort(key=lambda point: point.date)
        return points[-history_window:]

    @staticmethod
    def _percentile(
        points: list[FundNavPoint], config: NavPositionCalculationConfig
    ) -> Decimal:
        current_nav = points[-1].nav
        if len(points) == 1:
            return Decimal("0").quantize(config.quantum, rounding=config.decimal_rounding)
        # 使用包含同值的最高秩，令样本最低值为 0、最高值为 100，边界可复放。
        rank = sum(point.nav <= current_nav for point in points) - 1
        value = Decimal(rank) * ONE_HUNDRED / Decimal(len(points) - 1)
        return value.quantize(config.quantum, rounding=config.decimal_rounding)

    @staticmethod
    def _drawdown(
        points: list[FundNavPoint], config: NavPositionCalculationConfig
    ) -> Decimal:
        running_high = max(point.nav for point in points)
        value = (points[-1].nav - running_high) * ONE_HUNDRED / running_high
        return value.quantize(config.quantum, rounding=config.decimal_rounding)

    @staticmethod
    def _moving_average_deviation(
        points: list[FundNavPoint], window: int, config: NavPositionCalculationConfig
    ) -> Decimal | None:
        if len(points) < window:
            return None
        average = sum((point.nav for point in points[-window:]), Decimal("0")) / Decimal(window)
        value = (points[-1].nav - average) * ONE_HUNDRED / average
        return value.quantize(config.quantum, rounding=config.decimal_rounding)

    @staticmethod
    def _region(
        percentile: Decimal,
        thresholds: tuple[Decimal, Decimal, Decimal],
    ) -> NavPositionRegion:
        low, normal, high = thresholds
        if percentile < low:
            return NavPositionRegion.LOW_VALUATION
        if percentile < normal:
            return NavPositionRegion.NORMAL
        if percentile < high:
            return NavPositionRegion.HIGH_VALUATION
        return NavPositionRegion.RISK

    def _indicators(
        self,
        points: list[FundNavPoint],
        ma60: Decimal | None,
        ma120: Decimal | None,
        ma250: Decimal | None,
        config: NavPositionCalculationConfig,
    ) -> list[NavPositionIndicator]:
        return [
            NavPositionIndicator(
                code="CURRENT_DRAWDOWN",
                value=self._drawdown(points, config),
                available=True,
            ),
            self._moving_average_indicator("MA60_DEVIATION", ma60),
            self._moving_average_indicator("MA120_DEVIATION", ma120),
            self._moving_average_indicator("MA250_DEVIATION", ma250),
        ]

    @staticmethod
    def _moving_average_indicator(code: str, value: Decimal | None) -> NavPositionIndicator:
        return NavPositionIndicator(
            code=code,
            value=value,
            available=value is not None,
            reasonCode=None if value is not None else "INSUFFICIENT_SAMPLE",
        )

    @staticmethod
    def _unavailable(
        fund_code: str,
        config: NavPositionCalculationConfig,
        calculated_at: datetime,
        input_data_version: str | None,
        sample_count: int,
        effective_start_date: date | None,
        effective_end_date: date | None,
        reason_code: str,
        message: str,
        *,
        actual: Decimal | int | None = None,
        required: Decimal | int | None = None,
        indicators: list[NavPositionIndicator] | None = None,
    ) -> NavPositionData:
        return NavPositionData(
            fundCode=fund_code,
            tradeDate=effective_end_date,
            calculatedAt=calculated_at,
            status=NavPositionStatus.UNAVAILABLE,
            algorithmVersion=config.algorithm_version,
            configReleaseVersion=config.config_release_version,
            configReleaseChecksum=config.config_release_checksum,
            navPositionConfigVersion=config.nav_position_config_version,
            navPositionConfigChecksum=config.nav_position_config_checksum,
            inputDataVersion=input_data_version,
            sampleCount=sample_count,
            effectiveStartDate=effective_start_date,
            effectiveEndDate=effective_end_date,
            reasons=[
                NavPositionReason(
                    code=reason_code,
                    message=message,
                    actual=actual,
                    required=required,
                )
            ],
            indicators=indicators or [],
        )
