import { computed, reactive, ref } from 'vue';

import { defineStore } from 'pinia';

import {
  getFundDetailApi,
  getFundEstimateApi,
  getFundListApi,
  getFundSyncRunsApi,
  getFundSyncStatusApi,
  retryFundSyncApi,
  triggerFundSyncApi,
  type FundApi,
} from '#/api/fund';

/**
 * 基金实时估值状态。
 * 服务端数据只在当前会话缓存，避免把易过期的估值持久化到浏览器本地存储。
 */
export const useFundStore = defineStore('fund-realtime', () => {
  const list = ref<FundApi.FundListItem[]>([]);
  const total = ref(0);
  const listLoading = ref(false);
  const detailLoading = ref(false);
  const detail = ref<FundApi.FundDetail>();
  let detailRequestSequence = 0;
  const syncRuns = ref<FundApi.FundSyncRun[]>([]);
  const syncRunsTotal = ref(0);
  const syncRunsLoading = ref(false);
  const syncStatus = ref<FundApi.FundSyncStatusSummary>();
  const syncTriggerLoading = ref(false);
  const query = reactive<FundApi.FundListParams>({
    fundCode: '',
    fundName: '',
    fundType: '',
    pageNum: 1,
    pageSize: 20,
    qualityStatus: '',
    source: '',
    syncStatus: '',
  });
  const syncQuery = reactive<FundApi.FundSyncRunParams>({
    dataset: '',
    fundCode: '',
    pageNum: 1,
    pageSize: 20,
    status: '',
    syncType: '',
  });

  const hasEstimate = computed(() => Boolean(detail.value?.estimate?.estimateTime));

  async function fetchList(resetPage = false) {
    if (resetPage) query.pageNum = 1;
    listLoading.value = true;
    try {
      const result = await getFundListApi({ ...query });
      list.value = result.items;
      total.value = result.total;
    } finally {
      listLoading.value = false;
    }
  }

  async function fetchDetail(code: string, period: FundApi.NavPeriod = '3m') {
    const requestSequence = ++detailRequestSequence;
    detailLoading.value = true;
    try {
      const nextDetail = await getFundDetailApi(code, period);
      // 周期切换可能产生并发请求，只接收最后一次请求，避免旧周期响应覆盖当前图表数据。
      if (requestSequence === detailRequestSequence) {
        detail.value = nextDetail;
      }
      return nextDetail;
    } finally {
      if (requestSequence === detailRequestSequence) {
        detailLoading.value = false;
      }
    }
  }

  async function refreshEstimate(code: string) {
    const estimate = await getFundEstimateApi(code);
    if (detail.value?.fundCode === code) {
      detail.value = { ...detail.value, estimate };
    }
    const row = list.value.find((item) => item.fundCode === code);
    if (row) {
      Object.assign(row, estimate);
    }
    return estimate;
  }

  async function fetchSyncRuns(resetPage = false) {
    if (resetPage) syncQuery.pageNum = 1;
    syncRunsLoading.value = true;
    try {
      const result = await getFundSyncRunsApi({ ...syncQuery });
      syncRuns.value = result.items;
      syncRunsTotal.value = result.total;
    } finally {
      syncRunsLoading.value = false;
    }
  }

  async function fetchSyncStatus() {
    syncStatus.value = await getFundSyncStatusApi();
    return syncStatus.value;
  }

  async function triggerSync(payload: FundApi.FundManualSyncPayload) {
    syncTriggerLoading.value = true;
    try {
      const result = await triggerFundSyncApi(payload);
      await Promise.all([fetchSyncRuns(true), fetchSyncStatus()]);
      return result;
    } finally {
      syncTriggerLoading.value = false;
    }
  }

  async function retrySync(runId: number | string) {
    syncTriggerLoading.value = true;
    try {
      const result = await retryFundSyncApi(runId);
      await Promise.all([fetchSyncRuns(), fetchSyncStatus()]);
      return result;
    } finally {
      syncTriggerLoading.value = false;
    }
  }

  function resetQuery() {
    Object.assign(query, {
      fundCode: '',
      fundName: '',
      fundType: '',
      pageNum: 1,
      pageSize: 20,
      qualityStatus: '',
      source: '',
      syncStatus: '',
    });
  }

  function resetSyncQuery() {
    Object.assign(syncQuery, {
      dataset: '',
      fundCode: '',
      pageNum: 1,
      pageSize: 20,
      status: '',
      syncType: '',
    });
  }

  return {
    detail,
    detailLoading,
    fetchDetail,
    fetchList,
    fetchSyncRuns,
    fetchSyncStatus,
    hasEstimate,
    list,
    listLoading,
    query,
    refreshEstimate,
    resetSyncQuery,
    retrySync,
    resetQuery,
    syncQuery,
    syncRuns,
    syncRunsLoading,
    syncRunsTotal,
    syncStatus,
    syncTriggerLoading,
    triggerSync,
    total,
  };
});
