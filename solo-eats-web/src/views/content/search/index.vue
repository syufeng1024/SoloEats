<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Refresh } from '@element-plus/icons-vue'
import {
  createSearchKeyword,
  deleteSearchKeyword,
  listSearchKeywords,
  updateSearchKeyword,
  type SaveSearchKeywordPayload,
  type SearchKeywordView,
} from '@/api/content-admin'
import {
  CONTENT_STATUS,
  CONTENT_STATUS_OPTIONS,
  KEYWORD_TYPE,
  KEYWORD_TYPE_OPTIONS,
  labelOf,
  tagOf,
} from '@/constants/contentStatus'
import { formatDateTime } from '@/utils/datetime'
import { errorMessage } from '@/views/report/helpers'

const PAGE_SIZE_OPTIONS = [10, 20, 50]

const KEYWORD_TABS: Array<{ name: string; label: string; keywordType?: number }> = [
  { name: 'all', label: '全部' },
  { name: 'default', label: '默认词', keywordType: 1 },
  { name: 'hot', label: '热搜词', keywordType: 2 },
  { name: 'suggest', label: '联想词', keywordType: 3 },
  { name: 'blocked', label: '屏蔽词', keywordType: 4 },
]

const loading = ref(false)
const loadError = ref('')
const actionError = ref('')
const rows = ref<SearchKeywordView[]>([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(20)
const activeTab = ref('all')
const keywordType = ref<number | undefined>(undefined)

const dialogVisible = ref(false)
const editingId = ref<number | null>(null)
const submitting = ref(false)
const dialogError = ref('')

const form = reactive({
  keyword: '',
  keywordType: 1,
  weight: 0,
  status: 1,
})

async function load(): Promise<void> {
  loading.value = true
  loadError.value = ''
  try {
    const result = await listSearchKeywords({
      keywordType: keywordType.value,
      pageNum: pageNum.value,
      pageSize: pageSize.value,
    })
    rows.value = result.list
    total.value = result.total
  } catch (error) {
    rows.value = []
    total.value = 0
    loadError.value = errorMessage(error, '搜索词加载失败')
  } finally {
    loading.value = false
  }
}

function handleTabChange(name: string | number): void {
  const tab = KEYWORD_TABS.find((item) => item.name === String(name))
  keywordType.value = tab?.keywordType
  pageNum.value = 1
  void load()
}

function search(): void {
  pageNum.value = 1
  void load()
}

function reset(): void {
  activeTab.value = 'all'
  keywordType.value = undefined
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
  editingId.value = null
  form.keyword = ''
  form.keywordType = keywordType.value ?? 1
  form.weight = 0
  form.status = 1
  dialogError.value = ''
  dialogVisible.value = true
}

function openEdit(row: SearchKeywordView): void {
  editingId.value = row.id
  form.keyword = row.keyword
  form.keywordType = row.keywordType
  form.weight = row.weight
  form.status = row.status
  dialogError.value = ''
  dialogVisible.value = true
}

function buildPayload(): SaveSearchKeywordPayload | null {
  const keyword = form.keyword.trim()
  if (!keyword) {
    dialogError.value = '关键词不能为空'
    return null
  }
  if (keyword.length > 64) {
    dialogError.value = '关键词不能超过 64 个字符'
    return null
  }
  if (form.keywordType < 1 || form.keywordType > 4) {
    dialogError.value = '关键词分类只能是 1~4'
    return null
  }
  if (!Number.isInteger(form.weight) || form.weight < 0) {
    dialogError.value = '权重必须是不小于 0 的整数'
    return null
  }
  return {
    keyword,
    keywordType: form.keywordType,
    weight: form.weight,
    status: form.status,
  }
}

async function submitKeyword(): Promise<void> {
  dialogError.value = ''
  const payload = buildPayload()
  if (!payload) {
    return
  }
  submitting.value = true
  try {
    if (editingId.value === null) {
      await createSearchKeyword(payload)
      ElMessage.success('关键词已创建')
    } else {
      await updateSearchKeyword(editingId.value, payload)
      ElMessage.success('关键词已更新')
    }
    dialogVisible.value = false
    await load()
  } catch (error) {
    dialogError.value = errorMessage(error, '关键词保存失败')
  } finally {
    submitting.value = false
  }
}

async function confirmDelete(row: SearchKeywordView): Promise<void> {
  try {
    await ElMessageBox.confirm(
      `确认删除关键词「${row.keyword}」？同类型下关键词重复会被后端拒绝。`,
      '删除关键词',
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
    await deleteSearchKeyword(row.id)
    ElMessage.success('关键词已删除')
    await load()
  } catch (error) {
    actionError.value = errorMessage(error, '关键词删除失败')
  }
}

onMounted(load)
</script>

<template>
  <div class="page">
    <div class="page-lead">
      <div>
        <h2>搜索运营</h2>
        <p>
          数据源：<span class="mono">GET /api/v1/admin/search-keywords</span>。四类关键词共用一张表，
          以 <span class="mono">keywordType</span> 区分；列表按权重倒序。
        </p>
      </div>
      <div class="toolbar">
        <el-button :icon="Refresh" :loading="loading" @click="load">刷新</el-button>
        <el-button type="primary" :icon="Plus" @click="openCreate">新建关键词</el-button>
      </div>
    </div>

    <el-alert v-if="loadError" type="error" :closable="false" show-icon :title="loadError" />
    <el-alert v-if="actionError" type="error" :closable="false" show-icon :title="actionError" />

    <el-card shadow="never">
      <el-tabs v-model="activeTab" class="tabs" @tab-change="handleTabChange">
        <el-tab-pane
          v-for="tab in KEYWORD_TABS"
          :key="tab.name"
          :label="tab.label"
          :name="tab.name"
        />
      </el-tabs>

      <div class="filter-bar">
        <el-button type="primary" @click="search">查询</el-button>
        <el-button @click="reset">重置分类</el-button>
      </div>

      <el-table v-loading="loading" :data="rows" border stripe row-key="id">
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="keyword" label="关键词" min-width="200" />
        <el-table-column label="分类" width="110" align="center">
          <template #default="{ row }">
            <el-tag :type="tagOf(KEYWORD_TYPE, row.keywordType)" size="small">
              {{ labelOf(KEYWORD_TYPE, row.keywordType) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="weight" label="权重（越大越靠前）" width="170" />
        <el-table-column label="状态" width="80" align="center">
          <template #default="{ row }">
            <el-tag :type="tagOf(CONTENT_STATUS, row.status)" size="small">
              {{ labelOf(CONTENT_STATUS, row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="创建时间" min-width="170">
          <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="更新时间" min-width="170">
          <template #default="{ row }">{{ formatDateTime(row.updatedAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="140" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button link type="danger" @click="confirmDelete(row)">删除</el-button>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="当前分类暂无关键词。" :image-size="80" />
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
      :title="editingId === null ? '新建关键词' : '编辑关键词'"
      width="520px"
      :close-on-click-modal="false"
    >
      <el-alert
        v-if="dialogError"
        class="dialog-alert"
        type="error"
        :closable="false"
        show-icon
        :title="dialogError"
      />
      <el-form label-width="96px">
        <el-form-item label="关键词" required>
          <el-input v-model="form.keyword" maxlength="64" show-word-limit />
        </el-form-item>
        <el-form-item label="分类" required>
          <el-select v-model="form.keywordType" class="full-width">
            <el-option
              v-for="item in KEYWORD_TYPE_OPTIONS"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="权重">
          <el-input-number v-model="form.weight" :min="0" :max="999999" />
          <div class="form-hint">权重越大越靠前，默认 0。</div>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="form.status" class="full-width">
            <el-option
              v-for="item in CONTENT_STATUS_OPTIONS"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitKeyword">
          {{ editingId === null ? '创建' : '保存' }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.tabs :deep(.el-tabs__header) {
  margin-bottom: 14px;
}

.filter-bar {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-bottom: 14px;
}

.filter-select {
  width: 160px;
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

.dialog-alert {
  margin-bottom: 12px;
}

.pagination {
  margin-top: 16px;
  justify-content: flex-end;
}
</style>
