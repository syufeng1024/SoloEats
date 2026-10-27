<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { Refresh } from '@element-plus/icons-vue'
import { getReportUsers, type UserReportView } from '@/api/report'
import {
  defaultDateRange,
  errorMessage,
  formatCount,
  formatPercent,
  progressValue,
} from '@/views/report/helpers'

const DEFAULT_DAYS = 30

const EMPTY_USER_REPORT: UserReportView = {
  newUserCount: 0,
  activeUserCount: 0,
  repurchaseUserCount: 0,
  repurchaseRate: 0,
}

const loading = ref(false)
const loadError = ref('')
const report = ref<UserReportView>({ ...EMPTY_USER_REPORT })

const filters = reactive<{ dateRange: string[] | null }>({
  dateRange: defaultDateRange(DEFAULT_DAYS),
})

const userCards = computed(() => [
  { key: 'newUserCount', label: '新增用户', value: report.value.newUserCount },
  { key: 'activeUserCount', label: '活跃用户', value: report.value.activeUserCount },
  { key: 'repurchaseUserCount', label: '复购用户', value: report.value.repurchaseUserCount },
])

const metricRows = computed(() => [
  {
    key: 'newUserCount',
    label: '新增用户',
    value: `${formatCount(report.value.newUserCount)} 人`,
    description: '区间内在 t_member_user 首次注册（created_at 落入区间）的用户数。',
  },
  {
    key: 'activeUserCount',
    label: '活跃用户',
    value: `${formatCount(report.value.activeUserCount)} 人`,
    description: '区间内已完成订单（status = 60）的去重下单用户数。',
  },
  {
    key: 'repurchaseUserCount',
    label: '复购用户',
    value: `${formatCount(report.value.repurchaseUserCount)} 人`,
    description: '区间内已完成订单数 ≥ 2 的去重用户数。',
  },
  {
    key: 'repurchaseRate',
    label: '复购率',
    value: formatPercent(report.value.repurchaseRate),
    description: '复购用户数 ÷ 活跃用户数；后端返回百分比数值（10.00 表示 10.00%）。',
  },
])

async function load(): Promise<void> {
  loading.value = true
  loadError.value = ''
  const range =
    Array.isArray(filters.dateRange) && filters.dateRange.length === 2
      ? filters.dateRange
      : defaultDateRange(DEFAULT_DAYS)
  filters.dateRange = range
  try {
    report.value = await getReportUsers({ dateFrom: range[0], dateTo: range[1] })
  } catch (error) {
    report.value = { ...EMPTY_USER_REPORT }
    loadError.value = errorMessage(error, '用户报表加载失败')
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
        <h2>用户报表</h2>
        <p>
          数据源：<span class="mono">GET /api/v1/admin/report/users</span>。新增用户按注册时间统计，
          活跃与复购按已完成订单去重统计；复购率为百分比数值。
        </p>
      </div>
      <div class="toolbar">
        <el-button :icon="Refresh" :loading="loading" @click="load">刷新</el-button>
      </div>
    </div>

    <el-alert v-if="loadError" type="error" :closable="false" show-icon :title="loadError" />

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

    <el-row :gutter="12" v-loading="loading">
      <el-col
        v-for="card in userCards"
        :key="card.key"
        :xs="12"
        :sm="8"
        :md="6"
        :lg="6"
      >
        <el-card shadow="never" class="metric-card">
          <div class="summary-label">{{ card.label }}</div>
          <div class="summary-value">
            {{ formatCount(card.value) }}<span class="summary-unit">人</span>
          </div>
        </el-card>
      </el-col>

      <el-col :xs="12" :sm="8" :md="6" :lg="6">
        <el-card shadow="never" class="metric-card">
          <div class="summary-label">复购率</div>
          <div class="summary-value">{{ formatPercent(report.repurchaseRate) }}</div>
          <el-progress
            class="rate-progress"
            :percentage="progressValue(report.repurchaseRate)"
            :stroke-width="8"
            :format="() => formatPercent(report.repurchaseRate)"
          />
          <div class="muted rate-note">复购用户 ÷ 活跃用户</div>
        </el-card>
      </el-col>
    </el-row>

    <el-card shadow="never">
      <div class="section-head">
        <h3>指标明细</h3>
        <span class="muted">统计区间：{{ filters.dateRange?.[0] }} ~ {{ filters.dateRange?.[1] }}</span>
      </div>

      <el-table v-loading="loading" :data="metricRows" border stripe row-key="key">
        <el-table-column prop="label" label="指标" width="140" />
        <el-table-column label="数值" min-width="160" align="right">
          <template #default="{ row }">
            <span class="metric-value">{{ row.value }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="description" label="口径说明" min-width="380" />

        <template #empty>
          <el-empty description="当前筛选条件下没有用户数据。" :image-size="80" />
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

.metric-card {
  margin-bottom: 12px;
}

.summary-label {
  color: var(--app-muted);
  font-size: 12px;
}

.summary-value {
  margin-top: 8px;
  font-size: 28px;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
  line-height: 1.2;
}

.summary-unit {
  margin-left: 4px;
  color: var(--app-muted);
  font-size: 13px;
  font-weight: 400;
}

.rate-progress {
  margin-top: 12px;
}

.rate-note {
  margin-top: 6px;
  font-size: 12px;
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

.metric-value {
  font-variant-numeric: tabular-nums;
  font-weight: 600;
}

@media (max-width: 900px) {
  .date-range {
    width: 100%;
  }
}
</style>
