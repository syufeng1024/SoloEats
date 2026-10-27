<template>
  <view class="review mt-page">
    <!-- 筛选：评分等级 + 回复状态 -->
    <view class="review__filters mt-card">
      <scroll-view scroll-x class="review__tabs" :show-scrollbar="false">
        <view
          v-for="tab in SCORE_TABS"
          :key="String(tab.value)"
          class="review__tab"
          :class="{ 'is-active': tab.value === scoreLevel }"
          @click="changeScoreLevel(tab.value)"
        >
          {{ tab.label }}
        </view>
      </scroll-view>

      <view class="review__reply-tabs">
        <view
          v-for="tab in REPLY_TABS"
          :key="String(tab.value)"
          class="review__reply-tab"
          :class="{ 'is-active': tab.value === replyStatus }"
          @click="changeReplyStatus(tab.value)"
        >
          {{ tab.label }}
        </view>
      </view>

      <text class="review__filter-note">
        列表只展示已审核通过的评价（未审核评价不能回复）；共 {{ total }} 条。
      </text>
    </view>

    <view v-if="loading && reviews.length === 0" class="mt-loading">正在加载评价…</view>

    <view v-else-if="error && reviews.length === 0" class="mt-error">
      <text class="mt-error__msg">{{ error }}</text>
      <view class="mt-error__retry" @click="load(true)">重新加载</view>
    </view>

    <view v-else-if="reviews.length === 0" class="mt-empty review__empty">
      <text class="mt-empty__title">暂无评价</text>
      <text>{{ emptyDesc }}</text>
    </view>

    <template v-else>
      <view v-for="item in reviews" :key="item.id" class="review__row">
        <view class="mt-between">
          <view class="review__stars">
            <text
              v-for="n in 5"
              :key="n"
              class="review__star"
              :class="{ 'is-on': n <= (item.overallScore || 0) }"
            >★</text>
            <text class="review__score">{{ item.overallScore }} 分</text>
          </view>
          <text class="mt-tag" :class="item.merchantReply ? 'mt-tag--ok' : 'mt-tag--warn'">
            {{ item.merchantReply ? '已回复' : '未回复' }}
          </text>
        </view>

        <text class="review__content">{{ item.content || '用户未填写文字评价' }}</text>

        <view v-if="imageCount(item) > 0" class="review__image-count">
          共 {{ imageCount(item) }} 张图片
        </view>

        <view v-if="item.tags && item.tags.length > 0" class="review__tags">
          <text v-for="tag in item.tags" :key="tag" class="mt-tag">{{ tag }}</text>
        </view>

        <view v-if="item.appendContent" class="review__append">
          追评：{{ item.appendContent }}
        </view>

        <view v-if="item.merchantReply" class="review__reply">
          <text class="review__reply-label">商家回复</text>
          <text class="review__reply-text">{{ item.merchantReply }}</text>
        </view>

        <view class="mt-between review__foot">
          <view class="review__foot-meta">
            <text class="mt-muted">{{ formatDateTime(item.createdAt) }}</text>
            <text class="mt-muted">订单 {{ item.orderId ?? '--' }}</text>
          </view>
          <text v-if="!item.merchantReply" class="review__action" @click="openReply(item)">
            回复
          </text>
        </view>
      </view>

      <view v-if="hasMore" class="review__more" @click="loadMore">
        {{ loading ? '加载中…' : '加载更多' }}
      </view>
      <view v-else class="review__end">没有更多评价了</view>
    </template>

    <!-- 回复浮层 -->
    <view v-if="replyTarget" class="mt-mask" @click="closeReply" />
    <view v-if="replyTarget" class="mt-sheet">
      <view class="mt-between">
        <text class="mt-sheet__title">回复评价</text>
        <text class="review__close" @click="closeReply">关闭</text>
      </view>
      <view class="mt-sheet__tip">
        订单 {{ replyTarget.orderId ?? '--' }} · {{ replyTarget.overallScore }} 分 · 回复后用户端可见
      </view>

      <view class="review__origin">{{ replyTarget.content || '用户未填写文字评价' }}</view>

      <textarea
        v-model="replyText"
        class="review__textarea"
        maxlength="500"
        placeholder="请输入回复内容，最多 500 字"
        placeholder-class="mt-placeholder"
      />
      <view class="mt-between review__counter">
        <text class="mt-muted">回复只能提交一次，请确认后提交</text>
        <text class="mt-muted">{{ replyText.length }}/500</text>
      </view>

      <view class="mt-sheet__actions">
        <button class="mt-btn mt-btn--plain" @click="closeReply">取消</button>
        <button
          class="mt-btn mt-btn--primary"
          :disabled="replying || !replyText.trim()"
          @click="submitReply"
        >
          {{ replying ? '提交中…' : '提交回复' }}
        </button>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { onPullDownRefresh, onReachBottom, onShow } from '@dcloudio/uni-app'
import { ApiError } from '@/api/http'
import { pageMerchantReviews, replyReview } from '@/api/review'
import type { ReviewVO } from '@/api/types'
import { useAuthStore } from '@/stores/auth'
import { formatDateTime } from '@/utils/date'

/**
 * 评价管理。
 *
 * 接口口径（ProductReviewService#merchantPage）：
 * - merchantId 由后端从令牌解析（PermissionGuard#requireMerchantId），前端不传；
 * - 后端按该商家全部门店 + status=1（已审核）过滤，未审核评价不会出现在列表；
 * - scoreLevel：1差评(1~2星) / 2中评(3星) / 3好评(4~5星)；
 *   replyStatus：0未回复 / 1已回复；
 * - 分页由后端支持（pageNum/pageSize），滚动到底加载下一页。
 */

interface FilterTab {
  value: number | null
  label: string
}

const PAGE_SIZE = 10
const REVIEW_PATH = '/pages/review/Review'

const SCORE_TABS: FilterTab[] = [
  { value: null, label: '全部评分' },
  { value: 1, label: '差评 1-2 星' },
  { value: 2, label: '中评 3 星' },
  { value: 3, label: '好评 4-5 星' },
]

const REPLY_TABS: FilterTab[] = [
  { value: null, label: '全部' },
  { value: 0, label: '未回复' },
  { value: 1, label: '已回复' },
]

const auth = useAuthStore()

const scoreLevel = ref<number | null>(null)
const replyStatus = ref<number | null>(null)
const reviews = ref<ReviewVO[]>([])
const pageNum = ref(0)
const total = ref(0)
const loading = ref(false)
const error = ref('')

const replyTarget = ref<ReviewVO | null>(null)
const replyText = ref('')
const replying = ref(false)

let requestToken = 0

const hasMore = computed(() => reviews.value.length < total.value)

const emptyDesc = computed(() => {
  if (replyStatus.value === 0) {
    return '当前筛选下没有待回复评价'
  }
  if (scoreLevel.value === 1) {
    return '当前筛选下没有差评'
  }
  return '该筛选条件下暂无评价'
})

onShow(() => {
  if (!auth.ensureLogin(REVIEW_PATH)) {
    return
  }
  void load(true)
})

onPullDownRefresh(() => {
  void load(true)
})

onReachBottom(() => {
  loadMore()
})

function toast(message: string): void {
  uni.showToast({ title: message, icon: 'none', duration: 2400 })
}

function imageCount(item: ReviewVO): number {
  return Array.isArray(item.images) ? item.images.length : 0
}

async function load(reset: boolean): Promise<void> {
  if (!reset && loading.value) {
    return
  }
  const token = ++requestToken
  const nextPage = reset ? 1 : pageNum.value + 1
  loading.value = true
  error.value = ''
  try {
    const page = await pageMerchantReviews({
      scoreLevel: scoreLevel.value,
      replyStatus: replyStatus.value,
      pageNum: nextPage,
      pageSize: PAGE_SIZE,
      silent: true,
    })
    if (token !== requestToken) {
      return
    }
    const list = Array.isArray(page.list) ? page.list : []
    if (reset) {
      reviews.value = list
    } else {
      const merged = new Map<number, ReviewVO>()
      reviews.value.forEach((item) => merged.set(item.id, item))
      list.forEach((item) => merged.set(item.id, item))
      reviews.value = Array.from(merged.values())
    }
    total.value = Number(page.total) || 0
    pageNum.value = nextPage
  } catch (err) {
    if (token !== requestToken) {
      return
    }
    error.value = (err as Error)?.message || '评价加载失败'
    if (reset) {
      reviews.value = []
      total.value = 0
    }
  } finally {
    if (token === requestToken) {
      loading.value = false
    }
    uni.stopPullDownRefresh()
  }
}

function loadMore(): void {
  if (!hasMore.value || loading.value) {
    return
  }
  void load(false)
}

function changeScoreLevel(value: number | null): void {
  if (scoreLevel.value === value) {
    return
  }
  scoreLevel.value = value
  reviews.value = []
  pageNum.value = 0
  total.value = 0
  void load(true)
}

function changeReplyStatus(value: number | null): void {
  if (replyStatus.value === value) {
    return
  }
  replyStatus.value = value
  reviews.value = []
  pageNum.value = 0
  total.value = 0
  void load(true)
}

function openReply(item: ReviewVO): void {
  replyTarget.value = item
  replyText.value = ''
}

function closeReply(): void {
  if (replying.value) {
    return
  }
  replyTarget.value = null
  replyText.value = ''
}

async function submitReply(): Promise<void> {
  const target = replyTarget.value
  if (!target || replying.value) {
    return
  }
  const text = replyText.value.trim()
  if (!text) {
    toast('请输入回复内容')
    return
  }
  if (text.length > 500) {
    toast('回复最多 500 字')
    return
  }

  replying.value = true
  try {
    await replyReview(target.id, text)
    toast('回复成功')
    replyTarget.value = null
    replyText.value = ''
    if (replyStatus.value === 0) {
      // 未回复筛选下该条已不匹配，重新拉取以保持分页与总数一致
      reviews.value = []
      pageNum.value = 0
      total.value = 0
      void load(true)
    } else {
      const current = reviews.value.find((item) => item.id === target.id)
      if (current) {
        current.merchantReply = text
        current.replyAt = new Date().toISOString()
      }
    }
  } catch (err) {
    if (err instanceof ApiError && err.code === 10004) {
      toast('无权回复该门店评价')
    } else {
      toast((err as Error)?.message || '回复失败，请重试')
    }
  } finally {
    replying.value = false
  }
}
</script>

<style scoped>
.review {
  padding-bottom: calc(40rpx + env(safe-area-inset-bottom));
}

.review__filters {
  padding-bottom: 14rpx;
}

.review__tabs {
  white-space: nowrap;
}

.review__tab {
  display: inline-block;
  padding: 10rpx 18rpx;
  margin-right: 8rpx;
  border-radius: 8rpx;
  background-color: #f7f9fc;
  font-size: 23rpx;
  color: #55637a;
}

.review__tab.is-active {
  background-color: #fff1e6;
  color: #c2410c;
  font-weight: 700;
}

.review__reply-tabs {
  display: flex;
  gap: 10rpx;
  margin-top: 12rpx;
}

.review__reply-tab {
  flex: 1;
  height: 62rpx;
  line-height: 62rpx;
  text-align: center;
  border-radius: 8rpx;
  background-color: #f2f5f9;
  font-size: 23rpx;
  color: #44536b;
  border: 2rpx solid transparent;
}

.review__reply-tab.is-active {
  background-color: #fff1e6;
  border-color: #ff9a3c;
  color: #c2410c;
  font-weight: 700;
}

.review__filter-note {
  display: block;
  margin-top: 10rpx;
  font-size: 20rpx;
  color: #8d99aa;
  line-height: 1.6;
}

.review__empty {
  background-color: #ffffff;
  border-radius: 12rpx;
}

.review__row {
  padding: 16rpx 18rpx;
  margin-bottom: 10rpx;
  border-radius: 10rpx;
  background-color: #ffffff;
}

.review__stars {
  display: flex;
  align-items: center;
  gap: 2rpx;
}

.review__star {
  font-size: 24rpx;
  color: #d8dee7;
}

.review__star.is-on {
  color: #ff9a3c;
}

.review__score {
  margin-left: 8rpx;
  font-size: 21rpx;
  color: #7a8699;
}

.review__content {
  display: block;
  margin-top: 10rpx;
  font-size: 25rpx;
  line-height: 1.6;
  color: #1b2432;
  word-break: break-all;
}

.review__image-count {
  margin-top: 8rpx;
  font-size: 20rpx;
  color: #2c4a75;
}

.review__tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8rpx;
  margin-top: 8rpx;
}

.review__append {
  margin-top: 10rpx;
  padding: 8rpx 12rpx;
  border-radius: 8rpx;
  background-color: #f7f9fc;
  font-size: 21rpx;
  color: #55637a;
  line-height: 1.6;
}

.review__reply {
  margin-top: 10rpx;
  padding: 10rpx 12rpx;
  border-radius: 8rpx;
  background-color: #f0f7f3;
}

.review__reply-label {
  display: block;
  font-size: 19rpx;
  color: #067647;
  font-weight: 600;
}

.review__reply-text {
  display: block;
  margin-top: 4rpx;
  font-size: 22rpx;
  color: #33455f;
  line-height: 1.6;
  word-break: break-all;
}

.review__foot {
  margin-top: 12rpx;
  padding-top: 10rpx;
  border-top: 1rpx solid #f4f6f9;
}

.review__foot-meta {
  display: flex;
  gap: 16rpx;
}

.review__action {
  padding: 4rpx 22rpx;
  border-radius: 8rpx;
  background-color: #fff1e6;
  color: #c2410c;
  font-size: 22rpx;
  font-weight: 600;
}

.review__more,
.review__end {
  padding: 24rpx 0;
  text-align: center;
  font-size: 22rpx;
  color: #8d99aa;
}

.review__more {
  color: #e85d04;
  font-weight: 600;
}

.review__close {
  font-size: 23rpx;
  color: #7a8699;
}

.review__origin {
  margin-top: 12rpx;
  padding: 10rpx 12rpx;
  border-radius: 8rpx;
  background-color: #f7f9fc;
  font-size: 22rpx;
  color: #55637a;
  line-height: 1.6;
  max-height: 160rpx;
  overflow: hidden;
}

.review__textarea {
  width: 100%;
  height: 200rpx;
  margin-top: 12rpx;
  padding: 14rpx;
  border-radius: 10rpx;
  background-color: #f7f9fc;
  font-size: 24rpx;
  line-height: 1.6;
  color: #1b2432;
}

.review__counter {
  margin-top: 8rpx;
}
</style>
