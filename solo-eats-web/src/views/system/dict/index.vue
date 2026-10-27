<script setup lang="ts">
import { computed, nextTick, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Refresh } from '@element-plus/icons-vue'
import {
  createAdminDictItem,
  createAdminDictType,
  deleteAdminDictItem,
  deleteAdminDictType,
  listAdminDictItems,
  listAdminDictTypes,
  updateAdminDictItem,
  updateAdminDictType,
  type AdminDictItemView,
  type AdminDictTypeView,
  type SaveDictItemPayload,
  type SaveDictTypePayload,
} from '@/api/system-admin'
import {
  DICT_TYPE_STATUS,
  DICT_TYPE_STATUS_OPTIONS,
  FLAG,
  FLAG_OPTIONS,
  labelOf,
  tagOf,
} from '@/constants/systemStatus'
import { debounce } from '@/utils/debounce'
import { formatDateTime } from '@/utils/datetime'
import { errorMessage } from '@/views/report/helpers'

interface DictTypeTableExpose {
  setCurrentRow: (row: AdminDictTypeView | null) => void
}

const PAGE_SIZE_OPTIONS = [10, 20, 50]

const typeLoading = ref(false)
const typeLoadError = ref('')
const typeActionError = ref('')
const types = ref<AdminDictTypeView[]>([])
const typeTotal = ref(0)
const typePageNum = ref(1)
const typePageSize = ref(20)
const typeFilters = reactive({
  dictType: '',
  status: undefined as number | undefined,
})
const selectedType = ref<AdminDictTypeView | null>(null)
const typeTableRef = ref<DictTypeTableExpose | null>(null)

const itemLoading = ref(false)
const itemLoadError = ref('')
const itemActionError = ref('')
const items = ref<AdminDictItemView[]>([])
let itemRequestSeq = 0

const typeDialogVisible = ref(false)
const typeEditingId = ref<number | null>(null)
const typeOriginalCode = ref('')
const typeSubmitting = ref(false)
const typeForm = reactive({
  dictType: '',
  dictName: '',
  status: 0,
  remark: '',
})

const itemDialogVisible = ref(false)
const itemEditingId = ref<number | null>(null)
const itemSubmitting = ref(false)
const itemForm = reactive({
  dictType: '',
  dictLabel: '',
  dictValue: '',
  dictSort: 0,
  cssClass: '',
  isDefault: 0,
  remark: '',
})

const selectedTypeOnPage = computed(() =>
  types.value.some((item) => item.id === selectedType.value?.id),
)

const selectedTitle = computed(() =>
  selectedType.value
    ? `${selectedType.value.dictType} · ${selectedType.value.dictName}`
    : '请选择左侧字典类型',
)

async function loadTypes(keepSelection: boolean): Promise<void> {
  typeLoading.value = true
  typeLoadError.value = ''
  try {
    const result = await listAdminDictTypes({
      dictType: typeFilters.dictType.trim() || undefined,
      status:
        typeof typeFilters.status === 'number' ? typeFilters.status : undefined,
      pageNum: typePageNum.value,
      pageSize: typePageSize.value,
    })
    types.value = result.list
    typeTotal.value = result.total

    const previous = selectedType.value
    const onPage = previous
      ? result.list.find((item) => item.id === previous.id)
      : undefined

    if (onPage) {
      selectedType.value = onPage
      await nextTick()
      typeTableRef.value?.setCurrentRow(onPage)
      return
    }

    if (keepSelection && previous) {
      selectedType.value = previous
      await nextTick()
      typeTableRef.value?.setCurrentRow(null)
      return
    }

    const first = result.list[0] ?? null
    selectedType.value = first
    await nextTick()
    typeTableRef.value?.setCurrentRow(first)
    if (first) {
      await loadItems(first.dictType)
    } else {
      itemRequestSeq += 1
      items.value = []
      itemLoadError.value = ''
    }
  } catch (error) {
    types.value = []
    typeTotal.value = 0
    selectedType.value = null
    itemRequestSeq += 1
    items.value = []
    typeLoadError.value = errorMessage(error, '字典类型加载失败')
  } finally {
    typeLoading.value = false
  }
}

async function loadItems(dictType: string): Promise<void> {
  const seq = ++itemRequestSeq
  itemLoading.value = true
  itemLoadError.value = ''
  try {
    const result = await listAdminDictItems(dictType)
    if (seq !== itemRequestSeq) {
      return
    }
    items.value = result
  } catch (error) {
    if (seq !== itemRequestSeq) {
      return
    }
    items.value = []
    itemLoadError.value = errorMessage(error, '字典项加载失败')
  } finally {
    if (seq === itemRequestSeq) {
      itemLoading.value = false
    }
  }
}

function refreshItems(): void {
  if (selectedType.value) {
    void loadItems(selectedType.value.dictType)
  }
}

function selectType(row: AdminDictTypeView): void {
  if (selectedType.value?.id === row.id) {
    return
  }
  selectedType.value = row
  void loadItems(row.dictType)
}

function handleTypeCurrentChange(row: AdminDictTypeView | null): void {
  if (row) {
    selectType(row)
  }
}

const runTypeKeywordSearch = debounce(() => {
  typePageNum.value = 1
  void loadTypes(false)
}, 300)

function searchTypes(): void {
  typePageNum.value = 1
  void loadTypes(false)
}

function resetTypeFilters(): void {
  typeFilters.dictType = ''
  typeFilters.status = undefined
  typePageNum.value = 1
  void loadTypes(false)
}

function handleTypePageChange(page: number): void {
  typePageNum.value = page
  void loadTypes(true)
}

function handleTypeSizeChange(size: number): void {
  typePageSize.value = size
  typePageNum.value = 1
  void loadTypes(true)
}

function resetTypeForm(): void {
  Object.assign(typeForm, {
    dictType: '',
    dictName: '',
    status: 0,
    remark: '',
  })
}

function openCreateType(): void {
  typeEditingId.value = null
  typeOriginalCode.value = ''
  resetTypeForm()
  typeDialogVisible.value = true
}

function openEditType(row: AdminDictTypeView): void {
  typeEditingId.value = row.id
  typeOriginalCode.value = row.dictType
  Object.assign(typeForm, {
    dictType: row.dictType,
    dictName: row.dictName,
    status: row.status,
    remark: row.remark ?? '',
  })
  typeDialogVisible.value = true
}

function validateTypeForm(): string | null {
  const dictType = typeForm.dictType.trim()
  if (
    typeEditingId.value === null &&
    !/^[A-Za-z][A-Za-z0-9_]{1,63}$/.test(dictType)
  ) {
    return '字典类型编码必须以字母开头，只能包含字母、数字、下划线，长度 2~64'
  }
  const dictName = typeForm.dictName.trim()
  if (dictName === '') {
    return '字典类型名称不能为空'
  }
  if (dictName.length > 64) {
    return '字典类型名称不能超过 64 个字符'
  }
  if (typeForm.status !== 0 && typeForm.status !== 1) {
    return '状态不正确'
  }
  if (typeForm.remark.trim().length > 255) {
    return '备注不能超过 255 个字符'
  }
  return null
}

function buildTypePayload(): SaveDictTypePayload {
  return {
    dictType:
      typeEditingId.value === null
        ? typeForm.dictType.trim()
        : typeOriginalCode.value,
    dictName: typeForm.dictName.trim(),
    status: typeForm.status,
    remark: typeForm.remark.trim(),
  }
}

async function submitType(): Promise<void> {
  const invalid = validateTypeForm()
  if (invalid !== null) {
    ElMessage.warning(invalid)
    return
  }

  const payload = buildTypePayload()
  typeSubmitting.value = true
  typeActionError.value = ''
  try {
    if (typeEditingId.value === null) {
      await createAdminDictType(payload)
      ElMessage.success('字典类型已创建')
      typeFilters.dictType = payload.dictType
      typePageNum.value = 1
      typeDialogVisible.value = false
      await loadTypes(false)
    } else {
      await updateAdminDictType(typeEditingId.value, payload)
      ElMessage.success('字典类型已更新')
      typeDialogVisible.value = false
      await loadTypes(true)
    }
  } catch (error) {
    typeActionError.value = errorMessage(error, '字典类型保存失败')
  } finally {
    typeSubmitting.value = false
  }
}

async function confirmDeleteType(row: AdminDictTypeView): Promise<void> {
  try {
    await ElMessageBox.confirm(
      `确认删除字典类型「${row.dictType} · ${row.dictName}」？该类型下的全部字典项会被级联逻辑删除。`,
      '删除字典类型',
      {
        confirmButtonText: '确认删除',
        cancelButtonText: '取消',
        type: 'warning',
      },
    )
  } catch {
    return
  }

  typeActionError.value = ''
  try {
    await deleteAdminDictType(row.id)
    ElMessage.success('字典类型已删除')
    if (selectedType.value?.id === row.id) {
      selectedType.value = null
      itemRequestSeq += 1
      items.value = []
      itemLoadError.value = ''
    }
    await loadTypes(false)
  } catch (error) {
    typeActionError.value = errorMessage(error, '字典类型删除失败')
  }
}

function resetItemForm(): void {
  Object.assign(itemForm, {
    dictType: selectedType.value?.dictType ?? '',
    dictLabel: '',
    dictValue: '',
    dictSort: 0,
    cssClass: '',
    isDefault: 0,
    remark: '',
  })
}

function openCreateItem(): void {
  if (!selectedType.value) {
    ElMessage.warning('请先选择左侧字典类型')
    return
  }
  itemEditingId.value = null
  resetItemForm()
  itemDialogVisible.value = true
}

function openEditItem(row: AdminDictItemView): void {
  itemEditingId.value = row.id
  Object.assign(itemForm, {
    dictType: row.dictType,
    dictLabel: row.dictLabel,
    dictValue: row.dictValue,
    dictSort: row.dictSort,
    cssClass: row.cssClass ?? '',
    isDefault: row.isDefault,
    remark: row.remark ?? '',
  })
  itemDialogVisible.value = true
}

function validateItemForm(): string | null {
  const dictLabel = itemForm.dictLabel.trim()
  if (dictLabel === '') {
    return '字典标签不能为空'
  }
  if (dictLabel.length > 128) {
    return '字典标签不能超过 128 个字符'
  }
  const dictValue = itemForm.dictValue.trim()
  if (dictValue === '') {
    return '字典值不能为空'
  }
  if (dictValue.length > 128) {
    return '字典值不能超过 128 个字符'
  }
  if (!Number.isInteger(itemForm.dictSort) || itemForm.dictSort < 0) {
    return '排序必须是非负整数'
  }
  if (itemForm.cssClass.trim().length > 64) {
    return '样式类不能超过 64 个字符'
  }
  if (itemForm.isDefault !== 0 && itemForm.isDefault !== 1) {
    return '默认项取值不正确'
  }
  if (itemForm.remark.trim().length > 255) {
    return '备注不能超过 255 个字符'
  }
  return null
}

function buildItemPayload(): SaveDictItemPayload {
  return {
    dictType: itemForm.dictType,
    dictLabel: itemForm.dictLabel.trim(),
    dictValue: itemForm.dictValue.trim(),
    dictSort: itemForm.dictSort,
    cssClass: itemForm.cssClass.trim(),
    isDefault: itemForm.isDefault,
    // 接口只返回启用项，停用后无法在本页恢复，因此固定以启用状态保存
    status: 0,
    remark: itemForm.remark.trim(),
  }
}

async function submitItem(): Promise<void> {
  const invalid = validateItemForm()
  if (invalid !== null) {
    ElMessage.warning(invalid)
    return
  }

  const payload = buildItemPayload()
  itemSubmitting.value = true
  itemActionError.value = ''
  try {
    if (itemEditingId.value === null) {
      await createAdminDictItem(payload)
      ElMessage.success('字典项已创建')
    } else {
      await updateAdminDictItem(itemEditingId.value, payload)
      ElMessage.success('字典项已更新')
    }
    itemDialogVisible.value = false
    refreshItems()
  } catch (error) {
    itemActionError.value = errorMessage(error, '字典项保存失败')
  } finally {
    itemSubmitting.value = false
  }
}

async function confirmDeleteItem(row: AdminDictItemView): Promise<void> {
  try {
    await ElMessageBox.confirm(
      `确认删除字典项「${row.dictLabel}」？该操作会逻辑删除该字典项。`,
      '删除字典项',
      {
        confirmButtonText: '确认删除',
        cancelButtonText: '取消',
        type: 'warning',
      },
    )
  } catch {
    return
  }

  itemActionError.value = ''
  try {
    await deleteAdminDictItem(row.id)
    ElMessage.success('字典项已删除')
    refreshItems()
  } catch (error) {
    itemActionError.value = errorMessage(error, '字典项删除失败')
  }
}

onMounted(() => {
  void loadTypes(false)
})
</script>

<template>
  <div class="page">
    <div class="page-lead">
      <div>
        <h2>字典管理</h2>
        <p>
          左侧 <span class="mono">GET /api/v1/admin/dicts</span> 分页查询字典类型；
          右侧 <span class="mono">GET /api/v1/admin/dicts/{dictType}/items</span>
          只返回启用项且不分页。字典项一旦停用会从右侧消失，当前后端没有恢复入口，
          因此本页固定以启用状态保存字典项。
        </p>
      </div>
    </div>

    <el-alert
      v-if="typeLoadError"
      type="error"
      :closable="false"
      show-icon
      :title="typeLoadError"
    />
    <el-alert
      v-if="typeActionError"
      type="error"
      :closable="false"
      show-icon
      :title="typeActionError"
    />

    <el-row :gutter="16" class="dict-body">
      <el-col :xs="24" :md="11" :lg="9">
        <el-card shadow="never" class="pane-card">
          <div class="pane-head">
            <strong>字典类型</strong>
            <el-button type="primary" size="small" :icon="Plus" @click="openCreateType">
              新建类型
            </el-button>
          </div>

          <div class="toolbar filter-bar">
            <el-input
              v-model="typeFilters.dictType"
              clearable
              placeholder="编码/名称"
              style="width: 160px"
              @input="runTypeKeywordSearch"
              @keyup.enter="searchTypes"
            />
            <el-select
              v-model="typeFilters.status"
              clearable
              placeholder="状态"
              style="width: 100px"
            >
              <el-option
                v-for="option in DICT_TYPE_STATUS_OPTIONS"
                :key="option.value"
                :label="option.label"
                :value="option.value"
              />
            </el-select>
            <el-button @click="searchTypes">查询</el-button>
            <el-button @click="resetTypeFilters">重置</el-button>
          </div>

          <el-table
            ref="typeTableRef"
            v-loading="typeLoading"
            :data="types"
            border
            stripe
            row-key="id"
            highlight-current-row
            @current-change="handleTypeCurrentChange"
          >
            <el-table-column label="类型编码" min-width="140">
              <template #default="{ row }">
                <span class="mono">{{ row.dictType }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="dictName" label="名称" min-width="120" />
            <el-table-column label="状态" width="75" align="center">
              <template #default="{ row }">
                <el-tag :type="tagOf(DICT_TYPE_STATUS, row.status)" size="small">
                  {{ labelOf(DICT_TYPE_STATUS, row.status) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="110" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" @click.stop="openEditType(row)">
                  编辑
                </el-button>
                <el-button link type="danger" @click.stop="confirmDeleteType(row)">
                  删除
                </el-button>
              </template>
            </el-table-column>

            <template #empty>
              <el-empty description="没有字典类型。" :image-size="70" />
            </template>
          </el-table>

          <el-pagination
            class="pagination"
            layout="total, sizes, prev, pager, next"
            :total="typeTotal"
            :page-size="typePageSize"
            :page-sizes="PAGE_SIZE_OPTIONS"
            :current-page="typePageNum"
            @current-change="handleTypePageChange"
            @size-change="handleTypeSizeChange"
          />
        </el-card>
      </el-col>

      <el-col :xs="24" :md="13" :lg="15">
        <el-card shadow="never" class="pane-card">
          <div class="pane-head">
            <div>
              <strong>{{ selectedTitle }}</strong>
              <span v-if="selectedType && !selectedTypeOnPage" class="muted">
                （当前选中类型不在本页）
              </span>
            </div>
            <div class="toolbar">
              <el-button
                size="small"
                :icon="Refresh"
                :disabled="!selectedType"
                @click="refreshItems"
              >
                刷新
              </el-button>
              <el-button
                type="primary"
                size="small"
                :icon="Plus"
                :disabled="!selectedType"
                @click="openCreateItem"
              >
                新增字典项
              </el-button>
            </div>
          </div>

          <el-alert
            type="warning"
            :closable="false"
            show-icon
            title="字典项接口只返回启用项；停用后无法在本页恢复，因此表单固定提交 status=0。"
          />
          <el-alert
            v-if="itemLoadError"
            type="error"
            :closable="false"
            show-icon
            :title="itemLoadError"
            class="pane-alert"
          />
          <el-alert
            v-if="itemActionError"
            type="error"
            :closable="false"
            show-icon
            :title="itemActionError"
            class="pane-alert"
          />

          <el-table
            v-loading="itemLoading"
            :data="items"
            border
            stripe
            row-key="id"
          >
            <el-table-column label="标签" min-width="140">
              <template #default="{ row }">
                <el-tag :class="row.cssClass" size="small">
                  {{ row.dictLabel }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="值" min-width="120">
              <template #default="{ row }">
                <span class="mono">{{ row.dictValue }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="dictSort" label="排序" width="70" align="right" />
            <el-table-column label="样式类" min-width="100">
              <template #default="{ row }">
                <span class="mono">{{ row.cssClass || '—' }}</span>
              </template>
            </el-table-column>
            <el-table-column label="默认" width="70" align="center">
              <template #default="{ row }">
                <el-tag :type="tagOf(FLAG, row.isDefault)" size="small">
                  {{ labelOf(FLAG, row.isDefault) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="备注" min-width="130" show-overflow-tooltip>
              <template #default="{ row }">{{ row.remark || '—' }}</template>
            </el-table-column>
            <el-table-column label="更新时间" min-width="165">
              <template #default="{ row }">{{ formatDateTime(row.updatedAt) }}</template>
            </el-table-column>
            <el-table-column label="操作" width="110" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" @click="openEditItem(row)">编辑</el-button>
                <el-button link type="danger" @click="confirmDeleteItem(row)">
                  删除
                </el-button>
              </template>
            </el-table-column>

            <template #empty>
              <el-empty
                :description="selectedType ? '该类型下没有启用的字典项。' : '请选择左侧字典类型。'"
                :image-size="80"
              />
            </template>
          </el-table>
        </el-card>
      </el-col>
    </el-row>

    <el-dialog
      v-model="typeDialogVisible"
      :title="typeEditingId === null ? '新建字典类型' : '编辑字典类型'"
      width="560px"
      :close-on-click-modal="false"
    >
      <el-form label-width="100px">
        <el-form-item label="类型编码" required>
          <el-input
            v-model="typeForm.dictType"
            :disabled="typeEditingId !== null"
            maxlength="64"
            placeholder="例如 order_status"
          />
          <div v-if="typeEditingId !== null" class="form-hint">
            更新接口禁止变更 dictType，编码创建后不可修改。
          </div>
        </el-form-item>
        <el-form-item label="类型名称" required>
          <el-input v-model="typeForm.dictName" maxlength="64" show-word-limit />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="typeForm.status" class="full-width">
            <el-option
              v-for="option in DICT_TYPE_STATUS_OPTIONS"
              :key="option.value"
              :label="option.label"
              :value="option.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="备注">
          <el-input
            v-model="typeForm.remark"
            type="textarea"
            :rows="3"
            maxlength="255"
            show-word-limit
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="typeDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="typeSubmitting" @click="submitType">
          {{ typeEditingId === null ? '创建' : '保存' }}
        </el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="itemDialogVisible"
      :title="itemEditingId === null ? '新建字典项' : '编辑字典项'"
      width="600px"
      :close-on-click-modal="false"
    >
      <el-form label-width="100px">
        <el-form-item label="字典类型">
          <el-input :model-value="itemForm.dictType" disabled />
        </el-form-item>
        <el-form-item label="标签" required>
          <el-input v-model="itemForm.dictLabel" maxlength="128" show-word-limit />
        </el-form-item>
        <el-form-item label="值" required>
          <el-input v-model="itemForm.dictValue" maxlength="128" show-word-limit />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number
            v-model="itemForm.dictSort"
            :min="0"
            :precision="0"
            :controls="false"
          />
        </el-form-item>
        <el-form-item label="样式类">
          <el-input
            v-model="itemForm.cssClass"
            maxlength="64"
            placeholder="任意 CSS 类名，用于 el-tag 预览"
          />
        </el-form-item>
        <el-form-item label="标签预览">
          <el-tag
            v-if="itemForm.dictLabel.trim()"
            :class="itemForm.cssClass.trim()"
            size="small"
          >
            {{ itemForm.dictLabel.trim() }}
          </el-tag>
          <span v-else class="muted">填写标签后预览</span>
        </el-form-item>
        <el-form-item label="默认项">
          <el-select v-model="itemForm.isDefault" class="full-width">
            <el-option
              v-for="option in FLAG_OPTIONS"
              :key="option.value"
              :label="option.label"
              :value="option.value"
            />
          </el-select>
          <div class="form-hint">同一字典类型最多一个默认项，由后端保证。</div>
        </el-form-item>
        <el-form-item label="备注">
          <el-input
            v-model="itemForm.remark"
            type="textarea"
            :rows="3"
            maxlength="255"
            show-word-limit
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="itemDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="itemSubmitting" @click="submitItem">
          {{ itemEditingId === null ? '创建' : '保存' }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.dict-body {
  align-items: stretch;
}

.pane-card {
  height: 100%;
}

.pane-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}

.filter-bar {
  margin-bottom: 12px;
}

.pagination {
  margin-top: 12px;
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

.pane-alert {
  margin-top: 8px;
}
</style>
