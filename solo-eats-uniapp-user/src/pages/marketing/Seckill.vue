<template>
  <view class="seckill">
    <view class="seckill__banner">
      <text class="seckill__banner-title">限时秒杀</text>
      <text class="seckill__banner-desc">
        当前仅开放库存预占，下单与支付链路下一批接入；每人每件商品只能预占一次。
      </text>
    </view>

    <view v-if="loading && sessions.length === 0" class="se-loading">正在加载秒杀场次…</view>

    <view v-else-if="error && sessions.length === 0" class="se-error">
      <text>{{ error }}</text>
      <view class="se-error__retry" @click="load(false)">重新加载</view>
    </view>

    <view v-else-if="sessions.length === 0" class="se-empty">
      <text class="se-empty__title">暂无秒杀场次</text>
      <text>有新场次开放时会在这里展示</text>
    </view>

    <template v-else>
      <view v-if="error" class="seckill__error">
        <text>{{ error }}</text>
        <text class="seckill__error-retry" @click="load(false)">重试</text>
      </view>

      <view v-for="group in groups" :key="group.status" class="seckill__group">
        <view class="seckill__group-head">
          <text class="seckill__group-title" :class="`is-${group.tone}`">{{ group.label }}</text>
          <text class="se-muted">{{ group.sessions.length }} 场</text>
        </view>

        <view
          v-for="session in group.sessions"
          :key="session.id"
          class="se-card seckill__session"
        >
          <view class="se-between seckill__session-head">
            <text class="seckill__activity">{{ session.activityName }}</text>
            <text class="seckill__status" :class="`is-${group.tone}`">{{ group.label }}</text>
          </view>

          <text class="seckill__time">
            {{ formatDateTime(session.sessionStart) }} ~ {{ formatDateTime(session.sessionEnd) }}
          </text>
          <text v-if="session.status === 0 && session.preheatAt" class="seckill__preheat">
            预热开始 {{ formatDateTime(session.preheatAt) }}
          </text>

          <view v-if="session.items.length === 0" class="seckill__items-empty">
            该场次暂未配置商品
          </view>

          <view
            v-for="item in session.items"
            :key="item.itemId"
            class="seckill__item"
          >
            <view class="seckill__item-main">
              <text class="seckill__item-name">商品 SPU {{ item.spuId }}</text>
              <text class="seckill__item-sku">SKU {{ item.skuId }}</text>

              <view class="seckill__price-row">
                <text class="seckill__price">¥{{ fenToYuan(item.seckillPrice) }}</text>
                <text class="seckill__origin">原价 --</text>
              </view>

              <view class="seckill__stock-row">
                <text
                  class="seckill__stock"
                  :class="{ 'is-empty': item.remainStock <= 0 }"
                >
                  剩余 {{ item.remainStock }} / {{ item.seckillStock }}
                </text>
                <text class="seckill__limit">每人限购 {{ item.limitPerUser }} 件</text>
              </view>

              <text v-if="reserved[item.itemId]" class="seckill__reserved">
                已预占 {{ reserved[item.itemId].qty }} 件 · 凭证
                {{ reserved[item.itemId].reserveToken }}
              </text>
            </view>

            <view
              class="seckill__action"
              :class="{
                'is-disabled': !canReserve(session, item),
                'is-reserved': Boolean(reserved[item.itemId]),
              }"
              @click="handleReserve(session, item)"
            >
              {{ buttonLabel(session, item) }}
            </view>
          </view>
        </view>
      </view>
    </template>
  </view>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { onPullDownRefresh, onShow } from '@dcloudio/uni-app'
import { fetchSeckillSessions, reserveSeckill } from '@/api/marketing'
import type {
  SeckillItemVO,
  SeckillReserveResultVO,
  SeckillSessionVO,
} from '@/api/marketing'
import { fenToYuan } from '@/utils/money'
import { formatDateTime } from '@/utils/date'
import { useAuthStore } from '@/stores/auth'

/**
 * 秒杀场次。
 *
 * 后端 status：0 待开始（预热中）/ 1 进行中 / 2 已结束，列表只返回未结束的场次。
 * 「抢购」目前只调用 POST /c/seckill/{itemId}/reserve 预占库存，不下单：
 * 成功后展示 reserveToken（格式 SK + orderId），下单链路下一批接入。
 * 已预占/售罄/未开始/已结束一律禁用按钮，避免无意义请求。
 */

interface StatusGroup {
  status: number
  label: string
  tone: string
}

const STATUS_GROUPS: StatusGroup[] = [
  { status: 0, label: '预热中', tone: 'pending' },
  { status: 1, label: '进行中', tone: 'ongoing' },
  { status: 2, label: '已结束', tone: 'ended' },
]

const auth = useAuthStore()

const sessions = ref<SeckillSessionVO[]>([])
const loading = ref(false)
const error = ref('')
const reserving = ref<Record<number, boolean>>({})
const reserved = ref<Record<number, SeckillReserveResultVO>>({})

let sequence = 0

const groups = computed(() =>
  STATUS_GROUPS.map((group) => ({
    ...group,
    sessions: sessions.value.filter((session) => session.status === group.status),
  })).filter((group) => group.sessions.length > 0),
)

onShow(() => {
  load(false)
})

onPullDownRefresh(() => {
  if (loading.value) {
    uni.stopPullDownRefresh()
    return
  }
  load(false)
})

async function load(silent: boolean): Promise<void> {
  const current = ++sequence
  loading.value = true
  if (!silent) {
    error.value = ''
  }
  try {
    const list = await fetchSeckillSessions(silent)
    if (current !== sequence) {
      return
    }
    sessions.value = list
    error.value = ''
  } catch (err) {
    if (current === sequence) {
      // 保留已有场次，只提示失败
      error.value = (err as Error)?.message || '秒杀场次加载失败'
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

async function handleReserve(session: SeckillSessionVO, item: SeckillItemVO): Promise<void> {
  if (!canReserve(session, item)) {
    uni.showToast({ title: buttonLabel(session, item), icon: 'none' })
    return
  }
  if (!auth.ensureLogin('/pages/marketing/Seckill')) {
    return
  }
  reserving.value = { ...reserving.value, [item.itemId]: true }
  try {
    const result = await reserveSeckill(item.itemId, 1)
    reserved.value = { ...reserved.value, [item.itemId]: result }
    // 预占成功即扣减一次展示库存，下拉刷新会以服务端余量为准
    item.remainStock = Math.max(0, item.remainStock - result.qty)
    uni.showModal({
      title: '预占成功',
      content:
        `已预占 ${result.qty} 件，秒杀价 ¥${fenToYuan(result.seckillPrice)}。` +
        `凭证：${result.reserveToken}。下单与支付链路下一批接入，请先保留凭证。`,
      showCancel: false,
      confirmText: '知道了',
    })
  } catch {
    // 售罄、未开始、超出限购、重复预占等错误由 http 层统一 toast
  } finally {
    const next = { ...reserving.value }
    delete next[item.itemId]
    reserving.value = next
  }
}

function canReserve(session: SeckillSessionVO, item: SeckillItemVO): boolean {
  return (
    session.status === 1 &&
    item.remainStock > 0 &&
    !reserved.value[item.itemId] &&
    !reserving.value[item.itemId]
  )
}

function buttonLabel(session: SeckillSessionVO, item: SeckillItemVO): string {
  if (reserved.value[item.itemId]) {
    return '已预占'
  }
  if (session.status === 2) {
    return '已结束'
  }
  if (session.status === 0) {
    return '未开始'
  }
  if (item.remainStock <= 0) {
    return '已售罄'
  }
  if (reserving.value[item.itemId]) {
    return '预占中…'
  }
  return '抢购'
}
</script>

<style scoped>
.seckill {
  min-height: 100vh;
  padding: 24rpx;
  background-color: #f5f5f7;
}

.seckill__banner {
  padding: 28rpx 32rpx;
  border-radius: 12rpx;
  background: linear-gradient(120deg, #ff6b4c 0%, #e63a3a 100%);
  color: #ffffff;
  box-shadow: 0 8rpx 20rpx rgba(230, 58, 58, 0.22);
}

.seckill__banner-title {
  display: block;
  font-size: 36rpx;
  font-weight: 600;
}

.seckill__banner-desc {
  display: block;
  margin-top: 10rpx;
  font-size: 22rpx;
  line-height: 1.6;
  opacity: 0.92;
}

.seckill__error {
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

.seckill__error-retry {
  color: #ff7a00;
  margin-left: 16rpx;
}

.seckill__group {
  margin-top: 24rpx;
}

.seckill__group-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 8rpx 14rpx;
}

.seckill__group-title {
  font-size: 30rpx;
  font-weight: 600;
  color: #444444;
}

.seckill__group-title.is-ongoing,
.seckill__status.is-ongoing {
  color: #ff4d00;
}

.seckill__group-title.is-pending,
.seckill__status.is-pending {
  color: #ff9a2e;
}

.seckill__group-title.is-ended,
.seckill__status.is-ended {
  color: #a0a0a0;
}

.seckill__session {
  padding: 24rpx;
  margin-bottom: 16rpx;
}

.seckill__session-head {
  gap: 16rpx;
}

.seckill__activity {
  flex: 1;
  font-size: 29rpx;
  font-weight: 600;
  color: #1f1f1f;
}

.seckill__status {
  padding: 4rpx 14rpx;
  border-radius: 6rpx;
  background-color: #f7f7f8;
  font-size: 20rpx;
  flex-shrink: 0;
}

.seckill__time {
  display: block;
  margin-top: 8rpx;
  color: #8a8a8a;
  font-size: 21rpx;
}

.seckill__preheat {
  display: block;
  margin-top: 4rpx;
  color: #ff9a2e;
  font-size: 21rpx;
}

.seckill__items-empty {
  padding: 24rpx 0;
  color: #a0a0a0;
  font-size: 22rpx;
  text-align: center;
}

.seckill__item {
  display: flex;
  align-items: center;
  gap: 20rpx;
  padding: 22rpx 0;
  border-top: 1rpx solid #f5f5f5;
}

.seckill__item-main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 6rpx;
}

.seckill__item-name {
  font-size: 27rpx;
  color: #1f1f1f;
  font-weight: 500;
}

.seckill__item-sku {
  color: #a0a0a0;
  font-size: 20rpx;
}

.seckill__price-row {
  display: flex;
  align-items: baseline;
  gap: 12rpx;
  margin-top: 2rpx;
}

.seckill__price {
  color: #ff4d00;
  font-size: 34rpx;
  font-weight: 600;
}

.seckill__origin {
  color: #b0b0b0;
  font-size: 22rpx;
  text-decoration: line-through;
}

.seckill__stock-row {
  display: flex;
  flex-wrap: wrap;
  gap: 16rpx;
}

.seckill__stock,
.seckill__limit {
  color: #8a8a8a;
  font-size: 21rpx;
}

.seckill__stock.is-empty {
  color: #ff4d4f;
}

.seckill__reserved {
  margin-top: 4rpx;
  color: #ff7a00;
  font-size: 21rpx;
  word-break: break-all;
}

.seckill__action {
  min-width: 140rpx;
  height: 68rpx;
  line-height: 68rpx;
  padding: 0 22rpx;
  border-radius: 34rpx;
  text-align: center;
  background: linear-gradient(135deg, #ff7a4c, #e63a3a);
  color: #ffffff;
  font-size: 25rpx;
  font-weight: 500;
  flex-shrink: 0;
}

.seckill__action.is-disabled {
  background: #e8e8ec;
  color: #a0a0a0;
}

.seckill__action.is-reserved {
  background-color: #fff2e6;
  color: #ff7a00;
}
</style>
