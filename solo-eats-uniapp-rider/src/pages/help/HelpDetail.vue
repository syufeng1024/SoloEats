<template>
  <view class="hdetail rd-page">
    <view v-if="loading && !faq" class="rd-loading">正在加载问题详情…</view>

    <view v-else-if="error && !faq" class="rd-error">
      <text class="rd-error__msg">{{ error }}</text>
      <view class="rd-error__retry" @click="load">重新加载</view>
    </view>

    <view v-else-if="faq">
      <view class="hdetail__card">
        <text class="hdetail__category">{{ faq.category || '其他' }}</text>
        <text class="hdetail__question">{{ faq.question }}</text>
        <view class="rd-divider hdetail__divider" />
        <text class="hdetail__answer">{{ faq.answer }}</text>
      </view>

      <view v-if="fromSnapshot" class="hdetail__snapshot">
        内容来自列表快照；如需最新版本，请返回帮助中心下拉刷新。
      </view>

      <view class="hdetail__actions">
        <button class="rd-btn rd-btn--plain" @click="goHelp">返回帮助中心</button>
        <button class="rd-btn rd-btn--ghost" @click="goContact">没解决，联系客服</button>
      </view>
    </view>

    <view v-else class="rd-empty">
      <text class="rd-empty__title">问题不存在</text>
      <text>该 FAQ 可能已下架，请返回帮助中心重新选择</text>
      <view class="rd-empty__action" @click="goHelp">返回帮助中心</view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { fetchFaqs } from '@/api/rider'
import type { FaqVO } from '@/api/types'

/**
 * FAQ 详情。
 * 后端没有单条 FAQ 详情接口，优先读取帮助中心列表写入的本地快照；
 * 快照缺失（例如冷启动直达本页）时回查 GET /c/faqs 再按 id 匹配。
 */

const HELP_SELECTED_KEY = 'rider:help:selected'

const faq = ref<FaqVO | null>(null)
const loading = ref(false)
const error = ref('')
const fromSnapshot = ref(false)

let faqId = 0

onLoad((options) => {
  const parsed = Number(options?.id)
  faqId = Number.isFinite(parsed) && parsed > 0 ? Math.trunc(parsed) : 0

  if (readSnapshot()) {
    return
  }
  if (!faqId) {
    error.value = '缺少 FAQ 参数，请从帮助中心进入'
    return
  }
  void load()
})

function readSnapshot(): boolean {
  try {
    const cached = uni.getStorageSync(HELP_SELECTED_KEY) as FaqVO | ''
    if (!cached || typeof cached !== 'object') {
      return false
    }
    if (faqId && Number(cached.id) !== faqId) {
      return false
    }
    if (!cached.question) {
      return false
    }
    faq.value = cached
    fromSnapshot.value = true
    return true
  } catch (err) {
    return false
  }
}

async function load(): Promise<void> {
  if (!faqId) {
    return
  }
  loading.value = true
  error.value = ''
  try {
    const list = await fetchFaqs()
    const matched = (Array.isArray(list) ? list : []).find(
      (item) => Number(item.id) === faqId,
    )
    if (!matched) {
      faq.value = null
      error.value = ''
      return
    }
    faq.value = matched
    fromSnapshot.value = false
  } catch (err) {
    error.value = (err as Error)?.message || '问题详情加载失败'
  } finally {
    loading.value = false
  }
}

function goHelp(): void {
  uni.navigateBack({
    fail: () => {
      uni.redirectTo({ url: '/pages/help/Help' })
    },
  })
}

function goContact(): void {
  uni.navigateTo({ url: '/pages/help/Contact' })
}
</script>

<style scoped>
.hdetail {
  padding-bottom: 60rpx;
}

.hdetail__card {
  padding: 32rpx;
  border-radius: 24rpx;
  background-color: #ffffff;
  box-shadow: 0 4rpx 16rpx rgba(20, 35, 58, 0.06);
}

.hdetail__category {
  display: inline-block;
  padding: 4rpx 14rpx;
  border-radius: 8rpx;
  background-color: #fff1e6;
  color: #c2410c;
  font-size: 21rpx;
}

.hdetail__question {
  display: block;
  margin-top: 18rpx;
  font-size: 34rpx;
  font-weight: 700;
  color: #16233a;
  line-height: 1.5;
}

.hdetail__divider {
  margin: 24rpx 0;
}

.hdetail__answer {
  display: block;
  font-size: 26rpx;
  color: #33455f;
  line-height: 1.8;
  white-space: pre-wrap;
  word-break: break-word;
}

.hdetail__snapshot {
  margin-top: 18rpx;
  padding: 14rpx 20rpx;
  border-radius: 12rpx;
  background-color: #f6f8fb;
  font-size: 21rpx;
  color: #8d99aa;
}

.hdetail__actions {
  display: flex;
  flex-direction: column;
  gap: 16rpx;
  margin-top: 28rpx;
}
</style>
