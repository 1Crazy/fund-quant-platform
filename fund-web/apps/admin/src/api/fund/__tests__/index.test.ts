import { beforeEach, describe, expect, it, vi } from 'vitest';

import {
  createQuantConfigDraftApi,
  getQuantConfigDiffApi,
  getFundEstimateApi,
  getFundNavPositionApi,
  getFundListApi,
  getFundSyncRunsApi,
  getFundSyncStatusApi,
  getQuantConfigReleasesApi,
  getQuantConfigVersionsApi,
  publishQuantConfigReleaseApi,
  rollbackQuantConfigReleaseApi,
  retryFundSyncApi,
  triggerFundSyncApi,
  updateQuantConfigDraftApi,
  validateQuantConfigDraftApi,
} from '../index';

const requestMocks = vi.hoisted(() => ({
  get: vi.fn(),
  post: vi.fn(),
  put: vi.fn(),
}));

vi.mock('#/api/request', () => ({
  requestClient: {
    get: requestMocks.get,
    post: requestMocks.post,
    put: requestMocks.put,
  },
}));

describe('fund api data-center extensions', () => {
  beforeEach(() => {
    requestMocks.get.mockReset();
    requestMocks.post.mockReset();
    requestMocks.put.mockReset();
  });

  it('normalizes list rows and forwards data-quality filters', async () => {
    requestMocks.get.mockResolvedValueOnce({
      rows: [
        {
          estimateGrowthRate: '1.23',
          estimateNav: '1.4567',
          fundCode: '000001',
          fundName: '测试基金',
          fundType: '混合型',
          isStale: false,
          latestNav: '1.2345',
          qualityStatus: 'PARTIAL',
          source: 'AKSHARE',
          syncStatus: 'RUNNING',
        },
      ],
      total: 1,
    });

    const result = await getFundListApi({
      fundCode: '000001',
      fundName: '',
      fundType: '',
      pageNum: 1,
      pageSize: 20,
      qualityStatus: 'PARTIAL',
      source: 'AKSHARE',
      syncStatus: 'RUNNING',
    });

    expect(requestMocks.get).toHaveBeenCalledWith(
      '/fund/list',
      expect.objectContaining({
        params: expect.objectContaining({
          qualityStatus: 'PARTIAL',
          source: 'AKSHARE',
          syncStatus: 'RUNNING',
        }),
      }),
    );
    expect(result.items[0]?.latestNav).toBe(1.2345);
    expect(result.items[0]?.estimateGrowthRate).toBe(1.23);
  });

  it('handles synchronization history counters as numbers', async () => {
    requestMocks.get.mockResolvedValueOnce({
      rows: [
        {
          dataset: 'fund_nav',
          failedCount: '1',
          rejectedCount: '2',
          retryCount: '3',
          runId: 'run-1',
          status: 'PARTIAL_SUCCESS',
          successCount: '10',
          syncType: 'NAV_BACKFILL',
          totalCount: '13',
        },
      ],
      total: 1,
    });

    const result = await getFundSyncRunsApi({
      pageNum: 1,
      pageSize: 20,
      status: 'PARTIAL_SUCCESS',
    });

    expect(requestMocks.get).toHaveBeenCalledWith(
      '/fund/sync/runs',
      expect.objectContaining({
        responseReturn: 'body',
      }),
    );
    expect(result.items[0]).toMatchObject({
      failedCount: 1,
      rejectedCount: 2,
      retryCount: 3,
      successCount: 10,
      totalCount: 13,
    });
  });

  it('normalizes current synchronization summary', async () => {
    requestMocks.get.mockResolvedValueOnce({
      activeRuns: [
        {
          dataset: 'fund_info',
          runId: 'run-active',
          status: 'RUNNING',
          successCount: '4',
          syncType: 'FULL_INIT',
          totalCount: '8',
        },
      ],
      failedCount: '1',
      partialCount: '2',
      runningCount: '1',
      staleCount: '3',
    });

    const result = await getFundSyncStatusApi();

    expect(requestMocks.get).toHaveBeenCalledWith('/fund/sync/status');
    expect(result.runningCount).toBe(1);
    expect(result.activeRuns?.[0]?.totalCount).toBe(8);
  });

  it('retains the exact quant-config lineage returned with an estimate', async () => {
    requestMocks.get.mockResolvedValueOnce({
      configReleaseChecksum: 'a'.repeat(64),
      configReleaseVersion: '7',
      contributions: [],
      fundCode: '000001',
      isStale: false,
    });

    const estimate = await getFundEstimateApi('000001');

    expect(requestMocks.get).toHaveBeenCalledWith(
      '/fund/estimate/000001',
      expect.objectContaining({ timeout: 120_000 }),
    );
    expect(estimate.configReleaseVersion).toBe(7);
    expect(estimate.configReleaseChecksum).toHaveLength(64);
  });

  it('normalizes the historical NAV position response without changing its region', async () => {
    requestMocks.get.mockResolvedValueOnce({
      configReleaseChecksum: 'a'.repeat(64),
      configReleaseVersion: '3',
      currentDrawdown: '-12.345678',
      fundCode: '000001',
      navPercentile: '24.867725',
      navPositionConfigVersion: '1',
      navPositionRegion: 'LOW_VALUATION',
      navPositionScore: '24.867725',
      sampleCount: '756',
      status: 'NORMAL',
    });

    const position = await getFundNavPositionApi('000001');

    expect(requestMocks.get).toHaveBeenCalledWith(
      '/fund/valuation/000001',
      expect.objectContaining({ timeout: 30_000 }),
    );
    expect(position).toMatchObject({
      currentDrawdown: -12.345678,
      navPositionRegion: 'LOW_VALUATION',
      navPositionScore: 24.867725,
      sampleCount: 756,
    });
  });

  it('posts manual trigger and retry requests', async () => {
    requestMocks.post.mockResolvedValueOnce({ accepted: true, runId: 'run-2' });
    await triggerFundSyncApi({
      dataset: 'fund_info',
      fundCode: '000001',
      syncScope: 'SINGLE_FUND',
      syncType: 'LAZY_LOAD',
    });

    expect(requestMocks.post).toHaveBeenCalledWith(
      '/fund/sync/trigger',
      expect.objectContaining({
        dataset: 'fund_info',
        fundCode: '000001',
      }),
      expect.objectContaining({ timeout: 120_000 }),
    );

    requestMocks.post.mockResolvedValueOnce({
      dataset: 'FUND_INFO',
      id: 1,
      runId: 'run-1',
      status: 'RUNNING',
      syncType: 'LAZY_LOAD',
    });
    await retryFundSyncApi(1);

    expect(requestMocks.post).toHaveBeenCalledWith(
      '/fund/sync/runs/1/retry',
      undefined,
      expect.objectContaining({ timeout: 120_000 }),
    );
  });

  it('normalizes quant config versions and release items', async () => {
    requestMocks.get.mockResolvedValueOnce({
      rows: [
        {
          configCode: 'TREND',
          configJson: '{"field":"server-owned"}',
          configVersion: '2',
          id: '11',
          schemaVersion: '1',
          status: 'VALIDATED',
          validation: {
            issues: [
              {
                code: 'QUANT_CONFIG_SCHEMA_UNSUPPORTED',
                fieldPath: 'field',
                level: 'ERROR',
                message: '结构版本不支持',
              },
            ],
            passed: false,
          },
        },
      ],
      total: 1,
    });

    const versions = await getQuantConfigVersionsApi({
      pageNum: 1,
      pageSize: 20,
      status: 'VALIDATED',
    });

    expect(requestMocks.get).toHaveBeenCalledWith(
      '/fund/config/versions',
      expect.objectContaining({ responseReturn: 'body' }),
    );
    expect(versions.items[0]).toMatchObject({
      configJson: { field: 'server-owned' },
      configVersion: 2,
      schemaVersion: 1,
    });
    expect(versions.items[0]?.validation?.errors?.[0]?.code).toBe(
      'QUANT_CONFIG_SCHEMA_UNSUPPORTED',
    );

    requestMocks.get.mockResolvedValueOnce([
      {
        id: '5',
        items: [{ configCode: 'TREND', configVersion: '2', id: '11' }],
        releaseVersion: '3',
        status: 'PUBLISHED',
      },
    ]);

    const releases = await getQuantConfigReleasesApi({
      pageNum: 1,
      pageSize: 10,
    });

    expect(releases.items[0]?.releaseVersion).toBe(3);
    expect(releases.items[0]?.items[0]?.configVersion).toBe(2);
    expect(releases.items[0]?.items[0]?.id).toBe('11');
  });

  it('serializes structured drafts and maps validate, diff, publish, and rollback operations', async () => {
    requestMocks.post.mockResolvedValueOnce({
      configCode: 'ESTIMATE',
      configJson: '{"max_quote_age_seconds":90}',
      id: '11',
      revision: '0',
      schemaVersion: '1',
      status: 'DRAFT',
    });
    const created = await createQuantConfigDraftApi({
      configCode: 'ESTIMATE',
      configJson: { max_quote_age_seconds: 90 },
      remark: '初始草稿',
      schemaVersion: 1,
    });
    expect(requestMocks.post).toHaveBeenLastCalledWith(
      '/fund/config/drafts',
      expect.objectContaining({
        configJson: expect.stringContaining('"max_quote_age_seconds": 90'),
        revision: 0,
      }),
    );
    expect(created.configJson).toEqual({ max_quote_age_seconds: 90 });

    requestMocks.put.mockResolvedValueOnce({
      ...created,
      revision: '1',
    });
    await updateQuantConfigDraftApi('11', {
      ...created,
      configJson: { max_quote_age_seconds: 120 },
      id: '11',
      revision: 0,
    });
    expect(requestMocks.put).toHaveBeenCalledWith(
      '/fund/config/drafts/11',
      expect.objectContaining({ revision: 0 }),
    );

    requestMocks.post.mockResolvedValueOnce({
      ...created,
      normalizedJson: '{"max_quote_age_seconds":90}',
      revision: '1',
      status: 'VALIDATED',
    });
    const validated = await validateQuantConfigDraftApi('11', 1);
    expect(requestMocks.post).toHaveBeenLastCalledWith(
      '/fund/config/drafts/11/validate',
      undefined,
      expect.objectContaining({ params: { revision: 1 } }),
    );
    expect(validated.status).toBe('VALIDATED');

    requestMocks.get.mockResolvedValueOnce({
      changes: [{ after: '2', before: '1', fieldPath: 'window', type: 'CHANGED' }],
      targetVersion: '2',
    });
    const diff = await getQuantConfigDiffApi({ baseId: '11', targetId: '12' });
    expect(requestMocks.get).toHaveBeenLastCalledWith(
      '/fund/config/versions/diff',
      expect.objectContaining({ params: { baseId: '11', targetId: '12' } }),
    );
    expect(diff.changes[0]).toMatchObject({ after: 2, before: 1 });

    requestMocks.post.mockResolvedValueOnce({
      id: '21',
      items: [],
      releaseVersion: '3',
      status: 'PUBLISHED',
    });
    const published = await publishQuantConfigReleaseApi({
      changeSummary: '首发',
      configVersionIds: ['11'],
      effectiveFrom: '2026-08-10T09:30:00+08:00',
    });
    expect(requestMocks.post).toHaveBeenLastCalledWith(
      '/fund/config/releases',
      expect.objectContaining({
        configVersionIds: ['11'],
        effectiveFrom: '2026-08-10T09:30:00+08:00',
      }),
    );
    expect(published.releaseVersion).toBe(3);

    requestMocks.post.mockResolvedValueOnce({
      id: '22',
      items: [],
      releaseVersion: '4',
      rollbackOfReleaseVersion: '3',
      status: 'PUBLISHED',
    });
    const rollback = await rollbackQuantConfigReleaseApi({
      configVersionIds: ['11'],
      sourceReleaseVersion: 3,
    });
    expect(requestMocks.post).toHaveBeenLastCalledWith(
      '/fund/config/releases/3/rollback',
      expect.objectContaining({ configVersionIds: ['11'] }),
    );
    expect(rollback.rollbackOfReleaseVersion).toBe(3);
  });

  it('preserves configuration API failures for the drawer state to render', async () => {
    requestMocks.post.mockRejectedValueOnce(new Error('QUANT_CONFIG_SCHEMA_UNSUPPORTED'));

    await expect(
      publishQuantConfigReleaseApi({ configVersionIds: ['11'] }),
    ).rejects.toThrow('QUANT_CONFIG_SCHEMA_UNSUPPORTED');
  });
});
