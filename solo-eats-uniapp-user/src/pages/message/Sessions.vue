<template>
  <view class="sessions">
    <view class="sessions__body">
      <view v-if="loading && list.length === 0" class="se-loading">正在加载会话…</view>

      <view v-else-if="error && list.length === 0" class="se-error">
        <text>{{ error }}</text>
        <view class="se-error__retry" @click="load">重新加载</view>
      </view>

      <view v-else-if="list.length === 0" class="se-empty">
        <view class="sessions__empty-icon">聊</view>
        <text class="se-empty__title">暂无会话</text>
        <text>在订单或门店页发起咨询后，会话会显示在这里</text>
      </view>

      <view v-else>
        <view
          v-for="session in list"
          :key="session.id"
          class="se-card sessions__item"
          @click="openChat(session)"
        >
          <view class="sessions__avatar" :class="avatarClass(session.peerType)">
            {{ avatarText(session.peerType) }}
          </view>

          <view class="sessions__main">
            <view class="sessions__head">
              <text class="sessions__name">{{ peerName(session) }}</text>
              <text class="sessions__time">{{ formatShortDateTime(session.lastMessageAt || session.createdAt) }}</text>
            </view>

            <view class="sessions__foot">
              <text class="sessions__last">{{ session.lastMessage || '暂无消息，打个招呼吧' }}</text>
              <text v-if="session.unread > 0" class="sessions__badge">
                {{ session.unread > 99 ? '99+' : session.unread }}
              </text>
            </view>

            <view class="sessions__meta">
              <text v-if="session.orderId" class="se-tag">订单 #{{ session.orderId }}</text>
              <text class="se-muted">{{ peerLabel(session.peerType) }}</text>
            </view>
          </view>
        </view>

        <view v-if="error" class="sessions__retry" @click="load">加载失败，点击重试</view>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { onPullDownRefresh, onShow } from '@dcloudio/uni-app'
import { fetchImSessions } from '@/api/message'
import type { ImSessionVO } from '@/api/message'
import { formatShortDateTime } from '@/utils/date'

const PEER_LABELS: Record<number, string> = {
  2: '商家',
  3: '骑手',
  4: '平台客服',
}

const list = ref<ImSessionVO[]>([])
const loading = ref(false)
const error = ref('')

onShow(() => {
  load()
})

onPullDownRefresh(() => {
  if (loading.value) {
    uni.stopPullDownRefresh()
    return
  }
  load()
})

async function load(): Promise<void> {
  if (loading.value) {
    return
  }
  loading.value = true
  let loadingShown = false
  if (list.value.length === 0) {
    uni.showLoading({ title: '正在加载', mask: true })
    loadingShown = true
  }
  try {
    const result = await fetchImSessions()
    list.value = Array.isArray(result) ? result : []
    error.value = ''
  } catch (err) {
    // 保留已有会话，失败只更新错误提示
    error.value = (err as Error)?.message || '会话加载失败'
  } finally {
    loading.value = false
    if (loadingShown) {
      uni.hideLoading()
    }
    uni.stopPullDownRefresh()
  }
}

function peerLabel(peerType: number): string {
  return PEER_LABELS[peerType] || '对方'
}

/** 会话接口不下发对方昵称/头像，只能按对端类型给占位名，不伪造用户信息 */
function peerName(session: ImSessionVO): string {
  return `${peerLabel(session.peerType)} #${session.peerId}`
}

function avatarText(peerType: number): string {
  if (peerType === 2) {
    return '商'
  }
  if (peerType === 3) {
    return '骑'
  }
  if (peerType === 4) {
    return '客'
  }
  return '聊'
}

function avatarClass(peerType: number): string {
  if (peerType === 3) {
    return 'is-rider'
  }
  if (peerType === 4) {
    return 'is-service'
  }
  return 'is-merchant'
}

function openChat(session: ImSessionVO): void {
  const title = peerName(session)
  uni.navigateTo({
    url: `/pages/message/Chat?sessionId=${session.id}&title=${encodeURIComponent(title)}`,
  })
}
</script>

<style scoped>
.sessions {
  min-height: 100vh;
  background-color: #f5f5f7;
}

.sessions__body {
  padding: 24rpx;
}

.sessions__item {
  display: flex;
  padding: 24rpx;
  margin-bottom: 16rpx;
}

.sessions__avatar {
  flex-shrink: 0;
  width: 92rpx;
  height: 92rpx;
  line-height: 92rpx;
  border-radius: 24rpx;
  text-align: center;
  font-size: 34rpx;
  font-weight: 600;
}

.sessions__avatar.is-merchant {
  color: #ff7a00;
  background-color: #fff2e6;
}

.sessions__avatar.is-rider {
  color: #1677ff;
  background-color: #e6f4ff;
}

.sessions__avatar.is-service {
  color: #52c41a;
  background-color: #f0fbe9;
}

.sessions__main {
  flex: 1;
  min-width: 0;
  margin-left: 20rpx;
}

.sessions__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16rpx;
}

.sessions__name {
  flex: 1;
  min-width: 0;
  font-size: 30rpx;
  font-weight: 600;
  color: #1f1f1f;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.sessions__time {
  flex-shrink: 0;
  font-size: 22rpx;
  color: #a0a0a0;
}

.sessions__foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16rpx;
  margin-top: 8rpx;
}

.sessions__last {
  flex: 1;
  min-width: 0;
  font-size: 24rpx;
  color: #8a8a8a;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.sessions__badge {
  flex-shrink: 0;
  min-width: 36rpx;
  height: 36rpx;
  padding: 0 10rpx;
  line-height: 36rpx;
  border-radius: 18rpx;
  background-color: #ff4d00;
  color: #ffffff;
  font-size: 20rpx;
  text-align: center;
}

.sessions__meta {
  display: flex;
  align-items: center;
  gap: 12rpx;
  margin-top: 8rpx;
}

.sessions__empty-icon {
  width: 120rpx;
  height: 120rpx;
  line-height: 120rpx;
  margin: 0 auto 20rpx;
  border-radius: 50%;
  background-color: #ffffff;
  color: #d0d0d0;
  font-size: 48rpx;
  text-align: center;
}

.sessions__retry {
  padding: 24rpx 0;
  text-align: center;
  color: #ff7a00;
  font-size: 24rpx;
}
</style>
