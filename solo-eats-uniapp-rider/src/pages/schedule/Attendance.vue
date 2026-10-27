<template>
  <view class="att rd-page">
    <view class="rd-card att__filters">
      <view class="att__filter-row">
        <text class="att__filter-label">开始日期</text>
        <picker mode="date" :value="dateFrom" :end="dateTo" @change="onDateFromChange">
          <view class="att__filter-value">{{ dateFrom }}</view>
        </picker>
      </view>
      <view class="rd-divider" />
      <view class="att__filter-row">
        <text class="att__filter-label">结束日期</text>
        <picker mode="date" :value="dateTo" :start="dateFrom" :end="today" @change="onDateToChange">
          <view class="att__filter-value">{{ dateTo }}</view>
        </picker>
      </view>
    </view>

    <view class="rd-card att__summary">
      <view class="att__summary-item">
        <text class="att__summary-value">{{ totalHours }}</text>
        <text class="att__summary-label">在线小时</text>
      </view>
      <view class="att__summary-divider" />
      <view class="att__summary-item">
        <text class="att__summary-value">{{ totalTimes }}</text>
        <text class="att__summary-label">上线次数</text>
      </view>
      <view class="att__summary-divider" />
      <view class="att__summary-item">
        <text class="att__summary-value">{{ rows.length }}</text>
        <text class="att__summary-label">有记录天数</text>
      </view>
    </view>

    <view v-if="error" class="att__error">
      <text>{{ error }}</text>
      <text class="att__error-retry" @click="load()">重试</text>
    </view>

    <view v-if="loading && rows.length === 0" class="rd-loading">正在加载考勤明细…</view>

    <view v-else-if="!loading && rows.length === 0 && !error" class="rd-empty">
      <text class="rd-empty__title">该区间暂无上线记录</text>
      <text>调整日期范围，或先在上线接单后查看</text>
    </view>

    <view v-else class="att__list">
      <view v-for="row in displayRows" :key="row.date" class="rd-card att__row">
        <view class="att__row-head rd-between">
          <text class="att__date">{{ row.date }}</text>
          <text class="att__duration">{{ formatMinutes(row.onlineMinutes) }}</text>
        </view>
        <view class="att__row-meta">
          <text>上线 {{ row.onlineTimes ?? 0 }} 次</text>
          <text>{{ weekdayLabel(row.date) }}</text>
        </view>
      </view>

      <view v-if="hasMore" class="att__more" @click="showMore">
        {{ loading ? '加载中…' : '加载更多' }}
      </view>
      <view v-else class="att__end">已显示全部 {{ rows.length }} 天</view>
    </view>

    <view class="att__note">
      时长按当日首条上线与末条上线的时间差估算，中途下线空档未扣除，与结算口径无关。
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { onPullDownRefresh, onReachBottom, onShow } from '@dcloudio/uni-app'
import { fetchAttendance } from '@/api/rider'
import type { RiderAttendanceDayVO } from '@/api/types'
import { useAuthStore } from '@/stores/auth'
import { todayString } from '@/utils/date'

/**
 * 考勤明细：GET /rider/attendance 返回区间内「有记录」的日期数组，无服务端分页。
 * 这里按日期倒序后前端分批展示，滚动到底继续放出，避免一次渲染过长列表。
 */

const PAGE_STEP = 15
const WEEKDAY_NAMES = ['周日', '周一', '周二', '周三', '周四', '周五', '周六']

const auth = useAuthStore()
const today = todayString()

const dateFrom = ref(shiftDate(today, -6))
const dateTo = ref(today)

const rows = ref<RiderAttendanceDayVO[]>([])
const loading = ref(false)
const error = ref('')
const visibleCount = ref(PAGE_STEP)

const sortedRows = computed(() =>
  [...rows.value].sort((left, right) => String(right?.date ?? '').localeCompare(String(left?.date ?? ''))),
)

const displayRows = computed(() => sortedRows.value.slice(0, visibleCount.value))
const hasMore = computed(() => visibleCount.value < sortedRows.value.length)

const totalMinutes = computed(() =>
  rows.value.reduce((total, row) => total + Math.max(0, Math.trunc(Number(row?.onlineMinutes ?? 0))), 0),
)

const totalHours = computed(() => (totalMinutes.value / 60).toFixed(1))

const totalTimes = computed(() =>
  rows.value.reduce((total, row) => total + Math.max(0, Math.trunc(Number(row?.onlineTimes ?? 0))), 0),
)

onShow(() => {
  if (!auth.ensureLogin('/pages/schedule/Attendance')) {
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

onReachBottom(() => {
  showMore()
})

function shiftDate(base: string, days: number): string {
  const parsed = new Date(`${base}T00:00:00`)
  if (Number.isNaN(parsed.getTime())) {
    return base
  }
  parsed.setDate(parsed.getDate() + days)
  const month = String(parsed.getMonth() + 1).padStart(2, '0')
  const day = String(parsed.getDate()).padStart(2, '0')
  return `${parsed.getFullYear()}-${month}-${day}`
}

function formatMinutes(minutes: number | null): string {
  const total = Math.max(0, Math.trunc(Number(minutes ?? 0)))
  if (total <= 0) {
    return '0 分钟'
  }
  const hours = Math.floor(total / 60)
  const rest = total % 60
  if (hours === 0) {
    return `${rest} 分钟`
  }
  return rest === 0 ? `${hours} 小时` : `${hours} 小时 ${rest} 分`
}

function weekdayLabel(date: string): string {
  const parsed = new Date(`${date}T00:00:00`)
  return Number.isNaN(parsed.getTime()) ? '' : WEEKDAY_NAMES[parsed.getDay()]
}

function onDateFromChange(event: { detail: { value: string } }): void {
  const value = event?.detail?.value
  if (!value) {
    return
  }
  dateFrom.value = value
  if (dateFrom.value > dateTo.value) {
    dateTo.value = dateFrom.value
  }
  visibleCount.value = PAGE_STEP
  rows.value = []
  void load()
}

function onDateToChange(event: { detail: { value: string } }): void {
  const value = event?.detail?.value
  if (!value) {
    return
  }
  dateTo.value = value
  if (dateTo.value < dateFrom.value) {
    dateFrom.value = dateTo.value
  }
  visibleCount.value = PAGE_STEP
  rows.value = []
  void load()
}

function showMore(): void {
  if (!hasMore.value) {
    return
  }
  visibleCount.value += PAGE_STEP
}

async function load(): Promise<void> {
  if (loading.value) {
    return
  }
  loading.value = true
  error.value = ''
  try {
    const data = await fetchAttendance({ dateFrom: dateFrom.value, dateTo: dateTo.value })
    rows.value = Array.isArray(data) ? data.filter((row) => !!row?.date) : []
    visibleCount.value = PAGE_STEP
  } catch (err) {
    // 失败保留已有列表，只提示错误，避免区间数据被清空后无法对照
    error.value = (err as Error)?.message || '考勤明细加载失败'
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.att {
  padding-bottom: 60rpx;
}

.att__filters {
  padding: 4rpx 24rpx;
}

.att__filter-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 96rpx;
}

.att__filter-label {
  font-size: 26rpx;
  color: #33455f;
}

.att__filter-value {
  padding: 10rpx 20rpx;
  border-radius: 10rpx;
  background-color: #f1f4f8;
  font-size: 24rpx;
  color: #16233a;
  font-variant-numeric: tabular-nums;
}

.att__summary {
  display: flex;
  align-items: center;
  margin-top: 20rpx;
  padding: 24rpx 12rpx;
}

.att__summary-item {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6rpx;
}

.att__summary-value {
  font-size: 34rpx;
  font-weight: 700;
  color: #16233a;
  font-variant-numeric: tabular-nums;
}

.att__summary-label {
  font-size: 21rpx;
  color: #7a8699;
}

.att__summary-divider {
  width: 1rpx;
  height: 52rpx;
  background-color: #edf1f6;
}

.att__error {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 20rpx;
  padding: 16rpx 20rpx;
  border-radius: 12rpx;
  background-color: #fff1f0;
  color: #b42318;
  font-size: 24rpx;
}

.att__error-retry {
  font-weight: 700;
  text-decoration: underline;
}

.att__list {
  margin-top: 20rpx;
}

.att__row {
  margin-bottom: 16rpx;
  padding: 24rpx;
}

.att__date {
  font-size: 30rpx;
  font-weight: 700;
  color: #16233a;
  font-variant-numeric: tabular-nums;
}

.att__duration {
  font-size: 28rpx;
  font-weight: 600;
  color: #e85d04;
}

.att__row-meta {
  display: flex;
  justify-content: space-between;
  margin-top: 10rpx;
  font-size: 22rpx;
  color: #8d99aa;
}

.att__more,
.att__end {
  padding: 24rpx 0;
  text-align: center;
  font-size: 24rpx;
  color: #8d99aa;
}

.att__more {
  color: #e85d04;
  font-weight: 600;
}

.att__note {
  margin-top: 20rpx;
  padding: 0 8rpx;
  font-size: 21rpx;
  color: #a9b4c4;
  line-height: 1.7;
}
</style>
