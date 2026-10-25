<template>
  <view class="setting mt-page">
    <view v-if="loading && !detail" class="mt-loading">正在加载门店设置…</view>

    <view v-else-if="error && !detail" class="mt-error">
      <text class="mt-error__msg">{{ error }}</text>
      <view class="mt-error__retry" @click="load">重新加载</view>
    </view>

    <template v-else-if="detail">
      <!-- 门店概览 -->
      <view class="setting__head">
        <view class="setting__head-main">
          <text class="setting__shop-name">{{ detail.shopName }}</text>
          <text class="setting__shop-addr">{{ detail.address || '地址待维护' }}</text>
        </view>
        <text class="mt-tag setting__status-tag">{{ detail.statusText || '状态未知' }}</text>
      </view>

      <!-- 营业状态 -->
      <view class="mt-card setting__card">
        <view class="setting__card-head">
          <text class="mt-section-title">营业状态</text>
          <text class="mt-muted">{{ statusSaving ? '保存中…' : '仅影响新订单' }}</text>
        </view>
        <view class="setting__segments">
          <view
            v-for="option in STATUS_OPTIONS"
            :key="option.value"
            class="setting__segment"
            :class="{
              'is-active': detail.status === option.value,
              'is-disabled': detail.status === 3,
            }"
            @click="changeStatus(option.value)"
          >
            {{ option.label }}
          </view>
        </view>
        <text class="setting__hint">
          营业状态是门店意愿；用户端能否下单还取决于当前时间是否落在营业时段内。
          打烊/休息需二次确认。
        </text>
      </view>

      <!-- 基础金额与出餐时长 -->
      <view class="mt-card setting__card">
        <view class="setting__card-head">
          <text class="mt-section-title">起送价与费用</text>
          <text class="mt-muted">金额单位：元</text>
        </view>

        <view class="mt-field">
          <text class="mt-field__label">起送价</text>
          <input v-model="form.minOrderAmount" class="mt-input" type="digit" placeholder="0.00" placeholder-class="mt-placeholder" />
        </view>
        <view class="mt-field">
          <text class="mt-field__label">配送费</text>
          <input v-model="form.deliveryFee" class="mt-input" type="digit" placeholder="0.00" placeholder-class="mt-placeholder" />
        </view>
        <view class="mt-field">
          <text class="mt-field__label">打包费</text>
          <input v-model="form.packagingFee" class="mt-input" type="digit" placeholder="0.00" placeholder-class="mt-placeholder" />
        </view>
        <view class="mt-field">
          <text class="mt-field__label">预计出餐（分钟）</text>
          <input v-model="form.prepMinutes" class="mt-input" type="number" placeholder="15" placeholder-class="mt-placeholder" />
        </view>

        <button class="mt-btn mt-btn--primary setting__save" :disabled="basicSaving" @click="saveBasic">
          {{ basicSaving ? '保存中…' : '保存起送价与费用' }}
        </button>
      </view>

      <!-- 营业时段 -->
      <view class="mt-card setting__card">
        <view class="setting__card-head">
          <text class="mt-section-title">营业时段</text>
          <text class="mt-muted">共 {{ hours.length }} 条</text>
        </view>
        <text class="setting__hint">
          保存会全量替换现有营业时段。结束时间早于开始时间按跨天处理（如 22:00-02:00）。
        </text>

        <view v-if="hours.length === 0" class="setting__hours-empty">
          当前未配置营业时段。后端口径：未配置时营业状态为「营业」即可接单。
        </view>

        <view v-for="(row, index) in hours" :key="index" class="setting__hour">
          <picker
            mode="selector"
            :range="DAY_LABELS"
            :value="row.dayOfWeek - 1"
            @change="onDayChange(index, $event)"
          >
            <view class="setting__hour-day">{{ DAY_LABELS[row.dayOfWeek - 1] }}</view>
          </picker>

          <picker mode="time" :value="row.startTime" @change="onStartChange(index, $event)">
            <view class="setting__hour-time">{{ row.startTime }}</view>
          </picker>

          <text class="setting__hour-sep">至</text>

          <picker mode="time" :value="row.endTime" @change="onEndChange(index, $event)">
            <view class="setting__hour-time">{{ row.endTime }}</view>
          </picker>

          <text class="setting__hour-del" @click="removeHour(index)">删除</text>
        </view>

        <button class="mt-btn mt-btn--plain setting__add" @click="addHour">+ 添加营业时段</button>

        <button
          class="mt-btn mt-btn--primary setting__save"
          :disabled="hoursSaving || hours.length === 0"
          @click="saveHours"
        >
          {{ hoursSaving ? '保存中…' : '保存营业时段' }}
        </button>
      </view>

      <!-- 到店自取 -->
      <view class="mt-card setting__card">
        <view class="setting__card-head">
          <text class="mt-section-title">到店自取</text>
          <switch
            :checked="pickupForm.pickupEnabled"
            color="#FF7A00"
            class="mt-switch"
            @change="onPickupToggle"
          />
        </view>
        <text class="setting__hint">
          关闭后用户端不再展示「到店自取」，已下单的自取单不受影响。
        </text>

        <view class="mt-field">
          <text class="mt-field__label">备餐时长（分钟）</text>
          <input v-model="pickupForm.pickupPrepareMinutes" class="mt-input" type="number" placeholder="15" placeholder-class="mt-placeholder" />
        </view>
        <view class="mt-field">
          <text class="mt-field__label">超时未取（分钟）</text>
          <input v-model="pickupForm.pickupTimeoutMinutes" class="mt-input" type="number" placeholder="120" placeholder-class="mt-placeholder" />
        </view>
        <view class="mt-field">
          <text class="mt-field__label">取餐码长度</text>
          <input v-model="pickupForm.pickupCodeLength" class="mt-input" type="number" placeholder="6" placeholder-class="mt-placeholder" />
        </view>
        <view class="mt-field">
          <text class="mt-field__label">取餐码有效期（分钟）</text>
          <input v-model="pickupForm.pickupCodeValidMinutes" class="mt-input" type="number" placeholder="60" placeholder-class="mt-placeholder" />
        </view>
        <view class="mt-field">
          <text class="mt-field__label">自取优惠（元）</text>
          <input v-model="pickupForm.pickupDiscount" class="mt-input" type="digit" placeholder="0.00" placeholder-class="mt-placeholder" />
        </view>
        <view v-if="detail.pickupCodePrefix" class="mt-field">
          <text class="mt-field__label">取餐码前缀</text>
          <text class="mt-field__value">{{ detail.pickupCodePrefix }}（在 Web 端修改）</text>
        </view>

        <button class="mt-btn mt-btn--primary setting__save" :disabled="pickupSaving" @click="savePickup">
          {{ pickupSaving ? '保存中…' : '保存自取设置' }}
        </button>
      </view>

      <!-- 账号 -->
      <view class="mt-card setting__card setting__account">
        <view class="mt-between">
          <text class="mt-muted">当前账号</text>
          <text class="setting__account-name">{{ auth.nickname }}</text>
        </view>
        <button class="mt-btn mt-btn--danger setting__logout" @click="handleLogout">退出登录</button>
      </view>

      <view class="setting__footer">营业时段与自取配置保存后立即生效，用户端菜单缓存由后端清理。</view>
    </template>
  </view>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { ApiError } from '@/api/http'
import { fetchShopDetail, saveBusinessHours, updatePickup, updateShop, updateShopStatus } from '@/api/shop'
import type { ShopDetailVO } from '@/api/types'
import { useAuthStore } from '@/stores/auth'
import { useShopStore } from '@/stores/shop'
import { fenToYuan, yuanToFen } from '@/utils/money'

/**
 * 门店基础设置。
 *
 * 门店详情只能从公开接口 GET /c/shops/{id} 回填（商家侧无 GET 详情），
 * 这样 PUT /merchant/shops/{id} 全量提交时必填字段（店名/地址/经纬度/配送方式/起送价）
 * 不会因为前端拿不到而写成空。只提交本页编辑的字段，其余可空字段传 null 由后端保留原值。
 */

const DAY_LABELS = ['周一', '周二', '周三', '周四', '周五', '周六', '周日']

/** 0 休息 / 1 营业 / 2 打烊；3 停用只能由平台设置，不在此处提供 */
const STATUS_OPTIONS = [
  { value: 1, label: '营业' },
  { value: 0, label: '休息' },
  { value: 2, label: '打烊' },
]

const auth = useAuthStore()
const shop = useShopStore()

const detail = ref<ShopDetailVO | null>(null)
const loading = ref(true)
const error = ref('')

const form = reactive({
  minOrderAmount: '0.00',
  deliveryFee: '0.00',
  packagingFee: '0.00',
  prepMinutes: '15',
})

const pickupForm = reactive({
  pickupEnabled: false,
  pickupPrepareMinutes: '15',
  pickupTimeoutMinutes: '120',
  pickupCodeLength: '6',
  pickupCodeValidMinutes: '60',
  pickupDiscount: '0.00',
})

const hours = ref<Array<{ dayOfWeek: number; startTime: string; endTime: string }>>([])

const statusSaving = ref(false)
const basicSaving = ref(false)
const hoursSaving = ref(false)
const pickupSaving = ref(false)

onLoad(() => {
  if (!auth.ensureLogin('/pages/shop/ShopSetting')) {
    return
  }
  void load()
})

async function ensureShop(): Promise<void> {
  if (shop.currentShopId !== null) {
    return
  }
  try {
    const list = await shop.loadShops(true)
    if (list.length > 0) {
      const preferred = list.find((item) => item.id === auth.shopId) || list[0]
      shop.selectShop(preferred.id)
    }
  } catch (err) {
    error.value = (err as Error)?.message || '门店列表加载失败'
  }
}

async function load(): Promise<void> {
  loading.value = true
  error.value = ''
  try {
    await ensureShop()
    if (shop.currentShopId === null) {
      error.value = '未选择门店，请返回工作台重新选择'
      return
    }
    const data = await fetchShopDetail(shop.currentShopId)
    detail.value = data
    fillForm(data)
  } catch (err) {
    error.value = (err as Error)?.message || '门店设置加载失败'
  } finally {
    loading.value = false
  }
}

function fillForm(data: ShopDetailVO): void {
  form.minOrderAmount = fenToYuan(data.minOrderAmount)
  form.deliveryFee = fenToYuan(data.deliveryFee)
  form.packagingFee = fenToYuan(data.packagingFee)
  form.prepMinutes = String(data.prepMinutes ?? 15)

  pickupForm.pickupEnabled = data.pickupEnabled === 1
  pickupForm.pickupPrepareMinutes = String(data.pickupPrepareMinutes ?? 15)
  pickupForm.pickupTimeoutMinutes = String(data.pickupTimeoutMinutes ?? 120)
  pickupForm.pickupCodeLength = String(data.pickupCodeLength ?? 6)
  pickupForm.pickupCodeValidMinutes = String(data.pickupCodeValidMinutes ?? 60)
  pickupForm.pickupDiscount = fenToYuan(data.pickupDiscount)

  // 后端 LocalTime 序列化为 09:00:00，picker 只认 HH:mm
  hours.value = (data.businessHours || []).map((item) => ({
    dayOfWeek: item.dayOfWeek,
    startTime: String(item.startTime || '').slice(0, 5),
    endTime: String(item.endTime || '').slice(0, 5),
  }))
}

function toast(message: string): void {
  uni.showToast({ title: message, icon: 'none', duration: 2400 })
}

function showConfirm(title: string, content: string, confirmText: string): Promise<boolean> {
  return new Promise((resolve) => {
    uni.showModal({
      title,
      content,
      confirmText,
      cancelText: '取消',
      success: (result) => resolve(Boolean(result.confirm)),
      fail: () => resolve(false),
    })
  })
}

function handleError(err: unknown, fallback: string): void {
  if (err instanceof ApiError) {
    if (err.code === 10004) {
      toast('无权操作该门店，请确认账号绑定')
      return
    }
    if (err.code === 20001) {
      toast('门店已被修改，请刷新后重试')
      return
    }
  }
  toast((err as Error)?.message || fallback)
}

/* ------------------------------ 营业状态 ------------------------------ */

async function changeStatus(target: number): Promise<void> {
  if (!detail.value || statusSaving.value || detail.value.status === target) {
    return
  }
  if (detail.value.status === 3) {
    toast('门店已被平台停用，需联系运营恢复后才能切换营业状态')
    return
  }
  if (target === 0 || target === 2) {
    const label = target === 0 ? '休息' : '打烊'
    const confirmed = await showConfirm(
      `切换为${label}`,
      `切换为「${label}」后用户端该门店立即不可下单（已下单不受影响）。确认切换？`,
      `确认${label}`,
    )
    if (!confirmed) {
      return
    }
  }

  statusSaving.value = true
  try {
    await updateShopStatus(detail.value.id, target)
    const text = target === 1 ? '营业' : target === 0 ? '休息' : '打烊'
    detail.value.status = target
    detail.value.statusText = text
    shop.applyStatus(target, text)
    toast(`已切换为${text}`)
  } catch (err) {
    handleError(err, '营业状态切换失败')
  } finally {
    statusSaving.value = false
  }
}

/* --------------------------- 起送价与费用 --------------------------- */

function parseFen(raw: string, label: string, allowEmpty = false): number | null {
  const text = String(raw ?? '').trim()
  if (!text) {
    if (allowEmpty) {
      return 0
    }
    toast(`请填写${label}`)
    return null
  }
  try {
    const fen = yuanToFen(text)
    if (fen < 0) {
      toast(`${label}不能为负数`)
      return null
    }
    return fen
  } catch (err) {
    toast(`${label}格式不正确，最多两位小数`)
    return null
  }
}

function parseIntInRange(raw: string, label: string, min: number, max: number): number | null {
  const text = String(raw ?? '').trim()
  if (!/^\d+$/.test(text)) {
    toast(`请填写${label}（${min}~${max} 的整数）`)
    return null
  }
  const value = Number(text)
  if (value < min || value > max) {
    toast(`${label}取值 ${min}~${max}`)
    return null
  }
  return value
}

async function saveBasic(): Promise<void> {
  const current = detail.value
  if (!current || basicSaving.value) {
    return
  }
  if (!current.shopName || !current.address || current.lng === null || current.lat === null) {
    toast('门店名称/地址/经纬度不完整，请在 Web 商家中心补全')
    return
  }
  if (!current.deliveryTypes || current.deliveryTypes < 1 || current.deliveryTypes > 3) {
    toast('门店配送方式未配置，请先在 Web 商家中心设置')
    return
  }

  const minOrderAmount = parseFen(form.minOrderAmount, '起送价')
  if (minOrderAmount === null) {
    return
  }
  const deliveryFee = parseFen(form.deliveryFee, '配送费', true)
  if (deliveryFee === null) {
    return
  }
  const packagingFee = parseFen(form.packagingFee, '打包费', true)
  if (packagingFee === null) {
    return
  }
  const prepMinutes = parseIntInRange(form.prepMinutes, '预计出餐时长', 1, 180)
  if (prepMinutes === null) {
    return
  }

  const confirmed = await showConfirm(
    '确认保存',
    `起送价 ¥${fenToYuan(minOrderAmount)}，配送费 ¥${fenToYuan(deliveryFee)}，打包费 ¥${fenToYuan(packagingFee)}，预计出餐 ${prepMinutes} 分钟。`,
    '保存',
  )
  if (!confirmed) {
    return
  }

  basicSaving.value = true
  try {
    const updated = await updateShop(current.id, {
      shopName: current.shopName,
      address: current.address,
      lng: current.lng,
      lat: current.lat,
      deliveryTypes: current.deliveryTypes,
      minOrderAmount,
      deliveryFee,
      packagingFee,
      prepMinutes,
    })
    detail.value = updated
    fillForm(updated)
    toast('起送价与费用已保存')
  } catch (err) {
    handleError(err, '保存失败，请重试')
  } finally {
    basicSaving.value = false
  }
}

/* ------------------------------ 营业时段 ------------------------------ */

function onDayChange(index: number, event: { detail: { value: string | number } }): void {
  const dayIndex = Number(event.detail.value)
  const row = hours.value[index]
  if (!row || !Number.isFinite(dayIndex)) {
    return
  }
  row.dayOfWeek = dayIndex + 1
}

function onStartChange(index: number, event: { detail: { value: string } }): void {
  const row = hours.value[index]
  if (row) {
    row.startTime = event.detail.value
  }
}

function onEndChange(index: number, event: { detail: { value: string } }): void {
  const row = hours.value[index]
  if (row) {
    row.endTime = event.detail.value
  }
}

function addHour(): void {
  if (hours.value.length >= 50) {
    toast('营业时段最多 50 条')
    return
  }
  const used = new Set(hours.value.map((item) => item.dayOfWeek))
  const nextDay = [1, 2, 3, 4, 5, 6, 7].find((day) => !used.has(day)) || 1
  hours.value.push({ dayOfWeek: nextDay, startTime: '09:00', endTime: '21:00' })
}

function removeHour(index: number): void {
  hours.value.splice(index, 1)
}

async function saveHours(): Promise<void> {
  const current = detail.value
  if (!current || hoursSaving.value) {
    return
  }
  if (hours.value.length === 0) {
    toast('至少添加 1 条营业时段')
    return
  }
  const invalid = hours.value.find(
    (row) => !row.startTime || !row.endTime || row.startTime === row.endTime,
  )
  if (invalid) {
    toast('营业时段的开始与结束时间不能相同')
    return
  }

  const confirmed = await showConfirm(
    '确认保存营业时段',
    `将用 ${hours.value.length} 条时段全量替换现有配置，确认保存？`,
    '保存',
  )
  if (!confirmed) {
    return
  }

  hoursSaving.value = true
  try {
    await saveBusinessHours(
      current.id,
      hours.value.map((row) => ({
        dayOfWeek: row.dayOfWeek,
        startTime: row.startTime,
        endTime: row.endTime,
      })),
    )
    toast('营业时段已保存')
    await reloadDetail(current.id)
  } catch (err) {
    handleError(err, '营业时段保存失败')
  } finally {
    hoursSaving.value = false
  }
}

/* ------------------------------ 到店自取 ------------------------------ */

function onPickupToggle(event: { detail: { value: boolean } }): void {
  const next = Boolean(event.detail.value)
  if (!next) {
    uni.showModal({
      title: '关闭到店自取',
      content: '关闭后用户端不再展示「到店自取」，已下单的自取单不受影响。确认关闭？',
      confirmText: '确认关闭',
      cancelText: '取消',
      success: (result) => {
        pickupForm.pickupEnabled = result.confirm ? false : true
      },
    })
    return
  }
  pickupForm.pickupEnabled = true
}

async function savePickup(): Promise<void> {
  const current = detail.value
  if (!current || pickupSaving.value) {
    return
  }
  const prepare = parseIntInRange(pickupForm.pickupPrepareMinutes, '自取备餐时长', 1, 120)
  if (prepare === null) {
    return
  }
  const timeout = parseIntInRange(pickupForm.pickupTimeoutMinutes, '超时未取时长', 10, 1440)
  if (timeout === null) {
    return
  }
  const codeLength = parseIntInRange(pickupForm.pickupCodeLength, '取餐码长度', 4, 10)
  if (codeLength === null) {
    return
  }
  const codeValid = parseIntInRange(pickupForm.pickupCodeValidMinutes, '取餐码有效期', 1, 120)
  if (codeValid === null) {
    return
  }
  const discount = parseFen(pickupForm.pickupDiscount, '自取优惠', true)
  if (discount === null) {
    return
  }
  if (discount > 100000) {
    toast('自取优惠不能超过 1000 元')
    return
  }

  pickupSaving.value = true
  try {
    await updatePickup(current.id, {
      pickupEnabled: pickupForm.pickupEnabled,
      pickupPrepareMinutes: prepare,
      pickupTimeoutMinutes: timeout,
      pickupCodePrefix: current.pickupCodePrefix || '',
      pickupCodeLength: codeLength,
      pickupCodeValidMinutes: codeValid,
      pickupDiscount: discount,
    })
    toast('自取设置已保存')
    await reloadDetail(current.id)
  } catch (err) {
    handleError(err, '自取设置保存失败')
  } finally {
    pickupSaving.value = false
  }
}

async function reloadDetail(shopId: number): Promise<void> {
  try {
    const data = await fetchShopDetail(shopId, true)
    detail.value = data
    fillForm(data)
    shop.applyStatus(data.status ?? 0, data.statusText)
  } catch (err) {
    // 保存已成功，刷新失败只提示，不回滚本地展示
    toast('设置已保存，详情刷新失败，请手动重试')
  }
}

/* ------------------------------- 账号 ------------------------------- */

function handleLogout(): void {
  uni.showModal({
    title: '退出登录',
    content: '确认退出当前商家账号？',
    confirmText: '退出',
    success: (result) => {
      if (!result.confirm) {
        return
      }
      auth.logout()
      shop.reset()
      uni.reLaunch({ url: '/pages/login/Login' })
    },
  })
}
</script>

<style scoped>
.setting {
  padding-bottom: calc(40rpx + env(safe-area-inset-bottom));
}

.setting__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16rpx;
  padding: 18rpx 20rpx;
  border-radius: 12rpx;
  background: linear-gradient(135deg, #1f3a4d, #16233a);
  color: #ffffff;
}

.setting__head-main {
  flex: 1;
  min-width: 0;
}

.setting__shop-name {
  display: block;
  font-size: 30rpx;
  font-weight: 700;
}

.setting__shop-addr {
  display: block;
  margin-top: 4rpx;
  font-size: 20rpx;
  color: rgba(255, 255, 255, 0.72);
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.setting__status-tag {
  flex: none;
}

.setting__card {
  margin-top: 14rpx;
}

.setting__card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12rpx;
  margin-bottom: 8rpx;
}

.setting__segments {
  display: flex;
  gap: 12rpx;
  margin-top: 8rpx;
}

.setting__segment {
  flex: 1;
  height: 68rpx;
  line-height: 68rpx;
  text-align: center;
  border-radius: 8rpx;
  background-color: #f2f5f9;
  color: #44536b;
  font-size: 25rpx;
  border: 2rpx solid transparent;
}

.setting__segment.is-active {
  background-color: #fff1e6;
  border-color: #ff9a3c;
  color: #c2410c;
  font-weight: 700;
}

.setting__segment.is-disabled {
  opacity: 0.45;
}

.setting__hint {
  display: block;
  margin: 8rpx 0;
  font-size: 20rpx;
  color: #8d99aa;
  line-height: 1.6;
}

.setting__save {
  margin-top: 16rpx;
  height: 80rpx;
  line-height: 80rpx;
  font-size: 26rpx;
}

.setting__hours-empty {
  margin: 8rpx 0;
  padding: 12rpx 14rpx;
  border-radius: 8rpx;
  background-color: #f7f9fc;
  color: #7a8699;
  font-size: 20rpx;
  line-height: 1.6;
}

.setting__hour {
  display: flex;
  align-items: center;
  gap: 10rpx;
  padding: 10rpx 0;
  border-bottom: 1rpx solid #f4f6f9;
}

.setting__hour-day {
  width: 88rpx;
  height: 60rpx;
  line-height: 60rpx;
  text-align: center;
  border-radius: 8rpx;
  background-color: #f2f5f9;
  font-size: 23rpx;
  color: #33455f;
}

.setting__hour-time {
  width: 108rpx;
  height: 60rpx;
  line-height: 60rpx;
  text-align: center;
  border-radius: 8rpx;
  background-color: #f7f9fc;
  font-size: 23rpx;
  color: #1b2432;
  font-variant-numeric: tabular-nums;
}

.setting__hour-sep {
  font-size: 21rpx;
  color: #8d99aa;
}

.setting__hour-del {
  margin-left: auto;
  font-size: 21rpx;
  color: #d93a2b;
}

.setting__add {
  margin-top: 14rpx;
  height: 68rpx;
  line-height: 68rpx;
  font-size: 24rpx;
}

.setting__account {
  margin-top: 14rpx;
}

.setting__account-name {
  font-size: 24rpx;
  color: #1b2432;
  font-weight: 600;
}

.setting__logout {
  margin-top: 16rpx;
  height: 72rpx;
  line-height: 72rpx;
  font-size: 25rpx;
}

.setting__footer {
  margin-top: 20rpx;
  text-align: center;
  font-size: 20rpx;
  color: #a9b4c4;
  line-height: 1.6;
}
</style>
