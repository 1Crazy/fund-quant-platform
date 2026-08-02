from datetime import datetime
from decimal import ROUND_HALF_UP, Decimal
from zoneinfo import ZoneInfo

from app.core.exceptions import DataNotFoundError
from app.schemas.estimate import EstimateData, HoldingContribution
from app.schemas.market import FundHolding, FundNavPoint, StockQuote

SIX_DECIMALS = Decimal("0.000001")
FOUR_DECIMALS = Decimal("0.0001")
ONE_HUNDRED = Decimal("100")
SHANGHAI_ZONE = ZoneInfo("Asia/Shanghai")
MINIMUM_HOLDING_COVERAGE = Decimal("10")


class EstimateCalculator:
    """使用公开持仓和股票实时涨跌幅估算基金盘中净值。"""

    def calculate(
        self,
        fund_code: str,
        holdings: list[FundHolding],
        quotes: dict[str, StockQuote],
        latest_nav: FundNavPoint,
    ) -> EstimateData:
        if not holdings:
            raise DataNotFoundError(f"基金 {fund_code} 没有可用于估值的股票持仓")

        contributions: list[HoldingContribution] = []
        total_change_percent = Decimal("0")
        holding_coverage = Decimal("0")
        for holding in holdings:
            quote = quotes.get(holding.stock_code)
            if quote is None:
                continue
            # weight 和 change_percent 均为百分数口径，二者相乘后除以 100 得到百分点贡献。
            contribution = holding.weight * quote.change_percent / ONE_HUNDRED
            total_change_percent += contribution
            holding_coverage += holding.weight
            contributions.append(
                HoldingContribution(
                    stockCode=holding.stock_code,
                    stockName=holding.stock_name,
                    weight=holding.weight.quantize(FOUR_DECIMALS, rounding=ROUND_HALF_UP),
                    changePercent=quote.change_percent.quantize(
                        FOUR_DECIMALS, rounding=ROUND_HALF_UP
                    ),
                    contribution=contribution.quantize(
                        FOUR_DECIMALS, rounding=ROUND_HALF_UP
                    ),
                    quoteTime=quote.update_time,
                )
            )

        if not contributions:
            raise DataNotFoundError(f"基金 {fund_code} 的持仓均未匹配到实时行情")
        if holding_coverage < MINIMUM_HOLDING_COVERAGE:
            raise DataNotFoundError(
                f"基金 {fund_code} 的直接股票持仓覆盖率仅 {holding_coverage}%，"
                "公开数据未提供目标 ETF 标识，不能据此生成盘中估值"
            )
        estimate_nav = latest_nav.nav * (Decimal("1") + total_change_percent / ONE_HUNDRED)
        return EstimateData(
            fundCode=fund_code,
            estimateNav=estimate_nav.quantize(SIX_DECIMALS, rounding=ROUND_HALF_UP),
            estimateGrowthRate=total_change_percent.quantize(
                SIX_DECIMALS, rounding=ROUND_HALF_UP
            ),
            previousNav=latest_nav.nav.quantize(SIX_DECIMALS, rounding=ROUND_HALF_UP),
            previousNavDate=latest_nav.date,
            estimateTime=datetime.now(SHANGHAI_ZONE),
            holdingCoverageRate=holding_coverage.quantize(
                FOUR_DECIMALS, rounding=ROUND_HALF_UP
            ),
            reportPeriod=holdings[0].report_period,
            contributions=sorted(
                contributions,
                key=lambda item: abs(item.contribution),
                reverse=True,
            ),
        )
