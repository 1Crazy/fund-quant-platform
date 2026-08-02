from app.calculators.estimate_calculator import EstimateCalculator
from app.core.cache import NullCache, RedisCache, cache_aside
from app.core.config import Settings
from app.repositories.fund_repository import FundRepository
from app.repositories.stock_repository import StockRepository
from app.schemas.estimate import EstimateData, HoldingRealtimeQuote


class EstimateService:
    def __init__(
        self,
        fund_repository: FundRepository,
        stock_repository: StockRepository,
        calculator: EstimateCalculator,
        cache: RedisCache | NullCache,
        settings: Settings,
    ) -> None:
        self._fund_repository = fund_repository
        self._stock_repository = stock_repository
        self._calculator = calculator
        self._cache = cache
        self._ttl = settings.estimate_cache_seconds

    def estimate(self, fund_code: str) -> EstimateData:
        code = fund_code.strip()
        return cache_aside(
            self._cache,
            f"fund_quant:fund:{code}:estimate",
            self._ttl,
            lambda: self._calculate(code),
            lambda value: value.model_dump(mode="json"),
            EstimateData.model_validate,
        )

    def holding_quotes(self, fund_code: str) -> list[HoldingRealtimeQuote]:
        code = fund_code.strip()
        holdings = self._fund_repository.get_holdings(code)
        quotes = self._stock_repository.get_quotes(
            [holding.stock_code for holding in holdings]
        )
        return [
            HoldingRealtimeQuote(
                stockCode=holding.stock_code,
                stockName=holding.stock_name,
                weight=holding.weight,
                changePercent=(quotes[holding.stock_code].change_percent
                               if holding.stock_code in quotes else None),
                quoteTime=(quotes[holding.stock_code].update_time
                           if holding.stock_code in quotes else None),
            )
            for holding in holdings
        ]

    def _calculate(self, fund_code: str) -> EstimateData:
        holdings = self._fund_repository.get_holdings(fund_code)
        nav_points = self._fund_repository.get_nav(fund_code, days=1)
        quotes = self._stock_repository.get_quotes(
            [holding.stock_code for holding in holdings]
        )
        return self._calculator.calculate(fund_code, holdings, quotes, nav_points[-1])
