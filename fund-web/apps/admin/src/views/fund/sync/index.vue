<script lang="ts" setup>
import { computed, onMounted, reactive } from 'vue';

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
  ElTag,
} from 'element-plus';
import { storeToRefs } from 'pinia';

import type { FundApi } from '#/api/fund';
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
  await fundStore.fetchSyncRuns(true);
}

async function reset() {
  fundStore.resetSyncQuery();
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
  ElMessage.success(result.message || '同步任务已提交');
}

async function retry(row: FundApi.FundSyncRun) {
  const result = await fundStore.retrySync(row.id ?? row.runId);
  ElMessage.success(result.message || '重试任务已提交');
}

onMounted(() => {
  void fundStore.fetchSyncStatus();
  void fundStore.fetchSyncRuns();
});
</script>

<template>
  <Page auto-content-height>
    <div class="fund-sync-page flex h-full min-h-0 flex-col gap-4">
      <section class="sync-header">
        <div>
          <div class="sync-kicker">FUND DATA CENTER</div>
          <h1>同步管理</h1>
          <p>观测基金数据同步批次、进度计数、失败摘要，并在授权后触发单基金或范围同步。</p>
        </div>
        <div class="sync-health">
          <ElTag :type="lastRun ? syncStatusMeta(lastRun.status).type : 'info'" effect="plain">
            最近批次：{{ lastRun ? syncStatusMeta(lastRun.status).label : '暂无' }}
          </ElTag>
          <span>{{ syncStatus?.updatedAt || '--' }}</span>
        </div>
      </section>

      <div class="grid gap-4 xl:grid-cols-[minmax(0,1fr)_360px]">
        <ElCard shadow="never">
          <template #header><span class="panel-title">当前同步状态</span></template>
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

      <ElCard class="min-h-0 flex-1" shadow="never">
        <template #header>
          <div class="flex flex-wrap items-center justify-between gap-3">
            <span class="panel-title">运行历史</span>
            <div class="grid gap-2 lg:grid-cols-[140px_140px_140px_160px_auto]">
              <ElSelect v-model="syncQuery.dataset" clearable placeholder="数据集">
                <ElOption label="基金档案" value="FUND_INFO" />
                <ElOption label="确认净值" value="FUND_NAV" />
                <ElOption label="披露持仓" value="FUND_HOLDING" />
              </ElSelect>
              <ElSelect v-model="syncQuery.status" clearable placeholder="状态">
                <ElOption label="等待中" value="PENDING" />
                <ElOption label="运行中" value="RUNNING" />
                <ElOption label="成功" value="SUCCESS" />
                <ElOption label="部分成功" value="PARTIAL_SUCCESS" />
                <ElOption label="失败" value="FAILED" />
                <ElOption label="已取消" value="CANCELLED" />
              </ElSelect>
              <ElSelect v-model="syncQuery.syncType" clearable placeholder="类型">
                <ElOption label="全量初始化" value="FULL_INIT" />
                <ElOption label="增量同步" value="INCREMENTAL" />
                <ElOption label="按需懒加载" value="LAZY_LOAD" />
                <ElOption label="历史 NAV 回填" value="NAV_BACKFILL" />
                <ElOption label="持仓回填" value="HOLDING_BACKFILL" />
              </ElSelect>
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
