import logging
import time
from collections.abc import Callable

import akshare as ak
import pandas as pd
import requests

from app.core.config import Settings, get_settings
from app.core.exceptions import UpstreamDataError

LOGGER = logging.getLogger(__name__)


class AkShareClient:
    """隔离 AkShare 调用，Repository 不直接依赖动态模块函数。"""

    def __init__(self, settings: Settings | None = None) -> None:
        self._settings = settings or get_settings()

    def stock_spot(self) -> pd.DataFrame:
        return self._invoke("A股实时行情", ak.stock_zh_a_spot_em)

    def fund_holdings(self, fund_code: str, year: int) -> pd.DataFrame:
        return self._invoke(
            f"基金 {fund_code} {year} 年持仓",
            ak.fund_portfolio_hold_em,
            symbol=fund_code,
            date=str(year),
        )

    def fund_nav(self, fund_code: str) -> pd.DataFrame:
        return self._invoke(
            f"基金 {fund_code} 历史净值",
            ak.fund_open_fund_info_em,
            symbol=fund_code,
            indicator="单位净值走势",
        )

    def fund_accumulated_nav(self, fund_code: str) -> pd.DataFrame:
        return self._invoke(
            f"基金 {fund_code} 累计净值",
            ak.fund_open_fund_info_em,
            symbol=fund_code,
            indicator="累计净值走势",
        )

    def fund_basic_info(self, fund_code: str) -> pd.DataFrame:
        return self._invoke(
            f"基金 {fund_code} 档案",
            ak.fund_individual_basic_info_xq,
            symbol=fund_code,
        )

    def fund_profile(self, fund_code: str) -> dict:
        """获取雪球基金公开档案原始数据，补充风险等级等 AkShare 表格未暴露字段。"""
        return self._invoke_json(
            f"基金 {fund_code} 完整档案",
            f"https://danjuanfunds.com/djapi/fund/{fund_code}",
        )

    def fund_holdings_xq(self, fund_code: str, report_date: str) -> dict:
        """获取雪球基金公开披露持仓，包含股票明细与资产配置。"""
        return self._invoke_json(
            f"基金 {fund_code} {report_date} 持仓",
            "https://danjuanfunds.com/djapi/fundx/base/fund/record/asset/percent",
            params={"fund_code": fund_code, "report_date": report_date},
        )

    def fund_catalog(self) -> pd.DataFrame:
        return self._invoke("基金基础信息", ak.fund_name_em)

    def _invoke(self, name: str, function: Callable[..., pd.DataFrame], **kwargs: str) -> pd.DataFrame:
        frame = self._retry(name, lambda: function(**kwargs))
        if frame is None or frame.empty:
            raise UpstreamDataError(f"AkShare 未返回{name}", code="EMPTY_DATA", retryable=False)
        return frame

    def _invoke_json(self, name: str, url: str, params: dict[str, str] | None = None) -> dict:
        def request_json() -> dict:
            response = requests.get(
                url,
                params=params,
                headers={"User-Agent": "Mozilla/5.0"},
                timeout=20,
            )
            response.raise_for_status()
            return response.json()

        payload = self._retry(name, request_json)
        if not isinstance(payload, dict):
            raise UpstreamDataError(f"公开接口未返回{name}", code="EMPTY_DATA", retryable=False)
        data = payload.get("data")
        if payload.get("result_code") != 0 or not isinstance(data, dict):
            raise UpstreamDataError(f"公开接口未返回{name}", code="EMPTY_DATA", retryable=False)
        return data

    def _retry(self, name: str, operation: Callable[[], pd.DataFrame | dict]) -> pd.DataFrame | dict:
        attempts = max(1, self._settings.upstream_max_retries + 1)
        for index in range(attempts):
            try:
                return operation()
            except requests.RequestException as exc:
                upstream_error = self._classify_request_error(name, exc, index + 1, attempts)
                if not upstream_error.retryable or index + 1 >= attempts:
                    raise upstream_error from exc
                if index + 1 >= attempts:
                    break
                time.sleep(self._settings.upstream_retry_base_seconds * (2**index))
            except (TimeoutError, ConnectionError) as exc:
                upstream_error = self._transient_error(name, exc, index + 1, attempts)
                if index + 1 >= attempts:
                    raise upstream_error from exc
                time.sleep(self._settings.upstream_retry_base_seconds * (2**index))
            except ValueError as exc:
                raise UpstreamDataError(
                    f"上游返回{name}不是合法数据: {exc}",
                    code="UPSTREAM_INVALID_RESPONSE",
                    retryable=False,
                    retry_after_seconds=None,
                    details={
                        "upstream": name,
                        "attempt": index + 1,
                        "attempts": attempts,
                        "errorType": type(exc).__name__,
                    },
                ) from exc
            except Exception as exc:
                LOGGER.exception("AkShare 获取%s失败", name)
                raise UpstreamDataError(
                    f"AkShare 获取{name}失败: {exc}",
                    retry_after_seconds=self._settings.upstream_retry_after_seconds,
                    details={
                        "upstream": name,
                        "attempt": index + 1,
                        "attempts": attempts,
                        "errorType": type(exc).__name__,
                    },
                ) from exc
        raise UpstreamDataError(
            f"上游获取{name}失败",
            code="UPSTREAM_TEMPORARY_UNAVAILABLE",
            retry_after_seconds=self._settings.upstream_retry_after_seconds,
            details={"upstream": name, "attempts": attempts},
        )

    def _classify_request_error(
        self,
        name: str,
        exc: requests.RequestException,
        attempt: int,
        attempts: int,
    ) -> UpstreamDataError:
        response = getattr(exc, "response", None)
        status_code = getattr(response, "status_code", None)
        retry_after = self._settings.upstream_retry_after_seconds
        details = {
            "upstream": name,
            "attempt": attempt,
            "attempts": attempts,
            "errorType": type(exc).__name__,
            "statusCode": status_code,
        }
        if status_code == 429:
            return UpstreamDataError(
                f"上游获取{name}触发限流",
                code="UPSTREAM_RATE_LIMITED",
                retryable=True,
                retry_after_seconds=retry_after,
                details=details,
            )
        if status_code is not None and 400 <= status_code < 500:
            return UpstreamDataError(
                f"上游拒绝{name}请求",
                code="UPSTREAM_REQUEST_REJECTED",
                retryable=False,
                retry_after_seconds=None,
                details=details,
            )
        return self._transient_error(name, exc, attempt, attempts, details)

    def _transient_error(
        self,
        name: str,
        exc: Exception,
        attempt: int,
        attempts: int,
        details: dict | None = None,
    ) -> UpstreamDataError:
        return UpstreamDataError(
            f"上游获取{name}暂时不可用: {exc}",
            code="UPSTREAM_TEMPORARY_UNAVAILABLE",
            retryable=True,
            retry_after_seconds=self._settings.upstream_retry_after_seconds,
            details=details
            or {
                "upstream": name,
                "attempt": attempt,
                "attempts": attempts,
                "errorType": type(exc).__name__,
            },
        )
