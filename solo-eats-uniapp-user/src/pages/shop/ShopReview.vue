<template>
  <view class="shop-rv">
    <view class="shop-rv__head">
      <text class="shop-rv__title">{{ shopName || '店铺评价' }}</text>
      <text class="shop-rv__count">共 {{ total }} 条</text>
    </view>

    <view class="shop-rv__filters">
      <view
        v-for="option in FILTERS"
        :key="option.label"
        class="shop-rv__filter"
        :class="{ 'is-active': onlyWithImage === option.hasImage }"
        @click="changeFilter(option.hasImage)"
      >
        {{ option.label }}
      </view>
    </view>

    <view v-if="loading && reviews.length === 0" class="se-loading">正在加载评价…</view>

    <view v-else-if="error && reviews.length === 0" class="se-error">
      <text>{{ error }}</text>
      <view class="se-error__retry" @click="load(true)">重新加载</view>
    </view>

    <view v-else-if="reviews.length === 0" class="se-empty">
      <text class="se-empty__title">{{ onlyWithImage ? '暂无带图评价' : '暂无店铺评价' }}</text>
      <text>完成订单后可在订单详情提交评价</text>
    </view>

    <view v-else class="shop-rv__list">
      <view v-for="review in reviews" :key="review.id" class="se-card shop-rv__card">
        <view class="shop-rv__card-head">
          <view class="shop-rv__user">
            <text class="shop-rv__user-name">{{ userLabel(review) }}</text>
            <text v-if="review.topFlag === 1" class="shop-rv__flag">置顶</text>
          </view>
          <text class="shop-rv__time">{{ formatDateTime(review.createdAt) }}</text>
        </view>

        <view class="shop-rv__stars">
          <text
            v-for="star in 5"
            :key="star"
            class="shop-rv__star"
            :class="{ 'is-active': star <= review.overallScore }"
          >
            ★
          </text>
          <text class="shop-rv__score">{{ review.overallScore }} 分</text>
        </view>

        <text v-if="review.content" class="shop-rv__content">{{ review.content }}</text>
        <text v-else class="shop-rv__content shop-rv__content--empty">该用户未填写文字评价</text>

        <view v-if="imageList(review).length > 0" class="shop-rv__thumbs">
          <image
            v-for="(image, index) in imageList(review)"
            :key="`${review.id}-${index}`"
            class="shop-rv__thumb"
            :src="image"
            mode="aspectFill"
            @click="previewImage(review, index)"
          />
        </view>

        <view v-if="review.tags && review.tags.length > 0" class="shop-rv__tags">
          <text v-for="tag in review.tags" :key="tag" class="se-tag">{{ tag }}</text>
        </view>

        <view v-if="review.merchantReply" class="shop-rv__reply">
          <text class="shop-rv__reply-title">商家回复</text>
          <text class="shop-rv__reply-text">{{ review.merchantReply }}</text>
        </view>

        <view v-if="review.appendContent" class="shop-rv__append">
          <text class="shop-rv__append-title">追评（{{ formatDateTime(review.appendAt) }}）</text>
          <text class="shop-rv__append-text">{{ review.appendContent }}</text>
        </view>
      </view>

      <view v-if="hasMore" class="shop-rv__more" @click="load(false)">
        {{ loading ? '加载中…' : '加载更多评价' }}
      </view>
      <view v-else class="shop-rv__end">没有更多评价了</view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { onLoad, onPullDownRefresh, onReachBottom } from '@dcloudio/uni-app'
import { fetchShopReviews } from '@/api/review'
import type { ReviewVO } from '@/api/types'
import { formatDateTime } from '@/utils/date'

/**
 * 店铺评价列表。接口：GET /c/shops/{shopId}/reviews（白名单，仅返回审核通过的评价）。
 *
 * ReviewView 不返回昵称与头像：anonymous=1 显示「匿名用户」，其余显示「顾客」，
 * 不使用 userId 拼造昵称。
 */

const PAGE_SIZE = 10

const FILTERS = [
  { label: '全部评价', hasImage: false },
  { label: '只看有图', hasImage: true },
] as const

const shopId = ref(0)
const shopName = ref('')
const onlyWithImage = ref(false)
const reviews = ref<ReviewVO[]>([])
const pageNum = ref(0)
const total = ref(0)
const hasMore = ref(false)
const loading = ref(false)
const error = ref('')

let requestSequence = 0

onLoad((options) => {
  const id = Number(options?.shopId)
  const rawName = options?.shopName
  if (rawName) {
    try {
      shopName.value = decodeURIComponent(String(rawName))
    } catch {
      shopName.value = ''
    }
  }
  if (!Number.isFinite(id) || id <= 0) {
    error.value = '缺少门店参数'
    return
  }
  shopId.value = id
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
  if (!shopId.value || loading.value) {
    uni.stopPullDownRefresh()
    return
  }
  const sequence = ++requestSequence
  loading.value = true
  error.value = ''
  try {
    const page = await fetchShopReviews(shopId.value, {
      hasImage: onlyWithImage.value || undefined,
      pageNum: reset ? 1 : pageNum.value + 1,
      pageSize: PAGE_SIZE,
    })
    if (sequence !== requestSequence) {
      return
    }
    reviews.value = reset ? page.list : reviews.value.concat(page.list)
    pageNum.value = page.pageNum
    total.value = page.total
    hasMore.value = reviews.value.length < page.total
  } catch (err) {
    if (sequence === requestSequence) {
      error.value = (err as Error)?.message || '评价加载失败'
    }
  } finally {
    if (sequence === requestSequence) {
      loading.value = false
      uni.stopPullDownRefresh()
    }
  }
}

function changeFilter(hasImage: boolean): void {
  if (onlyWithImage.value === hasImage) {
    return
  }
  onlyWithImage.value = hasImage
  reviews.value = []
  total.value = 0
  pageNum.value = 0
  hasMore.value = false
  load(true)
}

function userLabel(review: ReviewVO): string {
  return review.anonymous === 1 ? '匿名用户' : '顾客'
}

function imageList(review: ReviewVO): string[] {
  return Array.isArray(review.images) ? review.images : []
}

function previewImage(review: ReviewVO, index: number): void {
  const urls = imageList(review)
  if (urls.length === 0) {
    return
  }
  uni.previewImage({ urls, current: urls[index] })
}
</script>

<style scoped>
.shop-rv {
  min-height: 100vh;
  padding: 20rpx 24rpx 40rpx;
  background-color: #f5f5f7;
}

.shop-rv__head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  padding: 4rpx 8rpx 16rpx;
}

.shop-rv__title {
  flex: 1;
  min-width: 0;
  font-size: 32rpx;
  font-weight: 600;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.shop-rv__count {
  margin-left: 16rpx;
  flex-shrink: 0;
  font-size: 24rpx;
  color: #8a8a8a;
}

.shop-rv__filters {
  display: flex;
  gap: 16rpx;
  margin-bottom: 16rpx;
}

.shop-rv__filter {
  padding: 10rpx 28rpx;
  border-radius: 28rpx;
  background-color: #ffffff;
  color: #555555;
  font-size: 24rpx;
}

.shop-rv__filter.is-active {
  background-color: #fff2e6;
  color: #ff7a00;
  font-weight: 600;
}

.shop-rv__card {
  padding: 24rpx;
  margin-bottom: 16rpx;
}

.shop-rv__card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.shop-rv__user {
  display: flex;
  align-items: center;
  gap: 10rpx;
  min-width: 0;
}

.shop-rv__user-name {
  font-size: 26rpx;
  color: #444444;
}

.shop-rv__flag {
  padding: 2rpx 10rpx;
  border-radius: 4rpx;
  background-color: #fff2e6;
  color: #ff7a00;
  font-size: 20rpx;
}

.shop-rv__time {
  font-size: 22rpx;
  color: #a0a0a0;
}

.shop-rv__stars {
  display: flex;
  align-items: center;
  gap: 4rpx;
  margin-top: 12rpx;
}

.shop-rv__star {
  font-size: 28rpx;
  color: #e0e0e0;
  line-height: 1;
}

.shop-rv__star.is-active {
  color: #ff7a00;
}

.shop-rv__score {
  margin-left: 10rpx;
  font-size: 24rpx;
  color: #ff7a00;
  font-weight: 600;
}

.shop-rv__content {
  display: block;
  margin-top: 12rpx;
  font-size: 26rpx;
  line-height: 1.75;
  color: #1f1f1f;
  word-break: break-all;
}

.shop-rv__content--empty {
  color: #b0b0b0;
}

.shop-rv__thumbs {
  display: flex;
  flex-wrap: wrap;
  gap: 12rpx;
  margin-top: 16rpx;
}

.shop-rv__thumb {
  width: 150rpx;
  height: 150rpx;
  border-radius: 8rpx;
  background-color: #f5f5f5;
}

.shop-rv__tags {
  display: flex;
  flex-wrap: wrap;
  gap: 4rpx;
  margin-top: 14rpx;
}

.shop-rv__reply,
.shop-rv__append {
  margin-top: 16rpx;
  padding: 16rpx 18rpx;
  border-radius: 8rpx;
  background-color: #f7f8fa;
}

.shop-rv__reply-title,
.shop-rv__append-title {
  display: block;
  font-size: 22rpx;
  color: #1f6feb;
}

.shop-rv__append-title {
  color: #8a8a8a;
}

.shop-rv__reply-text,
.shop-rv__append-text {
  display: block;
  margin-top: 8rpx;
  font-size: 24rpx;
  line-height: 1.7;
  color: #444444;
  word-break: break-all;
}

.shop-rv__more,
.shop-rv__end {
  padding: 24rpx 0;
  text-align: center;
  font-size: 24rpx;
  color: #8a8a8a;
}

.shop-rv__more {
  color: #ff7a00;
}
</style>
