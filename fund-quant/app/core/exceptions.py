class FundQuantError(Exception):
    """可安全返回给内部调用方的业务异常。"""

    def __init__(
        self,
        code: str,
        message: str,
        *,
        status_code: int = 400,
        retryable: bool = False,
        category: str | None = None,
        dataset: str | None = None,
        retry_after_seconds: int | None = None,
        details: dict | None = None,
    ) -> None:
        super().__init__(message)
        self.code = code
        self.message = message
        self.status_code = status_code
        self.retryable = retryable
        self.category = category
        self.dataset = dataset
        self.retry_after_seconds = retry_after_seconds
        self.details = details or {}


class DataNotFoundError(FundQuantError):
    def __init__(self, message: str) -> None:
        super().__init__("DATA_NOT_FOUND", message, status_code=404)


class DataCenterUnavailableError(FundQuantError):
    """共享数据中心只读输入暂不可用，调用方可降级到最后成功快照。"""

    def __init__(self, message: str, *, retryable: bool = True) -> None:
        super().__init__(
            "DATA_CENTER_UNAVAILABLE",
            message,
            status_code=503,
            retryable=retryable,
            category="DATA_CENTER",
        )


class UpstreamDataError(FundQuantError):
    def __init__(
        self,
        message: str,
        *,
        code: str = "UPSTREAM_DATA_ERROR",
        retryable: bool = True,
        dataset: str | None = None,
        retry_after_seconds: int | None = None,
        details: dict | None = None,
    ) -> None:
        super().__init__(
            code,
            message,
            status_code=502,
            retryable=retryable,
            category="UPSTREAM",
            dataset=dataset,
            retry_after_seconds=retry_after_seconds,
            details=details,
        )


class ProviderSchemaChangedError(UpstreamDataError):
    def __init__(self, message: str, *, dataset: str | None = None, details: dict | None = None) -> None:
        super().__init__(
            message,
            code="DATA_PROVIDER_SCHEMA_CHANGED",
            retryable=False,
            dataset=dataset,
            details=details,
        )


class QuantConfigError(FundQuantError):
    """配置发布不存在、不兼容或校验和不一致时的严格失败。"""

    def __init__(self, code: str, message: str) -> None:
        super().__init__(code, message, status_code=409, retryable=False, category="QUANT_CONFIG")


class QuantConfigNotPublishedError(QuantConfigError):
    def __init__(self) -> None:
        super().__init__("QUANT_CONFIG_NOT_PUBLISHED", "指定的量化配置发布版本不存在或尚未生效")


class QuantConfigVersionMismatchError(QuantConfigError):
    def __init__(self) -> None:
        super().__init__("QUANT_CONFIG_VERSION_MISMATCH", "量化配置发布条目版本不一致")


class QuantConfigChecksumMismatchError(QuantConfigError):
    def __init__(self) -> None:
        super().__init__("QUANT_CONFIG_CHECKSUM_MISMATCH", "量化配置校验和不一致")


class QuantConfigSchemaUnsupportedError(QuantConfigError):
    def __init__(self, config_code: str, schema_version: int) -> None:
        super().__init__(
            "QUANT_CONFIG_SCHEMA_UNSUPPORTED",
            f"不支持量化配置 {config_code} 的结构版本 {schema_version}",
        )
