from datetime import date

from app.core.exceptions import FundQuantError
from app.repositories.fund_repository import FundRepository
from app.repositories.stock_repository import StockRepository
from app.schemas.data_center import (
    FundCatalogRecord,
    FundHoldingRecord,
    FundNavRecord,
    FundProfileRecord,
    ProviderScope,
    SyncEnvelope,
)
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

    def sync_catalog(
        self,
        page: int,
        page_size: int,
        batch_id: str | None = None,
    ) -> SyncEnvelope[FundCatalogRecord]:
        return self._fund_repository.sync_catalog(page, page_size, batch_id)

    def sync_fund(
        self,
        fund_code: str,
        scope: ProviderScope | None = None,
        batch_id: str | None = None,
    ) -> SyncEnvelope[FundProfileRecord]:
        return self._fund_repository.sync_fund_profile(
            fund_code,
            batch_id or (scope.batchId if scope else None),
        )

    def sync_nav(
        self,
        fund_code: str,
        start_date: date | None,
        end_date: date | None,
        scope: ProviderScope | None = None,
        batch_id: str | None = None,
    ) -> SyncEnvelope[FundNavRecord]:
        self._validate_date_range(start_date, end_date)
        return self._fund_repository.sync_nav(
            fund_code,
            start_date,
            end_date,
            batch_id or (scope.batchId if scope else None),
        )

    def sync_holdings(
        self,
        fund_code: str,
        report_date: date | None = None,
        scope: ProviderScope | None = None,
        batch_id: str | None = None,
    ) -> SyncEnvelope[FundHoldingRecord]:
        return self._fund_repository.sync_holdings(
            fund_code,
            report_date,
            batch_id or (scope.batchId if scope else None),
        )

    @staticmethod
    def _validate_date_range(start_date: date | None, end_date: date | None) -> None:
        if start_date is not None and end_date is not None and start_date > end_date:
            raise FundQuantError(
                "INVALID_DATE_RANGE",
                "startDate 不能晚于 endDate",
                status_code=422,
                retryable=False,
            )
