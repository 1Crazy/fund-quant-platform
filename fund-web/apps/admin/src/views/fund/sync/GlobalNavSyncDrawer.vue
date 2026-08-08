<script lang="ts" setup>
import { computed, onBeforeUnmount, watch } from 'vue';

import { RotateCw } from '@vben/icons';
import { ElButton, ElDrawer, ElEmpty, ElMessage, ElProgress, ElTag } from 'element-plus';
import { storeToRefs } from 'pinia';

import { useFundStore } from '#/store';

import { syncStatusMeta } from '../utils/status';

const props = defineProps<{
  modelValue: boolean;
}>();

const emit = defineEmits<{
  'update:modelValue': [value: boolean];
}>();

const visible = computed({
  get: () => props.modelValue,
  set: (value: boolean) => emit('update:modelValue', value),
});
const fundStore = useFundStore();
const { globalNavSyncStatus, syncTriggerLoading } = storeToRefs(fundStore);
let pollingTimer: ReturnType<typeof setInterval> | undefined;

const progress = computed(() => {
  const status = globalNavSyncStatus.value;
  if (!status || status.totalFundCount <= 0) return 0;
  return Math.min(100, Math.round((status.processedFundCount / status.totalFundCount) * 10_000) / 100);
});

const catalogPhase = computed(() => {
  const status = globalNavSyncStatus.value;
  return Boolean(
    status &&
      ['PAUSED', 'RUNNING'].includes(status.state) &&
      !status.cursorValue,
  );
});

const phaseLabel = computed(() => {
  const status = globalNavSyncStatus.value;
  if (!status || status.state === 'IDLE') return '尚未提交全量同步';
  if (status.state === 'PAUSED') return '同步已暂停，可从当前游标继续';
  if (status.state === 'INTERRUPTED') return '服务重启后任务已中断';
  if (status.state === 'FAILED') return '同步失败';
  if (status.state === 'SUCCESS' || status.state === 'PARTIAL_SUCCESS') return '同步已结束';
  return status.cursorValue ? '正在同步全部历史确认净值' : '正在同步上游公开基金目录';
});

const statusTag = computed(() => {
  const status = globalNavSyncStatus.value?.state;
  if (!status || status === 'IDLE') return { label: '未开始', type: 'info' as const };
  return syncStatusMeta(status);
});

const progressLabel = computed(() =>
  catalogPhase.value ? '上游目录同步' : '历史净值进度',
);

const progressValue = computed(() => {
  const status = globalNavSyncStatus.value;
  if (catalogPhase.value) {
    return status?.state === 'PAUSED'
      ? '已暂停，等待继续'
      : '正在读取上游公开基金目录';
  }
  return `${formatCount(status?.processedFundCount)} / ${formatCount(status?.totalFundCount)} 只`;
});

function formatCount(value?: number) {
  return (value ?? 0).toLocaleString('zh-CN');
}

function stopPolling() {
  if (pollingTimer) {
    clearInterval(pollingTimer);
    pollingTimer = undefined;
  }
}

async function refreshStatus() {
  const status = await fundStore.fetchGlobalNavSyncStatus();
  if (status.state !== 'RUNNING') stopPolling();
}

function startPolling() {
  stopPolling();
  if (globalNavSyncStatus.value?.state !== 'RUNNING') return;
  pollingTimer = setInterval(() => {
    void refreshStatus();
  }, 4000);
}

async function resumeSync() {
  await fundStore.resumeGlobalNavSync();
  await refreshStatus();
  startPolling();
  ElMessage.success('全量历史同步已继续执行');
}

async function pauseSync() {
  await fundStore.pauseGlobalNavSync();
  await refreshStatus();
  ElMessage.success('全量历史同步已暂停，当前游标会保留');
}

watch(
  () => props.modelValue,
  async (open) => {
    if (open) {
      await refreshStatus();
      startPolling();
    } else {
      stopPolling();
    }
  },
  { immediate: true },
);

onBeforeUnmount(stopPolling);
</script>

<template>
  <ElDrawer
    v-model="visible"
    append-to-body
    direction="rtl"
    size="min(480px, 100vw)"
    title="全量历史同步"
  >
    <template v-if="globalNavSyncStatus">
      <section class="sync-overview" aria-live="polite">
        <div>
          <p class="eyebrow">GLOBAL FUND NAV</p>
          <h2>{{ phaseLabel }}</h2>
        </div>
        <ElTag :type="statusTag.type" effect="plain">{{ statusTag.label }}</ElTag>
      </section>

      <section class="sync-progress" aria-label="同步进度">
        <div class="progress-label">
          <span>{{ progressLabel }}</span>
          <strong>{{ progressValue }}</strong>
        </div>
        <ElProgress :percentage="progress" :stroke-width="10" />
        <p class="progress-note">
          首先完整更新上游公开基金目录；目录完成后，再按基金代码逐只拉取全部历史确认净值。
        </p>
      </section>

      <dl class="sync-metrics">
        <div>
          <dt>本地目录</dt>
          <dd>{{ formatCount(globalNavSyncStatus.totalFundCount) }} 只</dd>
        </div>
        <div>
          <dt>当前游标</dt>
          <dd class="code">{{ globalNavSyncStatus.cursorValue || '目录同步中' }}</dd>
        </div>
        <div>
          <dt>成功 / 拒绝 / 失败</dt>
          <dd>
            {{ formatCount(globalNavSyncStatus.successCount) }} /
            {{ formatCount(globalNavSyncStatus.rejectedCount) }} /
            {{ formatCount(globalNavSyncStatus.failedCount) }}
          </dd>
        </div>
        <div>
          <dt>开始时间</dt>
          <dd>{{ globalNavSyncStatus.startedAt || '--' }}</dd>
        </div>
        <div v-if="globalNavSyncStatus.finishedAt">
          <dt>结束时间</dt>
          <dd>{{ globalNavSyncStatus.finishedAt }}</dd>
        </div>
      </dl>

      <p v-if="globalNavSyncStatus.errorMessage" class="sync-message">
        {{ globalNavSyncStatus.errorMessage }}
      </p>

      <div class="drawer-actions">
        <ElButton
          v-if="globalNavSyncStatus.state === 'RUNNING'"
          :loading="syncTriggerLoading"
          type="warning"
          @click="pauseSync"
        >
          暂停同步
        </ElButton>
        <ElButton
          v-if="globalNavSyncStatus.state === 'PAUSED' || globalNavSyncStatus.resumable"
          :loading="syncTriggerLoading"
          type="primary"
          @click="resumeSync"
        >
          <RotateCw class="mr-1 size-4" />继续同步
        </ElButton>
      </div>
    </template>
    <ElEmpty v-else description="正在读取同步状态" />
  </ElDrawer>
</template>

<style scoped>
.sync-overview {
  align-items: start;
  border-bottom: 1px solid #e2e8f0;
  display: flex;
  gap: 16px;
  justify-content: space-between;
  padding-bottom: 20px;
}

.sync-overview h2 {
  color: #14213d;
  font-size: 20px;
  font-weight: 700;
  line-height: 1.35;
  margin: 4px 0 0;
}

.eyebrow {
  color: #0f766e;
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 1.4px;
  margin: 0;
}

.sync-progress {
  padding: 24px 0;
}

.progress-label {
  align-items: baseline;
  color: #334155;
  display: flex;
  font-size: 14px;
  justify-content: space-between;
  margin-bottom: 10px;
}

.progress-label strong {
  color: #0f766e;
  font-variant-numeric: tabular-nums;
}

.progress-note {
  color: #64748b;
  font-size: 13px;
  line-height: 1.65;
  margin: 12px 0 0;
}

.sync-metrics {
  border-top: 1px solid #e2e8f0;
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  margin: 0;
}

.sync-metrics > div {
  border-bottom: 1px solid #e2e8f0;
  padding: 16px 12px 16px 0;
}

.sync-metrics > div:nth-child(odd) {
  padding-right: 16px;
}

.sync-metrics dt {
  color: #64748b;
  font-size: 12px;
  margin-bottom: 6px;
}

.sync-metrics dd {
  color: #1e293b;
  font-size: 14px;
  font-variant-numeric: tabular-nums;
  line-height: 1.45;
  margin: 0;
  overflow-wrap: anywhere;
}

.sync-metrics .code {
  color: #0f766e;
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
}

.sync-message {
  background: #fff7ed;
  border-left: 3px solid #f59e0b;
  color: #92400e;
  font-size: 13px;
  line-height: 1.6;
  margin: 20px 0 0;
  padding: 10px 12px;
}

.drawer-actions {
  display: flex;
  justify-content: flex-end;
  margin-top: 24px;
}

@media (max-width: 420px) {
  .sync-metrics {
    grid-template-columns: 1fr;
  }

  .sync-metrics > div,
  .sync-metrics > div:nth-child(odd) {
    padding-right: 0;
  }
}
</style>
