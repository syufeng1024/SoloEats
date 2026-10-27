<template>
  <view class="mine mt-page">
    <!-- 商家头部：名称 / 编号 / 入驻状态 -->
    <view class="mine__hero">
      <view class="mine__avatar">商</view>
      <view class="mine__hero-main">
        <text class="mine__name">{{ profile?.merchantName || auth.nickname || '商家' }}</text>
        <text class="mine__no">{{ profile?.merchantNo || '商家编号未返回' }}</text>
      </view>
      <text class="mt-tag" :class="statusClass(profile?.status)">
        {{ statusText(profile?.status) }}
      </text>
    </view>

    <view v-if="profileLoading && !profile" class="mt-loading">正在加载商家信息…</view>

    <view v-else-if="profileError && !profile" class="mt-error">
      <text class="mt-error__msg">{{ profileError }}</text>
      <view class="mt-error__retry" @click="loadProfile">重新加载</view>
    </view>

    <!-- 商家信息 -->
    <view v-else class="mt-card mine__card">
      <view class="mt-between mine__card-head">
        <text class="mt-section-title">商家信息</text>
        <text class="mt-muted">{{ profile?.merchantNo || '' }}</text>
      </view>
      <view class="mine__row">
        <text class="mt-muted">商家名称</text>
        <text class="mine__value">{{ profile?.merchantName || '--' }}</text>
      </view>
      <view class="mine__row">
        <text class="mt-muted">联系人</text>
        <text class="mine__value">{{ profile?.contactName || '--' }}</text>
      </view>
      <view class="mine__row">
        <text class="mt-muted">联系电话</text>
        <text class="mine__value">{{ profile?.contactPhone || '--' }}</text>
      </view>
      <view class="mine__row">
        <text class="mt-muted">联系邮箱</text>
        <text class="mine__value">{{ profile?.contactEmail || '--' }}</text>
      </view>
      <view class="mine__row">
        <text class="mt-muted">经营地址</text>
        <text class="mine__value">{{ fullAddress || '--' }}</text>
      </view>
      <view class="mine__row">
        <text class="mt-muted">平台佣金比例</text>
        <text class="mine__value">{{ commissionText }}</text>
      </view>
      <view class="mine__row">
        <text class="mt-muted">入驻时间</text>
        <text class="mine__value">{{ formatDateTime(profile?.openedAt) }}</text>
      </view>
    </view>

    <!-- 当前门店 -->
    <view class="mt-card mine__card">
      <view class="mt-between mine__card-head">
        <text class="mt-section-title">当前门店</text>
        <text v-if="shop.shops.length > 1" class="mine__link" @click="openShopPicker">
          切换门店 ›
        </text>
      </view>

      <view v-if="shop.currentShop" class="mine__shop">
        <view class="mine__shop-main">
          <text class="mine__shop-name">{{ shop.currentShop.shopName }}</text>
          <text class="mine__shop-addr">{{ shop.currentShop.address || '地址待维护' }}</text>
        </view>
        <text class="mt-tag" :class="shop.currentShop.status === 1 ? 'mt-tag--ok' : 'mt-tag--off'">
          {{ shop.currentShop.statusText || '状态未知' }}
        </text>
      </view>
      <view v-else-if="shop.loading" class="mt-muted">门店加载中…</view>
      <view v-else class="mt-muted">{{ shop.error || '未获取到门店，请确认账号已绑定门店' }}</view>

      <view class="mt-between mine__shop-foot">
        <text class="mine__switch-hint">
          {{ shop.shops.length > 1 ? `共 ${shop.shops.length} 家门店` : '单门店账号' }}
        </text>
        <text class="mine__link" @click="goShopSetting">门店设置 ›</text>
      </view>
    </view>

    <!-- 账号 -->
    <view class="mt-card mine__card">
      <view class="mt-between mine__card-head">
        <text class="mt-section-title">账号</text>
        <text class="mt-muted">{{ auth.nickname || '商家账号' }}</text>
      </view>
      <text class="mine__hint">
        切换门店只影响订单、商品等查询类接口；写接口后端仍按登录令牌绑定的门店校验。
      </text>
      <button class="mt-btn mt-btn--danger mine__logout" @click="handleLogout">退出登录</button>
    </view>

    <view class="mine__version">
      <text>数创星光外卖 · 商家端 v{{ APP_VERSION }}</text>
      <text>页面数据来自后端接口，金额单位展示为元</text>
    </view>

    <!-- 门店切换 -->
    <view v-if="shopPickerVisible" class="mt-mask" @click="shopPickerVisible = false" />
    <view v-if="shopPickerVisible" class="mt-sheet">
      <view class="mt-between">
        <text class="mt-sheet__title">切换门店</text>
        <text class="mine__close" @click="shopPickerVisible = false">关闭</text>
      </view>
      <view class="mt-sheet__tip">
        切换后订单、商品查询按新门店过滤；写接口仍以登录令牌绑定门店为准。
      </view>
      <scroll-view scroll-y class="mine__shop-list">
        <view
          v-for="item in shop.shops"
          :key="item.id"
          class="mine__shop-item"
          :class="{ 'is-active': item.id === shop.currentShopId }"
          @click="selectShop(item.id)"
        >
          <view class="mine__shop-main">
            <text class="mine__shop-name">{{ item.shopName }}</text>
            <text class="mine__shop-addr">{{ item.address || '地址待维护' }}</text>
          </view>
          <text class="mt-tag" :class="item.status === 1 ? 'mt-tag--ok' : 'mt-tag--off'">
            {{ item.statusText || '状态未知' }}
          </text>
        </view>
      </scroll-view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { fetchMerchantProfile } from '@/api/profile'
import type { MerchantDetailVO } from '@/api/types'
import { useAuthStore } from '@/stores/auth'
import { useShopStore } from '@/stores/shop'
import { formatDateTime } from '@/utils/date'

/**
 * 我的。
 * - 商家信息取 GET /merchant/profile（merchantId 由后端从令牌解析）；
 * - 当前门店复用 stores/shop：GET /merchant/shops 来自 login 后的门店列表，
 *   切换结果写本地 `shopId` 并由 api/http.ts 注入 X-Shop-Id 头；
 *   后端当前未解析该头，写接口仍按令牌 shopId 校验（已在页面提示）。
 * - 版本号与 package.json / manifest.json 的 versionName 保持一致。
 */

const APP_VERSION = '0.1.0'
const MINE_PATH = '/pages/mine/Mine'

/** t_merchant.status：0草稿 1待审核 2已通过 3已驳回 4已停用 */
const MERCHANT_STATUS_TEXT: Record<number, string> = {
  0: '草稿',
  1: '待审核',
  2: '已通过',
  3: '已驳回',
  4: '已停用',
}

const auth = useAuthStore()
const shop = useShopStore()

const profile = ref<MerchantDetailVO | null>(null)
const profileLoading = ref(false)
const profileError = ref('')
const shopPickerVisible = ref(false)

const fullAddress = computed(() => {
  const current = profile.value
  if (!current) {
    return ''
  }
  return [current.province, current.city, current.district, current.address]
    .filter((part) => Boolean(part))
    .join('')
})

const commissionText = computed(() => {
  const rate = profile.value?.commissionRate
  if (rate === null || rate === undefined) {
    return '--'
  }
  return `${(Number(rate) * 100).toFixed(2)}%`
})

onShow(() => {
  if (!auth.ensureLogin(MINE_PATH)) {
    return
  }
  void loadProfile()
  void loadShops()
})

function toast(message: string): void {
  uni.showToast({ title: message, icon: 'none', duration: 2400 })
}

function statusText(status: number | null | undefined): string {
  if (status === null || status === undefined) {
    return '状态未知'
  }
  return MERCHANT_STATUS_TEXT[status] ?? `状态 ${status}`
}

function statusClass(status: number | null | undefined): string {
  if (status === 2) {
    return 'mt-tag--ok'
  }
  if (status === 3 || status === 4) {
    return 'mt-tag--warn'
  }
  return 'mt-tag--off'
}

async function loadProfile(): Promise<void> {
  if (profileLoading.value) {
    return
  }
  profileLoading.value = true
  profileError.value = ''
  try {
    profile.value = await fetchMerchantProfile(true)
  } catch (err) {
    profileError.value = (err as Error)?.message || '商家信息加载失败'
  } finally {
    profileLoading.value = false
  }
}

async function loadShops(): Promise<void> {
  try {
    await shop.loadShops(true)
  } catch {
    // shop.error 已由 store 记录，页面直接展示
  }
}

function openShopPicker(): void {
  if (shop.shops.length <= 1) {
    return
  }
  shopPickerVisible.value = true
}

function selectShop(shopId: number): void {
  shop.selectShop(shopId)
  shopPickerVisible.value = false
  toast('已切换当前门店')
}

function goShopSetting(): void {
  uni.navigateTo({ url: '/pages/shop/ShopSetting' })
}

function handleLogout(): void {
  uni.showModal({
    title: '退出登录',
    content: '确认退出当前商家账号？',
    confirmText: '退出',
    success: (result) => {
      if (!result.confirm) {
        return
      }
      auth.logout()
      shop.reset()
      uni.reLaunch({ url: '/pages/login/Login' })
    },
  })
}
</script>

<style scoped>
.mine {
  padding-bottom: calc(40rpx + env(safe-area-inset-bottom));
}

.mine__hero {
  display: flex;
  align-items: center;
  gap: 16rpx;
  padding: 22rpx 20rpx;
  border-radius: 12rpx;
  background: linear-gradient(135deg, #1f3a4d, #16233a);
  color: #ffffff;
}

.mine__avatar {
  width: 76rpx;
  height: 76rpx;
  line-height: 76rpx;
  border-radius: 50%;
  background-color: #ff7a00;
  text-align: center;
  font-size: 32rpx;
  font-weight: 700;
  color: #ffffff;
  flex: none;
}

.mine__hero-main {
  flex: 1;
  min-width: 0;
}

.mine__name {
  display: block;
  font-size: 30rpx;
  font-weight: 700;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.mine__no {
  display: block;
  margin-top: 4rpx;
  font-size: 20rpx;
  color: rgba(255, 255, 255, 0.68);
  font-variant-numeric: tabular-nums;
}

.mine__card {
  margin-top: 14rpx;
}

.mine__card-head {
  margin-bottom: 8rpx;
}

.mine__row {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16rpx;
  padding: 12rpx 0;
  border-bottom: 1rpx solid #f4f6f9;
}

.mine__row:last-child {
  border-bottom: none;
}

.mine__value {
  flex: 1;
  text-align: right;
  font-size: 24rpx;
  color: #1b2432;
  word-break: break-all;
}

.mine__shop {
  display: flex;
  align-items: center;
  gap: 14rpx;
  padding: 14rpx;
  margin-top: 6rpx;
  border-radius: 10rpx;
  background-color: #f7f9fc;
}

.mine__shop-main {
  flex: 1;
  min-width: 0;
}

.mine__shop-name {
  display: block;
  font-size: 25rpx;
  font-weight: 600;
  color: #1b2432;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.mine__shop-addr {
  display: block;
  margin-top: 4rpx;
  font-size: 20rpx;
  color: #8d99aa;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.mine__shop-foot {
  margin-top: 12rpx;
}

.mine__switch-hint {
  font-size: 21rpx;
  color: #7a8699;
}

.mine__link {
  font-size: 22rpx;
  color: #e85d04;
  font-weight: 600;
}

.mine__hint {
  display: block;
  margin: 8rpx 0;
  font-size: 20rpx;
  color: #8d99aa;
  line-height: 1.6;
}

.mine__logout {
  margin-top: 16rpx;
  height: 72rpx;
  line-height: 72rpx;
  font-size: 25rpx;
}

.mine__version {
  margin-top: 22rpx;
  text-align: center;
  font-size: 20rpx;
  color: #a9b4c4;
  line-height: 1.7;
}

.mine__version text {
  display: block;
}

.mine__close {
  font-size: 23rpx;
  color: #7a8699;
}

.mine__shop-list {
  max-height: 52vh;
  margin-top: 12rpx;
}

.mine__shop-item {
  display: flex;
  align-items: center;
  gap: 14rpx;
  padding: 16rpx 14rpx;
  margin-bottom: 8rpx;
  border-radius: 10rpx;
  background-color: #f7f9fc;
  border: 2rpx solid transparent;
}

.mine__shop-item.is-active {
  background-color: #fff1e6;
  border-color: #ff9a3c;
}
</style>
