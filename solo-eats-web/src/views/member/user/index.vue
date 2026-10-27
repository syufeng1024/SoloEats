<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import {
  assignMemberTags,
  getMemberDetail,
  listMemberAddresses,
  listMemberTags,
  listMemberWalletLogs,
  listMembers,
  updateMemberStatus,
  type AdminAddressView,
  type MemberDetailView,
  type MemberView,
  type TagView,
  type WalletLogView,
} from '@/api/member-admin'
import {
  MEMBER_GENDER,
  MEMBER_STATUS,
  MEMBER_STATUS_OPTIONS,
  WALLET_DIRECTION,
  labelOf,
  labelOfBizType,
  tagOf,
} from '@/constants/memberStatus'
import { errorMessage } from '@/views/report/helpers'
import { formatDateTime } from '@/utils/datetime'
import { formatYuan } from '@/utils/money'
import WalletAdjustDialog from '../components/WalletAdjustDialog.vue'

const PAGE_SIZE_OPTIONS = [10, 20, 50]
const LOG_PAGE_SIZE_OPTIONS = [5, 10, 20]

const loading = ref(false)
const loadError = ref('')
const members = ref<MemberView[]>([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(20)

const filters = reactive<{
  keyword: string
  status: number | undefined
  levelId: number | undefined
}>({
  keyword: '',
  status: undefined,
  levelId: undefined,
})

const drawerVisible = ref(false)
const activeDetailId = ref<number | null>(null)
const detail = ref<MemberDetailView | null>(null)
const detailLoading = ref(false)
const detailError = ref('')
const activeTab = ref<'basic' | 'addresses' | 'wallet' | 'tags'>('basic')

const addresses = ref<AdminAddressView[]>([])
const addressLoading = ref(false)
const addressError = ref('')
const addressLoaded = ref(false)

const logs = ref<WalletLogView[]>([])
const logTotal = ref(0)
const logPageNum = ref(1)
const logPageSize = ref(10)
const logLoading = ref(false)
const logError = ref('')

const tagDialogVisible = ref(false)
const tagOptions = ref<TagView[]>([])
const tagOptionsLoading = ref(false)
const tagOptionsError = ref('')
const selectedTagIds = ref<number[]>([])
const tagSubmitting = ref(false)

const adjustVisible = ref(false)
const adjustTarget = ref<MemberView | MemberDetailView | null>(null)

function memberLabel(member: MemberView | MemberDetailView | null): string {
  if (!member) {
    return '—'
  }
  const nickname = member.nickname?.trim()
  return nickname ? `#${member.userId} ${nickname}` : `#${member.userId}`
}

async function load(): Promise<void> {
  loading.value = true
  loadError.value = ''
  try {
    const result = await listMembers({
      keyword: filters.keyword.trim() || undefined,
      status: typeof filters.status === 'number' ? filters.status : undefined,
      levelId: typeof filters.levelId === 'number' ? filters.levelId : undefined,
      pageNum: pageNum.value,
      pageSize: pageSize.value,
    })
    members.value = result.list
    total.value = result.total
  } catch (error) {
    members.value = []
    total.value = 0
    loadError.value = errorMessage(error, '会员列表加载失败')
  } finally {
    loading.value = false
  }
}

function search(): void {
  pageNum.value = 1
  void load()
}

function reset(): void {
  filters.keyword = ''
  filters.status = undefined
  filters.levelId = undefined
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

async function loadDetail(id: number): Promise<void> {
  const requestId = id
  detailLoading.value = true
  detailError.value = ''
  try {
    const result = await getMemberDetail(id)
    if (activeDetailId.value === requestId) {
      detail.value = result
    }
  } catch (error) {
    if (activeDetailId.value === requestId) {
      detail.value = null
      detailError.value = errorMessage(error, '会员详情加载失败')
    }
  } finally {
    if (activeDetailId.value === requestId) {
      detailLoading.value = false
    }
  }
}

function openDetail(row: MemberView): void {
  activeDetailId.value = row.userId
  drawerVisible.value = true
  activeTab.value = 'basic'
  detail.value = null
  detailError.value = ''
  resetAddressState()
  resetLogState()
  void loadDetail(row.userId)
}

function handleDrawerClosed(): void {
  activeDetailId.value = null
  detail.value = null
  detailError.value = ''
  resetAddressState()
  resetLogState()
}

function resetAddressState(): void {
  addresses.value = []
  addressError.value = ''
  addressLoaded.value = false
  addressLoading.value = false
}

function resetLogState(): void {
  logs.value = []
  logTotal.value = 0
  logPageNum.value = 1
  logError.value = ''
  logLoading.value = false
}

async function loadAddresses(id: number): Promise<void> {
  addressLoading.value = true
  addressError.value = ''
  try {
    const result = await listMemberAddresses(id)
    if (activeDetailId.value === id) {
      addresses.value = result
      addressLoaded.value = true
    }
  } catch (error) {
    if (activeDetailId.value === id) {
      addresses.value = []
      addressError.value = errorMessage(error, '收货地址加载失败')
    }
  } finally {
    if (activeDetailId.value === id) {
      addressLoading.value = false
    }
  }
}

async function loadLogs(id: number): Promise<void> {
  logLoading.value = true
  logError.value = ''
  try {
    const result = await listMemberWalletLogs(id, {
      pageNum: logPageNum.value,
      pageSize: logPageSize.value,
    })
    if (activeDetailId.value === id) {
      logs.value = result.list
      logTotal.value = result.total
    }
  } catch (error) {
    if (activeDetailId.value === id) {
      logs.value = []
      logTotal.value = 0
      logError.value = errorMessage(error, '钱包流水加载失败')
    }
  } finally {
    if (activeDetailId.value === id) {
      logLoading.value = false
    }
  }
}

function handleTabChange(name: string | number): void {
  const id = activeDetailId.value
  if (id === null) {
    return
  }
  if (name === 'addresses' && !addressLoaded.value && !addressLoading.value) {
    void loadAddresses(id)
  }
  if (name === 'wallet' && logs.value.length === 0 && !logLoading.value) {
    void loadLogs(id)
  }
}

function handleLogPageChange(page: number): void {
  const id = activeDetailId.value
  if (id === null) {
    return
  }
  logPageNum.value = page
  void loadLogs(id)
}

function handleLogSizeChange(size: number): void {
  logPageSize.value = size
  logPageNum.value = 1
  const id = activeDetailId.value
  if (id !== null) {
    void loadLogs(id)
  }
}

function openAdjust(row: MemberView | MemberDetailView): void {
  adjustTarget.value = row
  adjustVisible.value = true
}

async function handleAdjustSuccess(): Promise<void> {
  const id = activeDetailId.value
  if (id !== null) {
    await loadDetail(id)
    await loadLogs(id)
  }
  await load()
}

function handleStatusCommand(
  row: MemberView,
  command: string | number | object,
): void {
  void confirmStatus(row, Number(command))
}

async function confirmStatus(
  row: MemberView,
  status: number,
): Promise<void> {
  if (row.status === status) {
    ElMessage.info(`会员当前已是「${labelOf(MEMBER_STATUS, status)}」状态`)
    return
  }
  try {
    await ElMessageBox.confirm(
      `确认将会员 #${row.userId} 的状态由「${labelOf(MEMBER_STATUS, row.status)}」改为「${labelOf(MEMBER_STATUS, status)}」？`,
      '变更会员状态',
      {
        confirmButtonText: '确认变更',
        cancelButtonText: '取消',
        type: 'warning',
      },
    )
  } catch {
    return
  }

  try {
    await updateMemberStatus(row.userId, status)
    ElMessage.success('会员状态已更新')
    await load()
    if (activeDetailId.value === row.userId) {
      await loadDetail(row.userId)
    }
  } catch {
    // 错误提示由 http 响应拦截器统一输出，此处不重复弹
  }
}

async function loadTagOptions(): Promise<void> {
  tagOptionsLoading.value = true
  tagOptionsError.value = ''
  try {
    tagOptions.value = await listMemberTags()
  } catch (error) {
    tagOptions.value = []
    tagOptionsError.value = errorMessage(error, '标签列表加载失败')
  } finally {
    tagOptionsLoading.value = false
  }
}

async function openTagDialog(): Promise<void> {
  const current = detail.value
  if (!current) {
    return
  }
  tagDialogVisible.value = true
  selectedTagIds.value = []
  if (tagOptions.value.length === 0) {
    await loadTagOptions()
  }
  // 详情只返回标签名，没有标签 ID；按名称匹配回填，标签名有 uk 唯一约束。
  const names = new Set(current.tagNames)
  selectedTagIds.value = tagOptions.value
    .filter((tag) => names.has(tag.name))
    .map((tag) => tag.id)
}

async function submitTags(): Promise<void> {
  const id = activeDetailId.value
  if (id === null) {
    return
  }
  if (tagOptionsLoading.value || tagOptionsError.value) {
    ElMessage.warning('标签列表未加载完成，无法保存；全量覆盖语义下提交空集合会清空标签')
    return
  }
  tagSubmitting.value = true
  try {
    await assignMemberTags(id, selectedTagIds.value)
    ElMessage.success('会员标签已保存')
    tagDialogVisible.value = false
    await loadDetail(id)
  } catch {
    // 错误提示由 http 响应拦截器统一输出，此处不重复弹
  } finally {
    tagSubmitting.value = false
  }
}

function signedAmount(log: WalletLogView): string {
  const prefix = log.direction === 1 ? '+' : '-'
  return `${prefix}${formatYuan(log.amount)}`
}

function amountClass(log: WalletLogView): string {
  return log.direction === 1 ? 'amount-in' : 'amount-out'
}

onMounted(load)
</script>

<template>
  <div class="page">
    <div class="page-lead">
      <div>
        <h2>会员管理</h2>
        <p>
          数据源：<span class="mono">GET /api/v1/admin/members</span>。手机号由后端脱敏后返回，
          列表接口不返回明文；<span class="mono">keyword</span> 只按手机号后四位匹配。
          当前 <span class="mono">MemberView</span> 未包含等级、成长值与最近登录字段，故列表不展示。
        </p>
      </div>
      <div class="toolbar">
        <el-button :icon="Refresh" :loading="loading" @click="load">刷新</el-button>
      </div>
    </div>

    <el-alert v-if="loadError" type="error" :closable="false" show-icon :title="loadError" />

    <el-card shadow="never">
      <div class="toolbar filter-bar">
        <el-input
          v-model="filters.keyword"
          placeholder="手机号后四位"
          clearable
          maxlength="11"
          class="keyword-input"
          @keyup.enter="search"
        />
        <el-select v-model="filters.status" placeholder="全部状态" clearable class="status-select">
          <el-option
            v-for="option in MEMBER_STATUS_OPTIONS"
            :key="option.value"
            :label="option.label"
            :value="option.value"
          />
        </el-select>
        <el-input-number
          v-model="filters.levelId"
          placeholder="等级 ID"
          :min="1"
          :precision="0"
          :controls="false"
          class="id-input"
        />
        <el-button type="primary" @click="search">查询</el-button>
        <el-button @click="reset">重置</el-button>
      </div>

      <el-table v-loading="loading" :data="members" border stripe row-key="userId">
        <el-table-column label="用户 ID" width="90">
          <template #default="{ row }">
            <span class="mono">{{ row.userId }}</span>
          </template>
        </el-table-column>
        <el-table-column label="昵称" min-width="150">
          <template #default="{ row }">{{ row.nickname || '—' }}</template>
        </el-table-column>
        <el-table-column label="手机号" width="140">
          <template #default="{ row }">
            <span class="mono">{{ row.phone || '—' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="tagOf(MEMBER_STATUS, row.status)" size="small">
              {{ labelOf(MEMBER_STATUS, row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="订单数" width="90" align="right">
          <template #default="{ row }">{{ row.orderCount ?? 0 }}</template>
        </el-table-column>
        <el-table-column label="累计消费" width="120" align="right">
          <template #default="{ row }">
            <span class="amount">{{ formatYuan(row.totalAmount) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="最近下单" width="150">
          <template #default="{ row }">{{ formatDateTime(row.lastOrderAt) }}</template>
        </el-table-column>
        <el-table-column label="注册时间" width="150">
          <template #default="{ row }">{{ formatDateTime(row.registeredAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="230" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)">详情</el-button>
            <el-button link type="primary" @click="openAdjust(row)">调账</el-button>
            <el-dropdown
              trigger="click"
              @command="(command: string | number | object) => handleStatusCommand(row, command)"
            >
              <el-button link type="primary">状态</el-button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item
                    v-for="option in MEMBER_STATUS_OPTIONS"
                    :key="option.value"
                    :command="option.value"
                    :disabled="row.status === option.value"
                  >
                    设为{{ option.label }}
                  </el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </template>
        </el-table-column>

        <template #empty>
          <el-empty description="当前筛选条件下没有会员。" :image-size="80" />
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

    <el-drawer
      v-model="drawerVisible"
      title="会员详情"
      size="820px"
      :close-on-click-modal="false"
      @closed="handleDrawerClosed"
    >
      <div v-loading="detailLoading" class="drawer-body">
        <el-alert
          v-if="detailError"
          type="error"
          :closable="false"
          show-icon
          :title="detailError"
        />

        <template v-if="detail">
          <el-tabs v-model="activeTab" @tab-change="handleTabChange">
            <el-tab-pane label="基本信息" name="basic">
              <el-descriptions :column="2" border>
                <el-descriptions-item label="用户 ID">
                  <span class="mono">{{ detail.userId }}</span>
                </el-descriptions-item>
                <el-descriptions-item label="昵称">{{ detail.nickname || '—' }}</el-descriptions-item>
                <el-descriptions-item label="手机号">
                  <span class="mono">{{ detail.phone || '—' }}</span>
                </el-descriptions-item>
                <el-descriptions-item label="性别">
                  {{ labelOf(MEMBER_GENDER, detail.gender) }}
                </el-descriptions-item>
                <el-descriptions-item label="状态">
                  <el-tag :type="tagOf(MEMBER_STATUS, detail.status)" size="small">
                    {{ labelOf(MEMBER_STATUS, detail.status) }}
                  </el-tag>
                </el-descriptions-item>
                <el-descriptions-item label="生日">{{ detail.birthday || '—' }}</el-descriptions-item>
                <el-descriptions-item label="订单数">{{ detail.orderCount ?? 0 }}</el-descriptions-item>
                <el-descriptions-item label="累计消费">
                  <span class="amount">{{ formatYuan(detail.totalAmount) }}</span>
                </el-descriptions-item>
                <el-descriptions-item label="钱包余额">
                  <span class="amount">{{ formatYuan(detail.walletBalance) }}</span>
                </el-descriptions-item>
                <el-descriptions-item label="收货地址数">{{ detail.addressCount }}</el-descriptions-item>
                <el-descriptions-item label="最近下单">
                  {{ formatDateTime(detail.lastOrderAt) }}
                </el-descriptions-item>
                <el-descriptions-item label="注册时间">
                  {{ formatDateTime(detail.registeredAt) }}
                </el-descriptions-item>
                <el-descriptions-item label="注销申请时间">
                  {{ formatDateTime(detail.cancelApplyAt) }}
                </el-descriptions-item>
                <el-descriptions-item label="标签">
                  <template v-if="detail.tagNames.length > 0">
                    <el-tag
                      v-for="name in detail.tagNames"
                      :key="name"
                      size="small"
                      class="tag-item"
                    >
                      {{ name }}
                    </el-tag>
                  </template>
                  <span v-else class="muted">—</span>
                </el-descriptions-item>
              </el-descriptions>

              <div class="drawer-actions">
                <el-button type="primary" @click="openAdjust(detail)">钱包调账</el-button>
                <el-button @click="openTagDialog">打标签</el-button>
              </div>
            </el-tab-pane>

            <el-tab-pane label="收货地址" name="addresses">
              <el-alert
                v-if="addressError"
                type="error"
                :closable="false"
                show-icon
                :title="addressError"
              />
              <el-table v-loading="addressLoading" :data="addresses" border stripe row-key="id">
                <el-table-column prop="receiverName" label="收货人" width="110" />
                <el-table-column label="手机号" width="130">
                  <template #default="{ row }">
                    <span class="mono">{{ row.receiverPhone || '—' }}</span>
                  </template>
                </el-table-column>
                <el-table-column label="地区" min-width="170">
                  <template #default="{ row }">
                    {{ [row.province, row.city, row.district].filter(Boolean).join(' / ') || '—' }}
                  </template>
                </el-table-column>
                <el-table-column label="详细地址" min-width="200" show-overflow-tooltip>
                  <template #default="{ row }">
                    {{ row.address }}{{ row.houseNumber ? ` ${row.houseNumber}` : '' }}
                  </template>
                </el-table-column>
                <el-table-column label="标签" width="90">
                  <template #default="{ row }">{{ row.tag || '—' }}</template>
                </el-table-column>
                <el-table-column label="默认" width="70" align="center">
                  <template #default="{ row }">
                    <el-tag v-if="row.isDefault" size="small" type="success">默认</el-tag>
                    <span v-else class="muted">—</span>
                  </template>
                </el-table-column>

                <template #empty>
                  <el-empty description="该会员暂无收货地址。" :image-size="60" />
                </template>
              </el-table>
            </el-tab-pane>

            <el-tab-pane label="钱包流水" name="wallet">
              <el-alert
                v-if="logError"
                type="error"
                :closable="false"
                show-icon
                :title="logError"
              />
              <div class="tab-toolbar">
                <el-button type="primary" size="small" @click="openAdjust(detail)">
                  调账
                </el-button>
              </div>
              <el-table v-loading="logLoading" :data="logs" border stripe row-key="id">
                <el-table-column label="时间" width="150">
                  <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
                </el-table-column>
                <el-table-column label="类型" width="100">
                  <template #default="{ row }">{{ labelOfBizType(row.bizType) }}</template>
                </el-table-column>
                <el-table-column label="方向" width="80">
                  <template #default="{ row }">
                    <el-tag :type="tagOf(WALLET_DIRECTION, row.direction)" size="small">
                      {{ labelOf(WALLET_DIRECTION, row.direction) }}
                    </el-tag>
                  </template>
                </el-table-column>
                <el-table-column label="金额" width="110" align="right">
                  <template #default="{ row }">
                    <span :class="['amount', amountClass(row)]">{{ signedAmount(row) }}</span>
                  </template>
                </el-table-column>
                <el-table-column label="变动后余额" width="120" align="right">
                  <template #default="{ row }">
                    <span class="amount">{{ formatYuan(row.afterBalance) }}</span>
                  </template>
                </el-table-column>
                <el-table-column label="关联单号" width="100">
                  <template #default="{ row }">
                    <span class="mono">{{ row.bizId === null ? '—' : `#${row.bizId}` }}</span>
                  </template>
                </el-table-column>
                <el-table-column label="备注" min-width="180" show-overflow-tooltip>
                  <template #default="{ row }">{{ row.remark || '—' }}</template>
                </el-table-column>

                <template #empty>
                  <el-empty description="该会员暂无钱包流水。" :image-size="60" />
                </template>
              </el-table>
              <el-pagination
                class="pagination"
                layout="total, sizes, prev, pager, next"
                :total="logTotal"
                :page-size="logPageSize"
                :page-sizes="LOG_PAGE_SIZE_OPTIONS"
                :current-page="logPageNum"
                @current-change="handleLogPageChange"
                @size-change="handleLogSizeChange"
              />
            </el-tab-pane>

            <el-tab-pane label="标签" name="tags">
              <div class="tab-toolbar">
                <el-button type="primary" size="small" @click="openTagDialog">打标签</el-button>
              </div>
              <div v-if="detail.tagNames.length > 0" class="tag-list">
                <el-tag
                  v-for="name in detail.tagNames"
                  :key="name"
                  class="tag-item"
                >
                  {{ name }}
                </el-tag>
              </div>
              <el-empty v-else description="该会员暂无标签。" :image-size="60" />
            </el-tab-pane>
          </el-tabs>
        </template>
      </div>

      <template #footer>
        <el-button @click="drawerVisible = false">关闭抽屉</el-button>
      </template>
    </el-drawer>

    <el-dialog
      v-model="tagDialogVisible"
      title="会员打标签"
      width="480px"
      :close-on-click-modal="false"
    >
      <el-alert
        v-if="tagOptionsError"
        type="error"
        :closable="false"
        show-icon
        :title="tagOptionsError"
        class="dialog-alert"
      />
      <el-form label-width="90px" v-loading="tagOptionsLoading">
        <el-form-item label="会员">
          <span>{{ memberLabel(detail) }}</span>
        </el-form-item>
        <el-form-item label="标签">
          <el-select
            v-model="selectedTagIds"
            multiple
            filterable
            clearable
            placeholder="选择标签，留空提交将清空该会员全部标签"
            class="full-width"
          >
            <el-option
              v-for="tag in tagOptions"
              :key="tag.id"
              :value="tag.id"
              :label="`${tag.name}（${tag.memberCount} 人）`"
            />
          </el-select>
          <div class="form-hint">
            后端为全量覆盖语义：提交的集合即该会员最终标签集合；
            <span class="mono">tag_ids</span> 上限 50 个。
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="tagDialogVisible = false">取消</el-button>
        <el-button
          type="primary"
          :loading="tagSubmitting"
          :disabled="tagOptionsLoading || !!tagOptionsError"
          @click="submitTags"
        >
          保存标签
        </el-button>
      </template>
    </el-dialog>

    <WalletAdjustDialog
      v-model:visible="adjustVisible"
      :user-id="adjustTarget?.userId ?? activeDetailId"
      :member-label="memberLabel(adjustTarget)"
      @success="handleAdjustSuccess"
    />
  </div>
</template>

<style scoped>
.filter-bar {
  margin-bottom: 14px;
}

.keyword-input {
  width: 180px;
}

.status-select {
  width: 140px;
}

.id-input {
  width: 120px;
}

.amount {
  font-variant-numeric: tabular-nums;
  font-weight: 600;
}

.amount-in {
  color: var(--el-color-success);
}

.amount-out {
  color: var(--el-color-danger);
}

.pagination {
  margin-top: 16px;
  justify-content: flex-end;
}

.drawer-body {
  min-height: 200px;
}

.drawer-actions {
  margin-top: 18px;
}

.tab-toolbar {
  display: flex;
  justify-content: flex-end;
  margin-bottom: 12px;
}

.tag-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.tag-item {
  margin-right: 6px;
}

.full-width {
  width: 100%;
}

.form-hint {
  color: var(--el-text-color-secondary);
  font-size: 12px;
  line-height: 1.6;
  margin-top: 4px;
}

.dialog-alert {
  margin-bottom: 12px;
}
</style>
