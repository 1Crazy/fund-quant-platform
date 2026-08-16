<script lang="ts" setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import { useAccess } from '@vben/access';
import { Page } from '@vben/common-ui';
import { RotateCw, Search } from '@vben/icons';

import {
  ElButton,
  ElCard,
  ElDrawer,
  ElEmpty,
  ElInput,
  ElMessage,
  ElMessageBox,
  ElOption,
  ElPagination,
  ElSelect,
  ElTable,
  ElTableColumn,
  ElTag,
} from 'element-plus';
import { storeToRefs } from 'pinia';

import type { FundApi } from '#/api/fund';
import { useFundStore } from '#/store';

import {
  estimateRefreshPermissions,
  estimateStatusMeta,
  manualSyncPermissions,
  navPositionRegionMeta,
  qualityStatusMeta,
  syncStatusMeta,
} from '../utils/status';
import FundDetailDrawer from '../detail/index.vue';
import GlobalNavSyncDrawer from '../sync/GlobalNavSyncDrawer.vue';
import NavPositionCalculationDrawer from './NavPositionCalculationDrawer.vue';

const route = useRoute();
const router = useRouter();
const fundStore = useFundStore();
const { hasAccessByCodes } = useAccess();
const {
  estimateScheduleStatus,
  list,
  listLoading,
  navPositionBatchLoading,
  navPositionBatchStatus,
  query,
  syncTriggerLoading,
  total,
} = storeToRefs(fundStore);
const drawerVisible = ref(false);
const navPositionDrawerVisible = ref(false);
const syncDrawerVisible = ref(false);
const selectedFundCode = ref('');
const canMonitorEstimate = computed(() =>
  hasAccessByCodes(['*:*:*', 'fund:estimate:monitor']),
);
const canManualSync = computed(() => hasAccessByCodes(manualSyncPermissions));
const canCalculateNavPosition = computed(() =>
  hasAccessByCodes(estimateRefreshPermissions),
);
let navPositionBatchPollingTimer: ReturnType<typeof setInterval> | undefined;

const rangeSummary = computed(() => {
  if (!total.value) return '暂无基金数据';
  const start = (query.value.pageNum - 1) * query.value.pageSize + 1;
  const end = Math.min(query.value.pageNum * query.value.pageSize, total.value);
  return `显示 ${start}-${end}，共 ${total.value} 只基金`;
});

function formatNav(value?: number) {
  return value == null ? '--' : value.toFixed(4);
}

function formatCoverage(value?: number) {
  return value == null ? '--' : `${value.toFixed(2)}%`;
}

function formatSource(row: FundApi.FundListItem) {
  if (!row.source) return '--';
  return row.sourceUpdatedAt ? `${row.source} · ${row.sourceUpdatedAt}` : row.source;
}

function growthClass(value?: number) {
  if (value == null || value === 0) return 'text-slate-500';
  return value > 0 ? 'text-rose-600' : 'text-emerald-600';
}

function formatPositionScore(value?: number) {
  return value == null ? '--' : `${value.toFixed(2)} 分`;
}

const scheduleSummary = computed(() => {
  const status = estimateScheduleStatus.value;
  if (!status) return '';
  if (!status.scheduleEnabled) return '调度已关闭';
  if (!status.activeTradingSession) return '当前非交易时段';
  return `本批 ${status.normalCount ?? 0}/${status.requestedCount ?? 0} 可用`;
});

const scheduleTagType = computed(() => {
  const status = estimateScheduleStatus.value;
  if (!status || !status.scheduleEnabled) return 'info';
  if (status.scheduleLockHeld || (status.failedCount ?? 0) > 0) return 'warning';
  return status.activeTradingSession ? 'success' : 'info';
});

const navPositionBatchSummary = computed(() => {
  const status = navPositionBatchStatus.value;
  if (!status || status.state === 'IDLE') return '';
  if (status.state === 'RUNNING') {
    return `历史位置计算 ${status.processedCount}/${status.requestedCount}`;
  }
  if (status.state === 'SUCCESS' || status.state === 'PARTIAL_SUCCESS') {
    return `历史位置已完成 ${status.normalCount}/${status.requestedCount}`;
  }
  return '历史位置计算失败';
});

const navPositionBatchTagType = computed(() => {
  switch (navPositionBatchStatus.value?.state) {
    case 'SUCCESS':
      return 'success';
    case 'PARTIAL_SUCCESS':
      return 'warning';
    case 'FAILED':
      return 'danger';
    default:
      return 'primary';
  }
});

function openDetail(row: FundApi.FundListItem) {
  selectedFundCode.value = row.fundCode;
  drawerVisible.value = true;
  void router.replace({
    path: '/fund/list',
    query: { ...route.query, code: row.fundCode },
  });
}

function closeDetail() {
  drawerVisible.value = false;
  const nextQuery = { ...route.query };
  delete nextQuery.code;
  void router.replace({ path: '/fund/list', query: nextQuery });
}

function openSyncStatus() {
  syncDrawerVisible.value = true;
}

async function openNavPositionProgress() {
  navPositionDrawerVisible.value = true;
  try {
    await fundStore.fetchNavPositionBatchStatus();
  } catch {
    ElMessage.error('读取历史位置计算进度失败');
  }
}

async function search() {
  fundStore.query.fundCode = fundStore.query.fundCode?.trim() ?? '';
  await fundStore.fetchList(true);
}

async function reset() {
  fundStore.resetQuery();
  await fundStore.fetchList();
}

async function submitGlobalNavSync(
  syncType: 'CONTINUE_FROM_LATEST_NAV' | 'FULL_HISTORY',
) {
  const isFullHistory = syncType === 'FULL_HISTORY';
  try {
    await ElMessageBox.confirm(
      isFullHistory
        ? '将先同步上游公开基金目录，再逐只拉取目录内全部基金的历史确认净值至今天。该任务耗时较长，提交后可在同步状态抽屉查看进度。'
        : '将逐只读取当前最大确认净值日期，并从下一日期续拉至今天；没有新净值的基金会被跳过。该任务会在后台持续执行，可在同步管理查看进度。',
      isFullHistory ? '确认全量历史同步' : '确认按最新净值续拉',
      {
        cancelButtonText: '取消',
        confirmButtonText: '确认开始',
        type: 'warning',
      },
    );
  } catch {
    return;
  }

  const result = await fundStore.triggerSync({
    dataset: 'fund_nav',
    syncScope: 'ALL',
    syncType,
  });
  await fundStore.fetchList();
  ElMessage.success(result.message || '同步任务已提交，可在同步管理查看进度');
}

function stopNavPositionBatchPolling() {
  if (navPositionBatchPollingTimer) {
    clearInterval(navPositionBatchPollingTimer);
    navPositionBatchPollingTimer = undefined;
  }
}

async function refreshNavPositionBatchProgress() {
  try {
    const status = await fundStore.fetchNavPositionBatchStatus();
    if (status.state === 'RUNNING') {
      return;
    }
    stopNavPositionBatchPolling();
    await fundStore.fetchList();
  } catch {
    stopNavPositionBatchPolling();
  }
}

function startNavPositionBatchPolling() {
  stopNavPositionBatchPolling();
  navPositionBatchPollingTimer = setInterval(() => {
    void refreshNavPositionBatchProgress();
  }, 4000);
}

async function submitNavPositionBatchCalculation() {
  try {
    await ElMessageBox.confirm(
      '将使用当前已发布的量化配置，为所有已有确认净值的基金计算历史位置，并更新列表中的低位、正常、高位或风险区域。净值样本不足的基金会保留为不可用。任务在后台执行，可继续浏览页面。',
      '确认全量计算历史位置',
      {
        cancelButtonText: '取消',
        confirmButtonText: '确认计算',
        type: 'warning',
      },
    );
  } catch {
    return;
  }

  const result = await fundStore.refreshAllNavPositions();
  navPositionDrawerVisible.value = true;
  if (result.state === 'RUNNING') {
    startNavPositionBatchPolling();
    ElMessage.success('历史位置计算已提交，完成后列表会自动刷新');
    return;
  }
  ElMessage.info('历史位置计算任务已在执行，已显示当前进度');
}

function changePage() {
  void fundStore.fetchList();
}

function changePageSize() {
  void fundStore.fetchList(true);
}

watch(
  () => route.query.code,
  (value) => {
    const code = String(value ?? '').trim();
    selectedFundCode.value = code;
    drawerVisible.value = Boolean(code);
  },
  { immediate: true },
);

onMounted(async () => {
  await Promise.all([fundStore.fetchList(), fundStore.fetchNavPositionBatchStatus()]);
  if (navPositionBatchStatus.value?.state === 'RUNNING') {
    startNavPositionBatchPolling();
  }
  if (canMonitorEstimate.value) {
    await fundStore.fetchEstimateScheduleStatus();
  }
});

onBeforeUnmount(stopNavPositionBatchPolling);
</script>

<template>
  <Page auto-content-height>
    <div class="fund-list-page flex h-full min-h-0 flex-col gap-4">
      <section class="market-header">
        <div>
          <div class="market-kicker">REAL-TIME FUND DESK</div>
          <h1>基金实时估值</h1>
          <p>净值、盘中估值与更新时间集中呈现，快速定位当天异动基金。</p>
        </div>
        <div class="market-actions">
          <div class="market-stat">
            <span class="live-dot" aria-hidden="true"></span>
            <span>{{ rangeSummary }}</span>
            <ElTag v-if="scheduleSummary" :type="scheduleTagType" effect="plain" size="small">
              {{ scheduleSummary }}
            </ElTag>
            <ElTag
              v-if="navPositionBatchSummary"
              :type="navPositionBatchTagType"
              effect="plain"
              size="small"
            >
              {{ navPositionBatchSummary }}
            </ElTag>
          </div>
          <div class="market-sync-actions">
            <ElButton @click="openSyncStatus">
              <RotateCw class="mr-1 size-4" />同步状态
            </ElButton>
            <ElButton
              v-if="canCalculateNavPosition"
              :disabled="navPositionBatchStatus?.state === 'RUNNING'"
              :loading="navPositionBatchLoading"
              type="success"
              @click="submitNavPositionBatchCalculation"
            >
              <RotateCw class="mr-1 size-4" />全量计算历史位置
            </ElButton>
            <ElButton
              v-if="canCalculateNavPosition && navPositionBatchStatus?.state && navPositionBatchStatus.state !== 'IDLE'"
              @click="openNavPositionProgress"
            >
              <RotateCw class="mr-1 size-4" />计算进度
            </ElButton>
            <ElButton
              v-if="canManualSync"
              :loading="syncTriggerLoading"
              type="warning"
              @click="submitGlobalNavSync('FULL_HISTORY')"
            >
              <RotateCw class="mr-1 size-4" />全量历史同步
            </ElButton>
            <ElButton
              v-if="canManualSync"
              :loading="syncTriggerLoading"
              type="primary"
              @click="submitGlobalNavSync('CONTINUE_FROM_LATEST_NAV')"
            >
              <RotateCw class="mr-1 size-4" />按最新净值续拉
            </ElButton>
          </div>
        </div>
      </section>

      <ElCard class="filter-panel" shadow="never">
        <div class="fund-filter-grid">
          <ElInput
            v-model="query.fundCode"
            clearable
            maxlength="12"
            placeholder="基金代码"
            @keyup.enter="search"
          />
          <ElInput
            v-model="query.fundName"
            class="fund-name-filter"
            clearable
            placeholder="基金名称"
            @keyup.enter="search"
          />
          <ElSelect v-model="query.fundType" clearable placeholder="基金类型">
            <ElOption label="股票型" value="股票型" />
            <ElOption label="混合型" value="混合型" />
            <ElOption label="债券型" value="债券型" />
            <ElOption label="指数型" value="指数型" />
            <ElOption label="QDII" value="QDII" />
          </ElSelect>
          <ElSelect v-model="query.source" clearable placeholder="数据来源">
            <ElOption label="AkShare 目录" value="AKSHARE_CATALOG" />
            <ElOption label="AkShare 基金档案" value="AKSHARE_XQ" />
            <ElOption label="AkShare" value="AKSHARE" />
          </ElSelect>
          <ElSelect v-model="query.qualityStatus" clearable placeholder="质量状态">
            <ElOption label="正常" value="NORMAL" />
            <ElOption label="部分可用" value="PARTIAL" />
            <ElOption label="空数据" value="EMPTY" />
            <ElOption label="过期" value="STALE" />
            <ElOption label="失败" value="FAILED" />
          </ElSelect>
          <ElSelect v-model="query.syncStatus" clearable placeholder="同步状态">
            <ElOption label="等待中" value="PENDING" />
            <ElOption label="运行中" value="RUNNING" />
            <ElOption label="成功" value="SUCCESS" />
            <ElOption label="部分成功" value="PARTIAL_SUCCESS" />
            <ElOption label="失败" value="FAILED" />
            <ElOption label="已取消" value="CANCELLED" />
          </ElSelect>
          <ElSelect v-model="query.navPositionRegion" clearable placeholder="历史位置">
            <ElOption label="低估值" value="LOW_VALUATION" />
            <ElOption label="正常估值" value="NORMAL" />
            <ElOption label="高估值" value="HIGH_VALUATION" />
            <ElOption label="风险区域" value="RISK" />
          </ElSelect>
          <div class="flex gap-2">
            <ElButton :loading="listLoading" type="primary" @click="search">
              <Search class="mr-1 size-4" />查询
            </ElButton>
            <ElButton @click="reset">
              <RotateCw class="mr-1 size-4" />重置
            </ElButton>
          </div>
        </div>
      </ElCard>

      <ElCard class="min-h-0 flex-1" shadow="never">
        <ElTable
          v-loading="listLoading"
          :data="list"
          height="100%"
          row-key="fundCode"
          stripe
          @row-click="openDetail"
        >
          <ElTableColumn label="基金代码" min-width="112" prop="fundCode" fixed>
            <template #default="{ row }">
              <button class="fund-code" type="button" @click.stop="openDetail(row as FundApi.FundListItem)">
                {{ row.fundCode }}
              </button>
            </template>
          </ElTableColumn>
          <ElTableColumn label="基金名称" min-width="230" prop="fundName" show-overflow-tooltip />
          <ElTableColumn label="类型" min-width="110" prop="fundType">
            <template #default="{ row }">
              <ElTag effect="plain" type="info">{{ row.fundType }}</ElTag>
            </template>
          </ElTableColumn>
          <ElTableColumn label="来源" min-width="170" show-overflow-tooltip>
            <template #default="{ row }">{{ formatSource(row as FundApi.FundListItem) }}</template>
          </ElTableColumn>
          <ElTableColumn label="质量状态" min-width="120">
            <template #default="{ row }">
              <ElTag :type="qualityStatusMeta(row.qualityStatus).type" effect="plain">
                {{ qualityStatusMeta(row.qualityStatus).label }}
              </ElTag>
            </template>
          </ElTableColumn>
          <ElTableColumn align="right" label="最新净值" min-width="120">
            <template #default="{ row }">{{ formatNav(row.latestNav) }}</template>
          </ElTableColumn>
          <ElTableColumn align="center" label="最新 NAV 日期" min-width="130" prop="navDate">
            <template #default="{ row }">
              <div class="leading-5">
                <div>{{ row.navDate || '--' }}</div>
                <div v-if="row.latestNavQualityStatus" class="text-xs text-slate-500">
                  {{ qualityStatusMeta(row.latestNavQualityStatus).label }}
                </div>
              </div>
            </template>
          </ElTableColumn>
          <ElTableColumn label="同步状态" min-width="120">
            <template #default="{ row }">
              <ElTag :type="syncStatusMeta(row.syncStatus).type" effect="plain">
                {{ syncStatusMeta(row.syncStatus).label }}
              </ElTag>
            </template>
          </ElTableColumn>
          <ElTableColumn align="right" label="盘中估值" min-width="120">
            <template #default="{ row }">
              {{ row.estimateSourceStatus === 'NORMAL' && !row.isStale ? formatNav(row.estimateNav) : '--' }}
            </template>
          </ElTableColumn>
          <ElTableColumn align="right" label="估算涨跌" min-width="120">
            <template #default="{ row }">
              <span class="font-semibold tabular-nums" :class="growthClass(row.estimateGrowthRate)">
                {{ row.estimateGrowthRate == null ? '--' : `${row.estimateGrowthRate > 0 ? '+' : ''}${row.estimateGrowthRate.toFixed(2)}%` }}
              </span>
            </template>
          </ElTableColumn>
          <ElTableColumn label="估值状态" min-width="120">
            <template #default="{ row }">
              <ElTag :type="estimateStatusMeta(row.estimateSourceStatus).type" effect="plain">
                {{ estimateStatusMeta(row.estimateSourceStatus).label }}
              </ElTag>
            </template>
          </ElTableColumn>
          <ElTableColumn label="历史位置" min-width="150">
            <template #default="{ row }">
              <div v-if="row.navPositionRegion" class="leading-5">
                <ElTag
                  :type="navPositionRegionMeta(row.navPositionRegion).type"
                  effect="plain"
                >
                  {{ navPositionRegionMeta(row.navPositionRegion).label }}
                </ElTag>
                <div class="text-xs text-slate-500">
                  {{ formatPositionScore(row.navPositionScore) }} · {{ row.navPositionTradeDate || '--' }}
                </div>
              </div>
              <button
                v-else
                class="position-action"
                type="button"
                @click.stop="openDetail(row as FundApi.FundListItem)"
              >
                计算
              </button>
            </template>
          </ElTableColumn>
          <ElTableColumn label="覆盖率" min-width="150">
            <template #default="{ row }">
              <div class="leading-5 tabular-nums">
                <div>持仓 {{ formatCoverage(row.estimateHoldingCoverageRate) }}</div>
                <div class="text-xs text-slate-500">行情 {{ formatCoverage(row.estimateQuoteCoverageRate) }}</div>
              </div>
            </template>
          </ElTableColumn>
          <ElTableColumn label="估值时间" min-width="190" prop="estimateTime">
            <template #default="{ row }">
              <div class="leading-5">
                <div>{{ row.estimateTime || '--' }}</div>
                <ElTag v-if="row.isStale" size="small" type="warning">已过期</ElTag>
                <div v-else-if="row.estimateStatusReason" class="max-w-52 truncate text-xs text-slate-500">
                  {{ row.estimateStatusReason }}
                </div>
              </div>
            </template>
          </ElTableColumn>
          <template #empty>
            <ElEmpty description="未找到符合条件的基金" />
          </template>
        </ElTable>
        <div class="mt-4 flex justify-end">
          <ElPagination
            v-model:current-page="query.pageNum"
            v-model:page-size="query.pageSize"
            :page-sizes="[10, 20, 50, 100]"
            :total="total"
            background
            layout="total, sizes, prev, pager, next, jumper"
            @current-change="changePage"
            @size-change="changePageSize"
          />
        </div>
      </ElCard>

      <ElDrawer
        v-model="drawerVisible"
        :size="'min(1100px, 100vw)'"
        append-to-body
        destroy-on-close
        direction="rtl"
        @close="closeDetail"
      >
        <template #header>
          <div class="drawer-title">基金详情</div>
        </template>
        <FundDetailDrawer :active="drawerVisible" :code="selectedFundCode" />
      </ElDrawer>
      <GlobalNavSyncDrawer v-model="syncDrawerVisible" />
      <NavPositionCalculationDrawer v-model="navPositionDrawerVisible" />
    </div>
  </Page>
</template>

<style scoped>
.fund-list-page {
  --fund-ink: #14213d;
  --fund-teal: #0f766e;
}

.market-header {
  align-items: end;
  background: linear-gradient(105deg, #f8fafc 0%, #eef8f6 58%, #fff7ed 100%);
  border: 1px solid rgb(148 163 184 / 22%);
  border-radius: 8px;
  display: flex;
  justify-content: space-between;
  min-height: 132px;
  padding: 24px 28px;
}

.market-header h1 {
  color: var(--fund-ink);
  font-family: 'Songti SC', 'Noto Serif CJK SC', serif;
  font-size: 30px;
  font-weight: 700;
  line-height: 1.2;
  margin: 4px 0 9px;
}

.market-header p {
  color: #64748b;
  margin: 0;
}

.market-kicker {
  color: var(--fund-teal);
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 1.6px;
}

.market-stat {
  align-items: center;
  color: #334155;
  display: flex;
  font-size: 13px;
  gap: 8px;
}

.market-actions {
  align-items: end;
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  justify-content: flex-end;
}

.market-sync-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  justify-content: flex-end;
}

.fund-filter-grid {
  display: grid;
  gap: 12px;
  grid-template-columns: repeat(auto-fit, minmax(150px, 1fr));
}

@media (min-width: 768px) {
  .fund-name-filter {
    grid-column: span 2;
  }
}

.live-dot {
  background: #0f766e;
  border-radius: 999px;
  box-shadow: 0 0 0 5px rgb(15 118 110 / 12%);
  height: 8px;
  width: 8px;
}

.fund-code {
  color: var(--fund-teal);
  cursor: pointer;
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-weight: 700;
}

.position-action {
  color: var(--fund-teal);
  cursor: pointer;
  font-size: 13px;
  font-weight: 600;
}

.position-action:hover {
  text-decoration: underline;
}

@media (max-width: 900px) {
  .market-header {
    align-items: flex-start;
    flex-direction: column;
  }

  .market-actions {
    align-items: flex-start;
    justify-content: flex-start;
  }
}

.drawer-title {
  color: #14213d;
  font-size: 16px;
  font-weight: 700;
}

:deep(.el-table__row) {
  cursor: pointer;
}

@media (max-width: 768px) {
  .market-header {
    align-items: start;
    flex-direction: column;
    gap: 18px;
    padding: 20px;
  }

  .market-header h1 {
    font-size: 25px;
  }
}
</style>
