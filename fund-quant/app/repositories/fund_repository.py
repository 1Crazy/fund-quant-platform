import re
import hashlib
import json
import uuid
from datetime import date, datetime
from decimal import Decimal
from zoneinfo import ZoneInfo

import pandas as pd

from app.clients.akshare_client import AkShareClient
from app.core.cache import NullCache, RedisCache, cache_aside
from app.core.config import Settings
from app.core.exceptions import DataNotFoundError, ProviderSchemaChangedError, UpstreamDataError
from app.schemas.data_center import (
    DataSet,
    FundCatalogRecord,
    FundHoldingRecord,
    FundNavRecord,
    FundProfileRecord,
    QualityIssue,
    QualityStatus,
    SyncBatchMeta,
    SyncEnvelope,
)
from app.schemas.market import FundHolding, FundInfo, FundNavPoint

SHANGHAI_ZONE = ZoneInfo("Asia/Shanghai")
ZERO = Decimal("0")
ONE_HUNDRED = Decimal("100")


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

    def sync_catalog(
        self,
        page: int,
        page_size: int,
        batch_id: str | None = None,
    ) -> SyncEnvelope[FundCatalogRecord]:
        dataset = DataSet.FUND_CATALOG
        source_time = self._source_time()
        frame = self._client.fund_catalog().copy()
        self._require_columns(frame, {"基金代码", "基金简称"}, "基金基础信息", dataset)
        rows = frame.to_dict("records")
        start = (page - 1) * page_size
        page_rows = rows[start : start + page_size]
        records: list[FundCatalogRecord] = []
        issues: list[QualityIssue] = []
        for row in page_rows:
            record, issue = self._catalog_sync_row(row, source_time)
            if record is not None:
                records.append(record)
            if issue is not None:
                issues.append(issue)
        return self._sync_envelope(
            dataset=dataset,
            source="AKSHARE_CATALOG",
            source_time=source_time,
            records=records,
            issues=issues,
            batch_id=batch_id,
            page=page,
            page_size=page_size,
            total_count=len(rows),
            has_more=start + page_size < len(rows),
        )

    def sync_fund_profile(
        self,
        fund_code: str,
        batch_id: str | None = None,
    ) -> SyncEnvelope[FundProfileRecord]:
        dataset = DataSet.FUND_PROFILE
        source_time = self._source_time()
        code = self._fund_code(fund_code)
        record, issues = self._profile_sync_record(code, source_time)
        records = [record] if record is not None else []
        return self._sync_envelope(
            dataset=dataset,
            source="AKSHARE_XQ",
            source_time=source_time,
            records=records,
            issues=issues,
            batch_id=batch_id,
        )

    def sync_nav(
        self,
        fund_code: str,
        start_date: date | None,
        end_date: date | None,
        batch_id: str | None = None,
    ) -> SyncEnvelope[FundNavRecord]:
        dataset = DataSet.FUND_NAV
        source_time = self._source_time()
        code = self._fund_code(fund_code)
        frame = self._client.fund_nav(code).copy()
        self._require_columns(frame, {"净值日期", "单位净值"}, "基金历史净值", dataset)
        accumulated = self._client.fund_accumulated_nav(code).copy()
        self._require_columns(accumulated, {"净值日期", "累计净值"}, "基金累计净值", dataset)
        growth_column = "日增长率" if "日增长率" in frame.columns else None
        frame["净值日期"] = pd.to_datetime(frame["净值日期"], errors="coerce")
        accumulated["净值日期"] = pd.to_datetime(accumulated["净值日期"], errors="coerce")
        frame = frame.merge(accumulated[["净值日期", "累计净值"]], how="left", on="净值日期")
        frame = frame.sort_values("净值日期")
        records: list[FundNavRecord] = []
        issues: list[QualityIssue] = []
        seen: dict[date, FundNavRecord] = {}
        for row in frame.to_dict("records"):
            nav_date_value = row.get("净值日期")
            if not pd.isna(nav_date_value):
                nav_date = nav_date_value.date()
                if start_date is not None and nav_date < start_date:
                    continue
                if end_date is not None and nav_date > end_date:
                    continue
            record, issue = self._nav_sync_row(code, row, growth_column, source_time)
            if issue is not None:
                issues.append(issue)
                continue
            if record is None:
                continue
            previous = seen.get(record.navDate)
            if previous is not None:
                if previous.checksum != record.checksum:
                    issues.append(
                        self._quality_issue(
                            dataset,
                            f"{code}:{record.navDate.isoformat()}",
                            "DUPLICATE_NAV_CONFLICT",
                            "同一基金代码和净值日期存在冲突净值",
                            row,
                            source_time,
                        )
                    )
                continue
            seen[record.navDate] = record
            records.append(record)
        return self._sync_envelope(
            dataset=dataset,
            source="AKSHARE_NAV",
            source_time=source_time,
            records=records,
            issues=issues,
            batch_id=batch_id,
        )

    def sync_holdings(
        self,
        fund_code: str,
        report_date: date | None = None,
        batch_id: str | None = None,
    ) -> SyncEnvelope[FundHoldingRecord]:
        dataset = DataSet.FUND_HOLDING
        source_time = self._source_time()
        code = self._fund_code(fund_code)
        target_report_date = report_date or date.fromisoformat(self._recent_report_dates()[0])
        source = "AKSHARE_XQ"
        records: list[FundHoldingRecord] = []
        issues: list[QualityIssue] = []
        try:
            data = self._client.fund_holdings_xq(code, target_report_date.isoformat())
            source_report_date = self._optional_date(data.get("source")) or target_report_date
            stock_list = data.get("stock_list")
            if not isinstance(stock_list, list):
                raise ProviderSchemaChangedError(
                    "基金持仓字段发生变化，缺少 stock_list",
                    dataset=dataset.value,
                    details={"missing": ["stock_list"]},
                )
            for index, item in enumerate(stock_list, start=1):
                record, issue = self._holding_xq_sync_row(
                    code,
                    item,
                    index,
                    source_report_date,
                    source_time,
                )
                if record is not None:
                    records.append(record)
                if issue is not None:
                    issues.append(issue)
        except UpstreamDataError as primary_error:
            source = "AKSHARE_EASTMONEY"
            fallback_records, fallback_issues = self._sync_holdings_from_eastmoney(
                code,
                target_report_date,
                source_time,
            )
            if not fallback_records and not fallback_issues:
                raise primary_error
            records = fallback_records
            issues = fallback_issues
        return self._sync_envelope(
            dataset=dataset,
            source=source,
            source_time=source_time,
            records=records,
            issues=issues,
            batch_id=batch_id,
        )

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

    def _catalog_sync_row(
        self,
        row: dict,
        source_time: datetime,
    ) -> tuple[FundCatalogRecord | None, QualityIssue | None]:
        dataset = DataSet.FUND_CATALOG
        code = self._optional_fund_code(row.get("基金代码"))
        if not code:
            return None, self._quality_issue(
                dataset,
                str(row.get("基金代码") or ""),
                "INVALID_FUND_CODE",
                "基金代码不是六位数字",
                row,
                source_time,
            )
        name = self._optional_text(row.get("基金简称"))
        if not name:
            return None, self._quality_issue(
                dataset,
                code,
                "MISSING_FUND_NAME",
                "基金名称为空",
                row,
                source_time,
            )
        payload = {
            "fundCode": code,
            "fundName": name,
            "fundType": self._optional_text(row.get("基金类型")),
            "pinyinAbbr": self._optional_text(row.get("拼音缩写")),
            "status": self._optional_text(row.get("基金状态") or row.get("状态")),
            "source": "AKSHARE_CATALOG",
        }
        return (
            FundCatalogRecord(
                **payload,
                sourceTime=source_time,
                qualityStatus=QualityStatus.NORMAL,
                checksum=self._checksum(payload),
            ),
            None,
        )

    def _profile_sync_record(
        self,
        fund_code: str,
        source_time: datetime,
    ) -> tuple[FundProfileRecord | None, list[QualityIssue]]:
        issues: list[QualityIssue] = []
        catalog = next(
            (item for item in self._get_catalog() if item.fund_code == fund_code),
            None,
        )
        if catalog is None:
            return None, [
                self._quality_issue(
                    DataSet.FUND_PROFILE,
                    fund_code,
                    "FUND_NOT_IN_CATALOG",
                    "基金目录中不存在该基金",
                    {"fund_code": fund_code},
                    source_time,
                )
            ]
        basic_frame = self._client.fund_basic_info(fund_code).copy()
        self._require_columns(basic_frame, {"item", "value"}, "基金档案", DataSet.FUND_PROFILE)
        basic = {
            str(row["item"]).strip(): row["value"]
            for row in basic_frame.to_dict("records")
        }
        try:
            profile = self._client.fund_profile(fund_code)
        except UpstreamDataError as exc:
            issues.append(
                self._quality_issue(
                    DataSet.FUND_PROFILE,
                    fund_code,
                    exc.code,
                    exc.message,
                    {"fund_code": fund_code},
                    source_time,
                )
            )
            profile = {}
        payload = {
            "fundCode": fund_code,
            "fundName": catalog.fund_name,
            "fundType": catalog.fund_type,
            "pinyinAbbr": catalog.pinyin_abbr,
            "status": None,
            "source": "AKSHARE_XQ",
            "companyName": self._optional_text(
                profile.get("fund_company")
                or basic.get("基金管理人")
                or basic.get("管理人")
            ),
            "managerName": self._optional_text(profile.get("manager_name") or basic.get("基金经理")),
            "custodianName": self._optional_text(profile.get("trup_name") or basic.get("托管银行")),
            "establishDate": self._optional_date(profile.get("found_date") or basic.get("成立时间")),
            "benchmark": self._optional_text(basic.get("业绩比较基准")),
            "riskLevel": self._risk_level(profile.get("risk_level")),
            "fundScale": self._optional_scale(profile.get("totshare") or basic.get("最新规模")),
        }
        status = QualityStatus.PARTIAL if issues else QualityStatus.NORMAL
        return (
            FundProfileRecord(
                **payload,
                sourceTime=source_time,
                qualityStatus=status,
                checksum=self._checksum(payload),
            ),
            issues,
        )

    def _nav_sync_row(
        self,
        fund_code: str,
        row: dict,
        growth_column: str | None,
        source_time: datetime,
    ) -> tuple[FundNavRecord | None, QualityIssue | None]:
        dataset = DataSet.FUND_NAV
        nav_date_value = row.get("净值日期")
        if pd.isna(nav_date_value):
            return None, self._quality_issue(
                dataset,
                fund_code,
                "INVALID_NAV_DATE",
                "净值日期无效",
                row,
                source_time,
            )
        nav_date = nav_date_value.date()
        if nav_date > date.today():
            return None, self._quality_issue(
                dataset,
                f"{fund_code}:{nav_date.isoformat()}",
                "FUTURE_NAV_DATE",
                "净值日期不能晚于当前日期",
                row,
                source_time,
            )
        try:
            unit_nav = self._decimal(row["单位净值"])
        except Exception:
            return None, self._quality_issue(
                dataset,
                f"{fund_code}:{nav_date.isoformat()}",
                "INVALID_UNIT_NAV",
                "单位净值不是合法数值",
                row,
                source_time,
            )
        if unit_nav <= ZERO:
            return None, self._quality_issue(
                dataset,
                f"{fund_code}:{nav_date.isoformat()}",
                "NON_POSITIVE_UNIT_NAV",
                "单位净值必须为正数",
                row,
                source_time,
            )
        accumulated_nav = None
        if not pd.isna(row.get("累计净值")):
            try:
                accumulated_nav = self._decimal(row["累计净值"])
            except Exception:
                accumulated_nav = None
        daily_return = None
        if growth_column and not pd.isna(row.get(growth_column)):
            try:
                daily_return = self._decimal(row[growth_column])
            except Exception:
                daily_return = None
        payload = {
            "fundCode": fund_code,
            "navDate": nav_date,
            "unitNav": unit_nav,
            "accumulatedNav": accumulated_nav,
            "dailyReturn": daily_return,
            "source": "AKSHARE_NAV",
        }
        return (
            FundNavRecord(
                **payload,
                sourceTime=source_time,
                qualityStatus=QualityStatus.NORMAL,
                checksum=self._checksum(payload),
            ),
            None,
        )

    def _holding_xq_sync_row(
        self,
        fund_code: str,
        row: dict,
        rank: int,
        report_date: date,
        source_time: datetime,
    ) -> tuple[FundHoldingRecord | None, QualityIssue | None]:
        stock_code = self._stock_code(row.get("code"))
        stock_name = self._optional_text(row.get("name"))
        return self._holding_sync_record(
            fund_code,
            stock_code,
            stock_name,
            row.get("percent"),
            rank,
            report_date,
            source_time,
            "AKSHARE_XQ",
            row,
        )

    def _sync_holdings_from_eastmoney(
        self,
        fund_code: str,
        report_date: date,
        source_time: datetime,
    ) -> tuple[list[FundHoldingRecord], list[QualityIssue]]:
        frames: list[pd.DataFrame] = []
        for year in (report_date.year, report_date.year - 1):
            try:
                frames.append(self._client.fund_holdings(fund_code, year))
            except UpstreamDataError:
                continue
        if not frames:
            return [], []
        frame = pd.concat(frames, ignore_index=True).copy()
        self._require_columns(
            frame,
            {"股票代码", "股票名称", "占净值比例", "季度"},
            "基金持仓",
            DataSet.FUND_HOLDING,
        )
        frame["季度"] = frame["季度"].astype(str)
        frame["报告日期"] = frame["季度"].map(self._period_to_date)
        frame = frame.dropna(subset=["报告日期"])
        frame = frame.loc[frame["报告日期"] <= report_date]
        if frame.empty:
            return [], []
        latest_report_date = max(frame["报告日期"])
        frame = frame.loc[frame["报告日期"] == latest_report_date]
        records: list[FundHoldingRecord] = []
        issues: list[QualityIssue] = []
        for index, row in enumerate(frame.to_dict("records"), start=1):
            record, issue = self._holding_sync_record(
                fund_code,
                self._stock_code(row.get("股票代码")),
                self._optional_text(row.get("股票名称")),
                row.get("占净值比例"),
                index,
                latest_report_date,
                source_time,
                "AKSHARE_EASTMONEY",
                row,
            )
            if record is not None:
                records.append(record)
            if issue is not None:
                issues.append(issue)
        return records, issues

    def _holding_sync_record(
        self,
        fund_code: str,
        stock_code: str,
        stock_name: str | None,
        weight_value: object,
        rank: int,
        report_date: date,
        source_time: datetime,
        source: str,
        raw: dict,
    ) -> tuple[FundHoldingRecord | None, QualityIssue | None]:
        dataset = DataSet.FUND_HOLDING
        record_key = f"{fund_code}:{report_date.isoformat()}:{stock_code or rank}"
        if not stock_code:
            return None, self._quality_issue(
                dataset,
                record_key,
                "INVALID_STOCK_CODE",
                "股票代码不是六位数字",
                raw,
                source_time,
            )
        if not stock_name:
            return None, self._quality_issue(
                dataset,
                record_key,
                "MISSING_STOCK_NAME",
                "股票名称为空",
                raw,
                source_time,
            )
        try:
            weight = self._decimal(weight_value)
        except Exception:
            return None, self._quality_issue(
                dataset,
                record_key,
                "INVALID_HOLDING_WEIGHT",
                "持仓权重不是合法数值",
                raw,
                source_time,
            )
        if weight < ZERO or weight > ONE_HUNDRED:
            return None, self._quality_issue(
                dataset,
                record_key,
                "HOLDING_WEIGHT_OUT_OF_RANGE",
                "持仓权重必须在 0 到 100 之间",
                raw,
                source_time,
            )
        payload = {
            "fundCode": fund_code,
            "reportDate": report_date,
            "stockCode": stock_code,
            "stockName": stock_name,
            "weight": weight,
            "rank": rank,
            "source": source,
        }
        return (
            FundHoldingRecord(
                **payload,
                sourceTime=source_time,
                qualityStatus=QualityStatus.NORMAL,
                checksum=self._checksum(payload),
            ),
            None,
        )

    def _sync_envelope(
        self,
        *,
        dataset: DataSet,
        source: str,
        source_time: datetime,
        records: list,
        issues: list[QualityIssue],
        batch_id: str | None,
        page: int | None = None,
        page_size: int | None = None,
        total_count: int | None = None,
        has_more: bool | None = None,
    ) -> SyncEnvelope:
        quality_status = self._quality_status(records, issues)
        batch_checksum = self._checksum([record.checksum for record in records])
        resolved_batch_id = batch_id or uuid.uuid4().hex
        resolved_issues = [
            issue if issue.batchId else issue.model_copy(update={"batchId": resolved_batch_id})
            for issue in issues
        ]
        meta = SyncBatchMeta(
            batchId=resolved_batch_id,
            dataset=dataset,
            source=source,
            sourceTime=source_time,
            fetchedAt=source_time,
            qualityStatus=quality_status,
            checksum=batch_checksum,
            dataVersion=self._data_version(dataset, batch_checksum),
            successCount=len(records),
            rejectedCount=len(resolved_issues),
            totalCount=total_count,
            page=page,
            pageSize=page_size,
            hasMore=has_more,
            nextPage=page + 1 if page is not None and has_more else None,
        )
        return SyncEnvelope(meta=meta, records=records, issues=resolved_issues)

    @staticmethod
    def _quality_status(records: list, issues: list[QualityIssue]) -> QualityStatus:
        if records and issues:
            return QualityStatus.PARTIAL
        if records:
            return QualityStatus.NORMAL
        if issues:
            return QualityStatus.FAILED
        return QualityStatus.EMPTY

    @staticmethod
    def _source_time() -> datetime:
        return datetime.now(SHANGHAI_ZONE)

    @staticmethod
    def _data_version(dataset: DataSet, checksum: str) -> str:
        return f"{dataset.value.lower()}:{checksum[:16]}"

    @classmethod
    def _quality_issue(
        cls,
        dataset: DataSet,
        record_key: str,
        reason_code: str,
        message: str,
        raw: object,
        discovered_at: datetime,
    ) -> QualityIssue:
        return QualityIssue(
            dataset=dataset,
            recordKey=record_key,
            reasonCode=reason_code,
            message=message,
            rawDigest=cls._checksum(raw),
            discoveredAt=discovered_at,
        )

    @classmethod
    def _checksum(cls, value: object) -> str:
        normalized = cls._normalize_for_checksum(value)
        payload = json.dumps(normalized, ensure_ascii=False, sort_keys=True, separators=(",", ":"))
        return hashlib.sha256(payload.encode("utf-8")).hexdigest()

    @classmethod
    def _normalize_for_checksum(cls, value: object) -> object:
        if isinstance(value, Decimal):
            return format(value.normalize(), "f")
        if isinstance(value, (date, datetime)):
            return value.isoformat()
        if isinstance(value, pd.Timestamp):
            return value.isoformat()
        if isinstance(value, dict):
            return {str(key): cls._normalize_for_checksum(item) for key, item in value.items()}
        if isinstance(value, (list, tuple)):
            return [cls._normalize_for_checksum(item) for item in value]
        if pd.isna(value):
            return None
        return value

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
    def _optional_fund_code(value: object) -> str:
        if value is None or pd.isna(value):
            return ""
        digits = "".join(character for character in str(value) if character.isdigit())
        return digits.zfill(6) if len(digits) <= 6 and digits else ""

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
    def _period_to_date(value: str) -> date | None:
        year, quarter = FundRepository._period_sort_key(value)
        month_day = {
            1: (3, 31),
            2: (6, 30),
            3: (9, 30),
            4: (12, 31),
        }.get(quarter)
        return date(year, *month_day) if year and month_day else None

    @staticmethod
    def _require_columns(
        frame: pd.DataFrame,
        columns: set[str],
        label: str,
        dataset: DataSet | None = None,
    ) -> None:
        missing = columns - set(frame.columns)
        if missing:
            raise ProviderSchemaChangedError(
                f"{label}字段发生变化，缺少: {', '.join(sorted(missing))}",
                dataset=dataset.value if dataset else None,
                details={"missing": sorted(missing), "columns": list(frame.columns)},
            )
