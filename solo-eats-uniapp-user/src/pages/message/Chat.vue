<template>
  <view class="chat">
    <scroll-view
      class="chat__scroll"
      scroll-y
      :scroll-into-view="scrollTarget"
      :scroll-with-animation="scrollAnimated"
      :upper-threshold="20"
      @scrolltoupper="loadOlder"
    >
      <view class="chat__older">
        <text v-if="loadingOlder" class="se-muted">正在加载更早的消息…</text>
        <text v-else-if="hasMore && messages.length > 0" class="chat__older-link" @click="loadOlder">
          上拉加载更早消息
        </text>
        <text v-else-if="messages.length > 0" class="se-muted">没有更早的消息了</text>
      </view>

      <view v-if="loading && messages.length === 0" class="se-loading">正在加载会话…</view>

      <view v-else-if="error && messages.length === 0" class="se-error">
        <text>{{ error }}</text>
        <view class="se-error__retry" @click="reload">重新加载</view>
      </view>

      <view v-else-if="messages.length === 0" class="chat__empty">
        <text class="se-muted">还没有消息，发一条打个招呼吧</text>
      </view>

      <view v-else class="chat__list">
        <template v-for="(msg, index) in messages" :key="msg.id">
          <view v-if="showTimeDivider(index)" class="chat__time">
            {{ formatDateTime(msg.createdAt) }}
          </view>
          <view :id="`msg-${msg.id}`" class="chat__row" :class="{ 'is-mine': isMine(msg) }">
            <view class="chat__bubble" :class="{ 'is-mine': isMine(msg) }">
              <text class="chat__text">{{ messageText(msg) }}</text>
            </view>
          </view>
        </template>
      </view>
    </scroll-view>

    <view class="chat__bar">
      <input
        v-model="draft"
        class="chat__input"
        type="text"
        confirm-type="send"
        :maxlength="2000"
        :disabled="sending"
        placeholder="输入消息…"
        placeholder-class="chat__placeholder"
        @confirm="send"
      />
      <view class="chat__send" :class="{ 'is-disabled': !canSend }" @click="send">
        {{ sending ? '发送中' : '发送' }}
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed, nextTick, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import {
  fetchImMessages,
  fetchImSessions,
  markImSessionRead,
  sendImMessage,
} from '@/api/message'
import type { ImMessageVO, ImSessionVO } from '@/api/message'
import { formatDateTime } from '@/utils/date'

const PAGE_SIZE = 20
const TIME_GAP_MS = 5 * 60 * 1000

const PEER_LABELS: Record<number, string> = {
  2: '商家',
  3: '骑手',
  4: '平台客服',
}

const sessionId = ref(0)
const messages = ref<ImMessageVO[]>([])
const hasMore = ref(false)
const loading = ref(false)
const loadingOlder = ref(false)
const sending = ref(false)
const error = ref('')
const draft = ref('')
const scrollTarget = ref('')
const scrollAnimated = ref(false)
const titleResolved = ref(false)

const canSend = computed(() => draft.value.trim().length > 0 && !sending.value)

onLoad((query) => {
  const id = Number(query?.sessionId)
  if (!Number.isFinite(id) || id <= 0) {
    loading.value = false
    error.value = '缺少有效的会话 ID'
    return
  }
  sessionId.value = id

  const passedTitle = decodeQueryValue(query?.title)
  if (passedTitle) {
    titleResolved.value = true
    setTitle(passedTitle)
  }

  loadInitial()
})

function decodeQueryValue(value: unknown): string {
  const raw = typeof value === 'string' ? value : ''
  if (!raw) {
    return ''
  }
  try {
    return decodeURIComponent(raw).trim()
  } catch (err) {
    return raw.trim()
  }
}

function setTitle(title: string): void {
  uni.setNavigationBarTitle({ title })
}

function reload(): void {
  messages.value = []
  hasMore.value = false
  loadInitial()
}

async function loadInitial(): Promise<void> {
  if (loading.value) {
    return
  }
  loading.value = true
  error.value = ''
  let loadingShown = false
  if (messages.value.length === 0) {
    uni.showLoading({ title: '正在加载', mask: true })
    loadingShown = true
  }
  try {
    // 标题与消息并行：标题失败不影响读消息
    if (!titleResolved.value) {
      void resolveTitle()
    }
    const list = await fetchImMessages(sessionId.value, undefined, PAGE_SIZE)
    messages.value = Array.isArray(list) ? list : []
    hasMore.value = messages.value.length >= PAGE_SIZE
    scrollToBottom(false)
    markImSessionRead(sessionId.value).catch(() => {
      // 标已读失败不阻断阅读
    })
  } catch (err) {
    error.value = (err as Error)?.message || '会话加载失败'
  } finally {
    loading.value = false
    if (loadingShown) {
      uni.hideLoading()
    }
  }
}

/** 会话列表接口不下发对方昵称/头像，按对端类型给占位标题 */
async function resolveTitle(): Promise<void> {
  try {
    const sessions = await fetchImSessions(true)
    const session = (Array.isArray(sessions) ? sessions : []).find(
      (item: ImSessionVO) => item.id === sessionId.value,
    )
    if (session) {
      titleResolved.value = true
      setTitle(`${PEER_LABELS[session.peerType] || '对方'} #${session.peerId}`)
    }
  } catch (err) {
    // 标题解析失败保持默认标题
  }
}

async function loadOlder(): Promise<void> {
  if (loading.value || loadingOlder.value || !hasMore.value || messages.value.length === 0) {
    return
  }
  loadingOlder.value = true
  const oldestId = messages.value[0]?.id
  try {
    const rows = await fetchImMessages(sessionId.value, oldestId, PAGE_SIZE)
    const older = Array.isArray(rows) ? rows : []
    if (older.length === 0) {
      hasMore.value = false
      return
    }
    messages.value = older.concat(messages.value)
    hasMore.value = older.length >= PAGE_SIZE
  } catch (err) {
    // http 层已提示；保留当前消息，用户可再次上拉重试
  } finally {
    loadingOlder.value = false
  }
}

async function send(): Promise<void> {
  if (!canSend.value) {
    if (!draft.value.trim()) {
      uni.showToast({ title: '请输入消息内容', icon: 'none' })
    }
    return
  }
  const content = draft.value.trim()
  sending.value = true
  try {
    const created = await sendImMessage(sessionId.value, { msgType: 1, content })
    messages.value.push(created)
    draft.value = ''
    scrollToBottom(true)
  } catch (err) {
    // 发送失败保留输入内容，http 层已弹提示
  } finally {
    sending.value = false
  }
}

function scrollToBottom(animated: boolean): void {
  if (messages.value.length === 0) {
    return
  }
  scrollAnimated.value = animated
  const lastId = messages.value[messages.value.length - 1].id
  nextTick(() => {
    scrollTarget.value = `msg-${lastId}`
  })
}

function isMine(message: ImMessageVO): boolean {
  return message.senderType === 1
}

function messageText(message: ImMessageVO): string {
  if (message.msgType === 2) {
    return '[图片]'
  }
  if (message.msgType === 3) {
    return '[订单卡片]'
  }
  if (message.msgType === 4) {
    return '[位置]'
  }
  return message.content || ''
}

function showTimeDivider(index: number): boolean {
  if (index === 0) {
    return true
  }
  const prev = toTimestamp(messages.value[index - 1]?.createdAt)
  const current = toTimestamp(messages.value[index]?.createdAt)
  if (!prev || !current) {
    return false
  }
  return current - prev > TIME_GAP_MS
}

/** 后端时间格式为 2026-10-25T12:03:00，逐段解析，避免 iOS 对连字符格式解析失败 */
function toTimestamp(value: unknown): number {
  const text = typeof value === 'string' ? value : ''
  const matched = text.match(/^(\d{4})-(\d{2})-(\d{2})[T ](\d{2}):(\d{2})(?::(\d{2}))?/)
  if (!matched) {
    return 0
  }
  return new Date(
    Number(matched[1]),
    Number(matched[2]) - 1,
    Number(matched[3]),
    Number(matched[4]),
    Number(matched[5]),
    Number(matched[6] || 0),
  ).getTime()
}
</script>

<style scoped>
.chat {
  display: flex;
  flex-direction: column;
  height: 100vh;
  background-color: #f5f5f7;
}

.chat__scroll {
  flex: 1;
  min-height: 0;
}

.chat__older {
  padding: 16rpx 0;
  text-align: center;
  font-size: 22rpx;
}

.chat__older-link {
  color: #ff7a00;
  font-size: 22rpx;
}

.chat__empty {
  padding: 160rpx 0;
  text-align: center;
}

.chat__list {
  padding: 8rpx 24rpx 32rpx;
}

.chat__time {
  padding: 20rpx 0 12rpx;
  text-align: center;
  font-size: 20rpx;
  color: #a8a8a8;
}

.chat__row {
  display: flex;
  justify-content: flex-start;
  margin-bottom: 20rpx;
}

.chat__row.is-mine {
  justify-content: flex-end;
}

.chat__bubble {
  max-width: 76%;
  padding: 18rpx 22rpx;
  border-radius: 12rpx;
  background-color: #ffffff;
  box-shadow: 0 2rpx 8rpx rgba(20, 20, 20, 0.04);
}

.chat__bubble.is-mine {
  background: linear-gradient(135deg, #ffa24c, #ff7a00);
}

.chat__text {
  font-size: 28rpx;
  line-height: 1.6;
  color: #333333;
  word-break: break-word;
  white-space: pre-wrap;
}

.chat__bubble.is-mine .chat__text {
  color: #ffffff;
}

.chat__bar {
  display: flex;
  align-items: center;
  gap: 16rpx;
  padding: 16rpx 24rpx calc(16rpx + env(safe-area-inset-bottom));
  background-color: #ffffff;
  box-shadow: 0 -2rpx 10rpx rgba(20, 20, 20, 0.05);
}

.chat__input {
  flex: 1;
  height: 72rpx;
  padding: 0 24rpx;
  border-radius: 36rpx;
  background-color: #f2f2f4;
  font-size: 28rpx;
  color: #1f1f1f;
}

.chat__placeholder {
  color: #b0b0b0;
  font-size: 26rpx;
}

.chat__send {
  flex-shrink: 0;
  padding: 0 32rpx;
  height: 72rpx;
  line-height: 72rpx;
  border-radius: 36rpx;
  background: linear-gradient(135deg, #ffa24c, #ff7a00);
  color: #ffffff;
  font-size: 26rpx;
  font-weight: 500;
}

.chat__send.is-disabled {
  opacity: 0.45;
}
</style>
