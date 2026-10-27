<script setup lang="ts">
import { nextTick, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Refresh } from '@element-plus/icons-vue'
import {
  assignAdminRoleMenus,
  createAdminRole,
  getAdminMenuTree,
  getAdminRoleMenus,
  listAdminRoles,
  updateAdminRole,
  type AdminRoleView,
  type SaveRolePayload,
} from '@/api/system-admin'
import type { MenuNode } from '@/api/types'
import {
  DATA_SCOPE,
  DATA_SCOPE_OPTIONS,
  MENU_TYPE,
  ROLE_TYPE,
  ROLE_TYPE_OPTIONS,
  SYSTEM_STATUS,
  SYSTEM_STATUS_OPTIONS,
  labelOf,
  tagOf,
} from '@/constants/systemStatus'
import { formatDateTime } from '@/utils/datetime'
import { mergeRoleMenuIds, planRoleMenuSelection } from '@/utils/menu-perms'
import { errorMessage } from '@/views/report/helpers'

const PAGE_SIZE_OPTIONS = [10, 20, 50]

const loading = ref(false)
const loadError = ref('')
const actionError = ref('')
const roles = ref<AdminRoleView[]>([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(20)
const filters = reactive({
  roleType: undefined as number | undefined,
  status: undefined as number | undefined,
})

const formVisible = ref(false)
const editingId = ref<number | null>(null)
const originalRoleCode = ref('')
const originalRoleType = ref(1)
const submitting = ref(false)

const form = reactive({
  roleCode: '',
  roleName: '',
  roleType: 1,
  dataScope: 5,
  sort: 0,
  status: 0,
  remark: '',
})

interface MenuTreeExpose {
  setCheckedKeys: (keys: number[]) => void
  getCheckedKeys: (leafOnly?: boolean) => number[]
  getHalfCheckedKeys: () => number[]
}

const assignVisible = ref(false)
const assignRole = ref<AdminRoleView | null>(null)
const assignLoading = ref(false)
const assigning = ref(false)
const assignError = ref('')
const menuTreeData = ref<MenuNode[]>([])
const hiddenMenuIds = ref<number[]>([])
const strictMode = ref(false)
const pendingCheckedKeys = ref<number[]>([])
const menuTreeRef = ref<MenuTreeExpose | null>(null)

async function load(): Promise<void> {
  loading.value = true
  loadError.value = ''
  try {
    const result = await listAdminRoles({
      roleType: filters.roleType,
      status: filters.status,
      pageNum: pageNum.value,
      pageSize: pageSize.value,
    })
    roles.value = result.list
    total.value = result.total
  } catch (error) {
    roles.value = []
    total.value = 0
    loadError.value = errorMessage(error, '角色列表加载失败')
  } finally {
    loading.value = false
  }
}

function search(): void {
  pageNum.value = 1
  void load()
}

function resetFilters(): void {
  filters.roleType = undefined
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

function resetForm(): void {
  Object.assign(form, {
    roleCode: '',
    roleName: '',
    roleType: 1,
    dataScope: 5,
    sort: 0,
    status: 0,
    remark: '',
  })
}

function openCreate(): void {
  editingId.value = null
  originalRoleCode.value = ''
  originalRoleType.value = 1
  resetForm()
  formVisible.value = true
}

function openEdit(row: AdminRoleView): void {
  editingId.value = row.id
  originalRoleCode.value = row.roleCode
  originalRoleType.value = row.roleType
  Object.assign(form, {
    roleCode: row.roleCode,
    roleName: row.roleName,
    roleType: row.roleType,
    dataScope: row.dataScope,
    sort: row.sort,
    status: row.status,
    remark: row.remark ?? '',
  })
  formVisible.value = true
}

function normalizeRoleCode(value: string): string {
  return value.trim().toUpperCase()
}

function validateForm(): string | null {
  const roleCode = normalizeRoleCode(form.roleCode)
  if (!/^[A-Z][A-Z0-9_]{1,63}$/.test(roleCode)) {
    return '角色码必须以字母开头，只能包含大写字母、数字、下划线，长度 2~64'
  }
  const roleName = form.roleName.trim()
  if (roleName === '') {
    return '角色名称不能为空'
  }
  if (roleName.length > 64) {
    return '角色名称不能超过 64 个字符'
  }
  if (form.roleType !== 1 && form.roleType !== 2 && form.roleType !== 3) {
    return '角色类型不正确'
  }
  if (![1, 2, 3, 4, 5].includes(form.dataScope)) {
    return '数据范围不正确'
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

function buildPayload(): SaveRolePayload {
  return {
    roleCode: normalizeRoleCode(form.roleCode),
    roleName: form.roleName.trim(),
    roleType: form.roleType,
    dataScope: form.dataScope,
    sort: form.sort,
    status: form.status,
    remark: form.remark.trim(),
  }
}

async function submitRole(): Promise<void> {
  const invalid = validateForm()
  if (invalid !== null) {
    ElMessage.warning(invalid)
    return
  }

  const payload = buildPayload()
  if (
    editingId.value !== null &&
    (payload.roleCode !== originalRoleCode.value || payload.roleType !== originalRoleType.value)
  ) {
    try {
      await ElMessageBox.confirm(
        '修改角色码/角色类型会影响后续签发的 JWT 角色码，并可能让已分配账号的角色校验失败；已签发 token 到期前仍是旧角色码。确认继续？',
        '修改角色标识',
        {
          confirmButtonText: '确认修改',
          cancelButtonText: '取消',
          type: 'warning',
        },
      )
    } catch {
      return
    }
  }

  submitting.value = true
  actionError.value = ''
  try {
    if (editingId.value === null) {
      await createAdminRole(payload)
      ElMessage.success('角色已创建')
    } else {
      await updateAdminRole(editingId.value, payload)
      ElMessage.success('角色已更新')
    }
    formVisible.value = false
    await load()
  } catch (error) {
    actionError.value = errorMessage(error, '角色保存失败')
  } finally {
    submitting.value = false
  }
}

async function openAssignMenus(row: AdminRoleView): Promise<void> {
  assignRole.value = row
  assignVisible.value = true
  assignLoading.value = true
  assignError.value = ''
  hiddenMenuIds.value = []
  strictMode.value = false
  pendingCheckedKeys.value = []
  menuTreeData.value = []

  try {
    const [tree, grantedIds] = await Promise.all([
      getAdminMenuTree(),
      getAdminRoleMenus(row.id),
    ])
    const plan = planRoleMenuSelection(tree, grantedIds)
    menuTreeData.value = tree
    hiddenMenuIds.value = plan.hiddenMenuIds
    strictMode.value = plan.strictMode
    pendingCheckedKeys.value = plan.checkedKeys
    await nextTick()
    applyCheckedKeys()
  } catch (error) {
    assignError.value = errorMessage(error, '角色菜单加载失败，已禁止保存以避免清空授权')
  } finally {
    assignLoading.value = false
  }
}

function applyCheckedKeys(): void {
  menuTreeRef.value?.setCheckedKeys(pendingCheckedKeys.value)
}

async function saveAssignMenus(): Promise<void> {
  if (!assignRole.value || assignError.value !== '' || assignLoading.value) {
    return
  }

  const checked = menuTreeRef.value?.getCheckedKeys(false) ?? []
  const half = strictMode.value
    ? []
    : (menuTreeRef.value?.getHalfCheckedKeys() ?? [])
  const menuIds = mergeRoleMenuIds(checked, half, hiddenMenuIds.value)

  assigning.value = true
  assignError.value = ''
  try {
    await assignAdminRoleMenus(assignRole.value.id, menuIds)
    ElMessage.success('菜单授权已保存')
    assignVisible.value = false
  } catch (error) {
    assignError.value = errorMessage(error, '菜单授权保存失败')
  } finally {
    assigning.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="page">
    <div class="page-lead">
      <div>
        <h2>角色管理</h2>
        <p>
          数据源：<span class="mono">GET /api/v1/admin/roles</span>。
          后端未提供角色删除接口，因此本页不渲染删除按钮；
          分配菜单保存时会保留菜单树接口未返回的按钮授权 ID。
        </p>
      </div>
      <div class="toolbar">
        <el-button :icon="Refresh" :loading="loading" @click="load">刷新</el-button>
        <el-button type="primary" :icon="Plus" @click="openCreate">新建角色</el-button>
      </div>
    </div>

    <el-alert v-if="loadError" type="error" :closable="false" show-icon :title="loadError" />
    <el-alert v-if="actionError" type="error" :closable="false" show-icon :title="actionError" />

    <el-card shadow="never">
      <div class="toolbar filter-bar">
        <el-select
          v-model="filters.roleType"
          clearable
          placeholder="全部类型"
          style="width: 130px"
        >
          <el-option
            v-for="option in ROLE_TYPE_OPTIONS"
            :key="option.value"
            :label="option.label"
            :value="option.value"
          />
        </el-select>
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

      <el-table v-loading="loading" :data="roles" border stripe row-key="id">
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="roleCode" label="角色码" min-width="160" />
        <el-table-column prop="roleName" label="角色名称" min-width="160" />
        <el-table-column label="类型" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="tagOf(ROLE_TYPE, row.roleType)" size="small">
              {{ labelOf(ROLE_TYPE, row.roleType) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="数据范围" width="110" align="center">
          <template #default="{ row }">{{ labelOf(DATA_SCOPE, row.dataScope) }}</template>
        </el-table-column>
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
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button link type="primary" @click="openAssignMenus(row)">分配菜单</el-button>
          </template>
        </el-table-column>

        <template #empty>
          <el-empty description="当前筛选条件下没有角色。" :image-size="80" />
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
      v-model="formVisible"
      :title="editingId === null ? '新建角色' : '编辑角色'"
      width="620px"
      :close-on-click-modal="false"
    >
      <el-form label-width="100px">
        <el-form-item label="角色码" required>
          <el-input
            v-model="form.roleCode"
            maxlength="64"
            placeholder="例如 ADMIN_OPERATION"
          />
          <div class="form-hint">
            保存时统一转大写；角色码变更会影响后续签发的 JWT 角色码。
          </div>
        </el-form-item>
        <el-form-item label="角色名称" required>
          <el-input v-model="form.roleName" maxlength="64" show-word-limit />
        </el-form-item>
        <el-form-item label="角色类型" required>
          <el-select v-model="form.roleType" class="full-width">
            <el-option
              v-for="option in ROLE_TYPE_OPTIONS"
              :key="option.value"
              :label="option.label"
              :value="option.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="数据范围">
          <el-select v-model="form.dataScope" class="full-width">
            <el-option
              v-for="option in DATA_SCOPE_OPTIONS"
              :key="option.value"
              :label="option.label"
              :value="option.value"
            />
          </el-select>
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
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitRole">
          {{ editingId === null ? '创建' : '保存' }}
        </el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="assignVisible"
      :title="assignRole ? `分配菜单：${assignRole.roleName}` : '分配菜单'"
      width="640px"
      :close-on-click-modal="false"
      @opened="applyCheckedKeys"
    >
      <el-alert
        v-if="assignError"
        type="error"
        :closable="false"
        show-icon
        :title="assignError"
      />
      <template v-else>
        <el-alert
          v-if="hiddenMenuIds.length > 0"
          type="warning"
          :closable="false"
          show-icon
          :title="`已保留 ${hiddenMenuIds.length} 项菜单树未返回的授权（按钮或套餐外节点），保存不会清空它们。`"
          class="assign-alert"
        />
        <el-alert
          v-if="strictMode"
          type="info"
          :closable="false"
          show-icon
          title="该角色存在只授权父节点或非完整子树的历史数据，已切换为精确勾选模式，父子节点互不联动。"
          class="assign-alert"
        />
        <el-alert
          type="info"
          :closable="false"
          show-icon
          title="菜单树接口不返回按钮节点，本页无法新增或撤销按钮权限；保存时原按钮授权保持不变。"
          class="assign-alert"
        />

        <el-tree
          ref="menuTreeRef"
          v-loading="assignLoading"
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
              size="small"
              :type="tagOf(MENU_TYPE, data.menuType)"
              class="menu-type-tag"
            >
              {{ labelOf(MENU_TYPE, data.menuType) }}
            </el-tag>
            <span v-if="data.perms" class="mono menu-perms">{{ data.perms }}</span>
          </template>
        </el-tree>
      </template>

      <template #footer>
        <el-button @click="assignVisible = false">取消</el-button>
        <el-button
          type="primary"
          :loading="assigning"
          :disabled="assignError !== '' || assignLoading"
          @click="saveAssignMenus"
        >
          保存
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

.assign-alert {
  margin-bottom: 10px;
}

.menu-type-tag {
  margin-left: 6px;
}

.menu-perms {
  margin-left: 8px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
</style>
