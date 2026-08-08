from functools import lru_cache

from pydantic import AliasChoices, Field
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
    # AkShare 某些基金代码可能无限等待上游连接；必须在 Java HTTP 超时前返回可重试错误。
    sync_nav_timeout_seconds: int = 45

    # 量化配置事实来源。该连接仅使用 PostgreSQL 只读角色，并且不得回退到源码默认值。
    quant_config_readonly_dsn: str = Field(
        default="",
        validation_alias=AliasChoices(
            "FUND_QUANT_CONFIG_READONLY_DSN",
            "quant_config_readonly_dsn",
        ),
    )
    quant_config_pool_min_size: int = 1
    quant_config_pool_max_size: int = 10
    quant_config_statement_timeout_ms: int = 5000
    quant_config_application_name: str = "fund-quant-readonly-reader"


@lru_cache
def get_settings() -> Settings:
    return Settings()
