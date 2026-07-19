import { describe, expect, it } from 'vitest';

import {
  datasetLabel,
  formatDuration,
  hasAnyFundPermission,
  manualSyncPermissions,
  qualityStatusMeta,
  syncStatusMeta,
  syncTypeLabel,
} from '../status';

describe('fund data center status helpers', () => {
  it('renders quality states including empty, partial, failed and stale', () => {
    expect(qualityStatusMeta('NORMAL')).toMatchObject({
      label: '正常',
      type: 'success',
    });
    expect(qualityStatusMeta('PARTIAL')).toMatchObject({
      label: '部分可用',
      type: 'warning',
    });
    expect(qualityStatusMeta('EMPTY')).toMatchObject({
      label: '空数据',
      type: 'info',
    });
    expect(qualityStatusMeta('FAILED')).toMatchObject({
      label: '失败',
      type: 'danger',
    });
    expect(qualityStatusMeta('STALE')).toMatchObject({
      label: '过期',
      type: 'warning',
    });
  });

  it('renders synchronization state and fallback labels', () => {
    expect(syncStatusMeta('RUNNING')).toMatchObject({
      label: '运行中',
      type: 'primary',
    });
    expect(syncStatusMeta('PARTIAL_SUCCESS')).toMatchObject({
      label: '部分成功',
      type: 'warning',
    });
    expect(syncStatusMeta('UNKNOWN_STATUS')).toMatchObject({
      label: 'UNKNOWN_STATUS',
      type: 'info',
    });
  });

  it('formats dataset, sync type and duration display values', () => {
    expect(datasetLabel('fund_nav')).toBe('确认净值');
    expect(syncTypeLabel('LAZY_LOAD')).toBe('按需懒加载');
    expect(syncTypeLabel('NAV_BACKFILL')).toBe('历史 NAV 回填');
    expect(formatDuration(930)).toBe('930ms');
    expect(formatDuration(2500)).toBe('2.5s');
    expect(formatDuration(120_000)).toBe('2.0min');
  });

  it('supports hiding manual trigger actions without permission', () => {
    expect(
      hasAnyFundPermission(['fund:info:list'], manualSyncPermissions),
    ).toBe(false);
    expect(
      hasAnyFundPermission(['fund:sync:trigger'], manualSyncPermissions),
    ).toBe(true);
  });
});
