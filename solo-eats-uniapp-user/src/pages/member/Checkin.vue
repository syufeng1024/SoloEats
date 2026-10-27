<template>
  <view class="checkin">
    <view v-if="error && !status" class="se-error">
      <text>{{ error }}</text>
      <view class="se-error__retry" @click="loadStatus(false)">重新加载</view>
    </view>

    <view v-if="loading && !status" class="se-loading">正在加载签到状态…</view>

    <template v-else-if="status">
      <view class="checkin__hero">
        <view class="checkin__hero-top">
          <view class="checkin__hero-main">
            <text class="checkin__hero-label">连续签到</text>
            <view class="checkin__hero-days">
              <text class="checkin__hero-num">{{ status.continuousDays }}</text>
              <text class="checkin__hero-unit">天</text>
            </view>
          </view>
          <view class="checkin__hero-side">
            <text class="checkin__hero-side-label">{{ expectedLabel }}</text>
            <text class="checkin__hero-point">+{{ expectedAward }}</text>
            <text class="checkin__hero-side-label">积分</text>
          </view>
        </view>

        <view
          class="checkin__btn"
          :class="{ 'is-disabled': status.checkedInToday || checking }"
          @click="handleCheckin"
        >
          {{ status.checkedInToday ? '今日已签到' : checking ? '签到中…' : '立即签到' }}
        </view>
      </view>

      <view v-if="error" class="checkin__error">
        <text>{{ error }}</text>
        <text class="checkin__error-retry" @click="loadStatus(false)">重试</text>
      </view>

      <view class="se-card checkin__calendar">
        <view class="se-between checkin__cal-head">
          <view class="checkin__tabs">
            <text
              class="checkin__tab"
              :class="{ 'is-active': viewMode === 'week' }"
              @click="setView('week')"
            >
              本周
            </text>
            <text
              class="checkin__tab"
              :class="{ 'is-active': viewMode === 'month' }"
              @click="setView('month')"
            >
              本月
            </text>
          </view>
          <text class="se-muted">{{ calendarTitle }}</text>
        </view>

        <view class="checkin__week-head">
          <text v-for="label in WEEK_LABELS" :key="label" class="checkin__week-label">
            {{ label }}
          </text>
        </view>

        <view class="checkin__grid">
          <view
            v-for="cell in calendarCells"
            :key="cell.key"
            class="checkin__cell"
            :class="{
              'is-today': cell.isToday,
              'is-checked': cell.checked,
              'is-blank': !cell.date,
            }"
          >
            <template v-if="cell.date">
              <text class="checkin__cell-day">{{ cell.day }}</text>
              <text v-if="cell.checked" class="checkin__cell-mark">签</text>
              <text v-else-if="cell.isToday" class="checkin__cell-today">今</text>
            </template>
          </view>
        </view>

        <text class="checkin__legend">
          已签到日期标记「签」，今天标记「今」。日历按当前连续签到区间推算，更早的记录可在积分明细中查看。
        </text>
      </view>

      <view class="se-card checkin__compensate">
        <view class="se-between checkin__comp-head">
          <text class="checkin__comp-title">补签</text>
          <text class="se-muted">
            本月已用 {{ status.monthCompensationsUsed }}/{{ status.monthCompensationLimit }}
          </text>
        </view>

        <view v-if="compRemaining <= 0" class="checkin__comp-hint">
          本月补签次数已用完（{{ status.monthCompensationLimit }} 次/月），下月恢复。
        </view>

        <template v-else>
          <view class="checkin__comp-row">
            <picker
              mode="date"
              :value="compDate || compRangeEnd"
              :start="compRangeStart"
              :end="compRangeEnd"
              @change="onDateChange"
            >
              <view class="checkin__comp-picker">
                {{ compDate || '选择补签日期' }}
              </view>
            </picker>
            <view
              class="checkin__comp-btn"
              :class="{ 'is-disabled': compensating }"
              @click="handleCompensate"
            >
              {{ compensating ? '补签中…' : '补签' }}
            </view>
          </view>
          <text class="checkin__comp-hint">
            可补签 {{ compRangeStart }} 至 {{ compRangeEnd }}；补签按该日对应的连续天数发放积分与成长值。
          </text>
        </template>
      </view>
    </template>
  </view>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { onPullDownRefresh, onShow } from '@dcloudio/uni-app'
import { checkin, fetchCheckinStatus } from '@/api/mine'
import { compensateCheckin } from '@/api/member'
import type { CheckinStatusVO } from '@/api/mine'
import { useAuthStore } from '@/stores/auth'

/**
 * 签到 / 补签。
 *
 * 后端没有「逐日签到记录」查询接口，status 只返回 lastCheckinDate 与 continuousDays。
 * 因此日历按「最后签到日向前 continuousDays 天」推算当前连续区间；连续天数为 0
 * 时只标记 lastCheckinDate 当天。本次页面内补签成功的日期会额外加入本地标记，
 * 页面刷新后这部分标记不再保留（接口无历史明细可查）。
 *
 * 今日可得积分按后端公式推算：10 + min(连续天数, 7) * 2；已签到时优先后端
 * 返回的 todayPointAward。补签窗口与每月次数直接取 status 的 compensable* 与
 * monthCompensation* 字段，不在前端另写一套规则。
 */

const WEEK_LABELS = ['一', '二', '三', '四', '五', '六', '日']
const BASE_POINT = 10
const STEP_POINT = 2
const MAX_STREAK_DAYS = 7

interface CalendarCell {
  key: string
  date: string
  day: number
  isToday: boolean
  checked: boolean
}

const auth = useAuthStore()

const status = ref<CheckinStatusVO | null>(null)
const loading = ref(false)
const error = ref('')
const checking = ref(false)
const compensating = ref(false)
const viewMode = ref<'week' | 'month'>('week')
const compDate = ref('')
const localCompensated = ref<string[]>([])

let sequence = 0

const today = computed(() => status.value?.today || localToday())

const checkedSet = computed(() => {
  const dates = new Set<string>(localCompensated.value)
  const current = status.value
  if (current?.lastCheckinDate) {
    const count = current.continuousDays > 0 ? current.continuousDays : 1
    for (let i = 0; i < count; i += 1) {
      dates.add(addDays(current.lastCheckinDate, -i))
    }
  }
  return dates
})

const expectedAward = computed(() => {
  const current = status.value
  if (!current) {
    return 0
  }
  if (current.checkedInToday && current.todayPointAward != null) {
    return current.todayPointAward
  }
  if (current.checkedInToday) {
    return awardFor(Math.max(current.continuousDays, 1))
  }
  if (current.continuousDays > 0) {
    return awardFor(current.continuousDays + 1)
  }
  return awardFor(1)
})

const expectedLabel = computed(() => (status.value?.checkedInToday ? '今日已得' : '今日可得'))

const calendarCells = computed<CalendarCell[]>(() => {
  const todayText = today.value
  if (viewMode.value === 'week') {
    const start = startOfWeek(todayText)
    return Array.from({ length: 7 }, (_, index) => {
      const date = addDays(start, index)
      return buildCell(date, todayText)
    })
  }

  const anchor = parseDate(todayText)
  const year = anchor.getFullYear()
  const month = anchor.getMonth()
  const lead = (new Date(year, month, 1).getDay() + 6) % 7
  const daysInMonth = new Date(year, month + 1, 0).getDate()
  const cells: CalendarCell[] = []
  for (let i = 0; i < lead; i += 1) {
    cells.push({ key: `blank-${i}`, date: '', day: 0, isToday: false, checked: false })
  }
  for (let day = 1; day <= daysInMonth; day += 1) {
    cells.push(buildCell(formatDate(new Date(year, month, day)), todayText))
  }
  return cells
})

const calendarTitle = computed(() => {
  if (viewMode.value === 'month') {
    const anchor = parseDate(today.value)
    return `${anchor.getFullYear()} 年 ${anchor.getMonth() + 1} 月`
  }
  const start = startOfWeek(today.value)
  return `${start.slice(5)} ~ ${addDays(start, 6).slice(5)}`
})

const compRangeStart = computed(
  () => status.value?.compensableFrom || addDays(today.value, -7),
)
const compRangeEnd = computed(() => status.value?.compensableTo || addDays(today.value, -1))
const compRemaining = computed(() => {
  const current = status.value
  if (!current) {
    return 0
  }
  return Math.max(0, current.monthCompensationLimit - current.monthCompensationsUsed)
})

onShow(() => {
  if (!auth.ensureLogin('/pages/member/Checkin')) {
    return
  }
  loadStatus(false)
})

onPullDownRefresh(() => {
  if (loading.value) {
    uni.stopPullDownRefresh()
    return
  }
  loadStatus(false)
})

async function loadStatus(silent: boolean): Promise<void> {
  if (loading.value) {
    uni.stopPullDownRefresh()
    return
  }
  const current = ++sequence
  loading.value = true
  if (!silent) {
    error.value = ''
  }
  try {
    const result = await fetchCheckinStatus(silent)
    if (current === sequence) {
      status.value = result
      error.value = ''
    }
  } catch (err) {
    if (current === sequence) {
      error.value = (err as Error)?.message || '签到状态加载失败'
      if (!silent) {
        uni.showToast({ title: error.value, icon: 'none' })
      }
    }
  } finally {
    if (current === sequence) {
      loading.value = false
    }
    uni.stopPullDownRefresh()
  }
}

async function handleCheckin(): Promise<void> {
  if (!auth.ensureLogin('/pages/member/Checkin') || checking.value) {
    return
  }
  if (status.value?.checkedInToday) {
    uni.showToast({ title: '今日已签到', icon: 'none' })
    return
  }
  checking.value = true
  try {
    const result = await checkin()
    if (status.value) {
      status.value = {
        ...status.value,
        today: result.checkinDate,
        checkedInToday: true,
        continuousDays: result.continuousDays,
        todayPointAward: result.pointAward,
        lastCheckinDate: result.checkinDate,
      }
    }
    uni.showToast({ title: `签到成功 +${result.pointAward} 积分`, icon: 'none' })
    // 静默刷新一次，拿到本月补签次数等最新值；失败也不影响本地已展示的结果
    loadStatus(true)
  } catch {
    // 40207 今日已签到等业务错误由 http 层统一 toast
  } finally {
    checking.value = false
  }
}

function onDateChange(event: { detail: { value: string } }): void {
  compDate.value = event.detail.value
}

async function handleCompensate(): Promise<void> {
  if (!auth.ensureLogin('/pages/member/Checkin') || compensating.value) {
    return
  }
  const date = compDate.value
  if (!date) {
    uni.showToast({ title: '请选择补签日期', icon: 'none' })
    return
  }
  if (date < compRangeStart.value || date > compRangeEnd.value) {
    uni.showToast({ title: '补签日期不在可补签范围内', icon: 'none' })
    return
  }
  compensating.value = true
  try {
    const result = await compensateCheckin(date)
    localCompensated.value = localCompensated.value.concat(date)
    if (status.value) {
      status.value = {
        ...status.value,
        monthCompensationsUsed: status.value.monthCompensationsUsed + 1,
      }
    }
    compDate.value = ''
    uni.showToast({ title: `补签成功 +${result.pointAward} 积分`, icon: 'none' })
    loadStatus(true)
  } catch {
    // 日期越界、本月次数用尽、当日已签等错误由 http 层统一 toast
  } finally {
    compensating.value = false
  }
}

function setView(mode: 'week' | 'month'): void {
  viewMode.value = mode
}

function awardFor(streak: number): number {
  return BASE_POINT + Math.min(Math.max(streak, 1), MAX_STREAK_DAYS) * STEP_POINT
}

function buildCell(date: string, todayText: string): CalendarCell {
  return {
    key: date,
    date,
    day: Number(date.slice(8, 10)),
    isToday: date === todayText,
    checked: checkedSet.value.has(date),
  }
}

function pad(value: number): string {
  return String(value).padStart(2, '0')
}

function parseDate(value: string): Date {
  const [year, month, day] = value.split('-').map(Number)
  return new Date(year || 1970, (month || 1) - 1, day || 1)
}

function formatDate(date: Date): string {
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`
}

function localToday(): string {
  return formatDate(new Date())
}

function addDays(value: string, delta: number): string {
  const date = parseDate(value)
  date.setDate(date.getDate() + delta)
  return formatDate(date)
}

function startOfWeek(value: string): string {
  const date = parseDate(value)
  const weekday = date.getDay()
  const offset = weekday === 0 ? -6 : 1 - weekday
  date.setDate(date.getDate() + offset)
  return formatDate(date)
}
</script>

<style scoped>
.checkin {
  min-height: 100vh;
  padding: 24rpx;
  background-color: #f5f5f7;
}

.checkin__hero {
  padding: 32rpx;
  border-radius: 12rpx;
  background: linear-gradient(120deg, #ff8a1f 0%, #ff5c00 100%);
  color: #ffffff;
  box-shadow: 0 8rpx 20rpx rgba(255, 92, 0, 0.22);
}

.checkin__hero-top {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
}

.checkin__hero-label {
  font-size: 24rpx;
  opacity: 0.9;
}

.checkin__hero-days {
  display: flex;
  align-items: baseline;
  gap: 8rpx;
  margin-top: 4rpx;
}

.checkin__hero-num {
  font-size: 64rpx;
  line-height: 1.1;
  font-weight: 600;
}

.checkin__hero-unit {
  font-size: 26rpx;
}

.checkin__hero-side {
  display: flex;
  align-items: baseline;
  gap: 6rpx;
  padding-bottom: 8rpx;
}

.checkin__hero-side-label {
  font-size: 22rpx;
  opacity: 0.92;
}

.checkin__hero-point {
  font-size: 40rpx;
  font-weight: 600;
}

.checkin__btn {
  margin-top: 28rpx;
  height: 80rpx;
  line-height: 80rpx;
  border-radius: 40rpx;
  background-color: #ffffff;
  color: #ff5c00;
  text-align: center;
  font-size: 30rpx;
  font-weight: 600;
}

.checkin__btn.is-disabled {
  background-color: rgba(255, 255, 255, 0.28);
  color: rgba(255, 255, 255, 0.9);
}

.checkin__error {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 16rpx;
  padding: 14rpx 20rpx;
  border-radius: 8rpx;
  background-color: #fff8ef;
  color: #a26a2a;
  font-size: 22rpx;
}

.checkin__error-retry {
  color: #ff7a00;
  margin-left: 16rpx;
}

.checkin__calendar {
  margin-top: 20rpx;
  padding: 24rpx;
}

.checkin__cal-head {
  padding-bottom: 16rpx;
}

.checkin__tabs {
  display: flex;
  gap: 8rpx;
  padding: 4rpx;
  border-radius: 8rpx;
  background-color: #f5f5f7;
}

.checkin__tab {
  padding: 8rpx 24rpx;
  border-radius: 6rpx;
  color: #6f6f6f;
  font-size: 24rpx;
}

.checkin__tab.is-active {
  background-color: #ffffff;
  color: #ff7a00;
  font-weight: 600;
  box-shadow: 0 2rpx 6rpx rgba(20, 20, 20, 0.08);
}

.checkin__week-head {
  display: flex;
  padding: 8rpx 0;
}

.checkin__week-label {
  flex: 1;
  text-align: center;
  color: #a0a0a0;
  font-size: 22rpx;
}

.checkin__grid {
  display: flex;
  flex-wrap: wrap;
  padding: 8rpx 0;
}

.checkin__cell {
  position: relative;
  width: 14.2857%;
  height: 84rpx;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  font-size: 24rpx;
  color: #333333;
}

.checkin__cell.is-blank {
  visibility: hidden;
}

.checkin__cell.is-checked {
  color: #ff7a00;
  font-weight: 600;
}

.checkin__cell.is-today {
  border-radius: 10rpx;
  background-color: #fff2e6;
}

.checkin__cell-day {
  font-size: 26rpx;
}

.checkin__cell-mark,
.checkin__cell-today {
  margin-top: 2rpx;
  font-size: 18rpx;
  line-height: 1.2;
  color: #ff7a00;
}

.checkin__cell-today {
  color: #ff4d4f;
}

.checkin__legend {
  display: block;
  padding-top: 12rpx;
  border-top: 1rpx solid #f5f5f5;
  color: #a0a0a0;
  font-size: 20rpx;
  line-height: 1.6;
}

.checkin__compensate {
  margin-top: 20rpx;
  padding: 24rpx;
}

.checkin__comp-head {
  padding-bottom: 16rpx;
}

.checkin__comp-title {
  font-size: 30rpx;
  font-weight: 600;
}

.checkin__comp-row {
  display: flex;
  align-items: center;
  gap: 20rpx;
}

.checkin__comp-picker {
  min-width: 300rpx;
  padding: 16rpx 24rpx;
  border-radius: 8rpx;
  background-color: #f7f7f8;
  color: #333333;
  font-size: 26rpx;
}

.checkin__comp-btn {
  padding: 16rpx 36rpx;
  border-radius: 8rpx;
  background: linear-gradient(135deg, #ffa24c, #ff7a00);
  color: #ffffff;
  font-size: 26rpx;
}

.checkin__comp-btn.is-disabled {
  opacity: 0.5;
}

.checkin__comp-hint {
  display: block;
  margin-top: 14rpx;
  color: #a0a0a0;
  font-size: 22rpx;
  line-height: 1.6;
}
</style>
