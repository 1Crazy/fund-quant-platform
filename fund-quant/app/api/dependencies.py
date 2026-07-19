from functools import lru_cache

from app.calculators.estimate_calculator import EstimateCalculator
from app.clients.akshare_client import AkShareClient
from app.core.cache import RedisCache
from app.core.config import get_settings
from app.repositories.fund_repository import FundRepository
from app.repositories.stock_repository import StockRepository
from app.services.estimate_service import EstimateService
from app.services.fund_data_service import FundDataService


@lru_cache
def get_cache() -> RedisCache:
    return RedisCache(get_settings())


@lru_cache
def get_fund_repository() -> FundRepository:
    return FundRepository(AkShareClient(), get_cache(), get_settings())


@lru_cache
def get_stock_repository() -> StockRepository:
    return StockRepository(AkShareClient(), get_cache(), get_settings())


@lru_cache
def get_estimate_service() -> EstimateService:
    return EstimateService(
        get_fund_repository(),
        get_stock_repository(),
        EstimateCalculator(),
        get_cache(),
        get_settings(),
    )


@lru_cache
def get_fund_data_service() -> FundDataService:
    return FundDataService(get_fund_repository(), get_stock_repository())
