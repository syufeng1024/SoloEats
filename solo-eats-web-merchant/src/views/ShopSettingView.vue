<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { Plus, Refresh } from '@element-plus/icons-vue'
import { useAuthStore } from '@/stores/auth'
import { useShopStore } from '@/stores/shop'
import {
  getShopDetail,
  saveMerchantBusinessHours,
  updateMerchantShop,
  updateMerchantShopStatus,
} from '@/api/shop'
import type { MerchantBusinessHourPayload, MerchantSaveShopPayload } from '@/api/shop'
import type { ShopDetailView } from '@/api/types'
import { centsToYuan, yuanToFen } from '@/utils/money'

const DAY_OPTIONS = [
  { value: 1, label: '周一' },
  { value: 2, label: '周二' },
  { value: 3, label: '周三' },
  { value: 4, label: '周四' },
  { value: 5, label: '周五' },
  { value: 6, label: '周六' },
  { value: 7, label: '周日' },
]

const STATUS_OPTIONS = [
  { value: 1, label: '营业', type: 'success' as const },
  { value: 0, label: '休息', type: 'info' as const },
  { value: 2, label: '打烊', type: 'warning' as const },
]

interface ShopFormState {
  shopName: string
  contactName: string
  contactPhone: string
  province: string
  city: string
  district: string
  address: string
  lng: number | null
  lat: number | null
  logoUrl: string
  coverUrl: string
  announcement: string
  deliveryTypeValues: number[]
  minOrderAmountYuan: number
  deliveryFeeYuan: number
  packagingFeeYuan: number
  prepMinutes: number
  deliveryRadiusM: number
}

interface HourRow {
  id: number
  dayOfWeek: number
  start: string
  end: string
}

let nextHourId = 1

const auth = useAuthStore()
const shop = useShopStore()

const shopId = computed(() => shop.currentShopId ?? auth.shopId ?? null)
const detail = ref<ShopDetailView | null>(null)
const loading = ref(false)
const loadError = ref('')
const savingBasic = ref(false)
const savingHours = ref(false)
const statusAction = ref<number | null>(null)

const formRef = ref<FormInstance>()
const form = reactive<ShopFormState>({
  shopName: '',
  contactName: '',
  contactPhone: '',
  province: '',
  city: '',
  district: '',
  address: '',
  lng: null,
  lat: null,
  logoUrl: '',
  coverUrl: '',
  announcement: '',
  deliveryTypeValues: [1],
  minOrderAmountYuan: 0,
  deliveryFeeYuan: 0,
  packagingFeeYuan: 0,
  prepMinutes: 30,
  deliveryRadiusM: 3000,
})

const hours = ref<HourRow[]>([])
const hoursError = ref('')

const currentStatus = computed(() => detail.value?.status ?? null)
const statusText = computed(() => detail.value?.statusText ?? '未加载')

const rules: FormRules = {
  shopName: [
    { required: true, message: '请输入门店名称', trigger: 'blur' },
    { max: 128, message: '门店名称最长 128 字符', trigger: 'blur' },
  ],
  address: [
    { required: true, message: '请输入门店地址', trigger: 'blur' },
    { max: 255, message: '门店地址最长 255 字符', trigger: 'blur' },
  ],
  contactPhone: [
    {
      validator: (_rule, value, callback) => {
        const phone = String(value ?? '').trim()
        if (phone && !/^1[3-9]\d{9}$/.test(phone)) {
          callback(new Error('联系电话格式不正确'))
          return
        }
        callback()
      },
      trigger: 'blur',
    },
  ],
  lng: [
    {
      validator: (_rule, value, callback) => {
        if (typeof value !== 'number' || value < -180 || value > 180) {
          callback(new Error('经度取值 -180~180'))
          return
        }
        callback()
      },
      trigger: 'blur',
    },
  ],
  lat: [
    {
      validator: (_rule, value, callback) => {
        if (typeof value !== 'number' || value < -90 || value > 90) {
          callback(new Error('纬度取值 -90~90'))
          return
        }
        callback()
      },
      trigger: 'blur',
    },
  ],
  deliveryTypeValues: [
    {
      validator: (_rule, value, callback) => {
        if (!Array.isArray(value) || value.length === 0) {
          callback(new Error('至少选择一种履约方式'))
          return
        }
        callback()
      },
      trigger: 'change',
    },
  ],
  prepMinutes: [
    { required: true, message: '请输入预计出餐时长', trigger: 'blur' },
  ],
  deliveryRadiusM: [
    { required: true, message: '请输入配送半径', trigger: 'blur' },
  ],
}

function applyDetail(data: ShopDetailView): void {
  detail.value = data
  form.shopName = data.shopName
  form.contactName = data.contactName ?? ''
  form.contactPhone = data.contactPhone ?? ''
  form.province = data.province ?? ''
  form.city = data.city ?? ''
  form.district = data.district ?? ''
  form.address = data.address
  form.lng = data.lng ?? null
  form.lat = data.lat ?? null
  form.logoUrl = data.logoUrl ?? ''
  form.coverUrl = data.coverUrl ?? ''
  form.announcement = data.announcement ?? ''
  form.deliveryTypeValues = [1, 2].filter((bit) => (data.deliveryTypes & bit) === bit)
  form.minOrderAmountYuan = centsToYuan(data.minOrderAmount)
  form.deliveryFeeYuan = centsToYuan(data.deliveryFee)
  form.packagingFeeYuan = centsToYuan(data.packagingFee)
  form.prepMinutes = data.prepMinutes
  form.deliveryRadiusM = data.deliveryRadiusM
  hours.value = (data.businessHours ?? []).map((hour) => ({
    id: nextHourId++,
    dayOfWeek: hour.dayOfWeek,
    start: hour.startTime.slice(0, 5),
    end: hour.endTime.slice(0, 5),
  }))
  hoursError.value = ''
}

async function loadDetail(): Promise<void> {
  const id = shopId.value
  if (id === null) {
    detail.value = null
    return
  }
  loading.value = true
  loadError.value = ''
  try {
    applyDetail(await getShopDetail(id))
  } catch (error) {
    loadError.value = error instanceof Error ? error.message : '门店详情加载失败'
    detail.value = null
  } finally {
    loading.value = false
  }
}

function buildPayload(): MerchantSaveShopPayload {
  const deliveryTypes = form.deliveryTypeValues.reduce((sum, bit) => sum + bit, 0)
  return {
    shopName: form.shopName.trim(),
    province: form.province.trim() || null,
    city: form.city.trim() || null,
    district: form.district.trim() || null,
    address: form.address.trim(),
    lng: form.lng ?? 0,
    lat: form.lat ?? 0,
    contactName: form.contactName.trim() || null,
    contactPhone: form.contactPhone.trim() || null,
    logoUrl: form.logoUrl.trim() || null,
    coverUrl: form.coverUrl.trim() || null,
    announcement: form.announcement.trim() || null,
    deliveryTypes,
    minOrderAmount: yuanToFen(form.minOrderAmountYuan),
    deliveryFee: yuanToFen(form.deliveryFeeYuan),
    packagingFee: yuanToFen(form.packagingFeeYuan),
    prepMinutes: form.prepMinutes,
    deliveryRadiusM: form.deliveryRadiusM,
  }
}

async function saveBasic(): Promise<void> {
  const id = shopId.value
  if (id === null) {
    return
  }
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) {
    return
  }
  savingBasic.value = true
  try {
    applyDetail(await updateMerchantShop(id, buildPayload()))
    ElMessage.success('门店基本信息已保存')
  } catch {
    // 拦截器已提示
  } finally {
    savingBasic.value = false
  }
}

async function changeStatus(status: number): Promise<void> {
  const id = shopId.value
  if (id === null || status === currentStatus.value) {
    return
  }
  statusAction.value = status
  try {
    await updateMerchantShopStatus(id, status)
    ElMessage.success('营业状态已更新')
    await loadDetail()
  } catch {
    // 拦截器已提示
  } finally {
    statusAction.value = null
  }
}

function addHour(): void {
  hours.value.push({ id: nextHourId++, dayOfWeek: 1, start: '09:00', end: '22:00' })
}

function removeHour(index: number): void {
  hours.value.splice(index, 1)
}

function validateHours(): MerchantBusinessHourPayload[] | null {
  if (hours.value.length === 0) {
    hoursError.value = '至少保留一条营业时段；不配置营业时段会被后端判定为不限时段'
    return null
  }
  const payload: MerchantBusinessHourPayload[] = []
  for (const row of hours.value) {
    if (!row.start || !row.end) {
      hoursError.value = '每条营业时段都必须填写开始与结束时间'
      return null
    }
    if (row.start === row.end) {
      hoursError.value = '开始时间与结束时间不能相同'
      return null
    }
    payload.push({ dayOfWeek: row.dayOfWeek, startTime: row.start, endTime: row.end })
  }
  hoursError.value = ''
  return payload
}

async function saveHours(): Promise<void> {
  const id = shopId.value
  const payload = validateHours()
  if (id === null || payload === null) {
    return
  }
  savingHours.value = true
  try {
    await saveMerchantBusinessHours(id, payload)
    ElMessage.success('营业时段已保存')
    await loadDetail()
  } catch {
    // 拦截器已提示
  } finally {
    savingHours.value = false
  }
}

watch(shopId, () => {
  void loadDetail()
})

onMounted(loadDetail)
</script>

<template>
  <div class="page">
    <div class="page-lead">
      <div>
        <h2>门店设置</h2>
        <p>
          门店 ID <span class="mono">{{ shopId ?? '未绑定' }}</span>。详情读取
          <span class="mono">GET /api/v1/c/shops/{{ shopId ?? '{id}' }}</span>，保存调用
          <span class="mono">PUT /api/v1/merchant/shops/{{ shopId ?? '{id}' }}</span>；
          营业时段为全量替换，后端按 endTime &lt; startTime 自动识别跨天。
        </p>
      </div>
      <el-button :icon="Refresh" :loading="loading" @click="loadDetail">刷新</el-button>
    </div>

    <el-alert v-if="loadError" type="error" :closable="false" show-icon :title="loadError" />

    <el-empty
      v-if="shopId === null && !loading"
      description="当前令牌未绑定门店。请先在右上角选择门店，或使用门店账号登录。"
    />

    <template v-if="detail">
      <el-card shadow="never">
        <div class="card-title">营业状态</div>
        <div class="status-row">
          <span class="muted">当前状态</span>
          <el-tag type="success" effect="plain">{{ statusText }}</el-tag>
          <span class="muted status-hint">
            商家端只能切换 休息(0) / 营业(1) / 打烊(2)，停用(3) 由平台运营设置。
          </span>
        </div>
        <div class="status-actions">
          <el-button
            v-for="option in STATUS_OPTIONS"
            :key="option.value"
            :type="option.type"
            :plain="currentStatus !== option.value"
            :loading="statusAction === option.value"
            :disabled="statusAction !== null || currentStatus === option.value"
            @click="changeStatus(option.value)"
          >
            {{ option.label }}
          </el-button>
        </div>
      </el-card>

      <el-card shadow="never">
        <div class="card-title">门店基本信息</div>
        <el-form
          ref="formRef"
          :model="form"
          :rules="rules"
          label-width="120px"
          class="shop-form"
        >
          <div class="form-grid">
            <el-form-item label="门店名称" prop="shopName">
              <el-input v-model="form.shopName" maxlength="128" show-word-limit />
            </el-form-item>
            <el-form-item label="联系电话" prop="contactPhone">
              <el-input v-model="form.contactPhone" maxlength="11" placeholder="11 位手机号" />
            </el-form-item>
            <el-form-item label="联系人">
              <el-input v-model="form.contactName" maxlength="64" />
            </el-form-item>
            <el-form-item label="省 / 市 / 区">
              <div class="region-row">
                <el-input v-model="form.province" maxlength="64" placeholder="省" />
                <el-input v-model="form.city" maxlength="64" placeholder="市" />
                <el-input v-model="form.district" maxlength="64" placeholder="区" />
              </div>
            </el-form-item>
            <el-form-item label="详细地址" prop="address" class="span-2">
              <el-input v-model="form.address" maxlength="255" show-word-limit />
            </el-form-item>
            <el-form-item label="经度" prop="lng">
              <el-input-number
                v-model="form.lng"
                :precision="6"
                :step="0.000001"
                :controls="false"
                class="num-input"
              />
            </el-form-item>
            <el-form-item label="纬度" prop="lat">
              <el-input-number
                v-model="form.lat"
                :precision="6"
                :step="0.000001"
                :controls="false"
                class="num-input"
              />
            </el-form-item>
            <el-form-item label="履约方式" prop="deliveryTypeValues">
              <el-checkbox-group v-model="form.deliveryTypeValues">
                <el-checkbox :value="1">平台配送</el-checkbox>
                <el-checkbox :value="2">到店自取</el-checkbox>
              </el-checkbox-group>
            </el-form-item>
            <el-form-item label="起送金额（元）">
              <el-input-number
                v-model="form.minOrderAmountYuan"
                :min="0"
                :precision="2"
                class="num-input"
              />
            </el-form-item>
            <el-form-item label="配送费（元）">
              <el-input-number
                v-model="form.deliveryFeeYuan"
                :min="0"
                :precision="2"
                class="num-input"
              />
            </el-form-item>
            <el-form-item label="打包费（元）">
              <el-input-number
                v-model="form.packagingFeeYuan"
                :min="0"
                :precision="2"
                class="num-input"
              />
            </el-form-item>
            <el-form-item label="出餐时长（分）" prop="prepMinutes">
              <el-input-number v-model="form.prepMinutes" :min="1" :max="180" class="num-input" />
            </el-form-item>
            <el-form-item label="配送半径（米）" prop="deliveryRadiusM">
              <el-input-number
                v-model="form.deliveryRadiusM"
                :min="100"
                :max="50000"
                :step="100"
                class="num-input"
              />
            </el-form-item>
            <el-form-item label="Logo 地址">
              <el-input v-model="form.logoUrl" maxlength="512" placeholder="https://..." />
            </el-form-item>
            <el-form-item label="封面地址">
              <el-input v-model="form.coverUrl" maxlength="512" placeholder="https://..." />
            </el-form-item>
            <el-form-item label="门店公告" class="span-2">
              <el-input
                v-model="form.announcement"
                type="textarea"
                :rows="2"
                maxlength="500"
                show-word-limit
              />
            </el-form-item>
          </div>
        </el-form>
        <div class="form-actions">
          <el-button type="primary" :loading="savingBasic" @click="saveBasic">
            保存基本信息
          </el-button>
        </div>
      </el-card>

      <el-card shadow="never">
        <div class="card-title">营业时段</div>
        <p class="hint">
          全量替换：保存时先删除本店全部时段再按当前列表写入。跨天时段（如 22:00-02:00）直接填写即可。
        </p>
        <el-alert
          v-if="hoursError"
          type="warning"
          :closable="false"
          show-icon
          :title="hoursError"
          class="hours-alert"
        />
        <el-table :data="hours" border row-key="id" empty-text="暂无营业时段">
          <el-table-column label="星期" width="140">
            <template #default="{ row }">
              <el-select v-model="row.dayOfWeek" class="day-select">
                <el-option
                  v-for="day in DAY_OPTIONS"
                  :key="day.value"
                  :label="day.label"
                  :value="day.value"
                />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column label="开始时间" width="180">
            <template #default="{ row }">
              <el-time-picker
                v-model="row.start"
                format="HH:mm"
                value-format="HH:mm"
                placeholder="开始"
                class="time-picker"
              />
            </template>
          </el-table-column>
          <el-table-column label="结束时间" width="180">
            <template #default="{ row }">
              <el-time-picker
                v-model="row.end"
                format="HH:mm"
                value-format="HH:mm"
                placeholder="结束"
                class="time-picker"
              />
            </template>
          </el-table-column>
          <el-table-column label="说明" min-width="180">
            <template #default="{ row }">
              <span v-if="row.start && row.end && row.end < row.start" class="muted">
                跨天：{{ row.start }} 至次日 {{ row.end }}
              </span>
              <span v-else class="muted">当日 {{ row.start || '—' }} 至 {{ row.end || '—' }}</span>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="90" align="center">
            <template #default="{ $index }">
              <el-button link type="danger" @click="removeHour($index)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
        <div class="form-actions">
          <el-button :icon="Plus" @click="addHour">新增时段</el-button>
          <el-button type="primary" :loading="savingHours" @click="saveHours">
            保存营业时段
          </el-button>
        </div>
      </el-card>
    </template>
  </div>
</template>

<style scoped>
.status-row {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.status-hint {
  font-size: 12px;
}

.status-actions {
  display: flex;
  gap: 10px;
  margin-top: 14px;
  flex-wrap: wrap;
}

.shop-form {
  max-width: 980px;
}

.form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  column-gap: 24px;
}

.form-grid .span-2 {
  grid-column: 1 / -1;
}

.region-row {
  display: flex;
  gap: 8px;
  width: 100%;
}

.num-input {
  width: 180px;
}

.form-actions {
  display: flex;
  gap: 10px;
  justify-content: flex-end;
  margin-top: 16px;
}

.hint {
  color: var(--app-muted);
  font-size: 13px;
  line-height: 1.7;
  margin-bottom: 12px;
}

.hours-alert {
  margin-bottom: 12px;
}

.day-select {
  width: 110px;
}

.time-picker {
  width: 150px;
}

@media (max-width: 900px) {
  .form-grid {
    grid-template-columns: 1fr;
  }
}
</style>
