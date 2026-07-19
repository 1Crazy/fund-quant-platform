/**
 * 基金实时估值模块的前端数据契约。
 * 百分比字段均使用百分数口径，例如 1.23 表示 1.23%。
 */
export namespace FundApi {
  export interface FundListParams {
    fundCode?: string;
    fundName?: string;
    fundType?: string;
    pageNum: number;
    pageSize: number;
  }

  export interface FundEstimate {
    estimateGrowthRate?: number;
    estimateNav?: number;
    estimateTime?: string;
    fundCode: string;
    isStale: boolean;
    previousNav?: number;
    previousNavDate?: string;
    source?: string;
  }

  export interface FundListItem {
    estimateGrowthRate?: number;
    estimateNav?: number;
    estimateTime?: string;
    fundCode: string;
    fundName: string;
    fundType: string;
    isStale: boolean;
    latestNav?: number;
    navDate?: string;
  }

  export interface FundNavPoint {
    accumulatedNav?: number;
    dailyGrowthRate?: number;
    date: string;
    unitNav: number;
  }

  export interface FundHolding {
    reportPeriod: string;
    stockCode: string;
    stockName: string;
    weight: number;
  }

  export type NavPeriod = '1m' | '1y' | '3m' | '3y' | '5y' | '6m' | 'all';

  export interface FundDetail {
    benchmark?: string;
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
    managerName?: string;
    navDate?: string;
    navSeries: FundNavPoint[];
    riskLevel?: string;
    custodianName?: string;
  }

  export interface PageResult<T> {
    items: T[];
    total: number;
  }

  export interface RuoYiPage<T> {
    code: number;
    msg: string;
    rows: T[];
    total: number;
  }
}
