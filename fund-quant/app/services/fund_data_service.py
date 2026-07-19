from app.repositories.fund_repository import FundRepository
from app.repositories.stock_repository import StockRepository
from app.schemas.market import FundHolding, FundInfo, FundNavPoint, StockQuote


class FundDataService:
    def __init__(
        self,
        fund_repository: FundRepository,
        stock_repository: StockRepository,
    ) -> None:
        self._fund_repository = fund_repository
        self._stock_repository = stock_repository

    def get_nav(self, fund_code: str, days: int) -> list[FundNavPoint]:
        return self._fund_repository.get_nav(fund_code, days)

    def get_fund(self, fund_code: str) -> FundInfo:
        return self._fund_repository.get_fund(fund_code)

    def search_funds(self, keyword: str, limit: int) -> list[FundInfo]:
        return self._fund_repository.search_funds(keyword, limit)

    def get_holdings(self, fund_code: str) -> list[FundHolding]:
        return self._fund_repository.get_holdings(fund_code)

    def get_stock(self, stock_code: str) -> StockQuote:
        return self._stock_repository.get_quotes([stock_code])[stock_code]
