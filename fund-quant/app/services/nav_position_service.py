from datetime import date
from decimal import Decimal

from app.calculators.nav_position_calculator import (
    NavPositionCalculationConfig,
    NavPositionCalculator,
)
from app.core.exceptions import QuantConfigSchemaUnsupportedError
from app.repositories.fund_data_center_repository import FundDataCenterRepository
from app.repositories.quant_config_repository import QuantConfigRepository
from app.schemas.nav_position import NavPositionData
from app.schemas.quant_config import QuantConfigRelease


class NavPositionService:
    def __init__(
        self,
        fund_data_center_repository: FundDataCenterRepository,
        quant_config_repository: QuantConfigRepository,
        calculator: NavPositionCalculator,
    ) -> None:
        self._fund_data_center_repository = fund_data_center_repository
        self._quant_config_repository = quant_config_repository
        self._calculator = calculator

    def calculate(
        self,
        fund_code: str,
        release_version: int,
        release_checksum: str,
        trade_date: date | None = None,
    ) -> NavPositionData:
        config = self._calculation_config(release_version, release_checksum)
        inputs = self._fund_data_center_repository.load_nav_position_inputs(
            fund_code.strip(), config.history_window, trade_date
        )
        return self._calculator.calculate(
            fund_code.strip(),
            inputs.nav_points,
            config,
            input_data_version=inputs.input_data_version,
        )

    def _calculation_config(
        self, release_version: int, release_checksum: str
    ) -> NavPositionCalculationConfig:
        release = self._quant_config_repository.load_release(release_version, release_checksum)
        return self._to_calculation_config(release)

    @staticmethod
    def _to_calculation_config(release: QuantConfigRelease) -> NavPositionCalculationConfig:
        try:
            global_config = release.groups["GLOBAL_CONVENTIONS"].config
            nav_position_group = release.groups["NAV_POSITION"]
            nav_position_config = nav_position_group.config
            if global_config["timezone"] != "UTC" or global_config["percentage_unit"] != "PERCENT_POINT":
                raise ValueError("unsupported global convention")
            rounding_mode = str(global_config["rounding_mode"])
            if rounding_mode != "HALF_UP":
                raise ValueError("unsupported rounding mode")
            thresholds = tuple(
                Decimal(str(value)) for value in nav_position_config["region_thresholds"]
            )
            if (
                nav_position_group.schemaVersion != 1
                or len(thresholds) != 3
                or not Decimal("0") < thresholds[0] < thresholds[1] < thresholds[2] < Decimal("100")
            ):
                raise ValueError("unsupported NAV_POSITION schema")
            return NavPositionCalculationConfig(
                config_release_version=release.releaseVersion,
                config_release_checksum=release.checksum,
                nav_position_config_version=nav_position_group.configVersion,
                nav_position_config_checksum=nav_position_group.checksum,
                history_window=int(nav_position_config["history_window"]),
                min_sample_size=int(nav_position_config["min_sample_size"]),
                region_thresholds=(thresholds[0], thresholds[1], thresholds[2]),
                decimal_scale=int(global_config["decimal_scale"]),
                rounding_mode=rounding_mode,
            )
        except (KeyError, TypeError, ValueError) as error:
            raise QuantConfigSchemaUnsupportedError("NAV_POSITION", 1) from error
