import logging
import uuid
from collections.abc import Awaitable, Callable

from fastapi import FastAPI, Request
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse, Response

from app.api.dependencies import get_cache
from app.api.v1.data import router as data_router
from app.core.config import get_settings
from app.core.exceptions import FundQuantError
from app.schemas.common import ApiEnvelope, ErrorDetail
from app.schemas.estimate import HealthData

settings = get_settings()
logging.basicConfig(
    level=getattr(logging, settings.log_level.upper(), logging.INFO),
    format="%(asctime)s %(levelname)s %(name)s - %(message)s",
)

app = FastAPI(
    title="fund-quant",
    description="基金量化决策系统内部数据与计算服务",
    version="0.1.0",
    docs_url="/docs" if settings.environment != "prod" else None,
    redoc_url=None,
)


@app.middleware("http")
async def request_context(
    request: Request,
    call_next: Callable[[Request], Awaitable[Response]],
) -> Response:
    request.state.request_id = request.headers.get("X-Request-Id") or uuid.uuid4().hex
    response = await call_next(request)
    response.headers["X-Request-Id"] = request.state.request_id
    return response


@app.exception_handler(FundQuantError)
async def handle_business_error(request: Request, exc: FundQuantError) -> JSONResponse:
    envelope = ApiEnvelope[None](
        success=False,
        error=ErrorDetail(code=exc.code, message=exc.message, retryable=exc.retryable),
        requestId=getattr(request.state, "request_id", uuid.uuid4().hex),
    )
    return JSONResponse(status_code=exc.status_code, content=envelope.model_dump(mode="json"))


@app.exception_handler(RequestValidationError)
async def handle_validation_error(request: Request, exc: RequestValidationError) -> JSONResponse:
    envelope = ApiEnvelope[None](
        success=False,
        error=ErrorDetail(code="VALIDATION_ERROR", message=str(exc), retryable=False),
        requestId=getattr(request.state, "request_id", uuid.uuid4().hex),
    )
    return JSONResponse(status_code=422, content=envelope.model_dump(mode="json"))


@app.exception_handler(Exception)
async def handle_unexpected_error(request: Request, exc: Exception) -> JSONResponse:
    logging.getLogger(__name__).exception("未处理异常", exc_info=exc)
    envelope = ApiEnvelope[None](
        success=False,
        error=ErrorDetail(code="INTERNAL_ERROR", message="量化服务内部错误", retryable=True),
        requestId=getattr(request.state, "request_id", uuid.uuid4().hex),
    )
    return JSONResponse(status_code=500, content=envelope.model_dump(mode="json"))


@app.get("/health", response_model=ApiEnvelope[HealthData], tags=["健康检查"])
def health(request: Request) -> ApiEnvelope[HealthData]:
    redis_status = "UP" if get_cache().ping() else "DEGRADED"
    return ApiEnvelope(
        success=True,
        data=HealthData(status="UP", redis=redis_status),
        requestId=request.state.request_id,
    )


app.include_router(data_router)
