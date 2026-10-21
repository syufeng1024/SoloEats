<script setup lang="ts">
import { onMounted, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { Plus, Refresh, Search } from '@element-plus/icons-vue'
import { useAuthStore } from '@/stores/auth'
import {
  createSku,
  listMerchantSkus,
  updateSku,
  updateSkuStock,
} from '@/api/products'
import type { MerchantSkuView } from '@/api/types'
import { formatDateTime } from '@/utils/datetime'
import { debounce } from '@/utils/debounce'
import { formatYuan } from '@/utils/money'

const auth = useAuthStore()

const loading = ref(false)
const loadError = ref('')
const skus = ref<MerchantSkuView[]>([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(10)
const statusFilter = ref<number | undefined>(undefined)
const keyword = ref('')
const appliedKeyword = ref('')
const submitting = ref(false)

const createVisible = ref(false)
const editVisible = ref(false)
const stockVisible = ref(false)

const createForm = reactive({
  spuId: 1,
  skuName: '',
  priceYuan: 0,
  initialStock: 0,
  status: 0,
  specJson: '',
  imageUrl: '',
})

const editForm = reactive({
  skuId: 0,
  skuName: '',
  priceYuan: 0,
  status: 0,
  specJson: '',
  imageUrl: '',
})

const stockForm = reactive({
  skuId: 0,
  skuName: '',
  availableQty: 0,
  soldOut: false,
})

watch(
  keyword,
  debounce((value: string) => {
    appliedKeyword.value = value
    pageNum.value = 1
    void load()
  }, 300),
)

async function load(): Promise<void> {
  loading.value = true
  loadError.value = ''
  try {
    const result = await listMerchantSkus({
      shopId: auth.shopId ?? undefined,
      status: typeof statusFilter.value === 'number' ? statusFilter.value : undefined,
      keyword: appliedKeyword.value.trim() || undefined,
      pageNum: pageNum.value,
      pageSize: pageSize.value,
    })
    skus.value = result.list
    total.value = result.total
  } catch (error) {
    loadError.value = error instanceof Error ? error.message : 'SKU 加载失败'
  } finally {
    loading.value = false
  }
}

function handlePageChange(page: number): void {
  pageNum.value = page
  void load()
}

function openCreate(): void {
  Object.assign(createForm, {
    spuId: 1,
    skuName: '',
    priceYuan: 0,
    initialStock: 0,
    status: 0,
    specJson: '',
    imageUrl: '',
  })
  createVisible.value = true
}

function openEdit(row: MerchantSkuView): void {
  Object.assign(editForm, {
    skuId: row.skuId,
    skuName: row.skuName,
    priceYuan: row.price / 100,
    status: row.status,
    specJson: row.specJson ?? '',
    imageUrl: row.imageUrl ?? '',
  })
  editVisible.value = true
}

function openStock(row: MerchantSkuView): void {
  Object.assign(stockForm, {
    skuId: row.skuId,
    skuName: row.skuName,
    availableQty: row.availableQty,
    soldOut: row.soldOut,
  })
  stockVisible.value = true
}

function priceToCents(value: number): number | null {
  if (!Number.isFinite(value) || value <= 0) {
    return null
  }
  return Math.round(value * 100)
}

function isValidSpec(value: string): boolean {
  if (!value.trim()) {
    return true
  }
  try {
    JSON.parse(value)
    return true
  } catch {
    ElMessage.warning('规格 JSON 格式不合法')
    return false
  }
}

async function submitCreate(): Promise<void> {
  if (!createForm.spuId || createForm.spuId <= 0) {
    ElMessage.warning('SPU ID 必须是正整数')
    return
  }
  if (!createForm.skuName.trim()) {
    ElMessage.warning('SKU 名称不能为空')
    return
  }
  const price = priceToCents(createForm.priceYuan)
  if (price === null) {
    ElMessage.warning('售价必须大于 0')
    return
  }
  if (!isValidSpec(createForm.specJson)) {
    return
  }

  submitting.value = true
  try {
    await createSku({
      spuId: createForm.spuId,
      skuName: createForm.skuName.trim(),
      price,
      status: createForm.status,
      initialStock: createForm.initialStock,
      specJson: createForm.specJson.trim() || undefined,
      imageUrl: createForm.imageUrl.trim() || undefined,
    })
    ElMessage.success('SKU 已创建')
    createVisible.value = false
    await load()
  } catch {
    // 拦截器已提示
  } finally {
    submitting.value = false
  }
}

async function submitEdit(): Promise<void> {
  if (!editForm.skuName.trim()) {
    ElMessage.warning('SKU 名称不能为空')
    return
  }
  const price = priceToCents(editForm.priceYuan)
  if (price === null) {
    ElMessage.warning('售价必须大于 0')
    return
  }
  if (!isValidSpec(editForm.specJson)) {
    return
  }

  submitting.value = true
  try {
    await updateSku(editForm.skuId, {
      skuName: editForm.skuName.trim(),
      price,
      status: editForm.status,
      specJson: editForm.specJson,
      imageUrl: editForm.imageUrl,
    })
    ElMessage.success('SKU 已更新')
    editVisible.value = false
    await load()
  } catch {
    // 拦截器已提示
  } finally {
    submitting.value = false
  }
}

async function submitStock(): Promise<void> {
  submitting.value = true
  try {
    await updateSkuStock(stockForm.skuId, {
      availableQty: stockForm.availableQty,
      soldOut: stockForm.soldOut,
    })
    ElMessage.success('库存已更新')
    stockVisible.value = false
    await load()
  } catch {
    // 拦截器已提示
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
        <h2>商品与库存</h2>
        <p>
          数据源：<span class="mono">GET /api/v1/merchant/skus</span>，
          当前门店 <span class="mono">{{ auth.shopId ?? '未绑定' }}</span>。
          售价按分存储，表单以元输入并按
          <span class="mono">Math.round(元 × 100)</span> 转分。
        </p>
      </div>
      <div class="toolbar">
        <el-button type="primary" :icon="Plus" @click="openCreate">新增 SKU</el-button>
        <el-button :icon="Refresh" :loading="loading" @click="load">刷新</el-button>
      </div>
    </div>

    <el-alert v-if="loadError" type="error" :closable="false" show-icon :title="loadError" />

    <el-card shadow="never">
      <div class="toolbar list-toolbar">
        <el-select
          v-model="statusFilter"
          placeholder="全部状态"
          clearable
          class="status-select"
          @change="load"
        >
          <el-option label="上架" :value="0" />
          <el-option label="下架" :value="1" />
        </el-select>
        <el-input
          v-model="keyword"
          class="keyword-input"
          placeholder="按 SKU 名称过滤（300ms 防抖）"
          clearable
          :prefix-icon="Search"
        />
        <span class="muted">共 {{ total }} 个 SKU</span>
      </div>

      <el-table v-loading="loading" :data="skus" border row-key="skuId">
        <el-table-column prop="skuId" label="SKU ID" width="80" />
        <el-table-column label="名称" min-width="150">
          <template #default="{ row }">
            <div class="name-cell">{{ row.skuName }}</div>
            <div class="mono muted name-time">{{ formatDateTime(row.updatedAt) }}</div>
          </template>
        </el-table-column>
        <el-table-column label="规格" min-width="130">
          <template #default="{ row }">
            <span class="mono spec-text">{{ row.specJson || '—' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="售价" width="90" align="right">
          <template #default="{ row }">{{ formatYuan(row.price) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="80">
          <template #default="{ row }">
            <el-tag :type="row.status === 0 ? 'success' : 'info'" size="small">
              {{ row.status === 0 ? '上架' : '下架' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="availableQty" label="可售" width="70" align="right" />
        <el-table-column prop="lockedQty" label="锁定" width="70" align="right" />
        <el-table-column prop="soldQty" label="已售" width="70" align="right" />
        <el-table-column label="估清" width="70">
          <template #default="{ row }">
            <el-tag :type="row.soldOut ? 'danger' : 'success'" size="small" effect="plain">
              {{ row.soldOut ? '估清' : '正常' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="110" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button link type="primary" @click="openStock(row)">库存</el-button>
          </template>
        </el-table-column>

        <template #empty>
          <el-empty description="当前门店没有 SKU，点击右上角新增。" :image-size="80" />
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

    <el-dialog v-model="createVisible" title="新增 SKU" width="540px" :close-on-click-modal="false">
      <el-form label-width="96px">
        <el-form-item label="SPU ID" required>
          <el-input-number v-model="createForm.spuId" :min="1" :precision="0" :controls="false" />
        </el-form-item>
        <el-form-item label="SKU 名称" required>
          <el-input v-model="createForm.skuName" maxlength="128" show-word-limit />
        </el-form-item>
        <el-form-item label="售价（元）" required>
          <el-input-number v-model="createForm.priceYuan" :min="0.01" :precision="2" :step="1" />
        </el-form-item>
        <el-form-item label="初始库存">
          <el-input-number v-model="createForm.initialStock" :min="0" :precision="0" />
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="createForm.status">
            <el-radio :value="0">上架</el-radio>
            <el-radio :value="1">下架</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="规格 JSON">
          <el-input
            v-model="createForm.specJson"
            type="textarea"
            :rows="3"
            placeholder='例如 {"辣度":"中辣"}'
          />
        </el-form-item>
        <el-form-item label="图片 URL">
          <el-input v-model="createForm.imageUrl" maxlength="512" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitCreate">创建</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="editVisible" title="编辑 SKU" width="540px" :close-on-click-modal="false">
      <el-form label-width="96px">
        <el-form-item label="SKU ID">
          <span class="mono">{{ editForm.skuId }}</span>
        </el-form-item>
        <el-form-item label="SKU 名称" required>
          <el-input v-model="editForm.skuName" maxlength="128" show-word-limit />
        </el-form-item>
        <el-form-item label="售价（元）" required>
          <el-input-number v-model="editForm.priceYuan" :min="0.01" :precision="2" :step="1" />
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="editForm.status">
            <el-radio :value="0">上架</el-radio>
            <el-radio :value="1">下架</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="规格 JSON">
          <el-input v-model="editForm.specJson" type="textarea" :rows="3" />
        </el-form-item>
        <el-form-item label="图片 URL">
          <el-input v-model="editForm.imageUrl" maxlength="512" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitEdit">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="stockVisible" title="库存维护" width="440px" :close-on-click-modal="false">
      <p class="dialog-hint">{{ stockForm.skuName }}（{{ stockForm.skuId }}）</p>
      <el-form label-width="96px">
        <el-form-item label="可售库存">
          <el-input-number v-model="stockForm.availableQty" :min="0" :precision="0" />
        </el-form-item>
        <el-form-item label="估清">
          <el-switch
            v-model="stockForm.soldOut"
            active-text="已估清"
            inactive-text="正常售卖"
          />
        </el-form-item>
      </el-form>
      <p class="dialog-note">
        直接覆盖 available_qty；锁定库存与已售库存由订单/退款链路维护，不在此处修改。
      </p>
      <template #footer>
        <el-button @click="stockVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitStock">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.list-toolbar {
  margin-bottom: 14px;
}

.status-select {
  width: 140px;
}

.keyword-input {
  width: 300px;
}

.name-cell {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.name-time {
  font-size: 11px;
  margin-top: 2px;
}

.spec-text {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.pagination {
  margin-top: 16px;
  justify-content: flex-end;
}

.dialog-hint {
  font-weight: 600;
  margin-bottom: 12px;
}

.dialog-note {
  color: var(--app-muted);
  font-size: 12px;
  line-height: 1.6;
  margin-top: 4px;
}

@media (max-width: 720px) {
  .keyword-input {
    width: 100%;
  }
}
</style>
