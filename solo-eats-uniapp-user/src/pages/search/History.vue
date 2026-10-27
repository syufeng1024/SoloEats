<template>
  <view class="history">
    <view v-if="list.length > 0" class="history__head">
      <text class="se-muted">共 {{ list.length }} 条，最多保留 10 条</text>
      <text class="history__clear" @click="clearAll">清空全部</text>
    </view>

    <view v-if="list.length === 0" class="se-empty history__empty">
      <view class="history__empty-icon">◷</view>
      <text class="se-empty__title">暂无搜索历史</text>
      <text>搜索过的关键词会自动保存在这里</text>
      <view class="se-error__retry history__go" @click="goSearch">去搜索</view>
    </view>

    <view v-else class="se-card history__list">
      <view v-for="item in list" :key="item" class="history__item">
        <view class="history__keyword" @click="search(item)">
          <text class="history__icon">⌕</text>
          <text class="history__text">{{ item }}</text>
        </view>
        <text class="history__delete" @click.stop="remove(item)">删除</text>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import {
  clearSearchHistory,
  readSearchHistory,
  removeSearchHistory,
} from '@/utils/searchHistory'

const list = ref<string[]>([])

onShow(() => {
  list.value = readSearchHistory()
})

function search(keyword: string): void {
  uni.navigateTo({ url: `/pages/search/Result?keyword=${encodeURIComponent(keyword)}` })
}

function remove(keyword: string): void {
  list.value = removeSearchHistory(keyword)
  uni.showToast({ title: '已删除', icon: 'none' })
}

function clearAll(): void {
  uni.showModal({
    title: '清空搜索历史',
    content: '清空后不可恢复，确定继续？',
    confirmColor: '#ff7a00',
    success: (result) => {
      if (!result.confirm) {
        return
      }
      list.value = clearSearchHistory()
      uni.showToast({ title: '已清空', icon: 'none' })
    },
  })
}

function goSearch(): void {
  // 从搜索页进入时直接返回；独立打开（无上一页）则重定向到搜索页
  if (getCurrentPages().length > 1) {
    uni.navigateBack()
  } else {
    uni.redirectTo({ url: '/pages/search/Search' })
  }
}
</script>

<style scoped>
.history {
  min-height: 100vh;
  padding: 24rpx;
  background-color: #f5f5f7;
}

.history__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 8rpx 16rpx;
}

.history__clear {
  font-size: 24rpx;
  color: #ff7a00;
}

.history__empty {
  padding-top: 160rpx;
}

.history__empty-icon {
  width: 120rpx;
  height: 120rpx;
  line-height: 120rpx;
  margin: 0 auto 20rpx;
  border-radius: 50%;
  background-color: #ffffff;
  color: #d0d0d0;
  font-size: 52rpx;
  text-align: center;
}

.history__go {
  margin-top: 24rpx;
}

.history__list {
  padding: 0 24rpx;
}

.history__item {
  display: flex;
  align-items: center;
  border-bottom: 1rpx solid #f5f5f5;
}

.history__item:last-child {
  border-bottom: none;
}

.history__keyword {
  flex: 1;
  min-width: 0;
  display: flex;
  align-items: center;
  padding: 28rpx 0;
}

.history__icon {
  margin-right: 16rpx;
  color: #b0b0b0;
  font-size: 26rpx;
}

.history__text {
  flex: 1;
  min-width: 0;
  font-size: 28rpx;
  color: #444444;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.history__delete {
  flex-shrink: 0;
  padding: 16rpx 0 16rpx 32rpx;
  font-size: 24rpx;
  color: #999999;
}
</style>
