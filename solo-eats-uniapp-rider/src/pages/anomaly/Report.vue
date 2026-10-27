<template>
  <view class="report rd-page">
    <view class="rd-card report__tip">
      <text class="report__tip-title">上报后会生成一条异常记录</text>
      <text class="report__tip-line">
        平台与商家可在订单异常工单中看到该记录。请如实填写，恶意上报会影响服务分。
      </text>
    </view>

    <view class="report__section-head">
      <text class="rd-subtitle">选择配送单</text>
      <text class="report__refresh" @click="loadDeliveries">{{ loading ? '加载中…' : '刷新' }}</text>
    </view>

    <view v-if="error" class="report__error">
      <text>{{ error }}</text>
      <text class="report__error-retry" @click="loadDeliveries">重试</text>
    </view>

    <view v-if="loading && deliveries.length === 0" class="rd-loading">正在加载进行中的配送单…</view>

    <view v-else-if="deliveries.length === 0" class="rd-empty report__empty">
      <text class="rd-empty__title">没有可上报的配送单</text>
      <text>异常上报需要关联本人已接单且未完成的配送单</text>
    </view>

    <view v-else class="report__deliveries">
      <view
        v-for="item in deliveries"
        :key="item.deliveryId"
        class="rd-card report__delivery"
        :class="{ 'is-selected': selectedId === item.deliveryId }"
        @click="selectDelivery(item.deliveryId)"
      >
        <view class="report__delivery-head rd-between">
          <text class="report__delivery-no">{{ item.deliveryNo }}</text>
          <text class="report__delivery-status">{{ item.statusName }}</text>
        </view>
        <text class="report__delivery-addr">{{ item.receiverAddress || '送达地址待更新' }}</text>
        <view class="report__delivery-foot">
          <text>{{ item.itemCount }} 件</text>
          <text v-if="item.promiseDeliverAt">承诺 {{ formatDateTime(item.promiseDeliverAt) }}</text>
        </view>
        <view class="report__radio" :class="{ 'is-checked': selectedId === item.deliveryId }">
          {{ selectedId === item.deliveryId ? '✓' : '' }}
        </view>
      </view>
    </view>

    <view class="report__section-head report__section-head--gap">
      <text class="rd-subtitle">异常类型</text>
    </view>

    <view class="report__types">
      <view
        v-for="type in ANOMALY_TYPES"
        :key="type.value"
        class="report__type"
        :class="{ 'is-active': selectedType === type.value }"
        @click="selectedType = type.value"
      >
        {{ type.label }}
      </view>
    </view>

    <view class="report__section-head report__section-head--gap">
      <text class="rd-subtitle">情况说明</text>
      <text class="report__count">{{ remark.length }}/255</text>
    </view>

    <view class="rd-card report__remark">
      <textarea
        v-model="remark"
        class="report__textarea"
        :maxlength="255"
        placeholder="补充说明现场情况，例如：用户电话无人接听，已等待 10 分钟"
        placeholder-class="report__placeholder"
      />
    </view>

    <view class="report__note">
      图片凭证暂不支持上传；如需补充照片，请联系平台客服。
    </view>

    <view class="rd-fixed-bottom report__footer">
      <button
        class="rd-btn rd-btn--primary"
        :class="{ 'is-disabled': submitting }"
        :disabled="submitting"
        @click="submit"
      >
        {{ submitting ? '提交中…' : '提交异常上报' }}
      </button>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { pageMyDeliveries } from '@/api/order'
import { reportAnomaly } from '@/api/rider'
import type { RiderTaskVO } from '@/api/types'
import { useAuthStore } from '@/stores/auth'
import { formatDateTime } from '@/utils/date'

/**
 * 异常上报。
 * 配送单来自 /rider/deliveries 的已接单/已取货/配送中三种状态（20/30/40），
 * 接口一次只接受一个 status，这里并发拉取三页后按 deliveryId 去重。
 * 类型取值与 ReportAnomalyRequest 一致：1超时 2联系不上用户 3地址错误
 * 4餐品破损 5商家出餐慢 6用户拒收。images 字段后端接受，但骑手端没有上传页，因此不传。
 */

const ANOMALY_TYPES = [
  { value: 1, label: '超时' },
  { value: 2, label: '联系不上用户' },
  { value: 3, label: '地址错误' },
  { value: 4, label: '餐品破损' },
  { value: 5, label: '商家出餐慢' },
  { value: 6, label: '用户拒收' },
]

const ACTIVE_STATUSES = [20, 30, 40]
/** 后端 pageMyDeliveries 单页上限 50，三种状态各取一页，足以覆盖骑手在途单 */
const PAGE_SIZE = 50

const auth = useAuthStore()

const deliveries = ref<RiderTaskVO[]>([])
const loading = ref(false)
const error = ref('')
const selectedId = ref<number | null>(null)
const selectedType = ref<number | null>(null)
const remark = ref('')
const submitting = ref(false)

onShow(() => {
  if (!auth.ensureLogin('/pages/anomaly/Report')) {
    return
  }
  void loadDeliveries()
})

function selectDelivery(deliveryId: number): void {
  selectedId.value = deliveryId
}

async function loadDeliveries(): Promise<void> {
  if (loading.value) {
    return
  }
  loading.value = true
  error.value = ''
  try {
    const pages = await Promise.all(
      ACTIVE_STATUSES.map((status) =>
        pageMyDeliveries({ status, pageNum: 1, pageSize: PAGE_SIZE }),
      ),
    )
    const merged: RiderTaskVO[] = []
    const seen = new Set<number>()
    pages.forEach((page) => {
      const list = Array.isArray(page?.list) ? page.list : []
      list.forEach((item) => {
        if (item && !seen.has(item.deliveryId)) {
          seen.add(item.deliveryId)
          merged.push(item)
        }
      })
    })
    deliveries.value = merged
    if (selectedId.value !== null && !seen.has(selectedId.value)) {
      selectedId.value = null
    }
  } catch (err) {
    // 刷新失败不清空已有配送单，保留上一次可选列表
    error.value = (err as Error)?.message || '配送单加载失败'
  } finally {
    loading.value = false
  }
}

async function submit(): Promise<void> {
  if (submitting.value) {
    return
  }
  if (selectedId.value === null) {
    uni.showToast({ title: '请先选择配送单', icon: 'none' })
    return
  }
  if (selectedType.value === null) {
    uni.showToast({ title: '请选择异常类型', icon: 'none' })
    return
  }
  const text = remark.value.trim()
  if (text.length > 255) {
    uni.showToast({ title: '说明不能超过 255 字', icon: 'none' })
    return
  }

  submitting.value = true
  try {
    await reportAnomaly(selectedId.value, {
      type: selectedType.value,
      remark: text || null,
    })
    uni.showToast({ title: '异常已上报', icon: 'none' })
    setTimeout(() => {
      uni.navigateBack({ delta: 1 })
    }, 700)
  } catch {
    // http 层已弹错误 toast，这里保留表单内容供修改后重试
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
.report {
  padding-bottom: 200rpx;
}

.report__tip {
  padding: 22rpx 24rpx;
  background-color: #f6f8fb;
}

.report__tip-title {
  display: block;
  font-size: 26rpx;
  font-weight: 600;
  color: #33455f;
}

.report__tip-line {
  display: block;
  margin-top: 8rpx;
  font-size: 22rpx;
  color: #8d99aa;
  line-height: 1.7;
}

.report__section-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin: 28rpx 4rpx 14rpx;
}

.report__section-head--gap {
  margin-top: 32rpx;
}

.report__refresh {
  font-size: 23rpx;
  color: #e85d04;
}

.report__count {
  font-size: 22rpx;
  color: #a9b4c4;
  font-variant-numeric: tabular-nums;
}

.report__error {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16rpx 20rpx;
  border-radius: 12rpx;
  background-color: #fff1f0;
  color: #b42318;
  font-size: 24rpx;
}

.report__error-retry {
  font-weight: 700;
  text-decoration: underline;
}

.report__empty {
  padding: 60rpx 40rpx;
}

.report__delivery {
  position: relative;
  margin-bottom: 16rpx;
  padding: 22rpx 76rpx 22rpx 24rpx;
  border: 2rpx solid transparent;
}

.report__delivery.is-selected {
  border-color: #ff7a00;
  background-color: #fffaf5;
}

.report__delivery-head {
  gap: 16rpx;
}

.report__delivery-no {
  font-size: 26rpx;
  font-weight: 700;
  color: #16233a;
}

.report__delivery-status {
  padding: 4rpx 14rpx;
  border-radius: 8rpx;
  background-color: #fff1e6;
  color: #c2410c;
  font-size: 21rpx;
}

.report__delivery-addr {
  display: block;
  margin-top: 8rpx;
  font-size: 23rpx;
  color: #7a8699;
  line-height: 1.6;
}

.report__delivery-foot {
  display: flex;
  gap: 24rpx;
  margin-top: 8rpx;
  font-size: 21rpx;
  color: #a9b4c4;
}

.report__radio {
  position: absolute;
  top: 50%;
  right: 24rpx;
  transform: translateY(-50%);
  width: 40rpx;
  height: 40rpx;
  line-height: 36rpx;
  text-align: center;
  border-radius: 50%;
  border: 2rpx solid #c3ccd9;
  font-size: 24rpx;
  color: #ffffff;
}

.report__radio.is-checked {
  border-color: #ff7a00;
  background-color: #ff7a00;
}

.report__types {
  display: flex;
  flex-wrap: wrap;
  gap: 14rpx;
}

.report__type {
  padding: 14rpx 26rpx;
  border-radius: 999rpx;
  background-color: #ffffff;
  border: 2rpx solid #e3e9f1;
  font-size: 24rpx;
  color: #33455f;
}

.report__type.is-active {
  border-color: #ff7a00;
  background-color: #fff1e6;
  color: #c2410c;
  font-weight: 600;
}

.report__remark {
  padding: 20rpx;
}

.report__textarea {
  width: 100%;
  height: 180rpx;
  font-size: 26rpx;
  line-height: 1.7;
  color: #16233a;
}

.report__placeholder {
  color: #b3bdca;
  font-size: 24rpx;
}

.report__note {
  margin-top: 20rpx;
  padding: 0 8rpx;
  font-size: 21rpx;
  color: #a9b4c4;
  line-height: 1.7;
}

.report__footer {
  padding-bottom: calc(24rpx + env(safe-area-inset-bottom));
}
</style>
