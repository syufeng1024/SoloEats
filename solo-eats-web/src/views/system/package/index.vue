<script setup lang="ts">
import { computed, nextTick, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Refresh } from '@element-plus/icons-vue'
import {
  createAdminPackage,
  getAdminMenuTree,
  listAdminPackages,
  updateAdminPackage,
  type AdminPackageView,
  type SavePackagePayload,
} from '@/api/system-admin'
import type { MenuNode } from '@/api/types'
import {
  PACKAGE_STATUS,
  PACKAGE_STATUS_OPTIONS,
  labelOf,
  tagOf,
} from '@/constants/systemStatus'
import { formatDateTime } from '@/utils/datetime'
import { mergeRoleMenuIds, planRoleMenuSelection } from '@/utils/menu-perms'
import { centsToYuan, formatYuan, yuanToFen } from '@/utils/money'
import { errorMessage } from '@/views/report/helpers'

interface MenuTreeExpose {
  setCheckedKeys: (keys: number[]) => void
  getCheckedKeys: (leafOnly?: boolean) => number[]
  getHalfCheckedKeys: () => number[]
}

const loading = ref(false)
const loadError = ref('')
const actionError = ref('')
const packages = ref<AdminPackageView[]>([])
const keyword = ref('')
const statusFilter = ref<number | undefined>(undefined)

const filteredPackages = computed(() => {
  const needle = keyword.value.trim().toLowerCase()
  return packages.value.filter((item) => {
    if (statusFilter.value !== undefined && item.status !== statusFilter.value) {
      return false
    }
    if (needle === '') {
      return true
    }
    return (
      item.packageCode.toLowerCase().includes(needle) ||
      item.packageName.toLowerCase().includes(needle)
    )
  })
})

const dialogVisible = ref(false)
const editingId = ref<number | null>(null)
const originalPackageCode = ref('')
const originalMenuIds = ref<number[] | null>(null)
const submitting = ref(false)

const form = reactive({
  packageCode: '',
  packageName: '',
  scope: 'limited' as 'all' | 'limited',
  maxShop: 0,
  maxUser: 0,
  priceYuan: 0,
  validDays: 0,
  status: 0,
  remark: '',
})

const menuTreeData = ref<MenuNode[]>([])
const menuTreeLoading = ref(false)
const menuTreeError = ref('')
const menuTreeRef = ref<MenuTreeExpose | null>(null)
const hiddenMenuIds = ref<number[]>([])
const strictMode = ref(false)
const pendingCheckedKeys = ref<number[]>([])

const allScopeDisabled = computed(
  () => editingId.value !== null && originalMenuIds.value !== null,
)

function menuSummary(row: AdminPackageView): string {
  if (row.menuIds === null) {
    return '不限制'
  }
  return `已限制 ${row.menuIds.length} 项`
}

function limitText(value: number): string {
  return value === 0 ? '不限' : String(value)
}

function validDaysText(value: number): string {
  return value === 0 ? '长期' : `${value} 天`
}

async function load(): Promise<void> {
  loading.value = true
  loadError.value = ''
  try {
    packages.value = await listAdminPackages()
  } catch (error) {
    packages.value = []
    loadError.value = errorMessage(error, '套餐列表加载失败')
  } finally {
    loading.value = false
  }
}

async function ensureMenuTree(): Promise<void> {
  if (menuTreeData.value.length > 0) {
    return
  }
  menuTreeLoading.value = true
  menuTreeError.value = ''
  try {
    menuTreeData.value = await getAdminMenuTree()
  } catch (error) {
    menuTreeData.value = []
    menuTreeError.value = errorMessage(
      error,
      '菜单树加载失败，已禁止保存以避免清空授权',
    )
  } finally {
    menuTreeLoading.value = false
  }
}

function applyCheckedKeys(): void {
  menuTreeRef.value?.setCheckedKeys(pendingCheckedKeys.value)
}

function resetForm(): void {
  Object.assign(form, {
    packageCode: '',
    packageName: '',
    scope: 'limited',
    maxShop: 0,
    maxUser: 0,
    priceYuan: 0,
    validDays: 0,
    status: 0,
    remark: '',
  })
  originalMenuIds.value = null
  hiddenMenuIds.value = []
  strictMode.value = false
  pendingCheckedKeys.value = []
}

function openCreate(): void {
  editingId.value = null
  originalPackageCode.value = ''
  resetForm()
  dialogVisible.value = true
  void ensureMenuTree()
}

async function openEdit(row: AdminPackageView): Promise<void> {
  editingId.value = row.id
  originalPackageCode.value = row.packageCode
  originalMenuIds.value = row.menuIds === null ? null : [...row.menuIds]
  Object.assign(form, {
    packageCode: row.packageCode,
    packageName: row.packageName,
    scope: row.menuIds === null ? 'all' : 'limited',
    maxShop: row.maxShop,
    maxUser: row.maxUser,
    priceYuan: centsToYuan(row.price),
    validDays: row.validDays,
    status: row.status,
    remark: row.remark ?? '',
  })
  hiddenMenuIds.value = []
  strictMode.value = false
  pendingCheckedKeys.value = []
  dialogVisible.value = true

  if (row.menuIds !== null) {
    await ensureMenuTree()
    if (menuTreeError.value !== '') {
      return
    }
    const plan = planRoleMenuSelection(menuTreeData.value, row.menuIds)
    hiddenMenuIds.value = plan.hiddenMenuIds
    strictMode.value = plan.strictMode
    pendingCheckedKeys.value = plan.checkedKeys
    await nextTick()
    applyCheckedKeys()
  }
}

async function handleScopeChange(value: string | number | boolean | undefined): Promise<void> {
  if (value === 'all') {
    if (allScopeDisabled.value) {
      form.scope = 'limited'
      ElMessage.warning('后端更新接口以 null 表示保持原值，不能把已限制套餐改回不限制')
      return
    }
    pendingCheckedKeys.value = []
    hiddenMenuIds.value = []
    strictMode.value = false
    return
  }

  await ensureMenuTree()
  if (menuTreeError.value !== '') {
    return
  }

  if (editingId.value !== null && originalMenuIds.value !== null) {
    const plan = planRoleMenuSelection(menuTreeData.value, originalMenuIds.value)
    hiddenMenuIds.value = plan.hiddenMenuIds
    strictMode.value = plan.strictMode
    pendingCheckedKeys.value = plan.checkedKeys
  } else if (editingId.value !== null) {
    hiddenMenuIds.value = []
    strictMode.value = false
    pendingCheckedKeys.value = []
  }

  await nextTick()
  applyCheckedKeys()
}

function validateForm(): string | null {
  const packageCode = form.packageCode.trim()
  if (
    editingId.value === null &&
    !/^[A-Za-z0-9_-]{2,64}$/.test(packageCode)
  ) {
    return '套餐编码只能包含字母、数字、下划线、中划线，长度 2~64'
  }

  const packageName = form.packageName.trim()
  if (packageName === '') {
    return '套餐名称不能为空'
  }
  if (packageName.length > 64) {
    return '套餐名称不能超过 64 个字符'
  }

  if (!Number.isInteger(form.maxShop) || form.maxShop < 0) {
    return '门店上限必须是非负整数（0 表示不限制）'
  }
  if (!Number.isInteger(form.maxUser) || form.maxUser < 0) {
    return '账号上限必须是非负整数（0 表示不限制）'
  }
  if (!Number.isFinite(form.priceYuan) || form.priceYuan < 0) {
    return '套餐价格必须大于等于 0'
  }
  if (!Number.isInteger(form.validDays) || form.validDays < 0) {
    return '有效期天数必须是非负整数（0 表示长期有效）'
  }
  if (form.status !== 0 && form.status !== 1) {
    return '状态不正确'
  }
  if (form.remark.trim().length > 255) {
    return '备注不能超过 255 个字符'
  }
  if (form.scope === 'limited' && menuTreeError.value !== '') {
    return '菜单树加载失败，禁止保存以免清空授权'
  }
  return null
}

async function resolveMenuIds(): Promise<{ menuIds: number[] | null } | null> {
  if (form.scope === 'all') {
    if (allScopeDisabled.value) {
      ElMessage.warning('后端更新接口以 null 表示保持原值，不能把已限制套餐改回不限制')
      return null
    }
    return { menuIds: null }
  }

  await ensureMenuTree()
  if (menuTreeError.value !== '') {
    ElMessage.warning('菜单树加载失败，禁止保存以免清空授权')
    return null
  }

  const checked = menuTreeRef.value?.getCheckedKeys(false) ?? []
  const half = strictMode.value
    ? []
    : (menuTreeRef.value?.getHalfCheckedKeys() ?? [])
  const menuIds = mergeRoleMenuIds(checked, half, hiddenMenuIds.value)

  if (menuIds.length === 0) {
    if (editingId.value === null) {
      ElMessage.warning('请至少选择一个菜单，或选择不限制')
      return null
    }
    try {
      await ElMessageBox.confirm(
        '当前未勾选任何菜单，保存后该套餐将不开放任何菜单，确认继续？',
        '确认菜单范围',
        {
          confirmButtonText: '确认保存',
          cancelButtonText: '取消',
          type: 'warning',
        },
      )
    } catch {
      return null
    }
  }

  return { menuIds }
}

async function submitPackage(): Promise<void> {
  const invalid = validateForm()
  if (invalid !== null) {
    ElMessage.warning(invalid)
    return
  }

  const resolved = await resolveMenuIds()
  if (resolved === null) {
    return
  }

  const payload: SavePackagePayload = {
    packageCode:
      editingId.value === null ? form.packageCode.trim() : originalPackageCode.value,
    packageName: form.packageName.trim(),
    menuIds: resolved.menuIds,
    maxShop: form.maxShop,
    maxUser: form.maxUser,
    price: yuanToFen(form.priceYuan),
    validDays: form.validDays,
    status: form.status,
    remark: form.remark.trim(),
  }

  submitting.value = true
  actionError.value = ''
  try {
    if (editingId.value === null) {
      await createAdminPackage(payload)
      ElMessage.success('套餐已创建')
    } else {
      await updateAdminPackage(editingId.value, payload)
      ElMessage.success('套餐已更新')
    }
    dialogVisible.value = false
    await load()
  } catch (error) {
    actionError.value = errorMessage(error, '套餐保存失败')
  } finally {
    submitting.value = false
  }
}

function resetFilters(): void {
  keyword.value = ''
  statusFilter.value = undefined
}

onMounted(load)
</script>

<template>
  <div class="page">
    <div class="page-lead">
      <div>
        <h2>套餐管理</h2>
        <p>
          数据源：<span class="mono">GET /api/v1/admin/packages</span>（不分页）。
          后端未提供删除与独立启停接口；菜单范围 null 表示不限制，数组表示受限。
          更新接口以 null 表示保持原值，因此已受限套餐不能改回不限制。
        </p>
      </div>
      <div class="toolbar">
        <el-button :icon="Refresh" :loading="loading" @click="load">刷新</el-button>
        <el-button type="primary" :icon="Plus" @click="openCreate">新建套餐</el-button>
      </div>
    </div>

    <el-alert v-if="loadError" type="error" :closable="false" show-icon :title="loadError" />
    <el-alert v-if="actionError" type="error" :closable="false" show-icon :title="actionError" />

    <el-card shadow="never">
      <div class="toolbar filter-bar">
        <el-input
          v-model="keyword"
          clearable
          placeholder="套餐编码/名称（本地过滤）"
          style="width: 240px"
        />
        <el-select
          v-model="statusFilter"
          clearable
          placeholder="全部状态"
          style="width: 130px"
        >
          <el-option
            v-for="option in PACKAGE_STATUS_OPTIONS"
            :key="option.value"
            :label="option.label"
            :value="option.value"
          />
        </el-select>
        <el-button @click="resetFilters">重置</el-button>
        <span class="muted">共 {{ filteredPackages.length }} 个套餐</span>
      </div>

      <el-table v-loading="loading" :data="filteredPackages" border stripe row-key="id">
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column label="套餐编码" min-width="150">
          <template #default="{ row }">
            <span class="mono">{{ row.packageCode }}</span>
          </template>
        </el-table-column>
        <el-table-column
          prop="packageName"
          label="套餐名称"
          min-width="180"
          show-overflow-tooltip
        />
        <el-table-column label="菜单范围" width="130">
          <template #default="{ row }">{{ menuSummary(row) }}</template>
        </el-table-column>
        <el-table-column label="门店上限" width="100" align="right">
          <template #default="{ row }">{{ limitText(row.maxShop) }}</template>
        </el-table-column>
        <el-table-column label="账号上限" width="100" align="right">
          <template #default="{ row }">{{ limitText(row.maxUser) }}</template>
        </el-table-column>
        <el-table-column label="价格" width="110" align="right">
          <template #default="{ row }">{{ formatYuan(row.price) }}</template>
        </el-table-column>
        <el-table-column label="有效期" width="100" align="right">
          <template #default="{ row }">{{ validDaysText(row.validDays) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="85" align="center">
          <template #default="{ row }">
            <el-tag :type="tagOf(PACKAGE_STATUS, row.status)" size="small">
              {{ labelOf(PACKAGE_STATUS, row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="备注" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">{{ row.remark || '—' }}</template>
        </el-table-column>
        <el-table-column label="创建时间" min-width="165">
          <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="更新时间" min-width="165">
          <template #default="{ row }">{{ formatDateTime(row.updatedAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="90" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
          </template>
        </el-table-column>

        <template #empty>
          <el-empty description="当前筛选条件下没有套餐。" :image-size="80" />
        </template>
      </el-table>
    </el-card>

    <el-dialog
      v-model="dialogVisible"
      :title="editingId === null ? '新建套餐' : '编辑套餐'"
      width="720px"
      :close-on-click-modal="false"
      @opened="applyCheckedKeys"
    >
      <el-form label-width="100px">
        <el-form-item label="套餐编码" required>
          <el-input
            v-model="form.packageCode"
            :disabled="editingId !== null"
            maxlength="64"
            placeholder="例如 STANDARD"
          />
          <div v-if="editingId !== null" class="form-hint">
            更新接口忽略 packageCode，编码创建后不可修改。
          </div>
        </el-form-item>
        <el-form-item label="套餐名称" required>
          <el-input v-model="form.packageName" maxlength="64" show-word-limit />
        </el-form-item>
        <el-form-item label="菜单范围" required>
          <el-radio-group v-model="form.scope" @change="handleScopeChange">
            <el-radio value="all" :disabled="allScopeDisabled">不限制</el-radio>
            <el-radio value="limited">按菜单授权</el-radio>
          </el-radio-group>
          <div v-if="allScopeDisabled" class="form-hint">
            该套餐已有受限菜单；更新接口以 null 表示保持原值，无法改回不限制。
          </div>
        </el-form-item>

        <el-form-item v-if="form.scope === 'limited'" label="菜单授权">
          <div class="menu-scope-box">
            <el-alert
              v-if="menuTreeError"
              type="error"
              :closable="false"
              show-icon
              :title="menuTreeError"
            />
            <el-alert
              v-if="hiddenMenuIds.length > 0"
              type="warning"
              :closable="false"
              show-icon
              :title="`已保留 ${hiddenMenuIds.length} 项菜单树未返回的授权，保存不会清空它们。`"
            />
            <el-alert
              v-if="strictMode"
              type="info"
              :closable="false"
              show-icon
              title="当前授权不是完整子树，已切换为精确勾选模式，父子节点互不联动。"
            />
            <el-tree
              ref="menuTreeRef"
              v-loading="menuTreeLoading"
              :data="menuTreeData"
              node-key="id"
              show-checkbox
              default-expand-all
              :check-strictly="strictMode"
              :props="{ label: 'menuName', children: 'children' }"
            >
              <template #default="{ data }">
                <span>{{ data.menuName }}</span>
                <el-tag
                  v-if="data.menuType === 3"
                  type="warning"
                  size="small"
                  class="menu-type-tag"
                >
                  按钮
                </el-tag>
              </template>
            </el-tree>
          </div>
        </el-form-item>

        <el-form-item label="门店上限">
          <el-input-number
            v-model="form.maxShop"
            :min="0"
            :precision="0"
            :controls="false"
          />
          <span class="field-suffix">0 表示不限制</span>
        </el-form-item>
        <el-form-item label="账号上限">
          <el-input-number
            v-model="form.maxUser"
            :min="0"
            :precision="0"
            :controls="false"
          />
          <span class="field-suffix">0 表示不限制</span>
        </el-form-item>
        <el-form-item label="套餐价格">
          <el-input-number
            v-model="form.priceYuan"
            :min="0"
            :precision="2"
            :controls="false"
          />
          <span class="field-suffix">元；后台按分存储</span>
        </el-form-item>
        <el-form-item label="有效期">
          <el-input-number
            v-model="form.validDays"
            :min="0"
            :precision="0"
            :controls="false"
          />
          <span class="field-suffix">天；0 表示长期有效</span>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="form.status" class="full-width">
            <el-option
              v-for="option in PACKAGE_STATUS_OPTIONS"
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
        <el-button type="primary" :loading="submitting" @click="submitPackage">
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

.full-width {
  width: 100%;
}

.form-hint {
  color: var(--el-text-color-secondary);
  font-size: 12px;
  line-height: 1.6;
  margin-top: 4px;
}

.menu-scope-box {
  width: 100%;
  max-height: 280px;
  overflow: auto;
  padding: 4px;
  border: 1px solid var(--el-border-color);
  border-radius: 4px;
}

.menu-scope-box .el-alert {
  margin-bottom: 8px;
}

.menu-type-tag {
  margin-left: 6px;
}

.field-suffix {
  margin-left: 8px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
</style>
