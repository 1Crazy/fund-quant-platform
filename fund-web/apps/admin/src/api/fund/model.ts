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

  export interface FundDetail {
    benchmark?: string;
    establishDate?: string;
    estimate?: FundEstimate;
    fundCode: string;
    fundName: string;
    fundScale?: number;
    fundType: string;
    latestNav?: number;
    managerName?: string;
    navDate?: string;
    navSeries: FundNavPoint[];
    riskLevel?: string;
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
