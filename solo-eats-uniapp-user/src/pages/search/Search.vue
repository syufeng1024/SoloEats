<template>
  <view class="search">
    <view class="search__head">
      <view class="search__box">
        <text class="search__icon">⌕</text>
        <input
          v-model="keyword"
          class="search__input"
          type="text"
          confirm-type="search"
          placeholder="搜索店铺、商品"
          placeholder-class="search__placeholder"
          :focus="inputFocus"
          maxlength="32"
          @input="onInput"
          @confirm="submit"
        />
        <text v-if="keyword" class="search__clear" @click="clearKeyword">×</text>
      </view>
      <text class="search__submit" @click="submit">搜索</text>
    </view>

    <!-- 有输入时展示联想词，点击直接搜索；无输入时展示热搜与历史 -->
    <view v-if="keyword.trim()" class="search__panel">
      <view v-if="suggestLoading" class="se-loading search__suggest-loading">正在联想…</view>

      <view v-else-if="suggestions.length > 0" class="se-card search__suggest">
        <view
          v-for="item in suggestions"
          :key="item"
          class="search__suggest-item"
          @click="doSearch(item)"
        >
          <text class="search__suggest-icon">⌕</text>
          <text class="search__suggest-text">{{ item }}</text>
        </view>
      </view>

      <view v-else-if="suggestError" class="se-empty">
        <text class="se-empty__title">联想词加载失败</text>
        <text>{{ suggestError }}</text>
        <view class="se-error__retry" @click="loadSuggest(keyword)">重新加载</view>
      </view>

      <view v-else class="se-empty search__suggest-empty">
        <text>没有匹配的联想词，按「搜索」直接查找店铺</text>
      </view>
    </view>

    <view v-else class="search__panel">
      <view v-if="hotKeywords.length > 0" class="search__section">
        <view class="search__section-head">
          <text class="search__section-title">热门搜索</text>
        </view>
        <view class="search__chips">
          <text
            v-for="item in hotKeywords"
            :key="item"
            class="search__chip"
            @click="doSearch(item)"
          >
            {{ item }}
          </text>
        </view>
      </view>

      <view class="search__section">
        <view class="search__section-head">
          <text class="search__section-title">搜索历史</text>
          <view v-if="history.length > 0" class="search__section-actions">
            <text class="search__link" @click="goHistory">管理</text>
            <text class="search__link" @click="clearHistory">清空</text>
          </view>
        </view>

        <view v-if="history.length === 0" class="search__history-empty">
          暂无搜索历史，搜个店铺试试
        </view>

        <view v-else class="se-card search__history">
          <view
            v-for="item in history"
            :key="item"
            class="search__history-item"
            @click="doSearch(item)"
          >
            <text class="search__history-icon">◷</text>
            <text class="search__history-text">{{ item }}</text>
            <text class="search__history-arrow">›</text>
          </view>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { onLoad, onUnload } from '@dcloudio/uni-app'
import { fetchSearchSuggest } from '@/api/search'
import {
  addSearchHistory,
  clearSearchHistory,
  readSearchHistory,
} from '@/utils/searchHistory'

interface InputEvent {
  detail: { value: string }
}

const SUGGEST_DEBOUNCE_MS = 300

const keyword = ref('')
const inputFocus = ref(false)
const suggestions = ref<string[]>([])
const hotKeywords = ref<string[]>([])
const suggestLoading = ref(false)
const suggestError = ref('')
const history = ref<string[]>([])

let debounceTimer: ReturnType<typeof setTimeout> | null = null
let suggestSequence = 0

onLoad(() => {
  history.value = readSearchHistory()
  inputFocus.value = true
  // 空 keyword 调用时后端只返回默认词与热搜词
  loadSuggest('')
})

onUnload(() => {
  if (debounceTimer) {
    clearTimeout(debounceTimer)
    debounceTimer = null
  }
})

function onInput(event: InputEvent): void {
  keyword.value = event?.detail?.value ?? ''
  scheduleSuggest()
}

function scheduleSuggest(): void {
  if (debounceTimer) {
    clearTimeout(debounceTimer)
  }
  const value = keyword.value.trim()
  if (!value) {
    suggestions.value = []
    suggestError.value = ''
    suggestLoading.value = false
    return
  }
  debounceTimer = setTimeout(() => {
    loadSuggest(value)
  }, SUGGEST_DEBOUNCE_MS)
}

async function loadSuggest(value: string): Promise<void> {
  const sequence = ++suggestSequence
  suggestLoading.value = true
  suggestError.value = ''
  try {
    const result = await fetchSearchSuggest(value)
    // 只接受最后一次请求的结果，避免慢请求覆盖新输入
    if (sequence !== suggestSequence) {
      return
    }
    if (value.trim()) {
      suggestions.value = Array.isArray(result.suggestions) ? result.suggestions : []
    } else {
      const merged = [...(result.hotKeywords || []), ...(result.defaultKeywords || [])]
      hotKeywords.value = Array.from(new Set(merged.filter((item) => Boolean(item))))
    }
  } catch (err) {
    if (sequence !== suggestSequence) {
      return
    }
    if (value.trim()) {
      suggestions.value = []
    }
    suggestError.value = (err as Error)?.message || '联想词加载失败'
  } finally {
    if (sequence === suggestSequence) {
      suggestLoading.value = false
    }
  }
}

function submit(): void {
  doSearch(keyword.value)
}

function doSearch(value: string): void {
  const target = (value || '').trim()
  if (!target) {
    uni.showToast({ title: '请输入搜索内容', icon: 'none' })
    return
  }
  history.value = addSearchHistory(target)
  uni.navigateTo({ url: `/pages/search/Result?keyword=${encodeURIComponent(target)}` })
}

function clearKeyword(): void {
  keyword.value = ''
  suggestions.value = []
  suggestError.value = ''
  inputFocus.value = true
}

function clearHistory(): void {
  if (history.value.length === 0) {
    return
  }
  uni.showModal({
    title: '清空搜索历史',
    content: '清空后不可恢复，确定继续？',
    confirmColor: '#ff7a00',
    success: (result) => {
      if (!result.confirm) {
        return
      }
      history.value = clearSearchHistory()
      uni.showToast({ title: '已清空', icon: 'none' })
    },
  })
}

function goHistory(): void {
  uni.navigateTo({ url: '/pages/search/History' })
}
</script>

<style scoped>
.search {
  min-height: 100vh;
  background-color: #f5f5f7;
}

.search__head {
  display: flex;
  align-items: center;
  padding: 20rpx 24rpx;
  background-color: #ffffff;
}

.search__box {
  flex: 1;
  display: flex;
  align-items: center;
  height: 72rpx;
  padding: 0 20rpx;
  border-radius: 36rpx;
  background-color: #f2f2f4;
}

.search__icon {
  margin-right: 12rpx;
  color: #ff7a00;
  font-size: 30rpx;
}

.search__input {
  flex: 1;
  height: 72rpx;
  font-size: 28rpx;
  color: #1f1f1f;
}

.search__placeholder {
  color: #b0b0b0;
  font-size: 26rpx;
}

.search__clear {
  width: 44rpx;
  height: 44rpx;
  line-height: 40rpx;
  text-align: center;
  border-radius: 50%;
  background-color: #d8d8dc;
  color: #ffffff;
  font-size: 28rpx;
}

.search__submit {
  margin-left: 20rpx;
  color: #ff7a00;
  font-size: 28rpx;
  font-weight: 500;
}

.search__panel {
  padding: 24rpx;
}

.search__suggest {
  padding: 0 24rpx;
}

.search__suggest-item {
  display: flex;
  align-items: center;
  padding: 26rpx 0;
  border-bottom: 1rpx solid #f5f5f5;
}

.search__suggest-item:last-child {
  border-bottom: none;
}

.search__suggest-icon {
  margin-right: 16rpx;
  color: #b0b0b0;
  font-size: 26rpx;
}

.search__suggest-text {
  flex: 1;
  min-width: 0;
  font-size: 28rpx;
  color: #444444;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.search__suggest-loading {
  padding: 40rpx 0;
}

.search__suggest-empty {
  padding: 60rpx 40rpx;
}

.search__section {
  margin-bottom: 36rpx;
}

.search__section-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16rpx;
}

.search__section-title {
  font-size: 30rpx;
  font-weight: 600;
  color: #1f1f1f;
}

.search__section-actions {
  display: flex;
  gap: 24rpx;
}

.search__link {
  font-size: 24rpx;
  color: #8a8a8a;
}

.search__chips {
  display: flex;
  flex-wrap: wrap;
  gap: 16rpx;
}

.search__chip {
  padding: 12rpx 26rpx;
  border-radius: 28rpx;
  background-color: #ffffff;
  color: #444444;
  font-size: 24rpx;
  box-shadow: 0 2rpx 8rpx rgba(20, 20, 20, 0.04);
}

.search__history {
  padding: 0 24rpx;
}

.search__history-item {
  display: flex;
  align-items: center;
  padding: 26rpx 0;
  border-bottom: 1rpx solid #f5f5f5;
}

.search__history-item:last-child {
  border-bottom: none;
}

.search__history-icon {
  margin-right: 16rpx;
  color: #b0b0b0;
  font-size: 26rpx;
}

.search__history-text {
  flex: 1;
  min-width: 0;
  font-size: 28rpx;
  color: #444444;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.search__history-arrow {
  color: #c8c8c8;
  font-size: 30rpx;
}

.search__history-empty {
  padding: 36rpx 24rpx;
  border-radius: 8rpx;
  background-color: #ffffff;
  color: #8a8a8a;
  font-size: 24rpx;
  text-align: center;
}
</style>
