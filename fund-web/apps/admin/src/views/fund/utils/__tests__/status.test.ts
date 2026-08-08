import { describe, expect, it } from 'vitest';

import {
  datasetLabel,
  estimateRefreshPermissions,
  estimateStatusMeta,
  formatDuration,
  hasAnyFundPermission,
  manualSyncPermissions,
  quantConfigGroupLabel,
  quantConfigPublishPermissions,
  quantConfigStatusMeta,
  quantConfigValidatePermissions,
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

  it('renders estimate availability separately from data quality states', () => {
    expect(estimateStatusMeta('NORMAL')).toMatchObject({
      label: '可用',
      type: 'success',
    });
    expect(estimateStatusMeta('UNSUPPORTED')).toMatchObject({
      label: '不可估值',
      type: 'info',
    });
    expect(estimateStatusMeta('PARTIAL')).toMatchObject({
      label: '部分覆盖',
      type: 'warning',
    });
    expect(estimateStatusMeta('STALE')).toMatchObject({
      label: '已过期',
      type: 'warning',
    });
    expect(estimateStatusMeta('UPSTREAM_FAILED')).toMatchObject({
      label: '上游失败',
      type: 'danger',
    });
    expect(
      hasAnyFundPermission(['fund:estimate:refresh'], estimateRefreshPermissions),
    ).toBe(true);
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

  it('keeps configuration permissions and status labels distinct by action', () => {
    expect(quantConfigGroupLabel('RSI_MACD')).toBe('RSI / MACD');
    expect(quantConfigStatusMeta('VALIDATED')).toMatchObject({
      label: '已校验',
      type: 'primary',
    });
    expect(
      hasAnyFundPermission(['fund:config:validate'], quantConfigValidatePermissions),
    ).toBe(true);
    expect(
      hasAnyFundPermission(['fund:config:validate'], quantConfigPublishPermissions),
    ).toBe(false);
  });
});
