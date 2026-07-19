import json
import logging
from collections.abc import Callable
from typing import TypeVar

from redis import Redis
from redis.exceptions import RedisError

from app.core.config import Settings

LOGGER = logging.getLogger(__name__)
T = TypeVar("T")


class RedisCache:
    """JSON Redis 缓存；Redis 故障时降级为直连上游，不阻断行情服务。"""

    def __init__(self, settings: Settings) -> None:
        self._client = Redis.from_url(
            settings.redis_url,
            decode_responses=True,
            socket_connect_timeout=1,
            socket_timeout=1,
        )

    def get_json(self, key: str) -> dict | list | None:
        try:
            raw = self._client.get(key)
        except RedisError as exc:
            LOGGER.warning("读取 Redis 缓存失败，回退到上游数据源: %s", exc)
            return None
        if not raw:
            return None
        try:
            return json.loads(raw)
        except json.JSONDecodeError:
            # 缓存结构升级或历史脏值不应阻断实时数据请求。
            LOGGER.warning("Redis 缓存不是合法 JSON，将忽略并重新加载: %s", key)
            return None

    def set_json(self, key: str, value: dict | list, ttl_seconds: int) -> None:
        try:
            self._client.set(key, json.dumps(value, ensure_ascii=False), ex=ttl_seconds)
        except RedisError as exc:
            LOGGER.warning("写入 Redis 缓存失败，本次结果仍正常返回: %s", exc)

    def ping(self) -> bool:
        try:
            return bool(self._client.ping())
        except RedisError:
            return False


class NullCache:
    """测试或特殊部署使用的无状态缓存实现。"""

    def get_json(self, key: str) -> dict | list | None:
        return None

    def set_json(self, key: str, value: dict | list, ttl_seconds: int) -> None:
        return None

    def ping(self) -> bool:
        return False


def cache_aside(
    cache: RedisCache | NullCache,
    key: str,
    ttl_seconds: int,
    loader: Callable[[], T],
    serializer: Callable[[T], dict | list],
    deserializer: Callable[[dict | list], T],
) -> T:
    cached = cache.get_json(key)
    if cached is not None:
        return deserializer(cached)
    value = loader()
    cache.set_json(key, serializer(value), ttl_seconds)
    return value
