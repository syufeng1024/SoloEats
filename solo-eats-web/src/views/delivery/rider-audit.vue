<script setup lang="ts">
import { onMounted, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh, Search } from '@element-plus/icons-vue'
import {
  auditRiderCertification,
  getRiderDetail,
  listRiders,
  type AuditCertificationPayload,
  type RiderDetailView,
  type RiderListParams,
  type RiderView,
} from '@/api/delivery-admin'
import {
  CERT_RECORD_STATUS,
  RIDER_CERT_STATUS,
  RIDER_CERT_STATUS_OPTIONS,
  RIDER_STATUS,
  RIDER_STATUS_OPTIONS,
  VEHICLE_TYPE,
  labelOf,
  tagOf,
} from '@/constants/deliveryStatus'
import { debounce } from '@/utils/debounce'
import { formatDateTime } from '@/utils/datetime'

const PAGE_SIZE_OPTIONS = [10, 20, 50]

const loading = ref(false)
const loadError = ref('')
const rows = ref<RiderView[]>([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(20)

const filters = reactive({
  /** 默认只看待审核，对应 t_rider.cert_status = 1 */
  certStatus: 1 as number | undefined,
  status: undefined as number | undefined,
  keyword: '',
})
const appliedKeyword = ref('')

const detailVisible = ref(false)
const detailLoading = ref(false)
const detailError = ref('')
const detail = ref<RiderDetailView | null>(null)
const detailRiderId = ref<number | null>(null)

const auditVisible = ref(false)
const auditTarget = ref<RiderView | null>(null)
const auditResult = ref<1 | 2>(1)
const auditRemark = ref('')
const auditSaving = ref(false)

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

async function load(): Promise<void> {
  loading.value = true
  loadError.value = ''
  try {
    const params: RiderListParams = {
      certStatus:
        typeof filters.certStatus === 'number' ? filters.certStatus : undefined,
      status: typeof filters.status === 'number' ? filters.status : undefined,
      keyword: appliedKeyword.value.trim() || undefined,
      pageNum: pageNum.value,
      pageSize: pageSize.value,
    }
    const result = await listRiders(params)
    rows.value = result.list
    total.value = result.total
  } catch (error) {
    rows.value = []
    total.value = 0
    loadError.value = errorMessage(error, '骑手认证列表加载失败')
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
  filters.certStatus = 1
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

async function loadDetail(id: number): Promise<void> {
  detailLoading.value = true
  detailError.value = ''
  try {
    detail.value = await getRiderDetail(id)
  } catch (error) {
    detail.value = null
    detailError.value = errorMessage(error, '骑手详情加载失败')
  } finally {
    detailLoading.value = false
  }
}

function openDetail(row: RiderView): void {
  detailVisible.value = true
  detail.value = null
  detailError.value = ''
  detailRiderId.value = row.id
  void loadDetail(row.id)
}

function openAudit(row: RiderView): void {
  auditTarget.value = row
  auditResult.value = 1
  auditRemark.value = ''
  auditVisible.value = true
}

function openAuditFromDetail(): void {
  if (detail.value) {
    openAudit(detail.value.rider)
  }
}

async function submitAudit(): Promise<void> {
  const target = auditTarget.value
  if (target === null) {
    return
  }
  const remark = auditRemark.value.trim()
  if (auditResult.value === 2 && !remark) {
    ElMessage.warning('驳回必须填写原因')
    return
  }
  const payload: AuditCertificationPayload = {
    status: auditResult.value,
    ...(remark ? { remark } : {}),
  }
  auditSaving.value = true
  try {
    await auditRiderCertification(target.id, payload)
    ElMessage.success(auditResult.value === 1 ? '认证已通过' : '认证已驳回')
    auditVisible.value = false
    await load()
    if (detailVisible.value && detailRiderId.value !== null) {
      await loadDetail(detailRiderId.value)
    }
  } catch {
    // 拦截器已提示错误
  } finally {
    auditSaving.value = false
  }
}

function durationText(seconds: number): string {
  const value = Number(seconds)
  if (!Number.isFinite(value) || value <= 0) {
    return '0 分钟'
  }
  const hours = Math.floor(value / 3600)
  const minutes = Math.floor((value % 3600) / 60)
  if (hours > 0) {
    return `${hours} 小时 ${minutes} 分钟`
  }
  return `${minutes} 分钟`
}

onMounted(load)
</script>

<template>
  <div class="page">
    <div class="page-lead">
      <div>
        <h2>骑手审核</h2>
        <p>
          数据源：<span class="mono">GET /api/v1/admin/riders</span>（默认
          <span class="mono">certStatus=1</span>）与
          <span class="mono">GET /admin/riders/{id}</span>；审核写
          <span class="mono">POST /admin/riders/{id}/certification/audit</span>，只接受
          <span class="mono">status=1 通过 / 2 驳回</span>。列表接口不返回认证提交时间，
          该时间在详情的认证记录中展示。
        </p>
      </div>
      <el-button :icon="Refresh" :loading="loading" @click="load">刷新</el-button>
    </div>

    <el-alert v-if="loadError" type="error" :closable="false" show-icon :title="loadError" />

    <el-card shadow="never">
      <div class="toolbar filter-bar">
        <el-select
          v-model="filters.certStatus"
          placeholder="全部认证状态"
          clearable
          class="status-select"
          @change="search"
        >
          <el-option
            v-for="option in RIDER_CERT_STATUS_OPTIONS"
            :key="option.value"
            :label="option.label"
            :value="option.value"
          />
        </el-select>
        <el-select
          v-model="filters.status"
          placeholder="全部账号状态"
          clearable
          class="status-select"
          @change="search"
        >
          <el-option
            v-for="option in RIDER_STATUS_OPTIONS"
            :key="option.value"
            :label="option.label"
            :value="option.value"
          />
        </el-select>
        <el-input
          v-model="filters.keyword"
          placeholder="骑手编号 / 姓名 / 手机号（300ms 防抖）"
          clearable
          class="keyword-input"
          :prefix-icon="Search"
          @keyup.enter="search"
        />
        <el-button type="primary" @click="search">查询</el-button>
        <el-button @click="reset">重置</el-button>
      </div>

      <el-table v-loading="loading" :data="rows" border stripe row-key="id">
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="riderNo" label="骑手编号" width="150">
          <template #default="{ row }">
            <span class="mono">{{ row.riderNo }}</span>
          </template>
        </el-table-column>
        <el-table-column label="姓名" width="110">
          <template #default="{ row }">{{ row.realName || '—' }}</template>
        </el-table-column>
        <el-table-column label="手机号" width="130">
          <template #default="{ row }">{{ row.phoneMask || '—' }}</template>
        </el-table-column>
        <el-table-column label="车辆" width="90">
          <template #default="{ row }">{{ labelOf(VEHICLE_TYPE, row.vehicleType) }}</template>
        </el-table-column>
        <el-table-column label="认证状态" width="100">
          <template #default="{ row }">
            <el-tag :type="tagOf(RIDER_CERT_STATUS, row.certStatus)" size="small">
              {{ labelOf(RIDER_CERT_STATUS, row.certStatus) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="账号状态" width="90">
          <template #default="{ row }">
            <el-tag :type="tagOf(RIDER_STATUS, row.status)" size="small">
              {{ labelOf(RIDER_STATUS, row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="注册时间" width="160">
          <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)">详情</el-button>
            <el-button
              link
              type="success"
              :disabled="row.certStatus !== 1"
              @click="openAudit(row)"
            >
              审核
            </el-button>
          </template>
        </el-table-column>

        <template #empty>
          <el-empty description="当前筛选条件下没有骑手。" :image-size="80" />
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

    <el-drawer v-model="detailVisible" title="骑手详情" size="720px" destroy-on-close>
      <div v-loading="detailLoading">
        <el-alert
          v-if="detailError"
          type="error"
          :closable="false"
          show-icon
          :title="detailError"
        />
        <template v-else-if="detail">
          <el-descriptions :column="2" border size="small">
            <el-descriptions-item label="骑手编号">
              <span class="mono">{{ detail.rider.riderNo }}</span>
            </el-descriptions-item>
            <el-descriptions-item label="姓名">
              {{ detail.rider.realName || '—' }}
            </el-descriptions-item>
            <el-descriptions-item label="手机号">
              {{ detail.rider.phoneMask || '—' }}
            </el-descriptions-item>
            <el-descriptions-item label="车辆">
              {{ labelOf(VEHICLE_TYPE, detail.rider.vehicleType) }}
            </el-descriptions-item>
            <el-descriptions-item label="认证状态">
              <el-tag :type="tagOf(RIDER_CERT_STATUS, detail.rider.certStatus)" size="small">
                {{ labelOf(RIDER_CERT_STATUS, detail.rider.certStatus) }}
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="账号状态">
              <el-tag :type="tagOf(RIDER_STATUS, detail.rider.status)" size="small">
                {{ labelOf(RIDER_STATUS, detail.rider.status) }}
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="评分">
              {{ Number(detail.rider.rating).toFixed(2) }}
            </el-descriptions-item>
            <el-descriptions-item label="准时率">
              {{ Number(detail.rider.onTimeRate).toFixed(2) }}%
            </el-descriptions-item>
            <el-descriptions-item label="累计单量">
              {{ detail.rider.totalOrders }}
            </el-descriptions-item>
            <el-descriptions-item label="今日在线">
              {{ durationText(detail.todayOnlineSeconds) }}
            </el-descriptions-item>
            <el-descriptions-item label="注册时间">
              {{ formatDateTime(detail.rider.createdAt) }}
            </el-descriptions-item>
            <el-descriptions-item label="最后在线">
              {{ formatDateTime(detail.rider.lastOnlineAt) }}
            </el-descriptions-item>
          </el-descriptions>

          <div class="section-title">
            认证记录（{{ detail.certifications.length }}）
            <el-button
              v-if="detail.rider.certStatus === 1"
              type="success"
              size="small"
              class="audit-button"
              @click="openAuditFromDetail"
            >
              审核当前认证
            </el-button>
          </div>
          <el-table :data="detail.certifications" border size="small">
            <el-table-column prop="id" label="ID" width="70" />
            <el-table-column prop="realName" label="认证姓名" width="110" />
            <el-table-column label="健康证有效期" width="130">
              <template #default="{ row }">{{ row.healthCertExpireAt || '—' }}</template>
            </el-table-column>
            <el-table-column label="提交时间" width="160">
              <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
            </el-table-column>
            <el-table-column label="状态" width="90">
              <template #default="{ row }">
                <el-tag :type="tagOf(CERT_RECORD_STATUS, row.status)" size="small">
                  {{ labelOf(CERT_RECORD_STATUS, row.status) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="审核时间" width="160">
              <template #default="{ row }">{{ formatDateTime(row.auditAt) }}</template>
            </el-table-column>
            <el-table-column label="审核备注" min-width="160">
              <template #default="{ row }">{{ row.auditRemark || '—' }}</template>
            </el-table-column>
            <template #empty>
              <el-empty description="没有认证记录。" :image-size="64" />
            </template>
          </el-table>
        </template>
        <el-empty v-else description="暂无详情。" :image-size="80" />
      </div>
    </el-drawer>

    <el-dialog v-model="auditVisible" title="审核骑手认证" width="480px">
      <el-form label-width="90px">
        <el-form-item label="骑手">
          {{ auditTarget?.realName || '—' }}（{{ auditTarget?.riderNo || '—' }}）
        </el-form-item>
        <el-form-item label="审核结果">
          <el-radio-group v-model="auditResult">
            <el-radio :value="1">通过</el-radio>
            <el-radio :value="2">驳回</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="审核原因">
          <el-input
            v-model="auditRemark"
            type="textarea"
            :rows="3"
            maxlength="200"
            show-word-limit
            :placeholder="auditResult === 2 ? '驳回必填，写明缺失或不合格项' : '通过时可留空'"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="auditVisible = false">取消</el-button>
        <el-button
          :type="auditResult === 1 ? 'success' : 'danger'"
          :loading="auditSaving"
          @click="submitAudit"
        >
          {{ auditResult === 1 ? '确认通过' : '确认驳回' }}
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
  width: 150px;
}

.keyword-input {
  width: 300px;
}

.pagination {
  margin-top: 16px;
  justify-content: flex-end;
}

.section-title {
  display: flex;
  align-items: center;
  gap: 10px;
  margin: 18px 0 8px;
  font-weight: 600;
}

.audit-button {
  margin-left: auto;
}

@media (max-width: 900px) {
  .keyword-input {
    width: 100%;
  }
}
</style>
