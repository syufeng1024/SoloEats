<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Refresh } from '@element-plus/icons-vue'
import {
  bindFreightTemplateShop,
  createFreightTemplate,
  listFreightTemplates,
  updateFreightTemplate,
  type FreightTemplateRulePayload,
  type FreightTemplateView,
  type SaveFreightTemplatePayload,
} from '@/api/delivery-admin'
import {
  FREIGHT_RULE_STATUS_OPTIONS,
  FREIGHT_RULE_TYPE_OPTIONS,
  FREIGHT_TEMPLATE_STATUS,
  FREIGHT_TEMPLATE_STATUS_OPTIONS,
  labelOf,
  tagOf,
} from '@/constants/deliveryStatus'
import { errorMessage } from '@/views/report/helpers'
import { formatDateTime } from '@/utils/datetime'
import { centsToYuan, formatYuan, yuanToFen } from '@/utils/money'

const PAGE_SIZE_OPTIONS = [10, 20, 50]

const loading = ref(false)
const loadError = ref('')
const templates = ref<FreightTemplateView[]>([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(20)

interface FreightRuleDraft {
  ruleType: number
  conditionJson: string
  /** 表单输入单位元，提交时转分 */
  feeDeltaYuan: number
  sort: number
  status: number
}

const dialogVisible = ref(false)
const editingId = ref<number | null>(null)
const submitting = ref(false)

const form = reactive({
  templateName: '',
  baseFeeYuan: 0,
  baseDistanceM: 3000,
  perStepDistanceM: 500,
  perStepFeeYuan: 0,
  maxDistanceM: 10000,
  freeOverEnabled: false,
  freeOverYuan: 0,
  status: 0,
  rules: [] as FreightRuleDraft[],
})

const bindVisible = ref(false)
const bindTarget = ref<FreightTemplateView | null>(null)
const bindShopId = ref<number | null | undefined>(undefined)
const binding = ref(false)

function distanceText(meters: number): string {
  const numeric = Number(meters)
  if (!Number.isFinite(numeric)) {
    return '—'
  }
  return numeric >= 1000 ? `${(numeric / 1000).toFixed(2)} km` : `${numeric} m`
}

/** 后端没有 billingType 字段，计费结构由基础费 + 距离阶梯派生。 */
function billingSummary(row: FreightTemplateView): string {
  return `起步 ${formatYuan(row.baseFee)} / ${distanceText(
    row.baseDistanceM,
  )}，之后每 ${distanceText(row.perStepDistanceM)} +${formatYuan(row.perStepFee)}`
}

function freeOverText(row: FreightTemplateView): string {
  return row.freeOverAmount === null ? '不减免' : formatYuan(row.freeOverAmount)
}

function newRule(): FreightRuleDraft {
  return {
    ruleType: 2,
    conditionJson: '',
    feeDeltaYuan: 0,
    sort: 0,
    status: 0,
  }
}

async function load(): Promise<void> {
  loading.value = true
  loadError.value = ''
  try {
    const result = await listFreightTemplates({
      pageNum: pageNum.value,
      pageSize: pageSize.value,
    })
    templates.value = result.list
    total.value = result.total
  } catch (error) {
    templates.value = []
    total.value = 0
    loadError.value = errorMessage(error, '运费模板加载失败')
  } finally {
    loading.value = false
  }
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
    templateName: '',
    baseFeeYuan: 0,
    baseDistanceM: 3000,
    perStepDistanceM: 500,
    perStepFeeYuan: 0,
    maxDistanceM: 10000,
    freeOverEnabled: false,
    freeOverYuan: 0,
    status: 0,
    rules: [],
  })
}

function openCreate(): void {
  editingId.value = null
  resetForm()
  dialogVisible.value = true
}

function openEdit(row: FreightTemplateView): void {
  editingId.value = row.id
  Object.assign(form, {
    templateName: row.templateName,
    baseFeeYuan: centsToYuan(row.baseFee),
    baseDistanceM: row.baseDistanceM,
    perStepDistanceM: row.perStepDistanceM,
    perStepFeeYuan: centsToYuan(row.perStepFee),
    maxDistanceM: row.maxDistanceM,
    freeOverEnabled: row.freeOverAmount !== null,
    freeOverYuan: centsToYuan(row.freeOverAmount),
    status: row.status,
    rules: row.rules.map((rule) => ({
      ruleType: rule.ruleType,
      conditionJson: rule.conditionJson ?? '',
      feeDeltaYuan: centsToYuan(rule.feeDelta),
      sort: rule.sort,
      status: rule.status,
    })),
  })
  dialogVisible.value = true
}

function addRule(): void {
  form.rules.push(newRule())
}

function removeRule(index: number): void {
  form.rules.splice(index, 1)
}

function validate(): string | null {
  const templateName = form.templateName.trim()
  if (!templateName) {
    return '模板名称不能为空'
  }
  if (templateName.length > 64) {
    return '模板名称长度不能超过 64'
  }
  if (!Number.isFinite(form.baseFeeYuan) || form.baseFeeYuan < 0) {
    return '基础运费不能为负数'
  }
  if (!Number.isFinite(form.perStepFeeYuan) || form.perStepFeeYuan < 0) {
    return '续重价不能为负数'
  }
  if (!Number.isInteger(form.baseDistanceM) || form.baseDistanceM < 1) {
    return '基础距离必须是 ≥1 的整数（米）'
  }
  if (!Number.isInteger(form.perStepDistanceM) || form.perStepDistanceM < 1) {
    return '续重距离必须是 ≥1 的整数（米）'
  }
  if (!Number.isInteger(form.maxDistanceM) || form.maxDistanceM < 1) {
    return '最大配送距离必须是 ≥1 的整数（米）'
  }
  if (form.maxDistanceM < form.baseDistanceM) {
    return '最大配送距离不能小于基础距离'
  }
  if (form.freeOverEnabled) {
    if (!Number.isFinite(form.freeOverYuan) || form.freeOverYuan < 0) {
      return '满额减免金额不能为负数'
    }
  }
  for (let index = 0; index < form.rules.length; index += 1) {
    const rule = form.rules[index]
    if (!Number.isInteger(rule.ruleType) || rule.ruleType < 1 || rule.ruleType > 5) {
      return `第 ${index + 1} 条规则的规则类型必须是 1~5`
    }
    if (!Number.isFinite(rule.feeDeltaYuan) || rule.feeDeltaYuan < 0) {
      return `第 ${index + 1} 条规则的加价不能为负数`
    }
    if (!Number.isInteger(rule.sort)) {
      return `第 ${index + 1} 条规则的排序必须是整数`
    }
    if (rule.status !== 0 && rule.status !== 1) {
      return `第 ${index + 1} 条规则的状态只能是 0 或 1`
    }
    const condition = rule.conditionJson.trim()
    if (condition) {
      try {
        JSON.parse(condition)
      } catch {
        return `第 ${index + 1} 条规则的条件 JSON 格式不合法`
      }
    }
  }
  return null
}

function buildPayload(): SaveFreightTemplatePayload {
  const rules: FreightTemplateRulePayload[] = form.rules.map((rule) => ({
    ruleType: rule.ruleType,
    conditionJson: rule.conditionJson.trim() || null,
    feeDelta: yuanToFen(rule.feeDeltaYuan),
    sort: rule.sort,
    status: rule.status,
  }))
  return {
    templateName: form.templateName.trim(),
    baseFee: yuanToFen(form.baseFeeYuan),
    baseDistanceM: form.baseDistanceM,
    perStepDistanceM: form.perStepDistanceM,
    perStepFee: yuanToFen(form.perStepFeeYuan),
    maxDistanceM: form.maxDistanceM,
    freeOverAmount: form.freeOverEnabled ? yuanToFen(form.freeOverYuan) : null,
    status: form.status,
    rules,
  }
}

async function submit(): Promise<void> {
  const validationError = validate()
  if (validationError) {
    ElMessage.warning(validationError)
    return
  }

  // 后端更新时规则全量覆盖：空列表意味着清空全部规则
  if (editingId.value !== null && form.rules.length === 0) {
    try {
      await ElMessageBox.confirm(
        '当前没有加价规则，保存会清空该模板的全部规则，确认继续？',
        '清空规则确认',
        { confirmButtonText: '确认清空并保存', cancelButtonText: '取消', type: 'warning' },
      )
    } catch {
      return
    }
  }

  submitting.value = true
  try {
    if (editingId.value === null) {
      await createFreightTemplate(buildPayload())
      ElMessage.success('运费模板已创建')
    } else {
      await updateFreightTemplate(editingId.value, buildPayload())
      ElMessage.success('运费模板已更新')
    }
    dialogVisible.value = false
    await load()
  } catch {
    // 错误提示由 http 响应拦截器统一输出，此处不重复弹
  } finally {
    submitting.value = false
  }
}

function openBind(row: FreightTemplateView): void {
  bindTarget.value = row
  bindShopId.value = undefined
  bindVisible.value = true
}

async function submitBind(): Promise<void> {
  const target = bindTarget.value
  if (!target) {
    return
  }
  const shopId = bindShopId.value
  if (typeof shopId !== 'number' || !Number.isInteger(shopId) || shopId <= 0) {
    ElMessage.warning('门店 ID 必须是正整数')
    return
  }

  binding.value = true
  try {
    await bindFreightTemplateShop(target.id, { shopId })
    ElMessage.success(`模板「${target.templateName}」已绑定门店 #${shopId}`)
    bindVisible.value = false
  } catch {
    // 错误提示由 http 响应拦截器统一输出，此处不重复弹
  } finally {
    binding.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="page">
    <div class="page-lead">
      <div>
        <h2>运费模板</h2>
        <p>
          数据源：<span class="mono">/api/v1/admin/freight-templates</span>。后端无计费方式字段，
          本页用「基础费 / 距离 + 续程价」派生计费摘要；绑定门店只支持写入，无法查询绑定关系或解绑。
        </p>
      </div>
      <div class="toolbar">
        <el-button :icon="Refresh" :loading="loading" @click="load">刷新</el-button>
        <el-button type="primary" :icon="Plus" @click="openCreate">新增模板</el-button>
      </div>
    </div>

    <el-alert v-if="loadError" type="error" :closable="false" show-icon :title="loadError" />

    <el-card shadow="never">
      <el-table v-loading="loading" :data="templates" border stripe row-key="id">
        <el-table-column prop="id" label="模板 ID" width="85" />
        <el-table-column prop="templateName" label="模板名称" width="170" />
        <el-table-column label="计费摘要" min-width="320">
          <template #default="{ row }">{{ billingSummary(row) }}</template>
        </el-table-column>
        <el-table-column label="最大距离" width="110" align="right">
          <template #default="{ row }">{{ distanceText(row.maxDistanceM) }}</template>
        </el-table-column>
        <el-table-column label="满额减免" width="120" align="right">
          <template #default="{ row }">{{ freeOverText(row) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="tagOf(FREIGHT_TEMPLATE_STATUS, row.status)" size="small">
              {{ labelOf(FREIGHT_TEMPLATE_STATUS, row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="加价规则" width="95" align="right">
          <template #default="{ row }">{{ row.rules.length }} 条</template>
        </el-table-column>
        <el-table-column label="更新时间" width="160">
          <template #default="{ row }">{{ formatDateTime(row.updatedAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button link type="warning" @click="openBind(row)">绑定门店</el-button>
          </template>
        </el-table-column>

        <template #empty>
          <el-empty description="暂无运费模板，点击右上角新增模板。" :image-size="80" />
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
      :title="editingId === null ? '新增运费模板' : '编辑运费模板'"
      width="980px"
      :close-on-click-modal="false"
    >
      <el-form label-width="110px">
        <el-form-item label="模板名称" required>
          <el-input
            v-model="form.templateName"
            maxlength="64"
            show-word-limit
            placeholder="例如：默认配送运费"
            class="name-input"
          />
        </el-form-item>
        <el-form-item label="基础运费（元）" required>
          <el-input-number
            v-model="form.baseFeeYuan"
            :min="0"
            :precision="2"
            :step="0.1"
            :controls="false"
            class="money-input"
          />
        </el-form-item>
        <el-form-item label="基础距离（米）" required>
          <el-input-number
            v-model="form.baseDistanceM"
            :min="1"
            :precision="0"
            :controls="false"
            class="number-input"
          />
        </el-form-item>
        <el-form-item label="续重距离（米）" required>
          <el-input-number
            v-model="form.perStepDistanceM"
            :min="1"
            :precision="0"
            :controls="false"
            class="number-input"
          />
        </el-form-item>
        <el-form-item label="续重价（元）" required>
          <el-input-number
            v-model="form.perStepFeeYuan"
            :min="0"
            :precision="2"
            :step="0.1"
            :controls="false"
            class="money-input"
          />
        </el-form-item>
        <el-form-item label="最大距离（米）" required>
          <el-input-number
            v-model="form.maxDistanceM"
            :min="1"
            :precision="0"
            :controls="false"
            class="number-input"
          />
          <div class="form-hint">必须 ≥ 基础距离。</div>
        </el-form-item>
        <el-form-item label="满额减免">
          <el-checkbox v-model="form.freeOverEnabled">启用满额免配送费</el-checkbox>
          <el-input-number
            v-if="form.freeOverEnabled"
            v-model="form.freeOverYuan"
            :min="0"
            :precision="2"
            :step="1"
            :controls="false"
            class="money-input"
          />
          <span class="muted form-hint-inline">金额单位元，留空/关闭表示不减免。</span>
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="form.status">
            <el-radio
              v-for="option in FREIGHT_TEMPLATE_STATUS_OPTIONS"
              :key="option.value"
              :value="option.value"
            >
              {{ option.label }}
            </el-radio>
          </el-radio-group>
        </el-form-item>

        <el-form-item label="加价规则">
          <div class="rule-editor">
            <div v-for="(rule, index) in form.rules" :key="index" class="rule-row">
              <el-select v-model="rule.ruleType" class="rule-type">
                <el-option
                  v-for="option in FREIGHT_RULE_TYPE_OPTIONS"
                  :key="option.value"
                  :value="option.value"
                  :label="option.label"
                />
              </el-select>
              <el-input
                v-model="rule.conditionJson"
                placeholder='条件 JSON，如 {"startKm":3,"endKm":5}'
                class="rule-condition"
              />
              <el-input-number
                v-model="rule.feeDeltaYuan"
                :min="0"
                :precision="2"
                :step="0.1"
                :controls="false"
                placeholder="加价（元）"
                class="rule-number"
              />
              <el-input-number
                v-model="rule.sort"
                :precision="0"
                :controls="false"
                placeholder="排序"
                class="rule-sort"
              />
              <el-select v-model="rule.status" class="rule-status">
                <el-option
                  v-for="option in FREIGHT_RULE_STATUS_OPTIONS"
                  :key="option.value"
                  :value="option.value"
                  :label="option.label"
                />
              </el-select>
              <el-button link type="danger" @click="removeRule(index)">删除</el-button>
            </div>
            <el-button link type="primary" :icon="Plus" @click="addRule">新增规则</el-button>
            <div class="form-hint">
              保存时规则列表全量覆盖：删除某行后保存，该规则会被物理删除；规则
              <span class="mono">id</span> 不会回传。
            </div>
          </div>
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submit">
          {{ editingId === null ? '创建' : '保存' }}
        </el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="bindVisible"
      title="绑定门店"
      width="520px"
      :close-on-click-modal="false"
    >
      <el-alert
        type="warning"
        :closable="false"
        show-icon
        title="一店一模板，重复绑定会覆盖该门店原有模板；当前接口不支持查询已绑定门店或解绑。"
        class="block-alert"
      />
      <el-form label-width="130px">
        <el-form-item label="模板">
          <span>{{ bindTarget?.templateName ?? '—' }}</span>
        </el-form-item>
        <el-form-item label="门店 ID" required>
          <el-input-number
            v-model="bindShopId"
            :min="1"
            :precision="0"
            :controls="false"
            placeholder="正整数"
            class="number-input"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="bindVisible = false">取消</el-button>
        <el-button type="primary" :loading="binding" @click="submitBind">
          确认绑定
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.pagination {
  margin-top: 16px;
  justify-content: flex-end;
}

.name-input {
  width: 320px;
}

.number-input {
  width: 180px;
}

.money-input {
  width: 180px;
}

.rule-editor {
  width: 100%;
}

.rule-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
  flex-wrap: wrap;
}

.rule-type {
  width: 130px;
}

.rule-condition {
  flex: 1;
  min-width: 220px;
}

.rule-number {
  width: 130px;
}

.rule-sort {
  width: 90px;
}

.rule-status {
  width: 100px;
}

.form-hint {
  color: var(--el-text-color-secondary);
  font-size: 12px;
  line-height: 1.6;
  margin-top: 4px;
}

.form-hint-inline {
  font-size: 12px;
  margin-left: 10px;
}

.block-alert {
  margin-bottom: 14px;
}
</style>
