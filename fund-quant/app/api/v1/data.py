from typing import Annotated

from fastapi import APIRouter, Depends, Path, Query, Request
from starlette.concurrency import run_in_threadpool

from app.api.dependencies import get_estimate_service, get_fund_data_service
from app.schemas.common import ApiEnvelope
from app.schemas.estimate import EstimateData
from app.schemas.market import FundHolding, FundInfo, FundNavPoint, StockQuote
from app.services.estimate_service import EstimateService
from app.services.fund_data_service import FundDataService

router = APIRouter(prefix="/internal/v1/data", tags=["内部基金数据"])
FundCode = Annotated[str, Path(pattern=r"^\d{6}$", description="六位基金代码")]
StockCode = Annotated[str, Path(pattern=r"^\d{6}$", description="六位 A 股代码")]


@router.get("/stock/{code}", response_model=ApiEnvelope[StockQuote])
async def get_stock(
    request: Request,
    code: StockCode,
    service: Annotated[FundDataService, Depends(get_fund_data_service)],
) -> ApiEnvelope[StockQuote]:
    data = await run_in_threadpool(service.get_stock, code)
    return ApiEnvelope(
        success=True,
        data=data,
        requestId=request.state.request_id,
    )


@router.get("/estimate/{code}", response_model=ApiEnvelope[EstimateData])
async def get_estimate(
    request: Request,
    code: FundCode,
    service: Annotated[EstimateService, Depends(get_estimate_service)],
) -> ApiEnvelope[EstimateData]:
    data = await run_in_threadpool(service.estimate, code)
    return ApiEnvelope(success=True, data=data, requestId=request.state.request_id)


@router.get("/nav/{code}", response_model=ApiEnvelope[list[FundNavPoint]])
async def get_nav(
    request: Request,
    code: FundCode,
    service: Annotated[FundDataService, Depends(get_fund_data_service)],
    days: Annotated[int, Query(ge=0, le=5000)] = 120,
) -> ApiEnvelope[list[FundNavPoint]]:
    data = await run_in_threadpool(service.get_nav, code, days)
    return ApiEnvelope(
        success=True,
        data=data,
        requestId=request.state.request_id,
    )


@router.get("/funds", response_model=ApiEnvelope[list[FundInfo]])
async def search_funds(
    request: Request,
    service: Annotated[FundDataService, Depends(get_fund_data_service)],
    keyword: Annotated[str, Query(min_length=1, max_length=100)],
    limit: Annotated[int, Query(ge=1, le=100)] = 50,
) -> ApiEnvelope[list[FundInfo]]:
    data = await run_in_threadpool(service.search_funds, keyword, limit)
    return ApiEnvelope(success=True, data=data, requestId=request.state.request_id)


@router.get("/holdings/{code}", response_model=ApiEnvelope[list[FundHolding]])
async def get_holdings(
    request: Request,
    code: FundCode,
    service: Annotated[FundDataService, Depends(get_fund_data_service)],
) -> ApiEnvelope[list[FundHolding]]:
    data = await run_in_threadpool(service.get_holdings, code)
    return ApiEnvelope(
        success=True,
        data=data,
        requestId=request.state.request_id,
    )


@router.get("/fund/{code}", response_model=ApiEnvelope[FundInfo])
async def get_fund(
    request: Request,
    code: FundCode,
    service: Annotated[FundDataService, Depends(get_fund_data_service)],
) -> ApiEnvelope[FundInfo]:
    data = await run_in_threadpool(service.get_fund, code)
    return ApiEnvelope(
        success=True,
        data=data,
        requestId=request.state.request_id,
    )
