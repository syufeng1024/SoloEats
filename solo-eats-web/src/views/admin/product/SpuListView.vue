<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { Refresh, Search } from '@element-plus/icons-vue'
import { getCategoryTree, listSpus } from '@/api/product'
import type { CategoryNode, SpuView } from '@/api/types'
import {
  SPU_STATUS_MAP,
  SPU_STATUS_OPTIONS,
  statusMeta,
} from '@/constants/businessStatus'
import { formatDateTime } from '@/utils/datetime'
import { debounce } from '@/utils/debounce'
import { formatYuan } from '@/utils/money'

const PAGE_SIZE_OPTIONS = [10, 20, 50]

const loading = ref(false)
const loadError = ref('')
const spus = ref<SpuView[]>([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(10)
const appliedKeyword = ref('')
const categoryNodes = ref<CategoryNode[]>([])

const filters = reactive({
  categoryId: undefined as number | undefined,
  status: undefined as number | undefined,
  keyword: '',
})

interface FlatCategory {
  id: number
  label: string
}

const flatCategories = computed<FlatCategory[]>(() => {
  const result: FlatCategory[] = []
  for (const parent of categoryNodes.value) {
    result.push({ id: parent.id, label: parent.categoryName })
    for (const child of parent.children ?? []) {
      result.push({ id: child.id, label: `　${child.categoryName}` })
    }
  }
  return result
})

const categoryNames = computed<Record<number, string>>(() => {
  const map: Record<number, string> = {}
  for (const item of flatCategories.value) {
    map[item.id] = item.label.trim()
  }
  return map
})

watch(
  () => filters.keyword,
  debounce((value: string) => {
    appliedKeyword.value = value
    pageNum.value = 1
    void load()
  }, 300),
)

async function loadCategories(): Promise<void> {
  try {
    categoryNodes.value = await getCategoryTree()
  } catch {
    // 品类下拉失败不影响商品列表，拦截器已提示
  }
}

async function load(): Promise<void> {
  loading.value = true
  loadError.value = ''
  try {
    const result = await listSpus({
      categoryId: typeof filters.categoryId === 'number' ? filters.categoryId : undefined,
      status: typeof filters.status === 'number' ? filters.status : undefined,
      keyword: appliedKeyword.value.trim() || undefined,
      pageNum: pageNum.value,
      pageSize: pageSize.value,
    })
    spus.value = result.list
    total.value = result.total
  } catch (error) {
    loadError.value = error instanceof Error ? error.message : '商品库加载失败'
  } finally {
    loading.value = false
  }
}

function search(): void {
  pageNum.value = 1
  void load()
}

function reset(): void {
  filters.categoryId = undefined
  filters.status = undefined
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

function categoryName(categoryId: number | null): string {
  if (categoryId === null) {
    return '—'
  }
  return categoryNames.value[categoryId] ?? `品类 ${categoryId}`
}

onMounted(async () => {
  await Promise.all([loadCategories(), load()])
})
</script>

<template>
  <div class="page">
    <div class="page-lead">
      <div>
        <h2>商品库</h2>
        <p>
          数据源：<span class="mono">GET /api/v1/admin/spus</span>，权限码
          <span class="mono">product:spu:list</span>。本批次只提供列表与筛选；
          新建/编辑 SPU、门店商品上架属于后续批次页面。
        </p>
      </div>
      <el-button :icon="Refresh" :loading="loading" @click="load">刷新</el-button>
    </div>

    <el-alert v-if="loadError" type="error" :closable="false" show-icon :title="loadError" />

    <el-card shadow="never">
      <div class="toolbar filter-bar">
        <el-select
          v-model="filters.categoryId"
          placeholder="全部品类"
          clearable
          filterable
          class="category-select"
        >
          <el-option
            v-for="item in flatCategories"
            :key="item.id"
            :label="item.label"
            :value="item.id"
          />
        </el-select>
        <el-select v-model="filters.status" placeholder="全部状态" clearable class="status-select">
          <el-option
            v-for="option in SPU_STATUS_OPTIONS"
            :key="option.value"
            :label="option.label"
            :value="option.value"
          />
        </el-select>
        <el-input
          v-model="filters.keyword"
          class="keyword-input"
          placeholder="商品名称 / 副标题（300ms 防抖）"
          clearable
          :prefix-icon="Search"
        />
        <el-button type="primary" @click="search">查询</el-button>
        <el-button @click="reset">重置</el-button>
      </div>

      <el-table v-loading="loading" :data="spus" border row-key="id">
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column label="主图" width="72" align="center">
          <template #default="{ row }">
            <el-image
              v-if="row.mainImage"
              :src="row.mainImage"
              :preview-src-list="[row.mainImage]"
              fit="cover"
              preview-teleported
              class="thumb"
            />
            <span v-else class="muted">—</span>
          </template>
        </el-table-column>
        <el-table-column label="商品名称" min-width="190">
          <template #default="{ row }">
            <div class="name-cell">{{ row.spuName }}</div>
            <div class="muted name-sub">{{ row.subtitle || '—' }}</div>
          </template>
        </el-table-column>
        <el-table-column label="品类" min-width="120">
          <template #default="{ row }">{{ categoryName(row.categoryId) }}</template>
        </el-table-column>
        <el-table-column label="打包费" width="95" align="right">
          <template #default="{ row }">{{ formatYuan(row.packagingFee) }}</template>
        </el-table-column>
        <el-table-column label="起购 / 限购" width="105" align="right">
          <template #default="{ row }">{{ row.minPurchaseQty }} / {{ row.limitPerOrder }}</template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="statusMeta(SPU_STATUS_MAP, row.status).tag" size="small">
              {{ statusMeta(SPU_STATUS_MAP, row.status).label }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="标签" min-width="150">
          <template #default="{ row }">
            <div v-if="row.tags && row.tags.length" class="tag-cell">
              <el-tag
                v-for="tag in row.tags"
                :key="tag"
                size="small"
                effect="plain"
                type="info"
              >
                {{ tag }}
              </el-tag>
            </div>
            <span v-else class="muted">—</span>
          </template>
        </el-table-column>
        <el-table-column prop="monthSales" label="月售" width="70" align="right" />
        <el-table-column label="更新时间" width="150">
          <template #default="{ row }">{{ formatDateTime(row.updatedAt) }}</template>
        </el-table-column>

        <template #empty>
          <el-empty description="当前筛选条件下没有商品。" :image-size="80" />
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

.category-select {
  width: 180px;
}

.status-select {
  width: 140px;
}

.keyword-input {
  width: 300px;
}

.thumb {
  width: 40px;
  height: 40px;
  border-radius: 6px;
}

.name-cell {
  font-weight: 500;
}

.name-sub {
  font-size: 12px;
  margin-top: 2px;
}

.tag-cell {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
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
