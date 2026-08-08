<script lang="ts" setup>
import {
  computed,
  onActivated,
  onBeforeUnmount,
  onDeactivated,
  onMounted,
  reactive,
  ref,
} from 'vue';

import { useAccess } from '@vben/access';
import { Page } from '@vben/common-ui';
import { RotateCw, Search } from '@vben/icons';

import {
  ElButton,
  ElCard,
  ElDatePicker,
  ElDescriptions,
  ElDescriptionsItem,
  ElEmpty,
  ElInput,
  ElMessage,
  ElOption,
  ElPagination,
  ElProgress,
  ElSelect,
  ElTable,
  ElTableColumn,
  ElTabPane,
  ElTag,
  ElTabs,
} from 'element-plus';
import { storeToRefs } from 'pinia';

import type { FundApi } from '#/api/fund';
import { getFundQualityIssuesApi, getFundSyncRunsApi } from '#/api/fund';
import { useFundStore } from '#/store';

import {
  datasetLabel,
  formatCount,
  formatDuration,
  manualSyncPermissions,
  syncStatusMeta,
  syncTypeLabel,
} from '../utils/status';

const fundStore = useFundStore();
const { hasAccessByCodes } = useAccess();
const {
  syncQuery,
  syncRuns,
  syncRunsLoading,
  syncRunsTotal,
  syncStatus,
  syncTriggerLoading,
  globalNavSyncStatus,
} = storeToRefs(fundStore);

const triggerForm = reactive<FundApi.FundManualSyncPayload>({
  dataset: 'FUND_INFO',
  fundCode: '',
  rangeEndDate: '',
  rangeStartDate: '',
  syncScope: 'SINGLE_FUND',
  syncType: 'LAZY_LOAD',
});

const canManualSync = computed(() => hasAccessByCodes(manualSyncPermissions));
const activeRuns = computed(() => syncStatus.value?.activeRuns ?? []);
const lastRun = computed(() => syncStatus.value?.lastRun);
const hasRunningSync = computed(
  () =>
    globalNavSyncStatus.value?.state === 'RUNNING' ||
    (syncStatus.value?.runningCount ?? 0) > 0,
);
const syncStartedAtRange = ref<string[]>([]);
const failedRuns = ref<FundApi.FundSyncRun[]>([]);
const failedRunsLoading = ref(false);
const failedRunsTotal = ref(0);
const failedRunQuery = reactive<FundApi.FundSyncRunParams>({
  dataset: '',
  fundCode: '',
  pageNum: 1,
  pageSize: 10,
  status: 'FAILED',
  syncType: '',
});
const qualityIssues = ref<FundApi.FundDataQualityIssue[]>([]);
const qualityIssuesLoading = ref(false);
const qualityIssuesTotal = ref(0);
const qualityIssueQuery = reactive<FundApi.FundQualityIssueParams>({
  dataset: '',
  issueStatus: '',
  pageNum: 1,
  pageSize: 10,
  reasonCode: '',
});
const globalNavProgress = computed(() => {
  const status = globalNavSyncStatus.value;
  if (!status?.totalFundCount) return 0;
  return Math.min(100, Math.round((status.processedFundCount / status.totalFundCount) * 100));
});
let syncPollingTimer: ReturnType<typeof setInterval> | undefined;
let syncPageActive = false;

function progressPercent(row: FundApi.FundSyncRun) {
  if (!row.totalCount) return row.status === 'SUCCESS' ? 100 : 0;
  return Math.min(
    100,
    Math.round((formatCount(row.successCount) / row.totalCount) * 100),
  );
}

function canRetry(row: FundApi.FundSyncRun) {
  return canManualSync.value && ['FAILED', 'PARTIAL_SUCCESS'].includes(row.status);
}

async function search() {
  syncQuery.value.fundCode = syncQuery.value.fundCode?.trim() ?? '';
  const [startedAtStart, startedAtEnd] = syncStartedAtRange.value;
  syncQuery.value.startedAtStart = startedAtStart
    ? `${startedAtStart}T00:00:00+08:00`
    : '';
  syncQuery.value.startedAtEnd = startedAtEnd
    ? `${startedAtEnd}T23:59:59+08:00`
    : '';
  await fundStore.fetchSyncRuns(true);
}

async function reset() {
  fundStore.resetSyncQuery();
  syncStartedAtRange.value = [];
  await fundStore.fetchSyncRuns(true);
}

function changePage() {
  void fundStore.fetchSyncRuns();
}

function changePageSize() {
  void fundStore.fetchSyncRuns(true);
}

async function triggerSync() {
  const result = await fundStore.triggerSync({
    ...triggerForm,
    fundCode: triggerForm.fundCode?.trim() || undefined,
    rangeEndDate: triggerForm.rangeEndDate || undefined,
    rangeStartDate: triggerForm.rangeStartDate || undefined,
  });
  await refreshDashboard();
  startSyncPolling();
  ElMessage.success(result.message || '同步任务已提交');
}

async function retry(row: FundApi.FundSyncRun) {
  const result = await fundStore.retrySync(row.id ?? row.runId);
  await refreshDashboard();
  startSyncPolling();
  ElMessage.success(result.message || '重试任务已提交');
}

async function loadFailedRuns(resetPage = false) {
  if (resetPage) failedRunQuery.pageNum = 1;
  failedRunsLoading.value = true;
  try {
    const result = await getFundSyncRunsApi({
      ...failedRunQuery,
      fundCode: failedRunQuery.fundCode?.trim(),
      status: 'FAILED',
    });
    failedRuns.value = result.items;
    failedRunsTotal.value = result.total;
  } catch {
    ElMessage.error('失败同步记录读取失败');
  } finally {
    failedRunsLoading.value = false;
  }
}

function resetFailedRuns() {
  Object.assign(failedRunQuery, {
    dataset: '',
    fundCode: '',
    pageNum: 1,
    pageSize: 10,
    status: 'FAILED',
    syncType: '',
  });
  void loadFailedRuns();
}

async function retryFailedRun(row: FundApi.FundSyncRun) {
  await retry(row);
  await loadFailedRuns();
}

async function loadQualityIssues(resetPage = false) {
  if (resetPage) qualityIssueQuery.pageNum = 1;
  qualityIssuesLoading.value = true;
  try {
    const result = await getFundQualityIssuesApi({
      ...qualityIssueQuery,
      reasonCode: qualityIssueQuery.reasonCode?.trim(),
    });
    qualityIssues.value = result.items;
    qualityIssuesTotal.value = result.total;
  } catch {
    ElMessage.error('异常数据明细读取失败');
  } finally {
    qualityIssuesLoading.value = false;
  }
}

function resetQualityIssues() {
  Object.assign(qualityIssueQuery, {
    dataset: '',
    issueStatus: '',
    pageNum: 1,
    pageSize: 10,
    reasonCode: '',
  });
  void loadQualityIssues();
}

function issueFundCode(row: FundApi.FundDataQualityIssue) {
  return row.fundCode || row.recordKey?.match(/^\d{6}/)?.[0] || '--';
}

async function refreshDashboard() {
  await Promise.all([
    fundStore.fetchGlobalNavSyncStatus(),
    fundStore.fetchSyncStatus(),
    fundStore.fetchSyncRuns(),
  ]);
  if (!hasRunningSync.value) stopSyncPolling();
}

async function resumeGlobalNavSync() {
  const result = await fundStore.resumeGlobalNavSync();
  await refreshDashboard();
  startSyncPolling();
  ElMessage.success(result.message || '全量历史净值同步已继续执行');
}

function startSyncPolling() {
  stopSyncPolling();
  if (!syncPageActive || !hasRunningSync.value) return;
  syncPollingTimer = setInterval(() => {
    void refreshDashboard();
  }, 4000);
}

function stopSyncPolling() {
  if (syncPollingTimer) {
    clearInterval(syncPollingTimer);
    syncPollingTimer = undefined;
  }
}

async function activateSyncPage() {
  if (syncPageActive) return;
  syncPageActive = true;
  await Promise.all([refreshDashboard(), loadFailedRuns(), loadQualityIssues()]);
  if (syncPageActive) startSyncPolling();
}

function deactivateSyncPage() {
  syncPageActive = false;
  stopSyncPolling();
}

onMounted(() => {
  void activateSyncPage();
});

onActivated(() => {
  void activateSyncPage();
});

onDeactivated(() => {
  deactivateSyncPage();
});

onBeforeUnmount(() => {
  deactivateSyncPage();
});
</script>

<template>
  <Page auto-content-height>
    <div class="fund-sync-page grid gap-4">
      <section class="sync-header">
        <div>
          <div class="sync-kicker">FUND DATA CENTER</div>
          <h1>同步记录</h1>
          <p>查询每次基金数据同步的成功、失败、重试与数据版本，并快速定位需要处理的异常批次。</p>
        </div>
        <div class="sync-health">
          <ElTag :type="lastRun ? syncStatusMeta(lastRun.status).type : 'info'" effect="plain">
            最近批次：{{ lastRun ? syncStatusMeta(lastRun.status).label : '暂无' }}
          </ElTag>
          <span>{{ syncStatus?.updatedAt || '--' }}</span>
        </div>
      </section>

      <div class="order-2 grid gap-4 xl:grid-cols-[minmax(0,1fr)_360px]">
        <ElCard shadow="never">
          <template #header><span class="panel-title">全量历史净值同步</span></template>
          <div v-if="globalNavSyncStatus" class="global-nav-status">
            <div class="global-nav-status__summary">
              <div>
                <span>任务状态</span>
                <strong>{{ syncStatusMeta(globalNavSyncStatus.state).label }}</strong>
              </div>
              <ElTag :type="syncStatusMeta(globalNavSyncStatus.state).type" effect="plain">
                {{ globalNavSyncStatus.processedFundCount }}/{{ globalNavSyncStatus.totalFundCount }} 只基金
              </ElTag>
            </div>
            <ElProgress
              :percentage="globalNavProgress"
              :status="globalNavSyncStatus.state === 'FAILED' ? 'exception' : undefined"
              class="mt-3"
            />
            <div class="global-nav-status__meta">
              <span>当前游标：{{ globalNavSyncStatus.cursorValue || '尚未开始逐只拉取' }}</span>
              <span>成功 {{ formatCount(globalNavSyncStatus.successCount) }} / 失败 {{ formatCount(globalNavSyncStatus.failedCount) }}</span>
            </div>
            <p v-if="globalNavSyncStatus.errorMessage" class="global-nav-status__message">
              {{ globalNavSyncStatus.errorMessage }}
            </p>
            <ElButton
              v-if="globalNavSyncStatus.resumable && canManualSync"
              :loading="syncTriggerLoading"
              link
              type="primary"
              @click="resumeGlobalNavSync"
            >
              <RotateCw class="mr-1 size-4" />继续同步
            </ElButton>
          </div>
          <ElEmpty v-else description="正在读取全量历史同步状态" />
          <div class="my-4 border-t border-slate-200" />
          <span class="panel-title">其他同步状态</span>
          <div class="grid gap-3 md:grid-cols-4">
            <div class="metric-card">
              <span>运行中</span>
              <strong>{{ syncStatus?.runningCount ?? activeRuns.length }}</strong>
            </div>
            <div class="metric-card">
              <span>部分成功</span>
              <strong>{{ syncStatus?.partialCount ?? 0 }}</strong>
            </div>
            <div class="metric-card">
              <span>失败批次</span>
              <strong>{{ syncStatus?.failedCount ?? 0 }}</strong>
            </div>
            <div class="metric-card">
              <span>过期数据</span>
              <strong>{{ syncStatus?.staleCount ?? 0 }}</strong>
            </div>
          </div>
          <ElTable v-if="activeRuns.length" class="mt-4" :data="activeRuns" stripe>
            <ElTableColumn label="运行 ID" min-width="170" prop="runId" show-overflow-tooltip />
            <ElTableColumn label="数据集" min-width="110">
              <template #default="{ row }">{{ datasetLabel(row.dataset) }}</template>
            </ElTableColumn>
            <ElTableColumn label="状态" min-width="110">
              <template #default="{ row }">
                <ElTag :type="syncStatusMeta(row.status).type" effect="plain">
                  {{ syncStatusMeta(row.status).label }}
                </ElTag>
              </template>
            </ElTableColumn>
            <ElTableColumn label="进度" min-width="180">
              <template #default="{ row }">
                <ElProgress :percentage="progressPercent(row)" />
              </template>
            </ElTableColumn>
            <ElTableColumn label="游标" min-width="160" prop="cursorValue" show-overflow-tooltip />
          </ElTable>
          <ElEmpty v-else class="mt-3" description="当前没有运行中的同步任务" />
        </ElCard>

        <ElCard shadow="never">
          <template #header><span class="panel-title">手动触发</span></template>
          <div v-if="canManualSync" class="flex flex-col gap-3">
            <ElSelect v-model="triggerForm.dataset" placeholder="数据集">
              <ElOption label="基金档案" value="FUND_INFO" />
              <ElOption label="确认净值" value="FUND_NAV" />
              <ElOption label="披露持仓" value="FUND_HOLDING" />
            </ElSelect>
            <ElSelect v-model="triggerForm.syncScope" placeholder="同步范围">
              <ElOption label="单基金" value="SINGLE_FUND" />
              <ElOption label="全局范围" value="GLOBAL" />
            </ElSelect>
            <ElInput
              v-model="triggerForm.fundCode"
              clearable
              maxlength="12"
              placeholder="基金代码（单基金时）"
            />
            <ElDatePicker
              v-model="triggerForm.rangeStartDate"
              placeholder="开始日期"
              type="date"
              value-format="YYYY-MM-DD"
              class="w-full"
            />
            <ElDatePicker
              v-model="triggerForm.rangeEndDate"
              placeholder="结束日期"
              type="date"
              value-format="YYYY-MM-DD"
              class="w-full"
            />
            <ElButton :loading="syncTriggerLoading" type="primary" @click="triggerSync">
              <RotateCw class="mr-1 size-4" />提交同步
            </ElButton>
          </div>
          <ElEmpty v-else description="当前账号没有手动触发同步权限" />
        </ElCard>
      </div>

      <ElCard class="order-3 min-h-0 flex-1" shadow="never">
        <template #header>
          <div class="flex flex-wrap items-center justify-between gap-3">
            <span class="panel-title">运行历史</span>
            <div class="grid gap-2 lg:grid-cols-[140px_140px_140px_220px_160px_auto]">
              <ElSelect v-model="syncQuery.dataset" clearable placeholder="数据集">
                <ElOption label="基金档案" value="FUND_INFO" />
                <ElOption label="确认净值" value="FUND_NAV" />
                <ElOption label="披露持仓" value="FUND_HOLDING" />
              </ElSelect>
              <ElSelect v-model="syncQuery.status" clearable placeholder="状态">
                <ElOption label="等待中" value="PENDING" />
                <ElOption label="运行中" value="RUNNING" />
                <ElOption label="已暂停" value="PAUSED" />
                <ElOption label="成功" value="SUCCESS" />
                <ElOption label="部分成功" value="PARTIAL_SUCCESS" />
                <ElOption label="失败" value="FAILED" />
                <ElOption label="已取消" value="CANCELLED" />
              </ElSelect>
              <ElSelect v-model="syncQuery.syncType" clearable placeholder="类型">
                <ElOption label="全量历史同步" value="FULL_HISTORY" />
                <ElOption label="按最新净值续拉" value="CONTINUE_FROM_LATEST_NAV" />
                <ElOption label="全量初始化" value="FULL_INIT" />
                <ElOption label="增量同步" value="INCREMENTAL" />
                <ElOption label="按需懒加载" value="LAZY_LOAD" />
                <ElOption label="历史 NAV 回填" value="NAV_BACKFILL" />
                <ElOption label="持仓回填" value="HOLDING_BACKFILL" />
              </ElSelect>
              <ElDatePicker
                v-model="syncStartedAtRange"
                clearable
                end-placeholder="结束日期"
                range-separator="至"
                start-placeholder="开始日期"
                type="daterange"
                value-format="YYYY-MM-DD"
              />
              <ElInput v-model="syncQuery.fundCode" clearable placeholder="基金代码" @keyup.enter="search" />
              <div class="flex gap-2">
                <ElButton :loading="syncRunsLoading" type="primary" @click="search">
                  <Search class="mr-1 size-4" />查询
                </ElButton>
                <ElButton @click="reset">
                  <RotateCw class="mr-1 size-4" />重置
                </ElButton>
              </div>
            </div>
          </div>
        </template>

        <ElTable
          v-loading="syncRunsLoading"
          :data="syncRuns"
          height="100%"
          row-key="runId"
          stripe
        >
          <ElTableColumn label="运行 ID" min-width="170" prop="runId" show-overflow-tooltip />
          <ElTableColumn label="数据集" min-width="110">
            <template #default="{ row }">{{ datasetLabel(row.dataset) }}</template>
          </ElTableColumn>
          <ElTableColumn label="类型" min-width="130">
            <template #default="{ row }">{{ syncTypeLabel(row.syncType) }}</template>
          </ElTableColumn>
          <ElTableColumn label="范围" min-width="150" show-overflow-tooltip>
            <template #default="{ row }">
              {{ row.fundCode || row.syncScope || '--' }}
            </template>
          </ElTableColumn>
          <ElTableColumn label="状态" min-width="115">
            <template #default="{ row }">
              <ElTag :type="syncStatusMeta(row.status).type" effect="plain">
                {{ syncStatusMeta(row.status).label }}
              </ElTag>
            </template>
          </ElTableColumn>
          <ElTableColumn label="计数器" min-width="230">
            <template #default="{ row }">
              <ElDescriptions :column="2" size="small">
                <ElDescriptionsItem label="成功">{{ formatCount(row.successCount) }}</ElDescriptionsItem>
                <ElDescriptionsItem label="拒绝">{{ formatCount(row.rejectedCount) }}</ElDescriptionsItem>
                <ElDescriptionsItem label="失败">{{ formatCount(row.failedCount) }}</ElDescriptionsItem>
                <ElDescriptionsItem label="重试">{{ formatCount(row.retryCount) }}</ElDescriptionsItem>
              </ElDescriptions>
            </template>
          </ElTableColumn>
          <ElTableColumn label="失败摘要" min-width="220" prop="errorSummary" show-overflow-tooltip />
          <ElTableColumn label="数据版本" min-width="170" prop="dataVersion" show-overflow-tooltip />
          <ElTableColumn label="开始时间" min-width="180" prop="startedAt" />
          <ElTableColumn label="耗时" min-width="100">
            <template #default="{ row }">{{ formatDuration(row.durationMillis) }}</template>
          </ElTableColumn>
          <ElTableColumn fixed="right" label="操作" min-width="100">
            <template #default="{ row }">
              <ElButton
                v-if="canRetry(row)"
                :loading="syncTriggerLoading"
                link
                type="primary"
                @click="retry(row)"
              >
                重试
              </ElButton>
              <span v-else class="text-slate-400">--</span>
            </template>
          </ElTableColumn>
          <template #empty>
            <ElEmpty description="暂无同步运行记录" />
          </template>
        </ElTable>

        <div class="mt-4 flex justify-end">
          <ElPagination
            v-model:current-page="syncQuery.pageNum"
            v-model:page-size="syncQuery.pageSize"
            :page-sizes="[10, 20, 50, 100]"
            :total="syncRunsTotal"
            background
            layout="total, sizes, prev, pager, next, jumper"
            @current-change="changePage"
            @size-change="changePageSize"
          />
        </div>
      </ElCard>

      <ElCard class="order-1" shadow="never">
        <template #header>
          <div>
            <span class="panel-title">失败与异常数据</span>
            <p class="mt-1 text-xs text-slate-500">
              同步失败记录用于定位未拉取成功的基金；质量异常记录用于定位已拉取但被校验拒绝的具体数据。
            </p>
          </div>
        </template>

        <ElTabs>
          <ElTabPane label="同步失败">
            <div class="mb-4 flex flex-wrap gap-2">
              <ElInput
                v-model="failedRunQuery.fundCode"
                clearable
                class="w-44"
                placeholder="基金代码"
                @keyup.enter="loadFailedRuns(true)"
              />
              <ElSelect v-model="failedRunQuery.dataset" clearable class="w-36" placeholder="数据集">
                <ElOption label="基金档案" value="FUND_INFO" />
                <ElOption label="确认净值" value="FUND_NAV" />
                <ElOption label="披露持仓" value="FUND_HOLDING" />
              </ElSelect>
              <ElSelect v-model="failedRunQuery.syncType" clearable class="w-44" placeholder="同步类型">
                <ElOption label="全量历史同步" value="FULL_HISTORY" />
                <ElOption label="全量初始化" value="FULL_INIT" />
                <ElOption label="增量同步" value="INCREMENTAL" />
                <ElOption label="按需懒加载" value="LAZY_LOAD" />
                <ElOption label="历史 NAV 回填" value="NAV_BACKFILL" />
              </ElSelect>
              <ElButton :loading="failedRunsLoading" type="primary" @click="loadFailedRuns(true)">
                <Search class="mr-1 size-4" />查询失败数据
              </ElButton>
              <ElButton @click="resetFailedRuns">
                <RotateCw class="mr-1 size-4" />重置
              </ElButton>
            </div>

            <ElTable v-loading="failedRunsLoading" :data="failedRuns" row-key="runId" stripe>
              <ElTableColumn label="基金代码" min-width="110">
                <template #default="{ row }">{{ row.fundCode || row.scopeValue || '--' }}</template>
              </ElTableColumn>
              <ElTableColumn label="数据集" min-width="110">
                <template #default="{ row }">{{ datasetLabel(row.dataset) }}</template>
              </ElTableColumn>
              <ElTableColumn label="同步类型" min-width="130">
                <template #default="{ row }">{{ syncTypeLabel(row.syncType) }}</template>
              </ElTableColumn>
              <ElTableColumn label="失败原因" min-width="300" show-overflow-tooltip>
                <template #default="{ row }">
                  {{ row.errorSummary || row.errorMessage || row.errorCode || '--' }}
                </template>
              </ElTableColumn>
              <ElTableColumn label="重试次数" min-width="95">
                <template #default="{ row }">{{ formatCount(row.retryCount) }}</template>
              </ElTableColumn>
              <ElTableColumn label="失败时间" min-width="180">
                <template #default="{ row }">{{ row.finishedAt || row.startedAt || '--' }}</template>
              </ElTableColumn>
              <ElTableColumn fixed="right" label="操作" min-width="90">
                <template #default="{ row }">
                  <ElButton
                    v-if="canRetry(row)"
                    :loading="syncTriggerLoading"
                    link
                    type="primary"
                    @click="retryFailedRun(row)"
                  >
                    重试
                  </ElButton>
                  <span v-else class="text-slate-400">--</span>
                </template>
              </ElTableColumn>
              <template #empty>
                <ElEmpty description="暂无失败的同步数据" />
              </template>
            </ElTable>
            <div class="mt-4 flex justify-end">
              <ElPagination
                v-model:current-page="failedRunQuery.pageNum"
                v-model:page-size="failedRunQuery.pageSize"
                :page-sizes="[10, 20, 50, 100]"
                :total="failedRunsTotal"
                background
                layout="total, sizes, prev, pager, next, jumper"
                @current-change="() => loadFailedRuns()"
                @size-change="() => loadFailedRuns(true)"
              />
            </div>
          </ElTabPane>

          <ElTabPane label="质量异常">
            <div class="mb-4 flex flex-wrap gap-2">
              <ElSelect v-model="qualityIssueQuery.dataset" clearable class="w-36" placeholder="数据集">
                <ElOption label="基金档案" value="FUND_INFO" />
                <ElOption label="确认净值" value="FUND_NAV" />
                <ElOption label="披露持仓" value="FUND_HOLDING" />
              </ElSelect>
              <ElInput
                v-model="qualityIssueQuery.reasonCode"
                clearable
                class="w-52"
                placeholder="异常原因编码"
                @keyup.enter="loadQualityIssues(true)"
              />
              <ElSelect v-model="qualityIssueQuery.issueStatus" clearable class="w-36" placeholder="处置状态">
                <ElOption label="待处理" value="OPEN" />
                <ElOption label="已忽略" value="IGNORED" />
                <ElOption label="已解决" value="RESOLVED" />
              </ElSelect>
              <ElButton :loading="qualityIssuesLoading" type="primary" @click="loadQualityIssues(true)">
                <Search class="mr-1 size-4" />查询异常数据
              </ElButton>
              <ElButton @click="resetQualityIssues">
                <RotateCw class="mr-1 size-4" />重置
              </ElButton>
            </div>

            <ElTable v-loading="qualityIssuesLoading" :data="qualityIssues" row-key="recordKey" stripe>
              <ElTableColumn label="基金代码" min-width="110">
                <template #default="{ row }">{{ issueFundCode(row) }}</template>
              </ElTableColumn>
              <ElTableColumn label="记录键" min-width="210" prop="recordKey" show-overflow-tooltip />
              <ElTableColumn label="数据集" min-width="110">
                <template #default="{ row }">{{ datasetLabel(row.dataset) }}</template>
              </ElTableColumn>
              <ElTableColumn label="异常原因" min-width="180">
                <template #default="{ row }">{{ row.reasonCode || '--' }}</template>
              </ElTableColumn>
              <ElTableColumn label="原始数据摘要" min-width="300" show-overflow-tooltip>
                <template #default="{ row }">{{ row.reasonMessage || row.rawSummary || '--' }}</template>
              </ElTableColumn>
              <ElTableColumn label="处置状态" min-width="110">
                <template #default="{ row }">{{ row.issueStatus || '待处理' }}</template>
              </ElTableColumn>
              <ElTableColumn label="发现时间" min-width="180">
                <template #default="{ row }">{{ row.detectedAt || row.discoveredAt || '--' }}</template>
              </ElTableColumn>
              <template #empty>
                <ElEmpty description="暂无被校验拒绝的数据" />
              </template>
            </ElTable>
            <div class="mt-4 flex justify-end">
              <ElPagination
                v-model:current-page="qualityIssueQuery.pageNum"
                v-model:page-size="qualityIssueQuery.pageSize"
                :page-sizes="[10, 20, 50, 100]"
                :total="qualityIssuesTotal"
                background
                layout="total, sizes, prev, pager, next, jumper"
                @current-change="() => loadQualityIssues()"
                @size-change="() => loadQualityIssues(true)"
              />
            </div>
          </ElTabPane>
        </ElTabs>
      </ElCard>
    </div>
  </Page>
</template>

<style scoped>
.fund-sync-page {
  --fund-ink: #14213d;
  --fund-teal: #0f766e;
}

.sync-header {
  align-items: end;
  background: linear-gradient(105deg, #f8fafc 0%, #eef8f6 58%, #fff7ed 100%);
  border: 1px solid rgb(148 163 184 / 22%);
  border-radius: 8px;
  display: flex;
  justify-content: space-between;
  min-height: 132px;
  padding: 24px 28px;
}

.sync-header h1 {
  color: var(--fund-ink);
  font-family: 'Songti SC', 'Noto Serif CJK SC', serif;
  font-size: 30px;
  font-weight: 700;
  line-height: 1.2;
  margin: 4px 0 9px;
}

.sync-header p {
  color: #64748b;
  margin: 0;
}

.global-nav-status {
  display: grid;
  gap: 10px;
}

.global-nav-status__summary,
.global-nav-status__meta {
  align-items: center;
  display: flex;
  flex-wrap: wrap;
  gap: 8px 16px;
  justify-content: space-between;
}

.global-nav-status__summary span,
.global-nav-status__meta,
.global-nav-status__message {
  color: #64748b;
  font-size: 13px;
}

.global-nav-status__summary strong {
  color: var(--fund-ink);
  display: block;
  font-size: 20px;
  margin-top: 2px;
}

.global-nav-status__message {
  margin: 0;
}

.sync-kicker {
  color: var(--fund-teal);
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 1.6px;
}

.sync-health {
  align-items: center;
  color: #64748b;
  display: flex;
  flex-direction: column;
  font-size: 12px;
  gap: 8px;
}

.panel-title {
  font-size: 15px;
  font-weight: 700;
}

.metric-card {
  background: #f8fafc;
  border: 1px solid rgb(148 163 184 / 22%);
  border-radius: 8px;
  padding: 16px;
}

.metric-card span {
  color: #64748b;
  display: block;
  font-size: 12px;
}

.metric-card strong {
  color: var(--fund-ink);
  display: block;
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-size: 28px;
  margin-top: 6px;
}

@media (max-width: 768px) {
  .sync-header {
    align-items: start;
    flex-direction: column;
    gap: 18px;
    padding: 20px;
  }

  .sync-health {
    align-items: start;
  }
}
</style>
