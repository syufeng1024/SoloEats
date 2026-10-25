<template>
  <view class="receipt mt-page">
    <view v-if="loading" class="mt-loading">正在生成小票…</view>

    <view v-else-if="error" class="mt-error">
      <text class="mt-error__msg">{{ error }}</text>
      <view class="mt-error__retry" @click="handleRetry">
        {{ orderId > 0 ? '重新生成' : '返回订单列表' }}
      </view>
    </view>

    <template v-else>
      <view class="receipt__tip">
        小票由前端 Canvas 生成，仅用于备餐核对；不做真实打印。
      </view>

      <view v-if="imagePath" class="receipt__paper">
        <image
          :src="imagePath"
          mode="widthFix"
          class="receipt__image"
          show-menu-by-longpress
        />
      </view>
      <view v-else class="mt-loading">小票渲染中…</view>

      <view v-if="data" class="mt-card receipt__meta">
        <view class="mt-between">
          <text class="mt-muted">订单号</text>
          <text class="receipt__value">{{ data.orderNo }}</text>
        </view>
        <view class="mt-between">
          <text class="mt-muted">门店</text>
          <text class="receipt__value">{{ data.shopName }}</text>
        </view>
        <view class="mt-between">
          <text class="mt-muted">下单时间</text>
          <text class="receipt__value">{{ formatDateTime(data.createdAt) }}</text>
        </view>
        <view class="mt-between">
          <text class="mt-muted">商品件数</text>
          <text class="receipt__value">{{ totalQuantity }} 件</text>
        </view>
      </view>

      <view class="receipt__actions">
        <button class="mt-btn mt-btn--plain" :disabled="rendering" @click="renderReceipt">
          {{ rendering ? '生成中…' : '重新生成' }}
        </button>
        <button
          class="mt-btn mt-btn--primary"
          :disabled="!imagePath || saving"
          @click="handleSave"
        >
          {{ saving ? '保存中…' : '保存图片' }}
        </button>
      </view>

      <view class="receipt__footer">H5 端保存会触发浏览器下载；App 端保存到相册。</view>
    </template>

    <!-- 绘制用 canvas 放到可视区外，页面只展示导出的图片 -->
    <canvas
      :id="RECEIPT_CANVAS_ID"
      :canvas-id="RECEIPT_CANVAS_ID"
      class="receipt__canvas"
      :style="{ width: RECEIPT_WIDTH + 'px', height: canvasHeight + 'px' }"
      :width="RECEIPT_WIDTH"
      :height="canvasHeight"
    />
  </view>
</template>

<script setup lang="ts">
import { computed, getCurrentInstance, nextTick, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { getMerchantOrder } from '@/api/order'
import type { MerchantOrderDetailVO } from '@/api/types'
import { useAuthStore } from '@/stores/auth'
import { useShopStore } from '@/stores/shop'
import { formatDateTime } from '@/utils/date'
import {
  RECEIPT_CANVAS_ID,
  RECEIPT_WIDTH,
  calcReceiptHeight,
  drawReceipt,
  exportReceiptImage,
  saveReceiptImage,
  type ReceiptContext,
  type ReceiptData,
} from '@/utils/receipt'

/**
 * 小票预览：Canvas 绘制 → 导出图片。
 * 取餐码只在自取单展示；paidAt 后端恒为 null，不参与渲染。
 */

const auth = useAuthStore()
const shop = useShopStore()
const instance = getCurrentInstance()

const orderId = ref(0)
const order = ref<MerchantOrderDetailVO | null>(null)
const data = ref<ReceiptData | null>(null)
const loading = ref(true)
const rendering = ref(false)
const saving = ref(false)
const error = ref('')
const imagePath = ref('')
const canvasHeight = ref(600)

const totalQuantity = computed(() =>
  (data.value?.items || []).reduce((sum, item) => sum + (item.quantity || 0), 0),
)

onLoad((options) => {
  // 详情页传 orderId；保留 id 兼容手动拼参数
  const raw = options?.orderId ?? options?.id
  const id = Number(raw)
  if (!Number.isFinite(id) || id <= 0) {
    loading.value = false
    error.value = '缺少订单参数，请从订单详情进入'
    return
  }
  orderId.value = id
  if (!auth.ensureLogin(`/pages/order/Receipt?orderId=${id}`)) {
    return
  }
  void bootstrap()
})

async function bootstrap(): Promise<void> {
  loading.value = true
  error.value = ''
  imagePath.value = ''
  try {
    await ensureShopName()
    order.value = await getMerchantOrder(orderId.value)
    data.value = buildReceiptData(order.value)
    await renderReceipt()
  } catch (err) {
    error.value = (err as Error)?.message || '小票生成失败'
  } finally {
    loading.value = false
  }
}

function handleRetry(): void {
  if (orderId.value > 0) {
    void bootstrap()
    return
  }
  uni.switchTab({ url: '/pages/order/List' })
}

async function ensureShopName(): Promise<void> {
  // 门店信息加载失败不影响小票主流程，名称降级为「门店」
  if (shop.shops.length === 0) {
    try {
      await shop.loadShops(true)
    } catch (error) {
      // 静默：下方用兜底名称
    }
  }
}

function buildReceiptData(detail: MerchantOrderDetailVO): ReceiptData {
  const matched =
    shop.shops.find((item) => item.id === detail.shopId) ||
    (shop.currentShop?.id === detail.shopId ? shop.currentShop : null)
  return {
    shopName: matched?.shopName || '门店',
    orderNo: detail.orderNo,
    createdAt: detail.createdAt,
    fulfillmentTypeName:
      detail.fulfillmentTypeName || (detail.fulfillmentType === 2 ? '自取' : '配送'),
    pickupCode: detail.pickupCode,
    items: (detail.items || []).map((item) => ({
      name: item.skuName,
      quantity: item.quantity,
      amount: item.payableAmount ?? 0,
    })),
    goodsAmount: detail.goodsAmount ?? 0,
    packagingFee: detail.packagingFee ?? 0,
    deliveryFee: detail.deliveryFee ?? 0,
    payAmount: detail.payAmount ?? 0,
    remark: detail.remark,
  }
}

async function renderReceipt(): Promise<void> {
  if (!data.value || rendering.value) {
    return
  }
  rendering.value = true
  error.value = ''
  try {
    const payload = data.value
    canvasHeight.value = calcReceiptHeight(payload)
    imagePath.value = ''
    // 等 canvas 节点按新高度渲染完成再取上下文
    await nextTick()
    await delay(60)

    const ctx = uni.createCanvasContext(
      RECEIPT_CANVAS_ID,
      instance?.proxy as never,
    ) as unknown as ReceiptContext
    await drawReceipt(ctx, payload, canvasHeight.value, instance?.proxy)
    // 部分 H5 内核在 draw 回调后仍有一帧合成延迟，直接导图会得到空白图
    await delay(60)
    imagePath.value = await exportReceiptImage(instance?.proxy)
  } catch (err) {
    error.value = (err as Error)?.message || '小票渲染失败，请重试'
  } finally {
    rendering.value = false
  }
}

function delay(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms))
}

async function handleSave(): Promise<void> {
  if (!imagePath.value || saving.value) {
    return
  }
  saving.value = true
  try {
    const fileName = `receipt-${data.value?.orderNo || orderId.value}.png`
    await saveReceiptImage(imagePath.value, fileName)
    uni.showToast({ title: '已保存图片', icon: 'none' })
  } catch (err) {
    uni.showToast({ title: (err as Error)?.message || '保存失败', icon: 'none' })
  } finally {
    saving.value = false
  }
}
</script>

<style scoped>
.receipt {
  padding-bottom: calc(40rpx + env(safe-area-inset-bottom));
}

.receipt__tip {
  padding: 12rpx 16rpx;
  border-radius: 8rpx;
  background-color: #eef4ff;
  color: #2c4a75;
  font-size: 21rpx;
  line-height: 1.6;
}

.receipt__paper {
  margin-top: 16rpx;
  padding: 12rpx;
  border-radius: 10rpx;
  background-color: #ffffff;
  box-shadow: 0 4rpx 14rpx rgba(20, 35, 58, 0.08);
}

.receipt__image {
  width: 100%;
  display: block;
  border-radius: 6rpx;
}

.receipt__meta {
  margin-top: 16rpx;
}

.receipt__meta .mt-between {
  padding: 6rpx 0;
  font-size: 23rpx;
}

.receipt__value {
  color: #1b2432;
  font-variant-numeric: tabular-nums;
}

.receipt__actions {
  display: flex;
  gap: 16rpx;
  margin-top: 20rpx;
}

.receipt__actions .mt-btn {
  flex: 1;
}

.receipt__footer {
  margin-top: 16rpx;
  text-align: center;
  font-size: 20rpx;
  color: #a9b4c4;
}

.receipt__canvas {
  position: fixed;
  left: -2000px;
  top: 0;
  pointer-events: none;
}
</style>
