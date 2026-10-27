<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import {
  getTicketDetail,
  judgeComplaint,
  listTickets,
  type ComplaintView,
  type TicketDetailView,
  type TicketView,
} from '@/api/service'
import {
  COMPLAINT_TARGET,
  LIABILITY,
  LIABILITY_OPTIONS,
  PUNISH_ACTION,
  PUNISH_ACTION_OPTIONS,
  TICKET_PRIORITY,
  TICKET_SOURCE,
  TICKET_STATUS,
  TICKET_STATUS_OPTIONS,
  labelOf,
  tagOf,
} from '@/constants/serviceStatus'
import { defaultDateRange, errorMessage } from '@/views/report/helpers'
import { formatDateTime } from '@/utils/datetime'

const DEFAULT_DAYS = 30
const PAGE_SIZE_OPTIONS = [10, 20, 50]
const COMPLAINT_TICKET_TYPE = 4

const loading = ref(false)
const loadError = ref('')
const tickets = ref<TicketView[]>([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(20)

const filters = reactive<{
  status: number | undefined
  dateRange: string[] | null
}>({
  status: undefined,
  dateRange: defaultDateRange(DEFAULT_DAYS),
})

const drawerVisible = ref(false)
const detailLoading = ref(false)
const detailError = ref('')
const detail = ref<TicketDetailView | null>(null)
const activeDetailId = ref<number | null>(null)
const judging = ref(false)

const judgeForm = reactive<{
  liability: number | undefined
  result: string
  punishAction: number | undefined
}>({
  liability: undefined,
  result: '',
  punishAction: 0,
})

const detailTicket = computed(() => detail.value?.ticket ?? null)
const complaint = computed(() => detail.value?.complaint ?? null)
const canJudge = computed(
  () => complaint.value !== null && complaint.value.liability === null,
)

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
      ticketType: COMPLAINT_TICKET_TYPE,
      status: typeof filters.status === 'number' ? filters.status : undefined,
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
    loadError.value = errorMessage(error, '投诉工单加载失败')
  } finally {
    loading.value = false
  }
}

function search(): void {
  pageNum.value = 1
  void load()
}

function reset(): void {
  filters.status = undefined
  filters.dateRange = defaultDateRange(DEFAULT_DAYS)
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

function submitterText(row: TicketView): string {
  const source = labelOf(TICKET_SOURCE, row.submitterType)
  return `${source} #${row.submitterId}`
}

function orderText(row: TicketView): string {
  return row.orderId === null ? '—' : `#${row.orderId}`
}

function targetText(item: ComplaintView): string {
  return `${labelOf(COMPLAINT_TARGET, item.targetType)} #${item.targetId}`
}

function resetJudgeForm(): void {
  judgeForm.liability = undefined
  judgeForm.result = ''
  judgeForm.punishAction = 0
}

async function loadDetail(id: number): Promise<void> {
  const requestId = id
  detailLoading.value = true
  detailError.value = ''
  try {
    const result = await getTicketDetail(id)
    if (activeDetailId.value === requestId) {
      detail.value = result
      if (result.complaint !== null && result.complaint.liability === null) {
        resetJudgeForm()
        judgeForm.punishAction = result.complaint.punishAction
      }
    }
  } catch (error) {
    if (activeDetailId.value === requestId) {
      detail.value = null
      detailError.value = errorMessage(error, '投诉详情加载失败')
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
  resetJudgeForm()
  void loadDetail(row.id)
}

function handleDrawerClosed(): void {
  activeDetailId.value = null
  detail.value = null
  detailError.value = ''
  detailLoading.value = false
}

async function submitJudge(): Promise<void> {
  const item = complaint.value
  const ticket = detailTicket.value
  const liability = judgeForm.liability
  const result = judgeForm.result.trim()
  const punishAction = judgeForm.punishAction

  if (!item || !ticket) {
    return
  }
  if (typeof liability !== 'number') {
    ElMessage.warning('请选择责任方')
    return
  }
  if (!result) {
    ElMessage.warning('处理结果不能为空')
    return
  }
  if (typeof punishAction !== 'number') {
    ElMessage.warning('请选择处罚动作')
    return
  }

  judging.value = true
  try {
    await judgeComplaint(item.id, { liability, result, punishAction })
    ElMessage.success('责任判定已提交')
    await Promise.all([load(), loadDetail(ticket.id)])
  } catch {
    // 错误提示由 http 响应拦截器统一输出，此处不重复弹
  } finally {
    judging.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="page">
    <div class="page-lead">
      <div>
        <h2>投诉处理</h2>
        <p>
          列表来自 <span class="mono">GET /api/v1/admin/tickets?ticketType=4</span>；
          投诉单字段只在打开详情后通过
          <span class="mono">GET /api/v1/admin/tickets/{id}</span> 的
          <span class="mono">complaint</span> 获取，避免列表 N+1 查询。
        </p>
      </div>
      <div class="toolbar">
        <el-button :icon="Refresh" :loading="loading" @click="load">刷新</el-button>
      </div>
    </div>

    <el-alert v-if="loadError" type="error" :closable="false" show-icon :title="loadError" />

    <el-card shadow="never">
      <div class="toolbar filter-bar">
        <el-select v-model="filters.status" placeholder="全部状态" clearable class="status-select">
          <el-option
            v-for="option in TICKET_STATUS_OPTIONS"
            :key="option.value"
            :label="option.label"
            :value="option.value"
          />
        </el-select>
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

      <el-table v-loading="loading" :data="tickets" border stripe row-key="id">
        <el-table-column label="工单号" min-width="180">
          <template #default="{ row }">
            <span class="mono">{{ row.ticketNo }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="subject" label="投诉原因" min-width="260" show-overflow-tooltip />
        <el-table-column label="投诉人" min-width="130">
          <template #default="{ row }">{{ submitterText(row) }}</template>
        </el-table-column>
        <el-table-column label="提交时间" width="155">
          <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="tagOf(TICKET_STATUS, row.status)" size="small">
              {{ labelOf(TICKET_STATUS, row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="110" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)">查看 / 判定</el-button>
          </template>
        </el-table-column>

        <template #empty>
          <el-empty description="当前筛选条件下没有投诉工单。" :image-size="80" />
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
      title="投诉详情"
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
          <div class="section-title">工单基本信息</div>
          <el-descriptions :column="2" border>
            <el-descriptions-item label="工单号" :span="2">
              <span class="mono">{{ detailTicket.ticketNo }}</span>
            </el-descriptions-item>
            <el-descriptions-item label="标题" :span="2">
              {{ detailTicket.subject }}
            </el-descriptions-item>
            <el-descriptions-item label="内容" :span="2">
              <div class="pre-wrap">{{ detailTicket.content }}</div>
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
            <el-descriptions-item label="工单状态">
              <el-tag :type="tagOf(TICKET_STATUS, detailTicket.status)" size="small">
                {{ labelOf(TICKET_STATUS, detailTicket.status) }}
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="提交时间" :span="2">
              {{ formatDateTime(detailTicket.createdAt) }}
            </el-descriptions-item>
          </el-descriptions>

          <div class="section-title">投诉单信息</div>
          <el-alert
            v-if="!complaint"
            type="info"
            :closable="false"
            show-icon
            title="该投诉尚未生成投诉单。"
          />
          <template v-else>
            <el-descriptions :column="2" border>
              <el-descriptions-item label="投诉人">
                {{ labelOf(TICKET_SOURCE, complaint.complainantType) }}
                #{{ complaint.complainantId }}
              </el-descriptions-item>
              <el-descriptions-item label="投诉目标">
                {{ targetText(complaint) }}
              </el-descriptions-item>
              <el-descriptions-item label="关联订单">
                <span class="mono">#{{ complaint.orderId }}</span>
              </el-descriptions-item>
              <el-descriptions-item label="提交时间">
                {{ formatDateTime(complaint.createdAt) }}
              </el-descriptions-item>
              <el-descriptions-item label="投诉原因" :span="2">
                <div class="pre-wrap">{{ complaint.reason }}</div>
              </el-descriptions-item>
              <el-descriptions-item label="责任判定">
                {{ labelOf(LIABILITY, complaint.liability) }}
              </el-descriptions-item>
              <el-descriptions-item label="处罚动作">
                {{ labelOf(PUNISH_ACTION, complaint.punishAction) }}
              </el-descriptions-item>
              <el-descriptions-item label="处理结果" :span="2">
                <div class="pre-wrap">{{ complaint.result || '—' }}</div>
              </el-descriptions-item>
            </el-descriptions>

            <template v-if="canJudge">
              <div class="section-title">责任判定</div>
              <el-form label-width="96px">
                <el-form-item label="责任方" required>
                  <el-radio-group v-model="judgeForm.liability">
                    <el-radio
                      v-for="option in LIABILITY_OPTIONS"
                      :key="option.value"
                      :value="option.value"
                    >
                      {{ option.label }}
                    </el-radio>
                  </el-radio-group>
                </el-form-item>
                <el-form-item label="处理结果" required>
                  <el-input
                    v-model="judgeForm.result"
                    type="textarea"
                    :rows="4"
                    maxlength="500"
                    show-word-limit
                    placeholder="填写责任认定与处理结论"
                  />
                </el-form-item>
                <el-form-item label="处罚动作" required>
                  <el-select v-model="judgeForm.punishAction" class="punish-select">
                    <el-option
                      v-for="option in PUNISH_ACTION_OPTIONS"
                      :key="option.value"
                      :label="option.label"
                      :value="option.value"
                    />
                  </el-select>
                </el-form-item>
                <el-alert
                  v-if="judgeForm.punishAction === 3"
                  type="warning"
                  :closable="false"
                  show-icon
                  title="封禁仅登记，实际封禁由商家/骑手模块执行。"
                />
                <div class="judge-actions">
                  <el-button type="primary" :loading="judging" @click="submitJudge">
                    提交判定
                  </el-button>
                </div>
              </el-form>
            </template>
            <el-alert
              v-else
              class="judged-alert"
              type="success"
              :closable="false"
              show-icon
              title="该投诉已完成责任判定，以下结果为只读。"
            />
          </template>
        </template>
      </div>
    </el-drawer>
  </div>
</template>

<style scoped>
.filter-bar {
  margin-bottom: 14px;
}

.status-select {
  width: 150px;
}

.date-range {
  width: 280px;
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

.punish-select {
  width: 180px;
}

.judge-actions {
  margin-top: 16px;
  text-align: right;
}

.judged-alert {
  margin-top: 14px;
}

@media (max-width: 900px) {
  .date-range {
    width: 100%;
  }
}
</style>
