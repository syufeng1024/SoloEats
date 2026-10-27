<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import {
  acceptTicket,
  assignTicket,
  callbackTicket,
  closeTicket,
  finishTicket,
  getTicketDetail,
  getTicketStats,
  handleTicket,
  listTickets,
  type ComplaintView,
  type TicketDetailView,
  type TicketStatsView,
  type TicketView,
} from '@/api/service'
import {
  TICKET_PRIORITY,
  TICKET_PRIORITY_OPTIONS,
  TICKET_SOURCE,
  TICKET_STATUS,
  TICKET_STATUS_OPTIONS,
  TICKET_TYPE,
  TICKET_TYPE_OPTIONS,
  labelOf,
  tagOf,
} from '@/constants/serviceStatus'
import {
  defaultDateRange,
  errorMessage,
  formatCount,
  formatRatioPercent,
} from '@/views/report/helpers'
import { formatDateTime } from '@/utils/datetime'

const DEFAULT_DAYS = 30
const PAGE_SIZE_OPTIONS = [10, 20, 50]

const EMPTY_STATS: TicketStatsView = {
  ticketCount: 0,
  solvedRate: 0,
  avgResponseMinutes: 0,
  satisfactionAvg: 0,
}

const TICKET_ACTION_LABELS: Record<string, string> = {
  CREATED: '创建工单',
  ACCEPT: '受理',
  ASSIGN: '指派',
  HANDLE: '处理',
  CALLBACK: '回访',
  FINISH: '办结',
  CLOSE: '关闭',
  USER_CLOSE: '用户撤单',
  SATISFACTION: '满意度评价',
}

const COMPLAINT_TARGET: Record<number, string> = {
  2: '商家',
  3: '骑手',
  4: '平台',
}

const COMPLAINT_LIABILITY: Record<number, string> = {
  1: '商家责任',
  2: '骑手责任',
  3: '平台责任',
  4: '用户责任',
}

const COMPLAINT_PUNISH: Record<number, string> = {
  0: '无',
  1: '警告',
  2: '扣款',
  3: '封禁',
}

const loading = ref(false)
const loadError = ref('')
const tickets = ref<TicketView[]>([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(20)

const statsLoading = ref(false)
const statsError = ref('')
const stats = ref<TicketStatsView>({ ...EMPTY_STATS })

const filters = reactive<{
  status: number | undefined
  ticketType: number | undefined
  priority: number | undefined
  assigneeId: number | undefined
  dateRange: string[] | null
}>({
  status: undefined,
  ticketType: undefined,
  priority: undefined,
  assigneeId: undefined,
  dateRange: defaultDateRange(DEFAULT_DAYS),
})

const drawerVisible = ref(false)
const detailLoading = ref(false)
const detailError = ref('')
const detail = ref<TicketDetailView | null>(null)
const activeDetailId = ref<number | null>(null)
const acting = ref(false)

const assignVisible = ref(false)
const assigneeId = ref<number | undefined>(undefined)
const handleVisible = ref(false)
const handleContent = ref('')
const callbackVisible = ref(false)
const callbackContent = ref('')
const callbackResult = ref('')

const statsCards = computed(() => [
  {
    key: 'count',
    label: '工单总量',
    value: formatCount(stats.value.ticketCount),
    unit: '单',
  },
  {
    key: 'solved',
    label: '办结率',
    value: formatRatioPercent(stats.value.solvedRate),
    unit: '',
  },
  {
    key: 'response',
    label: '平均响应时长',
    value: formatDecimal(stats.value.avgResponseMinutes),
    unit: '分钟',
  },
  {
    key: 'satisfaction',
    label: '满意度均分',
    value: formatDecimal(stats.value.satisfactionAvg),
    unit: '分',
  },
])

const detailTicket = computed(() => detail.value?.ticket ?? null)
const detailRecords = computed(() => detail.value?.records ?? [])
const complaint = computed(() => detail.value?.complaint ?? null)

function formatDecimal(value: number | null | undefined): string {
  if (value === null || value === undefined) {
    return '—'
  }
  const numeric = Number(value)
  return Number.isFinite(numeric) ? numeric.toFixed(2) : '—'
}

function appliedRange(): [string, string] {
  const range =
    Array.isArray(filters.dateRange) && filters.dateRange.length === 2
      ? filters.dateRange
      : defaultDateRange(DEFAULT_DAYS)
  filters.dateRange = range
  return [range[0], range[1]]
}

async function load(): Promise<void> {
  loading.value = true
  loadError.value = ''
  const [dateFrom, dateTo] = appliedRange()
  try {
    const result = await listTickets({
      status: typeof filters.status === 'number' ? filters.status : undefined,
      ticketType:
        typeof filters.ticketType === 'number' ? filters.ticketType : undefined,
      priority:
        typeof filters.priority === 'number' ? filters.priority : undefined,
      assigneeId:
        typeof filters.assigneeId === 'number' ? filters.assigneeId : undefined,
      dateFrom,
      dateTo,
      pageNum: pageNum.value,
      pageSize: pageSize.value,
    })
    tickets.value = result.list
    total.value = result.total
  } catch (error) {
    tickets.value = []
    total.value = 0
    loadError.value = errorMessage(error, '工单列表加载失败')
  } finally {
    loading.value = false
  }
}

async function loadStats(): Promise<void> {
  statsLoading.value = true
  statsError.value = ''
  const [dateFrom, dateTo] = appliedRange()
  try {
    stats.value = await getTicketStats({ dateFrom, dateTo })
  } catch (error) {
    stats.value = { ...EMPTY_STATS }
    statsError.value = errorMessage(error, '工单统计加载失败')
  } finally {
    statsLoading.value = false
  }
}

async function refreshAll(): Promise<void> {
  await Promise.all([load(), loadStats()])
}

function search(): void {
  pageNum.value = 1
  void refreshAll()
}

function reset(): void {
  filters.status = undefined
  filters.ticketType = undefined
  filters.priority = undefined
  filters.assigneeId = undefined
  filters.dateRange = defaultDateRange(DEFAULT_DAYS)
  pageNum.value = 1
  pageSize.value = 20
  void refreshAll()
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

function submitterText(row: TicketView): string {
  const source = labelOf(TICKET_SOURCE, row.submitterType)
  return row.submitterId === null ? source : `${source} #${row.submitterId}`
}

function orderText(row: TicketView): string {
  return row.orderId === null ? '—' : `#${row.orderId}`
}

function satisfactionText(row: TicketView): string {
  return row.satisfaction === null ? '—' : `${row.satisfaction} 星`
}

function isOverdue(row: TicketView): boolean {
  if (row.deadlineAt === null || row.status >= 3) {
    return false
  }
  const deadline = Date.parse(row.deadlineAt)
  return Number.isFinite(deadline) && deadline < Date.now()
}

function rowClassName({ row }: { row: TicketView }): string {
  return isOverdue(row) ? 'overdue-row' : ''
}

function actionLabel(action: string | null | undefined): string {
  if (!action) {
    return '—'
  }
  return TICKET_ACTION_LABELS[action] ?? action
}

function operatorText(
  operatorName: string | null | undefined,
  operatorId: number | null | undefined,
): string {
  const name = operatorName || '—'
  return operatorId === null || operatorId === undefined
    ? name
    : `${name} #${operatorId}`
}

function mappedLabel(
  map: Record<number, string>,
  value: number | null | undefined,
): string {
  if (value === null || value === undefined) {
    return '—'
  }
  return map[value] ?? '—'
}

function complaintTargetText(item: ComplaintView): string {
  return `${mappedLabel(COMPLAINT_TARGET, item.targetType)} #${item.targetId}`
}

async function loadDetail(id: number): Promise<void> {
  const requestId = id
  detailLoading.value = true
  detailError.value = ''
  try {
    const result = await getTicketDetail(id)
    if (activeDetailId.value === requestId) {
      detail.value = result
    }
  } catch (error) {
    if (activeDetailId.value === requestId) {
      detail.value = null
      detailError.value = errorMessage(error, '工单详情加载失败')
    }
  } finally {
    if (activeDetailId.value === requestId) {
      detailLoading.value = false
    }
  }
}

function openDetail(row: TicketView): void {
  activeDetailId.value = row.id
  drawerVisible.value = true
  detail.value = null
  detailError.value = ''
  void loadDetail(row.id)
}

function handleDrawerClosed(): void {
  activeDetailId.value = null
  detail.value = null
  detailError.value = ''
}

async function afterAction(message: string, ticketId: number): Promise<void> {
  ElMessage.success(message)
  await refreshAll()
  if (activeDetailId.value === ticketId) {
    await loadDetail(ticketId)
  }
}

async function acceptCurrent(): Promise<void> {
  const ticket = detailTicket.value
  if (!ticket) {
    return
  }
  acting.value = true
  try {
    await acceptTicket(ticket.id)
    await afterAction('工单已受理', ticket.id)
  } catch {
    // 错误提示由 http 响应拦截器统一输出，此处不重复弹
  } finally {
    acting.value = false
  }
}

function openAssign(): void {
  assigneeId.value = detailTicket.value?.assigneeId ?? undefined
  assignVisible.value = true
}

async function submitAssign(): Promise<void> {
  const ticket = detailTicket.value
  const target = assigneeId.value
  if (!ticket) {
    return
  }
  if (typeof target !== 'number' || target < 1) {
    ElMessage.warning('请输入有效的受理人 ID')
    return
  }
  acting.value = true
  try {
    await assignTicket(ticket.id, { assigneeId: target })
    assignVisible.value = false
    await afterAction('工单已指派', ticket.id)
  } catch {
    // 错误提示由 http 响应拦截器统一输出，此处不重复弹
  } finally {
    acting.value = false
  }
}

function openHandle(): void {
  handleContent.value = ''
  handleVisible.value = true
}

async function submitHandle(): Promise<void> {
  const ticket = detailTicket.value
  const content = handleContent.value.trim()
  if (!ticket) {
    return
  }
  if (!content) {
    ElMessage.warning('处理内容不能为空')
    return
  }
  acting.value = true
  try {
    await handleTicket(ticket.id, { content })
    handleVisible.value = false
    await afterAction('处理记录已提交', ticket.id)
  } catch {
    // 错误提示由 http 响应拦截器统一输出，此处不重复弹
  } finally {
    acting.value = false
  }
}

function openCallback(): void {
  callbackContent.value = ''
  callbackResult.value = ''
  callbackVisible.value = true
}

async function submitCallback(): Promise<void> {
  const ticket = detailTicket.value
  const content = callbackContent.value.trim()
  if (!ticket) {
    return
  }
  if (!content) {
    ElMessage.warning('回访内容不能为空')
    return
  }
  acting.value = true
  try {
    await callbackTicket(ticket.id, {
      content,
      result: callbackResult.value.trim() || null,
    })
    callbackVisible.value = false
    await afterAction('回访已提交，工单进入待办结', ticket.id)
  } catch {
    // 错误提示由 http 响应拦截器统一输出，此处不重复弹
  } finally {
    acting.value = false
  }
}

async function finishCurrent(): Promise<void> {
  const ticket = detailTicket.value
  if (!ticket) {
    return
  }
  acting.value = true
  try {
    await finishTicket(ticket.id)
    await afterAction('工单已办结', ticket.id)
  } catch {
    // 错误提示由 http 响应拦截器统一输出，此处不重复弹
  } finally {
    acting.value = false
  }
}

async function confirmClose(): Promise<void> {
  const ticket = detailTicket.value
  if (!ticket) {
    return
  }
  try {
    await ElMessageBox.confirm(
      `确认关闭工单 ${ticket.ticketNo}？关闭后不可再处理。`,
      '关闭工单',
      {
        confirmButtonText: '确认关闭',
        cancelButtonText: '取消',
        type: 'warning',
      },
    )
  } catch {
    return
  }

  acting.value = true
  try {
    await closeTicket(ticket.id)
    await afterAction('工单已关闭', ticket.id)
  } catch {
    // 错误提示由 http 响应拦截器统一输出，此处不重复弹
  } finally {
    acting.value = false
  }
}

onMounted(refreshAll)
</script>

<template>
  <div class="page">
    <div class="page-lead">
      <div>
        <h2>工单管理</h2>
        <p>
          数据源：<span class="mono">GET /api/v1/admin/tickets</span> 与
          <span class="mono">/tickets/stats</span>。办结率按后端 0~1 比例换算为百分比；
          超时判定在页面按 <span class="mono">deadlineAt &lt; now &amp;&amp; status &lt; 3</span>
          二次兜底。
        </p>
      </div>
      <div class="toolbar">
        <el-button :icon="Refresh" :loading="loading || statsLoading" @click="refreshAll">
          刷新
        </el-button>
      </div>
    </div>

    <el-alert v-if="statsError" type="warning" :closable="false" show-icon :title="statsError" />
    <el-alert v-if="loadError" type="error" :closable="false" show-icon :title="loadError" />

    <el-row :gutter="12" v-loading="statsLoading">
      <el-col v-for="card in statsCards" :key="card.key" :xs="12" :sm="12" :md="6">
        <el-card shadow="never" class="metric-card">
          <div class="summary-label">{{ card.label }}</div>
          <div class="summary-value">
            {{ card.value }}<span v-if="card.unit" class="summary-unit">{{ card.unit }}</span>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-card shadow="never">
      <div class="toolbar filter-bar">
        <el-select v-model="filters.status" placeholder="全部状态" clearable class="select-sm">
          <el-option
            v-for="option in TICKET_STATUS_OPTIONS"
            :key="option.value"
            :label="option.label"
            :value="option.value"
          />
        </el-select>
        <el-select
          v-model="filters.ticketType"
          placeholder="全部类型"
          clearable
          class="select-sm"
        >
          <el-option
            v-for="option in TICKET_TYPE_OPTIONS"
            :key="option.value"
            :label="option.label"
            :value="option.value"
          />
        </el-select>
        <el-select
          v-model="filters.priority"
          placeholder="全部优先级"
          clearable
          class="select-sm"
        >
          <el-option
            v-for="option in TICKET_PRIORITY_OPTIONS"
            :key="option.value"
            :label="option.label"
            :value="option.value"
          />
        </el-select>
        <el-input-number
          v-model="filters.assigneeId"
          placeholder="受理人 ID"
          :min="1"
          :precision="0"
          :controls="false"
          class="id-input"
        />
        <el-date-picker
          v-model="filters.dateRange"
          type="daterange"
          value-format="YYYY-MM-DD"
          start-placeholder="提交开始日期"
          end-placeholder="提交结束日期"
          unlink-panels
          class="date-range"
        />
        <el-button type="primary" @click="search">查询</el-button>
        <el-button @click="reset">重置</el-button>
      </div>

      <el-table
        v-loading="loading"
        :data="tickets"
        :row-class-name="rowClassName"
        border
        stripe
        row-key="id"
      >
        <el-table-column label="工单号" min-width="170">
          <template #default="{ row }">
            <span class="mono">{{ row.ticketNo }}</span>
          </template>
        </el-table-column>
        <el-table-column label="类型" width="105">
          <template #default="{ row }">
            <el-tag :type="tagOf(TICKET_TYPE, row.ticketType)" size="small">
              {{ labelOf(TICKET_TYPE, row.ticketType) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="subject" label="标题" min-width="190" show-overflow-tooltip />
        <el-table-column label="提交人" min-width="130">
          <template #default="{ row }">{{ submitterText(row) }}</template>
        </el-table-column>
        <el-table-column label="关联订单" width="110">
          <template #default="{ row }">
            <span class="mono">{{ orderText(row) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="优先级" width="90">
          <template #default="{ row }">
            <el-tag :type="tagOf(TICKET_PRIORITY, row.priority)" size="small" effect="plain">
              {{ labelOf(TICKET_PRIORITY, row.priority) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="tagOf(TICKET_STATUS, row.status)" size="small">
              {{ labelOf(TICKET_STATUS, row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="受理人" width="95" align="right">
          <template #default="{ row }">
            {{ row.assigneeId === null ? '—' : `#${row.assigneeId}` }}
          </template>
        </el-table-column>
        <el-table-column label="受理时间" width="150">
          <template #default="{ row }">{{ formatDateTime(row.acceptAt) }}</template>
        </el-table-column>
        <el-table-column label="时限" width="190">
          <template #default="{ row }">
            <span :class="{ 'text-danger': isOverdue(row) }">
              {{ formatDateTime(row.deadlineAt) }}
            </span>
            <el-tag v-if="isOverdue(row)" type="danger" size="small" class="overdue-tag">
              已超时
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="满意度" width="90" align="right">
          <template #default="{ row }">{{ satisfactionText(row) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="86" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)">详情</el-button>
          </template>
        </el-table-column>

        <template #empty>
          <el-empty description="当前筛选条件下没有工单。" :image-size="80" />
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
      title="工单详情"
      size="720px"
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

        <template v-if="detailTicket">
          <div class="section-title">基本信息</div>
          <el-descriptions :column="2" border>
            <el-descriptions-item label="标题" :span="2">
              {{ detailTicket.subject }}
            </el-descriptions-item>
            <el-descriptions-item label="内容" :span="2">
              <div class="pre-wrap">{{ detailTicket.content }}</div>
            </el-descriptions-item>
            <el-descriptions-item label="类型">
              <el-tag :type="tagOf(TICKET_TYPE, detailTicket.ticketType)" size="small">
                {{ labelOf(TICKET_TYPE, detailTicket.ticketType) }}
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="状态">
              <el-tag :type="tagOf(TICKET_STATUS, detailTicket.status)" size="small">
                {{ labelOf(TICKET_STATUS, detailTicket.status) }}
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="提交人">
              {{ submitterText(detailTicket) }}
            </el-descriptions-item>
            <el-descriptions-item label="关联订单">
              <span class="mono">{{ orderText(detailTicket) }}</span>
            </el-descriptions-item>
            <el-descriptions-item label="优先级">
              <el-tag
                :type="tagOf(TICKET_PRIORITY, detailTicket.priority)"
                size="small"
                effect="plain"
              >
                {{ labelOf(TICKET_PRIORITY, detailTicket.priority) }}
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="受理人">
              {{ detailTicket.assigneeId === null ? '—' : `#${detailTicket.assigneeId}` }}
            </el-descriptions-item>
            <el-descriptions-item label="受理时间">
              {{ formatDateTime(detailTicket.acceptAt) }}
            </el-descriptions-item>
            <el-descriptions-item label="时限">
              <span :class="{ 'text-danger': isOverdue(detailTicket) }">
                {{ formatDateTime(detailTicket.deadlineAt) }}
              </span>
              <el-tag
                v-if="isOverdue(detailTicket)"
                type="danger"
                size="small"
                class="overdue-tag"
              >
                已超时
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="满意度">
              {{ satisfactionText(detailTicket) }}
            </el-descriptions-item>
            <el-descriptions-item label="提交时间">
              {{ formatDateTime(detailTicket.createdAt) }}
            </el-descriptions-item>
            <el-descriptions-item label="图片" :span="2">
              <div v-if="detailTicket.images.length > 0" class="image-list">
                <el-image
                  v-for="(url, index) in detailTicket.images"
                  :key="`${url}-${index}`"
                  :src="url"
                  :preview-src-list="detailTicket.images"
                  :initial-index="index"
                  fit="cover"
                  class="ticket-image"
                />
              </div>
              <span v-else class="muted">—</span>
            </el-descriptions-item>
          </el-descriptions>

          <div class="section-title">流转记录</div>
          <el-timeline v-if="detailRecords.length > 0">
            <el-timeline-item
              v-for="record in detailRecords"
              :key="record.id"
              :timestamp="formatDateTime(record.createdAt)"
              placement="top"
            >
              <div class="timeline-title">
                <strong>{{ actionLabel(record.action) }}</strong>
                <el-tag size="small" type="info" effect="plain">
                  {{ labelOf(TICKET_STATUS, record.fromStatus) }}
                  →
                  {{ labelOf(TICKET_STATUS, record.toStatus) }}
                </el-tag>
              </div>
              <div class="muted timeline-meta">
                操作人：{{ operatorText(record.operatorName, record.operatorId) }}
              </div>
              <div v-if="record.content" class="pre-wrap timeline-content">
                {{ record.content }}
              </div>
            </el-timeline-item>
          </el-timeline>
          <el-empty v-else description="暂无流转记录。" :image-size="60" />

          <template v-if="complaint">
            <div class="section-title">关联投诉</div>
            <el-descriptions :column="2" border>
              <el-descriptions-item label="投诉原因" :span="2">
                {{ complaint.reason }}
              </el-descriptions-item>
              <el-descriptions-item label="投诉目标">
                {{ complaintTargetText(complaint) }}
              </el-descriptions-item>
              <el-descriptions-item label="责任判定">
                {{ mappedLabel(COMPLAINT_LIABILITY, complaint.liability) }}
              </el-descriptions-item>
              <el-descriptions-item label="处理结果" :span="2">
                {{ complaint.result || '—' }}
              </el-descriptions-item>
              <el-descriptions-item label="处罚动作">
                {{ mappedLabel(COMPLAINT_PUNISH, complaint.punishAction) }}
              </el-descriptions-item>
              <el-descriptions-item label="回访结果">
                {{ complaint.callbackResult || '—' }}
              </el-descriptions-item>
            </el-descriptions>
          </template>
        </template>
      </div>

      <template #footer>
        <div class="drawer-footer">
          <el-button @click="drawerVisible = false">关闭抽屉</el-button>
          <div class="toolbar-spacer" />
          <template v-if="detailTicket">
            <el-button
              v-if="detailTicket.status === 0"
              type="primary"
              :loading="acting"
              @click="acceptCurrent"
            >
              受理
            </el-button>
            <template v-if="detailTicket.status === 1">
              <el-button :loading="acting" @click="openAssign">指派</el-button>
              <el-button type="primary" :loading="acting" @click="openHandle">
                处理
              </el-button>
              <el-button :loading="acting" @click="openCallback">回访</el-button>
            </template>
            <el-button
              v-if="detailTicket.status === 2"
              type="success"
              :loading="acting"
              @click="finishCurrent"
            >
              办结
            </el-button>
            <el-tooltip
              v-if="detailTicket.status < 4"
              :disabled="detailTicket.status !== 3"
              content="已办结工单为终态，后端不允许关闭"
              placement="top"
            >
              <span class="close-button-wrap">
                <el-button
                  :disabled="detailTicket.status === 3"
                  :loading="acting"
                  @click="confirmClose"
                >
                  关闭
                </el-button>
              </span>
            </el-tooltip>
          </template>
        </div>
      </template>
    </el-drawer>

    <el-dialog
      v-model="assignVisible"
      title="指派工单"
      width="440px"
      :close-on-click-modal="false"
    >
      <el-form label-width="90px">
        <el-form-item label="受理人 ID" required>
          <el-input-number
            v-model="assigneeId"
            :min="1"
            :precision="0"
            :controls="false"
            class="full-width"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="assignVisible = false">取消</el-button>
        <el-button type="primary" :loading="acting" @click="submitAssign">确认指派</el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="handleVisible"
      title="处理工单"
      width="520px"
      :close-on-click-modal="false"
    >
      <el-form label-width="90px">
        <el-form-item label="处理内容" required>
          <el-input
            v-model="handleContent"
            type="textarea"
            :rows="5"
            maxlength="1000"
            show-word-limit
            placeholder="填写本次处理记录，提交后工单保持处理中"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="handleVisible = false">取消</el-button>
        <el-button type="primary" :loading="acting" @click="submitHandle">提交处理</el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="callbackVisible"
      title="回访工单"
      width="560px"
      :close-on-click-modal="false"
    >
      <el-form label-width="90px">
        <el-form-item label="回访内容" required>
          <el-input
            v-model="callbackContent"
            type="textarea"
            :rows="4"
            maxlength="1000"
            show-word-limit
            placeholder="填写联系用户的过程与反馈"
          />
        </el-form-item>
        <el-form-item label="回访结果">
          <el-input
            v-model="callbackResult"
            type="textarea"
            :rows="3"
            maxlength="500"
            show-word-limit
            placeholder="选填，例如：用户接受处理方案"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="callbackVisible = false">取消</el-button>
        <el-button type="primary" :loading="acting" @click="submitCallback">
          提交回访
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.filter-bar {
  margin-bottom: 14px;
}

.select-sm {
  width: 135px;
}

.id-input {
  width: 125px;
}

.date-range {
  width: 280px;
}

.metric-card {
  margin-bottom: 12px;
}

.summary-label {
  color: var(--app-muted);
  font-size: 12px;
}

.summary-value {
  margin-top: 8px;
  font-size: 28px;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
  line-height: 1.2;
}

.summary-unit {
  margin-left: 4px;
  color: var(--app-muted);
  font-size: 13px;
  font-weight: 400;
}

.text-danger {
  color: var(--el-color-danger);
  font-weight: 600;
}

.overdue-tag {
  margin-left: 6px;
}

:deep(.overdue-row) td {
  background: var(--el-color-danger-light-9);
}

.pagination {
  margin-top: 16px;
  justify-content: flex-end;
}

.drawer-body {
  min-height: 200px;
}

.section-title {
  margin: 20px 0 12px;
  font-size: 15px;
  font-weight: 600;
}

.section-title:first-of-type {
  margin-top: 0;
}

.pre-wrap {
  white-space: pre-wrap;
  word-break: break-word;
  line-height: 1.6;
}

.image-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.ticket-image {
  width: 64px;
  height: 64px;
  border-radius: 4px;
}

.timeline-title {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.timeline-meta {
  margin-top: 4px;
  font-size: 12px;
}

.timeline-content {
  margin-top: 6px;
  font-size: 13px;
}

.drawer-footer {
  display: flex;
  align-items: center;
  gap: 8px;
}

.close-button-wrap {
  display: inline-flex;
}

.full-width {
  width: 100%;
}

@media (max-width: 900px) {
  .date-range {
    width: 100%;
  }
}
</style>
