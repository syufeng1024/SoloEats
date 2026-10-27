<script setup lang="ts">
import { onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import {
  getMerchantDetail,
  listMerchants,
  updateMerchantCommission,
  updateMerchantStatus,
  type AdminMerchantListParams,
} from '@/api/merchant'
import type { MerchantDetailView, MerchantView } from '@/api/types'
import {
  MERCHANT_STATUS_MAP,
  MERCHANT_STATUS_OPTIONS,
  statusMeta,
} from '@/constants/businessStatus'
import { debounce } from '@/utils/debounce'
import { formatDateTime } from '@/utils/datetime'

const PAGE_SIZE_OPTIONS = [10, 20, 50]

const loading = ref(false)
const loadError = ref('')
const rows = ref<MerchantView[]>([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(20)

const filters = reactive({
  status: undefined as number | undefined,
  keyword: '',
})
const appliedKeyword = ref('')

const detailVisible = ref(false)
const detailLoading = ref(false)
const detailError = ref('')
const detail = ref<MerchantDetailView | null>(null)

const commissionVisible = ref(false)
const commissionTarget = ref<MerchantView | null>(null)
const commissionPercent = ref(0)
const commissionSaving = ref(false)

watch(
  () => filters.keyword,
  debounce((value: string) => {
    appliedKeyword.value = value
    pageNum.value = 1
    void load()
  }, 300),
)

function errorMessage(error: unknown, fallback: string): string {
  return error instanceof Error ? error.message : fallback
}

/** 后端 MerchantView.contactPhone 返回明文，列表只在展示层做掩码。 */
function maskPhone(phone: string | null | undefined): string {
  if (!phone) {
    return '—'
  }
  if (phone.length < 7) {
    return phone
  }
  return `${phone.slice(0, 3)}****${phone.slice(-4)}`
}

function commissionText(rate: number | null | undefined): string {
  const numeric = Number(rate)
  if (!Number.isFinite(numeric)) {
    return '—'
  }
  return `${(numeric * 100).toFixed(2)}%`
}

async function load(): Promise<void> {
  loading.value = true
  loadError.value = ''
  try {
    const params: AdminMerchantListParams = {
      status: typeof filters.status === 'number' ? filters.status : undefined,
      keyword: appliedKeyword.value.trim() || undefined,
      pageNum: pageNum.value,
      pageSize: pageSize.value,
    }
    const result = await listMerchants(params)
    rows.value = result.list
    total.value = result.total
  } catch (error) {
    rows.value = []
    total.value = 0
    loadError.value = errorMessage(error, '商家列表加载失败')
  } finally {
    loading.value = false
  }
}

function search(): void {
  appliedKeyword.value = filters.keyword
  pageNum.value = 1
  void load()
}

function reset(): void {
  filters.status = undefined
  filters.keyword = ''
  appliedKeyword.value = ''
  pageNum.value = 1
  pageSize.value = 20
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

function canToggle(row: MerchantView): boolean {
  return row.status === 2 || row.status === 4
}

async function toggleStatus(row: MerchantView): Promise<void> {
  const nextStatus = row.status === 2 ? 4 : 2
  const action = nextStatus === 4 ? '停用' : '启用'
  try {
    await ElMessageBox.confirm(
      `确认${action}商家「${row.merchantName}」？${nextStatus === 4 ? '停用后商家端账号将无法继续经营。' : ''}`,
      `${action}商家`,
      { type: 'warning', confirmButtonText: action, cancelButtonText: '取消' },
    )
  } catch {
    return
  }
  try {
    await updateMerchantStatus(row.id, nextStatus)
    ElMessage.success(`已${action}`)
    await load()
  } catch {
    // 拦截器已提示错误
  }
}

async function openDetail(row: MerchantView): Promise<void> {
  detailVisible.value = true
  detail.value = null
  detailError.value = ''
  detailLoading.value = true
  try {
    detail.value = await getMerchantDetail(row.id)
  } catch (error) {
    detailError.value = errorMessage(error, '商家详情加载失败')
  } finally {
    detailLoading.value = false
  }
}

function openCommission(row: MerchantView): void {
  commissionTarget.value = row
  commissionPercent.value = Number((Number(row.commissionRate) * 100).toFixed(2))
  commissionVisible.value = true
}

async function submitCommission(): Promise<void> {
  const target = commissionTarget.value
  if (target === null) {
    return
  }
  const percent = Number(commissionPercent.value)
  if (!Number.isFinite(percent) || percent < 0 || percent > 50) {
    ElMessage.warning('抽佣比例必须在 0% ~ 50% 之间')
    return
  }
  const rate = Number((percent / 100).toFixed(4))
  commissionSaving.value = true
  try {
    await updateMerchantCommission(target.id, rate)
    ElMessage.success('抽佣比例已更新')
    commissionVisible.value = false
    await load()
  } catch {
    // 拦截器已提示错误
  } finally {
    commissionSaving.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="page">
    <div class="page-lead">
      <div>
        <h2>商家列表</h2>
        <p>
          数据源：<span class="mono">GET /api/v1/admin/merchants</span>。启停只允许
          <span class="mono">2 已通过 ↔ 4 已停用</span>；抽佣范围
          <span class="mono">0.0000 ~ 0.5000</span>。手机号列表展示掩码，详情取
          <span class="mono">GET /admin/merchants/{id}</span>。
        </p>
      </div>
      <el-button :icon="Refresh" :loading="loading" @click="load">刷新</el-button>
    </div>

    <el-alert v-if="loadError" type="error" :closable="false" show-icon :title="loadError" />

    <el-card shadow="never">
      <div class="toolbar filter-bar">
        <el-select
          v-model="filters.status"
          placeholder="全部状态"
          clearable
          class="status-select"
          @change="search"
        >
          <el-option
            v-for="option in MERCHANT_STATUS_OPTIONS"
            :key="option.value"
            :label="option.label"
            :value="option.value"
          />
        </el-select>
        <el-input
          v-model="filters.keyword"
          placeholder="商家名称 / 联系人 / 手机号（300ms 防抖）"
          clearable
          class="keyword-input"
          @keyup.enter="search"
        />
        <el-button type="primary" @click="search">查询</el-button>
        <el-button @click="reset">重置</el-button>
      </div>

      <el-table v-loading="loading" :data="rows" border stripe row-key="id">
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column label="商家名称" min-width="180">
          <template #default="{ row }">
            <div class="name-cell">{{ row.merchantName }}</div>
            <div class="muted name-sub mono">{{ row.merchantNo }}</div>
          </template>
        </el-table-column>
        <el-table-column label="联系人" width="110">
          <template #default="{ row }">{{ row.contactName || '—' }}</template>
        </el-table-column>
        <el-table-column label="手机号" width="130">
          <template #default="{ row }">{{ maskPhone(row.contactPhone) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="statusMeta(MERCHANT_STATUS_MAP, row.status).tag" size="small">
              {{ statusMeta(MERCHANT_STATUS_MAP, row.status).label }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="抽佣比例" width="100" align="right">
          <template #default="{ row }">{{ commissionText(row.commissionRate) }}</template>
        </el-table-column>
        <el-table-column label="入驻时间" width="160">
          <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)">详情</el-button>
            <el-button link type="primary" @click="openCommission(row)">改抽佣</el-button>
            <el-button
              link
              :type="row.status === 2 ? 'danger' : 'success'"
              :disabled="!canToggle(row)"
              @click="toggleStatus(row)"
            >
              {{ row.status === 2 ? '停用' : row.status === 4 ? '启用' : '不可启停' }}
            </el-button>
          </template>
        </el-table-column>

        <template #empty>
          <el-empty description="当前筛选条件下没有商家。" :image-size="80" />
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

    <el-dialog v-model="detailVisible" title="商家详情" width="860px" destroy-on-close>
      <div v-loading="detailLoading">
        <el-alert
          v-if="detailError"
          type="error"
          :closable="false"
          show-icon
          :title="detailError"
        />
        <template v-else-if="detail">
          <el-descriptions :column="3" border size="small">
            <el-descriptions-item label="商家名称">
              {{ detail.merchantName }}
            </el-descriptions-item>
            <el-descriptions-item label="商家编号">
              <span class="mono">{{ detail.merchantNo }}</span>
            </el-descriptions-item>
            <el-descriptions-item label="状态">
              <el-tag :type="statusMeta(MERCHANT_STATUS_MAP, detail.status).tag" size="small">
                {{ statusMeta(MERCHANT_STATUS_MAP, detail.status).label }}
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="联系人">
              {{ detail.contactName || '—' }}
            </el-descriptions-item>
            <el-descriptions-item label="手机号">
              {{ maskPhone(detail.contactPhone) }}
            </el-descriptions-item>
            <el-descriptions-item label="邮箱">
              {{ detail.contactEmail || '—' }}
            </el-descriptions-item>
            <el-descriptions-item label="品牌">
              {{ detail.brandName || '—' }}
            </el-descriptions-item>
            <el-descriptions-item label="抽佣比例">
              {{ commissionText(detail.commissionRate) }}
            </el-descriptions-item>
            <el-descriptions-item label="入驻时间">
              {{ formatDateTime(detail.createdAt) }}
            </el-descriptions-item>
            <el-descriptions-item label="经营地址" :span="3">
              {{ [detail.province, detail.city, detail.district, detail.address].filter(Boolean).join(' ') || '—' }}
            </el-descriptions-item>
            <el-descriptions-item label="营业执照号">
              {{ detail.businessLicenseNo || '—' }}
            </el-descriptions-item>
            <el-descriptions-item label="法人">
              {{ detail.legalPerson || '—' }}
            </el-descriptions-item>
            <el-descriptions-item label="法人身份证">
              {{ detail.legalIdCard || '—' }}
            </el-descriptions-item>
            <el-descriptions-item label="审核备注" :span="3">
              {{ detail.auditRemark || '—' }}
            </el-descriptions-item>
          </el-descriptions>

          <div class="section-title">资质材料（{{ detail.qualifications.length }}）</div>
          <el-table :data="detail.qualifications" border size="small">
            <el-table-column prop="id" label="ID" width="70" />
            <el-table-column label="资质类型" width="100">
              <template #default="{ row }">类型 {{ row.qualType }}</template>
            </el-table-column>
            <el-table-column label="文件" min-width="180">
              <template #default="{ row }">
                <el-link
                  v-if="row.fileUrl"
                  type="primary"
                  :href="row.fileUrl"
                  target="_blank"
                  rel="noopener"
                >
                  {{ row.fileName || row.fileUrl }}
                </el-link>
                <span v-else class="muted">{{ row.fileName || '—' }}</span>
              </template>
            </el-table-column>
            <el-table-column label="有效期" width="120">
              <template #default="{ row }">{{ row.expireAt || '—' }}</template>
            </el-table-column>
            <el-table-column prop="status" label="状态码" width="80" />
            <el-table-column label="备注" min-width="140">
              <template #default="{ row }">{{ row.auditRemark || '—' }}</template>
            </el-table-column>
            <template #empty>
              <el-empty description="没有上传资质材料。" :image-size="64" />
            </template>
          </el-table>

          <div class="section-title">审核记录（{{ detail.auditLogs.length }}）</div>
          <el-table :data="detail.auditLogs" border size="small">
            <el-table-column label="流转" width="120">
              <template #default="{ row }">
                {{ row.fromStatus }} → {{ row.toStatus }}
              </template>
            </el-table-column>
            <el-table-column prop="action" label="动作" width="120" />
            <el-table-column label="操作人" width="120">
              <template #default="{ row }">{{ row.operatorName || row.operatorId || '—' }}</template>
            </el-table-column>
            <el-table-column label="备注" min-width="180">
              <template #default="{ row }">{{ row.remark || '—' }}</template>
            </el-table-column>
            <el-table-column label="时间" width="160">
              <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
            </el-table-column>
            <template #empty>
              <el-empty description="没有审核记录。" :image-size="64" />
            </template>
          </el-table>
        </template>
        <el-empty v-else description="暂无详情。" :image-size="80" />
      </div>
    </el-dialog>

    <el-dialog v-model="commissionVisible" title="调整抽佣比例" width="420px">
      <el-form label-width="110px">
        <el-form-item label="商家">
          {{ commissionTarget?.merchantName || '—' }}
        </el-form-item>
        <el-form-item label="抽佣比例">
          <el-input-number
            v-model="commissionPercent"
            :min="0"
            :max="50"
            :precision="2"
            :step="0.5"
            class="percent-input"
          />
          <span class="percent-unit">%</span>
        </el-form-item>
        <el-form-item>
          <span class="muted">
            提交值为小数比例（例如 15% → 0.1500），后端上限 50%。
          </span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="commissionVisible = false">取消</el-button>
        <el-button type="primary" :loading="commissionSaving" @click="submitCommission">
          保存
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
  width: 140px;
}

.keyword-input {
  width: 300px;
}

.name-cell {
  font-weight: 500;
}

.name-sub {
  font-size: 12px;
  margin-top: 2px;
}

.pagination {
  margin-top: 16px;
  justify-content: flex-end;
}

.section-title {
  margin: 18px 0 8px;
  font-weight: 600;
}

.percent-input {
  width: 160px;
}

.percent-unit {
  margin-left: 8px;
  color: var(--app-muted);
}

@media (max-width: 900px) {
  .keyword-input {
    width: 100%;
  }
}
</style>
