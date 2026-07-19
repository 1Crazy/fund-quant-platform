<script lang="ts" setup>
import { computed, onMounted, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import { Page } from '@vben/common-ui';
import { ArrowLeft, RotateCw } from '@vben/icons';

import {
  ElButton,
  ElCard,
  ElDescriptions,
  ElDescriptionsItem,
  ElEmpty,
  ElMessage,
  ElSegmented,
  ElSkeleton,
  ElTag,
} from 'element-plus';
import { storeToRefs } from 'pinia';

import { useFundStore } from '#/store';

import NavChart from '../components/nav-chart.vue';

const route = useRoute();
const router = useRouter();
const fundStore = useFundStore();
const { detail, detailLoading } = storeToRefs(fundStore);
const days = ref(120);
const periodOptions = [
  { label: '120日', value: 120 },
  { label: '250日', value: 250 },
  { label: '全部', value: 0 },
];
const estimateLoading = ref(false);

const code = computed(() => String(route.query.code ?? '').trim());
const estimate = computed(() => detail.value?.estimate);
const growth = computed(() => estimate.value?.estimateGrowthRate);
const growthClass = computed(() => {
  if (growth.value == null || growth.value === 0) return 'neutral';
  return growth.value > 0 ? 'up' : 'down';
});

function nav(value?: number) {
  return value == null ? '--' : value.toFixed(4);
}

async function load() {
  if (!code.value) return;
  await fundStore.fetchDetail(code.value, days.value);
}

async function refreshEstimate() {
  if (!code.value) return;
  estimateLoading.value = true;
  try {
    await fundStore.refreshEstimate(code.value);
    ElMessage.success('估值已刷新');
  } finally {
    estimateLoading.value = false;
  }
}

onMounted(load);
watch(days, load);
watch(code, load);
</script>

<template>
  <Page>
    <div class="fund-detail-page">
      <div class="mb-4 flex items-center justify-between gap-3">
        <ElButton text @click="router.push('/fund/list')">
          <ArrowLeft class="mr-1 size-4" />返回基金列表
        </ElButton>
        <ElButton :loading="estimateLoading" type="primary" @click="refreshEstimate">
          <RotateCw class="mr-1 size-4" />刷新估值
        </ElButton>
      </div>

      <ElSkeleton v-if="detailLoading && !detail" :rows="8" animated />
      <ElEmpty v-else-if="!code || !detail" description="请选择基金后查看详情" />
      <template v-else>
        <section class="fund-identity">
          <div class="min-w-0">
            <div class="fund-code">{{ detail.fundCode }}</div>
            <h1>{{ detail.fundName }}</h1>
            <div class="mt-3 flex flex-wrap gap-2">
              <ElTag effect="plain">{{ detail.fundType }}</ElTag>
              <ElTag v-if="detail.riskLevel" effect="plain" type="warning">{{ detail.riskLevel }}</ElTag>
              <ElTag v-if="estimate?.isStale" type="warning">估值已过期</ElTag>
            </div>
          </div>
          <div class="valuation-block">
            <div class="valuation-label">盘中估值</div>
            <div class="valuation-number">{{ nav(estimate?.estimateNav) }}</div>
            <div class="valuation-growth" :class="growthClass">
              {{ growth == null ? '--' : `${growth > 0 ? '+' : ''}${growth.toFixed(2)}%` }}
            </div>
            <div class="valuation-time">{{ estimate?.estimateTime || '暂无盘中估值' }}</div>
          </div>
        </section>

        <div class="mt-4 grid gap-4 xl:grid-cols-[minmax(0,1fr)_330px]">
          <ElCard shadow="never">
            <template #header>
              <div class="flex flex-wrap items-center justify-between gap-3">
                <div>
                  <div class="panel-title">净值走势</div>
                  <div class="panel-subtitle">单位净值与累计净值</div>
                </div>
                <ElSegmented
                  v-model="days"
                  :options="periodOptions"
                />
              </div>
            </template>
            <NavChart v-if="detail.navSeries.length" :points="detail.navSeries" />
            <ElEmpty v-else description="暂无净值走势数据" />
          </ElCard>

          <div class="flex flex-col gap-4">
            <ElCard shadow="never">
              <template #header><span class="panel-title">最新净值</span></template>
              <div class="official-nav">{{ nav(detail.latestNav) }}</div>
              <div class="mt-1 text-sm text-slate-500">净值日期 {{ detail.navDate || '--' }}</div>
            </ElCard>
            <ElCard shadow="never">
              <template #header><span class="panel-title">基金档案</span></template>
              <ElDescriptions :column="1" border>
                <ElDescriptionsItem label="基金经理">{{ detail.managerName || '--' }}</ElDescriptionsItem>
                <ElDescriptionsItem label="成立日期">{{ detail.establishDate || '--' }}</ElDescriptionsItem>
                <ElDescriptionsItem label="基金规模">{{ detail.fundScale == null ? '--' : `${detail.fundScale.toFixed(2)} 亿元` }}</ElDescriptionsItem>
                <ElDescriptionsItem label="业绩基准">{{ detail.benchmark || '--' }}</ElDescriptionsItem>
              </ElDescriptions>
            </ElCard>
          </div>
        </div>

        <div class="risk-note">量化估值仅供辅助决策，不构成投资建议。盘中估值可能与基金公司最终公布净值存在差异。</div>
      </template>
    </div>
  </Page>
</template>

<style scoped>
.fund-detail-page {
  color: #14213d;
}

.fund-identity {
  align-items: center;
  background: linear-gradient(112deg, #f8fafc 0%, #eaf7f4 68%, #fff7ed 100%);
  border: 1px solid rgb(148 163 184 / 24%);
  border-radius: 8px;
  display: flex;
  justify-content: space-between;
  min-height: 190px;
  padding: 28px 32px;
}

.fund-code {
  color: #0f766e;
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-size: 14px;
  font-weight: 700;
  letter-spacing: 1px;
}

.fund-identity h1 {
  font-family: 'Songti SC', 'Noto Serif CJK SC', serif;
  font-size: 31px;
  font-weight: 700;
  letter-spacing: 0;
  margin: 6px 0 0;
}

.valuation-block {
  border-left: 1px solid rgb(15 118 110 / 24%);
  min-width: 230px;
  padding-left: 32px;
}

.valuation-label,
.valuation-time,
.panel-subtitle {
  color: #64748b;
  font-size: 12px;
}

.valuation-number,
.official-nav {
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-size: 38px;
  font-weight: 700;
  letter-spacing: 0;
  line-height: 1.2;
}

.official-nav {
  font-size: 32px;
}

.valuation-growth {
  font-size: 18px;
  font-weight: 700;
  margin: 5px 0;
}

.valuation-growth.up { color: #e11d48; }
.valuation-growth.down { color: #059669; }
.valuation-growth.neutral { color: #64748b; }

.panel-title {
  font-size: 15px;
  font-weight: 700;
}

.risk-note {
  border-top: 1px solid rgb(148 163 184 / 24%);
  color: #64748b;
  font-size: 12px;
  margin-top: 18px;
  padding: 14px 2px 4px;
}

@media (max-width: 768px) {
  .fund-identity {
    align-items: start;
    flex-direction: column;
    gap: 24px;
    padding: 22px;
  }

  .fund-identity h1 { font-size: 26px; }
  .valuation-block {
    border-left: 0;
    border-top: 1px solid rgb(15 118 110 / 24%);
    padding-left: 0;
    padding-top: 20px;
    width: 100%;
  }
}
</style>
