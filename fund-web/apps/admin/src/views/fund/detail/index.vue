<script lang="ts" setup>
import { computed, ref, watch } from 'vue';

import { useAccess } from '@vben/access';
import { RotateCw } from '@vben/icons';

import {
  ElAlert,
  ElButton,
  ElCard,
  ElDescriptions,
  ElDescriptionsItem,
  ElEmpty,
  ElMessage,
  ElPagination,
  ElSegmented,
  ElSkeleton,
  ElTable,
  ElTableColumn,
  ElTag,
} from 'element-plus';
import { storeToRefs } from 'pinia';

import { getFundHoldingQuotesApi, type FundApi } from '#/api/fund';
import { useFundStore } from '#/store';

import NavChart from '../components/nav-chart.vue';
import {
  datasetLabel,
  estimateRefreshPermissions,
  estimateStatusMeta,
  manualSyncPermissions,
  navPositionRegionMeta,
  qualityStatusMeta,
  syncStatusMeta,
} from '../utils/status';

const props = withDefaults(
  defineProps<{
    active?: boolean;
    code?: string;
  }>(),
  {
    active: false,
    code: '',
  },
);
const { hasAccessByCodes } = useAccess();
const fundStore = useFundStore();
const { detail, detailLoading, navPositionLoading, syncTriggerLoading } =
  storeToRefs(fundStore);
const period = ref<FundApi.NavPeriod>('3m');
const periodOptions: Array<{ label: string; value: FundApi.NavPeriod }> = [
  { label: '近1月', value: '1m' },
  { label: '近3月', value: '3m' },
  { label: '近6月', value: '6m' },
  { label: '近1年', value: '1y' },
  { label: '近3年', value: '3y' },
  { label: '近5年', value: '5y' },
  { label: '成立以来', value: 'all' },
];
const estimateLoading = ref(false);
const holdingQuotes = ref<FundApi.FundHoldingQuote[]>([]);
const historyPage = ref(1);
const historyPageSize = ref(20);

const code = computed(() => props.code.trim());
const isCurrentDetail = computed(() => detail.value?.fundCode === code.value);
const estimate = computed(() => detail.value?.estimate);
const navPosition = computed(() => detail.value?.navPosition);
const navPositionReason = computed(
  () => navPosition.value?.reasons?.[0]?.message,
);
const estimateContributions = computed(
  () => estimate.value?.contributions ?? [],
);
const contributionByStockCode = computed(
  () =>
    new Map(estimateContributions.value.map((item) => [item.stockCode, item])),
);
const holdingQuoteByStockCode = computed(
  () => new Map(holdingQuotes.value.map((item) => [item.stockCode, item])),
);
const latestQuoteTime = computed(
  () =>
    holdingQuotes.value.map((item) => item.quoteTime).find(Boolean) ||
    estimateContributions.value.map((item) => item.quoteTime).find(Boolean),
);
const estimateTime = computed(() => {
  if (estimate.value?.sourceStatus && estimate.value.sourceStatus !== 'NORMAL') {
    return estimate.value.statusReason
      ? `${estimateStatusMeta(estimate.value.sourceStatus).label}：${estimate.value.statusReason}`
      : estimateStatusMeta(estimate.value.sourceStatus).label;
  }
  if (estimate.value?.isStale) {
    return estimate.value.estimateTime
      ? `最新行情获取失败，已隐藏 ${estimate.value.estimateTime} 的过期估值`
      : '最新行情获取失败，暂无可用估值';
  }
  if (estimate.value?.estimateTime) return estimate.value.estimateTime;
  const coverage = detail.value?.holdingCoverageRate;
  return coverage != null
    ? `暂无盘中估值（公开持仓覆盖 ${coverage.toFixed(2)}%）`
    : '暂无盘中估值';
});
const growth = computed(() => estimate.value?.estimateGrowthRate);
const growthClass = computed(() => {
  if (estimate.value?.isStale || growth.value == null || growth.value === 0)
    return 'neutral';
  return growth.value > 0 ? 'up' : 'down';
});
const historyRows = computed(() => {
  const rows = [...(detail.value?.navSeries ?? [])].reverse();
  const start = (historyPage.value - 1) * historyPageSize.value;
  return rows.slice(start, start + historyPageSize.value);
});
const historyTotal = computed(() => detail.value?.navSeries.length ?? 0);
const navRange = computed(() => {
  const rows = detail.value?.navSeries ?? [];
  if (!rows.length) return '--';
  return `${rows[0]?.date} 至 ${rows.at(-1)?.date}`;
});
const latestHoldingReportDate = computed(
  () =>
    detail.value?.latestHoldingReportDate ??
    detail.value?.holdings.find((item) => item.reportDate)?.reportDate ??
    detail.value?.holdings[0]?.reportPeriod,
);
const qualityIssues = computed(() => detail.value?.qualityIssues ?? []);
const canManualSync = computed(() => hasAccessByCodes(manualSyncPermissions));
const canRefreshEstimate = computed(() =>
  hasAccessByCodes(estimateRefreshPermissions),
);

function nav(value?: number) {
  return value == null ? '--' : value.toFixed(4);
}

function formatGrowth(value?: number) {
  if (value == null) return '--';
  return `${value > 0 ? '+' : ''}${value.toFixed(2)}%`;
}

function formatPercent(value?: number) {
  return value == null ? '--' : `${value.toFixed(2)}%`;
}

function holdingChangePercent(stockCode: string) {
  return (
    holdingQuoteByStockCode.value.get(stockCode)?.changePercent ??
    contributionByStockCode.value.get(stockCode)?.changePercent
  );
}

function holdingQuoteTime(stockCode: string) {
  return (
    holdingQuoteByStockCode.value.get(stockCode)?.quoteTime ??
    contributionByStockCode.value.get(stockCode)?.quoteTime
  );
}

async function load() {
  if (!code.value) return;
  await fundStore.fetchDetail(code.value, period.value);
  await fundStore.fetchNavPosition(code.value).catch(() => undefined);
}

async function refreshEstimate() {
  if (!code.value || !canRefreshEstimate.value) return;
  estimateLoading.value = true;
  const quotesPromise = getFundHoldingQuotesApi(code.value);
  try {
    const value = await fundStore.refreshEstimate(code.value);
    if (value.sourceStatus !== 'NORMAL') {
      ElMessage.warning(
        value.statusReason || estimateStatusMeta(value.sourceStatus).label,
      );
    } else if (value.isStale) {
      ElMessage.warning('最新行情未获取成功，页面未使用过期估值');
    } else {
      ElMessage.success('估值和持仓实时行情已刷新');
    }
  } finally {
    // 低覆盖 ETF 联接基金会拒绝生成估值，但已披露股票的实时涨跌仍应保留给用户查看。
    holdingQuotes.value = await quotesPromise.catch(() => holdingQuotes.value);
    estimateLoading.value = false;
  }
}

async function refreshDataCenter() {
  if (!code.value) return;
  const result = await fundStore.triggerSync({
    dataset: 'fund_info',
    fundCode: code.value,
    syncScope: 'SINGLE_FUND',
    syncType: 'LAZY_LOAD',
  });
  await load();
  ElMessage.success(result.message || '数据同步已提交');
}

watch(period, () => {
  historyPage.value = 1;
  if (props.active) {
    void load();
  }
});
watch(
  [() => props.active, code],
  ([active, nextCode]) => {
    if (!active || !nextCode) return;
    historyPage.value = 1;
    holdingQuotes.value = [];
    void load();
  },
  { immediate: true },
);
</script>

<template>
  <div class="fund-detail-panel">
    <div class="detail-toolbar">
      <ElButton
        v-if="canManualSync"
        :loading="syncTriggerLoading"
        type="primary"
        @click="refreshDataCenter"
      >
        <RotateCw class="mr-1 size-4" />刷新数据
      </ElButton>
      <ElButton
        v-if="canRefreshEstimate"
        :loading="estimateLoading"
        @click="refreshEstimate"
      >
        <RotateCw class="mr-1 size-4" />刷新估值
      </ElButton>
    </div>

    <ElSkeleton v-if="detailLoading && !isCurrentDetail" :rows="8" animated />
    <ElEmpty
      v-else-if="!code || !isCurrentDetail"
      description="请选择基金后查看详情"
    />
    <template v-else-if="detail">
        <section class="fund-identity">
          <div class="min-w-0">
            <div class="fund-code">{{ detail.fundCode }}</div>
            <h1>{{ detail.fundName }}</h1>
            <div class="mt-3 flex flex-wrap gap-2">
              <ElTag effect="plain">{{ detail.fundType }}</ElTag>
              <ElTag v-if="detail.riskLevel" effect="plain" type="warning">{{
                detail.riskLevel
              }}</ElTag>
              <ElTag
                :type="qualityStatusMeta(detail.qualityStatus).type"
                effect="plain"
              >
                数据质量：{{ qualityStatusMeta(detail.qualityStatus).label }}
              </ElTag>
              <ElTag
                v-if="detail.syncStatus"
                :type="syncStatusMeta(detail.syncStatus).type"
                effect="plain"
              >
                最近同步：{{ syncStatusMeta(detail.syncStatus).label }}
              </ElTag>
              <ElTag v-if="estimate?.isStale" type="warning">估值已过期</ElTag>
              <ElTag
                v-if="estimate?.sourceStatus"
                :type="estimateStatusMeta(estimate.sourceStatus).type"
                effect="plain"
              >
                估值：{{ estimateStatusMeta(estimate.sourceStatus).label }}
              </ElTag>
              <ElTag
                v-if="navPosition?.navPositionRegion"
                :type="navPositionRegionMeta(navPosition.navPositionRegion).type"
                effect="plain"
              >
                历史位置：{{ navPositionRegionMeta(navPosition.navPositionRegion).label }}
              </ElTag>
            </div>
          </div>
          <div class="valuation-block">
            <div class="valuation-label">盘中估值</div>
            <div class="valuation-number">
              {{ nav(estimate?.isStale || estimate?.sourceStatus !== 'NORMAL' ? undefined : estimate?.estimateNav) }}
            </div>
            <div class="valuation-growth" :class="growthClass">
              {{
                estimate?.isStale || growth == null
                  ? '--'
                  : `${growth > 0 ? '+' : ''}${growth.toFixed(2)}%`
              }}
            </div>
            <div class="valuation-time">
              {{ estimateTime }}
            </div>
            <div v-if="estimate" class="valuation-meta">
              <span>持仓覆盖 {{ estimate.holdingCoverageRate == null ? '--' : `${estimate.holdingCoverageRate.toFixed(2)}%` }}</span>
              <span>行情覆盖 {{ estimate.quoteCoverageRate == null ? '--' : `${estimate.quoteCoverageRate.toFixed(2)}%` }}</span>
            </div>
          </div>
        </section>

        <div class="mt-4 grid gap-4 xl:grid-cols-[minmax(0,1fr)_330px]">
          <ElCard shadow="never">
            <template #header>
              <div class="flex flex-wrap items-center justify-between gap-3">
                <div>
                  <div class="panel-title">净值走势</div>
                  <div class="panel-subtitle">
                    {{ navRange }} · {{ detail.navSeries.length }} 个净值日
                  </div>
                </div>
                <ElSegmented v-model="period" :options="periodOptions" />
              </div>
            </template>
            <NavChart
              v-if="detail.navSeries.length"
              :key="`${detail.fundCode}:${period}`"
              :points="detail.navSeries"
            />
            <ElEmpty v-else description="暂无净值走势数据" />
          </ElCard>

          <div class="flex flex-col gap-4">
            <ElCard shadow="never">
              <template #header
                ><span class="panel-title">最新净值</span></template
              >
              <div class="official-nav">{{ nav(detail.latestNav) }}</div>
              <div class="mt-1 text-sm text-slate-500">
                净值日期 {{ detail.navDate || '--' }}
              </div>
            </ElCard>
            <ElCard shadow="never">
              <template #header
                ><span class="panel-title">历史位置</span></template
              >
              <ElSkeleton v-if="navPositionLoading" :rows="2" animated />
              <template v-else-if="navPosition">
                <div class="flex flex-wrap items-center gap-2">
                  <ElTag
                    v-if="navPosition.navPositionRegion"
                    :type="navPositionRegionMeta(navPosition.navPositionRegion).type"
                    effect="plain"
                  >
                    {{ navPositionRegionMeta(navPosition.navPositionRegion).label }}
                  </ElTag>
                  <span class="position-score">
                    {{ navPosition.navPositionScore == null ? '--' : `${navPosition.navPositionScore.toFixed(2)} 分` }}
                  </span>
                </div>
                <div class="mt-2 text-sm text-slate-500">
                  {{ navPosition.tradeDate || '--' }} · {{ navPosition.sampleCount ?? 0 }} 个确认净值日
                </div>
                <div class="mt-1 text-xs text-slate-500">
                  历史净值分位 {{ formatPercent(navPosition.navPercentile) }} · 当前回撤 {{ formatPercent(navPosition.currentDrawdown) }}
                </div>
                <ElAlert
                  v-if="navPosition.status !== 'NORMAL' && navPositionReason"
                  class="mt-3"
                  :closable="false"
                  :title="navPositionReason"
                  type="info"
                  show-icon
                />
              </template>
              <div v-else class="text-sm text-slate-500">历史位置暂不可用</div>
            </ElCard>
            <ElCard shadow="never">
              <template #header
                ><span class="panel-title">数据版本</span></template
              >
              <ElDescriptions :column="1" border>
                <ElDescriptionsItem label="数据版本">{{
                  detail.dataVersion || '--'
                }}</ElDescriptionsItem>
                <ElDescriptionsItem label="数据日期">{{
                  detail.asOfDate || detail.navDate || '--'
                }}</ElDescriptionsItem>
                <ElDescriptionsItem label="数据来源">{{
                  detail.source || '--'
                }}</ElDescriptionsItem>
                <ElDescriptionsItem label="来源时间">{{
                  detail.sourceUpdatedAt || '--'
                }}</ElDescriptionsItem>
                <ElDescriptionsItem label="持仓报告期">{{
                  estimate?.holdingReportDate || latestHoldingReportDate || '--'
                }}</ElDescriptionsItem>
                <ElDescriptionsItem label="估值输入版本">{{
                  estimate?.inputDataVersion || '--'
                }}</ElDescriptionsItem>
                <ElDescriptionsItem label="估值算法">{{
                  estimate?.algorithmVersion || '--'
                }}</ElDescriptionsItem>
              </ElDescriptions>
            </ElCard>
            <ElCard shadow="never">
              <template #header
                ><span class="panel-title">基金档案</span></template
              >
              <ElDescriptions :column="1" border>
                <ElDescriptionsItem label="基金经理">{{
                  detail.managerName || '--'
                }}</ElDescriptionsItem>
                <ElDescriptionsItem label="基金托管人">{{
                  detail.custodianName || '--'
                }}</ElDescriptionsItem>
                <ElDescriptionsItem label="成立日期">{{
                  detail.establishDate || '--'
                }}</ElDescriptionsItem>
                <ElDescriptionsItem label="基金规模">{{
                  detail.fundScale == null
                    ? '--'
                    : `${detail.fundScale.toFixed(2)} 亿元`
                }}</ElDescriptionsItem>
                <ElDescriptionsItem label="业绩基准">{{
                  detail.benchmark || '--'
                }}</ElDescriptionsItem>
              </ElDescriptions>
            </ElCard>
          </div>
        </div>

        <ElCard class="mt-4" shadow="never">
          <template #header>
            <div class="flex flex-wrap items-center justify-between gap-3">
              <div>
                <div class="panel-title">最新股票持仓</div>
                <div class="panel-subtitle">
                  {{ latestHoldingReportDate || '最近公开报告期' }} ·
                  权重为占基金净值比例
                </div>
              </div>
              <ElTag v-if="detail.holdings.length" effect="plain">
                共 {{ detail.holdings.length }} 只 · 直接股票合计
                {{ (detail.holdingCoverageRate ?? 0).toFixed(2) }}%
              </ElTag>
              <ElTag v-if="latestQuoteTime" effect="plain" type="success">
                实时行情 {{ latestQuoteTime }}
              </ElTag>
            </div>
          </template>
          <ElTable v-if="detail.holdings.length" :data="detail.holdings" stripe>
            <ElTableColumn label="股票代码" min-width="120" prop="stockCode" />
            <ElTableColumn label="股票名称" min-width="150" prop="stockName" />
            <ElTableColumn align="right" label="持仓占比" min-width="130">
              <template #default="{ row }"
                >{{ row.weight.toFixed(2) }}%</template
              >
            </ElTableColumn>
            <ElTableColumn align="right" label="实时涨跌" min-width="130">
              <template #default="{ row }">
                {{ formatGrowth(holdingChangePercent(row.stockCode)) }}
              </template>
            </ElTableColumn>
            <ElTableColumn align="right" label="估值贡献" min-width="130">
              <template #default="{ row }">
                {{
                  formatGrowth(
                    contributionByStockCode.get(row.stockCode)?.contribution,
                  )
                }}
              </template>
            </ElTableColumn>
            <ElTableColumn label="行情获取时间" min-width="190">
              <template #default="{ row }">
                {{ holdingQuoteTime(row.stockCode) || '--' }}
              </template>
            </ElTableColumn>
            <ElTableColumn label="报告期" min-width="150" prop="reportPeriod" />
            <ElTableColumn label="来源" min-width="130" prop="source" />
            <ElTableColumn label="质量" min-width="110">
              <template #default="{ row }">
                <ElTag
                  :type="qualityStatusMeta(row.qualityStatus).type"
                  effect="plain"
                >
                  {{ qualityStatusMeta(row.qualityStatus).label }}
                </ElTag>
              </template>
            </ElTableColumn>
          </ElTable>
          <ElEmpty v-else description="最新报告期暂无直接股票持仓" />
          <ElAlert
            v-if="detail.holdingNote"
            class="mt-3"
            :closable="false"
            :title="detail.holdingNote"
            type="info"
            show-icon
          />
        </ElCard>

        <ElCard class="mt-4" shadow="never">
          <template #header>
            <div>
              <div class="panel-title">数据质量问题</div>
              <div class="panel-subtitle">
                展示最近同步隔离或降级的数据问题，其他有效区块仍可使用
              </div>
            </div>
          </template>
          <ElTable v-if="qualityIssues.length" :data="qualityIssues" stripe>
            <ElTableColumn label="数据集" min-width="120">
              <template #default="{ row }">{{
                datasetLabel(row.dataset)
              }}</template>
            </ElTableColumn>
            <ElTableColumn
              label="业务日期"
              min-width="120"
              prop="businessDate"
            />
            <ElTableColumn label="原因码" min-width="180" prop="reasonCode" />
            <ElTableColumn
              label="说明"
              min-width="260"
              prop="reasonMessage"
              show-overflow-tooltip
            />
            <ElTableColumn label="状态" min-width="110">
              <template #default="{ row }">
                <ElTag
                  :type="qualityStatusMeta(row.qualityStatus).type"
                  effect="plain"
                >
                  {{ qualityStatusMeta(row.qualityStatus).label }}
                </ElTag>
              </template>
            </ElTableColumn>
            <ElTableColumn
              label="发现时间"
              min-width="180"
              prop="discoveredAt"
            />
          </ElTable>
          <ElEmpty v-else description="暂无数据质量问题" />
        </ElCard>

        <ElCard class="mt-4" shadow="never">
          <template #header>
            <div class="flex flex-wrap items-center justify-between gap-3">
              <div>
                <div class="panel-title">历史净值明细</div>
                <div class="panel-subtitle">
                  当前周期共 {{ historyTotal }} 个净值日，按日期倒序展示
                </div>
              </div>
            </div>
          </template>
          <ElTable v-if="historyRows.length" :data="historyRows" stripe>
            <ElTableColumn label="净值日期" min-width="130" prop="date" />
            <ElTableColumn align="right" label="单位净值" min-width="130">
              <template #default="{ row }">{{ nav(row.unitNav) }}</template>
            </ElTableColumn>
            <ElTableColumn align="right" label="累计净值" min-width="130">
              <template #default="{ row }">{{
                nav(row.accumulatedNav)
              }}</template>
            </ElTableColumn>
            <ElTableColumn align="right" label="日增长率" min-width="130">
              <template #default="{ row }">
                <span
                  :class="
                    row.dailyGrowthRate > 0
                      ? 'growth-up'
                      : row.dailyGrowthRate < 0
                        ? 'growth-down'
                        : ''
                  "
                >
                  {{ formatGrowth(row.dailyGrowthRate) }}
                </span>
              </template>
            </ElTableColumn>
          </ElTable>
          <ElEmpty v-else description="暂无历史净值数据" />
          <div v-if="historyTotal" class="mt-4 flex justify-end">
            <ElPagination
              v-model:current-page="historyPage"
              v-model:page-size="historyPageSize"
              :page-sizes="[10, 20, 50, 100]"
              :total="historyTotal"
              background
              layout="total, sizes, prev, pager, next, jumper"
            />
          </div>
        </ElCard>

        <div class="risk-note">
          历史净值与披露持仓来自公开数据源，仅用于数据展示；披露持仓不代表实时仓位，也不构成
          AI 建议或投资建议。
        </div>
    </template>
  </div>
</template>

<style scoped>
.fund-detail-panel {
  color: #14213d;
}

.detail-toolbar {
  display: flex;
  gap: 8px;
  justify-content: flex-end;
  margin-bottom: 16px;
}

.fund-identity {
  align-items: center;
  background: #fff;
  border: 1px solid rgb(148 163 184 / 24%);
  border-radius: 8px;
  display: flex;
  justify-content: space-between;
  min-height: 156px;
  padding: 24px;
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
  font-size: 26px;
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

.valuation-meta {
  color: #64748b;
  display: flex;
  flex-wrap: wrap;
  font-size: 12px;
  gap: 4px 12px;
  margin-top: 8px;
}

.valuation-number,
.official-nav {
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-size: 38px;
  font-weight: 700;
  letter-spacing: 0;
  line-height: 1.2;
}

.position-score {
  color: #14213d;
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-size: 18px;
  font-weight: 700;
}

.official-nav {
  font-size: 32px;
}

.valuation-growth {
  font-size: 18px;
  font-weight: 700;
  margin: 5px 0;
}

.valuation-growth.up {
  color: #e11d48;
}
.valuation-growth.down {
  color: #059669;
}
.valuation-growth.neutral {
  color: #64748b;
}
.growth-up {
  color: #e11d48;
  font-weight: 600;
}
.growth-down {
  color: #059669;
  font-weight: 600;
}

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

  .fund-identity h1 {
    font-size: 26px;
  }
  .valuation-block {
    border-left: 0;
    border-top: 1px solid rgb(15 118 110 / 24%);
    padding-left: 0;
    padding-top: 20px;
    width: 100%;
  }
}
</style>
