from decimal import Decimal

import pandas as pd

from app.core.cache import NullCache
from app.core.config import Settings
from app.repositories.stock_repository import StockRepository


class SelectedQuoteClient:
    def stock_spot_by_codes(self, stock_codes: list[str]) -> pd.DataFrame:
        assert stock_codes == ["600519"]
        return pd.DataFrame(
            [
                {
                    "代码": "600519",
                    "名称": "贵州茅台",
                    "最新价": "1450.20",
                    "涨跌幅": "1.25",
                    "成交量": "12345",
                },
            ]
        )


def test_reads_current_change_from_selected_holdings() -> None:
    repository = StockRepository(
        SelectedQuoteClient(),
        NullCache(),
        Settings(upstream_max_retries=0),
    )

    quotes = repository.get_quotes(["600519"])

    assert quotes["600519"].stock_name == "贵州茅台"
    assert quotes["600519"].latest_price == Decimal("1450.20")
    assert quotes["600519"].change_percent == Decimal("1.25")
