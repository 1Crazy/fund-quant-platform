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
  const response = await requestClient.get<
    FundApi.RuoYiPage<FundApi.FundListItem>
  >(
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
export function getFundDetailApi(
  code: string,
  period: FundApi.NavPeriod = '3m',
) {
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

/** 查询历史 NAV 所处区域。服务端固定活动量化发布版本并复用短期缓存。 */
export function getFundNavPositionApi(code: string) {
  return requestClient
    .get<FundApi.FundNavPosition>(`/fund/valuation/${code}`, {
      timeout: 30_000,
    })
    .then(normalizeNavPosition);
}

/** 提交全量历史位置计算，仅处理已有确认净值的基金。 */
export function refreshAllFundNavPositionsApi() {
  return requestClient
    .post<FundApi.FundNavPositionBatchStatus>(
      '/fund/valuation/batch/refresh',
      undefined,
      { timeout: 30_000 },
    )
    .then(normalizeNavPositionBatchStatus);
}

/** 查询全量历史位置计算进度。 */
export function getFundNavPositionBatchStatusApi() {
  return requestClient
    .get<FundApi.FundNavPositionBatchStatus>('/fund/valuation/batch/status')
    .then(normalizeNavPositionBatchStatus);
}

/** 手动刷新需要估值刷新权限，服务端会绕过当前 Redis 热缓存。 */
export function refreshFundEstimateApi(code: string) {
  return requestClient
    .post<FundApi.FundEstimate>(`/fund/estimate/${code}/refresh`, undefined, {
      timeout: 120_000,
    })
    .then(normalizeEstimate);
}

/** 读取当前节点的估值调度摘要，需要监控权限。 */
export function getFundEstimateScheduleStatusApi() {
  return requestClient
    .get<FundApi.FundEstimateScheduleStatus>('/fund/estimate/status')
    .then((value) => ({
      ...value,
      configReleaseVersion: optionalNumber(value.configReleaseVersion),
      failedCount: optionalNumber(value.failedCount),
      normalCount: optionalNumber(value.normalCount),
      partialCount: optionalNumber(value.partialCount),
      requestedCount: optionalNumber(value.requestedCount),
      unsupportedCount: optionalNumber(value.unsupportedCount),
    }));
}

/** 手动刷新时读取已披露持仓的实时行情，不将低覆盖持仓伪装成基金估值。 */
export function getFundHoldingQuotesApi(code: string) {
  return requestClient
    .get<FundApi.FundHoldingQuote[]>(`/fund/holding-quotes/${code}`, {
      timeout: 30_000,
    })
    .then((values) =>
      (values ?? []).map((value) => ({
        ...value,
        changePercent: optionalNumber(value.changePercent),
        weight: Number(value.weight),
      })),
    );
}

/** 查询基金同步运行历史。 */
export async function getFundSyncRunsApi(params: FundApi.FundSyncRunParams) {
  const response = await requestClient.get<
    FundApi.RuoYiPage<FundApi.FundSyncRun>
  >('/fund/sync/runs', {
    params,
    responseReturn: 'body' as const,
  });
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

/** 查询全量历史净值任务，区分真正运行中与服务重启后的中断状态。 */
export function getGlobalNavSyncStatusApi() {
  return requestClient
    .get<FundApi.FundGlobalNavSyncStatus>('/fund/sync/global-nav/status')
    .then(normalizeGlobalNavSyncStatus);
}

/** 暂停当前全量历史净值同步，已完成进度和游标会保留。 */
export function pauseGlobalNavSyncApi() {
  return requestClient
    .post<FundApi.FundSyncRun>('/fund/sync/global-nav/pause')
    .then(normalizeSyncRun);
}

/** 从当前游标继续已暂停的全量历史净值同步。 */
export function resumeGlobalNavSyncApi() {
  return requestClient
    .post<FundApi.FundSyncRun>('/fund/sync/global-nav/resume')
    .then(normalizeSyncRun);
}

/** 授权触发一次基金数据同步。 */
export function triggerFundSyncApi(payload: FundApi.FundManualSyncPayload) {
  return requestClient
    .post<FundApi.FundSyncRun>('/fund/sync/trigger', payload, {
      timeout: 120_000,
    })
    .then(normalizeManualSyncResult);
}

/** 重试失败或部分成功的同步运行。 */
export function retryFundSyncApi(runId: number | string) {
  return requestClient
    .post<FundApi.FundSyncRun>(`/fund/sync/runs/${runId}/retry`, undefined, {
      timeout: 120_000,
    })
    .then(normalizeManualSyncResult);
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

/** 查询量化配置分组概览。 */
export function getQuantConfigGroupsApi() {
  return requestClient
    .get<FundApi.QuantConfigGroup[]>('/fund/config/groups')
    .then((values) => (values ?? []).map(normalizeConfigGroup));
}

/** 查询配置组版本，RuoYi 分页结构在 API 层转换。 */
export async function getQuantConfigVersionsApi(
  params: FundApi.QuantConfigVersionParams,
) {
  const response = await requestClient.get<
    FundApi.RuoYiPage<FundApi.QuantConfigVersion>
  >('/fund/config/versions', {
    params,
    responseReturn: 'body' as const,
  });
  const rows = (response.rows ?? [])
    .map(normalizeConfigVersion)
    .filter((item) => !params.status || item.status === params.status);
  return {
    items: rows,
    total: params.status ? rows.length : (response.total ?? 0),
  } satisfies FundApi.PageResult<FundApi.QuantConfigVersion>;
}

/** 查询单个配置版本详情。 */
export function getQuantConfigVersionApi(id: number | string) {
  return requestClient
    .get<FundApi.QuantConfigVersion>(`/fund/config/versions/${id}`)
    .then(normalizeConfigVersion);
}

/** 创建配置草稿，配置值由服务端校验，不在浏览器中补默认数学参数。 */
export function createQuantConfigDraftApi(
  payload: FundApi.QuantConfigDraftPayload,
) {
  return requestClient
    .post<FundApi.QuantConfigVersion>(
      '/fund/config/drafts',
      toConfigDraftRequest(payload),
    )
    .then(normalizeConfigVersion);
}

/** 更新仍处于草稿状态的配置版本。 */
export function updateQuantConfigDraftApi(
  id: number | string,
  payload: FundApi.QuantConfigDraftPayload,
) {
  return requestClient
    .put<FundApi.QuantConfigVersion>(
      `/fund/config/drafts/${id}`,
      toConfigDraftRequest(payload),
    )
    .then(normalizeConfigVersion);
}

/** 请求 Java/Python 兼容校验结果。 */
export function validateQuantConfigDraftApi(
  id: number | string,
  revision: number,
) {
  return requestClient
    .post<FundApi.QuantConfigVersion>(
      `/fund/config/drafts/${id}/validate`,
      undefined,
      { params: { revision } },
    )
    .then(normalizeConfigVersion);
}

/** 查询两个配置版本的字段级差异。 */
export async function getQuantConfigDiffApi(
  params: FundApi.QuantConfigDiffParams,
) {
  return requestClient
    .get<FundApi.QuantConfigDiff>('/fund/config/versions/diff', { params })
    .then(normalizeConfigDiff);
}

/** 查询发布历史。 */
export async function getQuantConfigReleasesApi(
  params: FundApi.QuantConfigReleaseParams,
) {
  const response = await requestClient.get<FundApi.QuantConfigRelease[]>(
    '/fund/config/releases',
  );
  const filtered = (response ?? [])
    .map(normalizeConfigRelease)
    .filter((item) => !params.status || item.status === params.status);
  const start = (params.pageNum - 1) * params.pageSize;
  return {
    items: filtered.slice(start, start + params.pageSize),
    total: filtered.length,
  } satisfies FundApi.PageResult<FundApi.QuantConfigRelease>;
}

/** 发布一组已校验配置版本。 */
export function publishQuantConfigReleaseApi(
  payload: FundApi.QuantConfigPublishPayload,
) {
  return requestClient
    .post<FundApi.QuantConfigRelease>(
      '/fund/config/releases',
      toConfigReleaseRequest(payload),
    )
    .then(normalizeConfigRelease);
}

/** 回滚通过创建更高的新发布版本完成。 */
export function rollbackQuantConfigReleaseApi(
  payload: FundApi.QuantConfigRollbackPayload,
) {
  return requestClient
    .post<FundApi.QuantConfigRelease>(
      `/fund/config/releases/${payload.sourceReleaseVersion}/rollback`,
      toConfigReleaseRequest(payload),
    )
    .then(normalizeConfigRelease);
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
    configReleaseVersion: optionalNumber(value.configReleaseVersion),
    contributions: (value.contributions ?? []).map((contribution) => ({
      ...contribution,
      changePercent: Number(contribution.changePercent),
      contribution: Number(contribution.contribution),
      weight: Number(contribution.weight),
    })),
    estimateGrowthRate: optionalNumber(value.estimateGrowthRate),
    estimateNav: optionalNumber(value.estimateNav),
    estimateConfigVersion: optionalNumber(value.estimateConfigVersion),
    holdingCoverageRate: optionalNumber(value.holdingCoverageRate),
    missingQuoteCount: optionalNumber(value.missingQuoteCount),
    previousNav: optionalNumber(value.previousNav),
    quoteCoverageRate: optionalNumber(value.quoteCoverageRate),
  };
}

function normalizeNavPosition(
  value: FundApi.FundNavPosition,
): FundApi.FundNavPosition {
  return {
    ...value,
    configReleaseVersion: optionalNumber(value.configReleaseVersion),
    currentDrawdown: optionalNumber(value.currentDrawdown),
    indicators: (value.indicators ?? []).map((indicator) => ({
      ...indicator,
      value: optionalNumber(indicator.value),
    })),
    ma60Deviation: optionalNumber(value.ma60Deviation),
    ma120Deviation: optionalNumber(value.ma120Deviation),
    ma250Deviation: optionalNumber(value.ma250Deviation),
    navPercentile: optionalNumber(value.navPercentile),
    navPositionConfigVersion: optionalNumber(value.navPositionConfigVersion),
    navPositionScore: optionalNumber(value.navPositionScore),
    reasons: (value.reasons ?? []).map((reason) => ({
      ...reason,
      actual: optionalNumber(reason.actual),
      required: optionalNumber(reason.required),
    })),
    sampleCount: optionalNumber(value.sampleCount),
  };
}

function normalizeNavPositionBatchStatus(
  value: FundApi.FundNavPositionBatchStatus,
): FundApi.FundNavPositionBatchStatus {
  return {
    ...value,
    configReleaseVersion: optionalNumber(value.configReleaseVersion),
    failedCount: optionalNumber(value.failedCount) ?? 0,
    normalCount: optionalNumber(value.normalCount) ?? 0,
    processedCount: optionalNumber(value.processedCount) ?? 0,
    requestedCount: optionalNumber(value.requestedCount) ?? 0,
    unavailableCount: optionalNumber(value.unavailableCount) ?? 0,
  };
}

function normalizeListItem(value: FundApi.FundListItem): FundApi.FundListItem {
  return {
    ...value,
    estimateGrowthRate: optionalNumber(value.estimateGrowthRate),
    estimateHoldingCoverageRate: optionalNumber(value.estimateHoldingCoverageRate),
    estimateMissingQuoteCount: optionalNumber(value.estimateMissingQuoteCount),
    estimateQuoteCoverageRate: optionalNumber(value.estimateQuoteCoverageRate),
    estimateNav: optionalNumber(value.estimateNav),
    latestNav: optionalNumber(value.latestNav),
    navPositionScore: optionalNumber(value.navPositionScore),
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
      value.sourceUpdatedAt || (value as { sourceTime?: string }).sourceTime,
  };
}

function normalizeConfigGroup(
  value: FundApi.QuantConfigGroup,
): FundApi.QuantConfigGroup {
  return {
    ...value,
    activeConfigVersion: optionalNumber(value.activeConfigVersion),
    activeReleaseVersion: optionalNumber(value.activeReleaseVersion),
    latestConfigVersion: optionalNumber(value.latestConfigVersion),
    schemaVersion: optionalNumber(value.schemaVersion),
  };
}

function normalizeConfigVersion(
  value: FundApi.QuantConfigVersion,
): FundApi.QuantConfigVersion {
  const normalizedJson = normalizeJsonValue(value.configJson);
  return {
    ...value,
    configJson: normalizedJson,
    configVersion: optionalNumber(value.configVersion),
    createdAt:
      value.createdAt || (value as { createTime?: string }).createTime,
    id: value.id,
    normalizedJson:
      value.normalizedJson ||
      (value as { canonicalJson?: string }).canonicalJson ||
      stringifyJson(normalizedJson),
    revision: optionalNumber(value.revision),
    schemaVersion: Number(value.schemaVersion),
    validation: value.validation
      ? normalizeValidationResult(value.validation)
      : undefined,
    updatedAt:
      value.updatedAt || (value as { updateTime?: string }).updateTime,
  };
}

function normalizeValidationResult(
  value: FundApi.QuantConfigValidationResult,
): FundApi.QuantConfigValidationResult {
  const issues = [
    ...(value.issues ?? []),
    ...(value.errors ?? []),
    ...(value.warnings ?? []),
  ];
  return {
    ...value,
    canonicalJson: value.canonicalJson || stringifyJson((value as {
      configJson?: FundApi.JsonValue;
    }).configJson),
    errors: issues.filter((issue) => issue.level === 'ERROR'),
    issues,
    passed: Boolean(value.passed),
    warnings: issues.filter((issue) => issue.level === 'WARN'),
  };
}

function normalizeConfigDiff(
  value: FundApi.QuantConfigDiff,
): FundApi.QuantConfigDiff {
  return {
    ...value,
    baseVersion: optionalNumber(value.baseVersion),
    changes: (value.changes ?? []).map((change) => ({
      ...change,
      after: normalizeJsonValue(change.after),
      before: normalizeJsonValue(change.before),
    })),
    targetVersion: optionalNumber(value.targetVersion),
  };
}

function normalizeConfigRelease(
  value: FundApi.QuantConfigRelease,
): FundApi.QuantConfigRelease {
  return {
    ...value,
    items: (value.items ?? []).map((item) => ({
      ...item,
      configChecksum:
        item.configChecksum || (item as { checksum?: string }).checksum,
      configVersion: Number(item.configVersion),
      displayName: item.displayName,
      id: item.id,
      schemaVersion: optionalNumber(item.schemaVersion),
    })),
    publishedAt:
      value.publishedAt || (value as { createTime?: string }).createTime,
    releaseVersion: Number(value.releaseVersion),
    remark:
      value.remark || (value as { changeSummary?: string }).changeSummary,
    rollbackOfReleaseVersion: optionalNumber(value.rollbackOfReleaseVersion),
  };
}

function toConfigDraftRequest(payload: FundApi.QuantConfigDraftPayload) {
  return {
    configCode: payload.configCode,
    configJson: stringifyJson(payload.configJson),
    remark: payload.remark,
    revision: payload.revision ?? 0,
    schemaVersion: payload.schemaVersion,
  };
}

function toConfigReleaseRequest(
  payload:
    | FundApi.QuantConfigPublishPayload
    | FundApi.QuantConfigRollbackPayload,
) {
  return {
    changeSummary: payload.changeSummary,
    configVersionIds: payload.configVersionIds,
    effectiveFrom: normalizeOffsetDateTime(payload.effectiveFrom),
  };
}

function normalizeOffsetDateTime(value?: string) {
  if (!value) return undefined;
  if (/[zZ]|[+-]\d{2}:?\d{2}$/.test(value)) return value;
  return new Date(value.replace(' ', 'T')).toISOString();
}

function normalizeJsonValue(value?: FundApi.JsonValue): FundApi.JsonValue {
  if (typeof value === 'string') {
    try {
      return JSON.parse(value) as FundApi.JsonValue;
    } catch {
      return value;
    }
  }
  return value ?? {};
}

function stringifyJson(value: unknown) {
  if (typeof value === 'string') return value;
  if (value == null) return '';
  return JSON.stringify(value, null, 2);
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

function normalizeGlobalNavSyncStatus(
  value: FundApi.FundGlobalNavSyncStatus,
): FundApi.FundGlobalNavSyncStatus {
  return {
    ...value,
    failedCount: optionalNumber(value.failedCount),
    id: optionalNumber(value.id),
    processedFundCount: optionalNumber(value.processedFundCount) ?? 0,
    rejectedCount: optionalNumber(value.rejectedCount),
    successCount: optionalNumber(value.successCount),
    totalFundCount: optionalNumber(value.totalFundCount) ?? 0,
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
