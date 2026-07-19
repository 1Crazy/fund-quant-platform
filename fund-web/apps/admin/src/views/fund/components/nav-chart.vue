<script lang="ts" setup>
import type { EchartsUIType } from '@vben/plugins/echarts';

import { onMounted, ref, watch } from 'vue';

import { EchartsUI, useEcharts } from '@vben/plugins/echarts';

import type { FundApi } from '#/api/fund';

const props = defineProps<{
  points: FundApi.FundNavPoint[];
}>();

const chartRef = ref<EchartsUIType>();
const { renderEcharts } = useEcharts(chartRef);

function render() {
  const dates = props.points.map((point) => point.date);
  const unitNav = props.points.map((point) => point.unitNav);
  const accumulatedNav = props.points.map((point) => point.accumulatedNav ?? null);

  void renderEcharts({
    animationDuration: 650,
    color: ['#0f766e', '#d97706'],
    dataZoom: [
      { bottom: 8, end: 100, height: 18, start: Math.max(0, 100 - 12_000 / Math.max(dates.length, 1)), type: 'slider' },
      { type: 'inside' },
    ],
    grid: { bottom: 62, containLabel: true, left: 10, right: 18, top: 46 },
    legend: { right: 12, top: 4 },
    series: [
      {
        areaStyle: {
          color: {
            colorStops: [
              { color: 'rgba(15, 118, 110, 0.22)', offset: 0 },
              { color: 'rgba(15, 118, 110, 0.01)', offset: 1 },
            ],
            type: 'linear',
            x: 0,
            x2: 0,
            y: 0,
            y2: 1,
          },
        },
        data: unitNav,
        lineStyle: { width: 2.4 },
        name: '单位净值',
        showSymbol: false,
        smooth: 0.2,
        type: 'line',
      },
      {
        data: accumulatedNav,
        lineStyle: { type: 'dashed', width: 1.6 },
        name: '累计净值',
        showSymbol: false,
        smooth: 0.2,
        type: 'line',
      },
    ],
    tooltip: {
      axisPointer: { type: 'cross' },
      trigger: 'axis',
      valueFormatter: (value: unknown) =>
        typeof value === 'number' ? value.toFixed(4) : '--',
    },
    xAxis: {
      axisLine: { lineStyle: { color: '#94a3b8' } },
      boundaryGap: false,
      data: dates,
      type: 'category',
    },
    yAxis: {
      scale: true,
      splitLine: { lineStyle: { color: 'rgba(148, 163, 184, 0.18)' } },
      type: 'value',
    },
  });
}

onMounted(render);
watch(() => props.points, render, { deep: true });
</script>

<template>
  <div class="h-[380px] w-full">
    <EchartsUI ref="chartRef" />
  </div>
</template>
