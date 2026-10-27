<template>
  <view class="member">
    <view v-if="!auth.isLogin" class="se-empty">
      <text class="se-empty__title">登录后查看会员等级与权益</text>
      <view class="se-error__retry" @click="goLogin">去登录</view>
    </view>

    <template v-else>
      <view v-if="error" class="member__error">
        <text>{{ error }}</text>
        <text class="member__error-retry" @click="loadProfile">重新加载</text>
      </view>

      <view v-if="loading && !profile" class="se-loading">正在加载会员信息…</view>

      <template v-else-if="profile">
        <view class="member__level-card">
          <view class="member__level-top">
            <view class="member__level-main">
              <text class="member__level-name">{{ levelName }}</text>
              <text class="member__stars">{{ starText }}</text>
            </view>
            <text class="member__level-no">
              {{ profile.levelNo ? `LV.${profile.levelNo}` : '会员' }}
            </text>
          </view>

          <view class="member__progress-track">
            <view class="member__progress-bar" :style="{ width: `${progressPercent}%` }" />
          </view>
          <view class="member__progress-row">
            <text class="member__growth">成长值 {{ profile.growth }}</text>
            <text class="member__growth-next">{{ progressText }}</text>
          </view>
        </view>

        <view class="se-card member__benefits">
          <view class="se-between member__section-head">
            <text class="member__section-title">会员权益</text>
            <text class="se-muted">{{ benefitSourceText }}</text>
          </view>

          <view v-for="item in benefitItems" :key="item.label" class="member__benefit">
            <text class="member__benefit-label">{{ item.label }}</text>
            <text
              class="member__benefit-value"
              :class="{ 'is-off': !item.available }"
            >
              {{ item.value }}
            </text>
          </view>
        </view>

        <view class="se-card member__entries">
          <view class="member__entry" @click="goGrowth">
            <text class="member__entry-icon">值</text>
            <text class="member__entry-title">成长值</text>
            <text class="member__entry-desc">查看明细</text>
          </view>
          <view class="member__entry" @click="goPoints">
            <text class="member__entry-icon">积</text>
            <text class="member__entry-title">积分</text>
            <text class="member__entry-desc">查看明细</text>
          </view>
          <view class="member__entry" @click="goCheckin">
            <text class="member__entry-icon">签</text>
            <text class="member__entry-title">每日签到</text>
            <text class="member__entry-desc">{{ checkinDesc }}</text>
          </view>
          <view class="member__entry" @click="goInvite">
            <text class="member__entry-icon">邀</text>
            <text class="member__entry-title">邀请好友</text>
            <text class="member__entry-desc">好友首单得奖励</text>
          </view>
        </view>

        <view class="member__note">
          等级按近 365 天有效成长值计算，签到、下单、评价与邀请都会产生成长值。
        </view>
      </template>
    </template>
  </view>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { fetchCheckinStatus, fetchMemberProfile } from '@/api/mine'
import type { CheckinStatusVO, MemberProfileVO } from '@/api/mine'
import { useAuthStore } from '@/stores/auth'

/**
 * 会员中心。
 *
 * 进度不是后端现成百分比：profile.growth 是当前成长值，profile.nextThreshold
 * 是下一档门槛，页面按 growth / nextThreshold 自行换算；maxLevel 或门槛 <= 0 时按 100%。
 *
 * 权益来自 profile.benefitsJson（V33 种子字段：couponCount / freeDeliveryTimes /
 * exclusiveService）。字段缺失或 JSON 解析失败时展示固定能力项并标「待开放」，
 * 不臆造后端没有下发的权益。
 */

interface BenefitItem {
  label: string
  value: string
  available: boolean
}

const FALLBACK_BENEFITS: BenefitItem[] = [
  { label: '优惠券礼包', value: '待开放', available: false },
  { label: '免配送费', value: '待开放', available: false },
  { label: '专属客服', value: '待开放', available: false },
]

const auth = useAuthStore()

const profile = ref<MemberProfileVO | null>(null)
const checkinStatus = ref<CheckinStatusVO | null>(null)
const loading = ref(false)
const error = ref('')

let loadSequence = 0

const levelName = computed(() => profile.value?.levelName || '普通会员')

const starText = computed(() => {
  const count = profile.value?.star ?? 0
  return count > 0 ? '★'.repeat(Math.min(count, 10)) : '暂无星级'
})

const progressPercent = computed(() => {
  const current = profile.value
  if (!current) {
    return 0
  }
  if (current.maxLevel || current.nextThreshold <= 0) {
    return 100
  }
  return Math.max(0, Math.min(100, Math.round((current.growth / current.nextThreshold) * 100)))
})

const progressText = computed(() => {
  const current = profile.value
  if (!current) {
    return ''
  }
  if (current.maxLevel || current.nextThreshold <= 0) {
    return '已达最高等级'
  }
  return `距下一级还差 ${Math.max(0, current.growthToNext)}`
})

const benefitItems = computed<BenefitItem[]>(() => {
  const raw = profile.value?.benefitsJson
  if (!raw) {
    return FALLBACK_BENEFITS
  }
  let parsed: Record<string, unknown>
  try {
    const value = JSON.parse(raw) as unknown
    if (!value || typeof value !== 'object' || Array.isArray(value)) {
      return FALLBACK_BENEFITS
    }
    parsed = value as Record<string, unknown>
  } catch {
    return FALLBACK_BENEFITS
  }

  const items: BenefitItem[] = []
  if (typeof parsed.couponCount === 'number') {
    items.push({
      label: '优惠券礼包',
      value: parsed.couponCount > 0 ? `${parsed.couponCount} 张/月` : '未开通',
      available: parsed.couponCount > 0,
    })
  }
  if (typeof parsed.freeDeliveryTimes === 'number') {
    items.push({
      label: '免配送费',
      value: parsed.freeDeliveryTimes > 0 ? `${parsed.freeDeliveryTimes} 次/月` : '未开通',
      available: parsed.freeDeliveryTimes > 0,
    })
  }
  if (typeof parsed.exclusiveService === 'boolean') {
    items.push({
      label: '专属客服',
      value: parsed.exclusiveService ? '已开通' : '未开通',
      available: parsed.exclusiveService,
    })
  }
  return items.length > 0 ? items : FALLBACK_BENEFITS
})

const benefitSourceText = computed(() => (profile.value?.benefitsJson ? '按当前等级' : '待开放'))

const checkinDesc = computed(() => {
  const status = checkinStatus.value
  if (!status) {
    return '去签到'
  }
  return status.checkedInToday ? `已连续 ${status.continuousDays} 天` : '今日待签到'
})

onShow(() => {
  if (!auth.ensureLogin('/pages/member/Member')) {
    return
  }
  loadProfile()
})

async function loadProfile(): Promise<void> {
  if (loading.value) {
    return
  }
  const sequence = ++loadSequence
  loading.value = true
  error.value = ''
  const results = await Promise.allSettled([fetchMemberProfile(true), fetchCheckinStatus(true)])
  if (sequence !== loadSequence) {
    return
  }
  const [profileResult, checkinResult] = results
  if (profileResult.status === 'fulfilled') {
    profile.value = profileResult.value
  }
  if (checkinResult.status === 'fulfilled') {
    checkinStatus.value = checkinResult.value
  }
  if (profileResult.status === 'rejected') {
    // 保留上一次会员数据，只在顶部提示
    error.value = (profileResult.reason as Error)?.message || '会员信息加载失败'
  }
  loading.value = false
}

function goLogin(): void {
  auth.ensureLogin('/pages/member/Member')
}

function goGrowth(): void {
  uni.navigateTo({ url: '/pages/member/Growth' })
}

function goPoints(): void {
  uni.navigateTo({ url: '/pages/member/Points' })
}

function goCheckin(): void {
  uni.navigateTo({ url: '/pages/member/Checkin' })
}

function goInvite(): void {
  uni.navigateTo({ url: '/pages/member/Invite' })
}
</script>

<style scoped>
.member {
  min-height: 100vh;
  padding: 24rpx;
  background: linear-gradient(180deg, #fff3e6 0%, #f5f5f7 320rpx);
}

.member__error {
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

.member__error-retry {
  color: #ff7a00;
  margin-left: 16rpx;
}

.member__level-card {
  padding: 32rpx;
  border-radius: 12rpx;
  background: linear-gradient(120deg, #3a2a1a 0%, #6b4a25 100%);
  color: #ffe8c7;
  box-shadow: 0 8rpx 20rpx rgba(80, 52, 20, 0.24);
}

.member__level-top {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
}

.member__level-main {
  display: flex;
  flex-direction: column;
  gap: 8rpx;
}

.member__level-name {
  font-size: 40rpx;
  font-weight: 600;
  color: #ffd18f;
}

.member__stars {
  font-size: 24rpx;
  letter-spacing: 4rpx;
  color: #ffc56b;
}

.member__level-no {
  padding: 6rpx 16rpx;
  border-radius: 24rpx;
  background-color: rgba(255, 209, 143, 0.16);
  color: #ffd18f;
  font-size: 22rpx;
}

.member__progress-track {
  height: 10rpx;
  margin-top: 28rpx;
  border-radius: 5rpx;
  background-color: rgba(255, 232, 199, 0.18);
  overflow: hidden;
}

.member__progress-bar {
  height: 10rpx;
  border-radius: 5rpx;
  background: linear-gradient(90deg, #ffd18f, #ff9a2e);
  transition: width 0.3s ease;
}

.member__progress-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 14rpx;
  font-size: 22rpx;
  color: #f0d6b3;
}

.member__growth-next {
  opacity: 0.9;
}

.member__benefits {
  margin-top: 20rpx;
  padding: 24rpx;
}

.member__section-head {
  padding-bottom: 12rpx;
  border-bottom: 1rpx solid #f5f5f5;
}

.member__section-title {
  font-size: 30rpx;
  font-weight: 600;
}

.member__benefit {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 22rpx 0;
  border-bottom: 1rpx solid #f7f7f7;
}

.member__benefit:last-child {
  border-bottom: none;
}

.member__benefit-label {
  font-size: 27rpx;
  color: #333333;
}

.member__benefit-value {
  font-size: 26rpx;
  color: #ff7a00;
  font-weight: 500;
}

.member__benefit-value.is-off {
  color: #a0a0a0;
  font-weight: 400;
}

.member__entries {
  display: flex;
  margin-top: 20rpx;
  padding: 26rpx 0;
}

.member__entry {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8rpx;
  border-right: 1rpx solid #f5f5f5;
}

.member__entry:last-child {
  border-right: none;
}

.member__entry-icon {
  width: 72rpx;
  height: 72rpx;
  line-height: 72rpx;
  text-align: center;
  border-radius: 50%;
  background-color: #fff2e6;
  color: #ff7a00;
  font-size: 28rpx;
  font-weight: 600;
}

.member__entry-title {
  font-size: 24rpx;
  color: #333333;
}

.member__entry-desc {
  font-size: 20rpx;
  color: #a0a0a0;
}

.member__note {
  padding: 24rpx 8rpx;
  color: #a0a0a0;
  font-size: 22rpx;
  line-height: 1.6;
}
</style>
