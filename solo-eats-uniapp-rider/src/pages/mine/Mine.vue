<template>
  <view class="mine rd-page">
    <view class="mine__head">
      <view class="mine__avatar">{{ avatarText }}</view>
      <view class="mine__identity">
        <text class="mine__name">{{ rider.realName || auth.nickname || '骑手' }}</text>
        <text class="mine__account">账号 {{ auth.nickname || '--' }}</text>
      </view>
      <view class="mine__cert-badge" :class="certClass">{{ certText }}</view>
    </view>

    <view class="rd-card mine__stats">
      <view class="mine__stat">
        <text class="mine__stat-value">{{ rider.todayOrderCount }}</text>
        <text class="mine__stat-label">今日完成</text>
      </view>
      <view class="mine__stat-divider" />
      <view class="mine__stat">
        <text class="mine__stat-value">{{ onTimeRateText }}</text>
        <text class="mine__stat-label">准时率</text>
      </view>
      <view class="mine__stat-divider" />
      <view class="mine__stat">
        <text class="mine__stat-value">—</text>
        <text class="mine__stat-label">评分（平台统计）</text>
      </view>
    </view>

    <view class="rd-card mine__menu">
      <view class="mine__item" @click="goCertStatus">
        <text class="mine__item-label">我的认证</text>
        <view class="mine__item-right">
          <text class="mine__item-value" :class="certClass">{{ certText }}</text>
          <text class="mine__arrow">›</text>
        </view>
      </view>

      <view class="rd-divider" />

      <view class="mine__item" @click="goIncome">
        <text class="mine__item-label">收入明细</text>
        <view class="mine__item-right">
          <text class="mine__item-value">今日 ¥{{ fenToYuan(rider.todayAmount) }}</text>
          <text class="mine__arrow">›</text>
        </view>
      </view>

      <view class="rd-divider" />

      <view class="mine__item">
        <text class="mine__item-label">骑手编号</text>
        <text class="mine__item-value">{{ rider.riderNo || '--' }}</text>
      </view>

      <view class="rd-divider" />

      <view class="mine__item">
        <text class="mine__item-label">健康证到期</text>
        <text class="mine__item-value">{{ rider.healthCertExpireAt || '--' }}</text>
      </view>

      <view class="rd-divider" />

      <view class="mine__item">
        <text class="mine__item-label">语音播报</text>
        <text class="mine__item-value">默认开启（新派单播报）</text>
      </view>
    </view>

    <view v-if="certError" class="mine__error">{{ certError }}</view>

    <button class="rd-btn rd-btn--danger mine__logout" @click="handleLogout">退出登录</button>

    <view class="mine__version">数创星光外卖 · 骑手端 v0.1.0</view>
  </view>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { fetchIncomeOverview } from '@/api/rider'
import { useAuthStore } from '@/stores/auth'
import { useRiderStore } from '@/stores/rider'
import { resetTrackReporter, stopTrackReport } from '@/utils/location'
import { fenToYuan } from '@/utils/money'

const auth = useAuthStore()
const rider = useRiderStore()

const certError = ref('')

const CERT_TEXT: Record<number, string> = {
  '-1': '加载中',
  0: '未提交',
  1: '待审核',
  2: '已通过',
  3: '已驳回',
}

const avatarText = computed(() => {
  const name = rider.realName || auth.nickname || '骑'
  return name.slice(0, 1)
})

const certText = computed(() => CERT_TEXT[String(rider.certStatus)] || '未知')
const certClass = computed(() => {
  if (rider.certStatus === 2) {
    return 'is-ok'
  }
  if (rider.certStatus === 3) {
    return 'is-reject'
  }
  return 'is-pending'
})
const onTimeRateText = computed(() => `${(Number(rider.onTimeRate) || 0).toFixed(1)}%`)

onShow(() => {
  if (!auth.ensureLogin('/pages/mine/Mine')) {
    return
  }
  void refresh()
})

async function refresh(): Promise<void> {
  certError.value = ''
  try {
    await rider.loadCertification()
  } catch (error) {
    certError.value = (error as Error)?.message || '认证信息加载失败'
  }
  try {
    const overview = await fetchIncomeOverview()
    rider.applyIncomeOverview(overview)
  } catch (error) {
    // 收入概览失败不影响个人信息展示
  }
}

function goCertStatus(): void {
  uni.navigateTo({ url: '/pages/cert/CertStatus' })
}

function goIncome(): void {
  uni.navigateTo({ url: '/pages/income/Income' })
}

function handleLogout(): void {
  uni.showModal({
    title: '退出登录',
    content: '退出后将停止听单与轨迹上报，确定退出吗？',
    confirmText: '退出',
    confirmColor: '#D93A2B',
    success: (result) => {
      if (!result.confirm) {
        return
      }
      stopTrackReport()
      resetTrackReporter()
      rider.reset()
      auth.logout()
      uni.reLaunch({ url: '/pages/login/Login' })
    },
  })
}
</script>

<style scoped>
.mine__head {
  display: flex;
  align-items: center;
  gap: 22rpx;
  padding: 32rpx 28rpx;
  border-radius: 24rpx;
  color: #ffffff;
  background: linear-gradient(135deg, #2c4467, #16233a);
}

.mine__avatar {
  width: 104rpx;
  height: 104rpx;
  line-height: 104rpx;
  text-align: center;
  border-radius: 28rpx;
  background-color: rgba(255, 255, 255, 0.18);
  font-size: 46rpx;
  font-weight: 700;
}

.mine__identity {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.mine__name {
  font-size: 34rpx;
  font-weight: 700;
}

.mine__account {
  margin-top: 8rpx;
  font-size: 22rpx;
  opacity: 0.82;
}

.mine__cert-badge {
  padding: 8rpx 18rpx;
  border-radius: 10rpx;
  font-size: 22rpx;
  font-weight: 600;
}

.mine__cert-badge.is-ok {
  background-color: rgba(18, 183, 106, 0.24);
  color: #8df0bd;
}

.mine__cert-badge.is-pending {
  background-color: rgba(255, 178, 122, 0.24);
  color: #ffd2ad;
}

.mine__cert-badge.is-reject {
  background-color: rgba(217, 58, 43, 0.28);
  color: #ffb4ac;
}

.mine__stats {
  display: flex;
  align-items: center;
  margin-top: 20rpx;
  padding: 28rpx 12rpx;
}

.mine__stat {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8rpx;
}

.mine__stat-value {
  font-size: 34rpx;
  font-weight: 700;
  color: #16233a;
}

.mine__stat-label {
  font-size: 21rpx;
  color: #7a8699;
}

.mine__stat-divider {
  width: 1rpx;
  height: 56rpx;
  background-color: #edf1f6;
}

.mine__menu {
  margin-top: 24rpx;
  padding: 4rpx 24rpx;
}

.mine__item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 104rpx;
  gap: 20rpx;
}

.mine__item-label {
  font-size: 28rpx;
  color: #16233a;
  font-weight: 500;
}

.mine__item-right {
  display: flex;
  align-items: center;
  gap: 10rpx;
}

.mine__item-value {
  font-size: 24rpx;
  color: #7a8699;
  text-align: right;
}

.mine__item-value.is-ok {
  color: #067647;
  font-weight: 600;
}

.mine__item-value.is-reject {
  color: #b42318;
  font-weight: 600;
}

.mine__item-value.is-pending {
  color: #a2520a;
  font-weight: 600;
}

.mine__arrow {
  font-size: 34rpx;
  color: #c3ccd9;
}

.mine__error {
  margin-top: 20rpx;
  padding: 16rpx 20rpx;
  border-radius: 12rpx;
  background-color: #fff1f0;
  color: #b42318;
  font-size: 24rpx;
}

.mine__logout {
  margin-top: 36rpx;
}

.mine__version {
  margin-top: 36rpx;
  text-align: center;
  font-size: 21rpx;
  color: #a9b4c4;
}
</style>
