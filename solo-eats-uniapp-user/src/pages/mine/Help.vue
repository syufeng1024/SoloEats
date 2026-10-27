<template>
  <view class="help">
    <view class="help__head">
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
    </view>

    <view class="help__body">
      <view v-if="loading && faqs.length === 0" class="se-loading">正在加载帮助内容…</view>

      <view v-else-if="error && faqs.length === 0" class="se-error">
        <text>{{ error }}</text>
        <view class="se-error__retry" @click="load">重新加载</view>
      </view>

      <view v-else-if="faqs.length === 0" class="se-empty">
        <text class="se-empty__title">{{ emptyTitle }}</text>
        <text>换个关键词试试，或通过意见反馈联系我们</text>
      </view>

      <view v-else>
        <view v-if="error" class="help__error">
          <text>{{ error }}</text>
          <text class="help__error-retry" @click="load">重试</text>
        </view>

        <view v-for="group in groups" :key="group.category" class="se-card help__group">
          <view class="help__group-head" @click="toggleGroup(group.category)">
            <view class="help__group-title-wrap">
              <text class="help__group-title">{{ group.category }}</text>
              <text class="help__group-count">{{ group.items.length }} 条</text>
            </view>
            <text class="help__toggle">{{ isGroupOpen(group.category) ? '−' : '+' }}</text>
          </view>

          <view v-if="isGroupOpen(group.category)">
            <view v-for="faq in group.items" :key="faq.id" class="help__item">
              <view class="help__question" @click="toggleFaq(faq.id)">
                <text class="help__question-text">{{ faq.question }}</text>
                <text class="help__toggle help__toggle--small">{{ isFaqOpen(faq.id) ? '−' : '+' }}</text>
              </view>
              <text v-if="isFaqOpen(faq.id)" class="help__answer">{{ faq.answer }}</text>
            </view>
          </view>
        </view>

        <view class="help__foot">没有解决你的问题？可前往「意见反馈」提交详情</view>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { onLoad, onPullDownRefresh, onUnload } from '@dcloudio/uni-app'
import { fetchFaqs } from '@/api/mine'
import type { FaqVO } from '@/api/mine'

/**
 * 帮助中心。
 *
 * GET /c/faqs 是白名单接口，未登录也能看。
 * 后端一次返回全部启用条目（上限由 PUBLIC_LIMIT 控制），所以分组与折叠都在前端做；
 * 关键词搜索走服务端 LIKE（question/answer），输入防抖 300ms。
 */

interface FaqGroup {
  category: string
  items: FaqVO[]
}

const SEARCH_DEBOUNCE_MS = 300

const keyword = ref('')
const faqs = ref<FaqVO[]>([])
const loading = ref(false)
const error = ref('')

const groupOpen = reactive<Record<string, boolean>>({})
const faqOpen = reactive<Record<number, boolean>>({})

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
  keyword.value.trim() ? `没有找到与「${keyword.value.trim()}」相关的问题` : '暂无常见问题',
)

onLoad(() => {
  load()
})

onPullDownRefresh(() => {
  load()
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
    load()
  }, SEARCH_DEBOUNCE_MS)
}

function searchNow(): void {
  if (debounceTimer) {
    clearTimeout(debounceTimer)
    debounceTimer = null
  }
  load()
}

async function load(): Promise<void> {
  // 搜索场景允许多个请求并发，用 sequence 只采纳最后一次结果，避免慢请求覆盖新关键词
  const sequence = ++loadSequence
  loading.value = true
  error.value = ''
  try {
    const result = await fetchFaqs({ keyword: keyword.value.trim() || undefined })
    if (sequence !== loadSequence) {
      return
    }
    faqs.value = Array.isArray(result) ? result : []
    // 新出现的分类默认展开；已被用户折叠的分类保持折叠
    faqs.value.forEach((faq) => {
      const category = (faq.category || '其他').trim() || '其他'
      if (groupOpen[category] === undefined) {
        groupOpen[category] = true
      }
    })
  } catch (err) {
    // 失败不清空已有 FAQ，只展示错误条
    if (sequence === loadSequence) {
      error.value = (err as Error)?.message || '帮助内容加载失败'
    }
  } finally {
    if (sequence === loadSequence) {
      loading.value = false
      uni.stopPullDownRefresh()
    }
  }
}

function clearKeyword(): void {
  keyword.value = ''
  if (debounceTimer) {
    clearTimeout(debounceTimer)
    debounceTimer = null
  }
  load()
}

function isGroupOpen(category: string): boolean {
  return groupOpen[category] !== false
}

function toggleGroup(category: string): void {
  groupOpen[category] = !isGroupOpen(category)
}

function isFaqOpen(id: number): boolean {
  return faqOpen[id] === true
}

function toggleFaq(id: number): void {
  faqOpen[id] = !isFaqOpen(id)
}
</script>

<style scoped>
.help {
  min-height: 100vh;
  background-color: #f5f5f7;
}

.help__head {
  position: sticky;
  top: 0;
  z-index: 10;
  padding: 20rpx 24rpx;
  background-color: #ffffff;
  box-shadow: 0 2rpx 8rpx rgba(20, 20, 20, 0.04);
}

.help__search {
  display: flex;
  align-items: center;
  height: 72rpx;
  padding: 0 20rpx;
  border-radius: 36rpx;
  background-color: #f2f2f4;
}

.help__search-icon {
  margin-right: 12rpx;
  color: #ff7a00;
  font-size: 30rpx;
}

.help__search-input {
  flex: 1;
  height: 72rpx;
  font-size: 28rpx;
  color: #1f1f1f;
}

.help__placeholder {
  color: #b0b0b0;
  font-size: 26rpx;
}

.help__clear {
  width: 44rpx;
  height: 44rpx;
  line-height: 40rpx;
  text-align: center;
  border-radius: 50%;
  background-color: #d8d8dc;
  color: #ffffff;
  font-size: 28rpx;
}

.help__body {
  padding: 20rpx 24rpx 40rpx;
}

.help__error {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16rpx;
  padding: 14rpx 20rpx;
  border-radius: 8rpx;
  background-color: #fff8ef;
  color: #a26a2a;
  font-size: 22rpx;
}

.help__error-retry {
  color: #ff7a00;
  margin-left: 16rpx;
}

.help__group {
  margin-bottom: 16rpx;
  overflow: hidden;
}

.help__group-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 88rpx;
  padding: 0 24rpx;
}

.help__group-title-wrap {
  display: flex;
  align-items: center;
  gap: 12rpx;
}

.help__group-title {
  font-size: 28rpx;
  font-weight: 600;
  color: #1f1f1f;
}

.help__group-count {
  color: #a0a0a0;
  font-size: 22rpx;
}

.help__toggle {
  color: #ff7a00;
  font-size: 34rpx;
  line-height: 1;
  width: 44rpx;
  text-align: center;
}

.help__toggle--small {
  font-size: 26rpx;
}

.help__item {
  border-top: 1rpx solid #f5f5f5;
}

.help__question {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  padding: 22rpx 24rpx;
  gap: 16rpx;
}

.help__question-text {
  flex: 1;
  min-width: 0;
  color: #444444;
  font-size: 26rpx;
  line-height: 1.6;
}

.help__answer {
  display: block;
  padding: 0 24rpx 24rpx;
  color: #6f6f6f;
  font-size: 24rpx;
  line-height: 1.8;
  white-space: pre-wrap;
  word-break: break-all;
}

.help__foot {
  padding: 24rpx 12rpx;
  text-align: center;
  color: #a0a0a0;
  font-size: 22rpx;
}
</style>
