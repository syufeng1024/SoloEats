<template>
  <view class="se-page review">
    <view v-if="error" class="se-error">
      <text>{{ error }}</text>
      <view class="se-error__retry" @click="goDetail">返回订单详情</view>
    </view>

    <view v-else-if="!orderItemId" class="se-card review__blocked">
      <text class="review__blocked-title">暂无法提交评价</text>
      <text class="review__blocked-text">
        评价接口要求 orderItemId（订单明细主键），但当前 GET /api/v1/orders/{id} 返回的
        UserOrderItemView 只有 skuId，没有明细 ID，前端无法凭空推导。
      </text>
      <text class="review__blocked-text">
        需要后端在 UserOrderItemView 增加 orderItemId 字段后，本页无需改动即可提交。
      </text>
      <button class="se-btn se-btn-ghost review__blocked-btn" @click="goDetail">返回订单详情</button>
    </view>

    <view v-else-if="success" class="se-card review__success">
      <view class="review__success-mark">✓</view>
      <text class="review__success-title">评价已提交</text>
      <text class="se-muted">感谢你的反馈，评价会展示在门店页面</text>
      <button class="se-btn se-btn-primary review__success-btn" @click="goDetail">返回订单详情</button>
      <button class="se-btn se-btn-plain review__success-btn" @click="goHome">继续点餐</button>
    </view>

    <template v-else>
      <view class="se-card review__block">
        <view class="review__score-row">
          <text class="review__score-label">总体评分</text>
          <view class="review__stars">
            <text
              v-for="star in 5"
              :key="star"
              class="review__star"
              :class="{ 'is-active': star <= overallScore }"
              @click="overallScore = star"
            >
              ★
            </text>
          </view>
          <text class="se-muted">{{ overallScore }} 分</text>
        </view>
      </view>

      <view class="se-card review__block">
        <view class="review__sub-row">
          <text class="review__score-label">口味</text>
          <view class="review__stars review__stars--small">
            <text
              v-for="star in 5"
              :key="star"
              class="review__star"
              :class="{ 'is-active': star <= tasteScore }"
              @click="tasteScore = tasteScore === star ? 0 : star"
            >
              ★
            </text>
          </view>
        </view>
        <view class="review__sub-row">
          <text class="review__score-label">包装</text>
          <view class="review__stars review__stars--small">
            <text
              v-for="star in 5"
              :key="star"
              class="review__star"
              :class="{ 'is-active': star <= packageScore }"
              @click="packageScore = packageScore === star ? 0 : star"
            >
              ★
            </text>
          </view>
        </view>
        <view class="review__sub-row">
          <text class="review__score-label">配送</text>
          <view class="review__stars review__stars--small">
            <text
              v-for="star in 5"
              :key="star"
              class="review__star"
              :class="{ 'is-active': star <= deliveryScore }"
              @click="deliveryScore = deliveryScore === star ? 0 : star"
            >
              ★
            </text>
          </view>
        </view>
      </view>

      <view v-if="tags.length > 0" class="se-card review__block">
        <view class="review__block-title">评价标签</view>
        <view class="review__tags">
          <view
            v-for="tag in tags"
            :key="tag.id"
            class="review__tag"
            :class="{ 'is-active': selectedTags.includes(tag.tagName) }"
            @click="toggleTag(tag.tagName)"
          >
            {{ tag.tagName }}
          </view>
        </view>
      </view>

      <view class="se-card review__block">
        <view class="se-between review__block-title">
          <text>写点评价</text>
          <text class="se-muted">{{ content.length }}/500</text>
        </view>
        <textarea
          v-model="content"
          class="review__textarea"
          maxlength="500"
          placeholder="说说口味、包装和配送体验（选填）"
          placeholder-class="review__placeholder"
        />
      </view>

      <view class="se-card review__block se-between">
        <text class="review__score-label">匿名评价</text>
        <switch :checked="anonymous" color="#FF7A00" @change="onAnonymousChange" />
      </view>

      <button
        class="se-btn se-btn-primary review__submit"
        :disabled="submitting || cooldown > 0"
        @click="submit"
      >
        {{ submitting ? '提交中…' : cooldown > 0 ? `${cooldown} 秒后可再次提交` : '提交评价' }}
      </button>
    </template>
  </view>
</template>

<script setup lang="ts">
import { onUnmounted, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { fetchReviewTags, submitReview } from '@/api/order'
import type { ReviewTagVO } from '@/api/types'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()

const orderId = ref(0)
const shopId = ref(0)
const orderItemId = ref(0)

const overallScore = ref(5)
const tasteScore = ref(0)
const packageScore = ref(0)
const deliveryScore = ref(0)
const content = ref('')
const anonymous = ref(false)
const selectedTags = ref<string[]>([])
const tags = ref<ReviewTagVO[]>([])

const submitting = ref(false)
const cooldown = ref(0)
const success = ref(false)
const error = ref('')

let cooldownTimer: ReturnType<typeof setInterval> | null = null

onLoad((options) => {
  orderId.value = Number(options?.orderId) || 0
  shopId.value = Number(options?.shopId) || 0
  orderItemId.value = Number(options?.orderItemId) || 0

  if (!orderId.value) {
    error.value = '缺少订单参数'
    return
  }
  if (!auth.ensureLogin(`/pages/review/ReviewForm?orderId=${orderId.value}&shopId=${shopId.value}`)) {
    return
  }
  loadTags()
})

onUnmounted(() => {
  stopCooldown()
})

async function loadTags(): Promise<void> {
  try {
    tags.value = await fetchReviewTags()
  } catch (err) {
    // 标签是增强项，接口失败不阻塞评价提交
    tags.value = []
  }
}

function toggleTag(tagName: string): void {
  const index = selectedTags.value.indexOf(tagName)
  if (index >= 0) {
    selectedTags.value.splice(index, 1)
  } else {
    selectedTags.value.push(tagName)
  }
}

function onAnonymousChange(event: { detail: { value: boolean } }): void {
  anonymous.value = event.detail.value
}

function startCooldown(seconds: number): void {
  stopCooldown()
  cooldown.value = seconds
  cooldownTimer = setInterval(() => {
    cooldown.value -= 1
    if (cooldown.value <= 0) {
      stopCooldown()
    }
  }, 1000)
}

function stopCooldown(): void {
  if (cooldownTimer) {
    clearInterval(cooldownTimer)
    cooldownTimer = null
  }
  cooldown.value = 0
}

async function submit(): Promise<void> {
  if (submitting.value || cooldown.value > 0) {
    return
  }
  if (!orderItemId.value) {
    uni.showToast({ title: '缺少订单明细 ID，无法评价', icon: 'none' })
    return
  }
  if (overallScore.value < 1) {
    uni.showToast({ title: '请选择总体评分', icon: 'none' })
    return
  }

  submitting.value = true
  // 点击即进入 3 秒冷却，避免网络慢时用户连点造成重复提交
  startCooldown(3)
  try {
    await submitReview({
      orderId: orderId.value,
      orderItemId: orderItemId.value,
      overallScore: overallScore.value,
      tasteScore: tasteScore.value > 0 ? tasteScore.value : null,
      packageScore: packageScore.value > 0 ? packageScore.value : null,
      deliveryScore: deliveryScore.value > 0 ? deliveryScore.value : null,
      content: content.value.trim() || null,
      tags: selectedTags.value.length > 0 ? selectedTags.value : null,
      anonymous: anonymous.value ? 1 : 0,
    })
    success.value = true
  } catch (err) {
    // http 层已提示；保留冷却，用户 3 秒后可重试
  } finally {
    submitting.value = false
  }
}

function goDetail(): void {
  if (!orderId.value) {
    uni.switchTab({ url: '/pages/order/List' })
    return
  }
  uni.redirectTo({ url: `/pages/order/Detail?id=${orderId.value}` })
}

function goHome(): void {
  uni.switchTab({ url: '/pages/index/Index' })
}
</script>

<style scoped>
.review__block {
  padding: 24rpx;
  margin-bottom: 16rpx;
}

.review__block-title {
  font-size: 28rpx;
  font-weight: 600;
  margin-bottom: 12rpx;
}

.review__score-row {
  display: flex;
  align-items: center;
  gap: 16rpx;
}

.review__sub-row {
  display: flex;
  align-items: center;
  gap: 16rpx;
  padding: 8rpx 0;
}

.review__score-label {
  font-size: 26rpx;
  color: #444444;
  min-width: 120rpx;
}

.review__stars {
  display: flex;
  gap: 8rpx;
}

.review__star {
  font-size: 48rpx;
  line-height: 1;
  color: #e0e0e0;
}

.review__stars--small .review__star {
  font-size: 38rpx;
}

.review__star.is-active {
  color: #ff7a00;
}

.review__tags {
  display: flex;
  flex-wrap: wrap;
  gap: 16rpx;
}

.review__tag {
  padding: 10rpx 24rpx;
  border-radius: 8rpx;
  border: 1rpx solid #eeeeee;
  background-color: #fafafa;
  font-size: 24rpx;
  color: #555555;
}

.review__tag.is-active {
  border-color: #ff7a00;
  background-color: #fff2e6;
  color: #ff7a00;
}

.review__textarea {
  width: 100%;
  height: 200rpx;
  padding: 16rpx;
  border-radius: 8rpx;
  background-color: #fafafa;
  font-size: 26rpx;
  line-height: 1.6;
}

.review__placeholder {
  color: #b8b8b8;
}

.review__submit {
  margin-top: 8rpx;
}

.review__success,
.review__blocked {
  padding: 48rpx 32rpx;
  display: flex;
  flex-direction: column;
  align-items: center;
}

.review__success-mark {
  width: 112rpx;
  height: 112rpx;
  line-height: 112rpx;
  text-align: center;
  border-radius: 50%;
  background: linear-gradient(135deg, #52c41a, #389e0d);
  color: #ffffff;
  font-size: 56rpx;
  margin-bottom: 20rpx;
}

.review__success-title {
  font-size: 32rpx;
  font-weight: 600;
  margin-bottom: 8rpx;
}

.review__success-btn {
  width: 100%;
  margin-top: 16rpx;
}

.review__blocked-title {
  font-size: 32rpx;
  font-weight: 600;
  color: #d4380d;
  margin-bottom: 16rpx;
}

.review__blocked-text {
  font-size: 24rpx;
  line-height: 1.7;
  color: #666666;
  margin-bottom: 12rpx;
}

.review__blocked-btn {
  width: 100%;
  margin-top: 16rpx;
}
</style>
