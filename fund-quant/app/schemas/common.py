from typing import Generic, TypeVar

from pydantic import BaseModel, ConfigDict

T = TypeVar("T")


class ApiModel(BaseModel):
    model_config = ConfigDict(populate_by_name=True)


class ErrorDetail(ApiModel):
    code: str
    message: str
    retryable: bool = False


class ApiEnvelope(ApiModel, Generic[T]):
    success: bool
    data: T | None = None
    error: ErrorDetail | None = None
    requestId: str

