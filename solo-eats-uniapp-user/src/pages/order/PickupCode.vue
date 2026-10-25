<template>
  <view class="se-page pickup">
    <view v-if="loading" class="se-loading">正在加载取餐码…</view>

    <view v-else-if="error" class="se-error">
      <text>{{ error }}</text>
      <view class="se-error__retry" @click="goDetail">查看订单详情</view>
    </view>

    <template v-else-if="detail">
      <view class="pickup__card">
        <text class="pickup__label">取餐码</text>
        <text class="pickup__code">{{ detail.pickupCode }}</text>
        <text class="pickup__status">{{ detail.statusName }}</text>
        <text class="pickup__tip">到店出示以上号码，由门店核对后出餐</text>
      </view>

      <view v-if="shop" class="se-card pickup__shop">
        <text class="pickup__shop-name">{{ shop.shopName }}</text>
        <text class="pickup__shop-address">{{ shop.address }}</text>
        <text v-if="shop.businessHours && shop.businessHours.length > 0" class="se-muted">
          营业时间 {{ businessHoursText }}
        </text>
        <view class="pickup__shop-actions">
          <view v-if="shop.contactPhone" class="pickup__shop-btn" @click="callShop">拨打门店电话</view>
          <view class="pickup__shop-btn" @click="copyAddress">复制门店地址</view>
        </view>
      </view>

      <view class="se-card pickup__info">
        <view class="se-between pickup__row">
          <text class="se-label">订单号</text>
          <text>{{ detail.orderNo }}</text>
        </view>
        <view class="se-between pickup__row">
          <text class="se-label">下单时间</text>
          <text>{{ formatDateTime(detail.createdAt) }}</text>
        </view>
        <view class="se-between pickup__row">
          <text class="se-label">实付金额</text>
          <text class="se-price">{{ fenToYuan(detail.payAmount) }}</text>
        </view>
      </view>

      <view class="pickup__actions">
        <button class="se-btn se-btn-primary pickup__action" @click="markPickedUp">我已取餐</button>
        <button class="se-btn se-btn-plain pickup__action" @click="goDetail">查看订单详情</button>
      </view>

      <text class="pickup__note">
        取餐状态由门店核销：当前版本没有用户端「确认取餐」接口，点击「我已取餐」只做说明提示，不会改动订单状态。
      </text>
    </template>
  </view>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { onHide, onLoad, onShow, onUnload } from '@dcloudio/uni-app'
import { fetchOrderDetail, isTerminalStatus } from '@/api/order'
import { fetchShopDetail } from '@/api/shop'
import type { OrderDetailVO, ShopDetailVO } from '@/api/types'
import { fenToYuan } from '@/utils/money'
import { formatDateTime } from '@/utils/date'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()

const orderId = ref(0)
const detail = ref<OrderDetailVO | null>(null)
const shop = ref<ShopDetailVO | null>(null)
const loading = ref(true)
const error = ref('')

let timer: ReturnType<typeof setInterval> | null = null
let polling = false

const businessHoursText = computed(() => {
  const hours = shop.value?.businessHours || []
  return hours
    .map((hour) => `${hour.startTime}-${hour.endTime}`)
    .slice(0, 2)
    .join(' / ')
})

onLoad((options) => {
  const id = Number(options?.orderId)
  if (!Number.isFinite(id) || id <= 0) {
    loading.value = false
    error.value = '缺少订单参数'
    return
  }
  orderId.value = id
  if (!auth.ensureLogin(`/pages/order/PickupCode?orderId=${id}`)) {
    return
  }
  load()
})

onShow(() => {
  startPolling()
})

onHide(() => {
  stopPolling()
})

onUnload(() => {
  stopPolling()
})

async function load(): Promise<void> {
  loading.value = true
  error.value = ''
  try {
    const result = await fetchOrderDetail(orderId.value)
    // 只有自取单且后端已生成取餐码才允许停留在此页
    if (result.fulfillmentType !== 2 || !result.pickupCode) {
      detail.value = null
      error.value = '该订单无需取餐码'
    } else {
      detail.value = result
      startPolling()
    }
  } catch (err) {
    error.value = (err as Error)?.message || '取餐码加载失败'
  } finally {
    loading.value = false
  }

  if (detail.value) {
    try {
      shop.value = await fetchShopDetail(detail.value.shopId)
    } catch (err) {
      // 门店信息失败不影响取餐码展示
    }
  }
}

function startPolling(): void {
  stopPolling()
  if (!detail.value || isTerminalStatus(detail.value.status)) {
    return
  }
  // 商家核销后状态会变化，15 秒刷新一次，静默失败
  timer = setInterval(() => {
    void refreshSilently()
  }, 15_000)
}

function stopPolling(): void {
  if (timer) {
    clearInterval(timer)
    timer = null
  }
}

async function refreshSilently(): Promise<void> {
  if (polling) {
    return
  }
  polling = true
  try {
    const result = await fetchOrderDetail(orderId.value, true)
    if (result.fulfillmentType === 2 && result.pickupCode) {
      detail.value = result
      if (isTerminalStatus(result.status)) {
        stopPolling()
      }
    }
  } catch (err) {
    // 静默
  } finally {
    polling = false
  }
}

function markPickedUp(): void {
  uni.showModal({
    title: '我已取餐',
    content: '取餐核销由门店操作。请向店员出示取餐码，核销后订单状态会自动更新。',
    showCancel: false,
    confirmText: '知道了',
  })
}

function callShop(): void {
  const phone = shop.value?.contactPhone
  if (!phone) {
    return
  }
  uni.makePhoneCall({ phoneNumber: phone })
}

function copyAddress(): void {
  const address = shop.value?.address
  if (!address) {
    uni.showToast({ title: '门店未配置地址', icon: 'none' })
    return
  }
  uni.setClipboardData({ data: address })
}

function goDetail(): void {
  if (!orderId.value) {
    uni.switchTab({ url: '/pages/order/List' })
    return
  }
  uni.redirectTo({ url: `/pages/order/Detail?id=${orderId.value}` })
}
</script>

<style scoped>
.pickup {
  background: linear-gradient(180deg, #fff3e6 0%, #f5f5f7 46%);
}

.pickup__card {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 56rpx 32rpx;
  border-radius: 12rpx;
  background-color: #ffffff;
  box-shadow: 0 6rpx 24rpx rgba(255, 122, 0, 0.12);
}

.pickup__label {
  font-size: 26rpx;
  color: #8a8a8a;
}

.pickup__code {
  margin-top: 12rpx;
  font-size: 96rpx;
  line-height: 1.1;
  font-weight: 700;
  letter-spacing: 16rpx;
  color: #ff7a00;
}

.pickup__status {
  margin-top: 12rpx;
  font-size: 26rpx;
  color: #444444;
}

.pickup__tip {
  margin-top: 8rpx;
  font-size: 22rpx;
  color: #a0a0a0;
}

.pickup__shop {
  margin-top: 20rpx;
  padding: 24rpx;
}

.pickup__shop-name {
  display: block;
  font-size: 30rpx;
  font-weight: 600;
}

.pickup__shop-address {
  display: block;
  margin-top: 6rpx;
  font-size: 24rpx;
  color: #666666;
}

.pickup__shop-actions {
  display: flex;
  gap: 16rpx;
  margin-top: 16rpx;
}

.pickup__shop-btn {
  padding: 10rpx 24rpx;
  border: 1rpx solid #ff7a00;
  border-radius: 8rpx;
  color: #ff7a00;
  font-size: 22rpx;
}

.pickup__info {
  margin-top: 16rpx;
  padding: 20rpx 24rpx;
}

.pickup__row {
  padding: 8rpx 0;
  font-size: 24rpx;
}

.pickup__actions {
  margin-top: 32rpx;
}

.pickup__action {
  width: 100%;
  margin-bottom: 16rpx;
}

.pickup__note {
  display: block;
  padding: 0 8rpx;
  font-size: 20rpx;
  line-height: 1.7;
  color: #a0a0a0;
}
</style>
