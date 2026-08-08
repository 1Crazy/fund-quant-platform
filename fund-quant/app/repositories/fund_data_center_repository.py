from __future__ import annotations

import hashlib
from contextlib import contextmanager
from dataclasses import dataclass
from datetime import date
from typing import Iterator

from psycopg import Error as PsycopgError
from psycopg.rows import dict_row
from psycopg_pool import ConnectionPool, PoolTimeout

from app.core.config import Settings
from app.core.exceptions import DataCenterUnavailableError
from app.schemas.market import FundHolding, FundNavPoint


@dataclass(frozen=True)
class EstimateInputSnapshot:
    """同一只读事务内取得的估值输入及其数据血缘。"""

    latest_nav: FundNavPoint | None
    holdings: list[FundHolding]
    input_data_version: str | None


@dataclass(frozen=True)
class NavPositionInputSnapshot:
    """历史位置计算使用的确认 NAV 序列及其版本指纹。"""

    nav_points: list[FundNavPoint]
    input_data_version: str | None


class FundDataCenterRepository:
    """估值只读数据中心；不触发同步，也不访问租户私有表。"""

    def __init__(self, settings: Settings, pool: ConnectionPool | None = None) -> None:
        self._settings = settings
        self._pool = pool

    def load_estimate_inputs(self, fund_code: str) -> EstimateInputSnapshot:
        try:
            with self._readonly_connection() as connection:
                with connection.transaction():
                    with connection.cursor(row_factory=dict_row) as cursor:
                        cursor.execute("SET TRANSACTION READ ONLY")
                        cursor.execute(
                            "SELECT set_config('statement_timeout', %s, true)",
                            (str(self._settings.quant_config_statement_timeout_ms),),
                        )
                        cursor.execute(
                            """
                            SELECT nav_date, unit_nav, accumulated_nav, daily_growth_rate,
                                   data_version, quality_status, quality_reason
                            FROM fund_nav
                            WHERE fund_code = %s
                            ORDER BY nav_date DESC
                            LIMIT 1
                            """,
                            (fund_code,),
                        )
                        nav_row = cursor.fetchone()
                        cursor.execute(
                            """
                            WITH latest_report AS (
                                SELECT MAX(report_date) AS report_date
                                FROM fund_holding
                                WHERE fund_code = %s
                            )
                            SELECT stock_code, stock_name, disclosed_weight, report_date,
                                   data_version, quality_status, quality_reason
                            FROM fund_holding
                            WHERE fund_code = %s
                              AND report_date = (SELECT report_date FROM latest_report)
                            ORDER BY holding_rank NULLS LAST, stock_code
                            """,
                            (fund_code, fund_code),
                        )
                        holding_rows = cursor.fetchall()
        except (PsycopgError, PoolTimeout) as error:
            raise DataCenterUnavailableError("估值输入数据中心暂不可用") from error

        latest_nav = self._to_nav(fund_code, nav_row)
        holdings = [self._to_holding(fund_code, row) for row in holding_rows]
        return EstimateInputSnapshot(
            latest_nav=latest_nav,
            holdings=holdings,
            input_data_version=self._input_version(latest_nav, holdings),
        )

    def load_nav_position_inputs(
        self,
        fund_code: str,
        history_window: int,
        trade_date: date | None,
    ) -> NavPositionInputSnapshot:
        try:
            with self._readonly_connection() as connection:
                with connection.transaction():
                    with connection.cursor(row_factory=dict_row) as cursor:
                        cursor.execute("SET TRANSACTION READ ONLY")
                        cursor.execute(
                            "SELECT set_config('statement_timeout', %s, true)",
                            (str(self._settings.quant_config_statement_timeout_ms),),
                        )
                        cursor.execute(
                            """
                            SELECT nav_date, unit_nav, accumulated_nav, daily_growth_rate,
                                   data_version, quality_status, quality_reason
                            FROM fund_nav
                            WHERE fund_code = %s
                              AND unit_nav > 0
                              AND quality_status = 'NORMAL'
                              AND (%s::date IS NULL OR nav_date <= %s::date)
                            ORDER BY nav_date DESC
                            LIMIT %s
                            """,
                            (fund_code, trade_date, trade_date, history_window),
                        )
                        rows = cursor.fetchall()
        except (PsycopgError, PoolTimeout) as error:
            raise DataCenterUnavailableError("历史 NAV 输入数据中心暂不可用") from error

        nav_points = [self._to_nav(fund_code, row) for row in reversed(rows)]
        normalized_points = [point for point in nav_points if point is not None]
        version_parts = [f"nav:{point.date}:{point.data_version}" for point in normalized_points]
        input_data_version = (
            hashlib.sha256("\n".join(version_parts).encode("utf-8")).hexdigest()
            if version_parts
            else None
        )
        return NavPositionInputSnapshot(normalized_points, input_data_version)

    @staticmethod
    def _to_nav(fund_code: str, row: dict | None) -> FundNavPoint | None:
        if row is None:
            return None
        return FundNavPoint(
            fund_code=fund_code,
            date=row["nav_date"].isoformat(),
            nav=row["unit_nav"],
            accumulated_nav=row["accumulated_nav"],
            growth_rate=row["daily_growth_rate"],
            data_version=row["data_version"],
            quality_status=row["quality_status"],
            quality_reason=row["quality_reason"],
        )

    @staticmethod
    def _to_holding(fund_code: str, row: dict) -> FundHolding:
        return FundHolding(
            fund_code=fund_code,
            stock_code=row["stock_code"],
            stock_name=row["stock_name"],
            weight=row["disclosed_weight"],
            report_period=row["report_date"].isoformat(),
            data_version=row["data_version"],
            quality_status=row["quality_status"],
            quality_reason=row["quality_reason"],
        )

    @staticmethod
    def _input_version(
        latest_nav: FundNavPoint | None,
        holdings: list[FundHolding],
    ) -> str | None:
        if latest_nav is None and not holdings:
            return None
        parts = [
            f"nav:{latest_nav.data_version if latest_nav else '<missing>'}",
            *(
                f"holding:{holding.report_period}:{holding.stock_code}:{holding.data_version}"
                for holding in holdings
            ),
        ]
        # 原始版本组合可能超过快照列宽；指纹保留可重复、不可变的输入标识。
        return hashlib.sha256("\n".join(parts).encode("utf-8")).hexdigest()

    @contextmanager
    def _readonly_connection(self) -> Iterator:
        if not self._settings.quant_config_readonly_dsn:
            raise DataCenterUnavailableError("估值输入只读连接未配置", retryable=False)
        if self._pool is None:
            self._pool = ConnectionPool(
                conninfo=self._settings.quant_config_readonly_dsn,
                min_size=self._settings.quant_config_pool_min_size,
                max_size=self._settings.quant_config_pool_max_size,
                kwargs={"application_name": self._settings.quant_config_application_name},
            )
        with self._pool.connection() as connection:
            yield connection
