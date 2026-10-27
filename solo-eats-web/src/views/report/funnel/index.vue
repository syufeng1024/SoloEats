<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { Refresh } from '@element-plus/icons-vue'
import { getReportFunnel, type FunnelView } from '@/api/report'
import {
  defaultDateRange,
  errorMessage,
  formatCount,
  formatPercent,
  formatSharePercent,
  progressValue,
} from '@/views/report/helpers'

const DEFAULT_DAYS = 7

const EMPTY_FUNNEL: FunnelView = {
  exposeCount: 0,
  clickCount: 0,
  cartCount: 0,
  orderCount: 0,
  payCount: 0,
  clickRate: 0,
  cartRate: 0,
  orderRate: 0,
  payRate: 0,
}

const loading = ref(false)
const loadError = ref('')
const funnel = ref<FunnelView>({ ...EMPTY_FUNNEL })

const filters = reactive<{ dateRange: string[] | null }>({
  dateRange: defaultDateRange(DEFAULT_DAYS),
})

const stages = computed(() => [
  {
    key: 'expose',
    index: '01',
    label: '曝光',
    count: funnel.value.exposeCount,
    rate: null as number | null,
  },
  {
    key: 'click',
    index: '02',
    label: '点击',
    count: funnel.value.clickCount,
    rate: funnel.value.clickRate,
  },
  {
    key: 'cart',
    index: '03',
    label: '加购',
    count: funnel.value.cartCount,
    rate: funnel.value.cartRate,
  },
  {
    key: 'order',
    index: '04',
    label: '下单',
    count: funnel.value.orderCount,
    rate: funnel.value.orderRate,
  },
  {
    key: 'pay',
    index: '05',
    label: '支付',
    count: funnel.value.payCount,
    rate: funnel.value.payRate,
  },
])

const rows = computed(() =>
  stages.value.map((stage) => ({
    ...stage,
    rateText: stage.rate === null ? '—' : formatPercent(stage.rate),
    shareText: formatSharePercent(stage.count, funnel.value.exposeCount),
  })),
)

async function load(): Promise<void> {
  loading.value = true
  loadError.value = ''
  const range =
    Array.isArray(filters.dateRange) && filters.dateRange.length === 2
      ? filters.dateRange
      : defaultDateRange(DEFAULT_DAYS)
  filters.dateRange = range
  try {
    funnel.value = await getReportFunnel({ dateFrom: range[0], dateTo: range[1] })
  } catch (error) {
    funnel.value = { ...EMPTY_FUNNEL }
    loadError.value = errorMessage(error, '流量漏斗加载失败')
  } finally {
    loading.value = false
  }
}

function reset(): void {
  filters.dateRange = defaultDateRange(DEFAULT_DAYS)
  void load()
}

onMounted(load)
</script>

<template>
  <div class="page">
    <div class="page-lead">
      <div>
        <h2>流量漏斗</h2>
        <p>
          数据源：<span class="mono">GET /api/v1/admin/report/funnel</span>。五级计数来自
          <span class="mono">t_report_funnel_daily</span>；四级转化率由后端返回百分比数值，
          页面直接按 <span class="mono">10.00%</span> 展示。
        </p>
      </div>
      <div class="toolbar">
        <el-button :icon="Refresh" :loading="loading" @click="load">刷新</el-button>
      </div>
    </div>

    <el-alert v-if="loadError" type="error" :closable="false" show-icon :title="loadError" />
    <el-alert
      v-else-if="!loading && funnel.exposeCount === 0"
      type="info"
      :closable="false"
      show-icon
      title="所选区间没有漏斗数据，各阶段计数与转化率均为 0。"
    />

    <el-card shadow="never">
      <div class="toolbar filter-bar">
        <el-date-picker
          v-model="filters.dateRange"
          type="daterange"
          value-format="YYYY-MM-DD"
          start-placeholder="开始日期"
          end-placeholder="结束日期"
          unlink-panels
          class="date-range"
        />
        <el-button type="primary" @click="load">查询</el-button>
        <el-button @click="reset">重置</el-button>
      </div>
    </el-card>

    <div class="funnel-grid">
      <el-card v-for="stage in stages" :key="stage.key" shadow="never" class="stage-card">
        <div class="stage-head">
          <span class="stage-index mono">{{ stage.index }}</span>
          <span class="stage-label">{{ stage.label }}</span>
        </div>
        <div class="stage-count">
          {{ formatCount(stage.count) }}<span class="stage-unit">次</span>
        </div>
        <div class="stage-rate-row">
          <span class="muted">{{ stage.rate === null ? '漏斗入口' : '较上一级' }}</span>
          <span class="stage-rate">
            {{ stage.rate === null ? '—' : formatPercent(stage.rate) }}
          </span>
        </div>
        <el-progress
          :percentage="
            stage.rate === null
              ? stage.count > 0
                ? 100
                : 0
              : progressValue(stage.rate)
          "
          :stroke-width="8"
          :show-text="false"
        />
      </el-card>
    </div>

    <el-card shadow="never">
      <div class="section-head">
        <h3>阶段明细</h3>
        <span class="muted">转化率相对上一级；曝光阶段无上一级，记作「—」</span>
      </div>

      <el-table v-loading="loading" :data="rows" border stripe row-key="key">
        <el-table-column prop="label" label="阶段" min-width="120" />
        <el-table-column label="数量" min-width="140" align="right">
          <template #default="{ row }">{{ formatCount(row.count) }}</template>
        </el-table-column>
        <el-table-column label="相对上一级转化率" min-width="160" align="right">
          <template #default="{ row }">{{ row.rateText }}</template>
        </el-table-column>
        <el-table-column label="占曝光比例" min-width="140" align="right">
          <template #default="{ row }">{{ row.shareText }}</template>
        </el-table-column>

        <template #empty>
          <el-empty description="当前筛选条件下没有漏斗数据。" :image-size="80" />
        </template>
      </el-table>
    </el-card>
  </div>
</template>

<style scoped>
.filter-bar {
  margin-bottom: 0;
}

.date-range {
  width: 280px;
}

.funnel-grid {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 12px;
}

.stage-card {
  min-width: 0;
}

.stage-head {
  display: flex;
  align-items: center;
  gap: 8px;
}

.stage-index {
  color: var(--el-color-primary);
  font-weight: 600;
}

.stage-label {
  color: var(--app-muted);
  font-size: 13px;
}

.stage-count {
  margin-top: 14px;
  font-size: 28px;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
  line-height: 1.2;
}

.stage-unit {
  margin-left: 4px;
  color: var(--app-muted);
  font-size: 13px;
  font-weight: 400;
}

.stage-rate-row {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 8px;
  margin: 12px 0 8px;
  font-size: 12px;
}

.stage-rate {
  color: var(--app-ink);
  font-size: 14px;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}

.section-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
  margin-bottom: 14px;
}

.section-head h3 {
  font-size: 15px;
  font-weight: 600;
}

@media (max-width: 1200px) {
  .funnel-grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}

@media (max-width: 700px) {
  .funnel-grid {
    grid-template-columns: 1fr;
  }

  .date-range {
    width: 100%;
  }
}
</style>
