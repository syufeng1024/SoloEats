<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Refresh } from '@element-plus/icons-vue'
import {
  createAdminNotice,
  listAdminNotices,
  publishAdminNotice,
  updateAdminNotice,
  type AdminNoticeView,
  type SaveNoticePayload,
} from '@/api/system-admin'
import {
  NOTICE_STATUS,
  NOTICE_STATUS_OPTIONS,
  NOTICE_TARGET_TYPE,
  NOTICE_TARGET_TYPE_OPTIONS,
  NOTICE_TYPE,
  NOTICE_TYPE_OPTIONS,
  labelOf,
  tagOf,
} from '@/constants/systemStatus'
import { debounce } from '@/utils/debounce'
import { formatDateTime } from '@/utils/datetime'
import { errorMessage } from '@/views/report/helpers'

const PAGE_SIZE_OPTIONS = [10, 20, 50]

const loading = ref(false)
const loadError = ref('')
const actionError = ref('')
const notices = ref<AdminNoticeView[]>([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(20)
const filters = reactive({
  keyword: '',
  status: undefined as number | undefined,
  noticeType: undefined as number | undefined,
  targetType: undefined as number | undefined,
})

const dialogVisible = ref(false)
const editingId = ref<number | null>(null)
const originalStatus = ref(0)
const submitting = ref(false)

const form = reactive({
  title: '',
  content: '',
  noticeType: 1,
  targetType: 1,
  publishAt: '',
  expireAt: '',
})

async function load(): Promise<void> {
  loading.value = true
  loadError.value = ''
  try {
    const result = await listAdminNotices({
      keyword: filters.keyword.trim() || undefined,
      status: typeof filters.status === 'number' ? filters.status : undefined,
      noticeType:
        typeof filters.noticeType === 'number' ? filters.noticeType : undefined,
      targetType:
        typeof filters.targetType === 'number' ? filters.targetType : undefined,
      pageNum: pageNum.value,
      pageSize: pageSize.value,
    })
    notices.value = result.list
    total.value = result.total
  } catch (error) {
    notices.value = []
    total.value = 0
    loadError.value = errorMessage(error, '公告列表加载失败')
  } finally {
    loading.value = false
  }
}

function resetForm(): void {
  Object.assign(form, {
    title: '',
    content: '',
    noticeType: 1,
    targetType: 1,
    publishAt: '',
    expireAt: '',
  })
}

function openCreate(): void {
  editingId.value = null
  originalStatus.value = 0
  resetForm()
  dialogVisible.value = true
}

function openEdit(row: AdminNoticeView): void {
  if (row.status === 1) {
    ElMessage.warning('已发布公告请先撤回再修改')
    return
  }
  editingId.value = row.id
  originalStatus.value = row.status
  Object.assign(form, {
    title: row.title,
    content: row.content,
    noticeType: row.noticeType,
    targetType: row.targetType,
    publishAt: row.publishAt ? formatDateTime(row.publishAt) : '',
    expireAt: row.expireAt ? formatDateTime(row.expireAt) : '',
  })
  dialogVisible.value = true
}

function parseDateTime(value: string): number {
  return new Date(value.replace(' ', 'T')).getTime()
}

function validateForm(): string | null {
  const title = form.title.trim()
  if (title === '') {
    return '公告标题不能为空'
  }
  if (title.length > 128) {
    return '公告标题不能超过 128 个字符'
  }
  if (form.content.trim() === '') {
    return '公告正文不能为空'
  }
  if (form.content.length > 1_000_000) {
    return '公告正文不能超过 1,000,000 个字符'
  }
  if (form.noticeType !== 1 && form.noticeType !== 2) {
    return '公告类型不正确'
  }
  if (
    form.targetType !== 1 &&
    form.targetType !== 2 &&
    form.targetType !== 3 &&
    form.targetType !== 4
  ) {
    return '发布目标不正确'
  }

  const publishAt = form.publishAt.trim()
  const expireAt = form.expireAt.trim()
  if (publishAt !== '' && !Number.isFinite(parseDateTime(publishAt))) {
    return '发布时间格式不正确'
  }
  if (expireAt !== '' && !Number.isFinite(parseDateTime(expireAt))) {
    return '过期时间格式不正确'
  }
  if (
    publishAt !== '' &&
    expireAt !== '' &&
    parseDateTime(expireAt) <= parseDateTime(publishAt)
  ) {
    return '过期时间必须晚于发布时间'
  }
  return null
}

function buildPayload(): SaveNoticePayload {
  return {
    title: form.title.trim(),
    content: form.content.trim(),
    noticeType: form.noticeType,
    targetType: form.targetType,
    publishAt: form.publishAt.trim() || null,
    expireAt: form.expireAt.trim() || null,
  }
}

async function submitNotice(): Promise<void> {
  const invalid = validateForm()
  if (invalid !== null) {
    ElMessage.warning(invalid)
    return
  }

  const payload = buildPayload()
  submitting.value = true
  actionError.value = ''
  try {
    if (editingId.value === null) {
      await createAdminNotice(payload)
      ElMessage.success('公告已创建，当前为草稿状态')
    } else {
      await updateAdminNotice(editingId.value, payload)
      ElMessage.success('公告已更新')
    }
    dialogVisible.value = false
    await load()
  } catch (error) {
    actionError.value = errorMessage(error, '公告保存失败')
  } finally {
    submitting.value = false
  }
}

function publishActionLabel(row: AdminNoticeView): string {
  if (row.status === 1) {
    return '撤回'
  }
  return row.status === 2 ? '重新发布' : '发布'
}

async function confirmPublish(row: AdminNoticeView): Promise<void> {
  const publishing = row.status !== 1
  if (publishing && row.expireAt) {
    const expireTime = parseDateTime(formatDateTime(row.expireAt))
    if (Number.isFinite(expireTime) && expireTime <= Date.now()) {
      ElMessage.warning('公告已过期，请先编辑调整过期时间后再发布')
      return
    }
  }

  const actionText = publishing ? '发布' : '撤回'
  try {
    await ElMessageBox.confirm(
      publishing
        ? `确认发布公告「${row.title}」？若发布时间在未来，用户侧将在该时间生效。`
        : `确认撤回公告「${row.title}」？撤回后可再次编辑。`,
      `${actionText}公告`,
      {
        confirmButtonText: `确认${actionText}`,
        cancelButtonText: '取消',
        type: 'warning',
      },
    )
  } catch {
    return
  }

  actionError.value = ''
  try {
    await publishAdminNotice(row.id, publishing ? 1 : 2)
    ElMessage.success(`公告已${actionText}`)
    await load()
  } catch (error) {
    actionError.value = errorMessage(error, `${actionText}失败`)
  }
}

const runKeywordSearch = debounce(() => {
  pageNum.value = 1
  void load()
}, 300)

function search(): void {
  pageNum.value = 1
  void load()
}

function resetFilters(): void {
  filters.keyword = ''
  filters.status = undefined
  filters.noticeType = undefined
  filters.targetType = undefined
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

onMounted(load)
</script>

<template>
  <div class="page">
    <div class="page-lead">
      <div>
        <h2>公告管理</h2>
        <p>
          数据源：<span class="mono">GET /api/v1/admin/notices</span>。
          后端未提供删除接口，也没有发布人字段；新建后固定为草稿，
          已发布公告必须先撤回才能修改。发布/撤回走
          <span class="mono">PUT /api/v1/admin/notices/{id}/publish</span>。
        </p>
      </div>
      <div class="toolbar">
        <el-button :icon="Refresh" :loading="loading" @click="load">刷新</el-button>
        <el-button type="primary" :icon="Plus" @click="openCreate">新建公告</el-button>
      </div>
    </div>

    <el-alert v-if="loadError" type="error" :closable="false" show-icon :title="loadError" />
    <el-alert v-if="actionError" type="error" :closable="false" show-icon :title="actionError" />

    <el-card shadow="never">
      <div class="toolbar filter-bar">
        <el-input
          v-model="filters.keyword"
          clearable
          placeholder="标题关键词"
          style="width: 200px"
          @input="runKeywordSearch"
          @keyup.enter="search"
        />
        <el-select
          v-model="filters.status"
          clearable
          placeholder="全部状态"
          style="width: 120px"
        >
          <el-option
            v-for="option in NOTICE_STATUS_OPTIONS"
            :key="option.value"
            :label="option.label"
            :value="option.value"
          />
        </el-select>
        <el-select
          v-model="filters.noticeType"
          clearable
          placeholder="全部类型"
          style="width: 120px"
        >
          <el-option
            v-for="option in NOTICE_TYPE_OPTIONS"
            :key="option.value"
            :label="option.label"
            :value="option.value"
          />
        </el-select>
        <el-select
          v-model="filters.targetType"
          clearable
          placeholder="全部目标"
          style="width: 120px"
        >
          <el-option
            v-for="option in NOTICE_TARGET_TYPE_OPTIONS"
            :key="option.value"
            :label="option.label"
            :value="option.value"
          />
        </el-select>
        <el-button type="primary" @click="search">查询</el-button>
        <el-button @click="resetFilters">重置</el-button>
      </div>

      <el-table v-loading="loading" :data="notices" border stripe row-key="id">
        <el-table-column type="expand">
          <template #default="{ row }">
            <div class="expand-wrap">
              <div class="pre-wrap">{{ row.content }}</div>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column
          prop="title"
          label="标题"
          min-width="220"
          show-overflow-tooltip
        />
        <el-table-column label="类型" width="85" align="center">
          <template #default="{ row }">
            <el-tag :type="tagOf(NOTICE_TYPE, row.noticeType)" size="small">
              {{ labelOf(NOTICE_TYPE, row.noticeType) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="目标" width="85" align="center">
          <template #default="{ row }">
            <el-tag :type="tagOf(NOTICE_TARGET_TYPE, row.targetType)" size="small">
              {{ labelOf(NOTICE_TARGET_TYPE, row.targetType) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="tagOf(NOTICE_STATUS, row.status)" size="small">
              {{ labelOf(NOTICE_STATUS, row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="发布时间" min-width="165">
          <template #default="{ row }">{{ formatDateTime(row.publishAt) }}</template>
        </el-table-column>
        <el-table-column label="过期时间" min-width="165">
          <template #default="{ row }">{{ formatDateTime(row.expireAt) }}</template>
        </el-table-column>
        <el-table-column prop="readCount" label="阅读数" width="85" align="right" />
        <el-table-column label="创建时间" min-width="165">
          <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="更新时间" min-width="165">
          <template #default="{ row }">{{ formatDateTime(row.updatedAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="190" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button link type="primary" @click="confirmPublish(row)">
              {{ publishActionLabel(row) }}
            </el-button>
          </template>
        </el-table-column>

        <template #empty>
          <el-empty description="当前筛选条件下没有公告。" :image-size="80" />
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
      :title="editingId === null ? '新建公告' : '编辑公告'"
      width="760px"
      :close-on-click-modal="false"
    >
      <el-alert
        v-if="editingId === null"
        type="info"
        :closable="false"
        show-icon
        title="新建公告固定为草稿状态；保存后需在列表点击“发布”。"
        class="form-alert"
      />
      <el-alert
        v-else
        type="info"
        :closable="false"
        show-icon
        :title="`当前状态：${labelOf(NOTICE_STATUS, originalStatus)}。已发布公告需先撤回再修改。`"
        class="form-alert"
      />

      <el-form label-width="100px">
        <el-form-item label="标题" required>
          <el-input v-model="form.title" maxlength="128" show-word-limit />
        </el-form-item>
        <el-form-item label="类型" required>
          <el-select v-model="form.noticeType" class="full-width">
            <el-option
              v-for="option in NOTICE_TYPE_OPTIONS"
              :key="option.value"
              :label="option.label"
              :value="option.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="发布目标" required>
          <el-select v-model="form.targetType" class="full-width">
            <el-option
              v-for="option in NOTICE_TARGET_TYPE_OPTIONS"
              :key="option.value"
              :label="option.label"
              :value="option.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="发布时间">
          <el-date-picker
            v-model="form.publishAt"
            type="datetime"
            value-format="YYYY-MM-DD HH:mm:ss"
            placeholder="留空表示发布动作发生时立即生效"
            class="full-width"
          />
        </el-form-item>
        <el-form-item label="过期时间">
          <el-date-picker
            v-model="form.expireAt"
            type="datetime"
            value-format="YYYY-MM-DD HH:mm:ss"
            placeholder="留空表示不过期"
            class="full-width"
          />
          <div class="form-hint">
            编辑时留空提交 null，后端保持原值；后端不支持清空已设置的时间。
          </div>
        </el-form-item>
        <el-form-item label="正文" required>
          <el-input
            v-model="form.content"
            type="textarea"
            :rows="12"
            maxlength="1000000"
            placeholder="公告正文"
          />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitNotice">
          {{ editingId === null ? '创建草稿' : '保存' }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.filter-bar {
  margin-bottom: 14px;
}

.pagination {
  margin-top: 16px;
  justify-content: flex-end;
}

.full-width {
  width: 100%;
}

.form-hint {
  color: var(--el-text-color-secondary);
  font-size: 12px;
  line-height: 1.6;
  margin-top: 4px;
}

.form-alert {
  margin-bottom: 14px;
}

.expand-wrap {
  padding: 8px 16px 16px;
}

.pre-wrap {
  white-space: pre-wrap;
  word-break: break-word;
  max-height: 360px;
  overflow: auto;
  line-height: 1.7;
}
</style>
