<template>
  <view class="mine">
    <view class="mine__header">
      <view class="mine__avatar">{{ avatarLetter }}</view>
      <view class="mine__user">
        <text class="mine__name">{{ displayName }}</text>
        <view v-if="auth.isLogin" class="mine__member">
          <text class="mine__level">{{ levelName }}</text>
          <text v-if="profile" class="mine__growth">成长值 {{ profile.growth }}</text>
        </view>
        <text v-else class="mine__hint">登录后查看订单、钱包与会员权益</text>
      </view>
      <view v-if="!auth.isLogin" class="mine__login" @click="goLogin">登录/注册</view>
    </view>

    <view v-if="auth.isLogin && profile" class="mine__progress">
      <view class="mine__progress-track">
        <view class="mine__progress-bar" :style="{ width: `${progressPercent}%` }" />
      </view>
      <text class="mine__progress-text">{{ progressText }}</text>
    </view>

    <view v-if="loadError" class="mine__error">
      <text>{{ loadError }}</text>
      <text class="mine__error-retry" @click="loadMine">重新加载</text>
    </view>

    <view class="se-card mine__stats">
      <view class="mine__stat" @click="openWallet">
        <text class="mine__stat-value">{{ walletText }}</text>
        <text class="mine__stat-label">钱包余额</text>
      </view>
      <view class="mine__stat" @click="goPoints">
        <text class="mine__stat-value">{{ pointText }}</text>
        <text class="mine__stat-label">积分</text>
      </view>
      <view class="mine__stat" @click="goCheckin">
        <text class="mine__stat-value" :class="{ 'is-active': checkinStatus?.checkedInToday }">
          {{ checkinText }}
        </text>
        <text class="mine__stat-label">{{ checkinLabel }}</text>
      </view>
    </view>

    <view class="se-card mine__grid">
      <view class="mine__grid-item" @click="goMember">
        <text class="mine__grid-icon">会</text>
        <text class="mine__grid-text">会员中心</text>
      </view>
      <view class="mine__grid-item" @click="goMarketing">
        <text class="mine__grid-icon">活</text>
        <text class="mine__grid-text">活动中心</text>
      </view>
      <view class="mine__grid-item" @click="goOrders">
        <text class="mine__grid-icon">单</text>
        <text class="mine__grid-text">我的订单</text>
      </view>
      <view class="mine__grid-item" @click="openAddress">
        <text class="mine__grid-icon">址</text>
        <text class="mine__grid-text">收货地址</text>
      </view>
      <view class="mine__grid-item" @click="goReviews">
        <text class="mine__grid-icon">评</text>
        <text class="mine__grid-text">我的评价</text>
      </view>
      <view class="mine__grid-item" @click="goFavorites">
        <text class="mine__grid-icon">藏</text>
        <text class="mine__grid-text">我的收藏</text>
      </view>
      <view class="mine__grid-item" @click="goAfterSale">
        <text class="mine__grid-icon">售</text>
        <text class="mine__grid-text">售后记录</text>
      </view>
      <view class="mine__grid-item" @click="goTickets">
        <text class="mine__grid-icon">工</text>
        <text class="mine__grid-text">我的工单</text>
      </view>
      <view class="mine__grid-item is-disabled" @click="openCoupons">
        <view class="mine__grid-icon-wrap">
          <text class="mine__grid-icon">券</text>
        </view>
        <text class="mine__grid-text">优惠券</text>
        <text class="mine__grid-badge">待开放</text>
      </view>
      <view class="mine__grid-item" @click="goHelp">
        <text class="mine__grid-icon">帮</text>
        <text class="mine__grid-text">帮助中心</text>
      </view>
      <view class="mine__grid-item" @click="goFeedback">
        <text class="mine__grid-icon">反</text>
        <text class="mine__grid-text">意见反馈</text>
      </view>
      <view class="mine__grid-item" @click="goSettings">
        <text class="mine__grid-icon">设</text>
        <text class="mine__grid-text">设置</text>
      </view>
      <view class="mine__grid-item" @click="goAbout">
        <text class="mine__grid-icon">关</text>
        <text class="mine__grid-text">关于</text>
      </view>
    </view>

    <view class="mine__version">数创星光外卖 · v1.0.0</view>
  </view>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { fetchWallet } from '@/api/user'
import {
  fetchCheckinStatus,
  fetchMemberProfile,
  fetchPointAccount,
} from '@/api/mine'
import type { CheckinStatusVO, MemberProfileVO, PointAccountVO } from '@/api/mine'
import type { WalletVO } from '@/api/types'
import { fenToYuan } from '@/utils/money'
import { useAuthStore } from '@/stores/auth'

/**
 * 「我的」聚合页。
 *
 * 会员进度：/c/member/profile 返回的是 growth（当前成长值）与 nextThreshold（下一档门槛），
 * 不是 0~100 的百分比，所以这里按 growth / nextThreshold 自行换算，不用 ×100。
 * 已达最高档（maxLevel=true）或 nextThreshold<=0 时按 100% 展示。
 *
 * 积分与签到入口跳转到本批新增的独立页面（/pages/member/Points、/pages/member/Checkin），
 * 页内不再展开积分明细；顶部统计仍只读余额与签到状态，保持「我的」首屏轻量。
 */

const auth = useAuthStore()

const profile = ref<MemberProfileVO | null>(null)
const wallet = ref<WalletVO | null>(null)
const points = ref<PointAccountVO | null>(null)
const checkinStatus = ref<CheckinStatusVO | null>(null)
const loading = ref(false)
const loadError = ref('')

let loadSequence = 0

const displayName = computed(() => {
  if (!auth.isLogin) {
    return '未登录'
  }
  return auth.nickname || `用户 ${auth.userId ?? ''}`.trim()
})

const avatarLetter = computed(() => (auth.isLogin ? displayName.value.slice(0, 1) || '数' : '数'))

const levelName = computed(() => profile.value?.levelName || (auth.isLogin ? '普通会员' : ''))

const progressPercent = computed(() => {
  const current = profile.value
  if (!current) {
    return 0
  }
  if (current.maxLevel || current.nextThreshold <= 0) {
    return 100
  }
  const percent = Math.round((current.growth / current.nextThreshold) * 100)
  return Math.max(0, Math.min(100, percent))
})

const progressText = computed(() => {
  const current = profile.value
  if (!current) {
    return ''
  }
  if (current.maxLevel || current.nextThreshold <= 0) {
    return `已达最高等级 · 成长值 ${current.growth}`
  }
  return `成长值 ${current.growth} / ${current.nextThreshold}，距下一等级还差 ${Math.max(0, current.growthToNext)}`
})

const walletText = computed(() => {
  if (!auth.isLogin) {
    return '登录查看'
  }
  return wallet.value ? `¥${fenToYuan(wallet.value.balance)}` : '--'
})

const pointText = computed(() => {
  if (!auth.isLogin) {
    return '登录查看'
  }
  return points.value ? String(points.value.balance) : '--'
})

const checkinText = computed(() => {
  if (!auth.isLogin) {
    return '登录查看'
  }
  const status = checkinStatus.value
  if (!status) {
    return '--'
  }
  return status.checkedInToday ? `${status.continuousDays} 天` : '去签到'
})

const checkinLabel = computed(() => {
  const status = checkinStatus.value
  if (status?.checkedInToday) {
    return '连续签到'
  }
  if (status && status.todayPointAward) {
    return `签到 +${status.todayPointAward} 积分`
  }
  return '签到状态'
})

onShow(() => {
  loadMine()
})

async function loadMine(): Promise<void> {
  if (!auth.isLogin) {
    // 让退出登录前发出的请求结果失效，避免旧账号数据回填到已登出的页面
    loadSequence += 1
    loading.value = false
    profile.value = null
    wallet.value = null
    points.value = null
    checkinStatus.value = null
    loadError.value = ''
    return
  }
  if (loading.value) {
    return
  }
  const sequence = ++loadSequence
  loading.value = true
  loadError.value = ''
  const results = await Promise.allSettled([
    fetchMemberProfile(true),
    fetchWallet(true),
    fetchPointAccount(true),
    fetchCheckinStatus(true),
  ])
  if (sequence !== loadSequence) {
    loading.value = false
    return
  }
  const [profileResult, walletResult, pointResult, checkinResult] = results
  if (profileResult.status === 'fulfilled') {
    profile.value = profileResult.value
  }
  if (walletResult.status === 'fulfilled') {
    wallet.value = walletResult.value
  }
  if (pointResult.status === 'fulfilled') {
    points.value = pointResult.value
  }
  if (checkinResult.status === 'fulfilled') {
    checkinStatus.value = checkinResult.value
  }
  // 任一请求失败都提示一次，但不把已有数据清空
  if (results.some((item) => item.status === 'rejected')) {
    loadError.value = '部分数据加载失败，可点击重试'
  }
  loading.value = false
}

function goLogin(): void {
  auth.ensureLogin('/pages/mine/Mine')
}

function openWallet(): void {
  if (!auth.ensureLogin('/pages/mine/Mine')) {
    return
  }
  uni.navigateTo({ url: '/pages/mine/Wallet' })
}

function goPoints(): void {
  if (!auth.ensureLogin('/pages/mine/Mine')) {
    return
  }
  uni.navigateTo({ url: '/pages/member/Points' })
}

function goCheckin(): void {
  if (!auth.ensureLogin('/pages/mine/Mine')) {
    return
  }
  uni.navigateTo({ url: '/pages/member/Checkin' })
}

function goMember(): void {
  if (!auth.ensureLogin('/pages/mine/Mine')) {
    return
  }
  uni.navigateTo({ url: '/pages/member/Member' })
}

function goMarketing(): void {
  uni.navigateTo({ url: '/pages/marketing/Center' })
}

function goOrders(): void {
  if (!auth.ensureLogin('/pages/mine/Mine')) {
    return
  }
  uni.switchTab({ url: '/pages/order/List' })
}

function openAddress(): void {
  if (!auth.ensureLogin('/pages/mine/Mine')) {
    return
  }
  uni.navigateTo({ url: '/pages/address/List' })
}

function goReviews(): void {
  if (!auth.ensureLogin('/pages/mine/Mine')) {
    return
  }
  uni.navigateTo({ url: '/pages/review/List' })
}

function goFavorites(): void {
  if (!auth.ensureLogin('/pages/mine/Mine')) {
    return
  }
  uni.navigateTo({ url: '/pages/mine/Favorites' })
}

function goAfterSale(): void {
  if (!auth.ensureLogin('/pages/mine/Mine')) {
    return
  }
  uni.navigateTo({ url: '/pages/afterSale/List' })
}

function goTickets(): void {
  if (!auth.ensureLogin('/pages/mine/Mine')) {
    return
  }
  uni.navigateTo({ url: '/pages/mine/Tickets' })
}

function openCoupons(): void {
  uni.showToast({ title: '优惠券功能待开放', icon: 'none' })
}

function goHelp(): void {
  uni.navigateTo({ url: '/pages/mine/Help' })
}

function goFeedback(): void {
  if (!auth.ensureLogin('/pages/mine/Mine')) {
    return
  }
  uni.navigateTo({ url: '/pages/mine/Feedback' })
}

function goSettings(): void {
  uni.navigateTo({ url: '/pages/mine/Settings' })
}

function goAbout(): void {
  uni.navigateTo({ url: '/pages/mine/About' })
}
</script>

<style scoped>
.mine {
  min-height: 100vh;
  padding: 24rpx 24rpx 40rpx;
  /* 同上：为 H5 的 fixed tabBar 预留底部安全间距，
     否则末行「优惠券」下方的「待开放」标签会被 tabBar 压住（非 tab 页/小程序端 var 为 0，无副作用）。
     上/左右内边距保持 24rpx 不变。 */
  padding-bottom: calc(40rpx + var(--window-bottom, 0px));
  background: linear-gradient(180deg, #fff3e6 0%, #f5f5f7 320rpx);
}

.mine__header {
  display: flex;
  align-items: center;
  padding: 24rpx 8rpx 16rpx;
}

.mine__avatar {
  width: 120rpx;
  height: 120rpx;
  line-height: 120rpx;
  text-align: center;
  border-radius: 50%;
  background: linear-gradient(135deg, #ffa24c, #ff7a00);
  color: #ffffff;
  font-size: 48rpx;
  font-weight: 600;
  flex-shrink: 0;
}

.mine__user {
  flex: 1;
  min-width: 0;
  margin-left: 24rpx;
  display: flex;
  flex-direction: column;
}

.mine__name {
  font-size: 36rpx;
  font-weight: 600;
  color: #1f1f1f;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.mine__member {
  display: flex;
  align-items: center;
  margin-top: 8rpx;
  gap: 12rpx;
}

.mine__level {
  padding: 4rpx 14rpx;
  border-radius: 20rpx;
  background-color: #fff2e6;
  color: #ff7a00;
  font-size: 22rpx;
}

.mine__growth {
  color: #8a8a8a;
  font-size: 22rpx;
}

.mine__hint {
  margin-top: 8rpx;
  color: #8a8a8a;
  font-size: 24rpx;
}

.mine__login {
  padding: 14rpx 28rpx;
  border-radius: 32rpx;
  background: linear-gradient(135deg, #ffa24c, #ff7a00);
  color: #ffffff;
  font-size: 26rpx;
  flex-shrink: 0;
}

.mine__progress {
  padding: 0 8rpx 20rpx;
}

.mine__progress-track {
  height: 10rpx;
  border-radius: 5rpx;
  background-color: rgba(255, 122, 0, 0.15);
  overflow: hidden;
}

.mine__progress-bar {
  height: 10rpx;
  border-radius: 5rpx;
  background: linear-gradient(90deg, #ffa24c, #ff7a00);
  transition: width 0.3s ease;
}

.mine__progress-text {
  display: block;
  margin-top: 10rpx;
  color: #a26a2a;
  font-size: 22rpx;
}

.mine__error {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16rpx;
  padding: 14rpx 20rpx;
  border-radius: 8rpx;
  background-color: #fff8ef;
  color: #a26a2a;
  font-size: 22rpx;
}

.mine__error-retry {
  color: #ff7a00;
}

.mine__stats {
  display: flex;
  padding: 28rpx 0;
}

.mine__stat {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  border-right: 1rpx solid #f2f2f4;
}

.mine__stat:last-child {
  border-right: none;
}

.mine__stat-value {
  font-size: 34rpx;
  font-weight: 600;
  color: #1f1f1f;
}

.mine__stat-value.is-active {
  color: #ff7a00;
}

.mine__stat-label {
  margin-top: 6rpx;
  font-size: 22rpx;
  color: #8a8a8a;
}

.mine__grid {
  display: flex;
  flex-wrap: wrap;
  margin-top: 16rpx;
  padding: 28rpx 0 8rpx;
}

.mine__grid-item {
  width: 25%;
  display: flex;
  flex-direction: column;
  align-items: center;
  margin-bottom: 24rpx;
}

.mine__grid-item.is-disabled .mine__grid-icon {
  background-color: #f0f0f0;
  color: #a0a0a0;
}

.mine__grid-item.is-disabled .mine__grid-text {
  color: #a0a0a0;
}

.mine__grid-icon-wrap {
  /* 「待开放」标记已改为图标下方的独立一行（走正常文档流），不再需要绝对定位上下文；
     这里仅保留块级包裹，保证 uni-text 图标色块尺寸稳定。 */
  display: block;
}

.mine__grid-icon {
  /* uni-text 默认 inline，在 .mine__grid-icon-wrap（block）内 width/height 会失效，
     导致「券」色块比兄弟图标小、且 wrap 被 line-height 撑高。
     显式 block 化后色块回到 76×76，与其他格子一致（此修复勿回退）。
     「待开放」标记已改为下方独立一行，不再依赖色块定位。 */
  display: block;
  width: 76rpx;
  height: 76rpx;
  line-height: 76rpx;
  text-align: center;
  border-radius: 22rpx;
  background-color: #fff2e6;
  color: #ff7a00;
  font-size: 30rpx;
  font-weight: 600;
}

.mine__grid-badge {
  /* 历史实现是绝对定位 + translate(35%, -45%)，标签会压在「券」色块右上角并越出格子。
     现在改为图标与文案下方的第二行小标签：不与图标重叠、不越界，
     宽度受 25% 格子约束（max-width:100%），极端窄屏用省略号兜底，
     因此既不会挤占相邻格子，也不会产生横向滚动。 */
  display: inline-block;
  box-sizing: border-box;
  max-width: 100%;
  margin-top: 6rpx;
  padding: 0 8rpx;
  border-radius: 8rpx;
  background-color: #ff4d4f;
  color: #ffffff;
  font-size: 18rpx;
  line-height: 28rpx;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.mine__grid-text {
  margin-top: 10rpx;
  font-size: 22rpx;
  color: #444444;
}

.mine__version {
  padding: 24rpx 0;
  text-align: center;
  color: #b0b0b0;
  font-size: 20rpx;
}
</style>
