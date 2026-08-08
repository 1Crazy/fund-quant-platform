from typing import Annotated

from fastapi import APIRouter, Depends, Request
from starlette.concurrency import run_in_threadpool

from app.api.dependencies import get_quant_config_repository
from app.repositories.quant_config_repository import QuantConfigRepository, sha256
from app.schemas.common import ApiEnvelope
from app.schemas.quant_config import QuantConfigValidationData, QuantConfigValidationRequest

router = APIRouter(prefix="/internal/v1/quant-config", tags=["内部量化配置"])


@router.post("/validate", response_model=ApiEnvelope[QuantConfigValidationData])
async def validate_quant_config(
    request: Request,
    payload: QuantConfigValidationRequest,
    repository: Annotated[QuantConfigRepository, Depends(get_quant_config_repository)],
) -> ApiEnvelope[QuantConfigValidationData]:
    """供 Java 在发布前复核 Python 计算端能否接受精确配置版本。"""
    errors: list[str] = []
    for item in payload.configs:
        errors.extend(await run_in_threadpool(repository.validate_item, item))
    checksum_parts = sorted(
        f"{item.configCode}:{item.configVersion}:{item.checksum}" for item in payload.configs
    )
    expected_release_checksum = sha256("\n".join(checksum_parts))
    if payload.releaseChecksum != expected_release_checksum:
        errors.append("release checksum mismatch")
    return ApiEnvelope(
        success=True,
        data=QuantConfigValidationData(valid=not errors, errors=errors),
        requestId=request.state.request_id,
    )
