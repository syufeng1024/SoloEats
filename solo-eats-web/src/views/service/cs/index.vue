<script setup lang="ts">
import { computed, nextTick, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Refresh } from '@element-plus/icons-vue'
import {
  closeCsSession,
  createQuickReply,
  deleteQuickReply,
  getCsStats,
  listCsMessages,
  listCsSessions,
  listQuickReplies,
  sendCsMessage,
  takeCsSession,
  transferCsSession,
  updateQuickReply,
  type AgentSessionView,
  type CsStatsView,
  type ImMessageView,
  type QuickReplyCommandPayload,
  type QuickReplyView,
} from '@/api/service'
import {
  CS_SESSION_STATUS,
  CS_SESSION_STATUS_OPTIONS,
  IM_MSG_TYPE,
  SENDER_TYPE,
  labelOf,
  tagOf,
} from '@/constants/serviceStatus'
import { useAuthStore } from '@/stores/auth'
import { defaultDateRange, errorMessage } from '@/views/report/helpers'
import { formatDateTime } from '@/utils/datetime'

const auth = useAuthStore()
const currentUserId = computed(() => auth.session?.userId ?? null)

const EMPTY_STATS: CsStatsView = {
  servedSessions: 0,
  activeSessions: 0,
  waitingSessions: 0,
}

const SESSION_PAGE_SIZE = 20
const MESSAGE_PAGE_SIZE = 100
const STATS_DAYS = 30

const sessionsLoading = ref(false)
const sessionsError = ref('')
const sessions = ref<AgentSessionView[]>([])
const sessionTotal = ref(0)
const pageNum = ref(1)

const statsLoading = ref(false)
const stats = ref<CsStatsView>({ ...EMPTY_STATS })

const filters = reactive<{
  status: number | undefined
  onlyMine: boolean
}>({
  status: undefined,
  onlyMine: false,
})

const selectedSession = ref<AgentSessionView | null>(null)
const activeSessionId = ref<number | null>(null)
const messages = ref<ImMessageView[]>([])
const messagesLoading = ref(false)
const messagesError = ref('')
const messageListRef = ref<HTMLElement | null>(null)
const draft = ref('')
const sending = ref(false)
const sessionActing = ref(false)

const transferVisible = ref(false)
const transferTargetId = ref<number | undefined>(undefined)

const quickReplies = ref<QuickReplyView[]>([])
const quickLoading = ref(false)
const quickError = ref('')
const quickPickerVisible = ref(false)

const quickDialogVisible = ref(false)
const quickEditingId = ref<number | null>(null)
const quickSubmitting = ref(false)
const quickForm = reactive({
  category: '',
  content: '',
  sort: 0,
  status: 0,
})

const statsCards = computed(() => [
  { key: 'served', label: '我的接待', value: stats.value.servedSessions },
  { key: 'active', label: '进行中', value: stats.value.activeSessions },
  { key: 'waiting', label: '待接待', value: stats.value.waitingSessions },
])

const enabledQuickReplies = computed(() =>
  quickReplies.value.filter((item) => item.status === 0),
)

const canTake = computed(() => {
  const session = selectedSession.value
  return (
    session !== null &&
    session.status === 0 &&
    session.agentId === null &&
    currentUserId.value !== null
  )
})

const canOperate = computed(() => {
  const session = selectedSession.value
  return (
    session !== null &&
    session.status === 0 &&
    currentUserId.value !== null &&
    session.agentId === currentUserId.value
  )
})

const canChat = computed(() => canOperate.value)

const canSend = computed(
  () => canChat.value && draft.value.trim().length > 0 && !sending.value,
)

function agentText(agentId: number | null): string {
  if (agentId === null) {
    return '待接待'
  }
  return currentUserId.value !== null && agentId === currentUserId.value
    ? '我'
    : `坐席 #${agentId}`
}

function canReadSession(session: AgentSessionView): boolean {
  if (session.agentId === null) {
    return true
  }
  return currentUserId.value !== null && session.agentId === currentUserId.value
}

function isMine(message: ImMessageView): boolean {
  return (
    message.senderType === 4 &&
    currentUserId.value !== null &&
    message.senderId === currentUserId.value
  )
}

function senderLabel(message: ImMessageView): string {
  const role = message.senderType === 4 ? '客服' : labelOf(SENDER_TYPE, message.senderType)
  return `${role} #${message.senderId}`
}

function messageText(message: ImMessageView): string {
  return message.content.trim() || `[${labelOf(IM_MSG_TYPE, message.msgType)}]`
}

function quickStatusLabel(status: number): string {
  return status === 0 ? '启用' : '停用'
}

function quickStatusType(status: number): 'success' | 'info' {
  return status === 0 ? 'success' : 'info'
}

async function scrollMessagesToBottom(): Promise<void> {
  await nextTick()
  const element = messageListRef.value
  if (element) {
    element.scrollTop = element.scrollHeight
  }
}

async function loadSessions(): Promise<void> {
  sessionsLoading.value = true
  sessionsError.value = ''
  const selectedId = selectedSession.value?.id ?? null
  try {
    const result = await listCsSessions({
      status: typeof filters.status === 'number' ? filters.status : undefined,
      agentId: filters.onlyMine ? currentUserId.value ?? undefined : undefined,
      pageNum: pageNum.value,
      pageSize: SESSION_PAGE_SIZE,
    })
    sessions.value = result.list
    sessionTotal.value = result.total

    if (selectedId !== null) {
      const updated = result.list.find((item) => item.id === selectedId)
      if (updated) {
        selectedSession.value = updated
      } else {
        selectedSession.value = null
        activeSessionId.value = null
        messages.value = []
        messagesError.value = ''
        messagesLoading.value = false
      }
    }
  } catch (error) {
    sessions.value = []
    sessionTotal.value = 0
    sessionsError.value = errorMessage(error, '会话列表加载失败')
  } finally {
    sessionsLoading.value = false
  }
}

async function loadStats(): Promise<void> {
  statsLoading.value = true
  try {
    const [dateFrom, dateTo] = defaultDateRange(STATS_DAYS)
    stats.value = await getCsStats({
      dateFrom,
      dateTo,
      agentId: currentUserId.value ?? undefined,
    })
  } catch {
    stats.value = { ...EMPTY_STATS }
  } finally {
    statsLoading.value = false
  }
}

async function loadMessages(sessionId?: number): Promise<void> {
  const targetId = sessionId ?? selectedSession.value?.id
  const session = selectedSession.value
  if (!targetId || !session) {
    messagesLoading.value = false
    return
  }
  if (!canReadSession(session)) {
    messagesLoading.value = false
    messages.value = []
    messagesError.value = `该会话由坐席 #${session.agentId} 接待，仅接待坐席可查看消息。`
    return
  }

  messagesLoading.value = true
  messagesError.value = ''
  try {
    const result = await listCsMessages(targetId, { pageSize: MESSAGE_PAGE_SIZE })
    if (activeSessionId.value === targetId) {
      messages.value = result
      await scrollMessagesToBottom()
    }
  } catch (error) {
    if (activeSessionId.value === targetId) {
      messagesError.value = errorMessage(error, '会话消息加载失败')
    }
  } finally {
    if (activeSessionId.value === targetId) {
      messagesLoading.value = false
    }
  }
}

async function refreshMessages(): Promise<void> {
  await loadMessages()
}

function searchSessions(): void {
  pageNum.value = 1
  void loadSessions()
}

function handlePageChange(page: number): void {
  pageNum.value = page
  void loadSessions()
}

function selectSession(session: AgentSessionView): void {
  if (selectedSession.value?.id === session.id) {
    return
  }
  selectedSession.value = session
  activeSessionId.value = session.id
  messages.value = []
  messagesError.value = ''
  draft.value = ''
  void loadMessages(session.id)
}

async function refreshAll(): Promise<void> {
  await Promise.all([loadSessions(), loadStats(), refreshMessages()])
}

async function takeCurrent(): Promise<void> {
  const session = selectedSession.value
  if (!session) {
    return
  }
  sessionActing.value = true
  try {
    await takeCsSession(session.id)
    ElMessage.success('已接待该会话')
    await Promise.all([loadSessions(), loadStats()])
    await loadMessages()
  } catch {
    // 错误提示由 http 响应拦截器统一输出，此处不重复弹
  } finally {
    sessionActing.value = false
  }
}

function openTransfer(): void {
  transferTargetId.value = undefined
  transferVisible.value = true
}

async function submitTransfer(): Promise<void> {
  const session = selectedSession.value
  const targetId = transferTargetId.value
  if (!session) {
    return
  }
  if (typeof targetId !== 'number' || targetId < 1) {
    ElMessage.warning('请输入有效的转接坐席 ID')
    return
  }

  sessionActing.value = true
  try {
    await transferCsSession(session.id, { toAgentId: targetId })
    ElMessage.success(`会话已转接给坐席 #${targetId}`)
    transferVisible.value = false
    await Promise.all([loadSessions(), loadStats()])
  } catch {
    // 错误提示由 http 响应拦截器统一输出，此处不重复弹
  } finally {
    sessionActing.value = false
  }
}

async function closeCurrent(): Promise<void> {
  const session = selectedSession.value
  if (!session) {
    return
  }
  try {
    await ElMessageBox.confirm(
      `确认结束会话 ${session.sessionNo}？结束后用户侧将只读。`,
      '结束会话',
      {
        confirmButtonText: '确认结束',
        cancelButtonText: '取消',
        type: 'warning',
      },
    )
  } catch {
    return
  }

  sessionActing.value = true
  try {
    await closeCsSession(session.id)
    ElMessage.success('会话已结束')
    await Promise.all([loadSessions(), loadStats()])
  } catch {
    // 错误提示由 http 响应拦截器统一输出，此处不重复弹
  } finally {
    sessionActing.value = false
  }
}

async function sendCurrent(): Promise<void> {
  const session = selectedSession.value
  const content = draft.value.trim()
  if (!session || !content || !canChat.value) {
    return
  }

  sending.value = true
  try {
    const sent = await sendCsMessage(session.id, { msgType: 1, content })
    if (activeSessionId.value === session.id) {
      messages.value = [...messages.value, sent]
      if (selectedSession.value?.id === session.id) {
        selectedSession.value.lastMessage = content
        selectedSession.value.lastMessageAt = sent.createdAt
      }
      draft.value = ''
      await scrollMessagesToBottom()
    }
  } catch {
    // 错误提示由 http 响应拦截器统一输出，此处不重复弹
  } finally {
    sending.value = false
  }
}

async function loadQuickReplies(): Promise<void> {
  quickLoading.value = true
  quickError.value = ''
  try {
    quickReplies.value = await listQuickReplies()
  } catch (error) {
    quickReplies.value = []
    quickError.value = errorMessage(error, '快捷话术加载失败')
  } finally {
    quickLoading.value = false
  }
}

function useQuickReply(reply: QuickReplyView): void {
  if (!selectedSession.value) {
    ElMessage.warning('请先选择会话')
    return
  }
  if (!canChat.value) {
    ElMessage.warning('请先接待当前会话再发送话术')
    return
  }
  if (reply.status !== 0) {
    ElMessage.warning('该快捷话术已停用')
    return
  }
  draft.value = reply.content
  quickPickerVisible.value = false
}

function openQuickCreate(): void {
  quickEditingId.value = null
  Object.assign(quickForm, {
    category: '',
    content: '',
    sort: 0,
    status: 0,
  })
  quickDialogVisible.value = true
}

function openQuickEdit(reply: QuickReplyView): void {
  quickEditingId.value = reply.id
  Object.assign(quickForm, {
    category: reply.category,
    content: reply.content,
    sort: reply.sort,
    status: reply.status,
  })
  quickDialogVisible.value = true
}

async function submitQuickReply(): Promise<void> {
  const category = quickForm.category.trim()
  const content = quickForm.content.trim()
  if (!category) {
    ElMessage.warning('话术分类不能为空')
    return
  }
  if (!content) {
    ElMessage.warning('话术内容不能为空')
    return
  }

  const payload: QuickReplyCommandPayload = {
    category,
    content,
    sort: quickForm.sort,
    status: quickForm.status,
  }

  quickSubmitting.value = true
  try {
    if (quickEditingId.value === null) {
      await createQuickReply(payload)
      ElMessage.success('快捷话术已创建')
    } else {
      await updateQuickReply(quickEditingId.value, payload)
      ElMessage.success('快捷话术已更新')
    }
    quickDialogVisible.value = false
    await loadQuickReplies()
  } catch {
    // 错误提示由 http 响应拦截器统一输出，此处不重复弹
  } finally {
    quickSubmitting.value = false
  }
}

async function confirmDeleteQuick(reply: QuickReplyView): Promise<void> {
  try {
    await ElMessageBox.confirm(
      `确认删除快捷话术「${reply.content}」？`,
      '删除快捷话术',
      {
        confirmButtonText: '确认删除',
        cancelButtonText: '取消',
        type: 'warning',
      },
    )
  } catch {
    return
  }

  try {
    await deleteQuickReply(reply.id)
    ElMessage.success('快捷话术已删除')
    await loadQuickReplies()
  } catch {
    // 错误提示由 http 响应拦截器统一输出，此处不重复弹
  }
}

onMounted(() => {
  void loadSessions()
  void loadStats()
  void loadQuickReplies()
})
</script>

<template>
  <div class="cs-workspace">
    <div class="workspace-topbar">
      <div class="stats-strip" v-loading="statsLoading">
        <div v-for="card in statsCards" :key="card.key" class="stat-item">
          <span class="stat-label">{{ card.label }}</span>
          <span class="stat-number">{{ card.value }}</span>
        </div>
        <span class="muted stats-note">近 {{ STATS_DAYS }} 天 · 坐席 #{{ currentUserId ?? '—' }}</span>
      </div>
      <div class="toolbar-spacer" />
      <el-button
        :icon="Refresh"
        :loading="sessionsLoading || statsLoading || messagesLoading || quickLoading"
        @click="refreshAll"
      >
        刷新
      </el-button>
    </div>

    <el-row :gutter="12" class="cs-body">
      <el-col :span="6" class="pane-col">
        <el-card shadow="never" class="pane">
          <div class="pane-head">
            <span class="pane-title">会话列表</span>
            <span class="muted">{{ sessionTotal }} 条</span>
          </div>

          <div class="session-filters">
            <el-select
              v-model="filters.status"
              placeholder="全部状态"
              clearable
              size="small"
              class="status-select"
              @change="searchSessions"
            >
              <el-option
                v-for="option in CS_SESSION_STATUS_OPTIONS"
                :key="option.value"
                :label="option.label"
                :value="option.value"
              />
            </el-select>
            <el-switch
              v-model="filters.onlyMine"
              active-text="只看我的"
              @change="searchSessions"
            />
          </div>

          <el-alert
            v-if="sessionsError"
            type="error"
            :closable="false"
            show-icon
            :title="sessionsError"
            class="pane-alert"
          />

          <div v-loading="sessionsLoading" class="session-list">
            <div
              v-for="session in sessions"
              :key="session.id"
              class="session-item"
              :class="{ active: session.id === selectedSession?.id }"
              @click="selectSession(session)"
            >
              <div class="session-line">
                <span class="mono session-no">{{ session.sessionNo }}</span>
                <el-tag :type="tagOf(CS_SESSION_STATUS, session.status)" size="small">
                  {{ labelOf(CS_SESSION_STATUS, session.status) }}
                </el-tag>
              </div>
              <div class="session-meta">
                用户 #{{ session.userId }} · {{ agentText(session.agentId) }}
              </div>
              <div class="session-message">{{ session.lastMessage || '暂无消息' }}</div>
              <div class="session-line session-foot">
                <span class="muted">{{ formatDateTime(session.lastMessageAt) }}</span>
                <el-badge
                  v-if="session.userUnread > 0"
                  :value="session.userUnread"
                  :max="99"
                  type="danger"
                />
              </div>
            </div>

            <el-empty
              v-if="!sessionsLoading && sessions.length === 0"
              description="暂无会话"
              :image-size="60"
            />
          </div>

          <div class="pane-foot">
            <el-pagination
              small
              layout="prev, pager, next"
              :total="sessionTotal"
              :page-size="SESSION_PAGE_SIZE"
              :current-page="pageNum"
              @current-change="handlePageChange"
            />
          </div>
        </el-card>
      </el-col>

      <el-col :span="12" class="pane-col">
        <el-card shadow="never" class="pane chat-pane">
          <template v-if="selectedSession">
            <div class="chat-head">
              <div class="chat-title-wrap">
                <div class="pane-title mono">{{ selectedSession.sessionNo }}</div>
                <div class="chat-meta">
                  <span>用户 #{{ selectedSession.userId }}</span>
                  <span>·</span>
                  <span>{{ agentText(selectedSession.agentId) }}</span>
                  <span>·</span>
                  <el-tag
                    :type="tagOf(CS_SESSION_STATUS, selectedSession.status)"
                    size="small"
                  >
                    {{ labelOf(CS_SESSION_STATUS, selectedSession.status) }}
                  </el-tag>
                </div>
              </div>
              <div class="toolbar chat-actions">
                <el-button
                  :icon="Refresh"
                  size="small"
                  :loading="messagesLoading"
                  @click="refreshMessages"
                >
                  刷新
                </el-button>
                <el-button
                  v-if="canTake"
                  type="primary"
                  size="small"
                  :loading="sessionActing"
                  @click="takeCurrent"
                >
                  接待
                </el-button>
                <template v-if="canOperate">
                  <el-button size="small" :loading="sessionActing" @click="openTransfer">
                    转接
                  </el-button>
                  <el-button
                    size="small"
                    type="danger"
                    :loading="sessionActing"
                    @click="closeCurrent"
                  >
                    结束
                  </el-button>
                </template>
                <el-tag
                  v-else-if="
                    selectedSession.agentId !== null &&
                    selectedSession.agentId !== currentUserId
                  "
                  size="small"
                  type="info"
                >
                  坐席 #{{ selectedSession.agentId }} 接待中
                </el-tag>
              </div>
            </div>

            <el-alert
              v-if="messagesError"
              type="warning"
              :closable="false"
              show-icon
              :title="messagesError"
              class="pane-alert"
            />

            <div ref="messageListRef" v-loading="messagesLoading" class="message-list">
              <div
                v-for="message in messages"
                :key="message.id"
                class="message-row"
                :class="{ mine: isMine(message) }"
              >
                <div class="message-meta">
                  {{ isMine(message) ? '我' : senderLabel(message) }}
                  · {{ formatDateTime(message.createdAt) }}
                </div>
                <div class="message-bubble" :class="{ mine: isMine(message) }">
                  <el-tag
                    v-if="message.msgType !== 1"
                    size="small"
                    type="info"
                    effect="plain"
                    class="message-type"
                  >
                    {{ labelOf(IM_MSG_TYPE, message.msgType) }}
                  </el-tag>
                  <div class="pre-wrap">{{ messageText(message) }}</div>
                </div>
              </div>

              <el-empty
                v-if="!messagesLoading && messages.length === 0"
                description="暂无消息"
                :image-size="60"
              />
            </div>

            <div class="composer">
              <div class="composer-tools">
                <el-button size="small" @click="quickPickerVisible = true">快捷话术</el-button>
                <span class="muted">
                  {{ canChat ? 'Ctrl + Enter 发送' : '接待该会话后才能发送消息' }}
                </span>
              </div>
              <el-input
                v-model="draft"
                type="textarea"
                :rows="3"
                maxlength="2000"
                resize="none"
                :disabled="!canChat"
                placeholder="输入回复内容"
                @keydown.ctrl.enter.prevent="sendCurrent"
              />
              <div class="composer-actions">
                <span class="muted">{{ draft.length }}/2000</span>
                <el-button
                  type="primary"
                  :loading="sending"
                  :disabled="!canSend"
                  @click="sendCurrent"
                >
                  发送
                </el-button>
              </div>
            </div>
          </template>

          <el-empty v-else description="请选择左侧会话开始接待" class="chat-empty" />
        </el-card>
      </el-col>

      <el-col :span="6" class="pane-col">
        <el-card shadow="never" class="pane">
          <div class="pane-head">
            <span class="pane-title">快捷话术</span>
            <div class="toolbar">
              <el-button
                :icon="Refresh"
                size="small"
                :loading="quickLoading"
                @click="loadQuickReplies"
              />
              <el-button type="primary" size="small" :icon="Plus" @click="openQuickCreate">
                新建
              </el-button>
            </div>
          </div>

          <el-alert
            v-if="quickError"
            type="error"
            :closable="false"
            show-icon
            :title="quickError"
            class="pane-alert"
          />

          <div v-loading="quickLoading" class="quick-list">
            <div
              v-for="reply in quickReplies"
              :key="reply.id"
              class="quick-item"
              @click="useQuickReply(reply)"
            >
              <div class="quick-head">
                <el-tag size="small" effect="plain">{{ reply.category }}</el-tag>
                <el-tag :type="quickStatusType(reply.status)" size="small">
                  {{ quickStatusLabel(reply.status) }}
                </el-tag>
              </div>
              <div class="quick-content">{{ reply.content }}</div>
              <div class="quick-actions" @click.stop>
                <el-button link type="primary" @click="openQuickEdit(reply)">编辑</el-button>
                <el-button link type="danger" @click="confirmDeleteQuick(reply)">
                  删除
                </el-button>
              </div>
            </div>

            <el-empty
              v-if="!quickLoading && quickReplies.length === 0"
              description="暂无快捷话术"
              :image-size="60"
            />
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-dialog
      v-model="transferVisible"
      title="转接会话"
      width="420px"
      :close-on-click-modal="false"
    >
      <el-form label-width="110px">
        <el-form-item label="目标坐席 ID" required>
          <el-input-number
            v-model="transferTargetId"
            :min="1"
            :precision="0"
            :controls="false"
            class="full-width"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="transferVisible = false">取消</el-button>
        <el-button type="primary" :loading="sessionActing" @click="submitTransfer">
          确认转接
        </el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="quickPickerVisible"
      title="快捷话术"
      width="540px"
      :close-on-click-modal="false"
    >
      <div v-if="enabledQuickReplies.length > 0" class="quick-picker-list">
        <div
          v-for="reply in enabledQuickReplies"
          :key="reply.id"
          class="quick-pick-item"
          @click="useQuickReply(reply)"
        >
          <el-tag size="small" effect="plain">{{ reply.category }}</el-tag>
          <div class="quick-pick-content">{{ reply.content }}</div>
        </div>
      </div>
      <el-empty v-else description="暂无启用的快捷话术" :image-size="60" />
      <template #footer>
        <el-button @click="quickPickerVisible = false">关闭</el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="quickDialogVisible"
      :title="quickEditingId === null ? '新建快捷话术' : '编辑快捷话术'"
      width="560px"
      :close-on-click-modal="false"
    >
      <el-form label-width="72px">
        <el-form-item label="分类" required>
          <el-input
            v-model="quickForm.category"
            maxlength="64"
            show-word-limit
            placeholder="例如：通用问候"
          />
        </el-form-item>
        <el-form-item label="内容" required>
          <el-input
            v-model="quickForm.content"
            type="textarea"
            :rows="4"
            maxlength="500"
            show-word-limit
            placeholder="话术内容，点选后将填入发送框"
          />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number
            v-model="quickForm.sort"
            :min="0"
            :precision="0"
            :controls="false"
          />
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="quickForm.status">
            <el-radio :value="0">启用</el-radio>
            <el-radio :value="1">停用</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="quickDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="quickSubmitting" @click="submitQuickReply">
          {{ quickEditingId === null ? '创建' : '保存' }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.cs-workspace {
  height: calc(100vh - 160px);
  display: flex;
  flex-direction: column;
  gap: 12px;
  min-height: 520px;
}

.workspace-topbar {
  display: flex;
  align-items: center;
  gap: 16px;
  flex-wrap: wrap;
}

.stats-strip {
  display: flex;
  align-items: center;
  gap: 20px;
  padding: 8px 14px;
  background: #fff;
  border: 1px solid var(--app-border);
  border-radius: 8px;
}

.stat-item {
  display: flex;
  align-items: baseline;
  gap: 6px;
}

.stat-label {
  color: var(--app-muted);
  font-size: 12px;
}

.stat-number {
  font-size: 18px;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}

.stats-note {
  font-size: 12px;
}

.cs-body {
  flex: 1;
  min-height: 0;
}

.pane-col {
  height: 100%;
}

.pane {
  height: 100%;
}

.pane :deep(.el-card__body) {
  height: 100%;
  display: flex;
  flex-direction: column;
  padding: 0;
  overflow: hidden;
}

.pane-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 14px 14px 10px;
  border-bottom: 1px solid var(--app-border);
  flex: 0 0 auto;
}

.pane-title {
  font-size: 14px;
  font-weight: 600;
}

.pane-alert {
  margin: 8px 12px 0;
  flex: 0 0 auto;
}

.pane-foot {
  display: flex;
  justify-content: center;
  padding: 8px 10px;
  border-top: 1px solid var(--app-border);
  flex: 0 0 auto;
}

.session-filters {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 14px;
  flex-wrap: wrap;
  flex: 0 0 auto;
}

.status-select {
  width: 120px;
}

.session-list {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 0 10px 8px;
}

.session-item {
  padding: 10px;
  border: 1px solid transparent;
  border-radius: 8px;
  cursor: pointer;
  transition: background 0.15s ease;
}

.session-item:hover {
  background: var(--app-bg);
}

.session-item.active {
  background: var(--el-color-primary-light-9);
  border-color: var(--el-color-primary-light-7);
}

.session-line {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.session-no {
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.session-meta {
  margin-top: 5px;
  color: var(--app-muted);
  font-size: 12px;
}

.session-message {
  margin-top: 4px;
  color: var(--app-ink);
  font-size: 12px;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.session-foot {
  margin-top: 6px;
  font-size: 12px;
}

.chat-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  padding: 12px 14px;
  border-bottom: 1px solid var(--app-border);
  flex: 0 0 auto;
}

.chat-title-wrap {
  min-width: 0;
}

.chat-meta {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
  margin-top: 5px;
  color: var(--app-muted);
  font-size: 12px;
}

.chat-actions {
  flex: 0 0 auto;
}

.chat-pane :deep(.el-card__body) {
  background: #f8fafc;
}

.message-list {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 14px 16px;
}

.message-row {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  margin-bottom: 14px;
}

.message-row.mine {
  align-items: flex-end;
}

.message-meta {
  margin-bottom: 4px;
  color: var(--app-muted);
  font-size: 11px;
}

.message-bubble {
  max-width: 78%;
  padding: 8px 12px;
  color: var(--app-ink);
  background: #fff;
  border: 1px solid var(--app-border);
  border-radius: 10px;
  font-size: 13px;
  line-height: 1.55;
  word-break: break-word;
}

.message-bubble.mine {
  background: var(--el-color-primary-light-9);
  border-color: var(--el-color-primary-light-7);
}

.message-type {
  margin-bottom: 4px;
}

.composer {
  flex: 0 0 auto;
  padding: 10px 14px 12px;
  border-top: 1px solid var(--app-border);
  background: #fff;
}

.composer-tools {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 8px;
}

.composer-actions {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 12px;
  margin-top: 8px;
}

.chat-empty {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
}

.quick-list {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 10px 12px;
}

.quick-item {
  padding: 10px;
  border: 1px solid var(--app-border);
  border-radius: 8px;
  cursor: pointer;
  margin-bottom: 10px;
  background: #fff;
}

.quick-item:hover {
  border-color: var(--el-color-primary-light-5);
}

.quick-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.quick-content {
  margin-top: 8px;
  color: var(--app-ink);
  font-size: 13px;
  line-height: 1.55;
  white-space: pre-wrap;
  word-break: break-word;
}

.quick-actions {
  display: flex;
  justify-content: flex-end;
  margin-top: 6px;
}

.quick-picker-list {
  max-height: 420px;
  overflow-y: auto;
}

.quick-pick-item {
  padding: 10px 12px;
  border: 1px solid var(--app-border);
  border-radius: 8px;
  margin-bottom: 10px;
  cursor: pointer;
}

.quick-pick-item:hover {
  border-color: var(--el-color-primary-light-5);
}

.quick-pick-content {
  margin-top: 8px;
  font-size: 13px;
  line-height: 1.55;
  white-space: pre-wrap;
  word-break: break-word;
}

.pre-wrap {
  white-space: pre-wrap;
  word-break: break-word;
}

.full-width {
  width: 100%;
}

@media (max-width: 1200px) {
  .cs-workspace {
    height: auto;
    min-height: 0;
  }

  .pane-col {
    height: 640px;
    margin-bottom: 12px;
  }
}
</style>
