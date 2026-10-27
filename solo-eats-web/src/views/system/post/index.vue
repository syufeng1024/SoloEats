<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Refresh } from '@element-plus/icons-vue'
import {
  createAdminPost,
  deleteAdminPost,
  listAdminPosts,
  updateAdminPost,
  type AdminPostView,
  type SavePostPayload,
} from '@/api/system-admin'
import {
  SYSTEM_STATUS,
  SYSTEM_STATUS_OPTIONS,
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
const posts = ref<AdminPostView[]>([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(20)
const filters = reactive({
  keyword: '',
  status: undefined as number | undefined,
})

const dialogVisible = ref(false)
const editingId = ref<number | null>(null)
const originalPostCode = ref('')
const submitting = ref(false)

const form = reactive({
  postCode: '',
  postName: '',
  sort: 0,
  status: 0,
  remark: '',
})

async function load(): Promise<void> {
  loading.value = true
  loadError.value = ''
  try {
    const result = await listAdminPosts({
      keyword: filters.keyword.trim() || undefined,
      status: filters.status,
      pageNum: pageNum.value,
      pageSize: pageSize.value,
    })
    posts.value = result.list
    total.value = result.total
  } catch (error) {
    posts.value = []
    total.value = 0
    loadError.value = errorMessage(error, '岗位列表加载失败')
  } finally {
    loading.value = false
  }
}

function resetForm(): void {
  Object.assign(form, {
    postCode: '',
    postName: '',
    sort: 0,
    status: 0,
    remark: '',
  })
}

function openCreate(): void {
  editingId.value = null
  originalPostCode.value = ''
  resetForm()
  dialogVisible.value = true
}

function openEdit(row: AdminPostView): void {
  editingId.value = row.id
  originalPostCode.value = row.postCode
  Object.assign(form, {
    postCode: row.postCode,
    postName: row.postName,
    sort: row.sort,
    status: row.status,
    remark: row.remark ?? '',
  })
  dialogVisible.value = true
}

function validateForm(): string | null {
  const postCode = form.postCode.trim()
  if (!/^[A-Za-z0-9_-]{2,64}$/.test(postCode)) {
    return '岗位编码只能包含字母、数字、下划线、中划线，长度 2~64'
  }
  const postName = form.postName.trim()
  if (postName === '') {
    return '岗位名称不能为空'
  }
  if (postName.length > 64) {
    return '岗位名称不能超过 64 个字符'
  }
  if (!Number.isInteger(form.sort) || form.sort < 0) {
    return '排序必须是非负整数'
  }
  if (form.status !== 0 && form.status !== 1) {
    return '状态不正确'
  }
  if (form.remark.trim().length > 255) {
    return '备注不能超过 255 个字符'
  }
  return null
}

function buildPayload(): SavePostPayload {
  return {
    // PUT 的 postCode 是 @NotBlank，但服务端不更新，编辑时必须回传原值
    postCode: editingId.value === null ? form.postCode.trim() : originalPostCode.value,
    postName: form.postName.trim(),
    sort: form.sort,
    status: form.status,
    remark: form.remark.trim(),
  }
}

async function submitPost(): Promise<void> {
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
      await createAdminPost(payload)
      ElMessage.success('岗位已创建')
    } else {
      await updateAdminPost(editingId.value, payload)
      ElMessage.success('岗位已更新')
    }
    dialogVisible.value = false
    await load()
  } catch (error) {
    actionError.value = errorMessage(error, '岗位保存失败')
  } finally {
    submitting.value = false
  }
}

async function confirmDelete(row: AdminPostView): Promise<void> {
  try {
    await ElMessageBox.confirm(
      `确认删除岗位「${row.postName}」？仍有账号引用时后端会拒绝删除。`,
      '删除岗位',
      {
        confirmButtonText: '确认删除',
        cancelButtonText: '取消',
        type: 'warning',
      },
    )
  } catch {
    return
  }

  actionError.value = ''
  try {
    await deleteAdminPost(row.id)
    ElMessage.success('岗位已删除')
    await load()
  } catch (error) {
    const message = errorMessage(error, '岗位删除失败')
    actionError.value = message.includes('账号引用')
      ? `${message}。当前用户接口未暴露 postId，无法在本后台调整账号岗位；请先停用岗位或由后端/数据库处理。`
      : message
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
        <h2>岗位管理</h2>
        <p>
          数据源：<span class="mono">GET /api/v1/admin/posts</span>。
          编辑时岗位编码只读并以原值回传（PUT 接口要求 postCode 非空，但服务端不更新该字段）。
        </p>
      </div>
      <div class="toolbar">
        <el-button :icon="Refresh" :loading="loading" @click="load">刷新</el-button>
        <el-button type="primary" :icon="Plus" @click="openCreate">新建岗位</el-button>
      </div>
    </div>

    <el-alert v-if="loadError" type="error" :closable="false" show-icon :title="loadError" />
    <el-alert v-if="actionError" type="error" :closable="false" show-icon :title="actionError" />

    <el-card shadow="never">
      <div class="toolbar filter-bar">
        <el-input
          v-model="filters.keyword"
          clearable
          placeholder="岗位编码/名称"
          style="width: 220px"
          @input="runKeywordSearch"
          @keyup.enter="search"
        />
        <el-select
          v-model="filters.status"
          clearable
          placeholder="全部状态"
          style="width: 130px"
        >
          <el-option
            v-for="option in SYSTEM_STATUS_OPTIONS"
            :key="option.value"
            :label="option.label"
            :value="option.value"
          />
        </el-select>
        <el-button type="primary" @click="search">查询</el-button>
        <el-button @click="resetFilters">重置</el-button>
      </div>

      <el-table v-loading="loading" :data="posts" border stripe row-key="id">
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="postCode" label="岗位编码" min-width="150" />
        <el-table-column prop="postName" label="岗位名称" min-width="160" />
        <el-table-column prop="sort" label="排序" width="80" align="right" />
        <el-table-column label="状态" width="85" align="center">
          <template #default="{ row }">
            <el-tag :type="tagOf(SYSTEM_STATUS, row.status)" size="small">
              {{ labelOf(SYSTEM_STATUS, row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="备注" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">{{ row.remark || '—' }}</template>
        </el-table-column>
        <el-table-column label="创建时间" min-width="165">
          <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="160" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button link type="danger" @click="confirmDelete(row)">删除</el-button>
          </template>
        </el-table-column>

        <template #empty>
          <el-empty description="当前筛选条件下没有岗位。" :image-size="80" />
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
      :title="editingId === null ? '新建岗位' : '编辑岗位'"
      width="560px"
      :close-on-click-modal="false"
    >
      <el-form label-width="96px">
        <el-form-item label="岗位编码" required>
          <el-input
            v-model="form.postCode"
            :disabled="editingId !== null"
            maxlength="64"
            placeholder="例如 FINANCE"
          />
          <div v-if="editingId !== null" class="form-hint">
            PUT 接口要求 postCode 非空，但服务端不会更新；本页只读并以原值回传。
          </div>
        </el-form-item>
        <el-form-item label="岗位名称" required>
          <el-input v-model="form.postName" maxlength="64" show-word-limit />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number
            v-model="form.sort"
            :min="0"
            :precision="0"
            :controls="false"
          />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="form.status" class="full-width">
            <el-option
              v-for="option in SYSTEM_STATUS_OPTIONS"
              :key="option.value"
              :label="option.label"
              :value="option.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="备注">
          <el-input
            v-model="form.remark"
            type="textarea"
            :rows="3"
            maxlength="255"
            show-word-limit
          />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitPost">
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
</style>
