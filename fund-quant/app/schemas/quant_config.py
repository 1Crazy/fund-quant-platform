from __future__ import annotations

from pydantic import Field, field_validator, model_validator

from app.schemas.common import ApiModel


SUPPORTED_CONFIG_CODES = frozenset(
    {
        "GLOBAL_CONVENTIONS",
        "ESTIMATE",
        "TREND",
        "MOVING_AVERAGE",
        "RSI_MACD",
        "NAV_POSITION",
        "FACTOR",
        "FUND_RISK",
        "PORTFOLIO_RISK",
        "BACKTEST",
    }
)


class QuantConfigValidationItem(ApiModel):
    configCode: str
    configVersion: int = Field(ge=1)
    schemaVersion: int = Field(ge=1)
    configJson: str = Field(min_length=2, max_length=20_000)
    checksum: str = Field(pattern=r"^[0-9a-f]{64}$")

    @field_validator("configCode")
    @classmethod
    def config_code_must_be_supported(cls, value: str) -> str:
        if value not in SUPPORTED_CONFIG_CODES:
            raise ValueError("unsupported quant config code")
        return value


class QuantConfigValidationRequest(ApiModel):
    configs: list[QuantConfigValidationItem] = Field(min_length=10, max_length=10)
    releaseChecksum: str = Field(pattern=r"^[0-9a-f]{64}$")

    @model_validator(mode="after")
    def configs_must_cover_each_group_once(self) -> "QuantConfigValidationRequest":
        codes = [item.configCode for item in self.configs]
        if len(set(codes)) != len(codes) or set(codes) != SUPPORTED_CONFIG_CODES:
            raise ValueError("validation request must include each quant config group exactly once")
        return self


class QuantConfigValidationData(ApiModel):
    valid: bool
    errors: list[str] = []


class QuantConfigGroup(ApiModel):
    configCode: str
    configVersion: int
    schemaVersion: int
    checksum: str
    config: dict


class QuantConfigRelease(ApiModel):
    releaseVersion: int
    checksum: str
    groups: dict[str, QuantConfigGroup]
