<template>
  <view class="cert rd-page">
    <view v-if="existingStatus === 2" class="cert__banner cert__banner--ok">
      认证已通过，无需重复提交。<text class="cert__link" @click="goStatus">查看认证状态</text>
    </view>
    <view v-else-if="existingStatus === 1" class="cert__banner cert__banner--pending">
      当前资料正在审核中，重新提交会覆盖上一条记录并重置审核状态。
    </view>
    <view v-else-if="existingStatus === 3 && existingRemark" class="cert__banner cert__banner--reject">
      上次驳回原因：{{ existingRemark }}
    </view>

    <view class="rd-card cert__card">
      <view class="cert__card-title">基本信息</view>

      <view class="cert__field">
        <text class="cert__label">真实姓名</text>
        <input
          v-model="form.realName"
          class="cert__input"
          maxlength="64"
          placeholder="与身份证一致"
          placeholder-class="cert__placeholder"
        />
      </view>

      <view class="rd-divider" />

      <view class="cert__field">
        <text class="cert__label">身份证号</text>
        <input
          v-model="form.idCard"
          class="cert__input"
          maxlength="18"
          placeholder="18 位身份证号"
          placeholder-class="cert__placeholder"
        />
      </view>

      <view class="rd-divider" />

      <view class="cert__field">
        <text class="cert__label">车型</text>
        <picker
          class="cert__picker"
          mode="selector"
          :range="VEHICLES"
          range-key="label"
          :value="vehicleIndex"
          @change="onVehicleChange"
        >
          <view class="cert__picker-value">{{ vehicleLabel }}</view>
        </picker>
        <text class="cert__arrow">›</text>
      </view>
    </view>

    <view class="rd-card cert__card">
      <view class="cert__card-title">证件照片</view>
      <view class="cert__uploads">
        <view class="cert__upload" @click="pickImage('front')">
          <image v-if="previews.front" class="cert__image" :src="previews.front" mode="aspectFill" />
          <view v-else class="cert__upload-empty">
            <text class="cert__upload-plus">+</text>
            <text class="cert__upload-text">身份证正面</text>
          </view>
          <view v-if="uploading.front" class="cert__upload-mask">上传中…</view>
        </view>

        <view class="cert__upload" @click="pickImage('back')">
          <image v-if="previews.back" class="cert__image" :src="previews.back" mode="aspectFill" />
          <view v-else class="cert__upload-empty">
            <text class="cert__upload-plus">+</text>
            <text class="cert__upload-text">身份证反面</text>
          </view>
          <view v-if="uploading.back" class="cert__upload-mask">上传中…</view>
        </view>

        <view class="cert__upload" @click="pickImage('health')">
          <image v-if="previews.health" class="cert__image" :src="previews.health" mode="aspectFill" />
          <view v-else class="cert__upload-empty">
            <text class="cert__upload-plus">+</text>
            <text class="cert__upload-text">健康证</text>
          </view>
          <view v-if="uploading.health" class="cert__upload-mask">上传中…</view>
        </view>
      </view>
      <view class="cert__hint">
        支持 jpg / png / webp，单张 ≤10MB；证件信息仅用于平台审核。
      </view>
    </view>

    <view class="rd-card cert__card">
      <view class="cert__card-title">健康证有效期</view>
      <view class="cert__field">
        <text class="cert__label">到期日</text>
        <picker
          class="cert__picker"
          mode="date"
          :value="form.healthCertExpireAt"
          :start="today"
          @change="onDateChange"
        >
          <view class="cert__picker-value" :class="{ 'is-empty': !form.healthCertExpireAt }">
            {{ form.healthCertExpireAt || '请选择日期' }}
          </view>
        </picker>
        <text class="cert__arrow">›</text>
      </view>
      <view class="cert__hint">健康证过期后平台将拒绝新派单，请填写真实有效期。</view>
    </view>

    <view class="cert__spacer" />

    <view class="rd-fixed-bottom">
      <button
        class="rd-btn rd-btn--primary"
        :disabled="submitting || existingStatus === 2"
        @click="handleSubmit"
      >
        {{ submitting ? '提交中…' : '提交审核' }}
      </button>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { submitCertification } from '@/api/rider'
import { chooseAndUploadImage, UPLOAD_BIZ_TYPE } from '@/api/upload'
import { useAuthStore } from '@/stores/auth'
import { useRiderStore } from '@/stores/rider'
import { todayString } from '@/utils/date'

type ImageKind = 'front' | 'back' | 'health'

const auth = useAuthStore()
const rider = useRiderStore()

const VEHICLES = [
  { value: 1, label: '电动车' },
  { value: 2, label: '摩托车' },
  { value: 3, label: '自行车' },
  { value: 4, label: '步行' },
]

const FILE_ID_KEY: Record<
  ImageKind,
  'idCardFrontFileId' | 'idCardBackFileId' | 'healthCertFileId'
> = {
  front: 'idCardFrontFileId',
  back: 'idCardBackFileId',
  health: 'healthCertFileId',
}

const today = todayString()

const form = reactive({
  realName: '',
  idCard: '',
  idCardFrontFileId: null as number | null,
  idCardBackFileId: null as number | null,
  healthCertFileId: null as number | null,
  healthCertExpireAt: '',
  vehicleType: 1,
})

const previews = reactive<Record<ImageKind, string>>({
  front: '',
  back: '',
  health: '',
})

const uploading = reactive<Record<ImageKind, boolean>>({
  front: false,
  back: false,
  health: false,
})

const submitting = ref(false)
const existingStatus = ref(-1)
const existingRemark = ref('')

const vehicleIndex = computed(() =>
  Math.max(
    0,
    VEHICLES.findIndex((item) => item.value === form.vehicleType),
  ),
)
const vehicleLabel = computed(() => VEHICLES[vehicleIndex.value]?.label || '请选择车型')

onLoad(() => {
  if (!auth.ensureLogin('/pages/cert/Cert')) {
    return
  }
  void prefill()
})

/** 驳回/待审核时带回姓名与健康证信息，证件照与身份证号需重新填写 */
async function prefill(): Promise<void> {
  try {
    const view = await rider.loadCertification()
    existingStatus.value = view.certStatus
    existingRemark.value = view.certRemark || ''
    if (view.realName) {
      form.realName = view.realName
    }
    if (view.healthCertExpireAt) {
      form.healthCertExpireAt = String(view.healthCertExpireAt).slice(0, 10)
    }
    if (view.vehicleType) {
      form.vehicleType = view.vehicleType
    }
  } catch (error) {
    // 读不到历史资料不阻断首次填写
  }
}

function toast(message: string): void {
  uni.showToast({ title: message, icon: 'none', duration: 2200 })
}

function onVehicleChange(event: { detail: { value: string | number } }): void {
  const index = Number(event.detail.value)
  const vehicle = VEHICLES[index]
  if (vehicle) {
    form.vehicleType = vehicle.value
  }
}

function onDateChange(event: { detail: { value: string } }): void {
  form.healthCertExpireAt = String(event.detail.value || '')
}

async function pickImage(kind: ImageKind): Promise<void> {
  if (uploading[kind]) {
    return
  }
  uploading[kind] = true
  try {
    const result = await chooseAndUploadImage(UPLOAD_BIZ_TYPE.CERT)
    form[FILE_ID_KEY[kind]] = result.fileId
    previews[kind] = result.localPath
    toast('上传成功')
  } catch (error) {
    const message = (error as Error)?.message || '上传失败'
    // 取消选择不提示
    if (!/取消/.test(message)) {
      toast(message)
    }
  } finally {
    uploading[kind] = false
  }
}

function validate(): string {
  const realName = form.realName.trim()
  if (realName.length < 2) {
    return '请填写真实姓名'
  }
  if (realName.length > 64) {
    return '真实姓名不能超过 64 个字符'
  }
  const idCard = form.idCard.trim().toUpperCase()
  if (!/^\d{17}[\dX]$/.test(idCard)) {
    return '请填写 18 位身份证号'
  }
  if (!form.idCardFrontFileId) {
    return '请上传身份证正面'
  }
  if (!form.idCardBackFileId) {
    return '请上传身份证反面'
  }
  if (!form.healthCertFileId) {
    return '请上传健康证'
  }
  if (!form.healthCertExpireAt) {
    return '请选择健康证到期日'
  }
  if (form.healthCertExpireAt < today) {
    return '健康证已过期，请更换有效证件'
  }
  if (!VEHICLES.some((item) => item.value === form.vehicleType)) {
    return '请选择车型'
  }
  return ''
}

async function handleSubmit(): Promise<void> {
  if (submitting.value || existingStatus.value === 2) {
    return
  }
  const message = validate()
  if (message) {
    toast(message)
    return
  }
  submitting.value = true
  try {
    await submitCertification({
      realName: form.realName.trim(),
      idCard: form.idCard.trim().toUpperCase(),
      idCardFrontFileId: form.idCardFrontFileId,
      idCardBackFileId: form.idCardBackFileId,
      healthCertFileId: form.healthCertFileId,
      healthCertExpireAt: form.healthCertExpireAt,
      vehicleType: form.vehicleType,
    })
    uni.showToast({ title: '已提交，等待审核', icon: 'none', duration: 1600 })
    setTimeout(() => {
      uni.redirectTo({ url: '/pages/cert/CertStatus' })
    }, 800)
  } catch (error) {
    // http 层已提示（含身份证格式、健康证过期等后端校验）
  } finally {
    submitting.value = false
  }
}

function goStatus(): void {
  uni.redirectTo({ url: '/pages/cert/CertStatus' })
}
</script>

<style scoped>
.cert {
  padding-bottom: calc(160rpx + env(safe-area-inset-bottom));
}

.cert__banner {
  padding: 20rpx 24rpx;
  margin-bottom: 24rpx;
  border-radius: 16rpx;
  font-size: 24rpx;
  line-height: 1.7;
}

.cert__banner--ok {
  background-color: #dcfae6;
  color: #067647;
}

.cert__banner--pending {
  background-color: #fff6ea;
  color: #a2520a;
}

.cert__banner--reject {
  background-color: #fff1f0;
  color: #b42318;
}

.cert__link {
  margin-left: 12rpx;
  color: #067647;
  text-decoration: underline;
}

.cert__card {
  margin-bottom: 24rpx;
  padding: 8rpx 24rpx 24rpx;
}

.cert__card-title {
  padding: 20rpx 0 8rpx;
  font-size: 30rpx;
  font-weight: 700;
  color: #16233a;
}

.cert__field {
  display: flex;
  align-items: center;
  min-height: 100rpx;
  gap: 20rpx;
}

.cert__label {
  width: 150rpx;
  font-size: 27rpx;
  color: #55637a;
}

.cert__input {
  flex: 1;
  height: 100rpx;
  font-size: 30rpx;
  color: #16233a;
}

.cert__placeholder {
  color: #a9b4c4;
  font-size: 28rpx;
}

.cert__picker {
  flex: 1;
}

.cert__picker-value {
  font-size: 30rpx;
  color: #16233a;
  padding: 10rpx 0;
}

.cert__picker-value.is-empty {
  color: #a9b4c4;
}

.cert__arrow {
  color: #a9b4c4;
  font-size: 34rpx;
}

.cert__uploads {
  display: flex;
  gap: 20rpx;
  padding-top: 16rpx;
}

.cert__upload {
  position: relative;
  flex: 1;
  height: 190rpx;
  border-radius: 14rpx;
  border: 2rpx dashed #c9d4e2;
  background-color: #f8fafc;
  overflow: hidden;
}

.cert__image {
  width: 100%;
  height: 100%;
}

.cert__upload-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
}

.cert__upload-plus {
  font-size: 52rpx;
  color: #ff8a3d;
  line-height: 1;
}

.cert__upload-text {
  margin-top: 12rpx;
  font-size: 22rpx;
  color: #7a8699;
}

.cert__upload-mask {
  position: absolute;
  left: 0;
  right: 0;
  top: 0;
  bottom: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  background-color: rgba(22, 35, 58, 0.55);
  color: #ffffff;
  font-size: 24rpx;
}

.cert__hint {
  margin-top: 18rpx;
  font-size: 22rpx;
  color: #8d99aa;
  line-height: 1.6;
}

.cert__spacer {
  height: 40rpx;
}
</style>
