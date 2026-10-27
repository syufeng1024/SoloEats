<template>
  <view class="rd">
    <view class="rd__notice">
      评价详情接口后端尚未提供，本页展示「我的评价」返回的评价记录
    </view>

    <view v-if="loading" class="se-loading">正在加载评价…</view>

    <view v-else-if="error" class="se-error">
      <text>{{ error }}</text>
      <view class="se-error__retry" @click="retry">重新加载</view>
      <view class="se-error__retry rd__back-list" @click="goList">返回我的评价</view>
    </view>

    <template v-else-if="review">
      <view class="se-card rd__block rd__head">
        <view class="rd__score-row">
          <text class="rd__score">{{ review.overallScore }}</text>
          <text class="rd__score-unit">分</text>
          <view class="rd__stars">
            <text
              v-for="star in 5"
              :key="star"
              class="rd__star"
              :class="{ 'is-active': star <= review.overallScore }"
            >
              ★
            </text>
          </view>
        </view>
        <view class="rd__tags">
          <text v-if="review.anonymous === 1" class="se-tag">匿名评价</text>
          <text v-if="reviewStatusLabel(review.status)" class="se-tag">{{ reviewStatusLabel(review.status) }}</text>
          <text v-for="tag in review.tags || []" :key="tag" class="se-tag">{{ tag }}</text>
        </view>
        <view class="rd__meta">
          <text class="se-muted">订单 #{{ review.orderId }}</text>
          <text class="se-muted">{{ formatDateTime(review.createdAt) }}</text>
        </view>
      </view>

      <view
        v-if="subScores.length > 0"
        class="se-card rd__block"
      >
        <view class="rd__block-title">分项评分</view>
        <view v-for="item in subScores" :key="item.label" class="rd__sub-row">
          <text class="rd__sub-label">{{ item.label }}</text>
          <view class="rd__sub-dots">
            <view
              v-for="dot in 5"
              :key="dot"
              class="rd__dot"
              :class="{ 'is-active': dot <= item.value }"
            />
          </view>
          <text class="rd__sub-value">{{ item.value }} 分</text>
        </view>
      </view>

      <view class="se-card rd__block">
        <view class="rd__block-title">评价内容</view>
        <text v-if="review.content" class="rd__content">{{ review.content }}</text>
        <text v-else class="rd__content rd__content--empty">未填写文字评价</text>

        <view v-if="images.length > 0" class="rd__images">
          <image
            v-for="(image, index) in images"
            :key="`${image}-${index}`"
            :src="image"
            mode="aspectFill"
            class="rd__image"
            @click="previewImage(index)"
          />
        </view>
      </view>

      <view v-if="review.merchantReply" class="se-card rd__block rd__reply">
        <view class="se-between rd__block-title">
          <text>商家回复</text>
          <text class="se-muted">{{ formatDateTime(review.replyAt) }}</text>
        </view>
        <text class="rd__reply-text">{{ review.merchantReply }}</text>
      </view>

      <view v-if="review.appendContent" class="se-card rd__block rd__append">
        <view class="se-between rd__block-title">
          <text>我的追评</text>
          <text class="se-muted">{{ formatDateTime(review.appendAt) }}</text>
        </view>
        <text class="rd__append-text">{{ review.appendContent }}</text>
      </view>

      <view v-else class="rd__append-bar">
        <text v-if="appendHint" class="rd__append-hint">{{ appendHint }}</text>
        <button
          class="se-btn se-btn-primary rd__append-btn"
          :disabled="!canAppend"
          @click="openAppend"
        >
          追加评价
        </button>
      </view>
    </template>

    <view v-if="appendOpen" class="rd__mask" @click="closeAppend">
      <view class="rd__dialog" @click.stop>
        <text class="rd__dialog-title">追加评价</text>
        <textarea
          v-model="appendText"
          class="rd__textarea"
          maxlength="500"
          placeholder="补充说明口味、配送或售后处理情况"
          placeholder-class="rd__placeholder"
        />
        <view class="se-between rd__counter">
          <text class="se-muted">追评提交后不可修改</text>
          <text class="se-muted">{{ appendText.length }}/500</text>
        </view>
        <view class="rd__dialog-actions">
          <view class="rd__dialog-btn" @click="closeAppend">取消</view>
          <view
            class="rd__dialog-btn rd__dialog-btn--primary"
            :class="{ 'is-disabled': appendSubmitting }"
            @click="submitAppend"
          >
            {{ appendSubmitting ? '提交中…' : '提交追评' }}
          </view>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { appendReview, fetchMyReviews } from '@/api/review'
import type { ReviewVO } from '@/api/types'
import { formatDateTime } from '@/utils/date'
import { cacheReview, readCachedReview } from '@/utils/reviewCache'
import { useAuthStore } from '@/stores/auth'

/**
 * 评价详情。
 *
 * ReviewController 没有 GET /c/reviews/{id}，因此：
 * 1. 优先读取列表页写入的本地快照；
 * 2. 快照缺失（如直接刷新 H5）时，最多翻 5 页「我的评价」按 id 定位；
 * 3. 两处都拿不到才提示返回列表。
 * 追评成功后用 POST /c/reviews/{id}/append 的返回值覆盖快照。
 */

const SEARCH_PAGE_SIZE = 20
const SEARCH_MAX_PAGES = 5

const auth = useAuthStore()

const reviewId = ref(0)
const review = ref<ReviewVO | null>(null)
const loading = ref(true)
const error = ref('')

const appendOpen = ref(false)
const appendText = ref('')
const appendSubmitting = ref(false)

const images = computed<string[]>(() =>
  Array.isArray(review.value?.images) ? (review.value?.images as string[]) : [],
)

const subScores = computed(() => {
  const current = review.value
  if (!current) {
    return []
  }
  const result: Array<{ label: string; value: number }> = []
  if (current.tasteScore && current.tasteScore > 0) {
    result.push({ label: '口味', value: current.tasteScore })
  }
  if (current.packageScore && current.packageScore > 0) {
    result.push({ label: '包装', value: current.packageScore })
  }
  if (current.deliveryScore && current.deliveryScore > 0) {
    result.push({ label: '配送', value: current.deliveryScore })
  }
  return result
})

const canAppend = computed(() => {
  const current = review.value
  return Boolean(current && current.status === 1 && !current.appendContent)
})

const appendHint = computed(() => {
  const current = review.value
  if (!current) {
    return ''
  }
  if (current.status === 0) {
    return '评价审核通过后才能追评'
  }
  if (current.status === 2) {
    return '该评价已被隐藏，不能追评'
  }
  return ''
})

onLoad((options) => {
  const id = Number(options?.id)
  if (!Number.isFinite(id) || id <= 0) {
    loading.value = false
    error.value = '缺少评价参数'
    return
  }
  reviewId.value = id
  if (!auth.ensureLogin(`/pages/review/Detail?id=${id}`)) {
    return
  }
  load()
})

async function load(): Promise<void> {
  loading.value = true
  error.value = ''
  const cached = readCachedReview(reviewId.value)
  if (cached) {
    review.value = cached
    loading.value = false
    return
  }
  try {
    const found = await findInMyReviews(reviewId.value)
    if (!found) {
      error.value = '评价不存在，或已超出「我的评价」列表范围'
      return
    }
    review.value = found
    cacheReview(found)
  } catch (err) {
    error.value = (err as Error)?.message || '评价加载失败'
  } finally {
    loading.value = false
  }
}

async function findInMyReviews(id: number): Promise<ReviewVO | null> {
  let page = 1
  while (page <= SEARCH_MAX_PAGES) {
    const result = await fetchMyReviews(page, SEARCH_PAGE_SIZE, true)
    const found = result.list.find((item) => item.id === id)
    if (found) {
      return found
    }
    if (result.list.length < SEARCH_PAGE_SIZE || page * SEARCH_PAGE_SIZE >= result.total) {
      return null
    }
    page += 1
  }
  return null
}

function retry(): void {
  review.value = null
  load()
}

function previewImage(index: number): void {
  if (images.value.length === 0) {
    return
  }
  uni.previewImage({
    urls: images.value,
    current: images.value[index],
  })
}

function reviewStatusLabel(status: number | null): string {
  if (status === 0) {
    return '审核中'
  }
  if (status === 1) {
    return '已通过审核'
  }
  if (status === 2) {
    return '已隐藏'
  }
  return ''
}

function openAppend(): void {
  if (!canAppend.value) {
    return
  }
  appendText.value = ''
  appendOpen.value = true
}

function closeAppend(): void {
  if (appendSubmitting.value) {
    return
  }
  appendOpen.value = false
}

async function submitAppend(): Promise<void> {
  if (appendSubmitting.value || !review.value) {
    return
  }
  const content = appendText.value.trim()
  if (!content) {
    uni.showToast({ title: '请输入追评内容', icon: 'none' })
    return
  }
  if (content.length > 500) {
    uni.showToast({ title: '追评最多 500 字', icon: 'none' })
    return
  }
  appendSubmitting.value = true
  try {
    const updated = await appendReview(review.value.id, content)
    review.value = updated
    cacheReview(updated)
    appendOpen.value = false
    uni.showToast({ title: '追评已提交', icon: 'none' })
  } catch (err) {
    // http 层已 toast（如 40106 评价未通过审核 / 已追评）
  } finally {
    appendSubmitting.value = false
  }
}

function goList(): void {
  uni.redirectTo({ url: '/pages/review/List' })
}
</script>

<style scoped>
.rd {
  min-height: 100vh;
  padding: 20rpx 24rpx 60rpx;
  background-color: #f5f5f7;
}

.rd__notice {
  margin-bottom: 16rpx;
  padding: 14rpx 18rpx;
  border-radius: 8rpx;
  background-color: #fff8ef;
  color: #a26a2a;
  font-size: 22rpx;
  line-height: 1.6;
}

.rd__back-list {
  margin-left: 16rpx;
}

.rd__block {
  padding: 24rpx;
  margin-bottom: 16rpx;
}

.rd__head {
  background: linear-gradient(135deg, #ffffff 0%, #fff8f1 100%);
}

.rd__score-row {
  display: flex;
  align-items: baseline;
  gap: 8rpx;
}

.rd__score {
  font-size: 64rpx;
  font-weight: 600;
  color: #ff7a00;
  line-height: 1;
}

.rd__score-unit {
  font-size: 24rpx;
  color: #ff7a00;
  margin-right: 12rpx;
}

.rd__stars {
  display: flex;
  gap: 6rpx;
}

.rd__star {
  font-size: 32rpx;
  color: #e0e0e0;
  line-height: 1;
}

.rd__star.is-active {
  color: #ff7a00;
}

.rd__tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8rpx;
  margin-top: 18rpx;
}

.rd__meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 16rpx;
  padding-top: 16rpx;
  border-top: 1rpx solid #f5f5f5;
}

.rd__block-title {
  font-size: 28rpx;
  font-weight: 600;
  color: #1f1f1f;
  margin-bottom: 16rpx;
}

.rd__sub-row {
  display: flex;
  align-items: center;
  gap: 16rpx;
  padding: 8rpx 0;
}

.rd__sub-label {
  width: 100rpx;
  font-size: 25rpx;
  color: #666666;
}

.rd__sub-dots {
  display: flex;
  gap: 8rpx;
}

.rd__dot {
  width: 20rpx;
  height: 20rpx;
  border-radius: 50%;
  background-color: #eeeeee;
}

.rd__dot.is-active {
  background-color: #ff7a00;
}

.rd__sub-value {
  font-size: 24rpx;
  color: #8a8a8a;
}

.rd__content {
  font-size: 27rpx;
  line-height: 1.8;
  color: #333333;
  word-break: break-all;
}

.rd__content--empty {
  color: #b8b8b8;
}

.rd__images {
  display: flex;
  flex-wrap: wrap;
  gap: 12rpx;
  margin-top: 16rpx;
}

.rd__image {
  width: 200rpx;
  height: 200rpx;
  border-radius: 8rpx;
  background-color: #f5f5f5;
}

.rd__reply {
  border-left: 6rpx solid #1f6feb;
}

.rd__reply-text {
  font-size: 26rpx;
  line-height: 1.8;
  color: #333333;
}

.rd__append {
  border-left: 6rpx solid #52c41a;
}

.rd__append-text {
  font-size: 26rpx;
  line-height: 1.8;
  color: #333333;
}

.rd__append-bar {
  display: flex;
  flex-direction: column;
  gap: 12rpx;
  padding: 8rpx 0 24rpx;
}

.rd__append-hint {
  text-align: center;
  font-size: 22rpx;
  color: #a26a2a;
}

.rd__append-btn {
  width: 100%;
}

.rd__mask {
  position: fixed;
  inset: 0;
  z-index: 50;
  background-color: rgba(0, 0, 0, 0.45);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 40rpx;
}

.rd__dialog {
  width: 100%;
  padding: 28rpx 24rpx 20rpx;
  border-radius: 12rpx;
  background-color: #ffffff;
}

.rd__dialog-title {
  display: block;
  font-size: 30rpx;
  font-weight: 600;
  margin-bottom: 16rpx;
}

.rd__textarea {
  width: 100%;
  height: 200rpx;
  padding: 16rpx;
  border-radius: 8rpx;
  background-color: #fafafa;
  font-size: 26rpx;
  line-height: 1.6;
}

.rd__placeholder {
  color: #b8b8b8;
}

.rd__counter {
  padding: 10rpx 0 16rpx;
}

.rd__dialog-actions {
  display: flex;
  gap: 16rpx;
}

.rd__dialog-btn {
  flex: 1;
  height: 76rpx;
  line-height: 76rpx;
  text-align: center;
  border-radius: 8rpx;
  font-size: 27rpx;
  color: #555555;
  background-color: #f2f2f4;
}

.rd__dialog-btn--primary {
  color: #ffffff;
  background: linear-gradient(135deg, #ffa24c, #ff7a00);
}

.rd__dialog-btn.is-disabled {
  opacity: 0.5;
}
</style>
