<template>
  <view class="cal rd-page">
    <view class="rd-card cal__head">
      <view class="cal__month-nav">
        <view class="cal__nav" @click="changeMonth(-1)">‹</view>
        <text class="cal__month">{{ monthLabel }}</text>
        <view class="cal__nav" :class="{ 'is-disabled': !canNext }" @click="changeMonth(1)">›</view>
      </view>

      <view class="cal__summary">
        <view class="cal__summary-item">
          <text class="cal__summary-value">{{ monthHours }}</text>
          <text class="cal__summary-label">本月在线（小时）</text>
        </view>
        <view class="cal__summary-divider" />
        <view class="cal__summary-item">
          <text class="cal__summary-value">{{ monthTimes }}</text>
          <text class="cal__summary-label">上线次数</text>
        </view>
        <view class="cal__summary-divider" />
        <view class="cal__summary-item">
          <text class="cal__summary-value">{{ activeDays }}</text>
          <text class="cal__summary-label">有记录天数</text>
        </view>
      </view>
    </view>

    <view v-if="error" class="cal__error">
      <text>{{ error }}</text>
      <text class="cal__error-retry" @click="load()">重试</text>
    </view>

    <view class="rd-card cal__grid">
      <view class="cal__week">
        <text v-for="week in WEEKDAYS" :key="week" class="cal__week-cell">{{ week }}</text>
      </view>

      <view class="cal__cells">
        <view
          v-for="cell in cells"
          :key="cell.key"
          class="cal__cell"
          :class="{
            'is-blank': !cell.day,
            'has-data': cell.minutes > 0,
            'is-today': cell.isToday,
          }"
        >
          <text v-if="cell.day" class="cal__day">{{ cell.day }}</text>
          <text v-if="cell.day && cell.minutes > 0" class="cal__duration">
            {{ shortDuration(cell.minutes) }}
          </text>
          <text v-else-if="cell.day && cell.times > 0" class="cal__duration">0分</text>
        </view>
      </view>

      <view v-if="loading" class="cal__loading">正在加载考勤…</view>
      <view v-else-if="rows.length === 0" class="cal__empty">本月暂无上线记录</view>
    </view>

    <view class="cal__legend">
      <view class="cal__legend-item">
        <view class="cal__legend-dot cal__legend-dot--data" />
        <text>有在线记录</text>
      </view>
      <view class="cal__legend-item">
        <view class="cal__legend-dot cal__legend-dot--today" />
        <text>今天</text>
      </view>
    </view>

    <view class="cal__note">
      在线时长为当日首条上线与末条上线的差值估算，不扣除中途下线空档。
    </view>

    <view class="cal__actions">
      <button class="rd-btn rd-btn--ghost" @click="goAttendance">查看考勤明细</button>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { onPullDownRefresh, onShow } from '@dcloudio/uni-app'
import { fetchAttendance } from '@/api/rider'
import type { RiderAttendanceDayVO } from '@/api/types'
import { useAuthStore } from '@/stores/auth'
import { todayString } from '@/utils/date'

/**
 * 考勤日历。
 * GET /rider/attendance 只返回有上线记录的日期，没有记录的日期在网格中留空。
 * onlineMinutes 是首末上线差值估算，页面不做二次扣减。
 */

const WEEKDAYS = ['日', '一', '二', '三', '四', '五', '六']

interface CalendarCell {
  key: string
  day: number | null
  times: number
  minutes: number
  isToday: boolean
}

const auth = useAuthStore()
const today = todayString()

const cursor = ref(new Date(new Date().getFullYear(), new Date().getMonth(), 1))
const rows = ref<RiderAttendanceDayVO[]>([])
const loading = ref(false)
const error = ref('')

const monthLabel = computed(() => `${cursor.value.getFullYear()}年${cursor.value.getMonth() + 1}月`)

const canNext = computed(() => {
  const now = new Date()
  const current = new Date(now.getFullYear(), now.getMonth(), 1)
  return cursor.value.getTime() < current.getTime()
})

const rangeFrom = computed(() => `${year()}-${pad(cursor.value.getMonth() + 1)}-01`)

const rangeTo = computed(() => {
  const days = new Date(cursor.value.getFullYear(), cursor.value.getMonth() + 1, 0).getDate()
  return `${year()}-${pad(cursor.value.getMonth() + 1)}-${pad(days)}`
})

const attendanceByDate = computed<Record<string, RiderAttendanceDayVO>>(() => {
  const map: Record<string, RiderAttendanceDayVO> = {}
  rows.value.forEach((row) => {
    if (row && row.date) {
      map[row.date] = row
    }
  })
  return map
})

const cells = computed<CalendarCell[]>(() => {
  const yearValue = cursor.value.getFullYear()
  const monthValue = cursor.value.getMonth()
  const leading = new Date(yearValue, monthValue, 1).getDay()
  const days = new Date(yearValue, monthValue + 1, 0).getDate()
  const list: CalendarCell[] = []

  for (let i = 0; i < leading; i += 1) {
    list.push({ key: `blank-${i}`, day: null, times: 0, minutes: 0, isToday: false })
  }

  for (let day = 1; day <= days; day += 1) {
    const date = `${yearValue}-${pad(monthValue + 1)}-${pad(day)}`
    const row = attendanceByDate.value[date]
    list.push({
      key: date,
      day,
      times: Math.max(0, Math.trunc(Number(row?.onlineTimes ?? 0))),
      minutes: Math.max(0, Math.trunc(Number(row?.onlineMinutes ?? 0))),
      isToday: date === today,
    })
  }

  return list
})

const monthMinutes = computed(() =>
  rows.value.reduce((total, row) => total + Math.max(0, Math.trunc(Number(row?.onlineMinutes ?? 0))), 0),
)

const monthTimes = computed(() =>
  rows.value.reduce((total, row) => total + Math.max(0, Math.trunc(Number(row?.onlineTimes ?? 0))), 0),
)

const monthHours = computed(() => (monthMinutes.value / 60).toFixed(1))

const activeDays = computed(
  () => rows.value.filter((row) => Number(row?.onlineMinutes ?? 0) > 0).length,
)

onShow(() => {
  if (!auth.ensureLogin('/pages/schedule/Calendar')) {
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

function year(): number {
  return cursor.value.getFullYear()
}

function pad(value: number): string {
  return String(value).padStart(2, '0')
}

function shortDuration(minutes: number): string {
  const total = Math.max(0, Math.trunc(minutes))
  if (total < 60) {
    return `${total}分`
  }
  return `${(total / 60).toFixed(1)}h`
}

function changeMonth(delta: number): void {
  if (delta > 0 && !canNext.value) {
    return
  }
  cursor.value = new Date(cursor.value.getFullYear(), cursor.value.getMonth() + delta, 1)
  // 换月后日历与数据必须同步，先清空再加载；同月刷新失败则保留旧数据
  rows.value = []
  error.value = ''
  void load()
}

async function load(): Promise<void> {
  if (loading.value) {
    return
  }
  loading.value = true
  error.value = ''
  try {
    const data = await fetchAttendance({ dateFrom: rangeFrom.value, dateTo: rangeTo.value })
    rows.value = Array.isArray(data) ? data.filter((row) => !!row?.date) : []
  } catch (err) {
    error.value = (err as Error)?.message || '考勤数据加载失败'
  } finally {
    loading.value = false
  }
}

function goAttendance(): void {
  uni.navigateTo({ url: '/pages/schedule/Attendance' })
}
</script>

<style scoped>
.cal {
  padding-bottom: 60rpx;
}

.cal__head {
  padding: 20rpx 24rpx 24rpx;
}

.cal__month-nav {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.cal__month {
  font-size: 32rpx;
  font-weight: 700;
  color: #16233a;
}

.cal__nav {
  width: 64rpx;
  height: 64rpx;
  line-height: 64rpx;
  text-align: center;
  border-radius: 50%;
  background-color: #f1f4f8;
  color: #33455f;
  font-size: 40rpx;
}

.cal__nav.is-disabled {
  opacity: 0.35;
}

.cal__summary {
  display: flex;
  align-items: center;
  margin-top: 20rpx;
  padding-top: 20rpx;
  border-top: 1rpx solid #edf1f6;
}

.cal__summary-item {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6rpx;
}

.cal__summary-value {
  font-size: 36rpx;
  font-weight: 700;
  color: #16233a;
  font-variant-numeric: tabular-nums;
}

.cal__summary-label {
  font-size: 21rpx;
  color: #7a8699;
}

.cal__summary-divider {
  width: 1rpx;
  height: 52rpx;
  background-color: #edf1f6;
}

.cal__error {
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

.cal__error-retry {
  font-weight: 700;
  text-decoration: underline;
}

.cal__grid {
  margin-top: 20rpx;
  padding: 20rpx 16rpx 24rpx;
}

.cal__week {
  display: flex;
}

.cal__week-cell {
  flex: 1;
  text-align: center;
  font-size: 22rpx;
  color: #8d99aa;
}

.cal__cells {
  display: flex;
  flex-wrap: wrap;
  margin-top: 12rpx;
}

.cal__cell {
  width: 14.2857%;
  height: 96rpx;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 2rpx;
  border-radius: 12rpx;
}

.cal__cell.is-blank {
  background: transparent;
}

.cal__cell.has-data {
  background-color: #fff1e6;
}

.cal__cell.is-today {
  border: 2rpx solid #ff7a00;
}

.cal__day {
  font-size: 24rpx;
  color: #33455f;
  font-variant-numeric: tabular-nums;
}

.cal__cell.has-data .cal__day {
  color: #c2410c;
  font-weight: 600;
}

.cal__duration {
  font-size: 18rpx;
  color: #e85d04;
  font-variant-numeric: tabular-nums;
}

.cal__loading,
.cal__empty {
  padding: 32rpx 0 8rpx;
  text-align: center;
  font-size: 24rpx;
  color: #8d99aa;
}

.cal__legend {
  display: flex;
  gap: 32rpx;
  justify-content: center;
  margin-top: 20rpx;
}

.cal__legend-item {
  display: flex;
  align-items: center;
  gap: 8rpx;
  font-size: 21rpx;
  color: #8d99aa;
}

.cal__legend-dot {
  width: 20rpx;
  height: 20rpx;
  border-radius: 6rpx;
}

.cal__legend-dot--data {
  background-color: #fff1e6;
  border: 2rpx solid #ffb27a;
}

.cal__legend-dot--today {
  background-color: #ffffff;
  border: 2rpx solid #ff7a00;
}

.cal__note {
  margin-top: 24rpx;
  padding: 0 8rpx;
  font-size: 21rpx;
  color: #a9b4c4;
  line-height: 1.7;
}

.cal__actions {
  margin-top: 28rpx;
}
</style>
