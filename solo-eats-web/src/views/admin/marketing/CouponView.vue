<script setup lang="ts">
import { onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Refresh, Search, Tickets } from '@element-plus/icons-vue'
import {
  createCoupon,
  grantCoupon,
  listCouponRecords,
  listCoupons,
  updateCoupon,
  updateCouponStatus,
  type CouponTemplatePayload,
} from '@/api/marketing'
import type { CouponGrantRecordView, CouponTemplateView } from '@/api/types'
import {
  COUPON_BEARER_LABELS,
  COUPON_BEARER_OPTIONS,
  COUPON_STATUS_MAP,
  COUPON_STATUS_OPTIONS,
  COUPON_TYPE_LABELS,
  COUPON_TYPE_OPTIONS,
  USER_COUPON_STATUS_MAP,
  USER_COUPON_STATUS_OPTIONS,
  statusMeta,
} from '@/constants/businessStatus'
import { formatDateTime } from '@/utils/datetime'
import { debounce } from '@/utils/debounce'
import { centsToYuan, formatYuan, yuanToFen } from '@/utils/money'

const PAGE_SIZE_OPTIONS = [10, 20, 50]
const MAX_GRANT_USERS = 500

const loading = ref(false)
const loadError = ref('')
const coupons = ref<CouponTemplateView[]>([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(10)
const appliedName = ref('')

const filters = reactive({
  name: '',
  status: undefined as number | undefined,
  couponType: undefined as number | undefined,
  bearer: undefined as number | undefined,
})

watch(
  () => filters.name,
  debounce((value: string) => {
    appliedName.value = value
    pageNum.value = 1
    void load()
  }, 300),
)

const formVisible = ref(false)
const editingId = ref<number | null>(null)
const submitting = ref(false)

const form = reactive({
  name: '',
  couponType: 1,
  thresholdYuan: 0,
  discountYuan: 0,
  discountRate: 1,
  bearer: 1,
  totalQty: 1000,
  issuedQty: 0,
  validRange: null as string[] | null,
  validDays: 30 as number | null,
})

const grantVisible = ref(false)
const grantTarget = ref<CouponTemplateView | null>(null)
const grantText = ref('')
const granting = ref(false)

const recordsVisible = ref(false)
const recordsTarget = ref<CouponTemplateView | null>(null)
const recordsLoading = ref(false)
const records = ref<CouponGrantRecordView[]>([])
const recordsTotal = ref(0)
const recordsPageNum = ref(1)
const recordsPageSize = ref(10)
const recordsStatus = ref<number | undefined>(undefined)

async function load(): Promise<void> {
  loading.value = true
  loadError.value = ''
  try {
    const result = await listCoupons({
      name: appliedName.value.trim() || undefined,
      status: typeof filters.status === 'number' ? filters.status : undefined,
      couponType: typeof filters.couponType === 'number' ? filters.couponType : undefined,
      bearer: typeof filters.bearer === 'number' ? filters.bearer : undefined,
      pageNum: pageNum.value,
      pageSize: pageSize.value,
    })
    coupons.value = result.list
    total.value = result.total
  } catch (error) {
    loadError.value = error instanceof Error ? error.message : '券模板加载失败'
  } finally {
    loading.value = false
  }
}

function search(): void {
  pageNum.value = 1
  void load()
}

function reset(): void {
  filters.name = ''
  filters.status = undefined
  filters.couponType = undefined
  filters.bearer = undefined
  appliedName.value = ''
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
  Object.assign(form, {
    name: '',
    couponType: 1,
    thresholdYuan: 0,
    discountYuan: 0,
    discountRate: 1,
    bearer: 1,
    totalQty: 1000,
    issuedQty: 0,
    validRange: null,
    validDays: 30,
  })
  formVisible.value = true
}

function openEdit(row: CouponTemplateView): void {
  editingId.value = row.id
  Object.assign(form, {
    name: row.name,
    couponType: row.couponType,
    thresholdYuan: centsToYuan(row.thresholdAmount),
    discountYuan: centsToYuan(row.discountAmount),
    discountRate: row.discountRate === null ? 1 : Number(row.discountRate),
    bearer: row.bearer,
    totalQty: row.totalQty,
    issuedQty: row.issuedQty,
    validRange:
      row.validStart && row.validEnd ? [row.validStart, row.validEnd] : null,
    validDays: row.validDays,
  })
  formVisible.value = true
}

function validateForm(): string | null {
  if (!form.name.trim()) {
    return '券名称不能为空'
  }
  if (form.name.trim().length > 64) {
    return '券名称不能超过 64 个字符'
  }
  if (![1, 2, 3].includes(form.couponType)) {
    return '券类型只能是 1 满减 / 2 折扣 / 3 无门槛'
  }
  if (form.couponType === 2) {
    if (!(form.discountRate > 0 && form.discountRate <= 1)) {
      return '折扣率必须在 (0, 1] 区间，例如 0.9 表示 9 折'
    }
  } else if (form.discountYuan <= 0) {
    return '满减券/无门槛券的优惠金额必须大于 0'
  }
  if (form.thresholdYuan < 0 || form.discountYuan < 0) {
    return '金额字段不能为负数'
  }
  if (!Number.isInteger(form.totalQty) || form.totalQty < 0) {
    return '发放总量必须是非负整数'
  }
  if (editingId.value !== null && form.totalQty < form.issuedQty) {
    return `发放总量不能小于已发放数量 ${form.issuedQty}`
  }
  if (form.validDays !== null && form.validDays !== undefined && form.validDays <= 0) {
    return '领取后有效天数必须大于 0'
  }
  const range = Array.isArray(form.validRange) ? form.validRange : []
  if (range.length === 2 && !(new Date(range[1]).getTime() > new Date(range[0]).getTime())) {
    return '有效期结束时间必须晚于开始时间'
  }
  if ((form.validDays === null || form.validDays === undefined) && !range[1]) {
    return '必须配置「领取后有效天数」或「固定结束时间」，否则无法计算券过期时间'
  }
  return null
}

function toPayload(): CouponTemplatePayload {
  const range = Array.isArray(form.validRange) ? form.validRange : []
  return {
    name: form.name.trim(),
    couponType: editingId.value === null ? form.couponType : undefined,
    thresholdAmount: yuanToFen(form.thresholdYuan),
    discountAmount: form.couponType === 2 ? 0 : yuanToFen(form.discountYuan),
    discountRate: form.couponType === 2 ? form.discountRate : null,
    bearer: form.bearer,
    totalQty: form.totalQty,
    validStart: range[0] ?? null,
    validEnd: range[1] ?? null,
    validDays: form.validDays ?? null,
  }
}

async function submitForm(): Promise<void> {
  const invalid = validateForm()
  if (invalid) {
    ElMessage.warning(invalid)
    return
  }

  submitting.value = true
  try {
    if (editingId.value === null) {
      await createCoupon(toPayload())
      ElMessage.success('券模板已创建，初始状态为启用')
    } else {
      await updateCoupon(editingId.value, toPayload())
      ElMessage.success('券模板已更新')
    }
    formVisible.value = false
    await load()
  } catch {
    // 拦截器已提示
  } finally {
    submitting.value = false
  }
}

async function toggleStatus(row: CouponTemplateView): Promise<void> {
  const target = row.status === 0 ? 1 : 0
  const action = target === 1 ? '停用' : '启用'
  try {
    await ElMessageBox.confirm(
      `将券模板「${row.name}」${action}。停用后定向发券会返回 40212。`,
      `确认${action}`,
      { confirmButtonText: action, cancelButtonText: '取消', type: 'warning' },
    )
  } catch {
    return
  }

  try {
    await updateCouponStatus(row.id, target)
    ElMessage.success(`券模板已${action}`)
    await load()
  } catch {
    // 拦截器已提示
  }
}

function openGrant(row: CouponTemplateView): void {
  grantTarget.value = row
  grantText.value = ''
  grantVisible.value = true
}

function parseUserIds(text: string): { ids: number[]; error: string | null } {
  const tokens = text.split(/[\s,，;；]+/).filter(Boolean)
  if (tokens.length === 0) {
    return { ids: [], error: '请至少填写一个用户 ID' }
  }
  const ids: number[] = []
  const seen = new Set<number>()
  for (const token of tokens) {
    if (!/^[1-9]\d*$/.test(token)) {
      return { ids: [], error: `用户 ID「${token}」不是正整数` }
    }
    const id = Number(token)
    if (!Number.isSafeInteger(id)) {
      return { ids: [], error: `用户 ID「${token}」超出安全整数范围` }
    }
    if (!seen.has(id)) {
      seen.add(id)
      ids.push(id)
    }
  }
  if (ids.length > MAX_GRANT_USERS) {
    return { ids: [], error: `去重后 ${ids.length} 人，单次发券不能超过 ${MAX_GRANT_USERS} 人` }
  }
  return { ids, error: null }
}

async function submitGrant(): Promise<void> {
  const target = grantTarget.value
  if (!target) {
    return
  }
  const parsed = parseUserIds(grantText.value)
  if (parsed.error) {
    ElMessage.warning(parsed.error)
    return
  }
  if (target.status !== 0) {
    ElMessage.warning('模板已停用，请先启用再发券')
    return
  }

  granting.value = true
  try {
    const granted = await grantCoupon(target.id, parsed.ids)
    ElMessage.success(`本次成功发放 ${granted} 张，模板已发 ${target.issuedQty + granted} / ${target.totalQty}`)
    grantVisible.value = false
    await load()
  } catch {
    // 40211 余量不足 / 40212 模板停用 / 40210 模板不存在由拦截器统一提示
  } finally {
    granting.value = false
  }
}

function openRecords(row: CouponTemplateView): void {
  recordsTarget.value = row
  recordsStatus.value = undefined
  recordsPageNum.value = 1
  records.value = []
  recordsTotal.value = 0
  recordsVisible.value = true
  void loadRecords()
}

async function loadRecords(): Promise<void> {
  const target = recordsTarget.value
  if (!target) {
    return
  }
  recordsLoading.value = true
  try {
    const result = await listCouponRecords(target.id, {
      status: typeof recordsStatus.value === 'number' ? recordsStatus.value : undefined,
      pageNum: recordsPageNum.value,
      pageSize: recordsPageSize.value,
    })
    records.value = result.list
    recordsTotal.value = result.total
  } catch {
    // 拦截器已提示
  } finally {
    recordsLoading.value = false
  }
}

function handleRecordsPage(page: number): void {
  recordsPageNum.value = page
  void loadRecords()
}

function handleRecordsSize(size: number): void {
  recordsPageSize.value = size
  recordsPageNum.value = 1
  void loadRecords()
}

function couponValue(row: CouponTemplateView): string {
  if (row.couponType === 2) {
    return row.discountRate === null
      ? '—'
      : `${(Number(row.discountRate) * 10).toFixed(1)} 折`
  }
  if (row.couponType === 3) {
    return `无门槛减 ${formatYuan(row.discountAmount)}`
  }
  return `满 ${formatYuan(row.thresholdAmount)} 减 ${formatYuan(row.discountAmount)}`
}

function validityText(row: CouponTemplateView): string {
  const parts: string[] = []
  if (row.validDays !== null) {
    parts.push(`领取后 ${row.validDays} 天`)
  }
  if (row.validStart && row.validEnd) {
    parts.push(`${formatDateTime(row.validStart)} ~ ${formatDateTime(row.validEnd)}`)
  }
  return parts.length ? parts.join(' / ') : '—'
}

onMounted(load)
</script>

<template>
  <div class="page">
    <div class="page-lead">
      <div>
        <h2>基础券管理</h2>
        <p>
          数据源：<span class="mono">/api/v1/admin/coupons</span>，权限码
          <span class="mono">marketing:coupon:list</span> /
          <span class="mono">marketing:coupon:save</span> /
          <span class="mono">marketing:coupon:grant</span>。
          金额单位分，表单以元输入；模板状态 <span class="mono">0 启用 / 1 停用</span>。
        </p>
      </div>
      <div class="toolbar">
        <el-button type="primary" :icon="Plus" @click="openCreate">新建券模板</el-button>
        <el-button :icon="Refresh" :loading="loading" @click="load">刷新</el-button>
      </div>
    </div>

    <el-alert v-if="loadError" type="error" :closable="false" show-icon :title="loadError" />

    <el-card shadow="never">
      <div class="toolbar filter-bar">
        <el-input
          v-model="filters.name"
          class="keyword-input"
          placeholder="券名称（300ms 防抖）"
          clearable
          :prefix-icon="Search"
        />
        <el-select v-model="filters.status" placeholder="全部状态" clearable class="status-select">
          <el-option
            v-for="option in COUPON_STATUS_OPTIONS"
            :key="option.value"
            :label="option.label"
            :value="option.value"
          />
        </el-select>
        <el-select
          v-model="filters.couponType"
          placeholder="全部类型"
          clearable
          class="status-select"
        >
          <el-option
            v-for="option in COUPON_TYPE_OPTIONS"
            :key="option.value"
            :label="option.label"
            :value="option.value"
          />
        </el-select>
        <el-select v-model="filters.bearer" placeholder="全部承担方" clearable class="status-select">
          <el-option
            v-for="option in COUPON_BEARER_OPTIONS"
            :key="option.value"
            :label="option.label"
            :value="option.value"
          />
        </el-select>
        <el-button type="primary" @click="search">查询</el-button>
        <el-button @click="reset">重置</el-button>
      </div>

      <el-table v-loading="loading" :data="coupons" border row-key="id">
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="name" label="券名称" min-width="160" show-overflow-tooltip />
        <el-table-column label="类型 / 承担方" width="130">
          <template #default="{ row }">
            <div>{{ COUPON_TYPE_LABELS[row.couponType] ?? `类型 ${row.couponType}` }}</div>
            <div class="muted name-sub">
              {{ COUPON_BEARER_LABELS[row.bearer] ?? `承担方 ${row.bearer}` }}
            </div>
          </template>
        </el-table-column>
        <el-table-column label="优惠内容" min-width="190">
          <template #default="{ row }">{{ couponValue(row) }}</template>
        </el-table-column>
        <el-table-column label="发放量" width="115" align="right">
          <template #default="{ row }">
            <span :class="{ 'quota-danger': row.issuedQty >= row.totalQty }">
              {{ row.issuedQty }} / {{ row.totalQty }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="有效期" min-width="230">
          <template #default="{ row }">{{ validityText(row) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="80">
          <template #default="{ row }">
            <el-tag :type="statusMeta(COUPON_STATUS_MAP, row.status).tag" size="small">
              {{ statusMeta(COUPON_STATUS_MAP, row.status).label }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="创建时间" width="150">
          <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="190" fixed="right">
          <template #default="{ row }">
            <div class="row-actions">
              <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
              <el-button link type="primary" @click="toggleStatus(row)">
                {{ row.status === 0 ? '停用' : '启用' }}
              </el-button>
              <el-button
                link
                type="primary"
                :disabled="row.status !== 0"
                @click="openGrant(row)"
              >
                发券
              </el-button>
              <el-button link type="primary" @click="openRecords(row)">记录</el-button>
            </div>
          </template>
        </el-table-column>

        <template #empty>
          <el-empty description="暂无券模板，点击右上角新建。" :image-size="80" />
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
      :title="editingId === null ? '新建券模板' : `编辑券模板 #${editingId}`"
      width="620px"
      :close-on-click-modal="false"
    >
      <el-form label-width="130px">
        <el-form-item label="券名称" required>
          <el-input v-model="form.name" maxlength="64" show-word-limit />
        </el-form-item>
        <el-form-item label="券类型" required>
          <el-radio-group v-model="form.couponType" :disabled="editingId !== null">
            <el-radio-button
              v-for="option in COUPON_TYPE_OPTIONS"
              :key="option.value"
              :value="option.value"
            >
              {{ option.label }}
            </el-radio-button>
          </el-radio-group>
          <div v-if="editingId !== null" class="form-tip">
            编辑时后端忽略 couponType，只允许改金额、库存与有效期。
          </div>
        </el-form-item>
        <el-form-item v-if="form.couponType !== 3" label="使用门槛（元）">
          <el-input-number
            v-model="form.thresholdYuan"
            :min="0"
            :precision="2"
            :step="1"
          />
          <span class="field-hint">0 表示无门槛</span>
        </el-form-item>
        <el-form-item v-if="form.couponType === 2" label="折扣率" required>
          <el-input-number
            v-model="form.discountRate"
            :min="0.0001"
            :max="1"
            :step="0.05"
            :precision="4"
          />
          <span class="field-hint">0.9000 表示 9 折</span>
        </el-form-item>
        <el-form-item v-else label="优惠金额（元）" required>
          <el-input-number
            v-model="form.discountYuan"
            :min="0.01"
            :precision="2"
            :step="1"
          />
        </el-form-item>
        <el-form-item label="承担方">
          <el-radio-group v-model="form.bearer">
            <el-radio v-for="option in COUPON_BEARER_OPTIONS" :key="option.value" :value="option.value">
              {{ option.label }}
            </el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="发放总量">
          <el-input-number v-model="form.totalQty" :min="0" :precision="0" />
          <span v-if="editingId !== null" class="field-hint">
            已发放 {{ form.issuedQty }} 张，总量不能低于该值
          </span>
        </el-form-item>
        <el-form-item label="固定有效期">
          <el-date-picker
            v-model="form.validRange"
            type="datetimerange"
            value-format="YYYY-MM-DDTHH:mm:ss"
            start-placeholder="生效时间"
            end-placeholder="失效时间"
            class="full-width"
          />
        </el-form-item>
        <el-form-item label="领取后有效天数">
          <el-input-number v-model="form.validDays" :min="1" :precision="0" />
          <span class="field-hint">发券时优先按该天数计算过期时间</span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitForm">
          {{ editingId === null ? '创建' : '保存' }}
        </el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="grantVisible"
      title="定向发券"
      width="520px"
      :close-on-click-modal="false"
    >
      <p class="dialog-hint">
        模板「{{ grantTarget?.name }}」剩余可发
        {{ (grantTarget?.totalQty ?? 0) - (grantTarget?.issuedQty ?? 0) }} 张。
        用户 ID 为 C 端 <span class="mono">t_member_user.id</span>，支持空格/逗号/换行分隔，
        单次去重后不得超过 {{ MAX_GRANT_USERS }} 人。
      </p>
      <el-input
        v-model="grantText"
        type="textarea"
        :rows="6"
        placeholder="例如：10001, 10002, 10003"
      />
      <template #footer>
        <el-button @click="grantVisible = false">取消</el-button>
        <el-button
          type="primary"
          :icon="Tickets"
          :loading="granting"
          @click="submitGrant"
        >
          确认发放
        </el-button>
      </template>
    </el-dialog>

    <el-drawer
      v-model="recordsVisible"
      :title="`领取记录 · ${recordsTarget?.name ?? ''}`"
      size="720px"
    >
      <div class="toolbar records-toolbar">
        <el-select
          v-model="recordsStatus"
          placeholder="全部状态"
          clearable
          class="status-select"
          @change="handleRecordsPage(1)"
        >
          <el-option
            v-for="option in USER_COUPON_STATUS_OPTIONS"
            :key="option.value"
            :label="option.label"
            :value="option.value"
          />
        </el-select>
        <span class="muted">共 {{ recordsTotal }} 张</span>
      </div>

      <el-table v-loading="recordsLoading" :data="records" border size="small">
        <el-table-column prop="id" label="用户券 ID" width="100" />
        <el-table-column prop="userId" label="用户 ID" width="100" />
        <el-table-column label="状态" width="95">
          <template #default="{ row }">
            <el-tag
              :type="statusMeta(USER_COUPON_STATUS_MAP, row.status).tag"
              size="small"
            >
              {{ statusMeta(USER_COUPON_STATUS_MAP, row.status).label }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="过期时间" width="150">
          <template #default="{ row }">{{ formatDateTime(row.expireAt) }}</template>
        </el-table-column>
        <el-table-column label="使用时间" width="150">
          <template #default="{ row }">{{ formatDateTime(row.usedAt) }}</template>
        </el-table-column>
        <el-table-column prop="orderId" label="订单 ID" width="95">
          <template #default="{ row }">{{ row.orderId ?? '—' }}</template>
        </el-table-column>
        <el-table-column label="发放时间" width="150">
          <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
        </el-table-column>
        <template #empty>
          <el-empty description="该模板暂无领取记录。" :image-size="70" />
        </template>
      </el-table>

      <el-pagination
        class="pagination"
        layout="total, sizes, prev, pager, next"
        :total="recordsTotal"
        :page-size="recordsPageSize"
        :page-sizes="PAGE_SIZE_OPTIONS"
        :current-page="recordsPageNum"
        @current-change="handleRecordsPage"
        @size-change="handleRecordsSize"
      />
    </el-drawer>
  </div>
</template>

<style scoped>
.filter-bar {
  margin-bottom: 14px;
}

.keyword-input {
  width: 240px;
}

.status-select {
  width: 140px;
}

.name-sub {
  font-size: 12px;
  margin-top: 2px;
}

.quota-danger {
  color: var(--el-color-danger);
  font-weight: 600;
}

.row-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 2px;
  align-items: center;
}

.pagination {
  margin-top: 16px;
  justify-content: flex-end;
}

.full-width {
  width: 100%;
}

.field-hint {
  margin-left: 10px;
  color: var(--app-muted);
  font-size: 12px;
}

.form-tip {
  width: 100%;
  color: var(--app-muted);
  font-size: 12px;
  line-height: 1.6;
  margin-top: 4px;
}

.dialog-hint {
  color: var(--app-muted);
  font-size: 13px;
  line-height: 1.7;
  margin-bottom: 12px;
}

.records-toolbar {
  margin-bottom: 12px;
}

@media (max-width: 900px) {
  .keyword-input {
    width: 100%;
  }
}
</style>
