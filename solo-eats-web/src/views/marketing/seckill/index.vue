<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import type { TableInstance } from 'element-plus'
import { Plus, Refresh } from '@element-plus/icons-vue'
import {
  createSeckillActivity,
  getSeckillActivity,
  listSeckillActivities,
  updateSeckillActivity,
  type SeckillActivitySavePayload,
  type SeckillActivityView,
  type SeckillItemView,
} from '@/api/promotion'
import {
  SECKILL_STATUS,
  SECKILL_STATUS_OPTIONS,
  labelOf,
  tagOf,
} from '@/constants/marketingStatus'
import { formatDateTime } from '@/utils/datetime'
import { centsToYuan, formatYuan, yuanToFen } from '@/utils/money'

const PAGE_SIZE_OPTIONS = [10, 20]
/** 列表不返回 items，逐行拉详情时的并发上限，避免打满同源连接池 */
const HYDRATE_CONCURRENCY = 5

/**
 * 列表接口的 items 恒为空（后端不展开子表），这里用 null 区分「未加载/加载失败」，
 * 避免把失败渲染成 0 商品 / 0 库存。
 */
type SeckillRow = Omit<SeckillActivityView, 'items'> & {
  items: SeckillItemView[] | null
  itemsLoaded: boolean
  itemsLoading: boolean
}

interface ItemFormRow {
  shopId: number | null
  spuId: number | null
  skuId: number | null
  priceYuan: number | null
  stock: number | null
  limitPerUser: number | null
  /** 已有预占数量，仅用于编辑时禁止删除与校验库存下限 */
  soldQty: number
}

const loading = ref(false)
const hydrating = ref(false)
const loadError = ref('')
const activities = ref<SeckillRow[]>([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(10)
const tableRef = ref<TableInstance>()
let hydrateToken = 0

const filters = reactive({
  status: undefined as number | undefined,
})

const formVisible = ref(false)
const detailLoading = ref(false)
const submitting = ref(false)
const editingId = ref<number | null>(null)

const form = reactive({
  activityName: '',
  sessionStart: null as string | null,
  sessionEnd: null as string | null,
  preheatAt: null as string | null,
})

const itemRows = ref<ItemFormRow[]>([])

function errorMessage(error: unknown, fallback: string): string {
  return error instanceof Error ? error.message : fallback
}

async function load(): Promise<void> {
  const token = ++hydrateToken
  loading.value = true
  // 新一轮请求接管补全状态；旧 hydrate 任务的 finally 会因令牌不符而跳过清理
  hydrating.value = false
  loadError.value = ''
  try {
    const result = await listSeckillActivities({
      status: filters.status,
      pageNum: pageNum.value,
      pageSize: pageSize.value,
    })
    if (token !== hydrateToken) {
      return
    }
    activities.value = result.list.map((activity) => ({
      ...activity,
      items: null,
      itemsLoaded: false,
      itemsLoading: false,
    }))
    total.value = result.total
  } catch (error) {
    if (token === hydrateToken) {
      loadError.value = errorMessage(error, '秒杀场次加载失败')
    }
    return
  } finally {
    if (token === hydrateToken) {
      loading.value = false
    }
  }
  void hydrateItems(token)
}

/**
 * 当前页逐行补详情。失败的行保持 items=null（显示 —），可通过「商品明细」重试。
 * 令牌变化（翻页/改 pageSize）后丢弃旧响应，避免写脏新行。
 */
async function hydrateItems(token: number): Promise<void> {
  const rows = activities.value
  if (rows.length === 0) {
    return
  }
  hydrating.value = true
  let cursor = 0

  async function worker(): Promise<void> {
    while (cursor < rows.length) {
      const index = cursor
      cursor += 1
      const row = rows[index]
      if (!row || token !== hydrateToken) {
        return
      }
      row.itemsLoading = true
      try {
        const detail = await getSeckillActivity(row.id)
        if (token !== hydrateToken) {
          return
        }
        // 只合并 items，保留列表时刻的 status 快照
        row.items = detail.items
        row.itemsLoaded = true
      } catch {
        // 错误提示由 http 响应拦截器统一输出，此处不重复弹
      } finally {
        row.itemsLoading = false
      }
    }
  }

  const workerCount = Math.min(HYDRATE_CONCURRENCY, rows.length)
  try {
    await Promise.all(Array.from({ length: workerCount }, () => worker()))
  } finally {
    if (token === hydrateToken) {
      hydrating.value = false
    }
  }
}

async function loadItems(row: SeckillRow): Promise<void> {
  if (row.itemsLoaded || row.itemsLoading) {
    return
  }
  row.itemsLoading = true
  try {
    const detail = await getSeckillActivity(row.id)
    row.items = detail.items
    row.itemsLoaded = true
  } catch {
    // 错误提示由 http 响应拦截器统一输出，此处不重复弹
  } finally {
    row.itemsLoading = false
  }
}

function handleExpandChange(row: SeckillRow, expandedRows: SeckillRow[]): void {
  if (expandedRows.includes(row) && !row.itemsLoaded) {
    void loadItems(row)
  }
}

function toggleItems(row: SeckillRow): void {
  tableRef.value?.toggleRowExpansion(row)
  if (!row.itemsLoaded) {
    void loadItems(row)
  }
}

function search(): void {
  pageNum.value = 1
  void load()
}

function reset(): void {
  filters.status = undefined
  pageNum.value = 1
  void load()
}

function handlePageChange(page: number): void {
  pageNum.value = page
  void load()
}

function handleSizeChange(size: number): void {
  pageSize.value = size
  pageNum.value = 1
  void load()
}

function itemCountText(row: SeckillRow): string {
  return row.items === null ? '—' : String(row.items.length)
}

function stockSummary(row: SeckillRow): string {
  if (row.items === null) {
    return '—'
  }
  const stock = row.items.reduce((sum, item) => sum + item.seckillStock, 0)
  const sold = row.items.reduce((sum, item) => sum + item.soldQty, 0)
  return `${sold} / ${stock}`
}

function defaultItemRow(): ItemFormRow {
  return {
    shopId: null,
    spuId: null,
    skuId: null,
    priceYuan: null,
    stock: null,
    limitPerUser: 1,
    soldQty: 0,
  }
}

function resetForm(): void {
  editingId.value = null
  Object.assign(form, {
    activityName: '',
    sessionStart: null,
    sessionEnd: null,
    preheatAt: null,
  })
  itemRows.value = [defaultItemRow()]
}

function openCreate(): void {
  resetForm()
  formVisible.value = true
}

async function openEdit(row: SeckillRow): Promise<void> {
  resetForm()
  editingId.value = row.id
  detailLoading.value = true
  formVisible.value = true
  try {
    const detail = await getSeckillActivity(row.id)
    Object.assign(form, {
      activityName: detail.activityName,
      sessionStart: detail.sessionStart,
      sessionEnd: detail.sessionEnd,
      preheatAt: detail.preheatAt,
    })
    itemRows.value = detail.items.map((item) => ({
      shopId: item.shopId,
      spuId: item.spuId,
      skuId: item.skuId,
      priceYuan: centsToYuan(item.seckillPrice),
      stock: item.seckillStock,
      limitPerUser: item.limitPerUser,
      soldQty: item.soldQty,
    }))
    if (itemRows.value.length === 0) {
      itemRows.value = [defaultItemRow()]
    }
  } catch {
    // 错误提示由 http 响应拦截器统一输出，此处不重复弹
    formVisible.value = false
  } finally {
    detailLoading.value = false
  }
}

function addItem(): void {
  itemRows.value.push(defaultItemRow())
}

function removeItem(index: number): void {
  const row = itemRows.value[index]
  if (!row || row.soldQty > 0 || itemRows.value.length <= 1) {
    return
  }
  itemRows.value.splice(index, 1)
}

function isPositiveInteger(value: number | null): value is number {
  return value !== null && Number.isInteger(value) && value > 0
}

function validateForm(): string | null {
  if (!form.activityName.trim()) {
    return '活动名称不能为空'
  }
  if (form.activityName.trim().length > 64) {
    return '活动名称不能超过 64 个字符'
  }
  if (!form.sessionStart || !form.sessionEnd) {
    return '场次开始与结束时间不能为空'
  }
  if (
    !(new Date(form.sessionEnd).getTime() > new Date(form.sessionStart).getTime())
  ) {
    return '场次结束时间必须晚于开始时间'
  }
  if (itemRows.value.length === 0) {
    return '至少配置 1 个秒杀商品'
  }

  const skuIds = new Set<number>()
  for (let index = 0; index < itemRows.value.length; index += 1) {
    const row = itemRows.value[index]
    const position = index + 1
    if (!isPositiveInteger(row.shopId)) {
      return `第 ${position} 行门店 ID 必须是正整数`
    }
    if (!isPositiveInteger(row.spuId)) {
      return `第 ${position} 行 SPU ID 必须是正整数`
    }
    if (!isPositiveInteger(row.skuId)) {
      return `第 ${position} 行 SKU ID 必须是正整数`
    }
    if (row.priceYuan === null || !(row.priceYuan > 0)) {
      return `第 ${position} 行秒杀价必须大于 0`
    }
    if (row.stock === null || !Number.isInteger(row.stock) || row.stock < 1) {
      return `第 ${position} 行秒杀库存必须是正整数`
    }
    if (row.stock < row.soldQty) {
      return `第 ${position} 行秒杀库存不能小于已售/已预占数量 ${row.soldQty}`
    }
    if (
      row.limitPerUser === null ||
      !Number.isInteger(row.limitPerUser) ||
      row.limitPerUser < 1
    ) {
      return `第 ${position} 行每人限购必须 ≥ 1`
    }
    if (skuIds.has(row.skuId)) {
      return `第 ${position} 行 SKU ID 重复（${row.skuId}）`
    }
    skuIds.add(row.skuId)
  }

  return null
}

function toPayload(): SeckillActivitySavePayload {
  return {
    activityName: form.activityName.trim(),
    sessionStart: form.sessionStart ?? '',
    sessionEnd: form.sessionEnd ?? '',
    preheatAt: form.preheatAt,
    items: itemRows.value.map((row) => ({
      shopId: row.shopId ?? 0,
      spuId: row.spuId ?? 0,
      skuId: row.skuId ?? 0,
      seckillPrice: yuanToFen(row.priceYuan),
      seckillStock: row.stock ?? 0,
      limitPerUser: row.limitPerUser ?? 0,
    })),
  }
}

async function submitForm(): Promise<void> {
  if (detailLoading.value) {
    return
  }
  const invalid = validateForm()
  if (invalid) {
    ElMessage.warning(invalid)
    return
  }

  submitting.value = true
  try {
    if (editingId.value === null) {
      await createSeckillActivity(toPayload())
      ElMessage.success('秒杀场次已创建，状态按时间窗推导')
    } else {
      await updateSeckillActivity(editingId.value, toPayload())
      ElMessage.success('秒杀场次已更新')
    }
    formVisible.value = false
    await load()
  } catch {
    // 错误提示由 http 响应拦截器统一输出，此处不重复弹（否则同一条错误会弹两次）
  } finally {
    submitting.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="page">
    <div class="page-lead">
      <div>
        <h2>秒杀活动</h2>
        <p>
          数据源：<span class="mono">/api/v1/admin/seckill/activities</span>，权限码
          <span class="mono">marketing:seckill:list</span> /
          <span class="mono">marketing:seckill:update</span>。
          金额单位分，表单以元输入；状态由场次时间窗推导
          <span class="mono">0 待开始 / 1 进行中 / 2 已结束</span>，无独立状态变更接口。
        </p>
      </div>
      <div class="toolbar">
        <el-button type="primary" :icon="Plus" @click="openCreate">新建场次</el-button>
        <el-button :icon="Refresh" :loading="loading" @click="load">刷新</el-button>
      </div>
    </div>

    <el-alert v-if="loadError" type="error" :closable="false" show-icon :title="loadError" />

    <el-card shadow="never">
      <div class="toolbar filter-bar">
        <el-select v-model="filters.status" placeholder="全部状态" clearable class="status-select">
          <el-option
            v-for="option in SECKILL_STATUS_OPTIONS"
            :key="option.value"
            :label="option.label"
            :value="option.value"
          />
        </el-select>
        <el-button type="primary" @click="search">查询</el-button>
        <el-button @click="reset">重置</el-button>
        <span v-if="hydrating" class="muted hydrate-hint">正在加载商品明细…</span>
      </div>

      <el-table
        ref="tableRef"
        v-loading="loading"
        :data="activities"
        border
        stripe
        row-key="id"
        @expand-change="handleExpandChange"
      >
        <el-table-column type="expand">
          <template #default="{ row }">
            <div class="expand-wrap">
              <el-table :data="row.items ?? []" border size="small">
                <el-table-column prop="id" label="明细 ID" width="90" />
                <el-table-column prop="shopId" label="门店 ID" width="100" />
                <el-table-column prop="spuId" label="SPU ID" width="100" />
                <el-table-column prop="skuId" label="SKU ID" width="100" />
                <el-table-column label="秒杀价" width="110" align="right">
                  <template #default="{ row: item }">
                    <span class="amount">{{ formatYuan(item.seckillPrice) }}</span>
                  </template>
                </el-table-column>
                <el-table-column prop="seckillStock" label="秒杀库存" width="100" align="right" />
                <el-table-column prop="soldQty" label="已售" width="90" align="right" />
                <el-table-column label="剩余" width="90" align="right">
                  <template #default="{ row: item }">
                    <span :class="{ 'stock-danger': item.remainStock === 0 }">
                      {{ item.remainStock }}
                    </span>
                  </template>
                </el-table-column>
                <el-table-column prop="limitPerUser" label="每人限购" width="100" align="right" />
                <template #empty>
                  <el-empty
                    :description="row.itemsLoaded ? '该场次暂无商品' : '商品明细未加载，点击「商品明细」重试'"
                    :image-size="60"
                  />
                </template>
              </el-table>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="activityName" label="活动名称" min-width="170" show-overflow-tooltip />
        <el-table-column label="场次开始 ~ 结束" min-width="280">
          <template #default="{ row }">
            {{ formatDateTime(row.sessionStart) }} ~ {{ formatDateTime(row.sessionEnd) }}
          </template>
        </el-table-column>
        <el-table-column label="预热时间" width="160">
          <template #default="{ row }">{{ formatDateTime(row.preheatAt) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="tagOf(SECKILL_STATUS, row.status)" size="small">
              {{ labelOf(SECKILL_STATUS, row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="商品数" width="90" align="right">
          <template #default="{ row }">{{ itemCountText(row) }}</template>
        </el-table-column>
        <el-table-column label="总库存（已售 / 总量）" min-width="170" align="right">
          <template #default="{ row }">
            <span class="amount">{{ stockSummary(row) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <div class="row-actions">
              <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
              <el-button link type="primary" @click="toggleItems(row)">商品明细</el-button>
            </div>
          </template>
        </el-table-column>

        <template #empty>
          <el-empty description="当前筛选条件下没有秒杀场次，点击右上角新建。" :image-size="80" />
        </template>
      </el-table>

      <el-pagination
        class="pagination"
        layout="total, sizes, prev, pager, next"
        :total="total"
        :page-size="pageSize"
        :page-sizes="PAGE_SIZE_OPTIONS"
        :current-page="pageNum"
        @current-change="handlePageChange"
        @size-change="handleSizeChange"
      />
    </el-card>

    <el-dialog
      v-model="formVisible"
      :title="editingId === null ? '新建秒杀场次' : `编辑场次 #${editingId}`"
      width="900px"
      :close-on-click-modal="false"
    >
      <el-form v-loading="detailLoading" label-width="110px">
        <el-form-item label="活动名称" required>
          <el-input v-model="form.activityName" maxlength="64" show-word-limit />
        </el-form-item>
        <el-form-item label="场次开始" required>
          <el-date-picker
            v-model="form.sessionStart"
            type="datetime"
            value-format="YYYY-MM-DDTHH:mm:ss"
            placeholder="开始时间"
            class="full-width"
          />
        </el-form-item>
        <el-form-item label="场次结束" required>
          <el-date-picker
            v-model="form.sessionEnd"
            type="datetime"
            value-format="YYYY-MM-DDTHH:mm:ss"
            placeholder="结束时间"
            class="full-width"
          />
        </el-form-item>
        <el-form-item label="预热时间">
          <el-date-picker
            v-model="form.preheatAt"
            type="datetime"
            value-format="YYYY-MM-DDTHH:mm:ss"
            placeholder="不填则不做预热提醒"
            class="full-width"
          />
        </el-form-item>

        <el-form-item label="商品配置" required>
          <div class="block">
            <el-table :data="itemRows" border size="small">
              <el-table-column label="门店 ID" min-width="110">
                <template #default="{ row }">
                  <el-input-number
                    v-model="row.shopId"
                    :min="1"
                    :precision="0"
                    :controls="false"
                    size="small"
                    placeholder="门店"
                    class="full-width"
                  />
                </template>
              </el-table-column>
              <el-table-column label="SPU ID" min-width="110">
                <template #default="{ row }">
                  <el-input-number
                    v-model="row.spuId"
                    :min="1"
                    :precision="0"
                    :controls="false"
                    size="small"
                    placeholder="SPU"
                    class="full-width"
                  />
                </template>
              </el-table-column>
              <el-table-column label="SKU ID" min-width="110">
                <template #default="{ row }">
                  <el-input-number
                    v-model="row.skuId"
                    :min="1"
                    :precision="0"
                    :controls="false"
                    size="small"
                    placeholder="SKU"
                    class="full-width"
                  />
                </template>
              </el-table-column>
              <el-table-column label="秒杀价（元）" min-width="120">
                <template #default="{ row }">
                  <el-input-number
                    v-model="row.priceYuan"
                    :min="0.01"
                    :precision="2"
                    :controls="false"
                    size="small"
                    class="full-width"
                  />
                </template>
              </el-table-column>
              <el-table-column label="秒杀库存" min-width="110">
                <template #default="{ row }">
                  <el-input-number
                    v-model="row.stock"
                    :min="1"
                    :precision="0"
                    :controls="false"
                    size="small"
                    class="full-width"
                  />
                </template>
              </el-table-column>
              <el-table-column label="每人限购" min-width="100">
                <template #default="{ row }">
                  <el-input-number
                    v-model="row.limitPerUser"
                    :min="1"
                    :precision="0"
                    :controls="false"
                    size="small"
                    class="full-width"
                  />
                </template>
              </el-table-column>
              <el-table-column label="操作" width="70">
                <template #default="{ row, $index }">
                  <el-button
                    link
                    type="danger"
                    :disabled="itemRows.length <= 1 || row.soldQty > 0"
                    @click="removeItem($index)"
                  >
                    删除
                  </el-button>
                </template>
              </el-table-column>
              <template #empty>
                <el-empty description="至少添加 1 个秒杀商品" :image-size="60" />
              </template>
            </el-table>
            <el-button link type="primary" :icon="Plus" class="add-row" @click="addItem">
              新增商品
            </el-button>
            <div class="form-tip">
              秒杀价为独立活动价，提交时元→分；秒杀库存与 t_sku_stock 相互独立。
              <template v-if="editingId !== null">
                已产生预占的商品不可移除，且秒杀库存不得低于已售数量。
              </template>
            </div>
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button
          type="primary"
          :loading="submitting"
          :disabled="detailLoading"
          @click="submitForm"
        >
          {{ editingId === null ? '创建' : '保存' }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.filter-bar {
  margin-bottom: 14px;
}

.status-select {
  width: 145px;
}

.hydrate-hint {
  font-size: 12px;
}

.amount {
  font-variant-numeric: tabular-nums;
  font-weight: 600;
}

.stock-danger {
  color: var(--el-color-danger);
  font-weight: 600;
}

.expand-wrap {
  padding: 8px 12px 12px;
}

.row-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 2px;
  align-items: center;
}

.pagination {
  margin-top: 16px;
  justify-content: flex-end;
}

.full-width {
  width: 100%;
}

.block {
  width: 100%;
}

.add-row {
  margin-top: 8px;
}

.form-tip {
  width: 100%;
  color: var(--app-muted);
  font-size: 12px;
  line-height: 1.6;
  margin-top: 6px;
}

@media (max-width: 900px) {
  .status-select {
    width: 100%;
  }
}
</style>
