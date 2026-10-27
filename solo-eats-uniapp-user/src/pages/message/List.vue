<template>
  <view class="msg">
    <scroll-view scroll-x class="msg__tabs" :show-scrollbar="false">
      <view
        v-for="tab in TABS"
        :key="tab.key"
        class="msg__tab"
        :class="{ 'is-active': tab.key === activeKey }"
        @click="changeTab(tab.key)"
      >
        {{ tab.label }}
      </view>
    </scroll-view>

    <view class="msg__body">
      <view class="se-card msg__summary">
        <view class="msg__summary-main">
          <text class="msg__unread-total">{{ unread.total }}</text>
          <text class="msg__unread-label">条未读</text>
        </view>
        <view v-if="unread.total > 0" class="msg__summary-types">
          <text class="se-muted">订单 {{ unread.byType.order }}</text>
          <text class="se-muted">活动 {{ unread.byType.marketing }}</text>
          <text class="se-muted">系统 {{ unread.byType.system }}</text>
        </view>
        <text v-else class="se-muted">消息都已读完</text>
      </view>

      <view v-if="loading && list.length === 0" class="se-loading">正在加载消息…</view>

      <view v-else-if="error && list.length === 0" class="se-error">
        <text>{{ error }}</text>
        <view class="se-error__retry" @click="reload">重新加载</view>
      </view>

      <view v-else-if="list.length === 0" class="se-empty">
        <view class="msg__empty-icon">信</view>
        <text class="se-empty__title">暂无{{ currentTabLabel }}消息</text>
        <text>订单进度、活动与系统通知会出现在这里</text>
      </view>

      <view v-else>
        <view
          v-for="item in list"
          :key="item.id"
          class="se-card msg__item"
          :class="{ 'is-unread': item.readFlag === 0 }"
          @click="openDetail(item)"
        >
          <view class="msg__item-head">
            <view class="msg__item-title-row">
              <text v-if="item.readFlag === 0" class="msg__dot" />
              <text class="msg__title">{{ item.title }}</text>
            </view>
            <text class="msg__time">{{ formatShortDateTime(item.createdAt) }}</text>
          </view>

          <text class="msg__content">{{ item.content }}</text>

          <view class="msg__item-foot">
            <text class="se-tag" :class="typeClass(item.msgType)">{{ typeLabel(item.msgType) }}</text>
            <text v-if="item.readFlag === 0" class="msg__unread-text">未读</text>
          </view>
        </view>

        <view v-if="error" class="msg__retry" @click="retryLoad">加载失败，点击重试</view>
        <view v-else-if="hasMore" class="msg__more" @click="loadMore">
          {{ loading ? '加载中…' : '加载更多消息' }}
        </view>
        <view v-else class="msg__end">没有更多消息了</view>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { onPullDownRefresh, onReachBottom, onShow } from '@dcloudio/uni-app'
import { fetchSiteMessages, fetchUnreadCount } from '@/api/message'
import type { SiteMessageVO, UnreadCountVO } from '@/api/message'
import { formatShortDateTime } from '@/utils/date'

interface TabConfig {
  key: string
  label: string
  msgType?: number
}

const TABS: TabConfig[] = [
  { key: 'all', label: '全部' },
  { key: 'order', label: '订单', msgType: 1 },
  { key: 'system', label: '系统', msgType: 3 },
  { key: 'marketing', label: '活动', msgType: 2 },
]

const MSG_TYPE_LABELS: Record<number, string> = {
  1: '订单',
  2: '活动',
  3: '系统',
}

const PAGE_SIZE = 20
const DETAIL_CACHE_KEY = 'message_detail_cache'

const activeKey = ref('all')
const list = ref<SiteMessageVO[]>([])
const pageNum = ref(0)
const total = ref(0)
const hasMore = ref(false)
const loading = ref(false)
const error = ref('')
const failedReset = ref(false)
const unread = reactive<UnreadCountVO>({
  total: 0,
  byType: { order: 0, marketing: 0, system: 0 },
})

const currentTab = computed<TabConfig>(
  () => TABS.find((tab) => tab.key === activeKey.value) || TABS[0],
)
const currentTabLabel = computed(() => (activeKey.value === 'all' ? '' : currentTab.value.label))

onShow(() => {
  loadUnread()
  loadMessages(true)
})

onPullDownRefresh(() => {
  if (loading.value) {
    uni.stopPullDownRefresh()
    return
  }
  loadUnread()
  loadMessages(true)
})

onReachBottom(() => {
  loadMore()
})

function changeTab(key: string): void {
  if (activeKey.value === key) {
    return
  }
  activeKey.value = key
  list.value = []
  total.value = 0
  pageNum.value = 0
  hasMore.value = false
  error.value = ''
  loadMessages(true)
}

async function loadUnread(): Promise<void> {
  try {
    const result = await fetchUnreadCount(true)
    unread.total = Number(result?.total) || 0
    unread.byType.order = Number(result?.byType?.order) || 0
    unread.byType.marketing = Number(result?.byType?.marketing) || 0
    unread.byType.system = Number(result?.byType?.system) || 0
  } catch (err) {
    // 未读角标失败不阻断列表；请求已 silent，这里只保留上一次的值
  }
}

function reload(): void {
  loadMessages(true)
}

function retryLoad(): void {
  error.value = ''
  if (failedReset.value) {
    loadMessages(true)
  } else {
    loadMore()
  }
}

async function loadMessages(reset: boolean): Promise<void> {
  if (loading.value) {
    return
  }
  loading.value = true
  let loadingShown = false
  if (reset && list.value.length === 0) {
    uni.showLoading({ title: '正在加载', mask: true })
    loadingShown = true
  }
  try {
    const page = await fetchSiteMessages({
      msgType: currentTab.value.msgType,
      pageNum: reset ? 1 : pageNum.value + 1,
      pageSize: PAGE_SIZE,
    })
    list.value = reset ? page.list : list.value.concat(page.list)
    pageNum.value = page.pageNum
    total.value = page.total
    hasMore.value = list.value.length < page.total
    error.value = ''
    failedReset.value = false
  } catch (err) {
    error.value = (err as Error)?.message || '消息加载失败'
    failedReset.value = reset
  } finally {
    loading.value = false
    if (loadingShown) {
      uni.hideLoading()
    }
    uni.stopPullDownRefresh()
  }
}

function loadMore(): void {
  if (!hasMore.value || loading.value) {
    return
  }
  loadMessages(false)
}

function typeLabel(msgType: number): string {
  return MSG_TYPE_LABELS[msgType] || '通知'
}

function typeClass(msgType: number): string {
  if (msgType === 1) {
    return 'msg__tag--order'
  }
  if (msgType === 2) {
    return 'msg__tag--marketing'
  }
  return 'msg__tag--system'
}

function openDetail(message: SiteMessageVO): void {
  // 后端没有 GET /messages/{id}，详情页优先读这份列表快照，避免逐页扫描
  uni.setStorageSync(DETAIL_CACHE_KEY, { id: message.id, data: message })
  uni.navigateTo({ url: `/pages/message/Detail?id=${message.id}` })
}
</script>

<style scoped>
.msg {
  min-height: 100vh;
  background-color: #f5f5f7;
}

.msg__tabs {
  position: sticky;
  top: 0;
  z-index: 10;
  white-space: nowrap;
  background-color: #ffffff;
  box-shadow: 0 2rpx 8rpx rgba(20, 20, 20, 0.04);
}

.msg__tab {
  position: relative;
  display: inline-block;
  padding: 24rpx 0;
  margin: 0 24rpx;
  font-size: 28rpx;
  color: #555555;
}

.msg__tab.is-active {
  color: #ff7a00;
  font-weight: 600;
}

.msg__tab.is-active::after {
  content: '';
  position: absolute;
  left: 50%;
  bottom: 8rpx;
  width: 40rpx;
  height: 6rpx;
  margin-left: -20rpx;
  border-radius: 3rpx;
  background-color: #ff7a00;
}

.msg__body {
  padding: 20rpx 24rpx 40rpx;
}

.msg__summary {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 26rpx 24rpx;
  margin-bottom: 16rpx;
}

.msg__summary-main {
  display: flex;
  align-items: baseline;
}

.msg__unread-total {
  font-size: 44rpx;
  font-weight: 600;
  color: #ff7a00;
}

.msg__unread-label {
  margin-left: 8rpx;
  font-size: 24rpx;
  color: #666666;
}

.msg__summary-types {
  display: flex;
  gap: 20rpx;
}

.msg__item {
  padding: 24rpx;
  margin-bottom: 16rpx;
}

.msg__item.is-unread {
  border-left: 6rpx solid #ff7a00;
}

.msg__item-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16rpx;
}

.msg__item-title-row {
  flex: 1;
  min-width: 0;
  display: flex;
  align-items: center;
}

.msg__dot {
  flex-shrink: 0;
  width: 12rpx;
  height: 12rpx;
  margin-right: 10rpx;
  border-radius: 50%;
  background-color: #ff4d00;
}

.msg__title {
  flex: 1;
  min-width: 0;
  font-size: 30rpx;
  color: #333333;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.msg__item.is-unread .msg__title {
  font-weight: 600;
  color: #1f1f1f;
}

.msg__time {
  flex-shrink: 0;
  font-size: 22rpx;
  color: #a0a0a0;
}

.msg__content {
  display: -webkit-box;
  margin-top: 10rpx;
  font-size: 26rpx;
  line-height: 1.6;
  color: #8a8a8a;
  overflow: hidden;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
  word-break: break-all;
}

.msg__item.is-unread .msg__content {
  color: #666666;
}

.msg__item-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 12rpx;
}

.msg__unread-text {
  font-size: 22rpx;
  color: #ff4d00;
}

.msg__tag--order {
  color: #ff7a00;
  background-color: #fff2e6;
}

.msg__tag--marketing {
  color: #d4380d;
  background-color: #fff1f0;
}

.msg__tag--system {
  color: #1677ff;
  background-color: #e6f4ff;
}

.msg__empty-icon {
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

.msg__more,
.msg__end,
.msg__retry {
  padding: 24rpx 0;
  text-align: center;
  font-size: 24rpx;
  color: #8a8a8a;
}

.msg__more,
.msg__retry {
  color: #ff7a00;
}
</style>
