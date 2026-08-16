<script lang="ts" setup>
import { computed, ref } from 'vue';

import { RotateCw } from '@vben/icons';

import { ElButton, ElDrawer, ElEmpty, ElProgress, ElTag } from 'element-plus';
import { storeToRefs } from 'pinia';

import { useFundStore } from '#/store';

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
const { navPositionBatchStatus } = storeToRefs(fundStore);
const refreshing = ref(false);

const progress = computed(() => {
  const status = navPositionBatchStatus.value;
  if (!status || status.requestedCount <= 0) return 0;
  return Math.min(
    100,
    Math.round((status.processedCount / status.requestedCount) * 10_000) / 100,
  );
});

const statusMeta = computed(() => {
  switch (navPositionBatchStatus.value?.state) {
    case 'FAILED':
      return { label: '计算失败', type: 'danger' as const };
    case 'PARTIAL_SUCCESS':
      return { label: '部分完成', type: 'warning' as const };
    case 'RUNNING':
      return { label: '计算中', type: 'primary' as const };
    case 'SUCCESS':
      return { label: '计算完成', type: 'success' as const };
    default:
      return { label: '未开始', type: 'info' as const };
  }
});

const phaseLabel = computed(() => {
  const status = navPositionBatchStatus.value;
  if (!status || status.state === 'IDLE') return '尚未提交全量历史位置计算';
  if (status.state === 'RUNNING') {
    return status.requestedCount > 0 ? '正在按基金逐只计算历史位置' : '正在读取待计算基金';
  }
  if (status.state === 'FAILED') return '历史位置计算失败';
  return '历史位置计算已结束';
});

const progressValue = computed(() => {
  const status = navPositionBatchStatus.value;
  if (!status || status.requestedCount <= 0) return '等待任务初始化';
  return `${formatCount(status.processedCount)} / ${formatCount(status.requestedCount)} 只`;
});

function formatCount(value?: number) {
  return (value ?? 0).toLocaleString('zh-CN');
}

async function refreshStatus() {
  refreshing.value = true;
  try {
    await fundStore.fetchNavPositionBatchStatus();
  } finally {
    refreshing.value = false;
  }
}
</script>

<template>
  <ElDrawer
    v-model="visible"
    append-to-body
    direction="rtl"
    size="min(480px, 100vw)"
    title="全量历史位置计算"
  >
    <template v-if="navPositionBatchStatus">
      <section class="calculation-overview" aria-live="polite">
        <div>
          <p class="eyebrow">HISTORICAL NAV POSITION</p>
          <h2>{{ phaseLabel }}</h2>
        </div>
        <ElTag :type="statusMeta.type" effect="plain">{{ statusMeta.label }}</ElTag>
      </section>

      <section class="calculation-progress" aria-label="历史位置计算进度">
        <div class="progress-label">
          <span>计算进度</span>
          <strong>{{ progressValue }}</strong>
        </div>
        <ElProgress :percentage="progress" :stroke-width="10" />
        <p class="progress-note">
          仅计算已有确认净值的有效基金。净值样本不足或计算异常的基金不会进入低估值筛选结果。
        </p>
      </section>

      <dl class="calculation-metrics">
        <div>
          <dt>待计算基金</dt>
          <dd>{{ formatCount(navPositionBatchStatus.requestedCount) }} 只</dd>
        </div>
        <div>
          <dt>已处理</dt>
          <dd>{{ formatCount(navPositionBatchStatus.processedCount) }} 只</dd>
        </div>
        <div>
          <dt>可用结果</dt>
          <dd>{{ formatCount(navPositionBatchStatus.normalCount) }} 只</dd>
        </div>
        <div>
          <dt>不可用</dt>
          <dd>{{ formatCount(navPositionBatchStatus.unavailableCount) }} 只</dd>
        </div>
        <div>
          <dt>失败</dt>
          <dd>{{ formatCount(navPositionBatchStatus.failedCount) }} 只</dd>
        </div>
        <div>
          <dt>当前游标</dt>
          <dd class="code">{{ navPositionBatchStatus.cursorValue || '--' }}</dd>
        </div>
        <div v-if="navPositionBatchStatus.configReleaseVersion != null">
          <dt>量化发布版本</dt>
          <dd>v{{ navPositionBatchStatus.configReleaseVersion }}</dd>
        </div>
        <div>
          <dt>开始时间</dt>
          <dd>{{ navPositionBatchStatus.startedAt || '--' }}</dd>
        </div>
        <div v-if="navPositionBatchStatus.finishedAt">
          <dt>结束时间</dt>
          <dd>{{ navPositionBatchStatus.finishedAt }}</dd>
        </div>
      </dl>

      <p v-if="navPositionBatchStatus.errorMessage" class="calculation-message">
        {{ navPositionBatchStatus.errorMessage }}
      </p>

      <div class="drawer-actions">
        <ElButton :loading="refreshing" @click="refreshStatus">
          <RotateCw class="mr-1 size-4" />刷新进度
        </ElButton>
      </div>
    </template>
    <ElEmpty v-else description="正在读取计算状态" />
  </ElDrawer>
</template>

<style scoped>
.calculation-overview {
  align-items: start;
  border-bottom: 1px solid #e2e8f0;
  display: flex;
  gap: 16px;
  justify-content: space-between;
  padding-bottom: 20px;
}

.calculation-overview h2 {
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

.calculation-progress {
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

.calculation-metrics {
  border-top: 1px solid #e2e8f0;
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  margin: 0;
}

.calculation-metrics > div {
  border-bottom: 1px solid #e2e8f0;
  padding: 16px 12px 16px 0;
}

.calculation-metrics > div:nth-child(odd) {
  padding-right: 16px;
}

.calculation-metrics dt {
  color: #64748b;
  font-size: 12px;
  margin-bottom: 6px;
}

.calculation-metrics dd {
  color: #1e293b;
  font-size: 14px;
  font-variant-numeric: tabular-nums;
  line-height: 1.45;
  margin: 0;
  overflow-wrap: anywhere;
}

.calculation-metrics .code {
  color: #0f766e;
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
}

.calculation-message {
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
  .calculation-metrics {
    grid-template-columns: 1fr;
  }

  .calculation-metrics > div,
  .calculation-metrics > div:nth-child(odd) {
    padding-right: 0;
  }
}
</style>
