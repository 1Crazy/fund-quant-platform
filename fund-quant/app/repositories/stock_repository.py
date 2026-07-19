import logging
from datetime import datetime
from decimal import Decimal
from zoneinfo import ZoneInfo

import pandas as pd

from app.clients.akshare_client import AkShareClient
from app.core.cache import NullCache, RedisCache, cache_aside
from app.core.config import Settings
from app.core.exceptions import DataNotFoundError, UpstreamDataError
from app.schemas.market import StockQuote

SHANGHAI_ZONE = ZoneInfo("Asia/Shanghai")
LOGGER = logging.getLogger(__name__)


class StockRepository:
    def __init__(
        self,
        client: AkShareClient,
        cache: RedisCache | NullCache,
        settings: Settings,
    ) -> None:
        self._client = client
        self._cache = cache
        self._ttl = settings.market_cache_seconds

    def get_quotes(self, stock_codes: list[str]) -> dict[str, StockQuote]:
        normalized_codes = sorted({self._normalize_code(code) for code in stock_codes})
        if not normalized_codes:
            return {}
        market = cache_aside(
            self._cache,
            "fund_quant:market:a_share:spot",
            self._ttl,
            self._load_market,
            lambda value: value,
            lambda value: value,
        )
        quotes = {
            code: StockQuote.model_validate(market[code])
            for code in normalized_codes
            if code in market
        }
        missing = sorted(set(normalized_codes) - set(quotes))
        if missing:
            # 停牌股票可能没有可用实时价格；估值计算会基于实际匹配到的持仓并披露覆盖率。
            LOGGER.warning("部分持仓未获取到实时行情，将从本次估值中排除: %s", ", ".join(missing))
        if not quotes:
            raise DataNotFoundError(f"未获取到股票实时行情: {', '.join(normalized_codes)}")
        return quotes

    def _load_market(self) -> dict[str, dict]:
        frame = self._client.stock_spot().copy()
        self._require_columns(frame, {"代码", "名称", "最新价", "涨跌幅", "成交量"})
        frame["代码"] = frame["代码"].astype(str).str.extract(r"(\d{6})", expand=False)
        result: dict[str, dict] = {}
        update_time = datetime.now(SHANGHAI_ZONE)
        for row in frame.to_dict("records"):
            code = self._normalize_code(row["代码"])
            if not code or pd.isna(row["最新价"]) or pd.isna(row["涨跌幅"]):
                continue
            result[code] = StockQuote(
                stock_code=code,
                stock_name=str(row["名称"]),
                latest_price=self._decimal(row["最新价"]),
                change_percent=self._decimal(row["涨跌幅"]),
                volume=self._decimal(row["成交量"]),
                update_time=update_time,
            ).model_dump(mode="json")
        if not result:
            raise UpstreamDataError("A股实时行情没有可用记录")
        return result

    @staticmethod
    def _normalize_code(value: object) -> str:
        text = str(value).strip()
        digits = "".join(character for character in text if character.isdigit())
        return digits[-6:].zfill(6) if digits else ""

    @staticmethod
    def _decimal(value: object) -> Decimal:
        return Decimal(str(value).replace(",", ""))

    @staticmethod
    def _require_columns(frame: pd.DataFrame, columns: set[str]) -> None:
        missing = columns - set(frame.columns)
        if missing:
            raise UpstreamDataError(f"A股行情字段发生变化，缺少: {', '.join(sorted(missing))}")
