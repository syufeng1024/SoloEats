<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowLeft, CircleCheck, CircleClose, Refresh } from '@element-plus/icons-vue'
import {
  approveMerchant,
  getMerchantDetail,
  rejectMerchant,
  updateMerchantCommission,
  updateMerchantStatus,
} from '@/api/merchant'
import type { MerchantDetailView } from '@/api/types'
import { MERCHANT_STATUS_MAP, statusMeta } from '@/constants/businessStatus'
import { formatDateTime } from '@/utils/datetime'

const QUAL_TYPE_LABELS: Record<number, string> = {
  1: '营业执照',
  2: '食品经营许可证',
  3: '法人身份证正面',
  4: '法人身份证反面',
  5: '门店门头照',
  6: '店内照',
  7: '其他',
}

const route = useRoute()
const router = useRouter()

const merchantId = Number(route.params.id)
const idValid = Number.isInteger(merchantId) && merchantId > 0

const loading = ref(false)
const loadError = ref('')
const detail = ref<MerchantDetailView | null>(null)
const submitting = ref(false)

const approveForm = reactive({
  commissionRate: 0.06,
  merchantLevel: 1,
  adminUsername: '',
  initialPassword: '',
  remark: '',
})

const rejectVisible = ref(false)
const rejectRemark = ref('')

const commissionForm = reactive({ value: 0.06 })

const canAudit = computed(() => detail.value?.status === 1)
const canToggleStatus = computed(
  () => detail.value?.status === 2 || detail.value?.status === 4,
)
const nextStatus = computed(() => (detail.value?.status === 2 ? 4 : 2))

async function load(): Promise<void> {
  if (!idValid) {
    loadError.value = '路由参数 id 不是正整数'
    return
  }
  loading.value = true
  loadError.value = ''
  try {
    const result = await getMerchantDetail(merchantId)
    detail.value = result
    approveForm.commissionRate = Number(result.commissionRate ?? 0.06)
    approveForm.merchantLevel = result.level ?? 1
    approveForm.adminUsername = ''
    approveForm.initialPassword = ''
    approveForm.remark = ''
    commissionForm.value = Number(result.commissionRate ?? 0.06)
  } catch (error) {
    loadError.value = error instanceof Error ? error.message : '商家详情加载失败'
  } finally {
    loading.value = false
  }
}

function validateApprove(): string | null {
  if (!(approveForm.commissionRate >= 0 && approveForm.commissionRate <= 0.5)) {
    return '抽佣比例必须在 0.0000 ~ 0.5000 之间'
  }
  if (!Number.isInteger(approveForm.merchantLevel)
    || approveForm.merchantLevel < 1
    || approveForm.merchantLevel > 5) {
    return '商家等级只能是 1~5'
  }
  const username = approveForm.adminUsername.trim()
  if (username && !/^[A-Za-z0-9_.@-]{3,64}$/.test(username)) {
    return '管理员账号只能包含字母、数字、下划线、点、@ 与中划线，长度 3~64'
  }
  const password = approveForm.initialPassword
  if (password.length < 8 || password.length > 64) {
    return '初始密码长度必须是 8~64'
  }
  if (approveForm.remark.length > 512) {
    return '审核备注不能超过 512 字'
  }
  return null
}

async function submitApprove(): Promise<void> {
  const invalid = validateApprove()
  if (invalid) {
    ElMessage.warning(invalid)
    return
  }

  try {
    await ElMessageBox.confirm(
      '通过后将为该商家创建租户与商家管理员账号，并把 status 置为 2（已通过）。初始密码只在本次请求内使用。',
      '确认审核通过',
      { confirmButtonText: '通过并创建账号', cancelButtonText: '取消', type: 'warning' },
    )
  } catch {
    return
  }

  submitting.value = true
  try {
    await approveMerchant(merchantId, {
      commissionRate: approveForm.commissionRate,
      merchantLevel: approveForm.merchantLevel,
      adminUsername: approveForm.adminUsername.trim() || undefined,
      initialPassword: approveForm.initialPassword,
      remark: approveForm.remark.trim() || undefined,
    })
    ElMessage.success('审核通过，商家管理员账号已创建')
    await load()
  } catch {
    // 拦截器已提示
  } finally {
    submitting.value = false
  }
}

function openReject(): void {
  rejectRemark.value = ''
  rejectVisible.value = true
}

async function submitReject(): Promise<void> {
  const remark = rejectRemark.value.trim()
  if (remark.length < 5 || remark.length > 512) {
    ElMessage.warning('驳回原因必须是 5~512 字')
    return
  }

  submitting.value = true
  try {
    await rejectMerchant(merchantId, remark)
    ElMessage.success('已驳回，商家可修改资料后重新提交')
    rejectVisible.value = false
    await load()
  } catch {
    // 拦截器已提示
  } finally {
    submitting.value = false
  }
}

async function toggleStatus(): Promise<void> {
  if (!detail.value) {
    return
  }
  const target = nextStatus.value
  const action = target === 4 ? '停用' : '启用'
  try {
    await ElMessageBox.confirm(
      `将商家「${detail.value.merchantName}」${action}，状态会变为 ${target}。`,
      `确认${action}`,
      { confirmButtonText: action, cancelButtonText: '取消', type: 'warning' },
    )
  } catch {
    return
  }

  submitting.value = true
  try {
    await updateMerchantStatus(merchantId, target)
    ElMessage.success(`商家已${action}`)
    await load()
  } catch {
    // 拦截器已提示
  } finally {
    submitting.value = false
  }
}

async function saveCommission(): Promise<void> {
  if (!(commissionForm.value >= 0 && commissionForm.value <= 0.5)) {
    ElMessage.warning('抽佣比例必须在 0.0000 ~ 0.5000 之间')
    return
  }
  submitting.value = true
  try {
    await updateMerchantCommission(merchantId, commissionForm.value)
    ElMessage.success('抽佣比例已更新，仅影响后续结算')
    await load()
  } catch {
    // 拦截器已提示
  } finally {
    submitting.value = false
  }
}

function back(): void {
  void router.push('/merchant/apply')
}

onMounted(load)
</script>

<template>
  <div class="page">
    <div class="page-lead">
      <div class="lead-title">
        <el-button :icon="ArrowLeft" text @click="back">返回列表</el-button>
        <div>
          <h2>商家入驻审核</h2>
          <p>
            数据源：<span class="mono">GET /api/v1/admin/merchants/{{ merchantId }}</span>。
            审核通过会调用 <span class="mono">POST /merchants/{id}/approve</span>，
            驳回调用 <span class="mono">POST /merchants/{id}/reject</span>。
          </p>
        </div>
      </div>
      <el-button :icon="Refresh" :loading="loading" @click="load">刷新</el-button>
    </div>

    <el-alert v-if="loadError" type="error" :closable="false" show-icon :title="loadError" />

    <template v-if="detail">
      <el-card shadow="never">
        <template #header>
          <div class="card-head">
            <span class="card-title">{{ detail.merchantName }}（{{ detail.merchantNo }}）</span>
            <el-tag :type="statusMeta(MERCHANT_STATUS_MAP, detail.status).tag">
              {{ statusMeta(MERCHANT_STATUS_MAP, detail.status).label }}
            </el-tag>
          </div>
        </template>

        <el-descriptions :column="3" border size="small">
          <el-descriptions-item label="商家 ID">{{ detail.id }}</el-descriptions-item>
          <el-descriptions-item label="品牌">{{ detail.brandName || '—' }}</el-descriptions-item>
          <el-descriptions-item label="平台品类">{{ detail.categoryId ?? '—' }}</el-descriptions-item>
          <el-descriptions-item label="联系人">{{ detail.contactName }}</el-descriptions-item>
          <el-descriptions-item label="联系电话">
            <span class="mono">{{ detail.contactPhone }}</span>
          </el-descriptions-item>
          <el-descriptions-item label="联系邮箱">{{ detail.contactEmail || '—' }}</el-descriptions-item>
          <el-descriptions-item label="营业执照号">
            <span class="mono">{{ detail.businessLicenseNo || '—' }}</span>
          </el-descriptions-item>
          <el-descriptions-item label="法人">{{ detail.legalPerson || '—' }}</el-descriptions-item>
          <el-descriptions-item label="法人身份证">
            <span class="mono">{{ detail.legalIdCard || '—' }}</span>
          </el-descriptions-item>
          <el-descriptions-item label="地区" :span="2">
            {{ [detail.province, detail.city, detail.district].filter(Boolean).join(' / ') || '—' }}
          </el-descriptions-item>
          <el-descriptions-item label="地址">{{ detail.address || '—' }}</el-descriptions-item>
          <el-descriptions-item label="抽佣比例">
            {{ (Number(detail.commissionRate) * 100).toFixed(2) }}%
          </el-descriptions-item>
          <el-descriptions-item label="商家等级">{{ detail.level }}</el-descriptions-item>
          <el-descriptions-item label="提交时间">
            {{ formatDateTime(detail.createdAt) }}
          </el-descriptions-item>
          <el-descriptions-item label="审核时间">
            {{ formatDateTime(detail.auditAt) }}
          </el-descriptions-item>
          <el-descriptions-item label="审核备注" :span="2">
            {{ detail.auditRemark || '—' }}
          </el-descriptions-item>
        </el-descriptions>
      </el-card>

      <el-card shadow="never">
        <template #header>
          <span class="card-title">资质材料（{{ detail.qualifications.length }}）</span>
        </template>
        <el-table :data="detail.qualifications" border size="small">
          <el-table-column label="类型" width="150">
            <template #default="{ row }">
              {{ QUAL_TYPE_LABELS[row.qualType] ?? `类型 ${row.qualType}` }}
            </template>
          </el-table-column>
          <el-table-column prop="fileName" label="文件名" min-width="180">
            <template #default="{ row }">{{ row.fileName || '—' }}</template>
          </el-table-column>
          <el-table-column label="文件地址" min-width="260">
            <template #default="{ row }">
              <el-link
                v-if="row.fileUrl"
                type="primary"
                :href="row.fileUrl"
                target="_blank"
                rel="noopener noreferrer"
              >
                查看
              </el-link>
              <span v-else class="muted">—</span>
            </template>
          </el-table-column>
          <el-table-column label="有效期" width="115">
            <template #default="{ row }">{{ row.expireAt || '—' }}</template>
          </el-table-column>
          <el-table-column label="上传时间" width="150">
            <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
          </el-table-column>
          <template #empty>
            <el-empty description="该商家尚未上传资质材料。" :image-size="70" />
          </template>
        </el-table>
      </el-card>

      <el-card v-if="canAudit" shadow="never">
        <template #header>
          <span class="card-title">审核操作</span>
        </template>

        <el-form label-width="130px" class="audit-form">
          <el-form-item label="抽佣比例" required>
            <el-input-number
              v-model="approveForm.commissionRate"
              :min="0"
              :max="0.5"
              :step="0.01"
              :precision="4"
            />
            <span class="field-hint">0.0600 表示 6%，上限 0.5000</span>
          </el-form-item>
          <el-form-item label="商家等级" required>
            <el-input-number
              v-model="approveForm.merchantLevel"
              :min="1"
              :max="5"
              :precision="0"
            />
          </el-form-item>
          <el-form-item label="管理员账号">
            <el-input
              v-model="approveForm.adminUsername"
              maxlength="64"
              placeholder="留空则由后端按商家编号生成"
              clearable
            />
          </el-form-item>
          <el-form-item label="初始密码" required>
            <el-input
              v-model="approveForm.initialPassword"
              type="password"
              show-password
              maxlength="64"
              placeholder="8~64 位，仅本次请求使用，不会写入操作日志"
            />
          </el-form-item>
          <el-form-item label="审核备注">
            <el-input
              v-model="approveForm.remark"
              type="textarea"
              :rows="2"
              maxlength="512"
              show-word-limit
            />
          </el-form-item>
          <el-form-item>
            <el-button
              type="primary"
              :icon="CircleCheck"
              :loading="submitting"
              @click="submitApprove"
            >
              审核通过
            </el-button>
            <el-button
              type="danger"
              :icon="CircleClose"
              :disabled="submitting"
              @click="openReject"
            >
              驳回
            </el-button>
          </el-form-item>
        </el-form>
      </el-card>

      <el-card v-if="canToggleStatus" shadow="never">
        <template #header>
          <span class="card-title">商家状态与抽佣</span>
        </template>
        <div class="toolbar">
          <el-button
            :type="nextStatus === 4 ? 'danger' : 'primary'"
            :loading="submitting"
            @click="toggleStatus"
          >
            {{ nextStatus === 4 ? '停用商家' : '启用商家' }}
          </el-button>
          <el-divider direction="vertical" />
          <span class="muted">抽佣比例</span>
          <el-input-number
            v-model="commissionForm.value"
            :min="0"
            :max="0.5"
            :step="0.01"
            :precision="4"
          />
          <el-button :loading="submitting" @click="saveCommission">保存抽佣</el-button>
        </div>
        <p class="card-note">
          状态只允许在 2（已通过）与 4（已停用）之间切换；抽佣调整只影响之后生成的结算账单。
        </p>
      </el-card>

      <el-card shadow="never">
        <template #header>
          <span class="card-title">审核日志（{{ detail.auditLogs.length }}）</span>
        </template>
        <el-table :data="detail.auditLogs" border size="small">
          <el-table-column prop="action" label="动作" width="120" />
          <el-table-column label="状态流转" width="130">
            <template #default="{ row }">
              <span class="mono">{{ row.fromStatus }} → {{ row.toStatus }}</span>
            </template>
          </el-table-column>
          <el-table-column label="操作人" width="140">
            <template #default="{ row }">{{ row.operatorName || row.operatorId || '—' }}</template>
          </el-table-column>
          <el-table-column prop="remark" label="备注" min-width="240">
            <template #default="{ row }">{{ row.remark || '—' }}</template>
          </el-table-column>
          <el-table-column label="时间" width="160">
            <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
          </el-table-column>
          <template #empty>
            <el-empty description="暂无审核日志。" :image-size="70" />
          </template>
        </el-table>
      </el-card>
    </template>

    <el-skeleton v-else-if="loading" :rows="8" animated />

    <el-dialog
      v-model="rejectVisible"
      title="驳回入驻申请"
      width="460px"
      :close-on-click-modal="false"
    >
      <p class="dialog-hint">驳回原因必须是 5~512 字，会写入商家审核日志。</p>
      <el-input
        v-model="rejectRemark"
        type="textarea"
        :rows="4"
        maxlength="512"
        show-word-limit
        placeholder="例如：营业执照与法人信息不一致，请重新上传"
      />
      <template #footer>
        <el-button @click="rejectVisible = false">取消</el-button>
        <el-button type="danger" :loading="submitting" @click="submitReject">
          确认驳回
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.lead-title {
  display: flex;
  align-items: flex-start;
  gap: 8px;
}

.card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.card-head .card-title {
  margin-bottom: 0;
}

.audit-form {
  max-width: 720px;
}

.field-hint {
  margin-left: 10px;
  color: var(--app-muted);
  font-size: 12px;
}

.card-note {
  margin-top: 12px;
  color: var(--app-muted);
  font-size: 12px;
  line-height: 1.6;
}

.dialog-hint {
  color: var(--app-muted);
  font-size: 13px;
  line-height: 1.7;
  margin-bottom: 12px;
}
</style>
