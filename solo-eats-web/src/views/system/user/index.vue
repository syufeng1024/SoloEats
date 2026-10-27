<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Refresh } from '@element-plus/icons-vue'
import {
  createAdminUser,
  deleteAdminUser,
  getAdminDeptTree,
  listAdminRoles,
  listAdminUsers,
  resetAdminUserPassword,
  updateAdminUser,
  updateAdminUserStatus,
  type AdminDeptNode,
  type AdminRoleView,
  type AdminUserView,
  type SaveUserPayload,
} from '@/api/system-admin'
import {
  DATA_SCOPE,
  DATA_SCOPE_OPTIONS,
  SYSTEM_STATUS,
  SYSTEM_STATUS_OPTIONS,
  USER_TYPE,
  USER_TYPE_OPTIONS,
  labelOf,
  tagOf,
} from '@/constants/systemStatus'
import { useAuthStore } from '@/stores/auth'
import { debounce } from '@/utils/debounce'
import { formatDateTime } from '@/utils/datetime'
import { errorMessage } from '@/views/report/helpers'

const PAGE_SIZE_OPTIONS = [10, 20, 50]
const auth = useAuthStore()

const loading = ref(false)
const loadError = ref('')
const actionError = ref('')
const users = ref<AdminUserView[]>([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(20)
const filters = reactive({
  keyword: '',
  userType: undefined as number | undefined,
  status: undefined as number | undefined,
})

const deptTree = ref<AdminDeptNode[]>([])
const deptLoadError = ref('')

const formVisible = ref(false)
const editingId = ref<number | null>(null)
const originalStatus = ref(0)
const editingPhoneMask = ref<string | null>(null)
const submitting = ref(false)
const rolesLoading = ref(false)
const rolesError = ref('')
const roleOptions = ref<AdminRoleView[]>([])
let roleRequestSeq = 0

const form = reactive({
  username: '',
  password: '',
  confirmPassword: '',
  nickname: '',
  phone: '',
  userType: 1,
  merchantId: undefined as number | undefined,
  shopId: undefined as number | undefined,
  riderId: undefined as number | undefined,
  deptId: undefined as number | undefined,
  dataScope: 5,
  status: 0,
  roleIds: [] as number[],
})

const resetVisible = ref(false)
const resetTarget = ref<AdminUserView | null>(null)
const resetSubmitting = ref(false)
const passwordForm = reactive({
  newPassword: '',
  confirmPassword: '',
})

const deptNameById = computed(() => {
  const map = new Map<number, string>()
  const walk = (nodes: AdminDeptNode[]): void => {
    for (const node of nodes) {
      map.set(node.id, node.deptName)
      walk(node.children ?? [])
    }
  }
  walk(deptTree.value)
  return map
})

function deptText(row: AdminUserView): string {
  if (row.deptId === null) {
    return '—'
  }
  return deptNameById.value.get(row.deptId) ?? `#${row.deptId}`
}

function isSelf(row: AdminUserView): boolean {
  return auth.session?.userId === row.id
}

function emptyToNull(value: string): string | null {
  const trimmed = value.trim()
  return trimmed === '' ? null : trimmed
}

async function load(): Promise<void> {
  loading.value = true
  loadError.value = ''
  try {
    const result = await listAdminUsers({
      keyword: filters.keyword.trim() || undefined,
      userType: filters.userType,
      status: filters.status,
      pageNum: pageNum.value,
      pageSize: pageSize.value,
    })
    users.value = result.list
    total.value = result.total
  } catch (error) {
    users.value = []
    total.value = 0
    loadError.value = errorMessage(error, '用户列表加载失败')
  } finally {
    loading.value = false
  }
}

async function loadDepts(): Promise<void> {
  deptLoadError.value = ''
  try {
    deptTree.value = await getAdminDeptTree()
  } catch (error) {
    deptTree.value = []
    deptLoadError.value = errorMessage(error, '部门树加载失败，部门列回退显示 #ID')
  }
}

async function loadRoleOptions(userType: number): Promise<void> {
  const seq = ++roleRequestSeq
  rolesLoading.value = true
  rolesError.value = ''
  try {
    const all: AdminRoleView[] = []
    let page = 1
    let totalCount = 0
    do {
      const result = await listAdminRoles({
        roleType: userType,
        status: 0,
        pageNum: page,
        pageSize: 100,
      })
      all.push(...result.list)
      totalCount = result.total
      page += 1
    } while (all.length < totalCount && page <= 20)

    if (seq === roleRequestSeq) {
      roleOptions.value = all
    }
  } catch (error) {
    if (seq === roleRequestSeq) {
      roleOptions.value = []
      rolesError.value = errorMessage(error, '角色选项加载失败')
    }
  } finally {
    if (seq === roleRequestSeq) {
      rolesLoading.value = false
    }
  }
}

function resetForm(): void {
  Object.assign(form, {
    username: '',
    password: '',
    confirmPassword: '',
    nickname: '',
    phone: '',
    userType: 1,
    merchantId: undefined,
    shopId: undefined,
    riderId: undefined,
    deptId: undefined,
    dataScope: 5,
    status: 0,
    roleIds: [],
  })
  editingPhoneMask.value = null
  rolesError.value = ''
}

function openCreate(): void {
  editingId.value = null
  originalStatus.value = 0
  resetForm()
  formVisible.value = true
  void loadRoleOptions(form.userType)
}

function openEdit(row: AdminUserView): void {
  editingId.value = row.id
  originalStatus.value = row.status
  editingPhoneMask.value = row.phone
  Object.assign(form, {
    username: row.username,
    password: '',
    confirmPassword: '',
    nickname: row.nickname ?? '',
    phone: '',
    userType: row.userType,
    merchantId: row.merchantId ?? undefined,
    shopId: row.shopId ?? undefined,
    riderId: row.riderId ?? undefined,
    deptId: row.deptId ?? undefined,
    dataScope: row.dataScope,
    status: row.status,
    roleIds: [],
  })
  roleOptions.value = []
  rolesError.value = ''
  formVisible.value = true
}

function handleUserTypeChange(): void {
  form.merchantId = undefined
  form.shopId = undefined
  form.riderId = undefined
  form.roleIds = []
  void loadRoleOptions(form.userType)
}

function validateForm(): string | null {
  const username = form.username.trim()
  if (editingId.value === null) {
    if (username === '') {
      return '用户名不能为空'
    }
    if (!/^[A-Za-z0-9_.@-]{3,64}$/.test(username)) {
      return '用户名只能包含字母、数字、_ . @ -，长度 3~64'
    }
    if (form.password.length < 8 || form.password.length > 64) {
      return '密码长度必须为 8~64 位'
    }
    if (form.password !== form.confirmPassword) {
      return '两次输入的密码不一致'
    }
  }

  if (form.nickname.trim().length > 64) {
    return '昵称不能超过 64 个字符'
  }

  const phone = form.phone.trim()
  if (phone !== '') {
    if (phone.includes('*')) {
      return '手机号已脱敏，请不要把掩码写回；留空表示保持原值'
    }
    if (!/^1[3-9]\d{9}$/.test(phone)) {
      return '手机号格式不正确'
    }
  }

  if (form.userType !== 1 && form.userType !== 2 && form.userType !== 3) {
    return '用户类型不正确'
  }
  if (form.userType === 2 && form.merchantId === undefined) {
    return '商家账号必须填写商家 ID'
  }
  if (form.userType === 3 && form.riderId === undefined) {
    return '骑手账号必须填写骑手 ID'
  }
  if (form.merchantId !== undefined && (!Number.isInteger(form.merchantId) || form.merchantId <= 0)) {
    return '商家 ID 必须是正整数'
  }
  if (form.shopId !== undefined && (!Number.isInteger(form.shopId) || form.shopId <= 0)) {
    return '门店 ID 必须是正整数'
  }
  if (form.riderId !== undefined && (!Number.isInteger(form.riderId) || form.riderId <= 0)) {
    return '骑手 ID 必须是正整数'
  }
  if (form.deptId !== undefined && (!Number.isInteger(form.deptId) || form.deptId <= 0)) {
    return '部门不正确'
  }
  if (![1, 2, 3, 4, 5].includes(form.dataScope)) {
    return '数据范围不正确'
  }
  if (form.status !== 0 && form.status !== 1) {
    return '状态不正确'
  }

  if (editingId.value === null) {
    const allowedRoleIds = new Set(roleOptions.value.map((role) => role.id))
    if (form.roleIds.some((roleId) => !allowedRoleIds.has(roleId))) {
      return '角色选项已变化，请重新选择'
    }
  }

  return null
}

function buildCreatePayload(): SaveUserPayload {
  return {
    username: form.username.trim(),
    password: form.password,
    nickname: emptyToNull(form.nickname),
    phone: emptyToNull(form.phone),
    userType: form.userType,
    merchantId: form.userType === 2 ? form.merchantId ?? null : null,
    shopId: form.userType === 2 ? form.shopId ?? null : null,
    riderId: form.userType === 3 ? form.riderId ?? null : null,
    deptId: form.deptId ?? null,
    dataScope: form.dataScope,
    roleIds: [...form.roleIds],
  }
}

/**
 * 编辑时用户名/密码被后端忽略，角色由 null 保持不动；
 * phone/deptId 为 null 时后端保留原值，避免把掩码手机号写回。
 */
function buildUpdatePayload(): SaveUserPayload {
  return {
    nickname: form.nickname.trim(),
    phone: emptyToNull(form.phone),
    userType: form.userType,
    merchantId: form.userType === 2 ? form.merchantId ?? null : null,
    shopId: form.userType === 2 ? form.shopId ?? null : null,
    riderId: form.userType === 3 ? form.riderId ?? null : null,
    deptId: form.deptId ?? null,
    dataScope: form.dataScope,
    roleIds: null,
  }
}

async function syncStatus(id: number, target: number, current: number): Promise<void> {
  if (target === current) {
    return
  }
  await updateAdminUserStatus(id, target)
}

async function submitUser(): Promise<void> {
  const invalid = validateForm()
  if (invalid !== null) {
    ElMessage.warning(invalid)
    return
  }

  submitting.value = true
  actionError.value = ''
  let statusSyncError = ''
  try {
    if (editingId.value === null) {
      const created = await createAdminUser(buildCreatePayload())
      try {
        await syncStatus(created.id, form.status, 0)
      } catch (error) {
        statusSyncError = errorMessage(error, '状态设置失败')
      }
      ElMessage.success('用户已创建')
    } else if (editingId.value !== null) {
      await updateAdminUser(editingId.value, buildUpdatePayload())
      try {
        await syncStatus(editingId.value, form.status, originalStatus.value)
      } catch (error) {
        statusSyncError = errorMessage(error, '状态设置失败')
      }
      ElMessage.success('用户已更新')
    }
    formVisible.value = false
    await load()
  } catch (error) {
    actionError.value = errorMessage(error, '用户保存失败')
  } finally {
    submitting.value = false
    if (statusSyncError !== '') {
      actionError.value = `用户已保存，但${statusSyncError}`
    }
  }
}

async function toggleStatus(row: AdminUserView): Promise<void> {
  const target = row.status === 0 ? 1 : 0
  const actionText = target === 1 ? '停用' : '启用'
  try {
    await ElMessageBox.confirm(
      `确认${actionText}用户「${row.username}」？`,
      `${actionText}用户`,
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
    await updateAdminUserStatus(row.id, target)
    ElMessage.success(`用户已${actionText}`)
    await load()
  } catch (error) {
    actionError.value = errorMessage(error, `${actionText}失败`)
  }
}

function openReset(row: AdminUserView): void {
  resetTarget.value = row
  passwordForm.newPassword = ''
  passwordForm.confirmPassword = ''
  resetVisible.value = true
}

async function submitReset(): Promise<void> {
  const target = resetTarget.value
  if (!target) {
    return
  }
  if (passwordForm.newPassword.length < 8 || passwordForm.newPassword.length > 64) {
    ElMessage.warning('新密码长度必须为 8~64 位')
    return
  }
  if (passwordForm.newPassword !== passwordForm.confirmPassword) {
    ElMessage.warning('两次输入的新密码不一致')
    return
  }

  resetSubmitting.value = true
  actionError.value = ''
  try {
    await resetAdminUserPassword(target.id, passwordForm.newPassword)
    ElMessage.success('密码已重置')
    resetVisible.value = false
  } catch (error) {
    actionError.value = errorMessage(error, '密码重置失败')
  } finally {
    resetSubmitting.value = false
  }
}

async function confirmDelete(row: AdminUserView): Promise<void> {
  try {
    await ElMessageBox.confirm(
      `确认删除用户「${row.username}」？删除后同一用户名仍占用唯一键。`,
      '删除用户',
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
    await deleteAdminUser(row.id)
    ElMessage.success('用户已删除')
    await load()
  } catch (error) {
    actionError.value = errorMessage(error, '用户删除失败')
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
  filters.userType = undefined
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

onMounted(() => {
  void load()
  void loadDepts()
})
</script>

<template>
  <div class="page">
    <div class="page-lead">
      <div>
        <h2>用户管理</h2>
        <p>
          数据源：<span class="mono">GET /api/v1/admin/users</span>。
          关键字只匹配用户名/昵称；手机号由后端掩码返回，编辑时留空表示保持原值；
          编辑不修改用户名、密码与角色，角色仅在新建时分配。
        </p>
      </div>
      <div class="toolbar">
        <el-button :icon="Refresh" :loading="loading" @click="load">刷新</el-button>
        <el-button type="primary" :icon="Plus" @click="openCreate">新建用户</el-button>
      </div>
    </div>

    <el-alert v-if="loadError" type="error" :closable="false" show-icon :title="loadError" />
    <el-alert v-if="actionError" type="error" :closable="false" show-icon :title="actionError" />
    <el-alert
      v-if="deptLoadError"
      type="info"
      :closable="false"
      show-icon
      :title="deptLoadError"
    />

    <el-card shadow="never">
      <div class="toolbar filter-bar">
        <el-input
          v-model="filters.keyword"
          clearable
          placeholder="用户名/昵称"
          style="width: 220px"
          @input="runKeywordSearch"
          @keyup.enter="search"
        />
        <el-select
          v-model="filters.userType"
          clearable
          placeholder="全部类型"
          style="width: 130px"
        >
          <el-option
            v-for="option in USER_TYPE_OPTIONS"
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

      <el-table v-loading="loading" :data="users" border stripe row-key="id">
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="username" label="用户名" min-width="130" />
        <el-table-column label="昵称" min-width="120">
          <template #default="{ row }">{{ row.nickname || '—' }}</template>
        </el-table-column>
        <el-table-column label="手机" width="130">
          <template #default="{ row }">{{ row.phone || '—' }}</template>
        </el-table-column>
        <el-table-column label="用户类型" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="tagOf(USER_TYPE, row.userType)" size="small">
              {{ labelOf(USER_TYPE, row.userType) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="部门" min-width="140">
          <template #default="{ row }">{{ deptText(row) }}</template>
        </el-table-column>
        <el-table-column label="数据范围" width="100" align="center">
          <template #default="{ row }">{{ labelOf(DATA_SCOPE, row.dataScope) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="85" align="center">
          <template #default="{ row }">
            <el-tag :type="tagOf(SYSTEM_STATUS, row.status)" size="small">
              {{ labelOf(SYSTEM_STATUS, row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="最后登录" min-width="165">
          <template #default="{ row }">{{ formatDateTime(row.lastLoginAt) }}</template>
        </el-table-column>
        <el-table-column label="创建时间" min-width="165">
          <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="280" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button
              link
              type="primary"
              :disabled="isSelf(row)"
              @click="toggleStatus(row)"
            >
              {{ row.status === 0 ? '停用' : '启用' }}
            </el-button>
            <el-button link type="primary" @click="openReset(row)">重置密码</el-button>
            <el-button
              link
              type="danger"
              :disabled="isSelf(row)"
              @click="confirmDelete(row)"
            >
              删除
            </el-button>
          </template>
        </el-table-column>

        <template #empty>
          <el-empty description="当前筛选条件下没有用户。" :image-size="80" />
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
      :title="editingId === null ? '新建用户' : '编辑用户'"
      width="720px"
      :close-on-click-modal="false"
    >
      <el-form label-width="116px">
        <el-form-item label="用户名" required>
          <el-input
            v-model="form.username"
            :disabled="editingId !== null"
            maxlength="64"
            placeholder="3~64 位字母、数字、_ . @ -"
          />
          <div v-if="editingId !== null" class="form-hint">
            更新接口忽略 username，用户名创建后不可修改。
          </div>
        </el-form-item>

        <template v-if="editingId === null">
          <el-form-item label="密码" required>
            <el-input
              v-model="form.password"
              type="password"
              show-password
              maxlength="64"
              placeholder="8~64 位"
            />
          </el-form-item>
          <el-form-item label="确认密码" required>
            <el-input
              v-model="form.confirmPassword"
              type="password"
              show-password
              maxlength="64"
            />
          </el-form-item>
        </template>
        <el-alert
          v-else
          type="info"
          :closable="false"
          show-icon
          title="编辑不修改密码；如需重置请使用列表中的「重置密码」。"
          class="form-block"
        />

        <el-form-item label="昵称">
          <el-input v-model="form.nickname" maxlength="64" />
        </el-form-item>

        <el-form-item label="手机号">
          <el-input
            v-model="form.phone"
            maxlength="32"
            :placeholder="
              editingPhoneMask
                ? `当前 ${editingPhoneMask}，留空保持不变`
                : '选填'
            "
          />
          <div v-if="editingId !== null" class="form-hint">
            后端只返回掩码手机号；留空提交 null，原手机号保持不变。
          </div>
        </el-form-item>

        <el-form-item label="用户类型" required>
          <el-select
            v-model="form.userType"
            :disabled="editingId !== null"
            class="full-width"
            @change="handleUserTypeChange"
          >
            <el-option
              v-for="option in USER_TYPE_OPTIONS"
              :key="option.value"
              :label="option.label"
              :value="option.value"
            />
          </el-select>
        </el-form-item>

        <el-form-item v-if="form.userType === 2" label="商家 ID" required>
          <el-input-number
            v-model="form.merchantId"
            :min="1"
            :precision="0"
            :controls="false"
            class="full-width"
          />
        </el-form-item>
        <el-form-item v-if="form.userType === 2" label="门店 ID">
          <el-input-number
            v-model="form.shopId"
            :min="1"
            :precision="0"
            :controls="false"
            class="full-width"
          />
        </el-form-item>
        <el-form-item v-if="form.userType === 3" label="骑手 ID" required>
          <el-input-number
            v-model="form.riderId"
            :min="1"
            :precision="0"
            :controls="false"
            class="full-width"
          />
        </el-form-item>

        <el-form-item label="部门">
          <el-tree-select
            v-model="form.deptId"
            :data="deptTree"
            node-key="id"
            check-strictly
            :render-after-expand="false"
            :props="{ label: 'deptName', children: 'children' }"
            placeholder="不设置部门"
            class="full-width"
          />
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

        <el-form-item v-if="editingId === null" label="角色">
          <el-select
            v-model="form.roleIds"
            multiple
            filterable
            class="full-width"
            :loading="rolesLoading"
            placeholder="可多选"
          >
            <el-option
              v-for="role in roleOptions"
              :key="role.id"
              :label="`${role.roleName}（${role.roleCode}）`"
              :value="role.id"
            />
          </el-select>
          <el-alert
            v-if="rolesError"
            type="warning"
            :closable="false"
            show-icon
            :title="rolesError"
            class="form-block"
          />
        </el-form-item>
        <el-alert
          v-else
          type="info"
          :closable="false"
          show-icon
          title="后端未提供用户当前角色查询接口；编辑固定发送 roleIds=null，保持原角色不变。"
          class="form-block"
        />

        <el-form-item label="状态">
          <el-select v-model="form.status" class="full-width">
            <el-option
              v-for="option in SYSTEM_STATUS_OPTIONS"
              :key="option.value"
              :label="option.label"
              :value="option.value"
            />
          </el-select>
          <div class="form-hint">
            状态不在保存 DTO 内；保存成功后通过 PUT /users/{id}/status 单独同步。
          </div>
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitUser">
          {{ editingId === null ? '创建' : '保存' }}
        </el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="resetVisible"
      :title="resetTarget ? `重置密码：${resetTarget.username}` : '重置密码'"
      width="460px"
      :close-on-click-modal="false"
    >
      <el-form label-width="100px">
        <el-form-item label="新密码" required>
          <el-input
            v-model="passwordForm.newPassword"
            type="password"
            show-password
            maxlength="64"
            placeholder="8~64 位"
          />
        </el-form-item>
        <el-form-item label="确认密码" required>
          <el-input
            v-model="passwordForm.confirmPassword"
            type="password"
            show-password
            maxlength="64"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="resetVisible = false">取消</el-button>
        <el-button type="primary" :loading="resetSubmitting" @click="submitReset">
          确认重置
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

.form-block {
  margin-bottom: 18px;
}
</style>
