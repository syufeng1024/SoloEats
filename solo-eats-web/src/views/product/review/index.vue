<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import {
  auditReview,
  listReviews,
  type ReviewListParams,
  type ReviewView,
} from '@/api/product-admin'
import {
  REVIEW_SCORE_LEVEL_OPTIONS,
  REVIEW_STATUS,
  REVIEW_STATUS_OPTIONS,
  labelOf,
  tagOf,
} from '@/constants/productStatus'
import { errorMessage } from '@/views/report/helpers'
import { formatDateTime } from '@/utils/datetime'

const PAGE_SIZE_OPTIONS = [10, 20, 50]

const loading = ref(false)
const loadError = ref('')
const reviews = ref<ReviewView[]>([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(20)

const filters = reactive({
  shopId: undefined as number | null | undefined,
  spuId: undefined as number | null | undefined,
  status: undefined as number | undefined,
  scoreLevel: undefined as number | undefined,
})

function positiveId(value: number | null | undefined): number | undefined {
  return typeof value === 'number' && Number.isInteger(value) && value > 0
    ? value
    : undefined
}

async function load(): Promise<void> {
  loading.value = true
  loadError.value = ''
  try {
    const params: ReviewListParams = {
      shopId: positiveId(filters.shopId),
      spuId: positiveId(filters.spuId),
      status: typeof filters.status === 'number' ? filters.status : undefined,
      scoreLevel:
        typeof filters.scoreLevel === 'number' ? filters.scoreLevel : undefined,
      pageNum: pageNum.value,
      pageSize: pageSize.value,
    }
    const result = await listReviews(params)
    reviews.value = result.list
    total.value = result.total
  } catch (error) {
    reviews.value = []
    total.value = 0
    loadError.value = errorMessage(error, '评价列表加载失败')
  } finally {
    loading.value = false
  }
}

function search(): void {
  pageNum.value = 1
  void load()
}

function reset(): void {
  filters.shopId = undefined
  filters.spuId = undefined
  filters.status = undefined
  filters.scoreLevel = undefined
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

function userText(row: ReviewView): string {
  if (row.anonymous === 1 || row.userId === null) {
    return '匿名'
  }
  return `#${row.userId}`
}

async function setStatus(row: ReviewView, status: number): Promise<void> {
  try {
    await auditReview(row.id, { status })
    ElMessage.success(status === 1 ? '评价已通过' : '评价已隐藏')
    await load()
  } catch {
    // 错误提示由 http 响应拦截器统一输出，此处不重复弹
  }
}

async function confirmHide(row: ReviewView): Promise<void> {
  try {
    await ElMessageBox.confirm(
      `确认隐藏评价 #${row.id}？隐藏后用户端不再展示。`,
      '隐藏评价',
      { confirmButtonText: '确认隐藏', cancelButtonText: '取消', type: 'warning' },
    )
  } catch {
    return
  }
  await setStatus(row, 2)
}

onMounted(load)
</script>

<template>
  <div class="page">
    <div class="page-lead">
      <div>
        <h2>商品评价</h2>
        <p>
          数据源：<span class="mono">GET /api/v1/admin/reviews</span>，审核
          <span class="mono">PUT /api/v1/admin/reviews/{id}/status</span>，请求体
          <span class="mono">&#123; status &#125;</span>。匿名评价后端返回
          <span class="mono">userId=null</span>；视图只含 shopId / spuId / skuId，未联表名称。
        </p>
      </div>
      <div class="toolbar">
        <el-button :icon="Refresh" :loading="loading" @click="load">刷新</el-button>
      </div>
    </div>

    <el-alert v-if="loadError" type="error" :closable="false" show-icon :title="loadError" />

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
          v-model="filters.spuId"
          :min="1"
          :precision="0"
          :controls="false"
          placeholder="SPU ID"
          class="id-input"
        />
        <el-select v-model="filters.status" placeholder="全部状态" clearable class="status-select">
          <el-option
            v-for="option in REVIEW_STATUS_OPTIONS"
            :key="option.value"
            :value="option.value"
            :label="option.label"
          />
        </el-select>
        <el-select
          v-model="filters.scoreLevel"
          placeholder="全部评分档位"
          clearable
          class="score-select"
        >
          <el-option
            v-for="option in REVIEW_SCORE_LEVEL_OPTIONS"
            :key="option.value"
            :value="option.value"
            :label="option.label"
          />
        </el-select>
        <el-button type="primary" @click="search">查询</el-button>
        <el-button @click="reset">重置</el-button>
      </div>

      <el-table v-loading="loading" :data="reviews" border stripe row-key="id">
        <el-table-column prop="id" label="评价 ID" width="90" />
        <el-table-column label="用户" width="90">
          <template #default="{ row }">{{ userText(row) }}</template>
        </el-table-column>
        <el-table-column label="门店 / 商品" width="180">
          <template #default="{ row }">
            <div>门店 #{{ row.shopId }}</div>
            <div class="muted sub">SPU #{{ row.spuId }} · SKU #{{ row.skuId }}</div>
          </template>
        </el-table-column>
        <el-table-column label="综合评分" width="150">
          <template #default="{ row }">
            <el-rate :model-value="row.overallScore" disabled size="small" />
          </template>
        </el-table-column>
        <el-table-column label="评价内容" min-width="260">
          <template #default="{ row }">
            <div class="content-text">{{ row.content || '—' }}</div>
            <div v-if="row.appendContent" class="append-text">
              追评：{{ row.appendContent }}
            </div>
            <div v-if="row.tags.length" class="tag-line">
              <el-tag
                v-for="tag in row.tags"
                :key="tag"
                size="small"
                type="info"
                class="tag-item"
              >
                {{ tag }}
              </el-tag>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="图片数" width="80" align="right">
          <template #default="{ row }">{{ row.images.length }}</template>
        </el-table-column>
        <el-table-column label="商家回复" min-width="180">
          <template #default="{ row }">
            <div>{{ row.merchantReply || '—' }}</div>
            <div v-if="row.replyAt" class="muted sub">{{ formatDateTime(row.replyAt) }}</div>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="tagOf(REVIEW_STATUS, row.status)" size="small">
              {{ labelOf(REVIEW_STATUS, row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="评价时间" width="160">
          <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="130" fixed="right">
          <template #default="{ row }">
            <el-button
              v-if="row.status !== 1"
              link
              type="primary"
              @click="setStatus(row, 1)"
            >
              通过
            </el-button>
            <el-button
              v-if="row.status !== 2"
              link
              type="danger"
              @click="confirmHide(row)"
            >
              隐藏
            </el-button>
          </template>
        </el-table-column>

        <template #empty>
          <el-empty description="当前筛选条件下没有评价。" :image-size="80" />
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
  width: 130px;
}

.status-select {
  width: 130px;
}

.score-select {
  width: 160px;
}

.content-text {
  white-space: pre-wrap;
  word-break: break-word;
}

.append-text {
  color: var(--el-text-color-secondary);
  margin-top: 6px;
  white-space: pre-wrap;
  word-break: break-word;
}

.tag-line {
  margin-top: 6px;
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}

.tag-item {
  margin: 0;
}

.sub {
  font-size: 12px;
  margin-top: 2px;
}

.pagination {
  margin-top: 16px;
  justify-content: flex-end;
}
</style>
