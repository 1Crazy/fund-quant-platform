from typing import Generic, TypeVar

from pydantic import BaseModel, ConfigDict

T = TypeVar("T")


class ApiModel(BaseModel):
    model_config = ConfigDict(populate_by_name=True)


class ErrorDetail(ApiModel):
    code: str
    message: str
    retryable: bool = False
    category: str | None = None
    dataset: str | None = None
    retryAfterSeconds: int | None = None
    details: dict | None = None


class ApiEnvelope(ApiModel, Generic[T]):
    success: bool
    data: T | None = None
    error: ErrorDetail | None = None
    requestId: str
