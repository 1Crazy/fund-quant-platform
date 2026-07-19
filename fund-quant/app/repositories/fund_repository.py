import re
from datetime import date, datetime
from decimal import Decimal

import pandas as pd

from app.clients.akshare_client import AkShareClient
from app.core.cache import NullCache, RedisCache, cache_aside
from app.core.config import Settings
from app.core.exceptions import DataNotFoundError, UpstreamDataError
from app.schemas.market import FundHolding, FundInfo, FundNavPoint


class FundRepository:
    def __init__(
        self,
        client: AkShareClient,
        cache: RedisCache | NullCache,
        settings: Settings,
    ) -> None:
        self._client = client
        self._cache = cache
        self._holding_ttl = settings.holding_cache_seconds
        self._nav_ttl = settings.nav_cache_seconds
        self._fund_ttl = settings.fund_cache_seconds

    def get_holdings(self, fund_code: str) -> list[FundHolding]:
        code = self._fund_code(fund_code)
        return cache_aside(
            self._cache,
            f"fund_quant:fund:{code}:holdings:v2",
            self._holding_ttl,
            lambda: self._load_holdings(code),
            lambda values: [value.model_dump(mode="json") for value in values],
            lambda values: [FundHolding.model_validate(value) for value in values],
        )

    def get_nav(self, fund_code: str, days: int = 120) -> list[FundNavPoint]:
        code = self._fund_code(fund_code)
        all_points = cache_aside(
            self._cache,
            f"fund_quant:fund:{code}:nav:v2",
            self._nav_ttl,
            lambda: self._load_nav(code),
            lambda values: [value.model_dump(mode="json") for value in values],
            lambda values: [FundNavPoint.model_validate(value) for value in values],
        )
        return all_points if days == 0 else all_points[-days:]

    def get_fund(self, fund_code: str) -> FundInfo:
        code = self._fund_code(fund_code)
        return cache_aside(
            self._cache,
            f"fund_quant:fund:{code}:info:v3",
            self._fund_ttl,
            lambda: self._load_fund(code),
            lambda value: value.model_dump(mode="json"),
            FundInfo.model_validate,
        )

    def search_funds(self, keyword: str, limit: int = 50) -> list[FundInfo]:
        normalized = keyword.strip()
        if not normalized:
            return []
        lowered = normalized.casefold()
        return [
            fund
            for fund in self._get_catalog()
            if lowered in fund.fund_name.casefold()
            or lowered in (fund.pinyin_abbr or "").casefold()
        ][:limit]

    def _get_catalog(self) -> list[FundInfo]:
        return cache_aside(
            self._cache,
            "fund_quant:fund:catalog:v1",
            self._fund_ttl,
            self._load_catalog,
            lambda values: [value.model_dump(mode="json") for value in values],
            lambda values: [FundInfo.model_validate(value) for value in values],
        )

    def _load_catalog(self) -> list[FundInfo]:
        frame = self._client.fund_catalog().copy()
        self._require_columns(frame, {"基金代码", "基金简称"}, "基金基础信息")
        return [self._catalog_row(row) for row in frame.to_dict("records")]

    def _load_holdings(self, fund_code: str) -> list[FundHolding]:
        # 优先读取雪球基金公开披露接口。它对 ETF 联接基金的覆盖比东方财富接口更完整，
        # 并且会自动把未披露季度回退到最新报告期。
        latest_report_date = self._recent_report_dates()[0]
        primary_error: UpstreamDataError | None = None
        try:
            data = self._client.fund_holdings_xq(fund_code, latest_report_date)
            source_date = str(data.get("source") or latest_report_date)
            stock_list = data.get("stock_list")
            if isinstance(stock_list, list):
                return [
                    FundHolding(
                        fund_code=fund_code,
                        stock_code=stock_code,
                        stock_name=str(item.get("name") or stock_code),
                        weight=self._decimal(item.get("percent")),
                        report_period=source_date,
                    )
                    for item in stock_list
                    if (stock_code := self._stock_code(item.get("code")))
                    and item.get("percent") is not None
                ]
        except UpstreamDataError as exc:
            primary_error = exc

        # 雪球接口不可用时使用 AkShare 东方财富接口降级。
        frames: list[pd.DataFrame] = []
        # 年初时上一年度四季报通常仍是最新披露，因此同时查询当年和上一年。
        for year in (date.today().year, date.today().year - 1):
            try:
                frames.append(self._client.fund_holdings(fund_code, year))
            except UpstreamDataError:
                continue
        if frames:
            frame = pd.concat(frames, ignore_index=True).copy()
            self._require_columns(frame, {"股票代码", "股票名称", "占净值比例", "季度"}, "基金持仓")
            frame["季度"] = frame["季度"].astype(str)
            latest_period = max(frame["季度"], key=self._period_sort_key)
            frame = frame.loc[frame["季度"] == latest_period]
            holdings = self._holdings_from_eastmoney(fund_code, frame, latest_period)
            if holdings:
                return holdings

        # 最新报告期确实没有直接股票持仓时返回空数组，由调用方展示真实空态。
        if primary_error is not None and not frames:
            raise primary_error
        return []

    def _load_nav(self, fund_code: str) -> list[FundNavPoint]:
        frame = self._client.fund_nav(fund_code).copy()
        self._require_columns(frame, {"净值日期", "单位净值"}, "基金历史净值")
        accumulated = self._client.fund_accumulated_nav(fund_code).copy()
        self._require_columns(accumulated, {"净值日期", "累计净值"}, "基金累计净值")
        growth_column = "日增长率" if "日增长率" in frame.columns else None
        frame["净值日期"] = pd.to_datetime(frame["净值日期"], errors="coerce")
        accumulated["净值日期"] = pd.to_datetime(accumulated["净值日期"], errors="coerce")
        frame = frame.merge(
            accumulated[["净值日期", "累计净值"]],
            how="left",
            on="净值日期",
        )
        frame = frame.dropna(subset=["净值日期", "单位净值"]).sort_values("净值日期")
        points = [
            FundNavPoint(
                fund_code=fund_code,
                date=row["净值日期"].date().isoformat(),
                nav=self._decimal(row["单位净值"]),
                accumulated_nav=(
                    self._decimal(row["累计净值"])
                    if not pd.isna(row["累计净值"])
                    else None
                ),
                growth_rate=(
                    self._decimal(row[growth_column])
                    if growth_column and not pd.isna(row[growth_column])
                    else None
                ),
            )
            for row in frame.to_dict("records")
        ]
        if not points:
            raise DataNotFoundError(f"基金 {fund_code} 暂无历史净值")
        return points

    def _load_fund(self, fund_code: str) -> FundInfo:
        catalog = next(
            (item for item in self._get_catalog() if item.fund_code == fund_code),
            None,
        )
        if catalog is None:
            raise DataNotFoundError(f"基金 {fund_code} 不存在")
        basic_frame = self._client.fund_basic_info(fund_code).copy()
        self._require_columns(basic_frame, {"item", "value"}, "基金档案")
        basic = {
            str(row["item"]).strip(): row["value"]
            for row in basic_frame.to_dict("records")
        }
        try:
            profile = self._client.fund_profile(fund_code)
        except UpstreamDataError:
            profile = {}
        return catalog.model_copy(
            update={
                "manager_name": self._optional_text(profile.get("manager_name") or basic.get("基金经理")),
                "custodian_name": self._optional_text(profile.get("trup_name") or basic.get("托管银行")),
                "establish_date": self._optional_date(profile.get("found_date") or basic.get("成立时间")),
                "benchmark": self._optional_text(basic.get("业绩比较基准")),
                "risk_level": self._risk_level(profile.get("risk_level")),
                "fund_scale": self._optional_scale(profile.get("totshare") or basic.get("最新规模")),
                "source": "AKSHARE_XQ",
            }
        )

    def _holdings_from_eastmoney(
        self,
        fund_code: str,
        frame: pd.DataFrame,
        latest_period: str,
    ) -> list[FundHolding]:
        holdings: list[FundHolding] = []
        for row in frame.to_dict("records"):
            if pd.isna(row["占净值比例"]):
                continue
            stock_code = self._stock_code(row["股票代码"])
            # 第一阶段只接入 A 股实时行情，五位港股等非 A 股持仓不参与估值。
            if not stock_code:
                continue
            holdings.append(
                FundHolding(
                    fund_code=fund_code,
                    stock_code=stock_code,
                    stock_name=str(row["股票名称"]),
                    weight=self._decimal(row["占净值比例"]),
                    report_period=latest_period,
                )
            )
        return holdings

    @staticmethod
    def _recent_report_dates() -> list[str]:
        today = date.today()
        candidates = [
            date(year, month, day)
            for year in (today.year, today.year - 1)
            for month, day in ((3, 31), (6, 30), (9, 30), (12, 31))
        ]
        return [
            value.isoformat()
            for value in sorted((value for value in candidates if value <= today), reverse=True)
        ]

    @staticmethod
    def _catalog_row(row: dict) -> FundInfo:
        return FundInfo(
            fund_code=str(row["基金代码"]).zfill(6),
            fund_name=str(row["基金简称"]),
            fund_type=FundRepository._optional_text(row.get("基金类型")),
            pinyin_abbr=FundRepository._optional_text(row.get("拼音缩写")),
            source="AKSHARE_CATALOG",
        )

    @staticmethod
    def _fund_code(value: str) -> str:
        code = value.strip()
        if len(code) != 6 or not code.isdigit():
            raise DataNotFoundError(f"基金代码格式错误: {value}")
        return code

    @staticmethod
    def _stock_code(value: object) -> str:
        digits = "".join(character for character in str(value) if character.isdigit())
        return digits if len(digits) == 6 else ""

    @staticmethod
    def _decimal(value: object) -> Decimal:
        text = str(value).replace(",", "").replace("%", "").strip()
        return Decimal(text)

    @staticmethod
    def _optional_text(value: object) -> str | None:
        if value is None or pd.isna(value):
            return None
        text = str(value).strip()
        return None if not text or text in {"--", "<NA>"} else text

    @staticmethod
    def _optional_date(value: object) -> date | None:
        text = FundRepository._optional_text(value)
        if text is None:
            return None
        try:
            return datetime.strptime(text[:10], "%Y-%m-%d").date()
        except ValueError:
            return None

    @staticmethod
    def _optional_scale(value: object) -> Decimal | None:
        text = FundRepository._optional_text(value)
        if text is None:
            return None
        matched = re.search(r"[-+]?\d+(?:\.\d+)?", text.replace(",", ""))
        return Decimal(matched.group()) if matched else None

    @staticmethod
    def _optional_rating(value: object) -> str | None:
        text = FundRepository._optional_text(value)
        return None if text in {None, "暂无评级"} else text

    @staticmethod
    def _risk_level(value: object) -> str | None:
        text = FundRepository._optional_text(value)
        return {
            "1": "低风险",
            "2": "中低风险",
            "3": "中风险",
            "4": "中高风险",
            "5": "高风险",
        }.get(text or "", text)

    @staticmethod
    def _period_sort_key(value: str) -> tuple[int, int]:
        normalized = "".join(char if char.isdigit() else " " for char in value)
        digits = [int(part) for part in normalized.split()]
        year = digits[0] if digits else 0
        quarter = next((number for number in digits[1:] if 1 <= number <= 4), 0)
        return year, quarter

    @staticmethod
    def _require_columns(frame: pd.DataFrame, columns: set[str], dataset: str) -> None:
        missing = columns - set(frame.columns)
        if missing:
            raise UpstreamDataError(f"{dataset}字段发生变化，缺少: {', '.join(sorted(missing))}")
