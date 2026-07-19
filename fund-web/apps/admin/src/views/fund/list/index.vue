<script lang="ts" setup>
import { computed, onMounted } from 'vue';
import { useRouter } from 'vue-router';

import { Page } from '@vben/common-ui';
import { RotateCw, Search } from '@vben/icons';

import {
  ElButton,
  ElCard,
  ElEmpty,
  ElInput,
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

const router = useRouter();
const fundStore = useFundStore();
const { list, listLoading, query, total } = storeToRefs(fundStore);

const rangeSummary = computed(() => {
  if (!total.value) return '暂无基金数据';
  const start = (query.value.pageNum - 1) * query.value.pageSize + 1;
  const end = Math.min(query.value.pageNum * query.value.pageSize, total.value);
  return `显示 ${start}-${end}，共 ${total.value} 只基金`;
});

function formatNav(value?: number) {
  return value == null ? '--' : value.toFixed(4);
}

function growthClass(value?: number) {
  if (value == null || value === 0) return 'text-slate-500';
  return value > 0 ? 'text-rose-600' : 'text-emerald-600';
}

function openDetail(row: FundApi.FundListItem) {
  router.push({ path: '/fund/detail', query: { code: row.fundCode } });
}

async function search() {
  await fundStore.fetchList(true);
}

async function reset() {
  fundStore.resetQuery();
  await fundStore.fetchList();
}

function changePage() {
  void fundStore.fetchList();
}

function changePageSize() {
  void fundStore.fetchList(true);
}

onMounted(() => fundStore.fetchList());
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
        <div class="market-stat">
          <span class="live-dot" aria-hidden="true"></span>
          <span>{{ rangeSummary }}</span>
        </div>
      </section>

      <ElCard class="filter-panel" shadow="never">
        <div class="grid gap-3 md:grid-cols-[180px_1fr_180px_auto]">
          <ElInput
            v-model="query.fundCode"
            clearable
            maxlength="12"
            placeholder="基金代码"
            @keyup.enter="search"
          />
          <ElInput
            v-model="query.fundName"
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
          <div class="flex gap-2">
            <ElButton type="primary" @click="search">
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
              <button class="fund-code" type="button" @click.stop="openDetail(row)">
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
          <ElTableColumn align="right" label="最新净值" min-width="120">
            <template #default="{ row }">{{ formatNav(row.latestNav) }}</template>
          </ElTableColumn>
          <ElTableColumn align="center" label="净值日期" min-width="120" prop="navDate" />
          <ElTableColumn align="right" label="盘中估值" min-width="120">
            <template #default="{ row }">{{ formatNav(row.estimateNav) }}</template>
          </ElTableColumn>
          <ElTableColumn align="right" label="估算涨跌" min-width="120">
            <template #default="{ row }">
              <span class="font-semibold tabular-nums" :class="growthClass(row.estimateGrowthRate)">
                {{ row.estimateGrowthRate == null ? '--' : `${row.estimateGrowthRate > 0 ? '+' : ''}${row.estimateGrowthRate.toFixed(2)}%` }}
              </span>
            </template>
          </ElTableColumn>
          <ElTableColumn label="估值时间" min-width="190" prop="estimateTime">
            <template #default="{ row }">
              <div class="flex items-center gap-2">
                <span>{{ row.estimateTime || '--' }}</span>
                <ElTag v-if="row.isStale" size="small" type="warning">已过期</ElTag>
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
