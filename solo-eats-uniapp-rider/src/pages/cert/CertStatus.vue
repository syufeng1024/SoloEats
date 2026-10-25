<template>
  <view class="status rd-page">
    <view v-if="loading" class="rd-loading">正在查询认证状态…</view>

    <view v-else-if="error" class="rd-error">
      <text class="rd-error__msg">{{ error }}</text>
      <view class="rd-error__retry" @click="load">重新加载</view>
      <view class="status__relogin" @click="relogin">重新登录</view>
    </view>

    <template v-else>
      <view class="status__hero" :class="heroClass">
        <text class="status__hero-title">{{ statusText }}</text>
        <text class="status__hero-desc">{{ statusDesc }}</text>
      </view>

      <view v-if="cert.certStatus === 3 && cert.certRemark" class="status__reject">
        <text class="status__reject-title">驳回原因</text>
        <text class="status__reject-text">{{ cert.certRemark }}</text>
      </view>

      <view v-if="cert.certStatus === 2 && healthWarning" class="status__warn">
        {{ healthWarning }}
      </view>

      <view class="rd-card status__card">
        <view class="status__row">
          <text class="status__label">真实姓名</text>
          <text class="status__value">{{ cert.realName || '--' }}</text>
        </view>
        <view class="rd-divider" />
        <view class="status__row">
          <text class="status__label">骑手编号</text>
          <text class="status__value">{{ cert.riderNo || '--' }}</text>
        </view>
        <view class="rd-divider" />
        <view class="status__row">
          <text class="status__label">车型</text>
          <text class="status__value">{{ vehicleText }}</text>
        </view>
        <view class="rd-divider" />
        <view class="status__row">
          <text class="status__label">健康证到期</text>
          <text class="status__value">{{ cert.healthCertExpireAt || '--' }}</text>
        </view>
      </view>

      <view v-if="cert.certStatus === 1" class="status__note">
        资料已提交，平台审核通常在 1 个工作日内完成。审核通过后即可打开听单开关。
      </view>

      <view class="status__actions">
        <button
          v-if="cert.certStatus === 0 || cert.certStatus === 3"
          class="rd-btn rd-btn--primary"
          @click="goCert"
        >
          {{ cert.certStatus === 3 ? '重新提交认证' : '去实名认证' }}
        </button>

        <button
          v-else-if="cert.certStatus === 1"
          class="rd-btn rd-btn--plain"
          :disabled="loading"
          @click="load"
        >
          刷新审核状态
        </button>

        <button v-else class="rd-btn rd-btn--primary" @click="goHome">
          开始接单
        </button>

        <button v-if="cert.certStatus !== 2" class="rd-btn rd-btn--ghost" @click="goHome">
          先回首页
        </button>
      </view>
    </template>
  </view>
</template>

<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { useAuthStore } from '@/stores/auth'
import { useRiderStore } from '@/stores/rider'
import type { RiderCertStatusVO } from '@/api/types'

const auth = useAuthStore()
const rider = useRiderStore()

const loading = ref(false)
const error = ref('')

const cert = reactive<RiderCertStatusVO>({
  riderId: null,
  riderNo: null,
  realName: null,
  certStatus: 0,
  certRemark: null,
  healthCertExpireAt: null,
  vehicleType: null,
})

const STATUS_TEXT: Record<number, string> = {
  0: '尚未提交认证',
  1: '资料审核中',
  2: '认证已通过',
  3: '认证被驳回',
}

const STATUS_DESC: Record<number, string> = {
  0: '完成实名与健康证认证后才能听单接单',
  1: '请耐心等待平台审核，审核通过后可上线听单',
  2: '可正常听单、抢单与配送',
  3: '请按驳回原因修改资料后重新提交',
}

const VEHICLE_TEXT: Record<number, string> = {
  1: '电动车',
  2: '摩托车',
  3: '自行车',
  4: '步行',
}

const statusText = computed(() => STATUS_TEXT[cert.certStatus] || '未知状态')
const statusDesc = computed(() => STATUS_DESC[cert.certStatus] || '')
const vehicleText = computed(() => VEHICLE_TEXT[cert.vehicleType ?? 0] || '--')
const heroClass = computed(() => `is-${cert.certStatus}`)

/** 健康证到期提醒：过期或 30 天内到期时给骑手明确提示 */
const healthWarning = computed(() => {
  const days = daysUntil(cert.healthCertExpireAt)
  if (days === null) {
    return '健康证到期日缺失，请重新提交认证'
  }
  if (days < 0) {
    return `健康证已过期 ${Math.abs(days)} 天，平台将拒绝新派单，请联系运营更新证件`
  }
  if (days <= 30) {
    return `健康证还有 ${days} 天到期，请尽快更新`
  }
  return ''
})

function daysUntil(dateText: string | null): number | null {
  if (!dateText) {
    return null
  }
  const match = /^(\d{4})-(\d{2})-(\d{2})/.exec(dateText)
  if (!match) {
    return null
  }
  const target = new Date(Number(match[1]), Number(match[2]) - 1, Number(match[3])).getTime()
  const today = new Date()
  const todayStart = new Date(today.getFullYear(), today.getMonth(), today.getDate()).getTime()
  return Math.round((target - todayStart) / 86_400_000)
}

onShow(() => {
  if (!auth.ensureLogin('/pages/cert/CertStatus')) {
    return
  }
  void load()
})

async function load(): Promise<void> {
  if (loading.value) {
    return
  }
  loading.value = true
  error.value = ''
  try {
    const view = await rider.loadCertification()
    Object.assign(cert, view)
  } catch (err) {
    error.value = (err as Error)?.message || '认证状态查询失败'
  } finally {
    loading.value = false
  }
}

function goCert(): void {
  uni.navigateTo({ url: '/pages/cert/Cert' })
}

/** 认证接口 403/401 时避免困在错误页 */
function relogin(): void {
  auth.logout()
  rider.reset()
  uni.reLaunch({ url: '/pages/login/Login' })
}

function goHome(): void {
  uni.switchTab({ url: '/pages/home/Home' })
}
</script>

<style scoped>
.status__hero {
  padding: 36rpx 32rpx;
  border-radius: 20rpx;
  color: #ffffff;
  background: linear-gradient(135deg, #3b5a86, #16233a);
  margin-bottom: 24rpx;
}

.status__hero.is-2 {
  background: linear-gradient(135deg, #0f9d6b, #08704b);
}

.status__hero.is-3 {
  background: linear-gradient(135deg, #d93a2b, #9f1f14);
}

.status__hero.is-1 {
  background: linear-gradient(135deg, #e8a13a, #c2761a);
}

.status__hero-title {
  display: block;
  font-size: 40rpx;
  font-weight: 700;
}

.status__hero-desc {
  display: block;
  margin-top: 12rpx;
  font-size: 26rpx;
  line-height: 1.6;
  opacity: 0.92;
}

.status__reject {
  padding: 24rpx;
  margin-bottom: 24rpx;
  border-radius: 16rpx;
  background-color: #fff1f0;
  border: 2rpx solid #ffc9c4;
}

.status__reject-title {
  display: block;
  font-size: 26rpx;
  font-weight: 600;
  color: #b42318;
  margin-bottom: 8rpx;
}

.status__reject-text {
  font-size: 26rpx;
  color: #7a2018;
  line-height: 1.6;
}

.status__warn {
  padding: 20rpx 24rpx;
  margin-bottom: 24rpx;
  border-radius: 16rpx;
  background-color: #fff6ea;
  color: #a2520a;
  font-size: 24rpx;
  line-height: 1.6;
}

.status__card {
  padding: 8rpx 24rpx;
}

.status__row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 96rpx;
  gap: 24rpx;
}

.status__label {
  font-size: 26rpx;
  color: #7a8699;
}

.status__value {
  flex: 1;
  text-align: right;
  font-size: 28rpx;
  color: #16233a;
  font-weight: 500;
  word-break: break-all;
}

.status__note {
  margin-top: 24rpx;
  padding: 20rpx 24rpx;
  border-radius: 16rpx;
  background-color: #eef4ff;
  color: #2c4a75;
  font-size: 24rpx;
  line-height: 1.7;
}

.status__actions {
  margin-top: 40rpx;
  display: flex;
  flex-direction: column;
  gap: 20rpx;
}

.status__relogin {
  display: inline-block;
  margin-top: 28rpx;
  color: #55637a;
  font-size: 24rpx;
  text-decoration: underline;
}
</style>
