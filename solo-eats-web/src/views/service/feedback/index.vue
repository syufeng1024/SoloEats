<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import {
  listFeedbacks,
  replyFeedback,
  type FeedbackView,
} from '@/api/service'
import {
  FEEDBACK_STATUS,
  FEEDBACK_STATUS_OPTIONS,
  labelOf,
  tagOf,
} from '@/constants/serviceStatus'
import { errorMessage } from '@/views/report/helpers'
import { formatDateTime } from '@/utils/datetime'

const PAGE_SIZE_OPTIONS = [10, 20, 50]

const loading = ref(false)
const loadError = ref('')
const feedbacks = ref<FeedbackView[]>([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(20)

const filters = reactive<{ status: number | undefined }>({
  status: undefined,
})

const replyVisible = ref(false)
const replyTarget = ref<FeedbackView | null>(null)
const replyText = ref('')
const replying = ref(false)

async function load(): Promise<void> {
  loading.value = true
  loadError.value = ''
  try {
    const result = await listFeedbacks({
      status: typeof filters.status === 'number' ? filters.status : undefined,
      pageNum: pageNum.value,
      pageSize: pageSize.value,
    })
    feedbacks.value = result.list
    total.value = result.total
  } catch (error) {
    feedbacks.value = []
    total.value = 0
    loadError.value = errorMessage(error, '意见反馈加载失败')
  } finally {
    loading.value = false
  }
}

function search(): void {
  pageNum.value = 1
  void load()
}

function reset(): void {
  filters.status = undefined
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

function openReply(row: FeedbackView): void {
  replyTarget.value = row
  replyText.value = ''
  replyVisible.value = true
}

async function submitReply(): Promise<void> {
  const target = replyTarget.value
  const reply = replyText.value.trim()
  if (!target) {
    return
  }
  if (!reply) {
    ElMessage.warning('回复内容不能为空')
    return
  }

  replying.value = true
  try {
    await replyFeedback(target.id, { reply })
    ElMessage.success('反馈已回复')
    replyVisible.value = false
    await load()
  } catch {
    // 错误提示由 http 响应拦截器统一输出，此处不重复弹
  } finally {
    replying.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="page">
    <div class="page-lead">
      <div>
        <h2>意见反馈</h2>
        <p>
          数据源：<span class="mono">GET /api/v1/admin/feedbacks</span> 与
          <span class="mono">PUT /feedbacks/{id}/reply</span>。每条反馈只允许回复一次，
          已回复记录不再展示回复按钮。
        </p>
      </div>
      <div class="toolbar">
        <el-button :icon="Refresh" :loading="loading" @click="load">刷新</el-button>
      </div>
    </div>

    <el-alert v-if="loadError" type="error" :closable="false" show-icon :title="loadError" />

    <el-card shadow="never">
      <div class="toolbar filter-bar">
        <el-select
          v-model="filters.status"
          placeholder="全部状态"
          clearable
          class="status-select"
        >
          <el-option
            v-for="option in FEEDBACK_STATUS_OPTIONS"
            :key="option.value"
            :label="option.label"
            :value="option.value"
          />
        </el-select>
        <el-button type="primary" @click="search">查询</el-button>
        <el-button @click="reset">重置</el-button>
      </div>

      <el-table v-loading="loading" :data="feedbacks" border stripe row-key="id">
        <el-table-column label="用户 ID" width="100" align="right">
          <template #default="{ row }">{{ row.userId }}</template>
        </el-table-column>
        <el-table-column prop="content" label="内容" min-width="260" show-overflow-tooltip />
        <el-table-column label="图片数" width="90" align="center">
          <template #default="{ row }">{{ row.images?.length ?? 0 }}</template>
        </el-table-column>
        <el-table-column label="联系方式" width="150" show-overflow-tooltip>
          <template #default="{ row }">{{ row.contact || '—' }}</template>
        </el-table-column>
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="tagOf(FEEDBACK_STATUS, row.status)" size="small">
              {{ labelOf(FEEDBACK_STATUS, row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="回复内容" min-width="240" show-overflow-tooltip>
          <template #default="{ row }">{{ row.reply || '—' }}</template>
        </el-table-column>
        <el-table-column label="回复时间" width="150">
          <template #default="{ row }">{{ formatDateTime(row.replyAt) }}</template>
        </el-table-column>
        <el-table-column label="提交时间" width="150">
          <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="86" fixed="right">
          <template #default="{ row }">
            <el-button
              v-if="row.status === 0"
              link
              type="primary"
              @click="openReply(row)"
            >
              回复
            </el-button>
            <span v-else class="muted">—</span>
          </template>
        </el-table-column>

        <template #empty>
          <el-empty description="当前筛选条件下没有意见反馈。" :image-size="80" />
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

    <el-dialog
      v-model="replyVisible"
      title="回复意见反馈"
      width="560px"
      :close-on-click-modal="false"
    >
      <div v-if="replyTarget" class="feedback-quote">
        <div class="feedback-quote-label">用户反馈</div>
        <div class="pre-wrap">{{ replyTarget.content }}</div>
      </div>
      <el-form label-width="80px">
        <el-form-item label="回复内容" required>
          <el-input
            v-model="replyText"
            type="textarea"
            :rows="5"
            maxlength="1000"
            show-word-limit
            placeholder="回复内容将展示给提交反馈的用户"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="replyVisible = false">取消</el-button>
        <el-button type="primary" :loading="replying" @click="submitReply">
          提交回复
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.filter-bar {
  margin-bottom: 14px;
}

.status-select {
  width: 150px;
}

.pagination {
  margin-top: 16px;
  justify-content: flex-end;
}

.feedback-quote {
  margin-bottom: 16px;
  padding: 12px 14px;
  background: var(--app-bg);
  border: 1px solid var(--app-border);
  border-radius: 6px;
  font-size: 13px;
}

.feedback-quote-label {
  margin-bottom: 6px;
  color: var(--app-muted);
  font-size: 12px;
}

.pre-wrap {
  white-space: pre-wrap;
  word-break: break-word;
  line-height: 1.6;
}
</style>
