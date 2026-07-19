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

/** 查询基金同步运行历史。 */
export async function getFundSyncRunsApi(params: FundApi.FundSyncRunParams) {
  const response = await requestClient.get<FundApi.RuoYiPage<FundApi.FundSyncRun>>(
    '/fund/sync/runs',
    {
      params,
      responseReturn: 'body' as const,
    },
  );
  return {
    items: (response.rows ?? []).map(normalizeSyncRun),
    total: response.total ?? 0,
  } satisfies FundApi.PageResult<FundApi.FundSyncRun>;
}

/** 查询一次同步运行详情。 */
export function getFundSyncRunDetailApi(runId: string) {
  return requestClient
    .get<FundApi.FundSyncRun>(`/fund/sync/runs/${runId}`)
    .then(normalizeSyncRun);
}

/** 查询当前同步状态摘要。 */
export function getFundSyncStatusApi() {
  return requestClient
    .get<FundApi.FundSyncStatusSummary>('/fund/sync/status')
    .then((value) => normalizeSyncStatus(value ?? {}));
}

/** 授权触发一次基金数据同步。 */
export function triggerFundSyncApi(payload: FundApi.FundManualSyncPayload) {
  return requestClient.post<FundApi.FundSyncRun>(
    '/fund/sync/trigger',
    payload,
    { timeout: 120_000 },
  ).then(normalizeManualSyncResult);
}

/** 重试失败或部分成功的同步运行。 */
export function retryFundSyncApi(runId: number | string) {
  return requestClient.post<FundApi.FundSyncRun>(
    `/fund/sync/runs/${runId}/retry`,
    undefined,
    { timeout: 120_000 },
  ).then(normalizeManualSyncResult);
}

/** 查询基金数据质量问题。 */
export async function getFundQualityIssuesApi(
  params: FundApi.FundQualityIssueParams,
) {
  const response = await requestClient.get<
    FundApi.RuoYiPage<FundApi.FundDataQualityIssue>
  >('/fund/sync/issues', {
    params,
    responseReturn: 'body' as const,
  });
  return {
    items: (response.rows ?? []).map(normalizeQualityIssue),
    total: response.total ?? 0,
  } satisfies FundApi.PageResult<FundApi.FundDataQualityIssue>;
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
    syncStatus:
      value.syncStatus ||
      (value as { syncState?: FundApi.FundSyncStatus }).syncState,
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
      marketValue: optionalNumber(holding.marketValue),
      sourceUpdatedAt: holding.sourceUpdatedAt || holding.sourceTime,
      weight: Number(holding.weight),
    })),
    latestNav: optionalNumber(value.latestNav),
    navSeries: (value.navSeries ?? []).map((point) => ({
      ...point,
      accumulatedNav: optionalNumber(point.accumulatedNav),
      dailyGrowthRate: optionalNumber(point.dailyGrowthRate),
      sourceUpdatedAt: point.sourceUpdatedAt || point.sourceTime,
      unitNav: Number(point.unitNav),
    })),
    qualityIssues: (value.qualityIssues ?? []).map(normalizeQualityIssue),
  };
}

function normalizeQualityIssue(
  value: FundApi.FundDataQualityIssue,
): FundApi.FundDataQualityIssue {
  return {
    ...value,
    discoveredAt: value.discoveredAt || value.detectedAt,
    reasonMessage: value.reasonMessage || value.rawSummary,
    sourceUpdatedAt:
      value.sourceUpdatedAt ||
      (value as { sourceTime?: string }).sourceTime,
  };
}

function normalizeSyncRun(value: FundApi.FundSyncRun): FundApi.FundSyncRun {
  return {
    ...value,
    durationMillis: optionalNumber(value.durationMillis),
    failedCount: optionalNumber(value.failedCount),
    rejectedCount: optionalNumber(value.rejectedCount),
    retryCount: optionalNumber(value.retryCount),
    runId: value.runId || value.fetchBatchId || String(value.id ?? ''),
    status: value.status || value.state || 'PENDING',
    syncScope:
      value.syncScope ||
      (value.scopeValue
        ? `${value.scopeType || 'SCOPE'}:${value.scopeValue}`
        : value.scopeType),
    fundCode:
      value.fundCode ||
      (value.scopeType === 'FUND_CODE' ? value.scopeValue : undefined),
    errorSummary: value.errorSummary || value.errorMessage,
    successCount: optionalNumber(value.successCount),
    totalCount: optionalNumber(value.totalCount),
  };
}

function normalizeSyncStatus(
  value: FundApi.FundSyncStatusSummary,
): FundApi.FundSyncStatusSummary {
  if (value.lastRun || value.activeRuns) {
    return {
      ...value,
      activeRuns: (value.activeRuns ?? []).map(normalizeSyncRun),
      failedCount: optionalNumber(value.failedCount),
      lastRun: value.lastRun ? normalizeSyncRun(value.lastRun) : undefined,
      partialCount: optionalNumber(value.partialCount),
      runningCount: optionalNumber(value.runningCount),
      staleCount: optionalNumber(value.staleCount),
    };
  }
  const lastRun = normalizeSyncRun({
    dataset: value.dataset || 'FUND_INFO',
    errorMessage: value.errorMessage,
    failedCount: value.failedCount,
    fetchBatchId: value.fetchBatchId,
    rejectedCount: value.rejectedCount,
    retryCount: value.retryCount,
    runId: value.fetchBatchId || '',
    state: value.state,
    status: value.state || 'PENDING',
    successCount: value.successCount,
    syncType: 'INCREMENTAL',
  });
  return {
    ...value,
    activeRuns: value.state === 'RUNNING' ? [lastRun] : [],
    failedCount: value.state === 'FAILED' ? 1 : 0,
    lastRun,
    partialCount: value.state === 'PARTIAL_SUCCESS' ? 1 : 0,
    runningCount: value.state === 'RUNNING' ? 1 : 0,
    staleCount: optionalNumber(value.staleCount),
  };
}

function normalizeManualSyncResult(
  value: FundApi.FundSyncRun,
): FundApi.FundManualSyncResult {
  const run = normalizeSyncRun(value);
  return {
    accepted: ['PENDING', 'RUNNING', 'SUCCESS', 'PARTIAL_SUCCESS'].includes(
      run.status,
    ),
    message: `同步任务 ${run.status}`,
    runId: run.runId,
    status: run.status,
  };
}

export type { FundApi } from './model';
