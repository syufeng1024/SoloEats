<script setup lang="ts">
import { onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { Refresh, Search } from '@element-plus/icons-vue'
import { listMerchants } from '@/api/merchant'
import type { MerchantView } from '@/api/types'
import {
  MERCHANT_STATUS_OPTIONS,
  MERCHANT_STATUS_MAP,
  statusMeta,
} from '@/constants/businessStatus'
import { formatDateTime } from '@/utils/datetime'
import { debounce } from '@/utils/debounce'

const PAGE_SIZE_OPTIONS = [10, 20, 50]

const router = useRouter()

const loading = ref(false)
const loadError = ref('')
const merchants = ref<MerchantView[]>([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(10)
const appliedKeyword = ref('')

const filters = reactive({
  status: 1 as number | undefined,
  keyword: '',
  categoryId: undefined as number | undefined,
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
    const result = await listMerchants({
      status: typeof filters.status === 'number' ? filters.status : undefined,
      keyword: appliedKeyword.value.trim() || undefined,
      categoryId: typeof filters.categoryId === 'number' ? filters.categoryId : undefined,
      pageNum: pageNum.value,
      pageSize: pageSize.value,
    })
    merchants.value = result.list
    total.value = result.total
  } catch (error) {
    loadError.value = error instanceof Error ? error.message : '商家列表加载失败'
  } finally {
    loading.value = false
  }
}

function search(): void {
  pageNum.value = 1
  void load()
}

function reset(): void {
  filters.status = 1
  filters.keyword = ''
  filters.categoryId = undefined
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

function openAudit(row: MerchantView): void {
  void router.push(`/merchant/apply/${row.id}`)
}

onMounted(load)
</script>

<template>
  <div class="page">
    <div class="page-lead">
      <div>
        <h2>商家入驻审核</h2>
        <p>
          数据源：<span class="mono">GET /api/v1/admin/merchants</span>，权限码
          <span class="mono">merchant:list</span> / <span class="mono">merchant:audit</span>。
          默认只列 <span class="mono">status=1</span> 待审核；草稿与已驳回可在状态下拉中切换查看。
        </p>
      </div>
      <el-button :icon="Refresh" :loading="loading" @click="load">刷新</el-button>
    </div>

    <el-alert v-if="loadError" type="error" :closable="false" show-icon :title="loadError" />

    <el-card shadow="never">
      <div class="toolbar filter-bar">
        <el-select v-model="filters.status" placeholder="全部状态" clearable class="status-select">
          <el-option
            v-for="option in MERCHANT_STATUS_OPTIONS"
            :key="option.value"
            :label="option.label"
            :value="option.value"
          />
        </el-select>
        <el-input-number
          v-model="filters.categoryId"
          placeholder="平台品类 ID"
          :min="1"
          :precision="0"
          :controls="false"
          class="id-input"
        />
        <el-input
          v-model="filters.keyword"
          class="keyword-input"
          placeholder="商家名称 / 编号 / 联系人 / 手机号（300ms 防抖）"
          clearable
          :prefix-icon="Search"
        />
        <el-button type="primary" @click="search">查询</el-button>
        <el-button @click="reset">重置</el-button>
      </div>

      <el-table v-loading="loading" :data="merchants" border row-key="id">
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="merchantNo" label="商家编号" width="145">
          <template #default="{ row }">
            <span class="mono">{{ row.merchantNo }}</span>
          </template>
        </el-table-column>
        <el-table-column label="商家 / 品牌" min-width="180">
          <template #default="{ row }">
            <div class="name-cell">{{ row.merchantName }}</div>
            <div class="muted name-sub">{{ row.brandName || '—' }}</div>
          </template>
        </el-table-column>
        <el-table-column label="联系人" min-width="130">
          <template #default="{ row }">
            <div>{{ row.contactName }}</div>
            <div class="mono muted">{{ row.contactPhone }}</div>
          </template>
        </el-table-column>
        <el-table-column label="地区" min-width="140">
          <template #default="{ row }">
            {{ [row.province, row.city, row.district].filter(Boolean).join(' / ') || '—' }}
          </template>
        </el-table-column>
        <el-table-column label="状态" width="95">
          <template #default="{ row }">
            <el-tag :type="statusMeta(MERCHANT_STATUS_MAP, row.status).tag" size="small">
              {{ statusMeta(MERCHANT_STATUS_MAP, row.status).label }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="抽佣比例" width="100" align="right">
          <template #default="{ row }">
            {{ row.commissionRate === null ? '—' : `${(row.commissionRate * 100).toFixed(2)}%` }}
          </template>
        </el-table-column>
        <el-table-column label="提交时间" width="150">
          <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="90" fixed="right">
          <template #default="{ row }">
            <el-button
              link
              type="primary"
              @click="openAudit(row)"
            >
              {{ row.status === 1 ? '审核' : '详情' }}
            </el-button>
          </template>
        </el-table-column>

        <template #empty>
          <el-empty description="当前筛选条件下没有入驻申请。" :image-size="80" />
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

.status-select {
  width: 140px;
}

.id-input {
  width: 130px;
}

.keyword-input {
  width: 340px;
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
