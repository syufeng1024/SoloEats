<template>
  <view class="rv">
    <view v-if="loading && reviews.length === 0" class="se-loading">正在加载评价…</view>

    <view v-else-if="error && reviews.length === 0" class="se-error">
      <text>{{ error }}</text>
      <view class="se-error__retry" @click="load(true)">重新加载</view>
    </view>

    <view v-else-if="reviews.length === 0" class="se-empty">
      <text class="se-empty__title">还没有提交过评价</text>
      <text>订单完成后可在订单详情点击「去评价」</text>
      <view class="se-error__retry rv__empty-btn" @click="goOrders">去订单列表</view>
    </view>

    <template v-else>
      <view class="rv__summary">
        <text>共 {{ total }} 条评价</text>
        <text v-if="error" class="rv__error-retry" @click="load(true)">刷新失败，点击重试</text>
      </view>

      <view
        v-for="review in reviews"
        :key="review.id"
        class="se-card rv__card"
        @click="openDetail(review)"
      >
        <view class="se-between rv__head">
          <text class="rv__order">{{ orderLabel(review) }}</text>
          <view class="rv__score">
            <text class="rv__score-num">{{ review.overallScore }}</text>
            <text class="rv__score-unit">分</text>
          </view>
        </view>

        <view class="rv__stars">
          <text
            v-for="star in 5"
            :key="star"
            class="rv__star"
            :class="{ 'is-active': star <= review.overallScore }"
          >
            ★
          </text>
          <text v-if="review.anonymous === 1" class="rv__flag">匿名</text>
          <text v-if="reviewStatusLabel(review.status)" class="rv__flag rv__flag--muted">
            {{ reviewStatusLabel(review.status) }}
          </text>
        </view>

        <text class="rv__goods">{{ goodsLabel(review) }}</text>

        <text v-if="review.content" class="rv__content">{{ review.content }}</text>
        <text v-else class="rv__content rv__content--empty">未填写文字评价</text>

        <view v-if="imageList(review).length > 0" class="rv__thumbs">
          <image
            v-for="(image, index) in imageList(review).slice(0, 3)"
            :key="`${review.id}-${index}`"
            :src="image"
            mode="aspectFill"
            class="rv__thumb"
          />
          <text v-if="imageList(review).length > 3" class="rv__thumb-more">
            +{{ imageList(review).length - 3 }}
          </text>
        </view>

        <view v-if="review.tags && review.tags.length > 0" class="rv__tags">
          <text v-for="tag in review.tags" :key="tag" class="se-tag">{{ tag }}</text>
        </view>

        <view class="rv__foot">
          <view class="rv__foot-left">
            <text v-if="review.appendContent" class="rv__state">已追评</text>
            <text v-if="review.merchantReply" class="rv__state rv__state--reply">商家已回复</text>
            <text class="rv__time">{{ formatDateTime(review.createdAt) }}</text>
          </view>
          <text class="rv__more">查看详情</text>
        </view>
      </view>

      <view v-if="hasMore" class="rv__load-more" @click="load(false)">
        {{ loading ? '加载中…' : '加载更多' }}
      </view>
      <view v-else class="rv__end">没有更多评价了</view>
    </template>
  </view>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { onPullDownRefresh, onReachBottom, onShow } from '@dcloudio/uni-app'
import { fetchOrderDetail } from '@/api/order'
import { fetchMyReviews } from '@/api/review'
import type { ReviewVO } from '@/api/types'
import { formatDateTime } from '@/utils/date'
import { cacheReview } from '@/utils/reviewCache'
import { useAuthStore } from '@/stores/auth'

/**
 * 我的评价列表。
 *
 * ReviewView 只有 orderId / shopId / skuId，没有订单号与商品名。
 * 为了展示「订单号 + 商品」，这里按 orderId 调用 GET /orders/{id} 补全，
 * 每个 orderId 只请求一次并缓存；补全失败时退回展示真实 id，不拼接假数据。
 */

const PAGE_SIZE = 10

interface OrderBrief {
  orderNo: string
  firstItemName: string
  namesBySku: Record<number, string>
}

const auth = useAuthStore()

const reviews = ref<ReviewVO[]>([])
const pageNum = ref(0)
const total = ref(0)
const hasMore = ref(false)
const loading = ref(false)
const error = ref('')

const briefs = reactive<Record<number, OrderBrief | null>>({})
const resolving = reactive<Record<number, boolean>>({})

onShow(() => {
  if (!auth.ensureLogin('/pages/review/List')) {
    return
  }
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

async function load(reset: boolean): Promise<void> {
  if (loading.value) {
    uni.stopPullDownRefresh()
    return
  }
  loading.value = true
  error.value = ''
  try {
    const page = await fetchMyReviews(reset ? 1 : pageNum.value + 1, PAGE_SIZE)
    reviews.value = reset ? page.list : reviews.value.concat(page.list)
    pageNum.value = page.pageNum
    total.value = page.total
    hasMore.value = reviews.value.length < page.total
    void resolveOrders(page.list)
  } catch (err) {
    error.value = (err as Error)?.message || '评价加载失败'
    if (reset) {
      uni.showToast({ title: error.value, icon: 'none' })
    }
  } finally {
    loading.value = false
    uni.stopPullDownRefresh()
  }
}

async function resolveOrders(list: ReviewVO[]): Promise<void> {
  const pending = Array.from(new Set(list.map((review) => review.orderId)))
    .filter(
      (orderId) =>
        orderId > 0 &&
        !Object.prototype.hasOwnProperty.call(briefs, orderId) &&
        !resolving[orderId],
    )
    .slice(0, PAGE_SIZE)

  await Promise.all(
    pending.map(async (orderId) => {
      resolving[orderId] = true
      try {
        const detail = await fetchOrderDetail(orderId, true)
        const namesBySku: Record<number, string> = {}
        detail.items.forEach((item) => {
          namesBySku[item.skuId] = item.skuName
        })
        briefs[orderId] = {
          orderNo: detail.orderNo,
          firstItemName: detail.items[0]?.skuName || '',
          namesBySku,
        }
      } catch (err) {
        // 订单详情失败时保留 id 展示，不渲染编造的商品名
        briefs[orderId] = null
      } finally {
        resolving[orderId] = false
      }
    }),
  )
}

function orderLabel(review: ReviewVO): string {
  const brief = briefs[review.orderId]
  return brief ? `订单 ${brief.orderNo}` : `订单 #${review.orderId}`
}

function goodsLabel(review: ReviewVO): string {
  const brief = briefs[review.orderId]
  if (brief && review.skuId && brief.namesBySku[review.skuId]) {
    return brief.namesBySku[review.skuId]
  }
  if (brief && brief.firstItemName) {
    return `${brief.firstItemName} 等`
  }
  if (review.skuId) {
    return `商品 #${review.skuId} · 门店 #${review.shopId}`
  }
  return `门店 #${review.shopId}`
}

function reviewStatusLabel(status: number | null): string {
  if (status === 0) {
    return '审核中'
  }
  if (status === 2) {
    return '已隐藏'
  }
  return ''
}

function imageList(review: ReviewVO): string[] {
  return Array.isArray(review.images) ? review.images : []
}

function openDetail(review: ReviewVO): void {
  cacheReview(review)
  uni.navigateTo({ url: `/pages/review/Detail?id=${review.id}` })
}

function goOrders(): void {
  uni.switchTab({ url: '/pages/order/List' })
}
</script>

<style scoped>
.rv {
  min-height: 100vh;
  padding: 20rpx 24rpx 40rpx;
  background-color: #f5f5f7;
}

.rv__summary {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 4rpx 8rpx 20rpx;
  font-size: 24rpx;
  color: #8a8a8a;
}

.rv__error-retry {
  color: #ff7a00;
}

.rv__empty-btn {
  margin-top: 24rpx;
}

.rv__card {
  padding: 24rpx;
  margin-bottom: 16rpx;
}

.rv__head {
  margin-bottom: 8rpx;
}

.rv__order {
  font-size: 24rpx;
  color: #8a8a8a;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
  max-width: 70%;
}

.rv__score {
  display: flex;
  align-items: baseline;
  color: #ff7a00;
}

.rv__score-num {
  font-size: 34rpx;
  font-weight: 600;
}

.rv__score-unit {
  font-size: 22rpx;
  margin-left: 2rpx;
}

.rv__stars {
  display: flex;
  align-items: center;
  gap: 6rpx;
  margin-bottom: 12rpx;
}

.rv__star {
  font-size: 30rpx;
  color: #e0e0e0;
  line-height: 1;
}

.rv__star.is-active {
  color: #ff7a00;
}

.rv__flag {
  margin-left: 12rpx;
  padding: 2rpx 10rpx;
  border-radius: 4rpx;
  font-size: 20rpx;
  color: #ff7a00;
  background-color: #fff2e6;
}

.rv__flag--muted {
  color: #8a8a8a;
  background-color: #f2f2f4;
}

.rv__goods {
  display: block;
  font-size: 28rpx;
  font-weight: 600;
  color: #1f1f1f;
  margin-bottom: 10rpx;
}

.rv__content {
  display: block;
  font-size: 26rpx;
  line-height: 1.7;
  color: #444444;
  word-break: break-all;
}

.rv__content--empty {
  color: #b8b8b8;
}

.rv__thumbs {
  display: flex;
  align-items: center;
  gap: 12rpx;
  margin-top: 16rpx;
}

.rv__thumb {
  width: 140rpx;
  height: 140rpx;
  border-radius: 8rpx;
  background-color: #f5f5f5;
}

.rv__thumb-more {
  font-size: 24rpx;
  color: #8a8a8a;
}

.rv__tags {
  display: flex;
  flex-wrap: wrap;
  gap: 4rpx;
  margin-top: 12rpx;
}

.rv__foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 18rpx;
  padding-top: 16rpx;
  border-top: 1rpx solid #f5f5f5;
}

.rv__foot-left {
  display: flex;
  align-items: center;
  gap: 12rpx;
  min-width: 0;
}

.rv__state {
  padding: 2rpx 10rpx;
  border-radius: 4rpx;
  font-size: 20rpx;
  color: #52c41a;
  background-color: #f0f9eb;
}

.rv__state--reply {
  color: #1f6feb;
  background-color: #eef4ff;
}

.rv__time {
  font-size: 22rpx;
  color: #a0a0a0;
}

.rv__more {
  font-size: 24rpx;
  color: #ff7a00;
  flex-shrink: 0;
}

.rv__load-more,
.rv__end {
  padding: 24rpx 0;
  text-align: center;
  font-size: 24rpx;
  color: #8a8a8a;
}

.rv__load-more {
  color: #ff7a00;
}
</style>
