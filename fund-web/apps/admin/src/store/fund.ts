import { computed, reactive, ref } from 'vue';

import { defineStore } from 'pinia';

import {
  getFundDetailApi,
  getFundEstimateApi,
  getFundListApi,
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
  const query = reactive<FundApi.FundListParams>({
    fundCode: '',
    fundName: '',
    fundType: '',
    pageNum: 1,
    pageSize: 20,
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
    detailLoading.value = true;
    try {
      detail.value = await getFundDetailApi(code, period);
      return detail.value;
    } finally {
      detailLoading.value = false;
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

  function resetQuery() {
    Object.assign(query, {
      fundCode: '',
      fundName: '',
      fundType: '',
      pageNum: 1,
      pageSize: 20,
    });
  }

  return {
    detail,
    detailLoading,
    fetchDetail,
    fetchList,
    hasEstimate,
    list,
    listLoading,
    query,
    refreshEstimate,
    resetQuery,
    total,
  };
});
