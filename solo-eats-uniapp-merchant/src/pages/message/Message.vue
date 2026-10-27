<template>
  <view class="message mt-page">
    <!-- 会话 / 通知切换 -->
    <view class="message__tabs mt-card">
      <view
        class="message__tab"
        :class="{ 'is-active': activeTab === 'im' }"
        @click="switchTab('im')"
      >
        <text>用户会话</text>
        <text v-if="imUnread > 0" class="message__tab-badge">
          {{ imUnread > 99 ? '99+' : imUnread }}
        </text>
      </view>
      <view
        class="message__tab"
        :class="{ 'is-active': activeTab === 'notice' }"
        @click="switchTab('notice')"
      >
        <text>平台通知</text>
        <text v-if="unread.total > 0" class="message__tab-badge">
          {{ unread.total > 99 ? '99+' : unread.total }}
        </text>
      </view>
    </view>

    <!-- 会话列表：GET /im/sessions -->
    <template v-if="activeTab === 'im'">
      <view v-if="sessionLoading && sessions.length === 0" class="mt-loading">
        正在加载会话…
      </view>

      <view v-else-if="sessionError && sessions.length === 0" class="mt-error">
        <text class="mt-error__msg">{{ sessionError }}</text>
        <view class="mt-error__retry" @click="loadSessions">重新加载</view>
      </view>

      <view v-else-if="sessions.length === 0" class="mt-empty">
        <text class="mt-empty__title">暂无用户会话</text>
        <text>用户在订单页发起咨询后，会话会显示在这里</text>
      </view>

      <template v-else>
        <view
          v-for="session in sessions"
          :key="session.id"
          class="mt-card message__session"
          @click="openChat(session)"
        >
          <view class="message__avatar">用</view>
          <view class="message__session-main">
            <view class="mt-between">
              <text class="message__session-name">用户 #{{ session.userId }}</text>
              <text class="mt-muted">
                {{ formatShortDateTime(session.lastMessageAt || session.createdAt) }}
              </text>
            </view>

            <view class="mt-between message__session-foot">
              <text class="message__last">{{ session.lastMessage || '暂无消息，等待用户开口' }}</text>
              <text v-if="Number(session.unread) > 0" class="message__badge">
                {{ Number(session.unread) > 99 ? '99+' : session.unread }}
              </text>
            </view>

            <view class="message__session-meta">
              <text v-if="session.orderId" class="mt-tag mt-tag--info">
                订单 #{{ session.orderId }}
              </text>
              <text class="mt-tag" :class="sessionStatusClass(session.status)">
                {{ sessionStatusText(session.status) }}
              </text>
            </view>
          </view>
        </view>

        <view v-if="sessionError" class="message__retry" @click="loadSessions">
          加载失败，点击重试
        </view>
      </template>
    </template>

    <!-- 站内信：GET /messages -->
    <template v-else>
      <view class="mt-card message__summary">
        <view class="message__summary-main">
          <text class="message__summary-total">{{ unread.total }}</text>
          <text class="mt-muted">条未读</text>
        </view>
        <view class="message__summary-types">
          <text class="mt-muted">订单 {{ unread.byType.order }}</text>
          <text class="mt-muted">活动 {{ unread.byType.marketing }}</text>
          <text class="mt-muted">系统 {{ unread.byType.system }}</text>
        </view>
      </view>

      <view v-if="noticeLoading && notices.length === 0" class="mt-loading">
        正在加载通知…
      </view>

      <view v-else-if="noticeError && notices.length === 0" class="mt-error">
        <text class="mt-error__msg">{{ noticeError }}</text>
        <view class="mt-error__retry" @click="loadNotices(true)">重新加载</view>
      </view>

      <view v-else-if="notices.length === 0" class="mt-empty">
        <text class="mt-empty__title">暂无通知</text>
        <text>订单进度、活动与系统通知会出现在这里</text>
      </view>

      <template v-else>
        <view
          v-for="item in notices"
          :key="item.id"
          class="mt-card message__notice"
          :class="{ 'is-unread': item.readFlag === 0 }"
          @click="toggleNotice(item)"
        >
          <view class="mt-between">
            <view class="message__notice-title-row">
              <text v-if="item.readFlag === 0" class="message__dot" />
              <text class="message__notice-title">{{ item.title }}</text>
            </view>
            <text class="mt-muted">{{ formatShortDateTime(item.createdAt) }}</text>
          </view>

          <text
            class="message__notice-content"
            :class="{ 'is-expanded': expandedId === item.id }"
          >
            {{ item.content }}
          </text>

          <view class="mt-between message__notice-foot">
            <text class="mt-tag" :class="noticeTagClass(item.msgType)">
              {{ noticeTypeLabel(item.msgType) }}
            </text>
            <text class="message__expand">
              {{ expandedId === item.id ? '收起' : '展开全文' }}
            </text>
          </view>
        </view>

        <view v-if="noticeError" class="message__retry" @click="loadNotices(noticeFailedReset)">
          加载失败，点击重试
        </view>
        <view v-else-if="hasMoreNotices" class="message__more" @click="loadNotices(false)">
          {{ noticeLoading ? '加载中…' : '加载更多通知' }}
        </view>
        <view v-else class="message__end">没有更多通知了</view>
      </template>
    </template>
  </view>
</template>

<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { onPullDownRefresh, onReachBottom, onShow } from '@dcloudio/uni-app'
import {
  fetchImSessions,
  fetchSiteMessages,
  fetchUnreadCount,
  markSiteMessageRead,
} from '@/api/message'
import type { ImSessionVO, SiteMessageVO, UnreadCountVO } from '@/api/message'
import { useAuthStore } from '@/stores/auth'
import { formatShortDateTime } from '@/utils/date'

/**
 * 消息中心。
 *
 * - 站内信：GET /messages + /messages/unread-count，receiverId 由后端按令牌解析为
 *   商家业务 ID（t_merchant.id），前端不传任何接收者参数。
 * - IM：GET /im/sessions，商家侧会话的 peer_type 固定为 2、peer_id 为本人 merchantId，
 *   t_im_session.user_id 恒为消费者，所以对端只按「用户 #userId」展示，不伪造昵称。
 * - 后端没有 GET /messages/{id}，通知详情用列表数据就地展开；展开即标已读。
 */

const MESSAGE_PATH = '/pages/message/Message'
const NOTICE_PAGE_SIZE = 20

type TabKey = 'im' | 'notice'

const auth = useAuthStore()

const activeTab = ref<TabKey>('im')

const sessions = ref<ImSessionVO[]>([])
const sessionLoading = ref(false)
const sessionError = ref('')

const notices = ref<SiteMessageVO[]>([])
const noticeLoading = ref(false)
const noticeError = ref('')
const noticePageNum = ref(0)
const noticeTotal = ref(0)
const noticeFailedReset = ref(false)
const expandedId = ref<number | null>(null)

const unread = reactive<UnreadCountVO>({
  total: 0,
  byType: { order: 0, marketing: 0, system: 0 },
})

const imUnread = computed(() =>
  sessions.value.reduce((sum, item) => sum + (Number(item.unread) || 0), 0),
)

const hasMoreNotices = computed(() => notices.value.length < noticeTotal.value)

onShow(() => {
  if (!auth.ensureLogin(MESSAGE_PATH)) {
    return
  }
  void refresh()
})

onPullDownRefresh(() => {
  void refresh()
})

onReachBottom(() => {
  if (activeTab.value === 'notice') {
    loadNotices(false)
  }
})

async function refresh(): Promise<void> {
  await Promise.all([loadUnread(), loadSessions(), loadNotices(true)])
  uni.stopPullDownRefresh()
}

function switchTab(tab: TabKey): void {
  if (activeTab.value === tab) {
    return
  }
  activeTab.value = tab
  if (tab === 'notice' && notices.value.length === 0 && !noticeLoading.value) {
    void loadNotices(true)
  }
  if (tab === 'im' && sessions.value.length === 0 && !sessionLoading.value) {
    void loadSessions()
  }
}

async function loadUnread(): Promise<void> {
  try {
    const result = await fetchUnreadCount(true)
    unread.total = Number(result?.total) || 0
    unread.byType.order = Number(result?.byType?.order) || 0
    unread.byType.marketing = Number(result?.byType?.marketing) || 0
    unread.byType.system = Number(result?.byType?.system) || 0
  } catch {
    // 角标失败不阻断列表；保留上一次的值
  }
}

async function loadSessions(): Promise<void> {
  if (sessionLoading.value) {
    return
  }
  sessionLoading.value = true
  try {
    const result = await fetchImSessions(true)
    sessions.value = Array.isArray(result) ? result : []
    sessionError.value = ''
  } catch (err) {
    // 保留已有会话，失败只更新错误提示
    sessionError.value = (err as Error)?.message || '会话加载失败'
  } finally {
    sessionLoading.value = false
  }
}

async function loadNotices(reset: boolean): Promise<void> {
  if (noticeLoading.value) {
    return
  }
  if (!reset && !hasMoreNotices.value) {
    return
  }
  noticeLoading.value = true
  if (reset) {
    noticeError.value = ''
  }
  const nextPage = reset ? 1 : noticePageNum.value + 1
  try {
    const page = await fetchSiteMessages(
      { pageNum: nextPage, pageSize: NOTICE_PAGE_SIZE },
      true,
    )
    const list = Array.isArray(page.list) ? page.list : []
    notices.value = reset ? list : mergeNotices(notices.value, list)
    noticePageNum.value = nextPage
    noticeTotal.value = Number(page.total) || 0
    noticeError.value = ''
    noticeFailedReset.value = false
  } catch (err) {
    noticeError.value = (err as Error)?.message || '通知加载失败'
    noticeFailedReset.value = reset
    if (reset) {
      notices.value = []
      noticeTotal.value = 0
    }
  } finally {
    noticeLoading.value = false
  }
}

/** 翻页合并按 id 去重，避免重复展开时把同一条渲染两次 */
function mergeNotices(existing: SiteMessageVO[], incoming: SiteMessageVO[]): SiteMessageVO[] {
  const map = new Map<number, SiteMessageVO>()
  existing.forEach((item) => map.set(item.id, item))
  incoming.forEach((item) => map.set(item.id, item))
  return Array.from(map.values()).sort((left, right) => right.id - left.id)
}

function toggleNotice(item: SiteMessageVO): void {
  expandedId.value = expandedId.value === item.id ? null : item.id
  if (item.readFlag !== 0) {
    return
  }
  // 先更新本地态让角标立刻变化，接口失败不回滚：下次进页面以服务端为准
  item.readFlag = 1
  unread.total = Math.max(0, unread.total - 1)
  const key = unreadKey(item.msgType)
  if (key) {
    unread.byType[key] = Math.max(0, unread.byType[key] - 1)
  }
  markSiteMessageRead(item.id).catch(() => {
    // silent 请求，不打断阅读
  })
}

function unreadKey(msgType: number): 'order' | 'marketing' | 'system' | null {
  if (msgType === 1) {
    return 'order'
  }
  if (msgType === 2) {
    return 'marketing'
  }
  if (msgType === 3) {
    return 'system'
  }
  return null
}

function noticeTypeLabel(msgType: number): string {
  if (msgType === 1) {
    return '订单'
  }
  if (msgType === 2) {
    return '活动'
  }
  if (msgType === 3) {
    return '系统'
  }
  return '通知'
}

function noticeTagClass(msgType: number): string {
  if (msgType === 1) {
    return 'mt-tag--ok'
  }
  if (msgType === 2) {
    return 'mt-tag--warn'
  }
  return 'mt-tag--info'
}

/** 会话状态：0进行中 1已结束 2只读 */
function sessionStatusText(status: number): string {
  if (status === 0) {
    return '进行中'
  }
  if (status === 1) {
    return '已结束'
  }
  if (status === 2) {
    return '只读'
  }
  return `状态 ${status}`
}

function sessionStatusClass(status: number): string {
  return status === 0 ? 'mt-tag--ok' : 'mt-tag--off'
}

function openChat(session: ImSessionVO): void {
  const title = `用户 #${session.userId}`
  const query = [
    `id=${session.id}`,
    `title=${encodeURIComponent(title)}`,
    `status=${session.status}`,
  ]
  if (session.orderId) {
    query.push(`orderId=${session.orderId}`)
  }
  uni.navigateTo({ url: `/pages/message/Chat?${query.join('&')}` })
}
</script>

<style scoped>
.message__tabs {
  display: flex;
  padding: 8rpx;
  margin-bottom: 16rpx;
}

.message__tab {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8rpx;
  height: 72rpx;
  border-radius: 10rpx;
  font-size: 27rpx;
  color: #55637a;
}

.message__tab.is-active {
  background-color: #fff1e6;
  color: #c2410c;
  font-weight: 700;
}

.message__tab-badge {
  min-width: 34rpx;
  height: 34rpx;
  padding: 0 9rpx;
  line-height: 34rpx;
  border-radius: 17rpx;
  background-color: #d93a2b;
  color: #ffffff;
  font-size: 19rpx;
  text-align: center;
}

.message__session {
  display: flex;
  gap: 18rpx;
  margin-bottom: 14rpx;
}

.message__avatar {
  flex: none;
  width: 84rpx;
  height: 84rpx;
  line-height: 84rpx;
  border-radius: 20rpx;
  background-color: #e6f0ff;
  color: #2c4a75;
  font-size: 32rpx;
  font-weight: 700;
  text-align: center;
}

.message__session-main {
  flex: 1;
  min-width: 0;
}

.message__session-name {
  font-size: 29rpx;
  font-weight: 600;
  color: #16233a;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.message__session-foot {
  margin-top: 8rpx;
}

.message__last {
  flex: 1;
  min-width: 0;
  font-size: 24rpx;
  color: #7a8699;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.message__badge {
  flex: none;
  min-width: 36rpx;
  height: 36rpx;
  padding: 0 10rpx;
  line-height: 36rpx;
  border-radius: 18rpx;
  background-color: #d93a2b;
  color: #ffffff;
  font-size: 20rpx;
  text-align: center;
}

.message__session-meta {
  display: flex;
  align-items: center;
  gap: 10rpx;
  margin-top: 10rpx;
}

.message__summary {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16rpx;
  margin-bottom: 16rpx;
}

.message__summary-main {
  display: flex;
  align-items: baseline;
  gap: 6rpx;
}

.message__summary-total {
  font-size: 40rpx;
  font-weight: 700;
  color: #e85d04;
  font-variant-numeric: tabular-nums;
}

.message__summary-types {
  display: flex;
  gap: 16rpx;
}

.message__notice {
  margin-bottom: 14rpx;
}

.message__notice.is-unread {
  border-left: 6rpx solid #ff7a00;
}

.message__notice-title-row {
  flex: 1;
  min-width: 0;
  display: flex;
  align-items: center;
}

.message__dot {
  flex: none;
  width: 12rpx;
  height: 12rpx;
  margin-right: 10rpx;
  border-radius: 50%;
  background-color: #d93a2b;
}

.message__notice-title {
  flex: 1;
  min-width: 0;
  font-size: 28rpx;
  color: #16233a;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.message__notice.is-unread .message__notice-title {
  font-weight: 700;
}

.message__notice-content {
  display: -webkit-box;
  margin-top: 10rpx;
  font-size: 24rpx;
  line-height: 1.65;
  color: #55637a;
  overflow: hidden;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
  word-break: break-all;
  white-space: pre-wrap;
}

.message__notice-content.is-expanded {
  display: block;
  overflow: visible;
}

.message__notice-foot {
  margin-top: 12rpx;
}

.message__expand {
  font-size: 22rpx;
  color: #e85d04;
  font-weight: 600;
}

.message__retry,
.message__more,
.message__end {
  padding: 22rpx 0;
  text-align: center;
  font-size: 23rpx;
  color: #7a8699;
}

.message__retry,
.message__more {
  color: #e85d04;
  font-weight: 600;
}
</style>
