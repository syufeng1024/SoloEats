<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh, Search } from '@element-plus/icons-vue'
import { useAuthStore } from '@/stores/auth'
import { useShopStore } from '@/stores/shop'
import { listMerchantOrders, verifyMerchantPickup } from '@/api/orders'
import type { MerchantOrderView } from '@/api/types'
import { formatDateTime } from '@/utils/datetime'
import { debounce } from '@/utils/debounce'
import { formatYuan } from '@/utils/money'

const auth = useAuthStore()
const shop = useShopStore()

const loading = ref(false)
const loadError = ref('')
const orders = ref<MerchantOrderView[]>([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(10)
const keyword = ref('')
const appliedKeyword = ref('')

const verifyVisible = ref(false)
const verifyOrder = ref<MerchantOrderView | null>(null)
const verifyCode = ref('')
const verifying = ref(false)

const manualOrderId = ref<number | null>(null)
const manualCode = ref('')
const manualSubmitting = ref(false)

watch(
  keyword,
  debounce((value: string) => {
    appliedKeyword.value = value
    pageNum.value = 1
    void load()
  }, 300),
)

watch(
  () => shop.currentShopId,
  () => {
    pageNum.value = 1
    void load()
  },
)

async function load(): Promise<void> {
  loading.value = true
  loadError.value = ''
  try {
    const result = await listMerchantOrders({
      shopId: shop.currentShopId ?? auth.shopId ?? undefined,
      status: 31,
      keyword: appliedKeyword.value.trim() || undefined,
      pageNum: pageNum.value,
      pageSize: pageSize.value,
    })
    // WAIT_USER_PICKUP 只产生于自取单，再按 fulfillmentType 兜底过滤
    orders.value = result.list.filter((row) => row.fulfillmentType === 2)
    total.value = result.total
  } catch (error) {
    loadError.value = error instanceof Error ? error.message : '待取餐订单加载失败'
  } finally {
    loading.value = false
  }
}

function handlePageChange(page: number): void {
  pageNum.value = page
  void load()
}

function openVerify(row: MerchantOrderView): void {
  verifyOrder.value = row
  verifyCode.value = row.pickupCode ?? ''
  verifyVisible.value = true
}

async function submitVerify(): Promise<void> {
  const order = verifyOrder.value
  if (!order) {
    return
  }
  const code = verifyCode.value.trim()
  if (!code) {
    ElMessage.warning('请输入取餐码')
    return
  }
  verifying.value = true
  try {
    await verifyMerchantPickup(order.orderId, code)
    ElMessage.success(`订单 ${order.orderNo} 已核销`)
    verifyVisible.value = false
    await load()
  } catch {
    // 拦截器已提示；重复核销返回 ORDER_STATUS_INVALID，刷新列表即可看到最新状态
  } finally {
    verifying.value = false
  }
}

async function submitManual(): Promise<void> {
  const orderId = manualOrderId.value
  const code = manualCode.value.trim()
  if (orderId === null || !Number.isSafeInteger(orderId) || orderId <= 0) {
    ElMessage.warning('请输入有效的订单 ID')
    return
  }
  if (!code) {
    ElMessage.warning('请输入取餐码')
    return
  }

  try {
    await ElMessageBox.confirm(
      `确认核销订单 ${orderId}，取餐码 ${code}？核销后订单进入已取餐(51)，不可回退。`,
      '确认核销',
      { confirmButtonText: '确认核销', cancelButtonText: '取消', type: 'warning' },
    )
  } catch {
    return
  }

  manualSubmitting.value = true
  try {
    await verifyMerchantPickup(orderId, code)
    ElMessage.success(`订单 ${orderId} 已核销`)
    manualOrderId.value = null
    manualCode.value = ''
    await load()
  } catch {
    // 拦截器已提示
  } finally {
    manualSubmitting.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="page">
    <div class="page-lead">
      <div>
        <h2>取餐核销</h2>
        <p>
          数据源：<span class="mono">GET /api/v1/merchant/orders?status=31</span> +
          <span class="mono">POST /api/v1/merchant/orders/{{ '{orderId}' }}/pickup/verify</span>。
          仅自取单（fulfillmentType=2）会进入待用户取餐(31)。
        </p>
      </div>
      <el-button :icon="Refresh" :loading="loading" @click="load">刷新</el-button>
    </div>

    <el-alert v-if="loadError" type="error" :closable="false" show-icon :title="loadError" />

    <el-card shadow="never">
      <div class="card-title">手工核销</div>
      <el-form class="manual-form" inline @submit.prevent>
        <el-form-item label="订单 ID">
          <el-input-number
            v-model="manualOrderId"
            :min="1"
            :step="1"
            :controls="false"
            placeholder="如 1024"
            class="manual-order"
          />
        </el-form-item>
        <el-form-item label="取餐码">
          <el-input
            v-model="manualCode"
            class="manual-code"
            maxlength="32"
            placeholder="输入顾客出示的取餐码"
            clearable
            @keyup.enter="submitManual"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="manualSubmitting" @click="submitManual">
            核销
          </el-button>
        </el-form-item>
      </el-form>
      <p class="hint">
        后端按 orderId + pickupCode 双校验：取餐码不匹配返回业务错误；订单已到 51 时返回
        ORDER_STATUS_INVALID，属预期结果。
      </p>
    </el-card>

    <el-card shadow="never">
      <div class="toolbar list-toolbar">
        <el-input
          v-model="keyword"
          class="keyword-input"
          placeholder="订单号 / 收货人（300ms 防抖）"
          clearable
          :prefix-icon="Search"
        />
        <span class="muted">待取餐 {{ total }} 笔（当前页展示 {{ orders.length }} 笔）</span>
        <div class="toolbar-spacer" />
        <el-button :icon="Refresh" :loading="loading" @click="load">刷新</el-button>
      </div>

      <el-table v-loading="loading" :data="orders" border row-key="orderId">
        <el-table-column prop="orderNo" label="订单号" width="180">
          <template #default="{ row }">
            <span class="mono">{{ row.orderNo }}</span>
          </template>
        </el-table-column>
        <el-table-column label="取餐码" width="140">
          <template #default="{ row }">
            <span class="pickup-code mono">{{ row.pickupCode || '—' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="实付" width="100" align="right">
          <template #default="{ row }">{{ formatYuan(row.payAmount) }}</template>
        </el-table-column>
        <el-table-column label="顾客" min-width="130">
          <template #default="{ row }">
            <div>{{ row.receiverName || '—' }}</div>
            <div class="mono muted">{{ row.receiverPhone || '—' }}</div>
          </template>
        </el-table-column>
        <el-table-column label="下单时间" width="150">
          <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openVerify(row)">核销</el-button>
          </template>
        </el-table-column>

        <template #empty>
          <el-empty
            description="当前没有待用户取餐的自取单。顾客下单并完成备餐后会出现在这里。"
            :image-size="80"
          />
        </template>
      </el-table>

      <el-pagination
        class="pagination"
        layout="total, prev, pager, next"
        :total="total"
        :page-size="pageSize"
        :current-page="pageNum"
        @current-change="handlePageChange"
      />
    </el-card>

    <el-dialog v-model="verifyVisible" title="取餐核销" width="420px" :close-on-click-modal="false">
      <p class="dialog-hint">
        订单 <span class="mono">{{ verifyOrder?.orderNo ?? '—' }}</span>
        ，取餐码 <span class="mono">{{ verifyOrder?.pickupCode ?? '—' }}</span>
      </p>
      <el-input
        v-model="verifyCode"
        maxlength="32"
        placeholder="请输入或核对取餐码"
        clearable
        @keyup.enter="submitVerify"
      />
      <template #footer>
        <el-button @click="verifyVisible = false">取消</el-button>
        <el-button type="primary" :loading="verifying" @click="submitVerify">
          确认核销
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.list-toolbar {
  margin-bottom: 14px;
}

.keyword-input {
  width: 300px;
}

.manual-form {
  margin-bottom: 0;
}

.manual-order {
  width: 160px;
}

.manual-code {
  width: 240px;
}

.pickup-code {
  font-size: 16px;
  font-weight: 600;
  letter-spacing: 1px;
  color: var(--el-color-primary);
}

.pagination {
  margin-top: 16px;
  justify-content: flex-end;
}

.hint,
.dialog-hint {
  color: var(--app-muted);
  font-size: 13px;
  line-height: 1.7;
}

.dialog-hint {
  margin-bottom: 12px;
}

@media (max-width: 720px) {
  .keyword-input,
  .manual-code,
  .manual-order {
    width: 100%;
  }
}
</style>
