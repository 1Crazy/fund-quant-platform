from functools import lru_cache

from psycopg_pool import ConnectionPool

from app.calculators.estimate_calculator import EstimateCalculator
from app.calculators.nav_position_calculator import NavPositionCalculator
from app.clients.akshare_client import AkShareClient
from app.core.cache import RedisCache
from app.core.config import get_settings
from app.core.exceptions import DataCenterUnavailableError
from app.repositories.fund_data_center_repository import FundDataCenterRepository
from app.repositories.fund_repository import FundRepository
from app.repositories.quant_config_repository import QuantConfigRepository
from app.repositories.stock_repository import StockRepository
from app.services.estimate_service import EstimateService
from app.services.fund_data_service import FundDataService
from app.services.nav_position_service import NavPositionService


@lru_cache
def get_cache() -> RedisCache:
    return RedisCache(get_settings())


@lru_cache
def get_fund_repository() -> FundRepository:
    settings = get_settings()
    return FundRepository(AkShareClient(settings), get_cache(), settings)


@lru_cache
def get_stock_repository() -> StockRepository:
    settings = get_settings()
    return StockRepository(AkShareClient(settings), get_cache(), settings)


@lru_cache
def get_estimate_service() -> EstimateService:
    return EstimateService(
        get_fund_data_center_repository(),
        get_stock_repository(),
        EstimateCalculator(),
        get_quant_config_repository(),
        get_cache(),
        get_settings(),
    )


@lru_cache
def get_nav_position_service() -> NavPositionService:
    return NavPositionService(
        get_fund_data_center_repository(),
        get_quant_config_repository(),
        NavPositionCalculator(),
    )


@lru_cache
def get_fund_data_service() -> FundDataService:
    return FundDataService(get_fund_repository(), get_stock_repository())


@lru_cache
def get_quant_config_repository() -> QuantConfigRepository:
    return QuantConfigRepository(get_settings(), pool=get_readonly_pool())


@lru_cache
def get_fund_data_center_repository() -> FundDataCenterRepository:
    return FundDataCenterRepository(get_settings(), pool=get_readonly_pool())


@lru_cache
def get_readonly_pool() -> ConnectionPool:
    settings = get_settings()
    if not settings.quant_config_readonly_dsn:
        raise DataCenterUnavailableError("量化只读数据库连接未配置", retryable=False)
    return ConnectionPool(
        conninfo=settings.quant_config_readonly_dsn,
        min_size=settings.quant_config_pool_min_size,
        max_size=settings.quant_config_pool_max_size,
        kwargs={"application_name": settings.quant_config_application_name},
    )
