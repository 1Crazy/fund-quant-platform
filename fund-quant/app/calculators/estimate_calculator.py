from dataclasses import dataclass
from datetime import date, datetime, timezone
from decimal import ROUND_HALF_UP, Decimal
from zoneinfo import ZoneInfo

from app.schemas.estimate import EstimateData, EstimateSourceStatus, HoldingContribution
from app.schemas.market import FundHolding, FundNavPoint, StockQuote

ONE_HUNDRED = Decimal("100")
SHANGHAI_ZONE = ZoneInfo("Asia/Shanghai")
ROUNDING_BY_CODE = {
    "HALF_UP": ROUND_HALF_UP,
}


@dataclass(frozen=True)
class EstimateCalculationConfig:
    """由精确发布版本派生的估值参数与结果血缘，不提供源码默认值。"""

    config_release_version: int
    config_release_checksum: str
    nav_decimal_scale: int
    percentage_decimal_scale: int
    rounding_mode: str
    min_holding_coverage_percent: Decimal
    max_quote_age_seconds: int
    estimate_config_version: int
    estimate_config_checksum: str
    algorithm_version: str

    @property
    def nav_quantum(self) -> Decimal:
        return Decimal(1).scaleb(-self.nav_decimal_scale)

    @property
    def percentage_quantum(self) -> Decimal:
        return Decimal(1).scaleb(-self.percentage_decimal_scale)

    @property
    def decimal_rounding(self) -> str:
        return ROUNDING_BY_CODE[self.rounding_mode]


class EstimateCalculator:
    """使用公开持仓和股票实时涨跌幅估算基金盘中净值。"""

    def calculate(
        self,
        fund_code: str,
        holdings: list[FundHolding],
        quotes: dict[str, StockQuote],
        latest_nav: FundNavPoint | None,
        config: EstimateCalculationConfig,
        now: datetime | None = None,
        input_data_version: str | None = None,
    ) -> EstimateData:
        calculation_time = (now or datetime.now(timezone.utc)).astimezone(SHANGHAI_ZONE)
        if latest_nav is None:
            return self._unavailable_result(
                fund_code,
                config,
                calculation_time,
                EstimateSourceStatus.UNSUPPORTED,
                "MISSING_CONFIRMED_NAV",
                input_data_version=input_data_version,
            )
        if latest_nav.quality_status is not None and latest_nav.quality_status != "NORMAL":
            return self._unavailable_result(
                fund_code,
                config,
                calculation_time,
                EstimateSourceStatus.UNSUPPORTED,
                f"NAV_QUALITY_{latest_nav.quality_status}",
                latest_nav,
                input_data_version=input_data_version,
            )
        if latest_nav.nav <= 0:
            return self._unavailable_result(
                fund_code,
                config,
                calculation_time,
                EstimateSourceStatus.FAILED,
                "INVALID_PREVIOUS_NAV",
                latest_nav,
                input_data_version=input_data_version,
            )
        if not holdings:
            return self._unavailable_result(
                fund_code,
                config,
                calculation_time,
                EstimateSourceStatus.UNSUPPORTED,
                "NO_DISCLOSED_EQUITY_HOLDINGS",
                latest_nav,
                input_data_version=input_data_version,
            )

        holding_quality = next(
            (
                holding.quality_status
                for holding in holdings
                if holding.quality_status is not None and holding.quality_status != "NORMAL"
            ),
            None,
        )
        if holding_quality is not None:
            return self._unavailable_result(
                fund_code,
                config,
                calculation_time,
                EstimateSourceStatus.UNSUPPORTED,
                f"HOLDING_QUALITY_{holding_quality}",
                latest_nav,
                report_period=holdings[0].report_period,
                input_data_version=input_data_version,
            )

        holding_coverage = sum((holding.weight for holding in holdings), Decimal("0"))
        if holding_coverage <= 0 or holding_coverage > ONE_HUNDRED:
            return self._unavailable_result(
                fund_code,
                config,
                calculation_time,
                EstimateSourceStatus.FAILED,
                "INVALID_DISCLOSED_HOLDING_WEIGHT",
                latest_nav,
                report_period=holdings[0].report_period,
                input_data_version=input_data_version,
            )

        contributions: list[HoldingContribution] = []
        quote_times: list[datetime] = []
        total_change_percent = Decimal("0")
        for holding in holdings:
            quote = quotes.get(holding.stock_code)
            if quote is None or not self._is_acceptable_quote(quote, calculation_time, config):
                continue
            # weight 和 change_percent 均为百分数口径，二者相乘后除以 100 得到百分点贡献。
            contribution = holding.weight * quote.change_percent / ONE_HUNDRED
            total_change_percent += contribution
            quote_times.append(quote.update_time.astimezone(SHANGHAI_ZONE))
            contributions.append(
                HoldingContribution(
                    stockCode=holding.stock_code,
                    stockName=holding.stock_name,
                    weight=holding.weight.quantize(
                        config.percentage_quantum, rounding=config.decimal_rounding
                    ),
                    changePercent=quote.change_percent.quantize(
                        config.percentage_quantum, rounding=config.decimal_rounding
                    ),
                    contribution=contribution.quantize(
                        config.percentage_quantum, rounding=config.decimal_rounding
                    ),
                    quoteTime=quote.update_time.astimezone(SHANGHAI_ZONE),
                )
            )

        quote_coverage = sum((item.weight for item in contributions), Decimal("0"))
        result_metadata = {
            "holding_coverage": holding_coverage,
            "quote_coverage": quote_coverage,
            "missing_quote_count": len(holdings) - len(contributions),
            "report_period": holdings[0].report_period,
            "quote_time": min(quote_times) if quote_times else None,
        }
        if holding_coverage < config.min_holding_coverage_percent:
            return self._unavailable_result(
                fund_code,
                config,
                calculation_time,
                EstimateSourceStatus.UNSUPPORTED,
                self._coverage_reason(
                    "INSUFFICIENT_HOLDING_COVERAGE", holding_coverage, config
                ),
                latest_nav,
                **result_metadata,
                input_data_version=input_data_version,
            )
        if quote_coverage == 0:
            return self._unavailable_result(
                fund_code,
                config,
                calculation_time,
                EstimateSourceStatus.UNSUPPORTED,
                "NO_ACCEPTABLE_QUOTES",
                latest_nav,
                **result_metadata,
                input_data_version=input_data_version,
            )
        if quote_coverage < config.min_holding_coverage_percent:
            return self._unavailable_result(
                fund_code,
                config,
                calculation_time,
                EstimateSourceStatus.PARTIAL,
                self._coverage_reason(
                    "INSUFFICIENT_QUOTE_COVERAGE", quote_coverage, config
                ),
                latest_nav,
                **result_metadata,
                input_data_version=input_data_version,
            )
        estimate_nav = latest_nav.nav * (Decimal("1") + total_change_percent / ONE_HUNDRED)
        return EstimateData(
            fundCode=fund_code,
            estimateTime=calculation_time,
            sourceStatus=EstimateSourceStatus.NORMAL,
            configReleaseVersion=config.config_release_version,
            configReleaseChecksum=config.config_release_checksum,
            estimateConfigVersion=config.estimate_config_version,
            estimateConfigChecksum=config.estimate_config_checksum,
            algorithmVersion=config.algorithm_version,
            estimateNav=estimate_nav.quantize(config.nav_quantum, rounding=config.decimal_rounding),
            estimateGrowthRate=total_change_percent.quantize(
                config.percentage_quantum, rounding=config.decimal_rounding
            ),
            previousNav=latest_nav.nav.quantize(config.nav_quantum, rounding=config.decimal_rounding),
            previousNavDate=latest_nav.date,
            holdingCoverageRate=holding_coverage.quantize(
                config.percentage_quantum, rounding=config.decimal_rounding
            ),
            quoteCoverageRate=quote_coverage.quantize(
                config.percentage_quantum, rounding=config.decimal_rounding
            ),
            missingQuoteCount=result_metadata["missing_quote_count"],
            quoteTime=result_metadata["quote_time"],
            holdingReportDate=self._parse_report_date(result_metadata["report_period"]),
            reportPeriod=holdings[0].report_period,
            inputDataVersion=input_data_version,
            tradeDate=calculation_time.date(),
            contributions=sorted(
                contributions,
                key=lambda item: abs(item.contribution),
                reverse=True,
            ),
        )

    def _is_acceptable_quote(
        self,
        quote: StockQuote,
        calculation_time: datetime,
        config: EstimateCalculationConfig,
    ) -> bool:
        if quote.update_time.tzinfo is None:
            return False
        age_seconds = (calculation_time - quote.update_time.astimezone(timezone.utc)).total_seconds()
        return 0 <= age_seconds <= config.max_quote_age_seconds

    def _unavailable_result(
        self,
        fund_code: str,
        config: EstimateCalculationConfig,
        calculation_time: datetime,
        status: EstimateSourceStatus,
        reason: str,
        latest_nav: FundNavPoint | None = None,
        *,
        holding_coverage: Decimal | None = None,
        quote_coverage: Decimal | None = None,
        missing_quote_count: int = 0,
        report_period: str | None = None,
        quote_time: datetime | None = None,
        input_data_version: str | None = None,
    ) -> EstimateData:
        return EstimateData(
            fundCode=fund_code,
            estimateTime=calculation_time,
            sourceStatus=status,
            statusReason=reason,
            configReleaseVersion=config.config_release_version,
            configReleaseChecksum=config.config_release_checksum,
            estimateConfigVersion=config.estimate_config_version,
            estimateConfigChecksum=config.estimate_config_checksum,
            algorithmVersion=config.algorithm_version,
            holdingCoverageRate=self._quantize_percentage_optional(holding_coverage, config),
            quoteCoverageRate=self._quantize_percentage_optional(quote_coverage, config),
            missingQuoteCount=missing_quote_count,
            previousNav=(
                latest_nav.nav.quantize(config.nav_quantum, rounding=config.decimal_rounding)
                if latest_nav is not None
                else None
            ),
            previousNavDate=latest_nav.date if latest_nav is not None else None,
            quoteTime=quote_time,
            holdingReportDate=self._parse_report_date(report_period),
            reportPeriod=report_period,
            inputDataVersion=input_data_version,
            tradeDate=calculation_time.date(),
        )

    @staticmethod
    def _coverage_reason(
        reason_code: str,
        actual_coverage: Decimal,
        config: EstimateCalculationConfig,
    ) -> str:
        actual = actual_coverage.quantize(config.percentage_quantum, rounding=config.decimal_rounding)
        required = config.min_holding_coverage_percent.quantize(
            config.percentage_quantum, rounding=config.decimal_rounding
        )
        return f"{reason_code}: actual={actual}, required={required}"

    @staticmethod
    def _parse_report_date(report_period: str | None) -> date | None:
        if report_period is None:
            return None
        try:
            return date.fromisoformat(report_period[:10])
        except ValueError:
            return None

    @staticmethod
    def _quantize_percentage_optional(
        value: Decimal | None,
        config: EstimateCalculationConfig,
    ) -> Decimal | None:
        if value is None:
            return None
        return value.quantize(config.percentage_quantum, rounding=config.decimal_rounding)
