class FundQuantError(Exception):
    """可安全返回给内部调用方的业务异常。"""

    def __init__(
        self,
        code: str,
        message: str,
        *,
        status_code: int = 400,
        retryable: bool = False,
    ) -> None:
        super().__init__(message)
        self.code = code
        self.message = message
        self.status_code = status_code
        self.retryable = retryable


class DataNotFoundError(FundQuantError):
    def __init__(self, message: str) -> None:
        super().__init__("DATA_NOT_FOUND", message, status_code=404)


class UpstreamDataError(FundQuantError):
    def __init__(self, message: str) -> None:
        super().__init__("UPSTREAM_DATA_ERROR", message, status_code=502, retryable=True)


