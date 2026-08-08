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
    | 'PAUSED'
    | 'PENDING'
    | 'RUNNING'
    | 'SUCCESS'
    | string;

  export type FundSyncType =
    | 'CONTINUE_FROM_LATEST_NAV'
    | 'FULL_HISTORY'
    | 'FULL_INIT'
    | 'HOLDING_BACKFILL'
    | 'INCREMENTAL'
    | 'LAZY_LOAD'
    | 'NAV_BACKFILL'
    | string;

  export type FundEstimateSourceStatus =
    | 'FAILED'
    | 'NORMAL'
    | 'PARTIAL'
    | 'STALE'
    | 'UNSUPPORTED'
    | 'UPSTREAM_FAILED'
    | string;

  export type FundNavPositionStatus = 'NORMAL' | 'UNAVAILABLE' | string;

  export type FundNavPositionRegion =
    | 'HIGH_VALUATION'
    | 'LOW_VALUATION'
    | 'NORMAL'
    | 'RISK'
    | string;

  export interface FundListParams {
    fundCode?: string;
    fundName?: string;
    fundType?: string;
    qualityStatus?: FundQualityStatus | '';
    navPositionRegion?: FundNavPositionRegion | '';
    pageNum: number;
    pageSize: number;
    source?: string;
    syncStatus?: FundSyncStatus | '';
  }

  export interface FundEstimate {
    configReleaseChecksum?: string;
    configReleaseVersion?: number;
    contributions?: FundEstimateContribution[];
    estimateGrowthRate?: number;
    estimateNav?: number;
    estimateTime?: string;
    estimateConfigChecksum?: string;
    estimateConfigVersion?: number;
    fundCode: string;
    holdingCoverageRate?: number;
    inputDataVersion?: string;
    isStale: boolean;
    missingQuoteCount?: number;
    previousNav?: number;
    previousNavDate?: string;
    quoteCoverageRate?: number;
    quoteTime?: string;
    reportPeriod?: string;
    source?: string;
    sourceStatus?: FundEstimateSourceStatus;
    statusReason?: string;
    tradeDate?: string;
    algorithmVersion?: string;
  }

  export interface FundEstimateContribution {
    changePercent: number;
    contribution: number;
    quoteTime?: string;
    stockCode: string;
    stockName: string;
    weight: number;
  }

  /** 历史净值位置，不表达内在价值或交易建议。 */
  export interface FundNavPosition {
    algorithmVersion?: string;
    calculatedAt?: string;
    configReleaseChecksum?: string;
    configReleaseVersion?: number;
    currentDrawdown?: number;
    effectiveEndDate?: string;
    effectiveStartDate?: string;
    fundCode: string;
    indicators?: FundNavPositionIndicator[];
    inputDataVersion?: string;
    ma60Deviation?: number;
    ma120Deviation?: number;
    ma250Deviation?: number;
    navPercentile?: number;
    navPositionConfigChecksum?: string;
    navPositionConfigVersion?: number;
    navPositionRegion?: FundNavPositionRegion;
    navPositionScore?: number;
    reasons?: FundNavPositionReason[];
    sampleCount?: number;
    status: FundNavPositionStatus;
    tradeDate?: string;
  }

  /** 全量历史位置计算的后台执行摘要。 */
  export interface FundNavPositionBatchStatus {
    configReleaseVersion?: number;
    cursorValue?: string;
    errorMessage?: string;
    failedCount: number;
    finishedAt?: string;
    normalCount: number;
    processedCount: number;
    requestedCount: number;
    startedAt?: string;
    state: 'FAILED' | 'IDLE' | 'PARTIAL_SUCCESS' | 'RUNNING' | 'SUCCESS' | string;
    unavailableCount: number;
  }

  export interface FundNavPositionReason {
    actual?: number;
    code: string;
    message: string;
    required?: number;
  }

  export interface FundNavPositionIndicator {
    available: boolean;
    code: string;
    reasonCode?: string;
    value?: number;
  }

  export interface FundEstimateScheduleStatus {
    activeTradingSession: boolean;
    configReleaseChecksum?: string;
    configReleaseVersion?: number;
    failedCount?: number;
    lastCompletedAt?: string;
    lastError?: string;
    lastStartedAt?: string;
    normalCount?: number;
    partialCount?: number;
    requestedCount?: number;
    scheduleCron?: string;
    scheduleEnabled: boolean;
    scheduleLockHeld: boolean;
    scheduleZoneId?: string;
    unsupportedCount?: number;
  }

  export interface FundListItem {
    asOfDate?: string;
    dataVersion?: string;
    estimateGrowthRate?: number;
    estimateNav?: number;
    estimateHoldingCoverageRate?: number;
    estimateMissingQuoteCount?: number;
    estimateQuoteCoverageRate?: number;
    estimateSourceStatus?: FundEstimateSourceStatus;
    estimateStatusReason?: string;
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
    navPositionCalculatedAt?: string;
    navPositionRegion?: FundNavPositionRegion;
    navPositionScore?: number;
    navPositionStatus?: FundNavPositionStatus;
    navPositionTradeDate?: string;
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
    navPosition?: FundNavPosition;
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
    startedAtEnd?: string;
    startedAtStart?: string;
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

  /** 全量历史净值同步的真实执行状态，进度由基金代码游标计算。 */
  export interface FundGlobalNavSyncStatus {
    cursorValue?: string;
    errorMessage?: string;
    failedCount?: number;
    finishedAt?: string;
    id?: number;
    processedFundCount: number;
    rejectedCount?: number;
    resumable: boolean;
    runId?: string;
    startedAt?: string;
    state:
      | 'FAILED'
      | 'IDLE'
      | 'INTERRUPTED'
      | 'PARTIAL_SUCCESS'
      | 'PAUSED'
      | 'RUNNING'
      | 'SUCCESS'
      | string;
    successCount?: number;
    syncType?: FundSyncType;
    totalFundCount: number;
  }

  export interface FundQualityIssueParams {
    dataset?: FundDataset | '';
    fetchBatchId?: string;
    fundCode?: string;
    issueStatus?: string;
    pageNum: number;
    pageSize: number;
    qualityStatus?: FundQualityStatus | '';
    reasonCode?: string;
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

  export type QuantConfigCode =
    | 'BACKTEST'
    | 'ESTIMATE'
    | 'FACTOR'
    | 'FUND_RISK'
    | 'GLOBAL_CONVENTIONS'
    | 'MOVING_AVERAGE'
    | 'NAV_POSITION'
    | 'PORTFOLIO_RISK'
    | 'RSI_MACD'
    | 'TREND'
    | string;

  export type QuantConfigStatus =
    | 'DRAFT'
    | 'PUBLISHED'
    | 'RETIRED'
    | 'VALIDATED'
    | string;

  export type QuantConfigReleaseStatus =
    | 'PUBLISHED'
    | 'RETIRED'
    | 'ROLLED_BACK'
    | string;

  export type JsonValue =
    | boolean
    | null
    | number
    | string
    | JsonValue[]
    | { [key: string]: JsonValue };

  export interface QuantConfigGroup {
    activeConfigVersion?: number;
    activeReleaseVersion?: number;
    configCode: QuantConfigCode;
    description?: string;
    displayName: string;
    latestConfigVersion?: number;
    schemaVersion?: number;
    status?: QuantConfigStatus;
    updatedAt?: string;
  }

  export interface QuantConfigVersionParams {
    configCode?: QuantConfigCode | '';
    pageNum: number;
    pageSize: number;
    status?: QuantConfigStatus | '';
  }

  export interface QuantConfigVersion {
    checksum?: string;
    configCode: QuantConfigCode;
    configJson: JsonValue;
    configVersion?: number;
    createdAt?: string;
    createdBy?: string;
    effectiveFrom?: string;
    id: number | string;
    normalizedJson?: string;
    remark?: string;
    revision?: number;
    schemaVersion: number;
    status: QuantConfigStatus;
    updatedAt?: string;
    updatedBy?: string;
    validation?: QuantConfigValidationResult;
  }

  export interface QuantConfigDraftPayload {
    configCode: QuantConfigCode;
    configJson: JsonValue;
    effectiveFrom?: string;
    id?: number | string;
    remark?: string;
    revision?: number;
    schemaVersion: number;
  }

  export type QuantConfigValidationLevel = 'ERROR' | 'INFO' | 'WARN' | string;

  export interface QuantConfigValidationIssue {
    code: string;
    fieldPath?: string;
    level: QuantConfigValidationLevel;
    message: string;
  }

  export interface QuantConfigValidationResult {
    canonicalJson?: string;
    checksum?: string;
    errors?: QuantConfigValidationIssue[];
    issues?: QuantConfigValidationIssue[];
    passed: boolean;
    warnings?: QuantConfigValidationIssue[];
  }

  export interface QuantConfigDiffParams {
    baseId?: number | string;
    targetId: number | string;
  }

  export type QuantConfigDiffType =
    | 'ADDED'
    | 'CHANGED'
    | 'REMOVED'
    | 'UNCHANGED'
    | string;

  export interface QuantConfigDiffEntry {
    after?: JsonValue;
    before?: JsonValue;
    configCode?: QuantConfigCode;
    fieldPath: string;
    type: QuantConfigDiffType;
  }

  export interface QuantConfigDiff {
    baseChecksum?: string;
    baseVersion?: number;
    changes: QuantConfigDiffEntry[];
    targetChecksum?: string;
    targetVersion?: number;
  }

  export interface QuantConfigReleaseParams {
    pageNum: number;
    pageSize: number;
    status?: QuantConfigReleaseStatus | '';
  }

  export interface QuantConfigReleaseItem {
    configChecksum?: string;
    configCode: QuantConfigCode;
    configVersion: number;
    displayName?: string;
    id?: number | string;
    schemaVersion?: number;
  }

  export interface QuantConfigRelease {
    checksum?: string;
    createdAt?: string;
    createdBy?: string;
    effectiveFrom?: string;
    id: number | string;
    items: QuantConfigReleaseItem[];
    publishedAt?: string;
    releaseVersion: number;
    remark?: string;
    rollbackOfReleaseVersion?: number;
    status: QuantConfigReleaseStatus;
  }

  export interface QuantConfigPublishPayload {
    changeSummary?: string;
    configVersionIds: Array<number | string>;
    effectiveFrom?: string;
  }

  export interface QuantConfigRollbackPayload {
    changeSummary?: string;
    configVersionIds: Array<number | string>;
    effectiveFrom?: string;
    sourceReleaseVersion: number | string;
  }

  export interface RuoYiPage<T> {
    code: number;
    msg: string;
    rows: T[];
    total: number;
  }
}
