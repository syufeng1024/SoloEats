<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { Refresh } from '@element-plus/icons-vue'
import {
  getReportProducts,
  type ProductRankSortBy,
  type ProductRankView,
} from '@/api/report'
import {
  defaultDateRange,
  errorMessage,
  formatCount,
} from '@/views/report/helpers'
import { formatYuan } from '@/utils/money'

const DEFAULT_DAYS = 30
const PAGE_SIZE_OPTIONS = [10, 20, 50]

const loading = ref(false)
const loadError = ref('')
const rows = ref<ProductRankView[]>([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(20)

const filters = reactive<{
  dateRange: string[] | null
  sortBy: ProductRankSortBy
  categoryId: number | undefined
}>({
  dateRange: defaultDateRange(DEFAULT_DAYS),
  sortBy: 'saleAmount',
  categoryId: undefined,
})

function productRowKey(row: ProductRankView): string {
  return `${row.spuId ?? 'null'}-${row.skuId ?? 'null'}`
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
    const result = await getReportProducts({
      dateFrom: range[0],
      dateTo: range[1],
      sortBy: filters.sortBy,
      categoryId:
        typeof filters.categoryId === 'number' ? filters.categoryId : undefined,
      pageNum: pageNum.value,
      pageSize: pageSize.value,
    })
    rows.value = result.list
    total.value = result.total
  } catch (error) {
    rows.value = []
    total.value = 0
    loadError.value = errorMessage(error, '商品销售榜加载失败')
  } finally {
    loading.value = false
  }
}

function search(): void {
  pageNum.value = 1
  void load()
}

function handleSortChange(): void {
  pageNum.value = 1
  void load()
}

function reset(): void {
  filters.dateRange = defaultDateRange(DEFAULT_DAYS)
  filters.sortBy = 'saleAmount'
  filters.categoryId = undefined
  pageNum.value = 1
  pageSize.value = 20
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

onMounted(load)
</script>

<template>
  <div class="page">
    <div class="page-lead">
      <div>
        <h2>商品销售榜</h2>
        <p>
          数据源：<span class="mono">GET /api/v1/admin/report/products</span>。销售额为已扣退款的净销售额（分），
          按 SPU + SKU 聚合；切换排序方式由服务端重新排序。
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
        <el-radio-group v-model="filters.sortBy" @change="handleSortChange">
          <el-radio-button value="saleAmount">销售额</el-radio-button>
          <el-radio-button value="saleQty">销量</el-radio-button>
        </el-radio-group>
        <el-input-number
          v-model="filters.categoryId"
          placeholder="品类 ID"
          :min="1"
          :precision="0"
          :controls="false"
          class="category-input"
        />
        <el-button type="primary" @click="search">查询</el-button>
        <el-button @click="reset">重置</el-button>
      </div>

      <el-table
        v-loading="loading"
        :data="rows"
        :row-key="productRowKey"
        border
        stripe
      >
        <el-table-column label="排名" width="76" align="center">
          <template #default="{ $index }">
            <span class="rank">{{ (pageNum - 1) * pageSize + $index + 1 }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="spuName" label="商品名称" min-width="220" show-overflow-tooltip />
        <el-table-column label="SPU ID" width="100" align="right">
          <template #default="{ row }">
            <span class="mono">{{ row.spuId ?? '—' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="SKU ID" width="100" align="right">
          <template #default="{ row }">
            <span class="mono">{{ row.skuId ?? '—' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="品类 ID" width="100" align="right">
          <template #default="{ row }">
            <span class="mono">{{ row.categoryId ?? '—' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="销量" width="110" align="right">
          <template #default="{ row }">{{ formatCount(row.saleQty) }}</template>
        </el-table-column>
        <el-table-column label="销售额" min-width="140" align="right">
          <template #default="{ row }">
            <span class="amount">{{ formatYuan(row.saleAmount) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="退款数量" width="110" align="right">
          <template #default="{ row }">{{ formatCount(row.refundQty) }}</template>
        </el-table-column>

        <template #empty>
          <el-empty description="当前筛选条件下没有商品销售数据。" :image-size="80" />
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
.filter-bar {
  margin-bottom: 14px;
}

.date-range {
  width: 280px;
}

.category-input {
  width: 130px;
}

.rank {
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}

.amount {
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}

.pagination {
  margin-top: 16px;
  justify-content: flex-end;
}

@media (max-width: 900px) {
  .date-range {
    width: 100%;
  }
}
</style>
