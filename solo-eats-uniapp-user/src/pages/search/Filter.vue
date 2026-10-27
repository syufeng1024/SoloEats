<template>
  <view class="filter">
    <view class="filter__section">
      <view class="filter__section-head">
        <text class="filter__section-title">距离范围</text>
        <text class="filter__hint">radius 单位为米，后端允许 100~50000</text>
      </view>
      <view class="filter__options">
        <view
          v-for="option in DISTANCE_OPTIONS"
          :key="option.label"
          class="filter__option"
          :class="{ 'is-active': radius === option.value }"
          @click="radius = option.value"
        >
          {{ option.label }}
        </view>
      </view>
    </view>

    <view class="filter__section">
      <view class="filter__section-head">
        <text class="filter__section-title">排序方式</text>
      </view>
      <view class="filter__options">
        <view
          v-for="option in SORT_OPTIONS"
          :key="option.value"
          class="filter__option"
          :class="{ 'is-active': sortBy === option.value }"
          @click="sortBy = option.value"
        >
          {{ option.label }}
        </view>
      </view>
    </view>

    <view class="filter__note">
      <text>排序与距离由 GET /c/shops 的 sortBy、radius 参数决定，筛选结果在返回后重新查询第一页。</text>
    </view>

    <view class="se-fixed-bottom filter__bar">
      <view class="se-btn se-btn-plain filter__reset" @click="reset">重置</view>
      <view class="se-btn se-btn-primary filter__confirm" @click="confirm">确定</view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import type { ShopSortBy } from '@/api/search'

interface FilterPayload {
  radius?: number
  sortBy: ShopSortBy
}

interface Option<T> {
  label: string
  value: T
}

const FILTER_EVENT = 'search:filter:apply'

const DISTANCE_OPTIONS: Array<Option<number | null>> = [
  { label: '不限', value: null },
  { label: '1km', value: 1000 },
  { label: '3km', value: 3000 },
  { label: '5km', value: 5000 },
]

const SORT_OPTIONS: Array<Option<ShopSortBy>> = [
  { label: '综合排序', value: 'comprehensive' },
  { label: '距离最近', value: 'distance' },
  { label: '评分最高', value: 'rating' },
  { label: '销量最高', value: 'sales' },
]

const radius = ref<number | null>(null)
const sortBy = ref<ShopSortBy>('comprehensive')

onLoad((query) => {
  const radiusValue = Number(query?.radius)
  radius.value =
    Number.isFinite(radiusValue) && radiusValue >= 100 && radiusValue <= 50_000
      ? radiusValue
      : null
  const sortValue = typeof query?.sortBy === 'string' ? query.sortBy : ''
  sortBy.value = SORT_OPTIONS.some((option) => option.value === sortValue)
    ? (sortValue as ShopSortBy)
    : 'comprehensive'
})

function reset(): void {
  radius.value = null
  sortBy.value = 'comprehensive'
}

/**
 * 结果页通过 uni.$emit 接收筛选条件（在 Result 的 onLoad 里 uni.$on，onUnload 里 uni.$off）。
 * 选它而不用 URL 回跳，是为了避免 Result 被重建、丢掉已加载的分页数据与滚动位置。
 */
function confirm(): void {
  const payload: FilterPayload = { sortBy: sortBy.value }
  if (radius.value !== null) {
    payload.radius = radius.value
  }
  uni.$emit(FILTER_EVENT, payload)
  uni.navigateBack()
}
</script>

<style scoped>
.filter {
  min-height: 100vh;
  padding: 24rpx 24rpx 180rpx;
  background-color: #f5f5f7;
}

.filter__section {
  padding: 24rpx;
  margin-bottom: 20rpx;
  border-radius: 8rpx;
  background-color: #ffffff;
  box-shadow: 0 2rpx 8rpx rgba(20, 20, 20, 0.04);
}

.filter__section-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  margin-bottom: 20rpx;
}

.filter__section-title {
  font-size: 30rpx;
  font-weight: 600;
  color: #1f1f1f;
}

.filter__hint {
  font-size: 20rpx;
  color: #a0a0a0;
}

.filter__options {
  display: flex;
  flex-wrap: wrap;
  gap: 16rpx;
}

.filter__option {
  padding: 14rpx 32rpx;
  border-radius: 8rpx;
  border: 1rpx solid #e5e5e8;
  background-color: #fafafa;
  color: #555555;
  font-size: 26rpx;
}

.filter__option.is-active {
  border-color: #ff7a00;
  background-color: #fff2e6;
  color: #ff7a00;
  font-weight: 500;
}

.filter__note {
  padding: 4rpx 8rpx;
  color: #a0a0a0;
  font-size: 22rpx;
  line-height: 1.6;
}

.filter__bar {
  display: flex;
  gap: 20rpx;
}

.filter__reset {
  width: 200rpx;
}

.filter__confirm {
  flex: 1;
}
</style>
