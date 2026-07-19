import { beforeEach, describe, expect, it, vi } from 'vitest';

import {
  getFundListApi,
  getFundSyncRunsApi,
  getFundSyncStatusApi,
  retryFundSyncApi,
  triggerFundSyncApi,
} from '../index';

const requestMocks = vi.hoisted(() => ({
  get: vi.fn(),
  post: vi.fn(),
}));

vi.mock('#/api/request', () => ({
  requestClient: {
    get: requestMocks.get,
    post: requestMocks.post,
  },
}));

describe('fund api data-center extensions', () => {
  beforeEach(() => {
    requestMocks.get.mockReset();
    requestMocks.post.mockReset();
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
});
