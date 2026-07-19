import { requestClient } from '#/api/request';

import type { FundApi } from './model';

/**
 * 查询基金分页列表。
 * RuoYi 的分页结构使用 rows/total，API 层统一转换为前端表格结构，避免页面感知后端框架细节。
 */
export async function getFundListApi(params: FundApi.FundListParams) {
  const exactCode = /^\d{6}$/.test(params.fundCode?.trim() ?? '');
  const remoteSearch = Boolean(params.fundName?.trim());
  const requestOptions = {
    params,
    responseReturn: 'body' as const,
    ...(exactCode || remoteSearch ? { timeout: 120_000 } : {}),
  };
  const response = await requestClient.get<FundApi.RuoYiPage<FundApi.FundListItem>>(
    '/fund/list',
    // 精确代码或名称搜索会由 Java 同步 AkShare 冷数据，普通分页仍沿用全局 10 秒超时。
    requestOptions,
  );
  return {
    items: (response.rows ?? []).map(normalizeListItem),
    total: response.total ?? 0,
  } satisfies FundApi.PageResult<FundApi.FundListItem>;
}

/** 查询基金详情及净值序列。 */
export function getFundDetailApi(code: string, period: FundApi.NavPeriod = '3m') {
  return requestClient
    .get<FundApi.FundDetail>(`/fund/detail/${code}`, {
      params: { period },
      timeout: 120_000,
    })
    .then(normalizeDetail);
}

/** 查询最新实时估值。 */
export function getFundEstimateApi(code: string) {
  return requestClient
    .get<FundApi.FundEstimate>(`/fund/estimate/${code}`, { timeout: 120_000 })
    .then(normalizeEstimate);
}

/** RuoYi 为避免金额精度丢失会把 BigDecimal 序列化为字符串，API 层统一恢复为 number。 */
function optionalNumber(value: unknown) {
  if (value == null || value === '') return undefined;
  const result = Number(value);
  return Number.isFinite(result) ? result : undefined;
}

function normalizeEstimate(value: FundApi.FundEstimate): FundApi.FundEstimate {
  return {
    ...value,
    estimateGrowthRate: optionalNumber(value.estimateGrowthRate),
    estimateNav: optionalNumber(value.estimateNav),
    previousNav: optionalNumber(value.previousNav),
  };
}

function normalizeListItem(value: FundApi.FundListItem): FundApi.FundListItem {
  return {
    ...value,
    estimateGrowthRate: optionalNumber(value.estimateGrowthRate),
    estimateNav: optionalNumber(value.estimateNav),
    latestNav: optionalNumber(value.latestNav),
  };
}

function normalizeDetail(value: FundApi.FundDetail): FundApi.FundDetail {
  return {
    ...value,
    estimate: value.estimate ? normalizeEstimate(value.estimate) : undefined,
    fundScale: optionalNumber(value.fundScale),
    holdingCoverageRate: optionalNumber(value.holdingCoverageRate),
    holdings: (value.holdings ?? []).map((holding) => ({
      ...holding,
      weight: Number(holding.weight),
    })),
    latestNav: optionalNumber(value.latestNav),
    navSeries: (value.navSeries ?? []).map((point) => ({
      ...point,
      accumulatedNav: optionalNumber(point.accumulatedNav),
      dailyGrowthRate: optionalNumber(point.dailyGrowthRate),
      unitNav: Number(point.unitNav),
    })),
  };
}

export type { FundApi } from './model';
