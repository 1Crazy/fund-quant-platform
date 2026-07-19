import logging
from collections.abc import Callable

import akshare as ak
import pandas as pd
import requests

from app.core.exceptions import UpstreamDataError

LOGGER = logging.getLogger(__name__)


class AkShareClient:
    """隔离 AkShare 调用，Repository 不直接依赖动态模块函数。"""

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

    @staticmethod
    def _invoke(name: str, function: Callable[..., pd.DataFrame], **kwargs: str) -> pd.DataFrame:
        try:
            frame = function(**kwargs)
        except Exception as exc:
            LOGGER.exception("AkShare 获取%s失败", name)
            raise UpstreamDataError(f"AkShare 获取{name}失败: {exc}") from exc
        if frame is None or frame.empty:
            raise UpstreamDataError(f"AkShare 未返回{name}")
        return frame

    @staticmethod
    def _invoke_json(name: str, url: str, params: dict[str, str] | None = None) -> dict:
        try:
            response = requests.get(
                url,
                params=params,
                headers={"User-Agent": "Mozilla/5.0"},
                timeout=20,
            )
            response.raise_for_status()
            payload = response.json()
        except (requests.RequestException, ValueError) as exc:
            LOGGER.exception("公开接口获取%s失败", name)
            raise UpstreamDataError(f"公开接口获取{name}失败: {exc}") from exc
        if not isinstance(payload, dict):
            raise UpstreamDataError(f"公开接口未返回{name}")
        data = payload.get("data")
        if payload.get("result_code") != 0 or not isinstance(data, dict):
            raise UpstreamDataError(f"公开接口未返回{name}")
        return data
