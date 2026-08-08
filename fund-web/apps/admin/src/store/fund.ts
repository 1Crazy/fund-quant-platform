import { computed, reactive, ref } from 'vue';

import { defineStore } from 'pinia';

import {
  createQuantConfigDraftApi,
  getFundDetailApi,
  getGlobalNavSyncStatusApi,
  getFundEstimateScheduleStatusApi,
  getFundNavPositionBatchStatusApi,
  getFundNavPositionApi,
  refreshFundEstimateApi,
  refreshAllFundNavPositionsApi,
  getFundListApi,
  getFundSyncRunsApi,
  getFundSyncStatusApi,
  getQuantConfigDiffApi,
  getQuantConfigGroupsApi,
  getQuantConfigReleasesApi,
  getQuantConfigVersionApi,
  getQuantConfigVersionsApi,
  pauseGlobalNavSyncApi,
  publishQuantConfigReleaseApi,
  resumeGlobalNavSyncApi,
  retryFundSyncApi,
  rollbackQuantConfigReleaseApi,
  triggerFundSyncApi,
  updateQuantConfigDraftApi,
  validateQuantConfigDraftApi,
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
  const navPositionLoading = ref(false);
  const navPositionBatchLoading = ref(false);
  const navPositionBatchStatus = ref<FundApi.FundNavPositionBatchStatus>();
  const detail = ref<FundApi.FundDetail>();
  let detailRequestSequence = 0;
  let navPositionRequestSequence = 0;
  const syncRuns = ref<FundApi.FundSyncRun[]>([]);
  const syncRunsTotal = ref(0);
  const syncRunsLoading = ref(false);
  const syncStatus = ref<FundApi.FundSyncStatusSummary>();
  const globalNavSyncStatus = ref<FundApi.FundGlobalNavSyncStatus>();
  const syncTriggerLoading = ref(false);
  const estimateScheduleStatus = ref<FundApi.FundEstimateScheduleStatus>();
  const configGroups = ref<FundApi.QuantConfigGroup[]>([]);
  const configGroupsLoading = ref(false);
  const configVersions = ref<FundApi.QuantConfigVersion[]>([]);
  const configVersionsTotal = ref(0);
  const configVersionsLoading = ref(false);
  const selectedConfigVersion = ref<FundApi.QuantConfigVersion>();
  const configValidation = ref<FundApi.QuantConfigValidationResult>();
  const configDiff = ref<FundApi.QuantConfigDiff>();
  const configMutationLoading = ref(false);
  const configReleases = ref<FundApi.QuantConfigRelease[]>([]);
  const configReleasesTotal = ref(0);
  const configReleasesLoading = ref(false);
  const query = reactive<FundApi.FundListParams>({
    fundCode: '',
    fundName: '',
    fundType: '',
    navPositionRegion: '',
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
    startedAtEnd: '',
    startedAtStart: '',
    status: '',
    syncType: '',
  });
  const configVersionQuery = reactive<FundApi.QuantConfigVersionParams>({
    configCode: '',
    pageNum: 1,
    pageSize: 20,
    status: '',
  });
  const configReleaseQuery = reactive<FundApi.QuantConfigReleaseParams>({
    pageNum: 1,
    pageSize: 10,
    status: '',
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
    const estimate = await refreshFundEstimateApi(code);
    if (detail.value?.fundCode === code) {
      detail.value = { ...detail.value, estimate };
    }
    const row = list.value.find((item) => item.fundCode === code);
    if (row) {
      const normal = estimate.sourceStatus === 'NORMAL' && !estimate.isStale;
      Object.assign(row, {
        estimateGrowthRate: normal ? estimate.estimateGrowthRate : undefined,
        estimateHoldingCoverageRate: estimate.holdingCoverageRate,
        estimateMissingQuoteCount: estimate.missingQuoteCount,
        estimateNav: normal ? estimate.estimateNav : undefined,
        estimateQuoteCoverageRate: estimate.quoteCoverageRate,
        estimateSourceStatus: estimate.sourceStatus,
        estimateStatusReason: estimate.statusReason,
        estimateTime: estimate.estimateTime,
        isStale: estimate.isStale,
      });
    }
    return estimate;
  }

  async function fetchNavPosition(code: string) {
    const requestSequence = ++navPositionRequestSequence;
    navPositionLoading.value = true;
    try {
      const navPosition = await getFundNavPositionApi(code);
      if (
        requestSequence === navPositionRequestSequence &&
        detail.value?.fundCode === code
      ) {
        detail.value = { ...detail.value, navPosition };
      }
      const row = list.value.find((item) => item.fundCode === code);
      if (row) {
        Object.assign(row, {
          navPositionCalculatedAt: navPosition.calculatedAt,
          navPositionRegion: navPosition.navPositionRegion,
          navPositionScore: navPosition.navPositionScore,
          navPositionStatus: navPosition.status,
          navPositionTradeDate: navPosition.tradeDate,
        });
      }
      return navPosition;
    } finally {
      if (requestSequence === navPositionRequestSequence) {
        navPositionLoading.value = false;
      }
    }
  }

  async function fetchNavPositionBatchStatus() {
    navPositionBatchStatus.value = await getFundNavPositionBatchStatusApi();
    return navPositionBatchStatus.value;
  }

  async function refreshAllNavPositions() {
    navPositionBatchLoading.value = true;
    try {
      navPositionBatchStatus.value = await refreshAllFundNavPositionsApi();
      return navPositionBatchStatus.value;
    } finally {
      navPositionBatchLoading.value = false;
    }
  }

  async function fetchEstimateScheduleStatus() {
    estimateScheduleStatus.value = await getFundEstimateScheduleStatusApi();
    return estimateScheduleStatus.value;
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

  async function fetchGlobalNavSyncStatus() {
    globalNavSyncStatus.value = await getGlobalNavSyncStatusApi();
    return globalNavSyncStatus.value;
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

  async function pauseGlobalNavSync() {
    syncTriggerLoading.value = true;
    try {
      const result = await pauseGlobalNavSyncApi();
      await Promise.all([
        fetchGlobalNavSyncStatus(),
        fetchSyncRuns(true),
        fetchSyncStatus(),
      ]);
      return result;
    } finally {
      syncTriggerLoading.value = false;
    }
  }

  async function resumeGlobalNavSync() {
    syncTriggerLoading.value = true;
    try {
      const result = await resumeGlobalNavSyncApi();
      await Promise.all([
        fetchGlobalNavSyncStatus(),
        fetchSyncRuns(true),
        fetchSyncStatus(),
      ]);
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

  async function fetchConfigGroups() {
    configGroupsLoading.value = true;
    try {
      configGroups.value = await getQuantConfigGroupsApi();
      return configGroups.value;
    } finally {
      configGroupsLoading.value = false;
    }
  }

  async function fetchConfigVersions(resetPage = false) {
    if (resetPage) configVersionQuery.pageNum = 1;
    configVersionsLoading.value = true;
    try {
      const result = await getQuantConfigVersionsApi({
        ...configVersionQuery,
      });
      configVersions.value = result.items;
      configVersionsTotal.value = result.total;
      return result;
    } finally {
      configVersionsLoading.value = false;
    }
  }

  async function fetchConfigVersion(id: number | string) {
    selectedConfigVersion.value = await getQuantConfigVersionApi(id);
    configValidation.value = selectedConfigVersion.value.validation;
    return selectedConfigVersion.value;
  }

  async function saveConfigDraft(payload: FundApi.QuantConfigDraftPayload) {
    configMutationLoading.value = true;
    try {
      const result = payload.id
        ? await updateQuantConfigDraftApi(payload.id, payload)
        : await createQuantConfigDraftApi(payload);
      selectedConfigVersion.value = result;
      await Promise.all([fetchConfigGroups(), fetchConfigVersions()]);
      return result;
    } finally {
      configMutationLoading.value = false;
    }
  }

  async function validateConfigDraft(id: number | string) {
    configMutationLoading.value = true;
    try {
      selectedConfigVersion.value = await validateQuantConfigDraftApi(
        id,
        selectedConfigVersion.value?.revision ?? 0,
      );
      configValidation.value = {
        canonicalJson: selectedConfigVersion.value.normalizedJson,
        checksum: selectedConfigVersion.value.checksum,
        issues: [],
        passed: selectedConfigVersion.value.status === 'VALIDATED',
      };
      await Promise.all([fetchConfigGroups(), fetchConfigVersions()]);
      return configValidation.value;
    } finally {
      configMutationLoading.value = false;
    }
  }

  async function fetchConfigDiff(params: FundApi.QuantConfigDiffParams) {
    configDiff.value = await getQuantConfigDiffApi(params);
    return configDiff.value;
  }

  async function fetchConfigReleases(resetPage = false) {
    if (resetPage) configReleaseQuery.pageNum = 1;
    configReleasesLoading.value = true;
    try {
      const result = await getQuantConfigReleasesApi({
        ...configReleaseQuery,
      });
      configReleases.value = result.items;
      configReleasesTotal.value = result.total;
      return result;
    } finally {
      configReleasesLoading.value = false;
    }
  }

  async function publishConfigRelease(
    payload: FundApi.QuantConfigPublishPayload,
  ) {
    configMutationLoading.value = true;
    try {
      const result = await publishQuantConfigReleaseApi(payload);
      await Promise.all([
        fetchConfigGroups(),
        fetchConfigVersions(),
        fetchConfigReleases(true),
      ]);
      return result;
    } finally {
      configMutationLoading.value = false;
    }
  }

  async function rollbackConfigRelease(
    payload: FundApi.QuantConfigRollbackPayload,
  ) {
    configMutationLoading.value = true;
    try {
      const result = await rollbackQuantConfigReleaseApi(payload);
      await Promise.all([
        fetchConfigGroups(),
        fetchConfigVersions(),
        fetchConfigReleases(true),
      ]);
      return result;
    } finally {
      configMutationLoading.value = false;
    }
  }

  function resetQuery() {
    Object.assign(query, {
      fundCode: '',
      fundName: '',
      fundType: '',
      navPositionRegion: '',
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
      startedAtEnd: '',
      startedAtStart: '',
      status: '',
      syncType: '',
    });
  }

  function resetConfigVersionQuery() {
    Object.assign(configVersionQuery, {
      configCode: '',
      pageNum: 1,
      pageSize: 20,
      status: '',
    });
  }

  function resetConfigReleaseQuery() {
    Object.assign(configReleaseQuery, {
      pageNum: 1,
      pageSize: 10,
      status: '',
    });
  }

  return {
    configDiff,
    configGroups,
    configGroupsLoading,
    configMutationLoading,
    configReleaseQuery,
    configReleases,
    configReleasesLoading,
    configReleasesTotal,
    configValidation,
    configVersionQuery,
    configVersions,
    configVersionsLoading,
    configVersionsTotal,
    detail,
    detailLoading,
    fetchConfigDiff,
    fetchConfigGroups,
    fetchConfigReleases,
    fetchConfigVersion,
    fetchConfigVersions,
    fetchDetail,
    fetchEstimateScheduleStatus,
    fetchGlobalNavSyncStatus,
    fetchList,
    fetchNavPosition,
    fetchNavPositionBatchStatus,
    fetchSyncRuns,
    fetchSyncStatus,
    hasEstimate,
    list,
    listLoading,
    navPositionLoading,
    navPositionBatchLoading,
    navPositionBatchStatus,
    pauseGlobalNavSync,
    publishConfigRelease,
    query,
    refreshAllNavPositions,
    refreshEstimate,
    resumeGlobalNavSync,
    resetConfigReleaseQuery,
    resetConfigVersionQuery,
    resetSyncQuery,
    retrySync,
    rollbackConfigRelease,
    resetQuery,
    saveConfigDraft,
    selectedConfigVersion,
    syncQuery,
    estimateScheduleStatus,
    syncRuns,
    syncRunsLoading,
    syncRunsTotal,
    globalNavSyncStatus,
    syncStatus,
    syncTriggerLoading,
    triggerSync,
    validateConfigDraft,
    total,
  };
});
