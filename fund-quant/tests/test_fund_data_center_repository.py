from datetime import date
from decimal import Decimal

from app.core.config import Settings
from app.repositories.fund_data_center_repository import FundDataCenterRepository


class _Cursor:
    def __init__(self) -> None:
        self.commands: list[str] = []
        self._last_query = ""

    def __enter__(self) -> "_Cursor":
        return self

    def __exit__(self, *_: object) -> None:
        return None

    def execute(self, command: str, *_: object) -> None:
        self.commands.append(command)
        self._last_query = command

    def fetchone(self) -> dict | None:
        if "FROM fund_nav" not in self._last_query:
            return None
        return {
            "nav_date": date(2026, 8, 7),
            "unit_nav": Decimal("1.234567"),
            "accumulated_nav": Decimal("1.987654"),
            "daily_growth_rate": Decimal("0.1234"),
            "data_version": "nav-20260807-v1",
            "quality_status": "NORMAL",
            "quality_reason": None,
        }

    def fetchall(self) -> list[dict]:
        return [
            {
                "stock_code": "600519",
                "stock_name": "贵州茅台",
                "disclosed_weight": Decimal("65.5"),
                "report_date": date(2026, 6, 30),
                "data_version": "holding-20260630-v1",
                "quality_status": "NORMAL",
                "quality_reason": None,
            }
        ]


class _Connection:
    def __init__(self) -> None:
        self.cursor_instance = _Cursor()

    def __enter__(self) -> "_Connection":
        return self

    def __exit__(self, *_: object) -> None:
        return None

    def transaction(self) -> "_Connection":
        return self

    def cursor(self, **_: object) -> _Cursor:
        return self.cursor_instance


class _Pool:
    def __init__(self) -> None:
        self.connection_instance = _Connection()

    def connection(self) -> _Connection:
        return self.connection_instance


def test_estimate_inputs_use_data_center_rows_in_a_read_only_transaction() -> None:
    pool = _Pool()
    repository = FundDataCenterRepository(
        Settings(quant_config_readonly_dsn="postgresql://readonly"),
        pool=pool,
    )

    snapshot = repository.load_estimate_inputs("000001")

    assert snapshot.latest_nav is not None
    assert snapshot.latest_nav.data_version == "nav-20260807-v1"
    assert snapshot.holdings[0].report_period == "2026-06-30"
    assert snapshot.holdings[0].quality_status == "NORMAL"
    assert snapshot.input_data_version is not None
    assert len(snapshot.input_data_version) == 64
    assert "SET TRANSACTION READ ONLY" in pool.connection_instance.cursor_instance.commands
