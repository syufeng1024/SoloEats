<template>
  <view class="settings">
    <view class="se-card settings__group">
      <view class="settings__row" @click="goNotifySettings">
        <text class="settings__name">消息通知</text>
        <text class="settings__arrow">›</text>
      </view>
    </view>

    <view class="se-card settings__group">
      <view class="settings__row" @click="clearCache">
        <text class="settings__name">清除本地缓存</text>
        <view class="settings__value-wrap">
          <text class="settings__value">{{ cacheSizeText }}</text>
          <text class="settings__arrow">›</text>
        </view>
      </view>
      <view class="se-divider" />
      <view class="settings__row" @click="openAgreement">
        <text class="settings__name">用户协议</text>
        <text class="settings__arrow">›</text>
      </view>
      <view class="se-divider" />
      <view class="settings__row" @click="goAbout">
        <text class="settings__name">关于</text>
        <text class="settings__arrow">›</text>
      </view>
    </view>

    <view v-if="auth.isLogin" class="se-card settings__group">
      <view class="settings__row settings__row--danger" @click="handleLogout">退出登录</view>
    </view>

    <view class="settings__hint">
      清除缓存不会退出登录；搜索历史、定位、临时幂等键会被删除。
    </view>

    <view v-if="agreementVisible" class="agreement-mask" @click="closeAgreement">
      <view class="agreement-sheet" @click.stop>
        <view class="se-between agreement__head">
          <text class="agreement__title">用户协议</text>
          <text class="agreement__close" @click="closeAgreement">×</text>
        </view>
        <scroll-view scroll-y class="agreement__body">
          <text class="agreement__meta">版本 v1.0 · 正式文本以平台发布内容为准</text>
          <text
            v-for="paragraph in AGREEMENT_PARAGRAPHS"
            :key="paragraph.title"
            class="agreement__paragraph"
          >
            {{ paragraph.title }}
            {{ paragraph.body }}
          </text>
        </scroll-view>
        <view class="agreement__foot" @click="closeAgreement">我已阅读</view>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { useAuthStore } from '@/stores/auth'

/**
 * 设置页。
 *
 * 后端没有 /api/v1/c/agreement 这个路径；协议相关只有
 * GET /c/users/agreement（仅返回版本号与是否同意，不返回正文），
 * 所以协议正文在本页以静态段落展示，不臆造接口。
 */

const AUTH_KEYS = ['token', 'tokenExpireAt', 'userId', 'nickname']

const AGREEMENT_PARAGRAPHS = [
  {
    title: '一、账号',
    body: '你使用手机号与短信验证码登录，需保证手机号真实有效。账号仅限本人使用，不得出租、转让或用于违法活动。',
  },
  {
    title: '二、订单与支付',
    body: '下单前请核对门店、商品、数量、配送方式与收货信息。订单金额以下单页展示为准，支付结果以服务端回调为准。',
  },
  {
    title: '三、配送与自取',
    body: '配送时效受门店出餐、天气与路况影响。选择到店自取时，请凭取餐码在有效期内取餐，逾期未取按门店规则处理。',
  },
  {
    title: '四、退款与售后',
    body: '订单取消与退款需符合订单状态机的可退条件。已支付订单的退款原路退回，到账时间以支付渠道为准。售后问题可通过工单或意见反馈提交。',
  },
  {
    title: '五、隐私',
    body: '手机号、收货地址等个人信息仅用于订单履约、客服处理与必要的安全校验，接口返回时已脱敏。你可以在设置中清除本地缓存。',
  },
]

const auth = useAuthStore()

const cacheSizeText = ref('0 KB')
const agreementVisible = ref(false)

onShow(() => {
  refreshCacheSize()
})

function refreshCacheSize(): void {
  try {
    const info = uni.getStorageInfoSync()
    const kb = Number(info?.currentSize ?? 0)
    cacheSizeText.value = kb < 1 ? '小于 1 KB' : `${Math.round(kb)} KB`
  } catch (err) {
    cacheSizeText.value = '--'
  }
}

function goNotifySettings(): void {
  if (!auth.ensureLogin('/pages/mine/Settings')) {
    return
  }
  uni.navigateTo({ url: '/pages/message/Settings' })
}

function clearCache(): void {
  uni.showModal({
    title: '清除本地缓存',
    content: '将清除搜索历史、定位、临时幂等键等本地数据，登录状态会保留。确定继续？',
    confirmColor: '#ff7a00',
    success: (result) => {
      if (!result.confirm) {
        return
      }
      // 先摘出登录态，清空后原样写回，避免「清缓存」把用户踢下线
      const authEntries = AUTH_KEYS.map((key) => [key, uni.getStorageSync(key)] as const)
      try {
        uni.clearStorageSync()
        authEntries.forEach(([key, value]) => {
          if (value !== '' && value !== null && value !== undefined) {
            uni.setStorageSync(key, value)
          }
        })
        uni.showToast({ title: '已清除缓存', icon: 'none' })
      } catch (err) {
        uni.showToast({ title: '缓存清除失败，请重试', icon: 'none' })
      }
      refreshCacheSize()
    },
  })
}

function openAgreement(): void {
  agreementVisible.value = true
}

function closeAgreement(): void {
  agreementVisible.value = false
}

function goAbout(): void {
  uni.navigateTo({ url: '/pages/mine/About' })
}

function handleLogout(): void {
  uni.showModal({
    title: '退出登录',
    content: '退出后需要重新登录才能查看订单与钱包，确定继续？',
    confirmColor: '#ff4d4f',
    success: (result) => {
      if (!result.confirm) {
        return
      }
      auth.logout()
      uni.showToast({ title: '已退出登录', icon: 'none' })
      uni.switchTab({ url: '/pages/index/Index' })
    },
  })
}
</script>

<style scoped>
.settings {
  min-height: 100vh;
  padding: 24rpx;
  background-color: #f5f5f7;
}

.settings__group {
  margin-bottom: 20rpx;
  overflow: hidden;
}

.settings__row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 96rpx;
  padding: 0 28rpx;
  font-size: 28rpx;
  color: #1f1f1f;
}

.settings__row--danger {
  justify-content: center;
  color: #ff4d4f;
}

.settings__name {
  font-size: 28rpx;
}

.settings__value-wrap {
  display: flex;
  align-items: center;
  gap: 8rpx;
}

.settings__value {
  color: #8a8a8a;
  font-size: 24rpx;
}

.settings__arrow {
  color: #c8c8c8;
  font-size: 34rpx;
  line-height: 1;
}

.settings__hint {
  padding: 8rpx 12rpx;
  color: #a0a0a0;
  font-size: 22rpx;
  line-height: 1.6;
}

.agreement-mask {
  position: fixed;
  left: 0;
  right: 0;
  top: 0;
  bottom: 0;
  z-index: 100;
  background-color: rgba(0, 0, 0, 0.45);
  display: flex;
  align-items: flex-end;
}

.agreement-sheet {
  width: 100%;
  max-height: 78vh;
  padding: 24rpx 28rpx calc(24rpx + env(safe-area-inset-bottom));
  border-radius: 20rpx 20rpx 0 0;
  background-color: #ffffff;
  display: flex;
  flex-direction: column;
}

.agreement__head {
  padding-bottom: 16rpx;
  border-bottom: 1rpx solid #f0f0f0;
}

.agreement__title {
  font-size: 32rpx;
  font-weight: 600;
}

.agreement__close {
  color: #8a8a8a;
  font-size: 40rpx;
  line-height: 1;
}

.agreement__body {
  flex: 1;
  min-height: 0;
  max-height: 56vh;
  padding: 16rpx 0;
}

.agreement__meta {
  display: block;
  margin-bottom: 16rpx;
  color: #a0a0a0;
  font-size: 22rpx;
}

.agreement__paragraph {
  display: block;
  margin-bottom: 20rpx;
  color: #444444;
  font-size: 26rpx;
  line-height: 1.75;
}

.agreement__foot {
  height: 80rpx;
  line-height: 80rpx;
  border-radius: 8rpx;
  background: linear-gradient(135deg, #ffa24c, #ff7a00);
  color: #ffffff;
  font-size: 28rpx;
  text-align: center;
}
</style>
