<script setup lang="ts">
import { onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { Plus, Refresh, Search } from '@element-plus/icons-vue'
import { listShops } from '@/api/store'
import type { ShopView } from '@/api/types'
import {
  SHOP_STATUS_MAP,
  SHOP_STATUS_OPTIONS,
  deliveryTypesLabel,
  statusMeta,
} from '@/constants/businessStatus'
import { formatDateTime } from '@/utils/datetime'
import { debounce } from '@/utils/debounce'
import { formatYuan } from '@/utils/money'

const PAGE_SIZE_OPTIONS = [10, 20, 50]

const router = useRouter()

const loading = ref(false)
const loadError = ref('')
const shops = ref<ShopView[]>([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(10)
const appliedKeyword = ref('')

const filters = reactive({
  merchantId: undefined as number | undefined,
  status: undefined as number | undefined,
  city: '',
  keyword: '',
})

watch(
  () => filters.keyword,
  debounce((value: string) => {
    appliedKeyword.value = value
    pageNum.value = 1
    void load()
  }, 300),
)

async function load(): Promise<void> {
  loading.value = true
  loadError.value = ''
  try {
    const result = await listShops({
      merchantId: typeof filters.merchantId === 'number' ? filters.merchantId : undefined,
      status: typeof filters.status === 'number' ? filters.status : undefined,
      city: filters.city.trim() || undefined,
      keyword: appliedKeyword.value.trim() || undefined,
      pageNum: pageNum.value,
      pageSize: pageSize.value,
    })
    shops.value = result.list
    total.value = result.total
  } catch (error) {
    loadError.value = error instanceof Error ? error.message : '门店列表加载失败'
  } finally {
    loading.value = false
  }
}

function search(): void {
  pageNum.value = 1
  void load()
}

function reset(): void {
  filters.merchantId = undefined
  filters.status = undefined
  filters.city = ''
  filters.keyword = ''
  appliedKeyword.value = ''
  pageNum.value = 1
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

function openCreate(): void {
  void router.push('/store/shop/new')
}

function openEdit(row: ShopView): void {
  void router.push(`/store/shop/${row.id}/edit`)
}

onMounted(load)
</script>

<template>
  <div class="page">
    <div class="page-lead">
      <div>
        <h2>门店管理</h2>
        <p>
          数据源：<span class="mono">GET /api/v1/admin/shops</span>，权限码
          <span class="mono">store:shop:list</span>。金额字段单位为分，列表按元展示。
        </p>
      </div>
      <div class="toolbar">
        <el-button type="primary" :icon="Plus" @click="openCreate">新增门店</el-button>
        <el-button :icon="Refresh" :loading="loading" @click="load">刷新</el-button>
      </div>
    </div>

    <el-alert v-if="loadError" type="error" :closable="false" show-icon :title="loadError" />

    <el-card shadow="never">
      <div class="toolbar filter-bar">
        <el-input-number
          v-model="filters.merchantId"
          placeholder="商家 ID"
          :min="1"
          :precision="0"
          :controls="false"
          class="id-input"
        />
        <el-select v-model="filters.status" placeholder="全部状态" clearable class="status-select">
          <el-option
            v-for="option in SHOP_STATUS_OPTIONS"
            :key="option.value"
            :label="option.label"
            :value="option.value"
          />
        </el-select>
        <el-input v-model="filters.city" class="city-input" placeholder="城市" clearable />
        <el-input
          v-model="filters.keyword"
          class="keyword-input"
          placeholder="门店名称 / 编号 / 地址（300ms 防抖）"
          clearable
          :prefix-icon="Search"
        />
        <el-button type="primary" @click="search">查询</el-button>
        <el-button @click="reset">重置</el-button>
      </div>

      <el-table v-loading="loading" :data="shops" border row-key="id">
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="shopNo" label="门店编号" width="145">
          <template #default="{ row }">
            <span class="mono">{{ row.shopNo }}</span>
          </template>
        </el-table-column>
        <el-table-column label="门店名称" min-width="170">
          <template #default="{ row }">
            <div class="name-cell">{{ row.shopName }}</div>
            <div class="muted name-sub">商家 {{ row.merchantId }}</div>
          </template>
        </el-table-column>
        <el-table-column label="地区 / 地址" min-width="220">
          <template #default="{ row }">
            <div>{{ [row.city, row.district].filter(Boolean).join(' / ') || '—' }}</div>
            <div class="muted name-sub">{{ row.address }}</div>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="85">
          <template #default="{ row }">
            <el-tag :type="statusMeta(SHOP_STATUS_MAP, row.status).tag" size="small">
              {{ statusMeta(SHOP_STATUS_MAP, row.status).label }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="履约" width="100">
          <template #default="{ row }">{{ deliveryTypesLabel(row.deliveryTypes) }}</template>
        </el-table-column>
        <el-table-column label="起送 / 配送费" width="125" align="right">
          <template #default="{ row }">
            <div>{{ formatYuan(row.minOrderAmount) }}</div>
            <div class="muted name-sub">{{ formatYuan(row.deliveryFee) }}</div>
          </template>
        </el-table-column>
        <el-table-column label="打包费" width="90" align="right">
          <template #default="{ row }">{{ formatYuan(row.packagingFee) }}</template>
        </el-table-column>
        <el-table-column label="出餐" width="70" align="right">
          <template #default="{ row }">{{ row.prepMinutes }} 分</template>
        </el-table-column>
        <el-table-column label="评分 / 月售" width="110" align="right">
          <template #default="{ row }">
            <div>{{ Number(row.rating).toFixed(2) }}</div>
            <div class="muted name-sub">{{ row.monthSales }} 单</div>
          </template>
        </el-table-column>
        <el-table-column label="创建时间" width="150">
          <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="80" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
          </template>
        </el-table-column>

        <template #empty>
          <el-empty description="当前筛选条件下没有门店。" :image-size="80" />
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

.id-input {
  width: 120px;
}

.status-select {
  width: 140px;
}

.city-input {
  width: 130px;
}

.keyword-input {
  width: 320px;
}

.name-cell {
  font-weight: 500;
}

.name-sub {
  font-size: 12px;
  margin-top: 2px;
}

.pagination {
  margin-top: 16px;
  justify-content: flex-end;
}

@media (max-width: 900px) {
  .keyword-input {
    width: 100%;
  }
}
</style>
