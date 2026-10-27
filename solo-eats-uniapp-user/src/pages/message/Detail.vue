<template>
  <view class="detail">
    <view v-if="loading" class="se-loading">正在加载消息…</view>

    <view v-else-if="error" class="se-error detail__error">
      <text>{{ error }}</text>
      <view class="se-error__retry" @click="goBack">返回消息列表</view>
    </view>

    <template v-else-if="message">
      <view class="se-card detail__card">
        <view class="detail__head">
          <text class="se-tag" :class="typeClass(message.msgType)">{{ typeLabel(message.msgType) }}</text>
          <text class="detail__time">{{ formatDateTime(message.createdAt) }}</text>
        </view>

        <text class="detail__title">{{ message.title }}</text>

        <view class="se-divider detail__divider" />

        <text class="detail__content">{{ message.content }}</text>
      </view>

      <view v-if="message.bizType || message.bizId" class="detail__biz">
        <text class="se-muted">关联业务：{{ message.bizType || '--' }}</text>
        <text v-if="message.bizId" class="se-muted"> #{{ message.bizId }}</text>
      </view>
    </template>
  </view>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { findSiteMessageById, markSiteMessageRead } from '@/api/message'
import type { SiteMessageVO } from '@/api/message'
import { formatDateTime } from '@/utils/date'

const DETAIL_CACHE_KEY = 'message_detail_cache'

const MSG_TYPE_LABELS: Record<number, string> = {
  1: '订单',
  2: '活动',
  3: '系统',
}

const loading = ref(true)
const error = ref('')
const message = ref<SiteMessageVO | null>(null)

onLoad((query) => {
  const id = Number(query?.id)
  if (!Number.isFinite(id) || id <= 0) {
    loading.value = false
    error.value = '缺少有效的消息 ID'
    return
  }
  loadDetail(id)
})

async function loadDetail(id: number): Promise<void> {
  loading.value = true
  error.value = ''
  try {
    const cached = readCache(id)
    const target = cached || (await findSiteMessageById(id))
    if (!target) {
      error.value = '消息不存在或已过期'
      return
    }
    message.value = target
    void markRead(id)
  } catch (err) {
    error.value = (err as Error)?.message || '消息加载失败'
  } finally {
    loading.value = false
  }
}

/** 列表页跳转时会写入 { id, data } 快照；校验 id 一致才采用，避免串消息 */
function readCache(id: number): SiteMessageVO | null {
  try {
    const cached = uni.getStorageSync(DETAIL_CACHE_KEY) as
      | { id?: number; data?: SiteMessageVO }
      | ''
    if (
      cached &&
      typeof cached === 'object' &&
      Number(cached.id) === id &&
      cached.data &&
      Number(cached.data.id) === id
    ) {
      return cached.data
    }
  } catch (err) {
    // 缓存损坏按未命中处理，走接口兜底
  }
  return null
}

async function markRead(id: number): Promise<void> {
  if (message.value?.readFlag === 1) {
    return
  }
  try {
    await markSiteMessageRead(id)
    if (message.value && message.value.id === id) {
      message.value.readFlag = 1
    }
  } catch (err) {
    // 标已读失败不打断阅读，列表页下次进入会重新拉取真实状态
  }
}

function typeLabel(msgType: number): string {
  return MSG_TYPE_LABELS[msgType] || '通知'
}

function typeClass(msgType: number): string {
  if (msgType === 1) {
    return 'detail__tag--order'
  }
  if (msgType === 2) {
    return 'detail__tag--marketing'
  }
  return 'detail__tag--system'
}

function goBack(): void {
  if (getCurrentPages().length > 1) {
    uni.navigateBack()
  } else {
    uni.redirectTo({ url: '/pages/message/List' })
  }
}
</script>

<style scoped>
.detail {
  min-height: 100vh;
  padding: 24rpx;
  background-color: #f5f5f7;
}

.detail__error {
  padding-top: 160rpx;
}

.detail__card {
  padding: 28rpx 24rpx;
}

.detail__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 20rpx;
}

.detail__time {
  font-size: 22rpx;
  color: #a0a0a0;
}

.detail__title {
  display: block;
  font-size: 34rpx;
  font-weight: 600;
  line-height: 1.5;
  color: #1f1f1f;
}

.detail__divider {
  margin: 24rpx 0;
}

.detail__content {
  display: block;
  font-size: 28rpx;
  line-height: 1.8;
  color: #444444;
  white-space: pre-wrap;
  word-break: break-word;
}

.detail__biz {
  margin-top: 20rpx;
  padding: 0 8rpx;
}

.detail__tag--order {
  color: #ff7a00;
  background-color: #fff2e6;
}

.detail__tag--marketing {
  color: #d4380d;
  background-color: #fff1f0;
}

.detail__tag--system {
  color: #1677ff;
  background-color: #e6f4ff;
}
</style>
