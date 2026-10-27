<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { Refresh } from '@element-plus/icons-vue'
import {
  getSoldOutReasonStats,
  listSoldOutRecords,
  type ReasonStatView,
  type SoldOutListParams,
  type SoldOutRecordView,
} from '@/api/product-admin'
import { SOLD_OUT_REASON_MAX_LENGTH } from '@/constants/productStatus'
import { errorMessage } from '@/views/report/helpers'
import { formatDateTime } from '@/utils/datetime'

const PAGE_SIZE_OPTIONS = [10, 20, 50]

const loading = ref(false)
const loadError = ref('')
const records = ref<SoldOutRecordView[]>([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(20)

const statsLoading = ref(false)
const statsError = ref('')
const stats = ref<ReasonStatView[]>([])
/** 统计结果对应的门店 ID；null 表示尚未成功统计过。 */
const statsShopId = ref<number | null>(null)
const statsRangeText = ref('')

const filters = reactive({
  shopId: undefined as number | null | undefined,
  skuId: undefined as number | null | undefined,
  reason: '',
  dateRange: null as string[] | null,
})

const validShopId = computed(() => {
  const value = filters.shopId
  return typeof value === 'number' && Number.isInteger(value) && value > 0 ? value : null
})

const statsTotal = computed(() =>
  stats.value.reduce((sum, item) => sum + item.count, 0),
)

function positiveId(value: number | null | undefined): number | undefined {
  return typeof value === 'number' && Number.isInteger(value) && value > 0
    ? value
    : undefined
}

/** 日期选择器只给 yyyy-MM-dd，后端 @DateTimeFormat 要求 yyyy-MM-dd HH:mm:ss。 */
function dayAfter(date: string): string {
  const parts = date.split('-').map(Number)
  const next = new Date(Date.UTC(parts[0], parts[1] - 1, parts[2] + 1))
  const year = next.getUTCFullYear()
  const month = String(next.getUTCMonth() + 1).padStart(2, '0')
  const day = String(next.getUTCDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

/**
 * 后端列表与统计均使用 ge(start) / lt(end)，
 * 因此结束日期要取次日 00:00:00，才能包含 dateTo 当天的全部记录。
 */
function dateBounds(): { start?: string; end?: string } {
  const range = Array.isArray(filters.dateRange) ? filters.dateRange : []
  const start = range[0] ? `${range[0]} 00:00:00` : undefined
  const end = range[1] ? `${dayAfter(range[1])} 00:00:00` : undefined
  return { start, end }
}

async function load(): Promise<void> {
  loading.value = true
  loadError.value = ''
  try {
    const bounds = dateBounds()
    const params: SoldOutListParams = {
      shopId: positiveId(filters.shopId),
      skuId: positiveId(filters.skuId),
      reason: filters.reason.trim() || undefined,
      start: bounds.start,
      end: bounds.end,
      pageNum: pageNum.value,
      pageSize: pageSize.value,
    }
    const result = await listSoldOutRecords(params)
    records.value = result.list
    total.value = result.total
  } catch (error) {
    records.value = []
    total.value = 0
    loadError.value = errorMessage(error, '估清记录加载失败')
  } finally {
    loading.value = false
  }
}

async function loadStats(): Promise<void> {
  const shopId = validShopId.value
  if (shopId === null) {
    stats.value = []
    statsShopId.value = null
    statsRangeText.value = ''
    statsError.value = ''
    return
  }

  statsLoading.value = true
  statsError.value = ''
  try {
    const bounds = dateBounds()
    stats.value = await getSoldOutReasonStats({
      shopId,
      start: bounds.start,
      end: bounds.end,
    })
    statsShopId.value = shopId
    const range = Array.isArray(filters.dateRange) ? filters.dateRange : []
    statsRangeText.value =
      range[0] && range[1] ? `${range[0]} ~ ${range[1]}` : '最近 30 天（后端默认）'
  } catch (error) {
    stats.value = []
    statsShopId.value = null
    statsRangeText.value = ''
    statsError.value = errorMessage(error, '估清原因统计加载失败')
  } finally {
    statsLoading.value = false
  }
}

function search(): void {
  pageNum.value = 1
  void load()
  void loadStats()
}

function reset(): void {
  filters.shopId = undefined
  filters.skuId = undefined
  filters.reason = ''
  filters.dateRange = null
  pageNum.value = 1
  pageSize.value = 20
  stats.value = []
  statsShopId.value = null
  statsRangeText.value = ''
  statsError.value = ''
  void load()
}

function handlePageChange(page: number): void {
  pageNum.value = page
  void load()
}

function handleSizeChange(size: number): void {
  pageSize.value = size
  pageNum.value = 1
  void load()
}

function operatorText(row: SoldOutRecordView): string {
  if (row.operatorName) {
    return row.operatorName
  }
  return row.operatorId === null ? '—' : `#${row.operatorId}`
}

onMounted(load)
</script>

<template>
  <div class="page">
    <div class="page-lead">
      <div>
        <h2>估清管理</h2>
        <p>
          数据源：<span class="mono">GET /api/v1/admin/sold-out</span>，时间参数为
          <span class="mono">start</span> / <span class="mono">end</span>，格式
          <span class="mono">yyyy-MM-dd HH:mm:ss</span>。原因统计的
          <span class="mono">shopId</span> 必传；后端视图只返回 shopId / skuId / spuId，未联表商品名称。
        </p>
      </div>
      <div class="toolbar">
        <el-button :icon="Refresh" :loading="loading" @click="load">刷新列表</el-button>
        <el-button :icon="Refresh" :loading="statsLoading" @click="loadStats">刷新统计</el-button>
      </div>
    </div>

    <el-alert v-if="loadError" type="error" :closable="false" show-icon :title="loadError" />

    <el-card shadow="never" class="stats-card">
      <template #header>
        <div class="stats-header">
          <span>估清原因分布</span>
          <span class="muted sub">
            {{ statsShopId === null ? '尚未统计' : `门店 #${statsShopId} · ${statsRangeText}` }}
          </span>
        </div>
      </template>

      <el-alert
        v-if="statsError"
        type="error"
        :closable="false"
        show-icon
        :title="statsError"
      />
      <el-empty
        v-else-if="validShopId === null"
        description="请先在上方筛选条件中指定门店 ID"
        :image-size="60"
      />
      <el-empty
        v-else-if="statsLoading"
        description="统计加载中…"
        :image-size="60"
      />
      <div v-else-if="stats.length" class="stats-list">
        <span class="muted sub">合计 {{ statsTotal }} 条</span>
        <div v-for="stat in stats" :key="stat.reason" class="stats-item">
          <span class="stats-reason">{{ stat.reason }}</span>
          <el-tag type="warning" size="small">{{ stat.count }} 条</el-tag>
        </div>
      </div>
      <el-empty
        v-else
        description="该门店在所选时间范围内没有估清记录"
        :image-size="60"
      />
    </el-card>

    <el-card shadow="never">
      <div class="toolbar filter-bar">
        <el-input-number
          v-model="filters.shopId"
          :min="1"
          :precision="0"
          :controls="false"
          placeholder="门店 ID"
          class="id-input"
        />
        <el-input-number
          v-model="filters.skuId"
          :min="1"
          :precision="0"
          :controls="false"
          placeholder="SKU ID"
          class="id-input"
        />
        <el-input
          v-model="filters.reason"
          :maxlength="SOLD_OUT_REASON_MAX_LENGTH"
          placeholder="估清原因关键词"
          clearable
          class="reason-input"
          @keyup.enter="search"
        />
        <el-date-picker
          v-model="filters.dateRange"
          type="daterange"
          value-format="YYYY-MM-DD"
          start-placeholder="估清开始日期"
          end-placeholder="估清结束日期"
          class="date-range"
        />
        <el-button type="primary" @click="search">查询</el-button>
        <el-button @click="reset">重置</el-button>
      </div>

      <el-table v-loading="loading" :data="records" border stripe row-key="id">
        <el-table-column prop="id" label="记录 ID" width="90" />
        <el-table-column prop="shopId" label="门店 ID" width="95" />
        <el-table-column prop="skuId" label="SKU ID" width="95" />
        <el-table-column prop="spuId" label="SPU ID" width="95" />
        <el-table-column prop="reason" label="估清原因" min-width="180" show-overflow-tooltip />
        <el-table-column label="状态" width="95" align="center">
          <template #default="{ row }">
            <el-tag :type="row.restoredAt ? 'success' : 'danger'" size="small">
              {{ row.restoredAt ? '已恢复' : '仍估清' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="估清时间" width="160">
          <template #default="{ row }">{{ formatDateTime(row.soldOutAt) }}</template>
        </el-table-column>
        <el-table-column label="恢复时间" width="160">
          <template #default="{ row }">{{ formatDateTime(row.restoredAt) }}</template>
        </el-table-column>
        <el-table-column label="操作人" width="130">
          <template #default="{ row }">{{ operatorText(row) }}</template>
        </el-table-column>

        <template #empty>
          <el-empty description="当前筛选条件下没有估清记录。" :image-size="80" />
        </template>
      </el-table>

      <el-pagination
        class="pagination"
        layout="total, sizes, prev, pager, next"
        :total="total"
        :page-size="pageSize"
        :page-sizes="PAGE_SIZE_OPTIONS"
        :current-page="pageNum"
        @current-change="handlePageChange"
        @size-change="handleSizeChange"
      />
    </el-card>
  </div>
</template>

<style scoped>
.stats-card {
  margin-bottom: 14px;
}

.stats-header {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
}

.stats-list {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px 18px;
}

.stats-item {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.stats-reason {
  font-weight: 500;
}

.filter-bar {
  margin-bottom: 14px;
}

.id-input {
  width: 130px;
}

.reason-input {
  width: 190px;
}

.date-range {
  width: 280px;
}

.sub {
  font-size: 12px;
}

.pagination {
  margin-top: 16px;
  justify-content: flex-end;
}

@media (max-width: 1100px) {
  .date-range {
    width: 100%;
  }
}
</style>
