<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { Refresh } from '@element-plus/icons-vue'
import { getReportShops, type ShopRankView } from '@/api/report'
import {
  defaultDateRange,
  errorMessage,
  formatCount,
  formatPercent,
} from '@/views/report/helpers'
import { formatYuan } from '@/utils/money'

const DEFAULT_DAYS = 30
const TOP_N_OPTIONS = [10, 20, 50]

const loading = ref(false)
const loadError = ref('')
const rows = ref<ShopRankView[]>([])

const filters = reactive<{
  dateRange: string[] | null
  topN: number
}>({
  dateRange: defaultDateRange(DEFAULT_DAYS),
  topN: 10,
})

function ratingText(value: number | null | undefined): string {
  if (value === null || value === undefined) {
    return '—'
  }
  const numeric = Number(value)
  return Number.isFinite(numeric) ? numeric.toFixed(2) : '—'
}

async function load(): Promise<void> {
  loading.value = true
  loadError.value = ''
  const range =
    Array.isArray(filters.dateRange) && filters.dateRange.length === 2
      ? filters.dateRange
      : defaultDateRange(DEFAULT_DAYS)
  filters.dateRange = range
  try {
    rows.value = await getReportShops({
      dateFrom: range[0],
      dateTo: range[1],
      topN: filters.topN,
    })
  } catch (error) {
    rows.value = []
    loadError.value = errorMessage(error, '门店排行加载失败')
  } finally {
    loading.value = false
  }
}

function reset(): void {
  filters.dateRange = defaultDateRange(DEFAULT_DAYS)
  filters.topN = 10
  void load()
}

onMounted(load)
</script>

<template>
  <div class="page">
    <div class="page-lead">
      <div>
        <h2>门店排行</h2>
        <p>
          数据源：<span class="mono">GET /api/v1/admin/report/shops</span>。按 GMV 倒序取 TopN，
          GMV 单位为分；取消率由后端按 <span class="mono">SUM(取消单) × 100 / COUNT(*)</span>
          计算，返回 0~100 的百分比数值。
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
        <el-select v-model="filters.topN" class="topn-select">
          <el-option
            v-for="option in TOP_N_OPTIONS"
            :key="option"
            :label="`Top ${option}`"
            :value="option"
          />
        </el-select>
        <el-button type="primary" @click="load">查询</el-button>
        <el-button @click="reset">重置</el-button>
      </div>

      <div class="section-head">
        <h3>门店明细</h3>
        <span class="muted">取消率超过 10% 标记为红色</span>
      </div>

      <el-table v-loading="loading" :data="rows" border stripe row-key="shopId">
        <el-table-column label="排名" width="76" align="center">
          <template #default="{ $index }">
            <span class="rank">{{ $index + 1 }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="shopName" label="门店名称" min-width="200" show-overflow-tooltip />
        <el-table-column label="商家 ID" width="100" align="right">
          <template #default="{ row }">{{ row.merchantId }}</template>
        </el-table-column>
        <el-table-column label="订单数" width="110" align="right">
          <template #default="{ row }">{{ formatCount(row.orderCount) }}</template>
        </el-table-column>
        <el-table-column label="GMV" min-width="140" align="right">
          <template #default="{ row }">
            <span class="amount">{{ formatYuan(row.gmv) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="评分" width="90" align="right">
          <template #default="{ row }">{{ ratingText(row.rating) }}</template>
        </el-table-column>
        <el-table-column label="平均出餐时长" width="140" align="right">
          <template #default="{ row }">{{ row.avgPrepMinutes }} 分钟</template>
        </el-table-column>
        <el-table-column label="取消率" width="130" align="center">
          <template #default="{ row }">
            <el-tag
              :type="row.cancelRate > 10 ? 'danger' : 'success'"
              effect="plain"
              size="small"
            >
              {{ formatPercent(row.cancelRate) }}
            </el-tag>
          </template>
        </el-table-column>

        <template #empty>
          <el-empty description="当前筛选条件下没有门店排行数据。" :image-size="80" />
        </template>
      </el-table>
    </el-card>
  </div>
</template>

<style scoped>
.filter-bar {
  margin-bottom: 14px;
}

.date-range {
  width: 280px;
}

.topn-select {
  width: 120px;
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

.rank {
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}

.amount {
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}

@media (max-width: 900px) {
  .date-range {
    width: 100%;
  }
}
</style>
