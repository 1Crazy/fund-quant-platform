from functools import lru_cache

from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    """通过 FUND_QUANT_ 前缀集中管理运行参数。"""

    model_config = SettingsConfigDict(
        env_file=".env",
        env_prefix="FUND_QUANT_",
        extra="ignore",
    )

    app_name: str = "fund-quant"
    environment: str = "dev"
    host: str = "0.0.0.0"
    port: int = 8000
    redis_url: str = "redis://localhost:6379/1"
    log_level: str = "INFO"

    market_cache_seconds: int = 15
    holding_cache_seconds: int = 6 * 60 * 60
    nav_cache_seconds: int = 30 * 60
    fund_cache_seconds: int = 6 * 60 * 60
    estimate_cache_seconds: int = 15
    upstream_max_retries: int = 2
    upstream_retry_base_seconds: float = 0.5
    upstream_retry_after_seconds: int = 30


@lru_cache
def get_settings() -> Settings:
    return Settings()
