from decimal import Decimal

from app.calculators.estimate_calculator import EstimateCalculationConfig, EstimateCalculator
from app.core.cache import NullCache, RedisCache, cache_aside
from app.core.config import Settings
from app.core.exceptions import DataNotFoundError, QuantConfigSchemaUnsupportedError
from app.repositories.fund_data_center_repository import FundDataCenterRepository
from app.repositories.quant_config_repository import QuantConfigRepository
from app.repositories.stock_repository import StockRepository
from app.schemas.quant_config import QuantConfigRelease
from app.schemas.estimate import EstimateData, HoldingRealtimeQuote
from app.schemas.market import FundHolding, FundNavPoint

ESTIMATE_ALGORITHM_PREFIX = "holding-estimate-v"


class EstimateService:
    def __init__(
        self,
        fund_data_center_repository: FundDataCenterRepository,
        stock_repository: StockRepository,
        calculator: EstimateCalculator,
        quant_config_repository: QuantConfigRepository,
        cache: RedisCache | NullCache,
        settings: Settings,
    ) -> None:
        self._fund_data_center_repository = fund_data_center_repository
        self._stock_repository = stock_repository
        self._calculator = calculator
        self._quant_config_repository = quant_config_repository
        self._cache = cache
        self._ttl = settings.estimate_cache_seconds

    def estimate(
        self,
        fund_code: str,
        release_version: int,
        release_checksum: str,
        result_cache_seconds: int,
        quote_cache_seconds: int,
    ) -> EstimateData:
        code = fund_code.strip()
        config = self._load_calculation_config(release_version, release_checksum)
        return cache_aside(
            self._cache,
            f"fund_quant:fund:{code}:estimate:{config.config_release_version}:{config.config_release_checksum}:ttl:{result_cache_seconds}",
            result_cache_seconds,
            lambda: self._calculate(code, config, quote_cache_seconds),
            lambda value: value.model_dump(mode="json"),
            EstimateData.model_validate,
        )

    def holding_quotes(self, fund_code: str) -> list[HoldingRealtimeQuote]:
        code = fund_code.strip()
        holdings = self._fund_data_center_repository.load_estimate_inputs(code).holdings
        quotes = self._stock_repository.get_quotes(
            [holding.stock_code for holding in holdings]
        )
        return [
            HoldingRealtimeQuote(
                stockCode=holding.stock_code,
                stockName=holding.stock_name,
                weight=holding.weight,
                changePercent=(quotes[holding.stock_code].change_percent
                               if holding.stock_code in quotes else None),
                quoteTime=(quotes[holding.stock_code].update_time
                           if holding.stock_code in quotes else None),
            )
            for holding in holdings
        ]

    def _calculate(
        self,
        fund_code: str,
        config: EstimateCalculationConfig,
        quote_cache_seconds: int,
    ) -> EstimateData:
        inputs = self._fund_data_center_repository.load_estimate_inputs(fund_code)
        holdings = inputs.holdings
        if self._can_request_quotes(inputs.latest_nav, holdings):
            try:
                quotes = self._stock_repository.get_quotes(
                    [holding.stock_code for holding in holdings],
                    cache_seconds=quote_cache_seconds,
                )
            except DataNotFoundError:
                # 无任何可接受行情是预期的可估值边界，而非服务端异常。
                quotes = {}
        else:
            quotes = {}
        return self._calculator.calculate(
            fund_code,
            holdings,
            quotes,
            inputs.latest_nav,
            config,
            input_data_version=inputs.input_data_version,
        )

    @staticmethod
    def _can_request_quotes(
        latest_nav: FundNavPoint | None,
        holdings: list[FundHolding],
    ) -> bool:
        if latest_nav is None or latest_nav.quality_status != "NORMAL" or not holdings:
            return False
        return all(holding.quality_status == "NORMAL" for holding in holdings)

    def _load_calculation_config(
        self,
        release_version: int,
        release_checksum: str,
    ) -> EstimateCalculationConfig:
        release = self._quant_config_repository.load_release(release_version, release_checksum)
        return self._to_calculation_config(release)

    @staticmethod
    def _to_calculation_config(release: QuantConfigRelease) -> EstimateCalculationConfig:
        try:
            global_config = release.groups["GLOBAL_CONVENTIONS"].config
            estimate_group = release.groups["ESTIMATE"]
            estimate_config = estimate_group.config
            if global_config["timezone"] != "UTC" or global_config["percentage_unit"] != "PERCENT_POINT":
                raise ValueError("unsupported global convention")
            rounding_mode = str(global_config["rounding_mode"])
            if rounding_mode not in {"HALF_UP"}:
                raise ValueError("unsupported rounding mode")
            global_decimal_scale = int(global_config["decimal_scale"])
            if estimate_group.schemaVersion == 1:
                # V1 明确约定 GLOBAL_CONVENTIONS 的全局精度适用于所有输出字段。
                nav_decimal_scale = global_decimal_scale
                percentage_decimal_scale = global_decimal_scale
            elif estimate_group.schemaVersion == 2:
                nav_decimal_scale = int(estimate_config["nav_decimal_scale"])
                percentage_decimal_scale = int(estimate_config["percentage_decimal_scale"])
                if nav_decimal_scale != global_decimal_scale:
                    raise ValueError("nav precision must match global convention")
            else:
                raise ValueError("unsupported estimate schema")
            return EstimateCalculationConfig(
                config_release_version=release.releaseVersion,
                config_release_checksum=release.checksum,
                nav_decimal_scale=nav_decimal_scale,
                percentage_decimal_scale=percentage_decimal_scale,
                rounding_mode=rounding_mode,
                min_holding_coverage_percent=Decimal(str(estimate_config["min_holding_coverage_percent"])),
                max_quote_age_seconds=int(estimate_config["max_quote_age_seconds"]),
                estimate_config_version=estimate_group.configVersion,
                estimate_config_checksum=estimate_group.checksum,
                # 公式实现变更时必须同步提升代码算法版本；配置结构版本提供稳定的发布血缘。
                algorithm_version=f"{ESTIMATE_ALGORITHM_PREFIX}{estimate_group.schemaVersion}",
            )
        except (KeyError, TypeError, ValueError) as error:
            raise QuantConfigSchemaUnsupportedError("ESTIMATE", 1) from error
