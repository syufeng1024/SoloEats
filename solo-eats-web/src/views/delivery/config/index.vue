<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import {
  getDispatchConfig,
  saveDispatchConfig,
  type DispatchConfigView,
  type SaveDispatchConfigPayload,
} from '@/api/delivery-admin'
import {
  DISPATCH_CONFIG_SCOPE,
  DISPATCH_CONFIG_SCOPE_OPTIONS,
  DISPATCH_CONFIG_STATUS_OPTIONS,
  DISPATCH_SWITCH_OPTIONS,
  labelOf,
} from '@/constants/deliveryStatus'
import { formatDateTime } from '@/utils/datetime'
import { errorMessage } from '@/views/report/helpers'

const loading = ref(false)
const loadError = ref('')
const saving = ref(false)
const saveError = ref('')
const loaded = ref<DispatchConfigView | null>(null)

/** 1 全局，3 门店；后端明确拒绝 2 城市 */
const scope = ref<number>(1)
const shopId = ref<number | null | undefined>(undefined)

const form = reactive({
  distanceWeight: 0.4,
  loadWeight: 0.3,
  ratingWeight: 0.2,
  timeoutWeight: 0.1,
  dispatchExpireSeconds: 15,
  maxRetry: 3,
  grabRadiusM: 3000,
  grabMaxOrders: 3,
  grabEnabled: 1,
  autoDispatchEnabled: 1,
  status: 0,
})

const validShopId = computed(() => {
  const value = shopId.value
  return typeof value === 'number' && Number.isInteger(value) && value > 0 ? value : null
})

const weightSum = computed(() =>
  Number(
    (
      form.distanceWeight +
      form.loadWeight +
      form.ratingWeight +
      form.timeoutWeight
    ).toFixed(4),
  ),
)

const modeText = computed(() => {
  if (form.grabEnabled === 1 && form.autoDispatchEnabled === 1) {
    return '自动派单 + 抢单大厅'
  }
  if (form.autoDispatchEnabled === 1) {
    return '仅自动派单'
  }
  if (form.grabEnabled === 1) {
    return '仅抢单大厅'
  }
  return '仅手动派单'
})

/** GET 返回生效配置；查询门店时返回全局说明该门店没有启用的覆盖。 */
const showGlobalFallback = computed(
  () => scope.value === 3 && loaded.value !== null && loaded.value.configScope === 1,
)

const loadedSourceText = computed(() => {
  if (loaded.value === null) {
    return '尚未加载'
  }
  if (loaded.value.configScope === 3) {
    return `门店覆盖 #${loaded.value.shopId ?? '—'}`
  }
  return '全局配置'
})

function applyConfig(config: DispatchConfigView): void {
  form.distanceWeight = Number(config.distanceWeight)
  form.loadWeight = Number(config.loadWeight)
  form.ratingWeight = Number(config.ratingWeight)
  form.timeoutWeight = Number(config.timeoutWeight)
  form.dispatchExpireSeconds = config.dispatchExpireSeconds
  form.maxRetry = config.maxRetry
  form.grabRadiusM = config.grabRadiusM
  form.grabMaxOrders = config.grabMaxOrders
  form.grabEnabled = config.grabEnabled
  form.autoDispatchEnabled = config.autoDispatchEnabled
  form.status = config.status
}

async function loadConfig(): Promise<void> {
  if (scope.value === 3 && validShopId.value === null) {
    loadError.value = '门店级配置必须指定正整数门店 ID'
    return
  }
  loading.value = true
  loadError.value = ''
  saveError.value = ''
  try {
    const config = await getDispatchConfig(
      scope.value === 3 && validShopId.value !== null ? validShopId.value : undefined,
    )
    loaded.value = config
    applyConfig(config)
  } catch (error) {
    loaded.value = null
    loadError.value = errorMessage(error, '派单策略加载失败')
  } finally {
    loading.value = false
  }
}

function validateWeight(value: number, label: string): string | null {
  if (!Number.isFinite(value) || value < 0 || value > 1) {
    return `${label}必须在 0~1 之间`
  }
  return null
}

function validate(): string | null {
  if (scope.value !== 1 && scope.value !== 3) {
    return '配置范围只能是 1 全局或 3 门店'
  }
  if (scope.value === 3 && validShopId.value === null) {
    return '门店级配置必须指定正整数门店 ID'
  }
  const weightError =
    validateWeight(form.distanceWeight, '距离权重') ??
    validateWeight(form.loadWeight, '负载权重') ??
    validateWeight(form.ratingWeight, '评分权重') ??
    validateWeight(form.timeoutWeight, '超时权重')
  if (weightError) {
    return weightError
  }
  if (
    !Number.isInteger(form.dispatchExpireSeconds) ||
    form.dispatchExpireSeconds < 1 ||
    form.dispatchExpireSeconds > 600
  ) {
    return '派单倒计时必须是 1~600 的整数（秒）'
  }
  if (!Number.isInteger(form.maxRetry) || form.maxRetry < 0 || form.maxRetry > 10) {
    return '最大重试次数必须是 0~10 的整数'
  }
  if (
    !Number.isInteger(form.grabRadiusM) ||
    form.grabRadiusM < 100 ||
    form.grabRadiusM > 50000
  ) {
    return '抢单半径必须是 100~50000 的整数（米）'
  }
  if (
    !Number.isInteger(form.grabMaxOrders) ||
    form.grabMaxOrders < 1 ||
    form.grabMaxOrders > 50
  ) {
    return '抢单进行中上限必须是 1~50 的整数'
  }
  if (form.grabEnabled !== 0 && form.grabEnabled !== 1) {
    return '抢单大厅开关只能是 0 或 1'
  }
  if (form.autoDispatchEnabled !== 0 && form.autoDispatchEnabled !== 1) {
    return '自动派单开关只能是 0 或 1'
  }
  if (form.status !== 0 && form.status !== 1) {
    return '配置状态只能是 0 启用或 1 停用'
  }
  return null
}

async function save(): Promise<void> {
  saveError.value = ''
  const validationError = validate()
  if (validationError) {
    ElMessage.warning(validationError)
    return
  }

  const scopeText =
    scope.value === 3 ? `门店 #${validShopId.value} 的覆盖配置` : '全局配置'
  try {
    await ElMessageBox.confirm(
      `确认保存${scopeText}？${
        form.status === 1
          ? '当前设置为停用，若没有其他可用配置，保存后读取可能返回「派单策略未配置」。'
          : ''
      }`,
      '保存派单策略',
      { confirmButtonText: '确认保存', cancelButtonText: '取消', type: 'warning' },
    )
  } catch {
    return
  }

  const payload: SaveDispatchConfigPayload = {
    configScope: scope.value,
    shopId: scope.value === 3 ? validShopId.value : null,
    distanceWeight: form.distanceWeight,
    loadWeight: form.loadWeight,
    ratingWeight: form.ratingWeight,
    timeoutWeight: form.timeoutWeight,
    dispatchExpireSeconds: form.dispatchExpireSeconds,
    maxRetry: form.maxRetry,
    grabRadiusM: form.grabRadiusM,
    grabMaxOrders: form.grabMaxOrders,
    grabEnabled: form.grabEnabled,
    autoDispatchEnabled: form.autoDispatchEnabled,
    status: form.status,
  }

  saving.value = true
  try {
    const saved = await saveDispatchConfig(payload)
    if (scope.value === 3 && saved.configScope !== 3) {
      ElMessage.warning(
        '门店配置已保存，但当前生效的是全局配置：门店覆盖可能处于停用状态',
      )
      loaded.value = saved
    } else {
      loaded.value = saved
      applyConfig(saved)
      ElMessage.success('派单策略已保存')
    }
  } catch (error) {
    saveError.value = errorMessage(error, '派单策略保存失败')
  } finally {
    saving.value = false
  }
}

onMounted(loadConfig)
</script>

<template>
  <div class="page">
    <div class="page-lead">
      <div>
        <h2>派单策略</h2>
        <p>
          数据源：<span class="mono">GET/PUT /api/v1/admin/dispatch-config</span>。后端只支持
          <span class="mono">configScope=1</span> 全局与 <span class="mono">3</span> 门店；
          查询门店时返回的是<strong>生效配置</strong>，门店无覆盖时回退全局。
        </p>
      </div>
      <div class="toolbar">
        <el-button :icon="Refresh" :loading="loading" @click="loadConfig">
          重新加载
        </el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </div>
    </div>

    <el-alert v-if="loadError" type="error" :closable="false" show-icon :title="loadError" />
    <el-alert v-if="saveError" type="error" :closable="false" show-icon :title="saveError" />
    <el-alert
      v-if="showGlobalFallback"
      type="info"
      :closable="false"
      show-icon
      title="该门店没有启用的独立配置，当前展示全局配置。直接保存将在门店级新建/覆盖一份配置。"
      class="block-alert"
    />

    <el-card shadow="never">
      <el-form label-width="170px" class="config-form">
        <el-form-item label="编辑范围">
          <div class="toolbar">
            <el-radio-group v-model="scope">
              <el-radio
                v-for="option in DISPATCH_CONFIG_SCOPE_OPTIONS"
                :key="option.value"
                :value="option.value"
              >
                {{ option.label }}
              </el-radio>
            </el-radio-group>
            <el-input-number
              v-if="scope === 3"
              v-model="shopId"
              :min="1"
              :precision="0"
              :controls="false"
              placeholder="门店 ID"
              class="id-input"
            />
            <el-button :loading="loading" @click="loadConfig">加载配置</el-button>
          </div>
          <div class="form-hint">
            切换范围或门店后需点「加载配置」；当前展示：{{ loadedSourceText }}
            <span v-if="loaded"> · 更新于 {{ formatDateTime(loaded.updatedAt) }}</span>
          </div>
        </el-form-item>

        <el-form-item label="派单模式（派生）">
          <el-tag type="primary">{{ modeText }}</el-tag>
          <span class="muted form-hint-inline">
            后端无 mode 字段，由抢单大厅与自动派单两个开关组合得出
          </span>
        </el-form-item>

        <div class="weight-grid">
          <el-form-item label="距离权重">
            <el-input-number
              v-model="form.distanceWeight"
              :min="0"
              :max="1"
              :step="0.01"
              :precision="2"
              class="number-input"
            />
          </el-form-item>
          <el-form-item label="负载权重">
            <el-input-number
              v-model="form.loadWeight"
              :min="0"
              :max="1"
              :step="0.01"
              :precision="2"
              class="number-input"
            />
          </el-form-item>
          <el-form-item label="评分权重">
            <el-input-number
              v-model="form.ratingWeight"
              :min="0"
              :max="1"
              :step="0.01"
              :precision="2"
              class="number-input"
            />
          </el-form-item>
          <el-form-item label="超时优先权重">
            <el-input-number
              v-model="form.timeoutWeight"
              :min="0"
              :max="1"
              :step="0.01"
              :precision="2"
              class="number-input"
            />
          </el-form-item>
        </div>

        <el-form-item label="权重合计">
          <el-tag :type="weightSum === 1 ? 'success' : 'warning'">
            {{ weightSum.toFixed(2) }}
          </el-tag>
          <span v-if="weightSum !== 1" class="muted form-hint-inline">
            后端不强制合计为 1，保存前请确认比例符合预期
          </span>
        </el-form-item>

        <el-form-item label="派单倒计时（秒）">
          <el-input-number
            v-model="form.dispatchExpireSeconds"
            :min="1"
            :max="600"
            :precision="0"
            :controls="false"
            class="number-input"
          />
        </el-form-item>
        <el-form-item label="最大重试次数">
          <el-input-number
            v-model="form.maxRetry"
            :min="0"
            :max="10"
            :precision="0"
            :controls="false"
            class="number-input"
          />
        </el-form-item>
        <el-form-item label="抢单半径（米）">
          <el-input-number
            v-model="form.grabRadiusM"
            :min="100"
            :max="50000"
            :precision="0"
            :controls="false"
            class="number-input"
          />
        </el-form-item>
        <el-form-item label="抢单进行中上限">
          <el-input-number
            v-model="form.grabMaxOrders"
            :min="1"
            :max="50"
            :precision="0"
            :controls="false"
            class="number-input"
          />
        </el-form-item>

        <el-form-item label="抢单大厅">
          <el-radio-group v-model="form.grabEnabled">
            <el-radio
              v-for="option in DISPATCH_SWITCH_OPTIONS"
              :key="option.value"
              :value="option.value"
            >
              {{ option.label }}
            </el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="自动派单">
          <el-radio-group v-model="form.autoDispatchEnabled">
            <el-radio
              v-for="option in DISPATCH_SWITCH_OPTIONS"
              :key="option.value"
              :value="option.value"
            >
              {{ option.label }}
            </el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="配置状态">
          <el-radio-group v-model="form.status">
            <el-radio
              v-for="option in DISPATCH_CONFIG_STATUS_OPTIONS"
              :key="option.value"
              :value="option.value"
            >
              {{ option.label }}
            </el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="配置范围 / 门店">
          <span class="mono">
            {{ labelOf(DISPATCH_CONFIG_SCOPE, loaded?.configScope ?? null) }}
            <template v-if="loaded?.configScope === 3"> #{{ loaded?.shopId }}</template>
          </span>
          <span class="muted form-hint-inline">
            这是上次成功加载的配置来源，不代表表单当前未保存的修改
          </span>
        </el-form-item>
      </el-form>

      <div class="form-footer">
        <el-button @click="loadConfig">放弃修改并重新加载</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存派单策略</el-button>
      </div>
    </el-card>
  </div>
</template>

<style scoped>
.block-alert {
  margin-bottom: 12px;
}

.config-form {
  max-width: 920px;
}

.weight-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0 16px;
}

.number-input {
  width: 180px;
}

.id-input {
  width: 140px;
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

.form-footer {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  border-top: 1px solid var(--el-border-color-lighter);
  padding-top: 16px;
  margin-top: 8px;
}

@media (max-width: 900px) {
  .weight-grid {
    grid-template-columns: 1fr;
  }
}
</style>
