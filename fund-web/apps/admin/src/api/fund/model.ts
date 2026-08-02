/**
 * 基金实时估值模块的前端数据契约。
 * 百分比字段均使用百分数口径，例如 1.23 表示 1.23%。
 */
export namespace FundApi {
  export type FundDataset = 'fund_holding' | 'fund_info' | 'fund_nav' | string;

  export type FundQualityStatus =
    | 'EMPTY'
    | 'FAILED'
    | 'NORMAL'
    | 'PARTIAL'
    | 'REJECTED'
    | 'STALE'
    | string;

  export type FundSyncStatus =
    | 'CANCELLED'
    | 'FAILED'
    | 'PARTIAL_SUCCESS'
    | 'PENDING'
    | 'RUNNING'
    | 'SUCCESS'
    | string;

  export type FundSyncType =
    | 'FULL_INIT'
    | 'HOLDING_BACKFILL'
    | 'INCREMENTAL'
    | 'LAZY_LOAD'
    | 'NAV_BACKFILL'
    | string;

  export interface FundListParams {
    fundCode?: string;
    fundName?: string;
    fundType?: string;
    qualityStatus?: FundQualityStatus | '';
    pageNum: number;
    pageSize: number;
    source?: string;
    syncStatus?: FundSyncStatus | '';
  }

  export interface FundEstimate {
    contributions?: FundEstimateContribution[];
    estimateGrowthRate?: number;
    estimateNav?: number;
    estimateTime?: string;
    fundCode: string;
    holdingCoverageRate?: number;
    isStale: boolean;
    previousNav?: number;
    previousNavDate?: string;
    reportPeriod?: string;
    source?: string;
  }

  export interface FundEstimateContribution {
    changePercent: number;
    contribution: number;
    quoteTime?: string;
    stockCode: string;
    stockName: string;
    weight: number;
  }

  export interface FundListItem {
    asOfDate?: string;
    dataVersion?: string;
    estimateGrowthRate?: number;
    estimateNav?: number;
    estimateTime?: string;
    fundCode: string;
    fundName: string;
    fundType: string;
    isStale: boolean;
    latestHoldingDataVersion?: string;
    latestHoldingReportDate?: string;
    latestNav?: number;
    latestNavDataVersion?: string;
    latestNavQualityStatus?: FundQualityStatus;
    navDate?: string;
    qualityStatus?: FundQualityStatus;
    source?: string;
    sourceTime?: string;
    sourceUpdatedAt?: string;
    syncStatus?: FundSyncStatus;
  }

  export interface FundNavPoint {
    accumulatedNav?: number;
    dailyGrowthRate?: number;
    dataVersion?: string;
    date: string;
    qualityStatus?: FundQualityStatus;
    source?: string;
    sourceUpdatedAt?: string;
    unitNav: number;
  }

  export interface FundHolding {
    dataVersion?: string;
    marketValue?: number;
    qualityStatus?: FundQualityStatus;
    rankNo?: number;
    reportDate?: string;
    reportPeriod: string;
    source?: string;
    sourceTime?: string;
    sourceUpdatedAt?: string;
    stockCode: string;
    stockName: string;
    weight: number;
  }

  export interface FundHoldingQuote {
    changePercent?: number;
    quoteTime?: string;
    stockCode: string;
    stockName: string;
    weight: number;
  }

  export interface FundDataQualityIssue {
    businessDate?: string;
    dataset: FundDataset;
    detectedAt?: string;
    discoveredAt?: string;
    fundCode?: string;
    qualityStatus?: FundQualityStatus;
    issueStatus?: string;
    rawSummary?: string;
    rawValueDigest?: string;
    reasonCode: string;
    reasonMessage?: string;
    recordKey?: string;
    runId?: string;
    source?: string;
    sourceTime?: string;
    sourceUpdatedAt?: string;
  }

  export type NavPeriod = '1m' | '1y' | '3m' | '3y' | '5y' | '6m' | 'all';

  export interface FundDetail {
    asOfDate?: string;
    benchmark?: string;
    dataVersion?: string;
    establishDate?: string;
    estimate?: FundEstimate;
    fundCode: string;
    fundName: string;
    fundScale?: number;
    holdingCoverageRate?: number;
    fundType: string;
    holdingNote?: string;
    holdings: FundHolding[];
    latestNav?: number;
    latestHoldingDataVersion?: string;
    latestHoldingReportDate?: string;
    managerName?: string;
    navDate?: string;
    navSeries: FundNavPoint[];
    qualityIssues?: FundDataQualityIssue[];
    qualityStatus?: FundQualityStatus;
    riskLevel?: string;
    source?: string;
    sourceUpdatedAt?: string;
    syncStatus?: FundSyncStatus;
    custodianName?: string;
  }

  export interface PageResult<T> {
    items: T[];
    total: number;
  }

  export interface FundSyncRunParams {
    dataset?: FundDataset | '';
    fundCode?: string;
    pageNum: number;
    pageSize: number;
    status?: FundSyncStatus | '';
    syncType?: FundSyncType | '';
  }

  export interface FundSyncRun {
    id?: number;
    cursorValue?: string;
    dataset: FundDataset;
    dataVersion?: string;
    durationMillis?: number;
    errorCode?: string;
    errorMessage?: string;
    errorSummary?: string;
    failedCount?: number;
    finishedAt?: string;
    fundCode?: string;
    partitionKey?: string;
    rangeEndDate?: string;
    rangeStartDate?: string;
    rejectedCount?: number;
    retryCount?: number;
    runId: string;
    scopeType?: string;
    scopeValue?: string;
    state?: FundSyncStatus;
    startedAt?: string;
    status: FundSyncStatus;
    successCount?: number;
    syncScope?: string;
    syncType: FundSyncType;
    totalCount?: number;
  }

  export interface FundSyncStatusSummary {
    activeRuns?: FundSyncRun[];
    failedCount?: number;
    lastRun?: FundSyncRun;
    partialCount?: number;
    runningCount?: number;
    staleCount?: number;
    updatedAt?: string;
    state?: FundSyncStatus;
    dataset?: FundDataset;
    fetchBatchId?: string;
    successCount?: number;
    rejectedCount?: number;
    retryCount?: number;
    errorMessage?: string;
  }

  export interface FundQualityIssueParams {
    dataset?: FundDataset | '';
    fundCode?: string;
    pageNum: number;
    pageSize: number;
    qualityStatus?: FundQualityStatus | '';
    runId?: string;
  }

  export interface FundManualSyncPayload {
    dataset: FundDataset;
    fundCode?: string;
    rangeEndDate?: string;
    rangeStartDate?: string;
    syncScope?: string;
    syncType?: FundSyncType;
  }

  export interface FundManualSyncResult {
    accepted: boolean;
    message?: string;
    runId?: string;
    status?: FundSyncStatus;
  }

  export interface RuoYiPage<T> {
    code: number;
    msg: string;
    rows: T[];
    total: number;
  }
}
