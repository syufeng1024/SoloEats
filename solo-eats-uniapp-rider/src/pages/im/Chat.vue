<template>
  <view class="chat rd-page">
    <view v-if="orderId" class="chat__order">关联订单 #{{ orderId }}</view>

    <view v-if="sessionStatus !== 0" class="chat__closed">
      会话已结束，仅可查看历史消息；如需继续沟通请联系平台客服。
    </view>

    <view v-if="error && messages.length === 0" class="rd-error">
      <text class="rd-error__msg">{{ error }}</text>
      <view class="rd-error__retry" @click="loadLatest(true)">重新加载</view>
    </view>

    <view v-else-if="loading && messages.length === 0" class="rd-loading">正在加载消息…</view>

    <view v-else>
      <view v-if="error" class="chat__inline-error">
        <text>{{ error }}</text>
        <text class="chat__inline-retry" @click="loadLatest(false)">重试</text>
      </view>

      <view v-if="hasMore" class="chat__more" @click="loadOlder">
        {{ loadingOlder ? '加载中…' : '加载更早消息' }}
      </view>

      <view v-if="messages.length === 0" class="rd-empty">
        <text class="rd-empty__title">还没有消息</text>
        <text>发送第一条消息开始沟通</text>
      </view>

      <view
        v-for="message in messages"
        :key="message.id"
        class="chat__row"
        :class="{ 'is-mine': isMine(message) }"
      >
        <text class="chat__time">{{ formatShortDateTime(message.createdAt) }}</text>
        <view class="chat__bubble">
          <text v-if="!isMine(message)" class="chat__sender">
            {{ senderLabel(message.senderType) }}
          </text>
          <text class="chat__text">{{ messageText(message) }}</text>
        </view>
      </view>

      <view class="chat__bottom-space" />
    </view>

    <view class="chat__bar">
      <input
        v-model="draft"
        class="chat__input"
        :disabled="sessionStatus !== 0"
        :maxlength="2000"
        confirm-type="send"
        placeholder="输入消息…"
        placeholder-class="chat__placeholder"
        @confirm="send"
      />
      <view class="chat__send" :class="{ 'is-disabled': !canSend }" @click="send">
        {{ sending ? '发送中…' : '发送' }}
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed, nextTick, ref } from 'vue'
import { onHide, onLoad, onShow, onUnload } from '@dcloudio/uni-app'
import { fetchImMessages, markImSessionRead, sendImText } from '@/api/rider'
import type { ImMessageVO } from '@/api/types'
import { useAuthStore } from '@/stores/auth'
import { formatShortDateTime } from '@/utils/date'

/**
 * 单会话聊天。
 * 可进入的会话都是消费者发起、peer_type=3 且 peer_id=本人 riderId 的会话，
 * 因此当前骑手是合法参与者，GET messages / POST messages 均通过后端校验。
 * 骑手无法新建客服会话（ImSessionServiceImpl 对非用户主体强制 peerType=1），本页不做发起入口。
 * 页面使用普通页面滚动 + 固定底部输入栏，避免 H5 下 scroll-view 高度受导航栏影响。
 */

const RIDER_SENDER_TYPE = 3
const PAGE_SIZE = 30
const POLL_INTERVAL_MS = 10_000

const auth = useAuthStore()

const sessionId = ref(0)
const orderId = ref<number | null>(null)
const sessionStatus = ref(0)

const messages = ref<ImMessageVO[]>([])
const loading = ref(false)
const loadingOlder = ref(false)
const sending = ref(false)
const hasMore = ref(false)
const error = ref('')
const draft = ref('')

let pollTimer: ReturnType<typeof setInterval> | null = null

const canSend = computed(
  () => sessionStatus.value === 0 && !sending.value && draft.value.trim().length > 0,
)

onLoad((query) => {
  const rawId = query?.id
  const parsedId = Number(Array.isArray(rawId) ? rawId[0] : rawId)
  if (!Number.isFinite(parsedId) || parsedId <= 0) {
    error.value = '会话参数缺失，无法加载消息'
    return
  }
  sessionId.value = parsedId

  const rawStatus = query?.status
  const parsedStatus = Number(Array.isArray(rawStatus) ? rawStatus[0] : rawStatus)
  sessionStatus.value = Number.isFinite(parsedStatus) ? parsedStatus : 0

  const rawOrderId = query?.orderId
  const parsedOrderId = Number(Array.isArray(rawOrderId) ? rawOrderId[0] : rawOrderId)
  orderId.value = Number.isFinite(parsedOrderId) && parsedOrderId > 0 ? parsedOrderId : null
})

onShow(() => {
  if (!auth.ensureLogin(`/pages/im/Chat?id=${sessionId.value}`)) {
    return
  }
  if (sessionId.value <= 0) {
    return
  }
  void loadLatest(messages.value.length === 0)
  startPolling()
})

onHide(() => {
  stopPolling()
})

onUnload(() => {
  stopPolling()
})

function startPolling(): void {
  stopPolling()
  pollTimer = setInterval(() => {
    void loadLatest(false)
  }, POLL_INTERVAL_MS)
}

function stopPolling(): void {
  if (pollTimer !== null) {
    clearInterval(pollTimer)
    pollTimer = null
  }
}

function isMine(message: ImMessageVO): boolean {
  return Number(message?.senderType) === RIDER_SENDER_TYPE
}

function senderLabel(senderType: number): string {
  switch (Number(senderType)) {
    case 1:
      return '用户'
    case 2:
      return '商家'
    case 4:
      return '平台客服'
    default:
      return '对方'
  }
}

function messageText(message: ImMessageVO): string {
  switch (Number(message?.msgType)) {
    case 2:
      return '[图片]（暂不支持查看）'
    case 3:
      return '[订单卡片]'
    case 4:
      return '[位置]'
    default:
      return message?.content || '[空消息]'
  }
}

/** 按 id 去重并以 id 升序合并，保证轮询、发消息、翻历史都不产生重复气泡 */
function mergeMessages(incoming: ImMessageVO[], existing: ImMessageVO[]): ImMessageVO[] {
  const map = new Map<number, ImMessageVO>()
  existing.forEach((item) => {
    if (item) {
      map.set(item.id, item)
    }
  })
  incoming.forEach((item) => {
    if (item) {
      map.set(item.id, item)
    }
  })
  return Array.from(map.values()).sort((left, right) => left.id - right.id)
}

async function loadLatest(initial: boolean): Promise<void> {
  if (loading.value || sessionId.value <= 0) {
    return
  }
  loading.value = true
  error.value = ''
  try {
    const page = await fetchImMessages(sessionId.value, undefined, PAGE_SIZE)
    const list = Array.isArray(page) ? page : []
    const knownIds = new Set(messages.value.map((item) => item.id))
    const hasIncoming = list.some(
      (item) => Number(item.senderType) !== RIDER_SENDER_TYPE && !knownIds.has(item.id),
    )
    messages.value = mergeMessages(list, messages.value)
    if (initial) {
      hasMore.value = list.length >= PAGE_SIZE
      void markRead()
    } else if (hasIncoming) {
      void markRead()
    }
    if (initial || hasIncoming) {
      scrollToBottom()
    }
  } catch (err) {
    error.value = (err as Error)?.message || '消息加载失败'
  } finally {
    loading.value = false
  }
}

async function loadOlder(): Promise<void> {
  if (loadingOlder.value || messages.value.length === 0) {
    return
  }
  loadingOlder.value = true
  try {
    const first = messages.value[0]
    const older = await fetchImMessages(sessionId.value, first.id, PAGE_SIZE)
    const list = Array.isArray(older) ? older : []
    messages.value = mergeMessages(list, messages.value)
    hasMore.value = list.length >= PAGE_SIZE
    if (list.length === 0) {
      uni.showToast({ title: '没有更早的消息了', icon: 'none' })
    }
  } catch (err) {
    uni.showToast({ title: (err as Error)?.message || '更早消息加载失败', icon: 'none' })
  } finally {
    loadingOlder.value = false
  }
}

async function markRead(): Promise<void> {
  try {
    await markImSessionRead(sessionId.value)
  } catch {
    // 未读清零失败不影响消息展示，下次进入会再次尝试
  }
}

async function send(): Promise<void> {
  if (sessionStatus.value !== 0) {
    uni.showToast({ title: '会话已结束，无法发送', icon: 'none' })
    return
  }
  if (sending.value) {
    return
  }
  const text = draft.value.trim()
  if (!text) {
    return
  }
  if (text.length > 2000) {
    uni.showToast({ title: '消息不能超过 2000 字', icon: 'none' })
    return
  }

  sending.value = true
  try {
    const message = await sendImText(sessionId.value, text)
    messages.value = mergeMessages([message], messages.value)
    draft.value = ''
    scrollToBottom()
  } catch {
    // http 层已 toast，保留输入内容供重发
  } finally {
    sending.value = false
  }
}

function scrollToBottom(): void {
  void nextTick(() => {
    uni.pageScrollTo({ scrollTop: 999999, duration: 0 })
  })
}
</script>

<style scoped>
.chat {
  padding-bottom: 180rpx;
}

.chat__order {
  margin-bottom: 16rpx;
  padding: 12rpx 20rpx;
  border-radius: 10rpx;
  background-color: #eef2f7;
  font-size: 22rpx;
  color: #7a8699;
}

.chat__closed {
  margin-bottom: 16rpx;
  padding: 16rpx 20rpx;
  border-radius: 12rpx;
  background-color: #fff6ea;
  color: #a2520a;
  font-size: 23rpx;
  line-height: 1.6;
}

.chat__inline-error {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16rpx;
  padding: 14rpx 20rpx;
  border-radius: 12rpx;
  background-color: #fff1f0;
  color: #b42318;
  font-size: 23rpx;
}

.chat__inline-retry {
  font-weight: 700;
  text-decoration: underline;
}

.chat__more {
  padding: 16rpx 0 24rpx;
  text-align: center;
  font-size: 23rpx;
  color: #e85d04;
}

.chat__row {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  margin-bottom: 22rpx;
}

.chat__row.is-mine {
  align-items: flex-end;
}

.chat__time {
  margin-bottom: 6rpx;
  padding: 0 6rpx;
  font-size: 19rpx;
  color: #a9b4c4;
  font-variant-numeric: tabular-nums;
}

.chat__bubble {
  max-width: 78%;
  padding: 18rpx 22rpx;
  border-radius: 18rpx;
  background-color: #ffffff;
  box-shadow: 0 4rpx 14rpx rgba(20, 35, 58, 0.06);
}

.chat__row.is-mine .chat__bubble {
  background: linear-gradient(135deg, #ff9a3c, #ff7a00);
  color: #ffffff;
}

.chat__sender {
  display: block;
  margin-bottom: 6rpx;
  font-size: 19rpx;
  color: #a9b4c4;
}

.chat__text {
  font-size: 27rpx;
  line-height: 1.6;
  color: #16233a;
  word-break: break-all;
}

.chat__row.is-mine .chat__text {
  color: #ffffff;
}

.chat__bottom-space {
  height: 24rpx;
}

.chat__bar {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  z-index: 30;
  display: flex;
  align-items: center;
  gap: 16rpx;
  padding: 16rpx 24rpx calc(16rpx + env(safe-area-inset-bottom));
  background-color: #ffffff;
  box-shadow: 0 -4rpx 16rpx rgba(20, 35, 58, 0.08);
}

.chat__input {
  flex: 1;
  height: 80rpx;
  padding: 0 22rpx;
  border-radius: 40rpx;
  background-color: #f1f4f8;
  font-size: 27rpx;
  color: #16233a;
}

.chat__placeholder {
  color: #b3bdca;
  font-size: 25rpx;
}

.chat__send {
  flex: none;
  min-width: 128rpx;
  height: 80rpx;
  line-height: 80rpx;
  text-align: center;
  border-radius: 40rpx;
  background: linear-gradient(135deg, #ff9a3c, #ff7a00);
  color: #ffffff;
  font-size: 27rpx;
  font-weight: 600;
}

.chat__send.is-disabled {
  opacity: 0.45;
}
</style>
