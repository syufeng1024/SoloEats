<template>
  <view class="fav">
    <view class="fav__tabs">
      <view
        v-for="tab in TABS"
        :key="tab.type"
        class="fav__tab"
        :class="{ 'is-active': activeType === tab.type }"
        @click="switchTab(tab.type)"
      >
        {{ tab.label }}
      </view>
    </view>

    <view v-if="loading && items.length === 0" class="se-loading">正在加载收藏…</view>

    <view v-else-if="error && items.length === 0" class="se-error">
      <text>{{ error }}</text>
      <view class="se-error__retry" @click="load(true)">重新加载</view>
    </view>

    <view v-else-if="items.length === 0" class="se-empty">
      <text class="se-empty__title">{{ emptyTitle }}</text>
      <text>{{ emptyHint }}</text>
    </view>

    <template v-else>
      <view v-for="item in items" :key="item.id" class="se-card fav__card" @click="openItem(item)">
        <view class="fav__cover">
          <image v-if="item.image" class="fav__cover-img" :src="item.image" mode="aspectFill" />
          <text v-else class="fav__cover-letter">{{ item.title.slice(0, 1) || '收' }}</text>
        </view>

        <view class="fav__body">
          <view class="fav__head">
            <text class="fav__title">{{ item.title }}</text>
            <text class="fav__type">{{ typeLabel(item.targetType) }}</text>
          </view>
          <text class="fav__sub">{{ item.subtitle }}</text>
          <text v-if="item.meta" class="fav__meta">{{ item.meta }}</text>
        </view>

        <view class="fav__remove" @click.stop="confirmRemove(item)">取消</view>
      </view>

      <view v-if="hasMore" class="fav__load-more" @click="load(false)">
        {{ loading ? '加载中…' : '加载更多' }}
      </view>
      <view v-else class="fav__end">没有更多收藏了</view>
    </template>
  </view>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { onPullDownRefresh, onReachBottom, onShow } from '@dcloudio/uni-app'
import {
  FAVORITE_TYPE_PRODUCT,
  FAVORITE_TYPE_SHOP,
  fetchFavorites,
  removeFavorite,
  type FavoriteVO,
} from '@/api/favorite'
import { fetchShopDetail, fetchSpuDetail } from '@/api/shop'
import { useAuthStore } from '@/stores/auth'

/**
 * 我的收藏（规格书 5.2.9）。
 *
 * FavoriteView 只有 targetType + targetId（member 模块不依赖 store/product），
 * 这里按类型分别调用已有的 /c/shops/{id} 与 /c/spus/{id} 补全名称、图片与销量；
 * 补全失败时保留「店铺 #id / 商品 #id」，不伪造数据。
 */

const PAGE_SIZE = 10

const TABS = [
  { type: FAVORITE_TYPE_SHOP, label: '收藏店铺' },
  { type: FAVORITE_TYPE_PRODUCT, label: '收藏商品' },
] as const

interface FavoriteItem extends FavoriteVO {
  title: string
  subtitle: string
  image: string | null
  meta: string
}

const auth = useAuthStore()

const activeType = ref<number>(FAVORITE_TYPE_SHOP)
const items = ref<FavoriteItem[]>([])
const pageNum = ref(0)
const total = ref(0)
const loading = ref(false)
const error = ref('')

/** 切 Tab / 下拉刷新时递增，旧请求返回后直接丢弃，避免上一个 Tab 的数据串台。 */
let loadSequence = 0

const hasMore = computed(() => items.value.length < total.value)

const emptyTitle = computed(() =>
  activeType.value === FAVORITE_TYPE_SHOP ? '还没有收藏店铺' : '还没有收藏商品',
)

const emptyHint = computed(() =>
  activeType.value === FAVORITE_TYPE_SHOP
    ? '在门店首页点击「☆ 收藏」即可保存'
    : '收藏的商品会出现在这里',
)

onShow(() => {
  if (!auth.ensureLogin('/pages/mine/Favorites')) {
    return
  }
  void load(true)
})

onPullDownRefresh(() => {
  void load(true)
})

onReachBottom(() => {
  if (hasMore.value && !loading.value) {
    void load(false)
  }
})

function typeLabel(targetType: number): string {
  return targetType === FAVORITE_TYPE_SHOP ? '店铺' : '商品'
}

function switchTab(targetType: number): void {
  if (activeType.value === targetType) {
    return
  }
  activeType.value = targetType
  items.value = []
  total.value = 0
  pageNum.value = 0
  error.value = ''
  void load(true)
}

async function load(reset: boolean): Promise<void> {
  if (loading.value && !reset) {
    uni.stopPullDownRefresh()
    return
  }
  const sequence = ++loadSequence
  const targetType = activeType.value
  const nextPageNum = reset ? 1 : pageNum.value + 1

  loading.value = true
  error.value = ''
  try {
    const page = await fetchFavorites({
      targetType,
      pageNum: nextPageNum,
      pageSize: PAGE_SIZE,
    })
    if (sequence !== loadSequence) {
      return
    }
    const mapped: FavoriteItem[] = page.list.map((favorite) => ({
      ...favorite,
      title: `${typeLabel(favorite.targetType)} #${favorite.targetId}`,
      subtitle: '正在加载收藏信息…',
      image: null,
      meta: '',
    }))
    items.value = reset ? mapped : items.value.concat(mapped)
    pageNum.value = page.pageNum
    total.value = page.total
    await resolveItems(mapped, sequence)
  } catch (err) {
    if (sequence !== loadSequence) {
      return
    }
    error.value = (err as Error)?.message || '收藏加载失败'
  } finally {
    if (sequence === loadSequence) {
      loading.value = false
      uni.stopPullDownRefresh()
    }
  }
}

/** 顺序补全，避免一次并发 10 个详情请求打满后端。 */
async function resolveItems(list: FavoriteItem[], sequence: number): Promise<void> {
  for (const item of list) {
    if (sequence !== loadSequence) {
      return
    }
    await resolveItem(item, sequence)
  }
}

async function resolveItem(item: FavoriteItem, sequence: number): Promise<void> {
  try {
    if (item.targetType === FAVORITE_TYPE_SHOP) {
      const shop = await fetchShopDetail(item.targetId)
      if (sequence !== loadSequence) {
        return
      }
      item.title = shop.shopName || `店铺 #${item.targetId}`
      item.subtitle = shop.address || '门店地址待补充'
      item.image = shop.logoUrl
      item.meta = `${shop.rating ? Number(shop.rating).toFixed(1) : '--'} 分 · 月售 ${shop.monthSales ?? 0}`
      return
    }

    const detail = await fetchSpuDetail(item.targetId)
    if (sequence !== loadSequence) {
      return
    }
    item.title = detail.spu.spuName || `商品 #${item.targetId}`
    item.subtitle = detail.spu.subtitle || '商品详情请在门店菜单中查看'
    item.image = detail.spu.mainImage
    item.meta = `月售 ${detail.spu.monthSales ?? 0}`
  } catch {
    if (sequence !== loadSequence) {
      return
    }
    item.title = `${typeLabel(item.targetType)} #${item.targetId}`
    item.subtitle = '信息加载失败，下拉可重试'
  }
}

function openItem(item: FavoriteItem): void {
  if (item.targetType === FAVORITE_TYPE_SHOP) {
    uni.navigateTo({ url: `/pages/shop/Shop?id=${item.targetId}` })
    return
  }
  // 商品没有独立详情页，只能在所属门店菜单中查看
  uni.showToast({ title: '请在对应门店的菜单中查看该商品', icon: 'none' })
}

function confirmRemove(item: FavoriteItem): void {
  uni.showModal({
    title: '取消收藏',
    content: `确定取消收藏「${item.title}」？`,
    success: (result) => {
      if (result.confirm) {
        void removeItem(item)
      }
    },
  })
}

async function removeItem(item: FavoriteItem): Promise<void> {
  try {
    await removeFavorite(item.targetType, item.targetId)
    items.value = items.value.filter((current) => current.id !== item.id)
    total.value = Math.max(0, total.value - 1)
    uni.showToast({ title: '已取消收藏', icon: 'none' })
  } catch {
    // 错误提示由 http 层统一 toast
  }
}
</script>

<style scoped>
.fav {
  min-height: 100vh;
  padding: 20rpx 24rpx 40rpx;
  background-color: #f5f5f7;
}

.fav__tabs {
  display: flex;
  margin-bottom: 20rpx;
  padding: 8rpx;
  border-radius: 12rpx;
  background-color: #ffffff;
}

.fav__tab {
  flex: 1;
  padding: 16rpx 0;
  text-align: center;
  border-radius: 8rpx;
  color: #666666;
  font-size: 28rpx;
}

.fav__tab.is-active {
  background-color: #fff2e6;
  color: #ff7a00;
  font-weight: 600;
}

.fav__card {
  display: flex;
  align-items: center;
  margin-bottom: 16rpx;
  padding: 20rpx;
}

.fav__cover {
  width: 112rpx;
  height: 112rpx;
  border-radius: 8rpx;
  overflow: hidden;
  background-color: #fff2e6;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.fav__cover-img {
  width: 112rpx;
  height: 112rpx;
}

.fav__cover-letter {
  color: #ff7a00;
  font-size: 40rpx;
  font-weight: 600;
}

.fav__body {
  flex: 1;
  min-width: 0;
  margin-left: 20rpx;
  display: flex;
  flex-direction: column;
}

.fav__head {
  display: flex;
  align-items: center;
}

.fav__title {
  flex: 1;
  min-width: 0;
  color: #1f1f1f;
  font-size: 30rpx;
  font-weight: 600;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.fav__type {
  margin-left: 12rpx;
  padding: 2rpx 12rpx;
  border-radius: 16rpx;
  background-color: #fff2e6;
  color: #ff7a00;
  font-size: 20rpx;
  flex-shrink: 0;
}

.fav__sub {
  margin-top: 8rpx;
  color: #8a8a8a;
  font-size: 24rpx;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.fav__meta {
  margin-top: 6rpx;
  color: #a26a2a;
  font-size: 22rpx;
}

.fav__remove {
  margin-left: 16rpx;
  padding: 10rpx 18rpx;
  border: 1rpx solid #e5e5e5;
  border-radius: 24rpx;
  color: #8a8a8a;
  font-size: 22rpx;
  flex-shrink: 0;
}

.fav__load-more,
.fav__end {
  padding: 24rpx 0;
  text-align: center;
  color: #999999;
  font-size: 24rpx;
}
</style>
