<template>
  <view class="help rd-page">
    <view class="help__search">
      <text class="help__search-icon">⌕</text>
      <input
        v-model="keyword"
        class="help__search-input"
        type="text"
        confirm-type="search"
        placeholder="搜索问题关键词"
        placeholder-class="help__placeholder"
        maxlength="32"
        @input="onInput"
        @confirm="searchNow"
      />
      <text v-if="keyword" class="help__clear" @click="clearKeyword">×</text>
    </view>

    <view v-if="loading && faqs.length === 0" class="rd-loading">正在加载帮助内容…</view>

    <view v-else-if="error && faqs.length === 0" class="rd-error">
      <text class="rd-error__msg">{{ error }}</text>
      <view class="rd-error__retry" @click="load">重新加载</view>
    </view>

    <view v-else-if="faqs.length === 0" class="rd-empty">
      <text class="rd-empty__title">{{ emptyTitle }}</text>
      <text>换个关键词试试，或前往「联系客服」</text>
    </view>

    <view v-else>
      <view v-if="error" class="help__error">
        <text>{{ error }}</text>
        <text class="help__error-retry" @click="load">重试</text>
      </view>

      <view v-for="group in groups" :key="group.category" class="rd-card help__group">
        <view class="help__group-head" @click="toggleGroup(group.category)">
          <view class="help__group-title-wrap">
            <text class="help__group-title">{{ group.category }}</text>
            <text class="help__group-count">{{ group.items.length }} 条</text>
          </view>
          <text class="help__toggle">{{ isGroupOpen(group.category) ? '−' : '+' }}</text>
        </view>

        <view v-if="isGroupOpen(group.category)">
          <view v-for="faq in group.items" :key="faq.id" class="help__item" @click="openFaq(faq)">
            <text class="help__question">{{ faq.question }}</text>
            <text class="help__arrow">›</text>
          </view>
        </view>
      </view>

      <view class="help__foot" @click="goContact">
        <text>没有找到答案？</text>
        <text class="help__foot-link">联系客服 ›</text>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { onLoad, onPullDownRefresh, onUnload } from '@dcloudio/uni-app'
import { fetchFaqs } from '@/api/rider'
import type { FaqVO } from '@/api/types'

/**
 * 帮助中心。
 * GET /c/faqs 是白名单接口，骑手账号可直接访问，返回启用状态的 FAQ 列表。
 * 分组与折叠在前端完成；关键词搜索走服务端 LIKE，输入防抖 300ms。
 */

interface FaqGroup {
  category: string
  items: FaqVO[]
}

const SEARCH_DEBOUNCE_MS = 300
const HELP_SELECTED_KEY = 'rider:help:selected'

const keyword = ref('')
const faqs = ref<FaqVO[]>([])
const loading = ref(false)
const error = ref('')

const groupOpen = reactive<Record<string, boolean>>({})

let debounceTimer: ReturnType<typeof setTimeout> | null = null
let loadSequence = 0

const groups = computed<FaqGroup[]>(() => {
  const result: FaqGroup[] = []
  const index = new Map<string, FaqGroup>()
  faqs.value.forEach((faq) => {
    const category = (faq.category || '其他').trim() || '其他'
    let group = index.get(category)
    if (!group) {
      group = { category, items: [] }
      index.set(category, group)
      result.push(group)
    }
    group.items.push(faq)
  })
  return result
})

const emptyTitle = computed(() =>
  keyword.value.trim()
    ? `没有找到与「${keyword.value.trim()}」相关的问题`
    : '暂无常见问题',
)

onLoad(() => {
  void load()
})

onPullDownRefresh(() => {
  void (async () => {
    await load()
    uni.stopPullDownRefresh()
  })()
})

onUnload(() => {
  if (debounceTimer) {
    clearTimeout(debounceTimer)
    debounceTimer = null
  }
})

function onInput(event: { detail: { value: string } }): void {
  keyword.value = event?.detail?.value ?? ''
  scheduleSearch()
}

function scheduleSearch(): void {
  if (debounceTimer) {
    clearTimeout(debounceTimer)
  }
  debounceTimer = setTimeout(() => {
    debounceTimer = null
    void load()
  }, SEARCH_DEBOUNCE_MS)
}

function searchNow(): void {
  if (debounceTimer) {
    clearTimeout(debounceTimer)
    debounceTimer = null
  }
  void load()
}

function clearKeyword(): void {
  keyword.value = ''
  searchNow()
}

async function load(): Promise<void> {
  const sequence = ++loadSequence
  loading.value = true
  error.value = ''
  try {
    const list = await fetchFaqs(keyword.value.trim())
    if (sequence !== loadSequence) {
      return
    }
    faqs.value = Array.isArray(list) ? list : []
    // 默认展开第一个分类，避免首屏只有一列分类标题
    if (faqs.value.length > 0) {
      const first = (faqs.value[0].category || '其他').trim() || '其他'
      if (groupOpen[first] === undefined) {
        groupOpen[first] = true
      }
    }
  } catch (err) {
    if (sequence !== loadSequence) {
      return
    }
    error.value = (err as Error)?.message || '帮助内容加载失败'
    faqs.value = []
  } finally {
    if (sequence === loadSequence) {
      loading.value = false
    }
  }
}

function isGroupOpen(category: string): boolean {
  return groupOpen[category] !== false
}

function toggleGroup(category: string): void {
  groupOpen[category] = !isGroupOpen(category)
}

function openFaq(faq: FaqVO): void {
  // 详情页优先用列表快照显示，避免再次请求；快照缺失时详情页会回查 /c/faqs
  uni.setStorageSync(HELP_SELECTED_KEY, faq)
  uni.navigateTo({ url: `/pages/help/HelpDetail?id=${faq.id}` })
}

function goContact(): void {
  uni.navigateTo({ url: '/pages/help/Contact' })
}
</script>

<style scoped>
.help {
  padding-bottom: 60rpx;
}

.help__search {
  display: flex;
  align-items: center;
  gap: 12rpx;
  padding: 0 24rpx;
  height: 84rpx;
  border-radius: 999rpx;
  background-color: #ffffff;
  box-shadow: 0 4rpx 16rpx rgba(20, 35, 58, 0.06);
}

.help__search-icon {
  font-size: 32rpx;
  color: #a9b4c4;
}

.help__search-input {
  flex: 1;
  height: 84rpx;
  font-size: 27rpx;
  color: #16233a;
}

.help__placeholder {
  color: #a9b4c4;
}

.help__clear {
  padding: 0 8rpx;
  font-size: 34rpx;
  color: #a9b4c4;
}

.help__group {
  margin-top: 20rpx;
  padding: 4rpx 24rpx 12rpx;
}

.help__group-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 96rpx;
}

.help__group-title-wrap {
  display: flex;
  align-items: baseline;
  gap: 12rpx;
}

.help__group-title {
  font-size: 29rpx;
  font-weight: 700;
  color: #16233a;
}

.help__group-count {
  font-size: 21rpx;
  color: #a9b4c4;
}

.help__toggle {
  font-size: 34rpx;
  color: #c3ccd9;
}

.help__item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20rpx;
  min-height: 88rpx;
  padding: 8rpx 0;
  border-top: 1rpx solid #edf1f6;
}

.help__question {
  flex: 1;
  font-size: 26rpx;
  color: #33455f;
  line-height: 1.5;
}

.help__arrow {
  font-size: 32rpx;
  color: #c3ccd9;
}

.help__error {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 18rpx;
  padding: 16rpx 20rpx;
  border-radius: 12rpx;
  background-color: #fff1f0;
  color: #b42318;
  font-size: 24rpx;
}

.help__error-retry {
  font-weight: 700;
  text-decoration: underline;
}

.help__foot {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10rpx;
  margin-top: 28rpx;
  font-size: 23rpx;
  color: #8d99aa;
}

.help__foot-link {
  color: #e85d04;
  font-weight: 600;
}
</style>
