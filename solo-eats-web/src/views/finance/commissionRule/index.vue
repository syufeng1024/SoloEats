<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Refresh } from '@element-plus/icons-vue'
import {
  createCommissionRule,
  getCommissionRule,
  listCommissionRules,
  updateCommissionRule,
  type CommissionRuleSavePayload,
  type CommissionRuleView,
} from '@/api/finance'
import {
  RULE_SCOPE,
  RULE_SCOPE_OPTIONS,
  RULE_STATUS,
  RULE_STATUS_OPTIONS,
  labelOf,
  tagOf,
} from '@/constants/financeStatus'
import { formatDateTime } from '@/utils/datetime'
import { centsToYuan, formatYuan, yuanToFen } from '@/utils/money'

const PAGE_SIZE_OPTIONS = [10, 20, 50]

const loading = ref(false)
const loadError = ref('')
const rules = ref<CommissionRuleView[]>([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(20)

const filters = reactive({
  ruleScope: undefined as number | undefined,
  refId: undefined as number | undefined,
  status: undefined as number | undefined,
})

const formVisible = ref(false)
const editingId = ref<number | null>(null)
const submitting = ref(false)

const form = reactive({
  ruleScope: 1,
  refId: undefined as number | undefined,
  /** 百分数，8 表示后端 commissionRate = 0.0800 */
  ratePercent: 6,
  minAmountYuan: null as number | null,
  maxAmountYuan: null as number | null,
  effectiveFrom: null as string | null,
  effectiveTo: null as string | null,
  priority: 0,
  status: 1,
  remark: '',
})

function errorMessage(error: unknown, fallback: string): string {
  return error instanceof Error ? error.message : fallback
}

async function load(): Promise<void> {
  loading.value = true
  loadError.value = ''
  try {
    const result = await listCommissionRules({
      ruleScope:
        typeof filters.ruleScope === 'number' ? filters.ruleScope : undefined,
      refId: typeof filters.refId === 'number' ? filters.refId : undefined,
      status: typeof filters.status === 'number' ? filters.status : undefined,
      pageNum: pageNum.value,
      pageSize: pageSize.value,
    })
    rules.value = result.list
    total.value = result.total
  } catch (error) {
    loadError.value = errorMessage(error, '佣金规则加载失败')
  } finally {
    loading.value = false
  }
}

function search(): void {
  pageNum.value = 1
  void load()
}

function reset(): void {
  filters.ruleScope = undefined
  filters.refId = undefined
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

function rateText(rate: number | null | undefined): string {
  if (rate === null || rate === undefined || !Number.isFinite(Number(rate))) {
    return '—'
  }
  return `${(Number(rate) * 100).toFixed(2)}%`
}

function amountText(cents: number | null): string {
  return cents === null ? '不限' : formatYuan(cents)
}

function amountRange(row: CommissionRuleView): string {
  if (row.minAmount === null && row.maxAmount === null) {
    return '不限'
  }
  return `${amountText(row.minAmount)} ~ ${amountText(row.maxAmount)}`
}

function periodText(row: CommissionRuleView): string {
  const from = formatDateTime(row.effectiveFrom)
  const to = row.effectiveTo === null ? '长期' : formatDateTime(row.effectiveTo)
  return `${from} ~ ${to}`
}

function handleScopeChange(value: string | number | boolean): void {
  if (Number(value) === 1) {
    form.refId = undefined
  }
}

function openCreate(): void {
  editingId.value = null
  Object.assign(form, {
    ruleScope: 1,
    refId: undefined,
    ratePercent: 6,
    minAmountYuan: null,
    maxAmountYuan: null,
    effectiveFrom: null,
    effectiveTo: null,
    priority: 0,
    status: 1,
    remark: '',
  })
  formVisible.value = true
}

function applyDetail(detail: CommissionRuleView): void {
  Object.assign(form, {
    ruleScope: detail.ruleScope,
    refId: detail.refId ?? undefined,
    ratePercent: Number((Number(detail.commissionRate) * 100).toFixed(2)),
    minAmountYuan:
      detail.minAmount === null ? null : centsToYuan(detail.minAmount),
    maxAmountYuan:
      detail.maxAmount === null ? null : centsToYuan(detail.maxAmount),
    effectiveFrom: detail.effectiveFrom ?? null,
    effectiveTo: detail.effectiveTo,
    priority: detail.priority,
    status: detail.status,
    remark: detail.remark ?? '',
  })
}

async function openEdit(row: CommissionRuleView): Promise<void> {
  editingId.value = row.id
  try {
    const detail = await getCommissionRule(row.id)
    applyDetail(detail)
    formVisible.value = true
  } catch {
    // 错误提示由 http 响应拦截器统一输出，此处不重复弹
  }
}

function validateForm(): string | null {
  if (
    !Number.isFinite(form.ratePercent) ||
    form.ratePercent <= 0 ||
    form.ratePercent > 100
  ) {
    return '佣金率必须大于 0 且不超过 100%（100% 对应后端 commissionRate = 1）'
  }
  if (form.ruleScope !== 1) {
    if (
      form.refId === null ||
      form.refId === undefined ||
      !Number.isInteger(form.refId) ||
      form.refId < 1
    ) {
      return '非全局规则必须填写引用 ID（品类 / 商家 / 活动 ID）'
    }
  }
  if (
    form.minAmountYuan !== null &&
    form.maxAmountYuan !== null &&
    form.maxAmountYuan < form.minAmountYuan
  ) {
    return '金额上限不能小于金额下限'
  }
  if (
    form.effectiveFrom !== null &&
    form.effectiveTo !== null &&
    new Date(form.effectiveTo).getTime() <= new Date(form.effectiveFrom).getTime()
  ) {
    return '生效结束时间必须晚于开始时间'
  }
  return null
}

function toPayload(): CommissionRuleSavePayload {
  return {
    ruleScope: form.ruleScope,
    refId: form.ruleScope === 1 ? null : form.refId ?? null,
    // 百分数 ÷ 100，保留 4 位小数，对齐后端 decimal(6,4)
    commissionRate: Number((form.ratePercent / 100).toFixed(4)),
    minAmount: form.minAmountYuan === null ? null : yuanToFen(form.minAmountYuan),
    maxAmount: form.maxAmountYuan === null ? null : yuanToFen(form.maxAmountYuan),
    effectiveFrom: form.effectiveFrom,
    effectiveTo: form.effectiveTo,
    priority: form.priority,
    status: form.status,
    remark: form.remark.trim() || null,
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
      await createCommissionRule(toPayload())
      ElMessage.success('佣金规则已创建')
    } else {
      await updateCommissionRule(editingId.value, toPayload())
      ElMessage.success('佣金规则已更新')
    }
    formVisible.value = false
    await load()
  } catch {
    // 错误提示由 http 响应拦截器统一输出，此处不重复弹
  } finally {
    submitting.value = false
  }
}

async function toggleStatus(row: CommissionRuleView): Promise<void> {
  const target = row.status === 1 ? 0 : 1
  const action = target === 1 ? '启用' : '停用'
  try {
    await ElMessageBox.confirm(
      `将佣金规则 #${row.id}（${labelOf(RULE_SCOPE, row.ruleScope)}，${rateText(
        row.commissionRate,
      )}）${action}？`,
      `确认${action}`,
      { confirmButtonText: action, cancelButtonText: '取消', type: 'warning' },
    )
  } catch {
    return
  }

  const payload: CommissionRuleSavePayload = {
    ruleScope: row.ruleScope,
    refId: row.refId,
    commissionRate: Number(row.commissionRate),
    minAmount: row.minAmount,
    maxAmount: row.maxAmount,
    effectiveFrom: row.effectiveFrom,
    effectiveTo: row.effectiveTo,
    priority: row.priority,
    status: target,
    remark: row.remark,
  }

  try {
    await updateCommissionRule(row.id, payload)
    ElMessage.success(`规则已${action}`)
    await load()
  } catch {
    // 错误提示由 http 响应拦截器统一输出，此处不重复弹
  }
}

onMounted(load)
</script>

<template>
  <div class="page">
    <div class="page-lead">
      <div>
        <h2>佣金规则</h2>
        <p>
          数据源：<span class="mono">/api/v1/admin/commission-rules</span>。命中优先级
          <span class="mono">活动(4) &gt; 商家(3) &gt; 品类(2) &gt; 全局(1)</span>，同范围内按
          <span class="mono">priority DESC, effective_from DESC</span> 取首条。费率以百分数录入，提交时 ÷100。
        </p>
      </div>
      <div class="toolbar">
        <el-button type="primary" :icon="Plus" @click="openCreate">新建规则</el-button>
        <el-button :icon="Refresh" :loading="loading" @click="load">刷新</el-button>
      </div>
    </div>

    <el-alert v-if="loadError" type="error" :closable="false" show-icon :title="loadError" />

    <el-card shadow="never">
      <div class="toolbar filter-bar">
        <el-select
          v-model="filters.ruleScope"
          placeholder="全部范围"
          clearable
          class="status-select"
        >
          <el-option
            v-for="option in RULE_SCOPE_OPTIONS"
            :key="option.value"
            :label="option.label"
            :value="option.value"
          />
        </el-select>
        <el-input-number
          v-model="filters.refId"
          placeholder="引用 ID"
          :min="1"
          :precision="0"
          :controls="false"
          class="id-input"
        />
        <el-select
          v-model="filters.status"
          placeholder="全部状态"
          clearable
          class="status-select"
        >
          <el-option
            v-for="option in RULE_STATUS_OPTIONS"
            :key="option.value"
            :label="option.label"
            :value="option.value"
          />
        </el-select>
        <el-button type="primary" @click="search">查询</el-button>
        <el-button @click="reset">重置</el-button>
      </div>

      <el-table v-loading="loading" :data="rules" border stripe row-key="id">
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column label="范围" width="90">
          <template #default="{ row }">
            <el-tag :type="tagOf(RULE_SCOPE, row.ruleScope)" size="small">
              {{ labelOf(RULE_SCOPE, row.ruleScope) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="引用 ID" width="95">
          <template #default="{ row }">
            {{ row.refId === null ? '—' : row.refId }}
          </template>
        </el-table-column>
        <el-table-column label="佣金率" width="100" align="right">
          <template #default="{ row }">{{ rateText(row.commissionRate) }}</template>
        </el-table-column>
        <el-table-column label="金额区间" min-width="170">
          <template #default="{ row }">{{ amountRange(row) }}</template>
        </el-table-column>
        <el-table-column label="生效期" min-width="230">
          <template #default="{ row }">{{ periodText(row) }}</template>
        </el-table-column>
        <el-table-column prop="priority" label="优先级" width="80" align="right" />
        <el-table-column label="状态" width="85">
          <template #default="{ row }">
            <el-tag :type="tagOf(RULE_STATUS, row.status)" size="small">
              {{ labelOf(RULE_STATUS, row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="备注" min-width="140" show-overflow-tooltip>
          <template #default="{ row }">{{ row.remark || '—' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="140" fixed="right">
          <template #default="{ row }">
            <div class="row-actions">
              <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
              <el-button link type="primary" @click="toggleStatus(row)">
                {{ row.status === 1 ? '停用' : '启用' }}
              </el-button>
            </div>
          </template>
        </el-table-column>

        <template #empty>
          <el-empty
            description="当前筛选条件下没有佣金规则。未命中规则时按默认费率 0.0600 计算。"
            :image-size="80"
          />
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
      :title="editingId === null ? '新建佣金规则' : `编辑佣金规则 #${editingId}`"
      width="640px"
      :close-on-click-modal="false"
    >
      <el-form label-width="130px">
        <el-form-item label="规则范围" required>
          <el-radio-group v-model="form.ruleScope" @change="handleScopeChange">
            <el-radio-button
              v-for="option in RULE_SCOPE_OPTIONS"
              :key="option.value"
              :value="option.value"
            >
              {{ option.label }}
            </el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="引用 ID" :required="form.ruleScope !== 1">
          <el-input-number
            v-model="form.refId"
            :min="1"
            :precision="0"
            :controls="false"
            :disabled="form.ruleScope === 1"
            :placeholder="form.ruleScope === 1 ? '全局规则无需引用 ID' : '品类 / 商家 / 活动 ID'"
            class="full-width"
          />
        </el-form-item>
        <el-form-item label="佣金率（%）" required>
          <el-input-number
            v-model="form.ratePercent"
            :min="0.01"
            :max="100"
            :precision="2"
            :step="0.5"
          />
          <span class="field-hint">填 8 表示 0.0800；提交时 ÷100 并保留 4 位小数</span>
        </el-form-item>
        <el-form-item label="金额下限（元）">
          <el-input-number
            v-model="form.minAmountYuan"
            :min="0"
            :precision="2"
            :step="10"
            placeholder="不填表示不限"
          />
          <span class="field-hint">订单金额达到该值才命中，空值表示不限</span>
        </el-form-item>
        <el-form-item label="金额上限（元）">
          <el-input-number
            v-model="form.maxAmountYuan"
            :min="0"
            :precision="2"
            :step="10"
            placeholder="不填表示不限"
          />
        </el-form-item>
        <el-form-item label="生效开始时间">
          <el-date-picker
            v-model="form.effectiveFrom"
            type="datetime"
            value-format="YYYY-MM-DDTHH:mm:ss"
            placeholder="不填则由后端取当前时间"
            class="full-width"
          />
        </el-form-item>
        <el-form-item label="生效结束时间">
          <el-date-picker
            v-model="form.effectiveTo"
            type="datetime"
            value-format="YYYY-MM-DDTHH:mm:ss"
            placeholder="不填表示长期有效"
            class="full-width"
          />
        </el-form-item>
        <el-form-item label="优先级">
          <el-input-number v-model="form.priority" :precision="0" :step="1" />
          <span class="field-hint">同范围内数值越大越优先</span>
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="form.status">
            <el-radio :value="1">启用</el-radio>
            <el-radio :value="0">停用</el-radio>
          </el-radio-group>
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
        <el-button type="primary" :loading="submitting" @click="submitForm">
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

.status-select {
  width: 140px;
}

.id-input {
  width: 130px;
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
</style>
