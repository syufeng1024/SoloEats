<template>
  <view class="promotion">
    <view class="promotion__banner">
      <text class="promotion__banner-title">满减与折扣</text>
      <text class="promotion__banner-desc">
        {{ shopId > 0 ? `当前按门店 ${shopId} 展示可参与的活动` : '未指定门店，展示全平台活动' }}
      </text>
    </view>

    <view v-if="loading && promotions.length === 0" class="se-loading">正在加载活动…</view>

    <view v-else-if="error && promotions.length === 0" class="se-error">
      <text>{{ error }}</text>
      <text class="promotion__error-hint">若从店铺页进入，请携带 shopId 重试；也可以返回店铺重新进入。</text>
      <view class="se-error__retry" @click="load(false)">重新加载</view>
    </view>

    <view v-else-if="promotions.length === 0" class="se-empty">
      <text class="se-empty__title">暂无可用活动</text>
      <text>可从店铺页进入，查看该店铺参与的满减与折扣活动</text>
    </view>

    <template v-else>
      <view v-if="error" class="promotion__error">
        <text>{{ error }}</text>
        <text class="promotion__error-retry" @click="load(false)">重试</text>
      </view>

      <view v-for="item in promotions" :key="item.id" class="se-card promotion__card">
        <view class="se-between promotion__head">
          <text class="promotion__name">{{ item.promotionName }}</text>
          <text class="promotion__type">{{ typeLabel(item.promotionType) }}</text>
        </view>

        <view class="promotion__meta">
          <text class="se-tag">{{ bearerLabel(item.bearer) }}</text>
          <text class="promotion__validity">
            {{ formatDateTime(item.startAt) }} ~ {{ formatDateTime(item.endAt) }}
          </text>
        </view>

        <view class="promotion__rules">
          <view
            v-for="(rule, index) in item.rules"
            :key="`${item.id}-${index}`"
            class="promotion__rule"
          >
            <text class="promotion__rule-index">第 {{ index + 1 }} 档</text>
            <text class="promotion__rule-text">{{ ruleText(item.promotionType, rule) }}</text>
          </view>
          <text v-if="item.rules.length === 0" class="promotion__rule-empty">
            该活动未配置阶梯档位，以结算试算结果为准
          </text>
        </view>

        <text class="promotion__scope">适用范围：按活动配置的门店与商品生效，最终以结算试算为准。</text>
      </view>
    </template>
  </view>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { onLoad, onPullDownRefresh } from '@dcloudio/uni-app'
import { fetchPromotions } from '@/api/marketing'
import type { PromotionAvailableVO, PromotionRuleVO } from '@/api/marketing'
import { fenToYuan } from '@/utils/money'
import { formatDateTime } from '@/utils/date'

/**
 * 满减活动列表。
 *
 * /c/promotions 只返回进行中且命中 shopId/spuIds 范围的活动，响应里不含
 * scopeType/scopeJson，所以页面不展示具体门店或商品范围，只提示「以结算试算为准」。
 * shopId 来自店铺页跳转参数；没有 shopId 时后端只按全平台范围过滤。
 */

const shopId = ref(0)
const promotions = ref<PromotionAvailableVO[]>([])
const loading = ref(false)
const error = ref('')

let sequence = 0

onLoad((options) => {
  const parsed = Number(options?.shopId)
  shopId.value = Number.isFinite(parsed) && parsed > 0 ? parsed : 0
  load(false)
})

onPullDownRefresh(() => {
  if (loading.value) {
    uni.stopPullDownRefresh()
    return
  }
  load(false)
})

async function load(silent: boolean): Promise<void> {
  const current = ++sequence
  loading.value = true
  if (!silent) {
    error.value = ''
  }
  try {
    const list = await fetchPromotions(
      { shopId: shopId.value > 0 ? shopId.value : undefined },
      silent,
    )
    if (current !== sequence) {
      return
    }
    promotions.value = list
    error.value = ''
  } catch (err) {
    if (current === sequence) {
      // 保留已有活动列表，只提示错误
      error.value = (err as Error)?.message || '活动加载失败'
      if (!silent) {
        uni.showToast({ title: error.value, icon: 'none' })
      }
    }
  } finally {
    if (current === sequence) {
      loading.value = false
    }
    uni.stopPullDownRefresh()
  }
}

function typeLabel(type: number): string {
  if (type === 1) {
    return '满减'
  }
  if (type === 2) {
    return '折扣'
  }
  if (type === 3) {
    return '新人立减'
  }
  return '优惠活动'
}

function bearerLabel(bearer: number | null): string {
  if (bearer === 1) {
    return '平台活动'
  }
  if (bearer === 2) {
    return '商家活动'
  }
  return '优惠活动'
}

function ruleText(type: number, rule: PromotionRuleVO): string {
  const threshold = `满 ¥${fenToYuan(rule.thresholdAmount)}`
  if (type === 2) {
    const discount = rule.discountRate != null ? discountText(rule.discountRate) : '享受折扣'
    const cap =
      rule.maxDiscount != null && rule.maxDiscount > 0
        ? `，最高减 ¥${fenToYuan(rule.maxDiscount)}`
        : ''
    return `${threshold} 享 ${discount}${cap}`
  }
  if (rule.discountAmount != null && rule.discountAmount > 0) {
    return type === 3
      ? `立减 ¥${fenToYuan(rule.discountAmount)}`
      : `${threshold} 减 ¥${fenToYuan(rule.discountAmount)}`
  }
  return `${threshold} 起享优惠`
}

/** 0.85 → 8.5 折；用整数换算避免 0.85 * 10 的浮点尾数。 */
function discountText(rate: number): string {
  const zhe = Math.round(rate * 1000) / 100
  return `${zhe} 折`
}
</script>

<style scoped>
.promotion {
  min-height: 100vh;
  padding: 24rpx;
  background-color: #f5f5f7;
}

.promotion__banner {
  padding: 28rpx 32rpx;
  border-radius: 12rpx;
  background: linear-gradient(120deg, #ff8a1f 0%, #ff5c00 100%);
  color: #ffffff;
  box-shadow: 0 8rpx 20rpx rgba(255, 92, 0, 0.22);
}

.promotion__banner-title {
  display: block;
  font-size: 36rpx;
  font-weight: 600;
}

.promotion__banner-desc {
  display: block;
  margin-top: 10rpx;
  font-size: 22rpx;
  opacity: 0.92;
}

.promotion__error-hint {
  display: block;
  margin-top: 12rpx;
  color: #a26a2a;
  font-size: 22rpx;
  line-height: 1.6;
}

.promotion__error {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 16rpx;
  padding: 14rpx 20rpx;
  border-radius: 8rpx;
  background-color: #fff8ef;
  color: #a26a2a;
  font-size: 22rpx;
}

.promotion__error-retry {
  color: #ff7a00;
  margin-left: 16rpx;
}

.promotion__card {
  margin-top: 20rpx;
  padding: 24rpx;
}

.promotion__head {
  gap: 16rpx;
}

.promotion__name {
  flex: 1;
  font-size: 30rpx;
  font-weight: 600;
  color: #1f1f1f;
}

.promotion__type {
  padding: 4rpx 14rpx;
  border-radius: 6rpx;
  background-color: #fff2e6;
  color: #ff7a00;
  font-size: 20rpx;
  flex-shrink: 0;
}

.promotion__meta {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 12rpx;
  margin-top: 12rpx;
}

.promotion__validity {
  color: #8a8a8a;
  font-size: 21rpx;
}

.promotion__rules {
  margin-top: 16rpx;
  padding-top: 12rpx;
  border-top: 1rpx solid #f5f5f5;
}

.promotion__rule {
  display: flex;
  align-items: center;
  gap: 14rpx;
  padding: 10rpx 0;
}

.promotion__rule-index {
  padding: 2rpx 10rpx;
  border-radius: 4rpx;
  background-color: #f7f7f8;
  color: #8a8a8a;
  font-size: 18rpx;
  flex-shrink: 0;
}

.promotion__rule-text {
  color: #ff4d00;
  font-size: 26rpx;
  font-weight: 500;
}

.promotion__rule-empty {
  display: block;
  padding: 12rpx 0;
  color: #a0a0a0;
  font-size: 22rpx;
}

.promotion__scope {
  display: block;
  margin-top: 12rpx;
  color: #a0a0a0;
  font-size: 20rpx;
  line-height: 1.6;
}
</style>
