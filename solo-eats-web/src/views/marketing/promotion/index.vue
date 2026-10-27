<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Refresh } from '@element-plus/icons-vue'
import {
  createPromotion,
  getPromotion,
  listPromotions,
  updatePromotion,
  updatePromotionStatus,
  type PromotionSavePayload,
  type PromotionView,
} from '@/api/promotion'
import {
  PROMOTION_BEARER,
  PROMOTION_BEARER_OPTIONS,
  PROMOTION_SCOPE,
  PROMOTION_SCOPE_OPTIONS,
  PROMOTION_STATUS,
  PROMOTION_STATUS_OPTIONS,
  PROMOTION_TYPE,
  PROMOTION_TYPE_OPTIONS,
  SCOPE_REF_TYPE_OPTIONS,
  labelOf,
  tagOf,
} from '@/constants/marketingStatus'
import { debounce } from '@/utils/debounce'
import { formatDateTime } from '@/utils/datetime'
import { centsToYuan, formatYuan, yuanToFen } from '@/utils/money'

const PAGE_SIZE_OPTIONS = [10, 20, 50]

const DEFAULT_DISCOUNT_PERCENT = 85

interface RuleFormRow {
  /** 元；null 仅来自 el-input-number 清空，提交前校验拦截 */
  thresholdYuan: number | null
  discountYuan: number | null
  /** 百分数，85 表示 0.85 */
  discountRatePercent: number | null
  maxDiscountYuan: number | null
  sort: number
}

interface ScopeFormRow {
  refType: number
  refId: number | null
}

interface PromotionStatusAction {
  label: string
  target: number
}

const loading = ref(false)
const loadError = ref('')
const promotions = ref<PromotionView[]>([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(20)
const appliedKeyword = ref('')

const filters = reactive({
  status: undefined as number | undefined,
  promotionType: undefined as number | undefined,
  bearer: undefined as number | undefined,
  keyword: '',
})

watch(
  () => filters.keyword,
  debounce((value: string) => {
    appliedKeyword.value = value
    pageNum.value = 1
    void load()
  }, 300),
)

const formVisible = ref(false)
const detailLoading = ref(false)
const submitting = ref(false)
const editingId = ref<number | null>(null)

const form = reactive({
  promotionName: '',
  promotionType: 1,
  bearer: 1,
  scopeType: 1,
  merchantId: null as number | null,
  shopId: null as number | null,
  startAt: null as string | null,
  endAt: null as string | null,
  stackable: false,
  priority: 0,
  totalBudgetYuan: null as number | null,
})

const ruleRows = ref<RuleFormRow[]>([])
const scopeRows = ref<ScopeFormRow[]>([])

/** scopeType=4 只允许品类（refType=3）；scopeType=5 只允许 SPU/SKU（refType=4/5） */
const scopeRefOptions = computed(() =>
  form.scopeType === 4
    ? SCOPE_REF_TYPE_OPTIONS.filter((option) => option.value === 3)
    : SCOPE_REF_TYPE_OPTIONS.filter(
        (option) => option.value === 4 || option.value === 5,
      ),
)

function errorMessage(error: unknown, fallback: string): string {
  return error instanceof Error ? error.message : fallback
}

async function load(): Promise<void> {
  loading.value = true
  loadError.value = ''
  try {
    const result = await listPromotions({
      status: filters.status,
      promotionType: filters.promotionType,
      bearer: filters.bearer,
      keyword: appliedKeyword.value.trim() || undefined,
      pageNum: pageNum.value,
      pageSize: pageSize.value,
    })
    promotions.value = result.list
    total.value = result.total
  } catch (error) {
    loadError.value = errorMessage(error, '满减活动加载失败')
  } finally {
    loading.value = false
  }
}

function search(): void {
  pageNum.value = 1
  void load()
}

function reset(): void {
  filters.status = undefined
  filters.promotionType = undefined
  filters.bearer = undefined
  filters.keyword = ''
  appliedKeyword.value = ''
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

function defaultRule(): RuleFormRow {
  return {
    thresholdYuan: 0,
    discountYuan: 0,
    discountRatePercent: DEFAULT_DISCOUNT_PERCENT,
    maxDiscountYuan: null,
    sort: 1,
  }
}

function resetForm(): void {
  editingId.value = null
  Object.assign(form, {
    promotionName: '',
    promotionType: 1,
    bearer: 1,
    scopeType: 1,
    merchantId: null,
    shopId: null,
    startAt: null,
    endAt: null,
    stackable: false,
    priority: 0,
    totalBudgetYuan: null,
  })
  ruleRows.value = [defaultRule()]
  scopeRows.value = []
}

function applyDetail(detail: PromotionView): void {
  Object.assign(form, {
    promotionName: detail.promotionName,
    promotionType: detail.promotionType,
    bearer: detail.bearer,
    scopeType: detail.scopeType,
    merchantId: detail.merchantId,
    shopId: detail.shopId,
    startAt: detail.startAt,
    endAt: detail.endAt,
    stackable: detail.stackable === 1,
    priority: detail.priority,
    totalBudgetYuan:
      detail.totalBudget === null ? null : centsToYuan(detail.totalBudget),
  })
  ruleRows.value = detail.rules.length
    ? detail.rules.map((rule) => ({
        thresholdYuan: centsToYuan(rule.thresholdAmount),
        discountYuan:
          rule.discountAmount === null ? null : centsToYuan(rule.discountAmount),
        discountRatePercent:
          rule.discountRate === null
            ? null
            : Number((Number(rule.discountRate) * 100).toFixed(2)),
        maxDiscountYuan:
          rule.maxDiscount === null ? null : centsToYuan(rule.maxDiscount),
        sort: rule.sort,
      }))
    : [defaultRule()]
  scopeRows.value = detail.scopes.map((scope) => ({
    refType: scope.refType,
    refId: scope.refId,
  }))
}

function openCreate(): void {
  resetForm()
  formVisible.value = true
}

async function openEdit(row: PromotionView): Promise<void> {
  resetForm()
  editingId.value = row.id
  detailLoading.value = true
  formVisible.value = true
  try {
    const detail = await getPromotion(row.id)
    applyDetail(detail)
  } catch {
    // 错误提示由 http 响应拦截器统一输出，此处不重复弹
    formVisible.value = false
  } finally {
    detailLoading.value = false
  }
}

function addRule(): void {
  ruleRows.value.push({ ...defaultRule(), sort: ruleRows.value.length + 1 })
}

function removeRule(index: number): void {
  if (ruleRows.value.length <= 1) {
    return
  }
  ruleRows.value.splice(index, 1)
  ruleRows.value.forEach((rule, position) => {
    rule.sort = position + 1
  })
}

function addScopeRow(): void {
  scopeRows.value.push({
    refType: form.scopeType === 4 ? 3 : 4,
    refId: null,
  })
}

function removeScopeRow(index: number): void {
  scopeRows.value.splice(index, 1)
}

/** 切换范围类型时清掉不适用的主表 ID，并把明细 refType 收敛到后端允许的取值 */
function handleScopeTypeChange(): void {
  if (form.scopeType !== 2) {
    form.merchantId = null
  }
  if (form.scopeType !== 3) {
    form.shopId = null
  }
  if (form.scopeType === 4) {
    scopeRows.value = scopeRows.value.map((row) => ({ ...row, refType: 3 }))
  } else if (form.scopeType === 5) {
    scopeRows.value = scopeRows.value.map((row) => ({
      ...row,
      refType: row.refType === 5 ? 5 : 4,
    }))
  }
}

function validateForm(): string | null {
  if (!form.promotionName.trim()) {
    return '活动名称不能为空'
  }
  if (form.promotionName.trim().length > 64) {
    return '活动名称不能超过 64 个字符'
  }
  if (!form.startAt || !form.endAt) {
    return '生效开始时间与结束时间不能为空'
  }
  if (
    !(new Date(form.endAt).getTime() > new Date(form.startAt).getTime())
  ) {
    return '结束时间必须晚于开始时间'
  }
  if (form.scopeType === 2 && form.merchantId === null) {
    return '适用范围为指定商家时，商家 ID 必填'
  }
  if (form.scopeType === 3 && form.shopId === null) {
    return '适用范围为指定门店时，门店 ID 必填'
  }
  if (form.totalBudgetYuan !== null && form.totalBudgetYuan < 0) {
    return '总预算不能为负数'
  }
  if (ruleRows.value.length === 0) {
    return '至少配置 1 条阶梯规则'
  }

  for (let index = 0; index < ruleRows.value.length; index += 1) {
    const rule = ruleRows.value[index]
    const position = index + 1
    if (rule.thresholdYuan === null || rule.thresholdYuan < 0) {
      return `第 ${position} 档门槛金额不能为空且不能为负数`
    }
    if (form.promotionType === 1) {
      if (rule.discountYuan === null || rule.discountYuan <= 0) {
        return `第 ${position} 档优惠金额必须大于 0`
      }
    } else {
      // 后端按 decimal(4,2) HALF_UP 后要求 ∈ (0,1)：0.5% → 0.005 → 0.01，
      // 99.5% → 0.995 → 1.00 会被拒，所以百分比区间是 [0.5, 99.5)
      if (
        rule.discountRatePercent === null ||
        rule.discountRatePercent < 0.5 ||
        rule.discountRatePercent >= 99.5
      ) {
        return `第 ${position} 档折扣率必须在 0.5% ~ 99.5% 之间（不含 99.5%）`
      }
      if (rule.maxDiscountYuan !== null && rule.maxDiscountYuan < 0) {
        return `第 ${position} 档封顶金额不能为负数`
      }
    }
  }

  if (form.scopeType === 4 || form.scopeType === 5) {
    if (scopeRows.value.length === 0) {
      return '适用范围为指定品类/商品时，范围明细至少 1 行'
    }
    const seen = new Set<string>()
    for (let index = 0; index < scopeRows.value.length; index += 1) {
      const scope = scopeRows.value[index]
      if (
        scope.refId === null ||
        !Number.isInteger(scope.refId) ||
        scope.refId <= 0
      ) {
        return `范围明细第 ${index + 1} 行引用 ID 必须是正整数`
      }
      const key = `${scope.refType}:${scope.refId}`
      if (seen.has(key)) {
        return `范围明细存在重复项（refType ${scope.refType} / refId ${scope.refId}）`
      }
      seen.add(key)
    }
  }

  return null
}

function toPayload(): PromotionSavePayload {
  const isFullReduction = form.promotionType === 1
  return {
    promotionType: form.promotionType,
    promotionName: form.promotionName.trim(),
    bearer: form.bearer,
    merchantId: form.scopeType === 2 ? form.merchantId : null,
    shopId: form.scopeType === 3 ? form.shopId : null,
    scopeType: form.scopeType,
    startAt: form.startAt ?? '',
    endAt: form.endAt ?? '',
    stackable: form.stackable ? 1 : 0,
    priority: form.priority,
    totalBudget:
      form.totalBudgetYuan === null ? null : yuanToFen(form.totalBudgetYuan),
    rules: ruleRows.value.map((rule) => ({
      thresholdAmount: yuanToFen(rule.thresholdYuan),
      discountAmount: isFullReduction
        ? rule.discountYuan === null
          ? null
          : yuanToFen(rule.discountYuan)
        : null,
      discountRate:
        isFullReduction || rule.discountRatePercent === null
          ? null
          : Number((rule.discountRatePercent / 100).toFixed(4)),
      maxDiscount:
        isFullReduction || rule.maxDiscountYuan === null
          ? null
          : yuanToFen(rule.maxDiscountYuan),
      sort: rule.sort,
    })),
    scopeRefs:
      form.scopeType === 4 || form.scopeType === 5
        ? scopeRows.value.map((scope) => ({
            refType: scope.refType,
            refId: scope.refId ?? 0,
          }))
        : [],
  }
}

async function submitForm(): Promise<void> {
  if (detailLoading.value) {
    return
  }
  const invalid = validateForm()
  if (invalid) {
    ElMessage.warning(invalid)
    return
  }

  submitting.value = true
  try {
    if (editingId.value === null) {
      await createPromotion(toPayload())
      ElMessage.success('满减活动已创建，初始状态为草稿')
    } else {
      await updatePromotion(editingId.value, toPayload())
      ElMessage.success('满减活动已更新')
    }
    formVisible.value = false
    await load()
  } catch {
    // 错误提示由 http 响应拦截器统一输出，此处不重复弹（否则同一条错误会弹两次）
  } finally {
    submitting.value = false
  }
}

function statusActions(row: PromotionView): PromotionStatusAction[] {
  if (row.status === 0) {
    return [{ label: '开始', target: 1 }]
  }
  if (row.status === 1) {
    return [
      { label: '暂停', target: 3 },
      { label: '结束', target: 2 },
    ]
  }
  if (row.status === 3) {
    return [{ label: '开始', target: 1 }]
  }
  return []
}

async function changeStatus(
  row: PromotionView,
  target: number,
): Promise<void> {
  const targetLabel = labelOf(PROMOTION_STATUS, target)
  try {
    await ElMessageBox.confirm(
      `将活动「${row.promotionName}」变更为「${targetLabel}」？`,
      `确认${targetLabel}`,
      {
        confirmButtonText: targetLabel,
        cancelButtonText: '取消',
        type: 'warning',
      },
    )
  } catch {
    return
  }

  try {
    await updatePromotionStatus(row.id, target)
    ElMessage.success(`活动已变更为${targetLabel}`)
    await load()
  } catch {
    // 错误提示由 http 响应拦截器统一输出，此处不重复弹
  }
}

function scopeText(row: PromotionView): string {
  const label = labelOf(PROMOTION_SCOPE, row.scopeType)
  if (row.scopeType === 2 && row.merchantId !== null) {
    return `${label} #${row.merchantId}`
  }
  if (row.scopeType === 3 && row.shopId !== null) {
    return `${label} #${row.shopId}`
  }
  return label
}

function budgetText(row: PromotionView): string {
  if (row.totalBudget === null) {
    return '不限制'
  }
  return `${formatYuan(row.usedBudget)} / ${formatYuan(row.totalBudget)}`
}

function budgetExhausted(row: PromotionView): boolean {
  return row.totalBudget !== null && row.usedBudget >= row.totalBudget
}

onMounted(load)
</script>

<template>
  <div class="page">
    <div class="page-lead">
      <div>
        <h2>满减活动</h2>
        <p>
          数据源：<span class="mono">/api/v1/admin/promotions</span>，权限码
          <span class="mono">marketing:promotion:list</span> /
          <span class="mono">marketing:promotion:update</span>。
          金额单位分，表单以元输入；状态 <span class="mono">0 草稿 / 1 进行中 / 2 已结束 / 3 暂停</span>，
          新建后固定为草稿，开始/暂停/结束走独立状态接口。
        </p>
      </div>
      <div class="toolbar">
        <el-button type="primary" :icon="Plus" @click="openCreate">新建活动</el-button>
        <el-button :icon="Refresh" :loading="loading" @click="load">刷新</el-button>
      </div>
    </div>

    <el-alert v-if="loadError" type="error" :closable="false" show-icon :title="loadError" />

    <el-card shadow="never">
      <div class="toolbar filter-bar">
        <el-select v-model="filters.status" placeholder="全部状态" clearable class="status-select">
          <el-option
            v-for="option in PROMOTION_STATUS_OPTIONS"
            :key="option.value"
            :label="option.label"
            :value="option.value"
          />
        </el-select>
        <el-select
          v-model="filters.promotionType"
          placeholder="全部类型"
          clearable
          class="status-select"
        >
          <el-option
            v-for="option in PROMOTION_TYPE_OPTIONS"
            :key="option.value"
            :label="option.label"
            :value="option.value"
          />
        </el-select>
        <el-select v-model="filters.bearer" placeholder="全部承担方" clearable class="status-select">
          <el-option
            v-for="option in PROMOTION_BEARER_OPTIONS"
            :key="option.value"
            :label="option.label"
            :value="option.value"
          />
        </el-select>
        <el-input
          v-model="filters.keyword"
          class="keyword-input"
          placeholder="活动名称（300ms 防抖）"
          clearable
        />
        <el-button type="primary" @click="search">查询</el-button>
        <el-button @click="reset">重置</el-button>
      </div>

      <el-table v-loading="loading" :data="promotions" border stripe row-key="id">
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="promotionName" label="活动名称" min-width="170" show-overflow-tooltip />
        <el-table-column label="类型" width="80">
          <template #default="{ row }">
            <el-tag :type="tagOf(PROMOTION_TYPE, row.promotionType)" size="small">
              {{ labelOf(PROMOTION_TYPE, row.promotionType) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="承担方" width="85">
          <template #default="{ row }">
            <el-tag :type="tagOf(PROMOTION_BEARER, row.bearer)" size="small">
              {{ labelOf(PROMOTION_BEARER, row.bearer) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="适用范围" min-width="140">
          <template #default="{ row }">{{ scopeText(row) }}</template>
        </el-table-column>
        <el-table-column label="生效期" min-width="260">
          <template #default="{ row }">
            {{ formatDateTime(row.startAt) }} ~ {{ formatDateTime(row.endAt) }}
          </template>
        </el-table-column>
        <el-table-column prop="priority" label="优先级" width="80" align="right" />
        <el-table-column label="可叠加" width="90">
          <template #default="{ row }">
            <el-tag :type="row.stackable === 1 ? 'success' : 'info'" size="small">
              {{ row.stackable === 1 ? '可叠加' : '不可叠加' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="85">
          <template #default="{ row }">
            <el-tag :type="tagOf(PROMOTION_STATUS, row.status)" size="small">
              {{ labelOf(PROMOTION_STATUS, row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="预算（已用 / 总额）" min-width="170" align="right">
          <template #default="{ row }">
            <span :class="{ 'amount': row.totalBudget !== null, 'budget-danger': budgetExhausted(row) }">
              {{ budgetText(row) }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="160" fixed="right">
          <template #default="{ row }">
            <div class="row-actions">
              <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
              <el-button
                v-for="action in statusActions(row)"
                :key="action.target"
                link
                type="primary"
                @click="changeStatus(row, action.target)"
              >
                {{ action.label }}
              </el-button>
              <span v-if="statusActions(row).length === 0" class="muted">—</span>
            </div>
          </template>
        </el-table-column>

        <template #empty>
          <el-empty description="当前筛选条件下没有满减活动，点击右上角新建。" :image-size="80" />
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
      :title="editingId === null ? '新建满减活动' : `编辑活动 #${editingId}`"
      width="860px"
      :close-on-click-modal="false"
    >
      <el-form v-loading="detailLoading" label-width="120px">
        <el-divider content-position="left">基本信息</el-divider>
        <el-form-item label="活动名称" required>
          <el-input v-model="form.promotionName" maxlength="64" show-word-limit />
        </el-form-item>
        <el-form-item label="活动类型" required>
          <el-radio-group v-model="form.promotionType">
            <el-radio-button
              v-for="option in PROMOTION_TYPE_OPTIONS"
              :key="option.value"
              :value="option.value"
            >
              {{ option.label }}
            </el-radio-button>
          </el-radio-group>
          <span class="field-hint">切换类型会改变档位字段，已填的金额/折扣互不兼容</span>
        </el-form-item>
        <el-form-item label="承担方">
          <el-radio-group v-model="form.bearer">
            <el-radio
              v-for="option in PROMOTION_BEARER_OPTIONS"
              :key="option.value"
              :value="option.value"
            >
              {{ option.label }}
            </el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="适用范围" required>
          <el-select
            v-model="form.scopeType"
            class="full-width"
            @change="handleScopeTypeChange"
          >
            <el-option
              v-for="option in PROMOTION_SCOPE_OPTIONS"
              :key="option.value"
              :label="option.label"
              :value="option.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item v-if="form.scopeType === 2" label="商家 ID" required>
          <el-input-number
            v-model="form.merchantId"
            :min="1"
            :precision="0"
            :controls="false"
            placeholder="t_merchant.id"
            class="id-input"
          />
        </el-form-item>
        <el-form-item v-if="form.scopeType === 3" label="门店 ID" required>
          <el-input-number
            v-model="form.shopId"
            :min="1"
            :precision="0"
            :controls="false"
            placeholder="t_shop.id"
            class="id-input"
          />
        </el-form-item>
        <el-form-item
          v-if="form.scopeType === 4 || form.scopeType === 5"
          label="范围明细"
          required
        >
          <div class="block">
            <el-table :data="scopeRows" border size="small">
              <el-table-column label="引用类型" width="160">
                <template #default="{ row }">
                  <el-select
                    v-model="row.refType"
                    size="small"
                    :disabled="form.scopeType === 4"
                  >
                    <el-option
                      v-for="option in scopeRefOptions"
                      :key="option.value"
                      :label="option.label"
                      :value="option.value"
                    />
                  </el-select>
                </template>
              </el-table-column>
              <el-table-column label="引用 ID" min-width="180">
                <template #default="{ row }">
                  <el-input-number
                    v-model="row.refId"
                    :min="1"
                    :precision="0"
                    :controls="false"
                    size="small"
                    placeholder="品类 / SPU / SKU ID"
                    class="full-width"
                  />
                </template>
              </el-table-column>
              <el-table-column label="操作" width="70">
                <template #default="{ $index }">
                  <el-button link type="danger" @click="removeScopeRow($index)">删除</el-button>
                </template>
              </el-table-column>
              <template #empty>
                <el-empty description="至少添加 1 行范围明细" :image-size="60" />
              </template>
            </el-table>
            <el-button link type="primary" :icon="Plus" class="add-row" @click="addScopeRow">
              新增范围
            </el-button>
            <div class="form-tip">
              指定品类只接受 refType=3；指定商品只接受 refType=4（SPU）或 5（SKU）。
            </div>
          </div>
        </el-form-item>

        <el-divider content-position="left">生效时间与规则开关</el-divider>
        <el-form-item label="生效开始" required>
          <el-date-picker
            v-model="form.startAt"
            type="datetime"
            value-format="YYYY-MM-DDTHH:mm:ss"
            placeholder="开始时间"
            class="full-width"
          />
        </el-form-item>
        <el-form-item label="生效结束" required>
          <el-date-picker
            v-model="form.endAt"
            type="datetime"
            value-format="YYYY-MM-DDTHH:mm:ss"
            placeholder="结束时间"
            class="full-width"
          />
        </el-form-item>
        <el-form-item label="可叠加">
          <el-switch
            v-model="form.stackable"
            active-text="可与其他优惠叠加"
            inactive-text="不可叠加"
          />
        </el-form-item>
        <el-form-item label="优先级">
          <el-input-number v-model="form.priority" :precision="0" :step="1" />
          <span class="field-hint">数值越大优先级越高，命中时优先取高优先级活动</span>
        </el-form-item>
        <el-form-item label="总预算（元）">
          <el-input-number
            v-model="form.totalBudgetYuan"
            :min="0"
            :precision="2"
            :step="100"
            placeholder="不填表示不限制"
          />
          <span class="field-hint">留空表示不限制；已用金额由核销链路维护，不在此处编辑</span>
        </el-form-item>

        <el-divider content-position="left">阶梯规则</el-divider>
        <el-form-item label="档位配置" required>
          <div class="block">
            <el-table :data="ruleRows" border size="small">
              <el-table-column label="门槛金额（元）" min-width="140">
                <template #default="{ row }">
                  <el-input-number
                    v-model="row.thresholdYuan"
                    :min="0"
                    :precision="2"
                    :controls="false"
                    size="small"
                    class="full-width"
                  />
                </template>
              </el-table-column>
              <el-table-column
                v-if="form.promotionType === 1"
                label="优惠金额（元）"
                min-width="140"
              >
                <template #default="{ row }">
                  <el-input-number
                    v-model="row.discountYuan"
                    :min="0.01"
                    :precision="2"
                    :controls="false"
                    size="small"
                    class="full-width"
                  />
                </template>
              </el-table-column>
              <el-table-column
                v-if="form.promotionType === 2"
                label="折扣率（%）"
                min-width="130"
              >
                <template #default="{ row }">
                  <el-input-number
                    v-model="row.discountRatePercent"
                    :min="0.5"
                    :max="99"
                    :precision="2"
                    :step="1"
                    :controls="false"
                    size="small"
                    class="full-width"
                  />
                </template>
              </el-table-column>
              <el-table-column
                v-if="form.promotionType === 2"
                label="封顶金额（元）"
                min-width="140"
              >
                <template #default="{ row }">
                  <el-input-number
                    v-model="row.maxDiscountYuan"
                    :min="0"
                    :precision="2"
                    :controls="false"
                    size="small"
                    placeholder="不填不封顶"
                    class="full-width"
                  />
                </template>
              </el-table-column>
              <el-table-column label="排序" width="90">
                <template #default="{ row }">
                  <el-input-number
                    v-model="row.sort"
                    :min="1"
                    :precision="0"
                    :controls="false"
                    size="small"
                    class="full-width"
                  />
                </template>
              </el-table-column>
              <el-table-column label="操作" width="70">
                <template #default="{ $index }">
                  <el-button
                    link
                    type="danger"
                    :disabled="ruleRows.length <= 1"
                    @click="removeRule($index)"
                  >
                    删除
                  </el-button>
                </template>
              </el-table-column>
            </el-table>
            <el-button link type="primary" :icon="Plus" class="add-row" @click="addRule">
              新增档位
            </el-button>
            <div class="form-tip">
              满减：每档填减免金额（元），提交时转分；折扣：百分比填 85 表示 0.85，
              提交时 ÷100 保留 4 位，后端按 decimal(4,2) 落库。
            </div>
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button
          type="primary"
          :loading="submitting"
          :disabled="detailLoading"
          @click="submitForm"
        >
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

.keyword-input {
  width: 220px;
}

.status-select {
  width: 145px;
}

.id-input {
  width: 200px;
}

.amount {
  font-variant-numeric: tabular-nums;
  font-weight: 600;
}

.budget-danger {
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

.block {
  width: 100%;
}

.add-row {
  margin-top: 8px;
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
  margin-top: 6px;
}

@media (max-width: 900px) {
  .keyword-input,
  .status-select {
    width: 100%;
  }
}
</style>
