<template>
  <view class="sessions rd-page">
    <view class="rd-card sessions__notice">
      <text class="sessions__notice-title">消息说明</text>
      <text class="sessions__notice-line">
        骑手端展示用户发起的会话，可查看并回复。骑手主动发起客服会话暂不开放：后端
        ImSessionServiceImpl 只允许商家/骑手/客服与消费者建立会话。
      </text>
    </view>

    <view v-if="loading && sessions.length === 0" class="rd-loading">正在加载会话…</view>

    <view v-else-if="error && sessions.length === 0" class="rd-error">
      <text class="rd-error__msg">{{ error }}</text>
      <view class="rd-error__retry" @click="load">重新加载</view>
    </view>

    <view v-else-if="sessions.length === 0" class="rd-empty">
      <text class="rd-empty__title">暂无可查看的会话</text>
      <text>用户就订单发起咨询后，会话会显示在这里</text>
      <view class="rd-empty__action" @click="goHelp">查看帮助中心</view>
    </view>

    <view v-else class="sessions__list">
      <view v-if="error" class="sessions__error">
        <text>{{ error }}</text>
        <text class="sessions__error-retry" @click="load">重试</text>
      </view>

      <view
        v-for="item in sessions"
        :key="item.id"
        class="rd-card sessions__row"
        @click="openChat(item)"
      >
        <view class="sessions__avatar">用</view>

        <view class="sessions__main">
          <view class="sessions__row-head">
            <text class="sessions__name">用户</text>
            <text v-if="item.orderId" class="sessions__order">订单 #{{ item.orderId }}</text>
            <text v-if="statusLabel(item.status)" class="sessions__status">
              {{ statusLabel(item.status) }}
            </text>
            <text class="sessions__time">{{ formatShortDateTime(item.lastMessageAt) }}</text>
          </view>
          <view class="sessions__row-body">
            <text class="sessions__last">{{ item.lastMessage || '暂无消息' }}</text>
            <text v-if="Number(item.unread) > 0" class="sessions__badge">
              {{ Number(item.unread) > 99 ? '99+' : item.unread }}
            </text>
          </view>
        </view>
      </view>

      <view class="sessions__end">共 {{ sessions.length }} 个会话</view>
    </view>

    <view class="sessions__footer">
      <text class="sessions__footer-line">客服热线与工单入口请前往「帮助中心 - 联系客服」。</text>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { onPullDownRefresh, onShow } from '@dcloudio/uni-app'
import { fetchImSessions } from '@/api/rider'
import type { ImSessionVO } from '@/api/types'
import { useAuthStore } from '@/stores/auth'
import { formatShortDateTime } from '@/utils/date'

/**
 * 消息列表：GET /im/sessions。
 *
 * 骑手侧只能拿到 peer_type=3 且 peer_id=本人 riderId 的会话，即由消费者发起的会话；
 * 创建接口对骑手主体只接受 peerType=1（对端必须是消费者），把 peerType=4（客服）
 * 传给 ImSessionServiceImpl#getOrCreate 会被 10001 拒绝，因此本页不提供“发起客服会话”。
 * 列表中的「对方」是 t_member_user.id，页面按「用户」展示，不伪造昵称。
 */

const auth = useAuthStore()

const sessions = ref<ImSessionVO[]>([])
const loading = ref(false)
const error = ref('')

onShow(() => {
  if (!auth.ensureLogin('/pages/im/Sessions')) {
    return
  }
  void load()
})

onPullDownRefresh(() => {
  void (async () => {
    await load()
    uni.stopPullDownRefresh()
  })()
})

function statusLabel(status: number): string {
  switch (Number(status)) {
    case 1:
      return '已结束'
    case 2:
      return '只读'
    default:
      return ''
  }
}

async function load(): Promise<void> {
  if (loading.value) {
    return
  }
  loading.value = true
  error.value = ''
  try {
    const data = await fetchImSessions()
    sessions.value = Array.isArray(data) ? data.filter((item) => !!item) : []
  } catch (err) {
    // 失败保留已有会话列表，仅提示错误
    error.value = (err as Error)?.message || '会话加载失败'
  } finally {
    loading.value = false
  }
}

function openChat(item: ImSessionVO): void {
  const query = [`id=${item.id}`, `status=${Number(item.status ?? 0)}`]
  if (item.orderId) {
    query.push(`orderId=${item.orderId}`)
  }
  uni.navigateTo({ url: `/pages/im/Chat?${query.join('&')}` })
}

function goHelp(): void {
  uni.navigateTo({ url: '/pages/help/Help' })
}
</script>

<style scoped>
.sessions {
  padding-bottom: 60rpx;
}

.sessions__notice {
  padding: 20rpx 24rpx;
  background-color: #f6f8fb;
}

.sessions__notice-title {
  display: block;
  font-size: 24rpx;
  font-weight: 600;
  color: #33455f;
}

.sessions__notice-line {
  display: block;
  margin-top: 6rpx;
  font-size: 21rpx;
  color: #8d99aa;
  line-height: 1.7;
}

.sessions__list {
  margin-top: 20rpx;
}

.sessions__error {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16rpx;
  padding: 16rpx 20rpx;
  border-radius: 12rpx;
  background-color: #fff1f0;
  color: #b42318;
  font-size: 24rpx;
}

.sessions__error-retry {
  font-weight: 700;
  text-decoration: underline;
}

.sessions__row {
  display: flex;
  align-items: center;
  gap: 20rpx;
  margin-bottom: 14rpx;
  padding: 22rpx 24rpx;
}

.sessions__avatar {
  flex: none;
  width: 80rpx;
  height: 80rpx;
  line-height: 80rpx;
  text-align: center;
  border-radius: 20rpx;
  background-color: #eef2f7;
  color: #33455f;
  font-size: 30rpx;
  font-weight: 700;
}

.sessions__main {
  flex: 1;
  min-width: 0;
}

.sessions__row-head {
  display: flex;
  align-items: center;
  gap: 12rpx;
}

.sessions__name {
  font-size: 28rpx;
  font-weight: 600;
  color: #16233a;
}

.sessions__order {
  padding: 2rpx 10rpx;
  border-radius: 6rpx;
  background-color: #f1f4f8;
  font-size: 19rpx;
  color: #7a8699;
}

.sessions__status {
  font-size: 19rpx;
  color: #a9b4c4;
}

.sessions__time {
  margin-left: auto;
  flex: none;
  font-size: 20rpx;
  color: #a9b4c4;
  font-variant-numeric: tabular-nums;
}

.sessions__row-body {
  display: flex;
  align-items: center;
  gap: 12rpx;
  margin-top: 6rpx;
}

.sessions__last {
  flex: 1;
  min-width: 0;
  font-size: 23rpx;
  color: #8d99aa;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.sessions__badge {
  flex: none;
  min-width: 34rpx;
  height: 34rpx;
  line-height: 34rpx;
  padding: 0 8rpx;
  border-radius: 17rpx;
  background-color: #d93a2b;
  color: #ffffff;
  font-size: 19rpx;
  text-align: center;
}

.sessions__end {
  padding: 20rpx 0;
  text-align: center;
  font-size: 21rpx;
  color: #a9b4c4;
}

.sessions__footer {
  margin-top: 20rpx;
  padding: 0 8rpx;
}

.sessions__footer-line {
  font-size: 21rpx;
  color: #a9b4c4;
  line-height: 1.7;
}
</style>
