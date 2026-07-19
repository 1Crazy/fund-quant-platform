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
  PARTIAL_SUCCESS: { label: '部分成功', type: 'warning' },
  PENDING: { label: '等待中', type: 'info' },
  RUNNING: { label: '运行中', type: 'primary' },
  SUCCESS: { label: '成功', type: 'success' },
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

export function datasetLabel(dataset?: FundApi.FundDataset) {
  if (!dataset) return '--';
  return datasetMap[dataset] ?? dataset;
}

export function syncTypeLabel(syncType?: FundApi.FundSyncType) {
  if (!syncType) return '--';
  return syncTypeMap[syncType] ?? syncType;
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
