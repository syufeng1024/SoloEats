<template>
  <view class="result">
    <view class="result__head">
      <view class="result__box">
        <text class="result__icon">⌕</text>
        <input
          v-model="searchText"
          class="result__input"
          type="text"
          confirm-type="search"
          placeholder="搜索店铺、商品"
          placeholder-class="result__placeholder"
          maxlength="32"
          @confirm="submitSearch"
        />
        <text v-if="searchText" class="result__clear" @click="clearSearchText">×</text>
      </view>
      <text class="result__submit" @click="submitSearch">搜索</text>
    </view>

    <view class="result__toolbar">
      <view class="result__tabs">
        <view
          v-for="tab in TABS"
          :key="tab.key"
          class="result__tab"
          :class="{ 'is-active': activeTab === tab.key }"
          @click="switchTab(tab.key)"
        >
          {{ tab.label }}
        </view>
      </view>
      <view class="result__filter" :class="{ 'is-active': filterActive }" @click="openFilter">
        筛选<text v-if="filterActive" class="result__filter-dot" />
      </view>
    </view>

    <view v-if="activeTab === 'shop'" class="result__body">
      <view v-if="loading && shops.length === 0" class="se-loading">正在搜索店铺…</view>

      <view v-else-if="error && shops.length === 0" class="se-error">
        <text>{{ error }}</text>
        <view class="se-error__retry" @click="reload">重新搜索</view>
      </view>

      <view v-else-if="shops.length === 0" class="se-empty">
        <text class="se-empty__title">没有找到「{{ keyword }}」相关店铺</text>
        <text>换个关键词，或放宽距离筛选</text>
        <view v-if="filterActive" class="se-error__retry result__empty-btn" @click="resetFilter">
          清除筛选
        </view>
      </view>

      <view v-else>
        <view class="result__summary">
          <text class="se-muted">共找到 {{ total }} 家店铺</text>
          <text class="se-muted">{{ filterSummary }}</text>
        </view>

        <view
          v-for="shop in shops"
          :key="shop.id"
          class="se-card result__shop"
          @click="openShop(shop.id)"
        >
          <view class="result__shop-logo">
            <image v-if="shop.logoUrl" class="result__shop-img" :src="shop.logoUrl" mode="aspectFill" />
            <text v-else class="result__shop-letter">{{ shop.shopName.slice(0, 1) }}</text>
          </view>
          <view class="result__shop-body">
            <text class="result__shop-name">{{ shop.shopName }}</text>
            <view class="result__shop-meta">
              <text class="result__rating">{{ ratingText(shop.rating) }}</text>
              <text class="se-muted">月售 {{ shop.monthSales || 0 }}</text>
              <text class="se-muted">{{ distanceText(shop.distanceM) }}</text>
            </view>
            <view class="result__shop-meta">
              <text class="se-muted">起送 ¥{{ fenToYuan(shop.minOrderAmount) }}</text>
              <text class="se-muted">配送 ¥{{ fenToYuan(shop.deliveryFee) }}</text>
            </view>
            <view class="result__shop-tags">
              <text v-if="shop.pickupEnabled === 1" class="se-tag">到店自取</text>
              <text v-if="shop.address" class="result__shop-address">{{ shop.address }}</text>
            </view>
          </view>
        </view>

        <view v-if="error" class="result__retry" @click="retryLoad">加载失败，点击重试</view>
        <view v-else-if="hasMore" class="result__more" @click="loadMore">
          {{ loading ? '加载中…' : '加载更多店铺' }}
        </view>
        <view v-else class="result__end">没有更多店铺了</view>
      </view>
    </view>

    <!-- 后端目前只有 GET /c/spus/{spuId} 详情，没有关键词搜商品的列表接口，此处不造数据 -->
    <view v-else class="result__product">
      <view class="result__product-icon">品</view>
      <text class="result__product-title">商品搜索暂未开放</text>
      <text class="result__product-desc">
        商品搜索待后端提供 GET /c/spus?keyword=
      </text>
      <text class="result__product-desc">
        当前可按关键词搜索店铺，进入门店后再浏览商品
      </text>
      <view class="result__product-btn" @click="switchTab('shop')">去搜索店铺「{{ keyword }}」</view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { onLoad, onPullDownRefresh, onReachBottom, onUnload } from '@dcloudio/uni-app'
import { searchShops } from '@/api/search'
import type { ShopSortBy } from '@/api/search'
import type { ShopVO } from '@/api/types'
import { fenToYuan } from '@/utils/money'
import { addSearchHistory } from '@/utils/searchHistory'

interface StoredLocation {
  lng: number
  lat: number
  city: string
}

interface FilterPayload {
  radius?: number
  sortBy?: ShopSortBy
}

interface TabConfig {
  key: 'shop' | 'product'
  label: string
}

const FILTER_EVENT = 'search:filter:apply'
const PAGE_SIZE = 10
const BEIJING: StoredLocation = { lng: 116.397428, lat: 39.90923, city: '北京' }

const TABS: TabConfig[] = [
  { key: 'shop', label: '店铺' },
  { key: 'product', label: '商品' },
]

const SORT_LABELS: Record<ShopSortBy, string> = {
  comprehensive: '综合排序',
  distance: '距离最近',
  rating: '评分最高',
  sales: '销量最高',
}

const location = reactive<StoredLocation>({ ...BEIJING })

const keyword = ref('')
const searchText = ref('')
const activeTab = ref<'shop' | 'product'>('shop')

const shops = ref<ShopVO[]>([])
const total = ref(0)
const pageNum = ref(0)
const hasMore = ref(false)
const loading = ref(false)
const error = ref('')
/** 记录失败的是首屏请求还是翻页请求，决定「点击重试」走哪条路径 */
const failedReset = ref(false)

const radius = ref<number | undefined>(undefined)
const sortBy = ref<ShopSortBy>('comprehensive')

const filterActive = computed(
  () => radius.value !== undefined || sortBy.value !== 'comprehensive',
)

const filterSummary = computed(() => {
  const distance = radius.value ? `距离 ${radius.value / 1000}km 内` : '不限距离'
  return `${distance} · ${SORT_LABELS[sortBy.value]}`
})

onLoad((query) => {
  const saved = uni.getStorageSync('location') as Partial<StoredLocation> | ''
  if (saved && typeof saved === 'object' && Number(saved.lng) && Number(saved.lat)) {
    location.lng = Number(saved.lng)
    location.lat = Number(saved.lat)
    location.city = String(saved.city || BEIJING.city)
  }

  keyword.value = decodeQueryValue(query?.keyword)
  searchText.value = keyword.value
  radius.value = normalizeRadius(query?.radius)
  sortBy.value = normalizeSortBy(query?.sortBy)

  uni.$on(FILTER_EVENT, applyFilter)

  if (keyword.value) {
    addSearchHistory(keyword.value)
    reload()
  } else {
    error.value = '缺少搜索关键词'
  }
})

onUnload(() => {
  uni.$off(FILTER_EVENT, applyFilter)
})

onPullDownRefresh(() => {
  if (loading.value) {
    uni.stopPullDownRefresh()
    return
  }
  reload()
})

onReachBottom(() => {
  loadMore()
})

function decodeQueryValue(value: unknown): string {
  const raw = typeof value === 'string' ? value : ''
  if (!raw) {
    return ''
  }
  try {
    return decodeURIComponent(raw).trim()
  } catch (err) {
    return raw.trim()
  }
}

function normalizeRadius(value: unknown): number | undefined {
  const numeric = Number(value)
  return Number.isFinite(numeric) && numeric >= 100 && numeric <= 50_000 ? numeric : undefined
}

function normalizeSortBy(value: unknown): ShopSortBy {
  const raw = typeof value === 'string' ? value : ''
  return Object.prototype.hasOwnProperty.call(SORT_LABELS, raw)
    ? (raw as ShopSortBy)
    : 'comprehensive'
}

function applyFilter(payload: FilterPayload): void {
  const nextRadius = normalizeRadius(payload?.radius)
  const nextSort = normalizeSortBy(payload?.sortBy)
  const changed = nextRadius !== radius.value || nextSort !== sortBy.value
  radius.value = nextRadius
  sortBy.value = nextSort
  if (changed) {
    resetAndSearch()
  }
}

function switchTab(key: 'shop' | 'product'): void {
  if (activeTab.value === key) {
    return
  }
  activeTab.value = key
}

function submitSearch(): void {
  const target = searchText.value.trim()
  if (!target) {
    uni.showToast({ title: '请输入搜索内容', icon: 'none' })
    return
  }
  if (target === keyword.value && shops.value.length > 0) {
    return
  }
  keyword.value = target
  addSearchHistory(target)
  resetAndSearch()
}

function clearSearchText(): void {
  searchText.value = ''
}

/** 下拉刷新/首屏：保留旧列表，成功后整体替换，失败时不清空已有数据 */
function reload(): void {
  if (!keyword.value) {
    uni.showToast({ title: '缺少搜索关键词', icon: 'none' })
    return
  }
  loadShops(true)
}

/** 换词或换筛选条件：上下文已变，先清空旧结果再请求 */
function resetAndSearch(): void {
  shops.value = []
  total.value = 0
  pageNum.value = 0
  hasMore.value = false
  error.value = ''
  loadShops(true)
}

function retryLoad(): void {
  error.value = ''
  if (failedReset.value) {
    loadShops(true)
  } else {
    loadMore()
  }
}

async function loadShops(reset: boolean): Promise<void> {
  if (loading.value || !keyword.value) {
    return
  }
  loading.value = true
  if (reset) {
    uni.showLoading({ title: '正在搜索', mask: true })
  }
  try {
    const page = await searchShops({
      lng: location.lng,
      lat: location.lat,
      keyword: keyword.value,
      radius: radius.value,
      sortBy: sortBy.value,
      pageNum: reset ? 1 : pageNum.value + 1,
      pageSize: PAGE_SIZE,
    })
    shops.value = reset ? page.list : shops.value.concat(page.list)
    pageNum.value = page.pageNum
    total.value = page.total
    hasMore.value = shops.value.length < page.total
    error.value = ''
    failedReset.value = false
  } catch (err) {
    error.value = (err as Error)?.message || '店铺搜索失败'
    failedReset.value = reset
  } finally {
    loading.value = false
    if (reset) {
      uni.hideLoading()
      uni.stopPullDownRefresh()
    }
  }
}

function loadMore(): void {
  if (activeTab.value !== 'shop' || !hasMore.value || loading.value) {
    return
  }
  loadShops(false)
}

function openFilter(): void {
  const params = [
    `keyword=${encodeURIComponent(keyword.value)}`,
    `sortBy=${sortBy.value}`,
  ]
  if (radius.value !== undefined) {
    params.push(`radius=${radius.value}`)
  }
  uni.navigateTo({ url: `/pages/search/Filter?${params.join('&')}` })
}

function resetFilter(): void {
  radius.value = undefined
  sortBy.value = 'comprehensive'
  resetAndSearch()
}

function openShop(shopId: number): void {
  uni.navigateTo({ url: `/pages/shop/Shop?id=${shopId}` })
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
.result {
  min-height: 100vh;
  background-color: #f5f5f7;
}

.result__head {
  display: flex;
  align-items: center;
  padding: 16rpx 24rpx;
  background-color: #ffffff;
}

.result__box {
  flex: 1;
  display: flex;
  align-items: center;
  height: 68rpx;
  padding: 0 20rpx;
  border-radius: 34rpx;
  background-color: #f2f2f4;
}

.result__icon {
  margin-right: 12rpx;
  color: #ff7a00;
  font-size: 28rpx;
}

.result__input {
  flex: 1;
  height: 68rpx;
  font-size: 26rpx;
  color: #1f1f1f;
}

.result__placeholder {
  color: #b0b0b0;
  font-size: 24rpx;
}

.result__clear {
  width: 40rpx;
  height: 40rpx;
  line-height: 36rpx;
  text-align: center;
  border-radius: 50%;
  background-color: #d8d8dc;
  color: #ffffff;
  font-size: 26rpx;
}

.result__submit {
  margin-left: 20rpx;
  color: #ff7a00;
  font-size: 28rpx;
  font-weight: 500;
}

.result__toolbar {
  position: sticky;
  top: 0;
  z-index: 10;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 24rpx;
  background-color: #ffffff;
  box-shadow: 0 2rpx 8rpx rgba(20, 20, 20, 0.04);
}

.result__tabs {
  display: flex;
  align-items: center;
}

.result__tab {
  position: relative;
  padding: 24rpx 0;
  margin-right: 48rpx;
  font-size: 28rpx;
  color: #555555;
}

.result__tab.is-active {
  color: #ff7a00;
  font-weight: 600;
}

.result__tab.is-active::after {
  content: '';
  position: absolute;
  left: 50%;
  bottom: 8rpx;
  width: 40rpx;
  height: 6rpx;
  margin-left: -20rpx;
  border-radius: 3rpx;
  background-color: #ff7a00;
}

.result__filter {
  position: relative;
  padding: 12rpx 24rpx;
  border-radius: 26rpx;
  border: 1rpx solid #e5e5e8;
  font-size: 24rpx;
  color: #555555;
}

.result__filter.is-active {
  border-color: #ff7a00;
  color: #ff7a00;
  background-color: #fff8ef;
}

.result__filter-dot {
  display: inline-block;
  width: 10rpx;
  height: 10rpx;
  margin-left: 8rpx;
  border-radius: 50%;
  background-color: #ff7a00;
  vertical-align: middle;
}

.result__body {
  padding: 20rpx 24rpx 40rpx;
}

.result__summary {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 4rpx 16rpx;
}

.result__empty-btn {
  margin-top: 24rpx;
}

.result__shop {
  display: flex;
  padding: 24rpx;
  margin-bottom: 16rpx;
}

.result__shop-logo {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 128rpx;
  height: 128rpx;
  border-radius: 8rpx;
  overflow: hidden;
  background-color: #fff2e6;
}

.result__shop-img {
  width: 128rpx;
  height: 128rpx;
}

.result__shop-letter {
  color: #ff7a00;
  font-size: 48rpx;
  font-weight: 600;
}

.result__shop-body {
  flex: 1;
  min-width: 0;
  margin-left: 20rpx;
}

.result__shop-name {
  display: block;
  font-size: 30rpx;
  font-weight: 600;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.result__shop-meta {
  display: flex;
  align-items: center;
  gap: 16rpx;
  margin-top: 6rpx;
}

.result__rating {
  color: #ff7a00;
  font-size: 24rpx;
  font-weight: 600;
}

.result__shop-tags {
  display: flex;
  align-items: center;
  margin-top: 8rpx;
  min-width: 0;
}

.result__shop-address {
  flex: 1;
  min-width: 0;
  font-size: 20rpx;
  color: #a0a0a0;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.result__more,
.result__end,
.result__retry {
  padding: 24rpx 0;
  text-align: center;
  font-size: 24rpx;
  color: #8a8a8a;
}

.result__more,
.result__retry {
  color: #ff7a00;
}

.result__product {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 120rpx 48rpx;
  text-align: center;
}

.result__product-icon {
  width: 120rpx;
  height: 120rpx;
  line-height: 120rpx;
  border-radius: 32rpx;
  background-color: #fff2e6;
  color: #ff7a00;
  font-size: 48rpx;
  font-weight: 600;
}

.result__product-title {
  margin-top: 28rpx;
  font-size: 32rpx;
  font-weight: 600;
  color: #333333;
}

.result__product-desc {
  margin-top: 12rpx;
  font-size: 24rpx;
  color: #8a8a8a;
  line-height: 1.6;
}

.result__product-btn {
  margin-top: 36rpx;
  padding: 18rpx 40rpx;
  border-radius: 8rpx;
  background: linear-gradient(135deg, #ffa24c, #ff7a00);
  color: #ffffff;
  font-size: 26rpx;
}
</style>
