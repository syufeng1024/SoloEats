<template>
  <view class="nearby">
    <view class="nearby__head">
      <view class="nearby__head-copy">
        <text class="nearby__city">{{ location.city }}</text>
        <text class="nearby__desc">
          {{ locating ? '正在定位…' : `5 公里内 · 共 ${total} 家门店` }}
        </text>
      </view>
      <text class="nearby__relocate" @click="locate">重新定位</text>
    </view>

    <view class="nearby__sorts">
      <view
        v-for="option in SORT_OPTIONS"
        :key="option.value"
        class="nearby__sort"
        :class="{ 'is-active': sortBy === option.value }"
        @click="changeSort(option.value)"
      >
        {{ option.label }}
      </view>
    </view>

    <view v-if="loading && shops.length === 0" class="se-loading">正在加载附近门店…</view>

    <view v-else-if="error && shops.length === 0" class="se-error">
      <text>{{ error }}</text>
      <view class="se-error__retry" @click="load(true)">重新加载</view>
    </view>

    <view v-else-if="shops.length === 0" class="se-empty">
      <text class="se-empty__title">附近暂无营业门店</text>
      <text>试试重新定位，或换一家门店逛逛</text>
    </view>

    <view v-else class="nearby__list">
      <view
        v-for="shop in shops"
        :key="shop.id"
        class="se-card nearby__shop"
        @click="openShop(shop)"
      >
        <view class="nearby__logo">
          <image v-if="shop.logoUrl" class="nearby__logo-img" :src="shop.logoUrl" mode="aspectFill" />
          <text v-else class="nearby__logo-letter">{{ shop.shopName.slice(0, 1) }}</text>
        </view>

        <view class="nearby__body">
          <view class="nearby__title-row">
            <text class="nearby__name">{{ shop.shopName }}</text>
            <text class="nearby__distance">{{ distanceText(shop.distanceM) }}</text>
          </view>

          <view class="nearby__meta">
            <text class="nearby__rating">{{ ratingText(shop.rating) }} 分</text>
            <text class="se-muted">月售 {{ shop.monthSales || 0 }}</text>
            <text class="se-muted">{{ shop.prepMinutes || 30 }} 分钟</text>
          </view>

          <view class="nearby__meta">
            <text class="se-muted">起送 ¥{{ fenToYuan(shop.minOrderAmount) }}</text>
            <text class="se-muted">配送 ¥{{ fenToYuan(shop.deliveryFee) }}</text>
            <text v-if="shop.pickupEnabled === 1" class="se-tag">到店自取</text>
          </view>

          <text v-if="shop.address" class="nearby__address">{{ shop.address }}</text>
        </view>
      </view>

      <view v-if="hasMore" class="nearby__more" @click="load(false)">
        {{ loading ? '加载中…' : '加载更多门店' }}
      </view>
      <view v-else class="nearby__end">没有更多门店了</view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { onLoad, onPullDownRefresh, onReachBottom, onUnload } from '@dcloudio/uni-app'
import { fetchNearbyShops } from '@/api/shop'
import type { ShopVO } from '@/api/types'
import { fenToYuan } from '@/utils/money'

/**
 * 附近找店：列表 + 排序（综合/距离/销量）。
 *
 * 地图视图降级说明：H5 的 uni map 组件需要在高德/腾讯地图控制台配置 key 并写入
 * manifest，本工程未接入 key，强行渲染会得到空白地图，因此本期只做列表 + 距离排序，
 * 门店卡片点击可进入点餐页。地图视图待地图 key 就绪后在本页追加。
 */

interface StoredLocation {
  lng: number
  lat: number
  city: string
}

const BEIJING: StoredLocation = { lng: 116.397428, lat: 39.90923, city: '北京' }
const PAGE_SIZE = 10
const LOCATE_TIMEOUT_MS = 5000

const SORT_OPTIONS = [
  { label: '综合排序', value: 'comprehensive' },
  { label: '距离最近', value: 'distance' },
  { label: '销量最高', value: 'sales' },
] as const

const location = reactive<StoredLocation>({ ...BEIJING })
const sortBy = ref<string>('comprehensive')
const shops = ref<ShopVO[]>([])
const total = ref(0)
const pageNum = ref(1)
const loading = ref(false)
const locating = ref(false)
const error = ref('')

const hasMore = computed(() => shops.value.length < total.value)

let locateTimer: ReturnType<typeof setTimeout> | null = null

onLoad(() => {
  readStoredLocation()
  load(true)
})

onPullDownRefresh(() => {
  load(true)
})

onReachBottom(() => {
  if (hasMore.value && !loading.value) {
    load(false)
  }
})

onUnload(() => {
  clearLocateTimer()
})

function readStoredLocation(): void {
  const saved = uni.getStorageSync('location') as Partial<StoredLocation> | ''
  if (saved && typeof saved === 'object' && Number(saved.lng) && Number(saved.lat)) {
    location.lng = Number(saved.lng)
    location.lat = Number(saved.lat)
    location.city = String(saved.city || BEIJING.city)
  }
}

function persistLocation(): void {
  uni.setStorageSync('location', { lng: location.lng, lat: location.lat, city: location.city })
}

function clearLocateTimer(): void {
  if (locateTimer !== null) {
    clearTimeout(locateTimer)
    locateTimer = null
  }
}

function locate(): void {
  if (locating.value) {
    return
  }
  locating.value = true
  clearLocateTimer()

  // H5 未授权定位时可能既不 success 也不 fail，用自建超时兜底，避免按钮永久禁用
  let settled = false
  locateTimer = setTimeout(() => {
    locateTimer = null
    if (settled) {
      return
    }
    settled = true
    locating.value = false
    uni.showToast({ title: '定位超时，仍按上次位置展示', icon: 'none' })
    load(true)
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
      location.city = '北京'
      persistLocation()
      load(true)
    },
    fail: () => {
      if (settled) {
        return
      }
      settled = true
      clearLocateTimer()
      uni.showToast({ title: '定位失败，仍按上次位置展示', icon: 'none' })
      load(true)
    },
    complete: () => {
      locating.value = false
    },
  })
}

async function load(reset: boolean): Promise<void> {
  if (loading.value) {
    uni.stopPullDownRefresh()
    return
  }
  loading.value = true
  error.value = ''
  try {
    const result = await fetchNearbyShops({
      lng: location.lng,
      lat: location.lat,
      sortBy: sortBy.value,
      pageNum: reset ? 1 : pageNum.value + 1,
      pageSize: PAGE_SIZE,
    })
    shops.value = reset ? result.list : shops.value.concat(result.list)
    total.value = result.total
    pageNum.value = result.pageNum
  } catch (err) {
    error.value = (err as Error)?.message || '门店加载失败'
  } finally {
    loading.value = false
    uni.stopPullDownRefresh()
  }
}

function changeSort(value: string): void {
  if (sortBy.value === value) {
    return
  }
  sortBy.value = value
  shops.value = []
  total.value = 0
  pageNum.value = 1
  load(true)
}

function openShop(shop: ShopVO): void {
  uni.navigateTo({ url: `/pages/shop/Shop?id=${shop.id}` })
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
.nearby {
  min-height: 100vh;
  padding: 20rpx 24rpx 40rpx;
  background-color: #f5f5f7;
}

.nearby__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 4rpx 8rpx 20rpx;
}

.nearby__head-copy {
  display: flex;
  flex-direction: column;
}

.nearby__city {
  font-size: 34rpx;
  font-weight: 600;
}

.nearby__desc {
  margin-top: 4rpx;
  font-size: 22rpx;
  color: #8a8a8a;
}

.nearby__relocate {
  padding: 8rpx 22rpx;
  border: 1rpx solid #ffcfa6;
  border-radius: 28rpx;
  color: #ff7a00;
  font-size: 24rpx;
}

.nearby__sorts {
  display: flex;
  align-items: center;
  margin-bottom: 16rpx;
  padding: 8rpx;
  border-radius: 8rpx;
  background-color: #ffffff;
}

.nearby__sort {
  flex: 1;
  padding: 14rpx 0;
  text-align: center;
  font-size: 26rpx;
  color: #555555;
  border-radius: 6rpx;
}

.nearby__sort.is-active {
  background-color: #fff2e6;
  color: #ff7a00;
  font-weight: 600;
}

.nearby__shop {
  display: flex;
  padding: 24rpx;
  margin-bottom: 16rpx;
}

.nearby__logo {
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

.nearby__logo-img {
  width: 128rpx;
  height: 128rpx;
}

.nearby__logo-letter {
  color: #ff7a00;
  font-size: 48rpx;
  font-weight: 600;
}

.nearby__body {
  flex: 1;
  min-width: 0;
  margin-left: 20rpx;
}

.nearby__title-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.nearby__name {
  flex: 1;
  min-width: 0;
  font-size: 30rpx;
  font-weight: 600;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.nearby__distance {
  margin-left: 12rpx;
  flex-shrink: 0;
  font-size: 24rpx;
  color: #ff7a00;
}

.nearby__meta {
  display: flex;
  align-items: center;
  gap: 16rpx;
  margin-top: 6rpx;
}

.nearby__rating {
  font-size: 24rpx;
  color: #ff7a00;
  font-weight: 600;
}

.nearby__address {
  display: block;
  margin-top: 8rpx;
  font-size: 22rpx;
  color: #a0a0a0;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.nearby__more,
.nearby__end {
  padding: 24rpx 0 40rpx;
  text-align: center;
  font-size: 24rpx;
  color: #8a8a8a;
}

.nearby__more {
  color: #ff7a00;
}
</style>
