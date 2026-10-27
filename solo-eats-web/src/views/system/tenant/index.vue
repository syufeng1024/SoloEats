<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Refresh } from '@element-plus/icons-vue'
import {
  createAdminTenant,
  listAdminPackages,
  listAdminTenants,
  updateAdminTenant,
  updateAdminTenantStatus,
  type AdminPackageView,
  type AdminTenantView,
  type SaveTenantPayload,
} from '@/api/system-admin'
import {
  TENANT_STATUS,
  TENANT_STATUS_OPTIONS,
  TENANT_TYPE,
  TENANT_TYPE_OPTIONS,
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
const tenants = ref<AdminTenantView[]>([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(20)
const filters = reactive({
  keyword: '',
  tenantType: undefined as number | undefined,
  status: undefined as number | undefined,
})

const packages = ref<AdminPackageView[]>([])
const packagesError = ref('')
const packageNameById = computed(() => {
  const map = new Map<number, string>()
  for (const item of packages.value) {
    map.set(item.id, item.packageName)
  }
  return map
})

const dialogVisible = ref(false)
const editingId = ref<number | null>(null)
const originalTenantCode = ref('')
const originalContactPhone = ref<string | null>(null)
const phoneTouched = ref(false)
const submitting = ref(false)

const form = reactive({
  tenantCode: '',
  tenantName: '',
  tenantType: 1,
  merchantId: undefined as number | undefined,
  packageId: undefined as number | undefined,
  expireAt: '',
  contactName: '',
  contactPhone: '',
})

function packageText(row: AdminTenantView): string {
  if (row.packageId === null) {
    return '—'
  }
  return packageNameById.value.get(row.packageId) ?? `#${row.packageId}`
}

function statusActionLabel(row: AdminTenantView): string {
  if (row.status === 2) {
    return '续期'
  }
  return row.status === 0 ? '停用' : '启用'
}

function optionalId(value: number | undefined): number | null {
  return typeof value === 'number' && Number.isInteger(value) && value > 0
    ? value
    : null
}

async function load(): Promise<void> {
  loading.value = true
  loadError.value = ''
  try {
    const result = await listAdminTenants({
      keyword: filters.keyword.trim() || undefined,
      tenantType: filters.tenantType,
      status: filters.status,
      pageNum: pageNum.value,
      pageSize: pageSize.value,
    })
    tenants.value = result.list
    total.value = result.total
  } catch (error) {
    tenants.value = []
    total.value = 0
    loadError.value = errorMessage(error, '租户列表加载失败')
  } finally {
    loading.value = false
  }
}

async function loadPackages(): Promise<void> {
  packagesError.value = ''
  try {
    packages.value = await listAdminPackages()
  } catch (error) {
    packages.value = []
    packagesError.value = errorMessage(error, '套餐列表加载失败，套餐列回退显示 #ID')
  }
}

function resetForm(): void {
  Object.assign(form, {
    tenantCode: '',
    tenantName: '',
    tenantType: 1,
    merchantId: undefined,
    packageId: undefined,
    expireAt: '',
    contactName: '',
    contactPhone: '',
  })
  originalContactPhone.value = null
  phoneTouched.value = false
}

function openCreate(): void {
  editingId.value = null
  originalTenantCode.value = ''
  resetForm()
  dialogVisible.value = true
}

function openEdit(row: AdminTenantView): void {
  editingId.value = row.id
  originalTenantCode.value = row.tenantCode
  originalContactPhone.value = row.contactPhone
  phoneTouched.value = false
  Object.assign(form, {
    tenantCode: row.tenantCode,
    tenantName: row.tenantName,
    tenantType: row.tenantType,
    merchantId: row.merchantId ?? undefined,
    packageId: row.packageId ?? undefined,
    expireAt: row.expireAt ? formatDateTime(row.expireAt) : '',
    contactName: row.contactName ?? '',
    contactPhone: '',
  })
  dialogVisible.value = true
}

function handleTenantTypeChange(): void {
  if (form.tenantType !== 2) {
    form.merchantId = undefined
  }
}

function validateForm(): string | null {
  const tenantCode = form.tenantCode.trim()
  if (editingId.value === null) {
    if (!/^[A-Za-z0-9_-]{2,64}$/.test(tenantCode)) {
      return '租户编码只能包含字母、数字、下划线、中划线，长度 2~64'
    }
  }

  const tenantName = form.tenantName.trim()
  if (tenantName === '') {
    return '租户名称不能为空'
  }
  if (tenantName.length > 128) {
    return '租户名称不能超过 128 个字符'
  }

  if (form.tenantType !== 1 && form.tenantType !== 2) {
    return '租户类型不正确'
  }
  if (form.tenantType === 2 && optionalId(form.merchantId) === null) {
    return '商家租户必须填写正整数商家 ID'
  }

  if (form.packageId !== undefined && optionalId(form.packageId) === null) {
    return '套餐 ID 必须是正整数'
  }

  const expireAt = form.expireAt.trim()
  if (expireAt !== '') {
    const expireTime = new Date(expireAt.replace(' ', 'T')).getTime()
    if (!Number.isFinite(expireTime)) {
      return '到期时间格式不正确'
    }
    if (expireTime <= Date.now()) {
      return '到期时间必须晚于当前时间；已过期租户请选择未来时间续期'
    }
  }

  if (form.contactName.trim().length > 64) {
    return '联系人不能超过 64 个字符'
  }

  const phone = form.contactPhone.trim()
  if (phone.length > 32) {
    return '联系电话不能超过 32 个字符'
  }
  if (phone.includes('*')) {
    return '联系电话已脱敏，请不要把掩码写回；留空表示保持原值'
  }
  if (phone !== '' && !/^[0-9+\-\s]{6,32}$/.test(phone)) {
    return '联系电话只能包含数字、+、-、空格，长度 6~32'
  }

  return null
}

function buildPayload(): SaveTenantPayload {
  return {
    tenantCode:
      editingId.value === null ? form.tenantCode.trim() : originalTenantCode.value,
    tenantName: form.tenantName.trim(),
    tenantType: form.tenantType,
    merchantId:
      form.tenantType === 2 ? optionalId(form.merchantId) : null,
    packageId: optionalId(form.packageId),
    expireAt: form.expireAt.trim() || null,
    contactName: form.contactName.trim(),
    contactPhone:
      editingId.value !== null && !phoneTouched.value
        ? null
        : form.contactPhone.trim() || '',
  }
}

async function submitTenant(): Promise<void> {
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
      await createAdminTenant(payload)
      ElMessage.success('租户已创建')
    } else {
      await updateAdminTenant(editingId.value, payload)
      ElMessage.success('租户已更新')
    }
    dialogVisible.value = false
    await load()
  } catch (error) {
    actionError.value = errorMessage(error, '租户保存失败')
  } finally {
    submitting.value = false
  }
}

async function toggleStatus(row: AdminTenantView): Promise<void> {
  if (row.status === 2) {
    ElMessage.warning('租户已过期，请编辑到期时间续期')
    openEdit(row)
    return
  }

  const target = row.status === 0 ? 1 : 0
  const actionText = target === 1 ? '停用' : '启用'
  try {
    await ElMessageBox.confirm(
      `确认${actionText}租户「${row.tenantName}」？`,
      `${actionText}租户`,
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
    await updateAdminTenantStatus(row.id, target)
    ElMessage.success(`租户已${actionText}`)
    await load()
  } catch (error) {
    const message = errorMessage(error, `${actionText}失败`)
    actionError.value = message.includes('已过期')
      ? `${message}。请编辑到期时间续期后再启用。`
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
  filters.tenantType = undefined
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
  void loadPackages()
})
</script>

<template>
  <div class="page">
    <div class="page-lead">
      <div>
        <h2>租户管理</h2>
        <p>
          数据源：<span class="mono">GET /api/v1/admin/tenants</span>。
          后端未提供删除接口；状态 2 为到期时间派生的过期态，人工启停只接受 0/1。
          联系电话由后端掩码返回，编辑时留空表示保持原值。套餐名称由
          <span class="mono">/packages</span> 本地映射。
        </p>
      </div>
      <div class="toolbar">
        <el-button :icon="Refresh" :loading="loading" @click="load">刷新</el-button>
        <el-button type="primary" :icon="Plus" @click="openCreate">新建租户</el-button>
      </div>
    </div>

    <el-alert v-if="loadError" type="error" :closable="false" show-icon :title="loadError" />
    <el-alert v-if="actionError" type="error" :closable="false" show-icon :title="actionError" />
    <el-alert
      v-if="packagesError"
      type="info"
      :closable="false"
      show-icon
      :title="packagesError"
    />

    <el-card shadow="never">
      <div class="toolbar filter-bar">
        <el-input
          v-model="filters.keyword"
          clearable
          placeholder="租户编码/名称"
          style="width: 220px"
          @input="runKeywordSearch"
          @keyup.enter="search"
        />
        <el-select
          v-model="filters.tenantType"
          clearable
          placeholder="全部类型"
          style="width: 130px"
        >
          <el-option
            v-for="option in TENANT_TYPE_OPTIONS"
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
            v-for="option in TENANT_STATUS_OPTIONS"
            :key="option.value"
            :label="option.label"
            :value="option.value"
          />
        </el-select>
        <el-button type="primary" @click="search">查询</el-button>
        <el-button @click="resetFilters">重置</el-button>
      </div>

      <el-table v-loading="loading" :data="tenants" border stripe row-key="id">
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="tenantCode" label="租户编码" min-width="150">
          <template #default="{ row }">
            <span class="mono">{{ row.tenantCode }}</span>
          </template>
        </el-table-column>
        <el-table-column
          prop="tenantName"
          label="租户名称"
          min-width="180"
          show-overflow-tooltip
        />
        <el-table-column label="类型" width="85" align="center">
          <template #default="{ row }">
            <el-tag :type="tagOf(TENANT_TYPE, row.tenantType)" size="small">
              {{ labelOf(TENANT_TYPE, row.tenantType) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="商家 ID" width="95">
          <template #default="{ row }">{{ row.merchantId ?? '—' }}</template>
        </el-table-column>
        <el-table-column label="套餐" min-width="150" show-overflow-tooltip>
          <template #default="{ row }">{{ packageText(row) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="tagOf(TENANT_STATUS, row.status)" size="small">
              {{ labelOf(TENANT_STATUS, row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="到期时间" min-width="165">
          <template #default="{ row }">{{ formatDateTime(row.expireAt) }}</template>
        </el-table-column>
        <el-table-column label="联系人" width="110">
          <template #default="{ row }">{{ row.contactName || '—' }}</template>
        </el-table-column>
        <el-table-column label="联系电话" width="130">
          <template #default="{ row }">{{ row.contactPhone || '—' }}</template>
        </el-table-column>
        <el-table-column label="创建时间" min-width="165">
          <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="更新时间" min-width="165">
          <template #default="{ row }">{{ formatDateTime(row.updatedAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button link type="primary" @click="toggleStatus(row)">
              {{ statusActionLabel(row) }}
            </el-button>
          </template>
        </el-table-column>

        <template #empty>
          <el-empty description="当前筛选条件下没有租户。" :image-size="80" />
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
      :title="editingId === null ? '新建租户' : '编辑租户'"
      width="680px"
      :close-on-click-modal="false"
    >
      <el-form label-width="104px">
        <el-form-item label="租户编码" required>
          <el-input
            v-model="form.tenantCode"
            :disabled="editingId !== null"
            maxlength="64"
            placeholder="例如 PLATFORM_STORE"
          />
          <div v-if="editingId !== null" class="form-hint">
            更新接口忽略 tenantCode，编码创建后不可修改。
          </div>
        </el-form-item>
        <el-form-item label="租户名称" required>
          <el-input v-model="form.tenantName" maxlength="128" show-word-limit />
        </el-form-item>
        <el-form-item label="租户类型" required>
          <el-select
            v-model="form.tenantType"
            :disabled="editingId !== null"
            class="full-width"
            @change="handleTenantTypeChange"
          >
            <el-option
              v-for="option in TENANT_TYPE_OPTIONS"
              :key="option.value"
              :label="option.label"
              :value="option.value"
            />
          </el-select>
          <div v-if="editingId !== null" class="form-hint">
            类型编辑后只读：后端以 null 表示保持 merchantId 原值，商家租户改平台会被拒绝。
          </div>
        </el-form-item>
        <el-form-item v-if="form.tenantType === 2" label="商家 ID" required>
          <el-input-number
            v-model="form.merchantId"
            :min="1"
            :precision="0"
            :controls="false"
            class="full-width"
          />
        </el-form-item>
        <el-form-item label="套餐">
          <el-select
            v-if="packagesError === ''"
            v-model="form.packageId"
            placeholder="不绑定套餐"
            class="full-width"
          >
            <el-option
              v-for="item in packages"
              :key="item.id"
              :label="`${item.packageName}（${item.packageCode}）`"
              :value="item.id"
            />
          </el-select>
          <el-input-number
            v-else
            v-model="form.packageId"
            :min="1"
            :precision="0"
            :controls="false"
            class="full-width"
          />
          <div class="form-hint">
            编辑时留空提交 null，后端保持原套餐；当前接口无法解绑套餐。
          </div>
        </el-form-item>
        <el-form-item label="到期时间">
          <el-date-picker
            v-model="form.expireAt"
            type="datetime"
            value-format="YYYY-MM-DD HH:mm:ss"
            placeholder="留空表示长期有效"
            class="full-width"
          />
          <div class="form-hint">
            编辑时留空提交 null，后端保持原到期时间；已过期租户请选择未来时间续期。
          </div>
        </el-form-item>
        <el-form-item label="联系人">
          <el-input v-model="form.contactName" maxlength="64" />
        </el-form-item>
        <el-form-item label="联系电话">
          <el-input
            v-model="form.contactPhone"
            maxlength="32"
            :placeholder="
              originalContactPhone
                ? `当前 ${originalContactPhone}，留空保持不变`
                : '选填'
            "
            @input="phoneTouched = true"
          />
          <div v-if="editingId !== null" class="form-hint">
            后端只返回掩码号码；未修改时提交 null，原号码保持不变。
          </div>
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitTenant">
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
