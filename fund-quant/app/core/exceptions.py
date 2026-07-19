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

