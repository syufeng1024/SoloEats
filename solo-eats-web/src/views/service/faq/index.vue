<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Refresh } from '@element-plus/icons-vue'
import {
  createFaq,
  deleteFaq,
  listFaqs,
  updateFaq,
  type FaqCommandPayload,
  type FaqView,
} from '@/api/service'
import { errorMessage, formatCount } from '@/views/report/helpers'
import { formatDateTime } from '@/utils/datetime'

const PAGE_SIZE_OPTIONS = [10, 20, 50]

const loading = ref(false)
const loadError = ref('')
const faqs = ref<FaqView[]>([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(20)

const filters = reactive({
  category: '',
  keyword: '',
})

const dialogVisible = ref(false)
const editingId = ref<number | null>(null)
const submitting = ref(false)

const form = reactive({
  category: '',
  question: '',
  answer: '',
  sort: 0,
  status: 0,
})

function faqStatusLabel(status: number): string {
  return status === 0 ? '启用' : '停用'
}

function faqStatusType(status: number): 'success' | 'info' {
  return status === 0 ? 'success' : 'info'
}

async function load(): Promise<void> {
  loading.value = true
  loadError.value = ''
  try {
    const result = await listFaqs({
      category: filters.category.trim() || undefined,
      keyword: filters.keyword.trim() || undefined,
      pageNum: pageNum.value,
      pageSize: pageSize.value,
    })
    faqs.value = result.list
    total.value = result.total
  } catch (error) {
    faqs.value = []
    total.value = 0
    loadError.value = errorMessage(error, 'FAQ 列表加载失败')
  } finally {
    loading.value = false
  }
}

function search(): void {
  pageNum.value = 1
  void load()
}

function reset(): void {
  filters.category = ''
  filters.keyword = ''
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

function openCreate(): void {
  editingId.value = null
  Object.assign(form, {
    category: '',
    question: '',
    answer: '',
    sort: 0,
    status: 0,
  })
  dialogVisible.value = true
}

function openEdit(row: FaqView): void {
  editingId.value = row.id
  Object.assign(form, {
    category: row.category,
    question: row.question,
    answer: row.answer,
    sort: row.sort,
    status: row.status,
  })
  dialogVisible.value = true
}

async function submitFaq(): Promise<void> {
  const category = form.category.trim()
  const question = form.question.trim()
  const answer = form.answer.trim()
  if (!category) {
    ElMessage.warning('分类不能为空')
    return
  }
  if (!question) {
    ElMessage.warning('问题不能为空')
    return
  }
  if (!answer) {
    ElMessage.warning('答案不能为空')
    return
  }

  const payload: FaqCommandPayload = {
    category,
    question,
    answer,
    sort: form.sort,
    status: form.status,
  }

  submitting.value = true
  try {
    if (editingId.value === null) {
      await createFaq(payload)
      ElMessage.success('FAQ 已创建')
    } else {
      await updateFaq(editingId.value, payload)
      ElMessage.success('FAQ 已更新')
    }
    dialogVisible.value = false
    await load()
  } catch {
    // 错误提示由 http 响应拦截器统一输出，此处不重复弹
  } finally {
    submitting.value = false
  }
}

async function confirmDelete(row: FaqView): Promise<void> {
  try {
    await ElMessageBox.confirm(
      `确认删除 FAQ「${row.question}」？删除后用户端不再展示。`,
      '删除 FAQ',
      {
        confirmButtonText: '确认删除',
        cancelButtonText: '取消',
        type: 'warning',
      },
    )
  } catch {
    return
  }

  try {
    await deleteFaq(row.id)
    ElMessage.success('FAQ 已删除')
    await load()
  } catch {
    // 错误提示由 http 响应拦截器统一输出，此处不重复弹
  }
}

onMounted(load)
</script>

<template>
  <div class="page">
    <div class="page-lead">
      <div>
        <h2>FAQ 维护</h2>
        <p>
          数据源：<span class="mono">GET/POST/PUT/DELETE /api/v1/admin/faqs</span>。
          分类、问题、答案均为纯文本，按 <span class="mono">sort</span> 升序、ID 倒序展示。
        </p>
      </div>
      <div class="toolbar">
        <el-button :icon="Refresh" :loading="loading" @click="load">刷新</el-button>
        <el-button type="primary" :icon="Plus" @click="openCreate">新建 FAQ</el-button>
      </div>
    </div>

    <el-alert v-if="loadError" type="error" :closable="false" show-icon :title="loadError" />

    <el-card shadow="never">
      <div class="toolbar filter-bar">
        <el-input
          v-model="filters.category"
          placeholder="分类"
          clearable
          class="filter-input"
          @keyup.enter="search"
        />
        <el-input
          v-model="filters.keyword"
          placeholder="问题或答案关键词"
          clearable
          class="keyword-input"
          @keyup.enter="search"
        />
        <el-button type="primary" @click="search">查询</el-button>
        <el-button @click="reset">重置</el-button>
      </div>

      <el-table v-loading="loading" :data="faqs" border stripe row-key="id">
        <el-table-column prop="category" label="分类" width="130" show-overflow-tooltip />
        <el-table-column prop="question" label="问题" min-width="220" show-overflow-tooltip />
        <el-table-column prop="answer" label="答案" min-width="320" show-overflow-tooltip />
        <el-table-column label="排序" width="80" align="right">
          <template #default="{ row }">{{ row.sort }}</template>
        </el-table-column>
        <el-table-column label="浏览量" width="95" align="right">
          <template #default="{ row }">{{ formatCount(row.viewCount) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="faqStatusType(row.status)" size="small">
              {{ faqStatusLabel(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="更新时间" width="150">
          <template #default="{ row }">{{ formatDateTime(row.updatedAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="130" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button link type="danger" @click="confirmDelete(row)">删除</el-button>
          </template>
        </el-table-column>

        <template #empty>
          <el-empty description="当前筛选条件下没有 FAQ。" :image-size="80" />
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
      v-model="dialogVisible"
      :title="editingId === null ? '新建 FAQ' : '编辑 FAQ'"
      width="640px"
      :close-on-click-modal="false"
    >
      <el-form label-width="72px">
        <el-form-item label="分类" required>
          <el-input
            v-model="form.category"
            maxlength="64"
            show-word-limit
            placeholder="例如：订单与配送"
          />
        </el-form-item>
        <el-form-item label="问题" required>
          <el-input
            v-model="form.question"
            maxlength="200"
            show-word-limit
            placeholder="用户看到的常见问题"
          />
        </el-form-item>
        <el-form-item label="答案" required>
          <el-input
            v-model="form.answer"
            type="textarea"
            :autosize="{ minRows: 6, maxRows: 14 }"
            placeholder="纯文本答案，禁止填写 HTML"
          />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="form.sort" :min="0" :precision="0" :controls="false" />
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="form.status">
            <el-radio :value="0">启用</el-radio>
            <el-radio :value="1">停用</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitFaq">
          {{ editingId === null ? '创建' : '保存' }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.filter-bar {
  margin-bottom: 14px;
}

.filter-input {
  width: 150px;
}

.keyword-input {
  width: 240px;
}

.pagination {
  margin-top: 16px;
  justify-content: flex-end;
}
</style>
