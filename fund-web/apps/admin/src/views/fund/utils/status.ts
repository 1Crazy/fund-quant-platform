import type { FundApi } from '#/api/fund';

type TagType = 'danger' | 'info' | 'primary' | 'success' | 'warning';

interface StatusMeta {
  description?: string;
  label: string;
  type: TagType;
}

const qualityStatusMap: Record<string, StatusMeta> = {
  EMPTY: {
    description: '上游成功响应但当前数据集没有可用记录',
    label: '空数据',
    type: 'info',
  },
  FAILED: {
    description: '最近一次数据发布失败，页面保留最后有效版本',
    label: '失败',
    type: 'danger',
  },
  NORMAL: {
    description: '数据通过质量校验并处于当前可用版本',
    label: '正常',
    type: 'success',
  },
  PARTIAL: {
    description: '有效记录已发布，但存在被拒绝或隔离的数据',
    label: '部分可用',
    type: 'warning',
  },
  REJECTED: {
    description: '记录未通过校验并已被隔离',
    label: '已拒绝',
    type: 'danger',
  },
  STALE: {
    description: '数据超过新鲜度阈值，请关注同步状态',
    label: '过期',
    type: 'warning',
  },
};

const syncStatusMap: Record<string, StatusMeta> = {
  CANCELLED: { label: '已取消', type: 'info' },
  FAILED: { label: '失败', type: 'danger' },
  INTERRUPTED: { label: '已中断', type: 'warning' },
  PARTIAL_SUCCESS: { label: '部分成功', type: 'warning' },
  PAUSED: { label: '已暂停', type: 'warning' },
  PENDING: { label: '等待中', type: 'info' },
  RUNNING: { label: '运行中', type: 'primary' },
  SUCCESS: { label: '成功', type: 'success' },
};

const estimateStatusMap: Record<string, StatusMeta> = {
  FAILED: { label: '计算失败', type: 'danger' },
  NORMAL: { label: '可用', type: 'success' },
  PARTIAL: { label: '部分覆盖', type: 'warning' },
  STALE: { label: '已过期', type: 'warning' },
  UNSUPPORTED: { label: '不可估值', type: 'info' },
  UPSTREAM_FAILED: { label: '上游失败', type: 'danger' },
};

const navPositionRegionMap: Record<string, StatusMeta> = {
  HIGH_VALUATION: { label: '高位区域', type: 'warning' },
  LOW_VALUATION: { label: '低位区域', type: 'success' },
  NORMAL: { label: '正常区域', type: 'info' },
  RISK: { label: '风险区域', type: 'danger' },
};

const datasetMap: Record<string, string> = {
  FUND_CATALOG: '基金目录',
  FUND_HOLDING: '披露持仓',
  FUND_INFO: '基金档案',
  FUND_NAV: '确认净值',
  fund_holding: '披露持仓',
  fund_info: '基金档案',
  fund_nav: '确认净值',
};

const syncTypeMap: Record<string, string> = {
  CONTINUE_FROM_LATEST_NAV: '按最新净值续拉',
  FULL_HISTORY: '全量历史同步',
  FULL_INIT: '全量初始化',
  HOLDING_BACKFILL: '持仓回填',
  INCREMENTAL: '增量同步',
  LAZY_LOAD: '按需懒加载',
  NAV_BACKFILL: '历史 NAV 回填',
};

export const manualSyncPermissions = [
  '*:*:*',
  'fund:sync:trigger',
  'fund:sync:manual',
];
export const estimateRefreshPermissions = ['*:*:*', 'fund:estimate:refresh'];

const quantConfigStatusMap: Record<string, StatusMeta> = {
  DRAFT: { label: '草稿', type: 'info' },
  PUBLISHED: { label: '已发布', type: 'success' },
  RETIRED: { label: '已停用', type: 'info' },
  ROLLED_BACK: { label: '已回滚', type: 'warning' },
  VALIDATED: { label: '已校验', type: 'primary' },
};

const quantConfigGroupMap: Record<string, string> = {
  BACKTEST: '回测参数',
  ESTIMATE: '盘中估值',
  FACTOR: '多因子权重',
  FUND_RISK: '基金风险',
  GLOBAL_CONVENTIONS: '全局口径',
  MOVING_AVERAGE: '均线参数',
  NAV_POSITION: '历史位置',
  PORTFOLIO_RISK: '组合风险',
  RSI_MACD: 'RSI / MACD',
  TREND: '趋势参数',
};

export const quantConfigReadPermissions = ['*:*:*', 'fund:config:list'];
export const quantConfigEditPermissions = ['*:*:*', 'fund:config:edit'];
export const quantConfigValidatePermissions = [
  '*:*:*',
  'fund:config:validate',
];
export const quantConfigPublishPermissions = [
  '*:*:*',
  'fund:config:publish',
];
export const quantConfigRollbackPermissions = [
  '*:*:*',
  'fund:config:rollback',
];

export function qualityStatusMeta(status?: FundApi.FundQualityStatus) {
  if (!status) return { label: '--', type: 'info' } satisfies StatusMeta;
  return (
    qualityStatusMap[status] ?? {
      label: status,
      type: 'info',
    }
  );
}

export function syncStatusMeta(status?: FundApi.FundSyncStatus) {
  if (!status) return { label: '--', type: 'info' } satisfies StatusMeta;
  return (
    syncStatusMap[status] ?? {
      label: status,
      type: 'info',
    }
  );
}

export function estimateStatusMeta(status?: FundApi.FundEstimateSourceStatus) {
  if (!status) return { label: '--', type: 'info' } satisfies StatusMeta;
  return (
    estimateStatusMap[status] ?? {
      label: status,
      type: 'info',
    }
  );
}

export function navPositionRegionMeta(
  region?: FundApi.FundNavPositionRegion,
) {
  if (!region) return { label: '--', type: 'info' } satisfies StatusMeta;
  return (
    navPositionRegionMap[region] ?? {
      label: region,
      type: 'info',
    }
  );
}

export function datasetLabel(dataset?: FundApi.FundDataset) {
  if (!dataset) return '--';
  return datasetMap[dataset] ?? dataset;
}

export function syncTypeLabel(syncType?: FundApi.FundSyncType) {
  if (!syncType) return '--';
  return syncTypeMap[syncType] ?? syncType;
}

export function quantConfigGroupLabel(code?: FundApi.QuantConfigCode) {
  if (!code) return '--';
  return quantConfigGroupMap[code] ?? code;
}

export function quantConfigStatusMeta(
  status?: FundApi.QuantConfigReleaseStatus | FundApi.QuantConfigStatus,
) {
  if (!status) return { label: '--', type: 'info' } satisfies StatusMeta;
  return (
    quantConfigStatusMap[status] ?? {
      label: status,
      type: 'info',
    }
  );
}

export function formatCount(value?: number) {
  return value == null ? 0 : value;
}

export function formatDuration(value?: number) {
  if (value == null) return '--';
  if (value < 1000) return `${value}ms`;
  if (value < 60_000) return `${(value / 1000).toFixed(1)}s`;
  return `${(value / 60_000).toFixed(1)}min`;
}

export function hasAnyFundPermission(
  userPermissions: string[],
  requiredPermissions: string[],
) {
  const userPermissionSet = new Set(userPermissions);
  return requiredPermissions.some((permission) =>
    userPermissionSet.has(permission),
  );
}
