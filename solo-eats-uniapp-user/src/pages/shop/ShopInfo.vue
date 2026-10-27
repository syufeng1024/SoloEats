<template>
  <view class="shop-info">
    <view v-if="loading" class="se-loading">正在加载门店信息…</view>

    <view v-else-if="error" class="se-error">
      <text>{{ error }}</text>
      <view class="se-error__retry" @click="load">重新加载</view>
    </view>

    <template v-else-if="shop">
      <view class="se-card shop-info__hero">
        <view class="shop-info__logo">
          <image
            v-if="shop.logoUrl"
            class="shop-info__logo-img"
            :src="shop.logoUrl"
            mode="aspectFill"
          />
          <text v-else class="shop-info__logo-letter">{{ shop.shopName.slice(0, 1) }}</text>
        </view>

        <view class="shop-info__hero-copy">
          <text class="shop-info__name">{{ shop.shopName }}</text>
          <view class="shop-info__meta">
            <text class="shop-info__rating">{{ ratingText(shop.rating) }} 分</text>
            <text class="se-muted">月售 {{ shop.monthSales || 0 }}</text>
            <text class="se-muted">约 {{ shop.prepMinutes || 30 }} 分钟</text>
          </view>
          <view class="shop-info__tags">
            <text class="se-tag">{{ shop.statusText || '营业中' }}</text>
            <text v-if="shop.pickupEnabled === 1" class="se-tag">到店自取</text>
          </view>
        </view>
      </view>

      <view class="se-card shop-info__card">
        <text class="shop-info__card-title">门店公告</text>
        <text v-if="shop.announcement" class="shop-info__announcement">{{ shop.announcement }}</text>
        <text v-else class="shop-info__placeholder">门店暂未发布公告</text>
      </view>

      <view class="se-card shop-info__card shop-info__rows">
        <view class="shop-info__row">
          <text class="shop-info__label">营业时间</text>
          <view class="shop-info__value shop-info__hours">
            <text
              v-for="hour in businessHours"
              :key="`${hour.dayOfWeek}-${hour.startTime}`"
              class="shop-info__hour"
            >
              {{ hour.label }}
            </text>
            <text v-if="businessHours.length === 0" class="shop-info__placeholder">
              暂未配置营业时间
            </text>
          </view>
        </view>

        <view class="se-divider" />

        <view class="shop-info__row" @click="copyAddress">
          <text class="shop-info__label">门店地址</text>
          <text class="shop-info__value shop-info__link">
            {{ shop.address || '暂未填写' }}
          </text>
        </view>

        <view class="se-divider" />

        <view class="shop-info__row" @click="callShop">
          <text class="shop-info__label">联系电话</text>
          <text v-if="shop.contactPhone" class="shop-info__value shop-info__link">
            {{ shop.contactPhone }}
          </text>
          <text v-else class="shop-info__placeholder">门店未提供电话</text>
        </view>

        <view class="se-divider" />

        <view class="shop-info__row">
          <text class="shop-info__label">配送信息</text>
          <view class="shop-info__value shop-info__fees">
            <text>起送 ¥{{ fenToYuan(shop.minOrderAmount) }}</text>
            <text>配送 ¥{{ fenToYuan(shop.deliveryFee) }}</text>
            <text>打包 ¥{{ fenToYuan(shop.packagingFee) }}</text>
          </view>
        </view>

        <view v-if="shop.deliveryRadiusM" class="se-divider" />

        <view v-if="shop.deliveryRadiusM" class="shop-info__row">
          <text class="shop-info__label">配送范围</text>
          <text class="shop-info__value">
            约 {{ (shop.deliveryRadiusM / 1000).toFixed(1) }} 公里（以配送范围校验结果为准）
          </text>
        </view>
      </view>

      <view class="se-card shop-info__card shop-info__ratings">
        <text class="shop-info__card-title">评分</text>
        <view class="shop-info__rating-row">
          <view class="shop-info__rating-item">
            <text class="shop-info__rating-num">{{ ratingText(shop.ratingTaste) }}</text>
            <text class="shop-info__rating-label">口味</text>
          </view>
          <view class="shop-info__rating-item">
            <text class="shop-info__rating-num">{{ ratingText(shop.ratingPackage) }}</text>
            <text class="shop-info__rating-label">包装</text>
          </view>
          <view class="shop-info__rating-item">
            <text class="shop-info__rating-num">{{ ratingText(shop.ratingDelivery) }}</text>
            <text class="shop-info__rating-label">配送</text>
          </view>
        </view>
      </view>

      <view class="shop-info__actions">
        <button class="se-btn se-btn-ghost shop-info__action" @click="openReviews">店铺评价</button>
        <button class="se-btn se-btn-primary shop-info__action" @click="openMenu">去点餐</button>
      </view>
    </template>
  </view>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { fetchShopDetail } from '@/api/shop'
import type { ShopDetailVO } from '@/api/types'
import { fenToYuan } from '@/utils/money'

/**
 * 店铺信息页：公告、营业时间、地址、电话与配送信息。
 * 数据来源 GET /c/shops/{id}（ShopDetailView），不额外拼接口。
 */

const DAY_LABELS = ['', '周一', '周二', '周三', '周四', '周五', '周六', '周日']

const shopId = ref(0)
const shop = ref<ShopDetailVO | null>(null)
const loading = ref(true)
const error = ref('')

const businessHours = computed(() => {
  const list = shop.value?.businessHours || []
  return [...list]
    .sort((left, right) => Number(left.dayOfWeek) - Number(right.dayOfWeek))
    .map((hour) => ({
      dayOfWeek: hour.dayOfWeek,
      startTime: hour.startTime,
      label: `${DAY_LABELS[Number(hour.dayOfWeek)] || `周${hour.dayOfWeek}`} ${hour.startTime}-${hour.endTime}${
        hour.crossDay ? '（次日）' : ''
      }`,
    }))
})

onLoad((options) => {
  const id = Number(options?.id)
  if (!Number.isFinite(id) || id <= 0) {
    loading.value = false
    error.value = '缺少门店参数'
    return
  }
  shopId.value = id
  load()
})

async function load(): Promise<void> {
  loading.value = true
  error.value = ''
  try {
    shop.value = await fetchShopDetail(shopId.value)
    if (shop.value?.shopName) {
      uni.setNavigationBarTitle({ title: shop.value.shopName })
    }
  } catch (err) {
    error.value = (err as Error)?.message || '门店信息加载失败'
  } finally {
    loading.value = false
  }
}

function ratingText(value: number | null): string {
  return value ? Number(value).toFixed(1) : '--'
}

function copyAddress(): void {
  const address = shop.value?.address
  if (!address) {
    uni.showToast({ title: '门店暂未填写地址', icon: 'none' })
    return
  }
  uni.setClipboardData({
    data: address,
    success: () => uni.showToast({ title: '地址已复制', icon: 'none' }),
  })
}

function callShop(): void {
  const phone = shop.value?.contactPhone
  if (!phone) {
    uni.showToast({ title: '门店未提供电话', icon: 'none' })
    return
  }
  uni.makePhoneCall({
    phoneNumber: phone,
    fail: () => {
      // H5 部分浏览器不支持直接拨号，降级为复制号码
      uni.setClipboardData({
        data: phone,
        success: () => uni.showToast({ title: '号码已复制', icon: 'none' }),
      })
    },
  })
}

function openReviews(): void {
  const name = shop.value?.shopName || ''
  uni.navigateTo({
    url: `/pages/shop/ShopReview?shopId=${shopId.value}&shopName=${encodeURIComponent(name)}`,
  })
}

function openMenu(): void {
  const pages = getCurrentPages()
  const previous = pages[pages.length - 2] as { route?: string } | undefined
  // 从点餐页进来时直接返回，避免页面栈里出现两个门店点餐页
  if (previous?.route === 'pages/shop/Shop') {
    uni.navigateBack()
    return
  }
  uni.navigateTo({ url: `/pages/shop/Shop?id=${shopId.value}` })
}
</script>

<style scoped>
.shop-info {
  min-height: 100vh;
  padding: 20rpx 24rpx 200rpx;
  background-color: #f5f5f7;
}

.shop-info__hero {
  display: flex;
  padding: 28rpx;
}

.shop-info__logo {
  width: 128rpx;
  height: 128rpx;
  border-radius: 8rpx;
  overflow: hidden;
  background-color: #fff2e6;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.shop-info__logo-img {
  width: 128rpx;
  height: 128rpx;
}

.shop-info__logo-letter {
  color: #ff7a00;
  font-size: 48rpx;
  font-weight: 600;
}

.shop-info__hero-copy {
  flex: 1;
  min-width: 0;
  margin-left: 20rpx;
}

.shop-info__name {
  display: block;
  font-size: 34rpx;
  font-weight: 600;
}

.shop-info__meta {
  display: flex;
  align-items: center;
  gap: 16rpx;
  margin-top: 10rpx;
}

.shop-info__rating {
  color: #ff7a00;
  font-size: 24rpx;
  font-weight: 600;
}

.shop-info__tags {
  margin-top: 12rpx;
}

.shop-info__card {
  margin-top: 16rpx;
  padding: 28rpx;
}

.shop-info__card-title {
  display: block;
  font-size: 28rpx;
  font-weight: 600;
  color: #1f1f1f;
}

.shop-info__announcement {
  display: block;
  margin-top: 14rpx;
  padding: 16rpx 18rpx;
  border-radius: 8rpx;
  background-color: #fff8ef;
  color: #a26a2a;
  font-size: 25rpx;
  line-height: 1.7;
}

.shop-info__placeholder {
  color: #a0a0a0;
  font-size: 24rpx;
}

.shop-info__card > .shop-info__placeholder {
  display: block;
  margin-top: 14rpx;
}

.shop-info__rows {
  padding: 0 28rpx;
}

.shop-info__row {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  padding: 26rpx 0;
  gap: 24rpx;
}

.shop-info__label {
  flex-shrink: 0;
  font-size: 26rpx;
  color: #6f6f6f;
}

.shop-info__value {
  flex: 1;
  text-align: right;
  font-size: 26rpx;
  color: #1f1f1f;
  word-break: break-all;
}

.shop-info__link {
  color: #ff7a00;
}

.shop-info__hours {
  display: flex;
  flex-direction: column;
  gap: 6rpx;
}

.shop-info__hour {
  font-size: 24rpx;
  color: #444444;
}

.shop-info__fees {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 16rpx;
  font-size: 24rpx;
  color: #444444;
}

.shop-info__ratings {
  margin-top: 16rpx;
}

.shop-info__rating-row {
  display: flex;
  margin-top: 18rpx;
}

.shop-info__rating-item {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
}

.shop-info__rating-num {
  font-size: 36rpx;
  font-weight: 600;
  color: #ff7a00;
}

.shop-info__rating-label {
  margin-top: 4rpx;
  font-size: 22rpx;
  color: #8a8a8a;
}

.shop-info__actions {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  display: flex;
  gap: 20rpx;
  padding: 16rpx 24rpx calc(16rpx + env(safe-area-inset-bottom));
  background-color: #ffffff;
  box-shadow: 0 -2rpx 10rpx rgba(20, 20, 20, 0.05);
}

.shop-info__action {
  flex: 1;
}
</style>
