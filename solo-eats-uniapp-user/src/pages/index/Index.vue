<template>
  <view class="se-page home">
    <view class="home__top">
      <view class="home__location" @click="locate">
        <text class="home__city">{{ location.city }}</text>
        <text class="home__loc">
          {{ locating ? '定位中…' : locFailed ? '定位失败，点击重试' : '当前位置' }}
        </text>
      </view>
      <view class="home__search" @click="onSearchTap">
        <text class="home__search-icon">⌕</text>
        <text class="home__search-text">搜索店铺、商品</text>
      </view>
    </view>

    <view class="home__banner">
      <view class="home__banner-copy">
        <text class="home__banner-title">新客首单立减</text>
        <text class="home__banner-sub">附近门店 30 分钟送达</text>
      </view>
      <text class="home__banner-badge">数创星光</text>
    </view>

    <view class="se-card home__quick">
      <view class="home__quick-item" @click="goNearby">
        <text class="home__quick-icon">店</text>
        <text class="home__quick-text">附近找店</text>
      </view>
      <view class="home__quick-item" @click="goOrders">
        <text class="home__quick-icon">单</text>
        <text class="home__quick-text">我的订单</text>
      </view>
      <view class="home__quick-item" @click="locate">
        <text class="home__quick-icon">位</text>
        <text class="home__quick-text">重新定位</text>
      </view>
    </view>

    <view id="shop-section" class="se-between home__section">
      <text class="home__section-title">附近好店</text>
      <text v-if="total > 0" class="se-muted">共 {{ total }} 家</text>
    </view>

    <view v-if="loading && shops.length === 0" class="se-loading">正在加载附近门店…</view>

    <view v-else-if="error && shops.length === 0" class="se-error">
      <text>{{ error }}</text>
      <view class="se-error__retry" @click="reload">重新加载</view>
    </view>

    <view v-else-if="shops.length === 0" class="se-empty">
      <text class="se-empty__title">附近暂无营业门店</text>
      <text>换个定位试试，或稍后再来</text>
    </view>

    <view v-else class="home__shops">
      <view v-for="shop in shops" :key="shop.id" class="se-card home__shop" @click="openShop(shop)">
        <view class="home__shop-logo">
          <image v-if="shop.logoUrl" class="home__shop-img" :src="shop.logoUrl" mode="aspectFill" />
          <text v-else class="home__shop-letter">{{ shop.shopName.slice(0, 1) }}</text>
        </view>
        <view class="home__shop-body">
          <text class="home__shop-name">{{ shop.shopName }}</text>
          <view class="home__shop-meta">
            <text class="home__rating">{{ ratingText(shop.rating) }}</text>
            <text class="se-muted">月售 {{ shop.monthSales || 0 }}</text>
            <text class="se-muted">{{ distanceText(shop.distanceM) }}</text>
          </view>
          <view class="home__shop-meta">
            <text class="se-muted">起送 ¥{{ fenToYuan(shop.minOrderAmount) }}</text>
            <text class="se-muted">配送 ¥{{ fenToYuan(shop.deliveryFee) }}</text>
            <text class="se-muted">{{ shop.prepMinutes || 30 }} 分钟</text>
          </view>
          <view class="home__shop-tags">
            <text v-if="shop.pickupEnabled === 1" class="se-tag">到店自取</text>
            <text v-if="shop.address" class="home__shop-address">{{ shop.address }}</text>
          </view>
        </view>
      </view>

      <view v-if="hasMore" class="home__more" @click="loadMore">
        {{ loading ? '加载中…' : '加载更多门店' }}
      </view>
      <view v-else class="home__end">没有更多门店了</view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { onLoad, onShow, onUnload } from '@dcloudio/uni-app'
import { fetchNearbyShops } from '@/api/shop'
import type { ShopVO } from '@/api/types'
import { fenToYuan } from '@/utils/money'
import { useAuthStore } from '@/stores/auth'

interface StoredLocation {
  lng: number
  lat: number
  city: string
}

const auth = useAuthStore()

// 本期单城市，定位失败或未授权时按北京中心坐标兜底，不阻断浏览
const BEIJING: StoredLocation = { lng: 116.397428, lat: 39.90923, city: '北京' }
const location = reactive<StoredLocation>({ ...BEIJING })

/** 定位兜底超时：H5 未授权场景下 getLocation 可能长时间无回调 */
const LOCATE_TIMEOUT_MS = 5000
let locateTimer: ReturnType<typeof setTimeout> | null = null

const shops = ref<ShopVO[]>([])
const total = ref(0)
const pageNum = ref(1)
const loading = ref(false)
const error = ref('')
const locating = ref(false)
const locFailed = ref(false)
const loaded = ref(false)
const hasMore = computed(() => shops.value.length < total.value)

onLoad(() => {
  const saved = uni.getStorageSync('location') as Partial<StoredLocation> | ''
  if (saved && typeof saved === 'object' && Number(saved.lng) && Number(saved.lat)) {
    location.lng = Number(saved.lng)
    location.lat = Number(saved.lat)
    location.city = String(saved.city || BEIJING.city)
    loadShops(true)
  } else {
    locate()
  }
})

onShow(() => {
  if (loaded.value && shops.value.length === 0 && !loading.value) {
    loadShops(true)
  }
})

function persistLocation(): void {
  uni.setStorageSync('location', { lng: location.lng, lat: location.lat, city: location.city })
}

/** 定位兜底：回落到北京中心坐标并落盘，保证下次进入直接出列表 */
function applyFallbackLocation(): void {
  location.lng = BEIJING.lng
  location.lat = BEIJING.lat
  location.city = BEIJING.city
  persistLocation()
}

function clearLocateTimer(): void {
  if (locateTimer !== null) {
    clearTimeout(locateTimer)
    locateTimer = null
  }
}

// H5 未授权定位时 uni.getLocation 可能既不 success 也不 fail，必须自带超时兜底
onUnload(() => {
  clearLocateTimer()
})

function locate(): void {
  if (locating.value) {
    return
  }
  locating.value = true
  locFailed.value = false
  clearLocateTimer()

  // 一次性守卫：超时兜底与真实回调只允许一方生效，避免重复请求门店列表
  let settled = false

  locateTimer = setTimeout(() => {
    locateTimer = null
    if (settled) {
      return
    }
    settled = true
    // 超时分支拿不到 complete 回调，这里手动复位定位态，避免页面永久卡「定位中…」
    locating.value = false
    locFailed.value = true
    uni.showToast({ title: '定位超时，已按北京展示', icon: 'none' })
    applyFallbackLocation()
    loadShops(true)
  }, LOCATE_TIMEOUT_MS)

  uni.getLocation({
    type: 'gcj02',
    success: (res) => {
      if (settled) {
        return
      }
      settled = true
      clearLocateTimer()
      location.lng = res.longitude
      location.lat = res.latitude
      // 单城市本期写死北京，多城市由后续版本的城市选择页替换
      location.city = '北京'
      locFailed.value = false
      persistLocation()
      loadShops(true)
    },
    fail: () => {
      if (settled) {
        return
      }
      settled = true
      clearLocateTimer()
      locFailed.value = true
      uni.showToast({ title: '定位失败，已按北京展示', icon: 'none' })
      applyFallbackLocation()
      loadShops(true)
    },
    complete: () => {
      locating.value = false
    },
  })
}

async function loadShops(reset: boolean): Promise<void> {
  if (loading.value) {
    return
  }
  loading.value = true
  error.value = ''
  try {
    const result = await fetchNearbyShops({
      lng: location.lng,
      lat: location.lat,
      pageNum: reset ? 1 : pageNum.value + 1,
      pageSize: 10,
    })
    shops.value = reset ? result.list : shops.value.concat(result.list)
    total.value = result.total
    pageNum.value = result.pageNum
    loaded.value = true
  } catch (err) {
    error.value = (err as Error)?.message || '门店加载失败'
  } finally {
    loading.value = false
  }
}

function reload(): void {
  loadShops(true)
}

function goNearby(): void {
  uni.navigateTo({ url: '/pages/nearby/Nearby' })
}

function loadMore(): void {
  if (!hasMore.value || loading.value) {
    return
  }
  loadShops(false)
}

function openShop(shop: ShopVO): void {
  uni.navigateTo({ url: `/pages/shop/Shop?id=${shop.id}` })
}

function goOrders(): void {
  if (!auth.ensureLogin('/pages/order/List')) {
    return
  }
  uni.switchTab({ url: '/pages/order/List' })
}

function onSearchTap(): void {
  uni.navigateTo({ url: '/pages/search/Search' })
}

function ratingText(rating: number | null): string {
  return rating ? Number(rating).toFixed(1) : '--'
}

function distanceText(distanceM: number | null): string {
  if (distanceM === null || distanceM === undefined) {
    return '距离未知'
  }
  return distanceM < 1000 ? `${distanceM}m` : `${(distanceM / 1000).toFixed(1)}km`
}
</script>

<style scoped>
.home {
  padding-top: 20rpx;
}

.home__top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 20rpx;
}

.home__location {
  display: flex;
  flex-direction: column;
}

.home__city {
  font-size: 32rpx;
  font-weight: 600;
}

.home__loc {
  font-size: 20rpx;
  color: #8a8a8a;
}

.home__search {
  flex: 1;
  display: flex;
  align-items: center;
  height: 68rpx;
  margin-left: 24rpx;
  padding: 0 24rpx;
  border-radius: 34rpx;
  background-color: #ffffff;
  box-shadow: 0 2rpx 8rpx rgba(20, 20, 20, 0.04);
}

.home__search-icon {
  color: #ff7a00;
  font-size: 30rpx;
  margin-right: 12rpx;
}

.home__search-text {
  color: #b0b0b0;
  font-size: 24rpx;
}

.home__banner {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 32rpx;
  border-radius: 8rpx;
  background: linear-gradient(120deg, #ff8a1f 0%, #ff5c00 100%);
  color: #ffffff;
}

.home__banner-copy {
  display: flex;
  flex-direction: column;
}

.home__banner-title {
  font-size: 38rpx;
  font-weight: 600;
  letter-spacing: 1rpx;
}

.home__banner-sub {
  margin-top: 8rpx;
  font-size: 24rpx;
  opacity: 0.9;
}

.home__banner-badge {
  padding: 8rpx 16rpx;
  border-radius: 8rpx;
  background-color: rgba(255, 255, 255, 0.22);
  font-size: 22rpx;
}

.home__quick {
  display: flex;
  margin-top: 20rpx;
  padding: 24rpx 0;
}

.home__quick-item {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
}

.home__quick-icon {
  width: 72rpx;
  height: 72rpx;
  line-height: 72rpx;
  text-align: center;
  border-radius: 24rpx;
  background-color: #fff2e6;
  color: #ff7a00;
  font-size: 30rpx;
  font-weight: 600;
}

.home__quick-text {
  margin-top: 8rpx;
  font-size: 24rpx;
  color: #444444;
}

.home__section {
  margin: 32rpx 4rpx 16rpx;
}

.home__section-title {
  font-size: 32rpx;
  font-weight: 600;
}

.home__shop {
  display: flex;
  padding: 24rpx;
  margin-bottom: 16rpx;
}

.home__shop-logo {
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

.home__shop-img {
  width: 128rpx;
  height: 128rpx;
}

.home__shop-letter {
  color: #ff7a00;
  font-size: 48rpx;
  font-weight: 600;
}

.home__shop-body {
  flex: 1;
  min-width: 0;
  margin-left: 20rpx;
}

.home__shop-name {
  display: block;
  font-size: 30rpx;
  font-weight: 600;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.home__shop-meta {
  display: flex;
  align-items: center;
  margin-top: 6rpx;
  gap: 16rpx;
}

.home__rating {
  color: #ff7a00;
  font-size: 24rpx;
  font-weight: 600;
}

.home__shop-tags {
  display: flex;
  align-items: center;
  margin-top: 8rpx;
  min-width: 0;
}

.home__shop-address {
  flex: 1;
  min-width: 0;
  font-size: 20rpx;
  color: #a0a0a0;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.home__more,
.home__end {
  padding: 24rpx 0 40rpx;
  text-align: center;
  font-size: 24rpx;
  color: #8a8a8a;
}

.home__more {
  color: #ff7a00;
}
</style>
