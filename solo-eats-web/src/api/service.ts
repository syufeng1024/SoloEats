import { request } from './http'
import type { PageResult } from './types'

/**
 * T09 客服中心视图与请求类型。
 * 字段与 solo-eats-service/src/main/java/com/soloeats/service/service/view/*.java 一一对应。
 */

/** TicketView.java，overdue 由后端按当前时间计算，列表仍在前端按同一规则兜底标红。 */
export interface TicketView {
  id: number
  ticketNo: string
  /** 1订单问题 2退款 3配送 4投诉 5建议 */
  ticketType: number
  /** 1用户 2商家 3骑手 4平台 */
  source: number
  /** 1用户 2商家 3骑手 4平台 */
  submitterType: number
  submitterId: number
  orderId: number | null
  subject: string
  content: string
  images: string[]
  /** 0普通 1紧急 2特急 */
  priority: number
  /** 0待受理 1处理中 2待回访 3已办结 4已关闭 */
  status: number
  assigneeId: number | null
  acceptAt: string | null
  deadlineAt: string | null
  finishAt: string | null
  satisfaction: number | null
  satisfactionRemark: string | null
  overdue: boolean
  createdAt: string
}

/** TicketRecordView.java */
export interface TicketRecordView {
  id: number
  fromStatus: number | null
  toStatus: number | null
  action: string
  operatorId: number | null
  operatorName: string | null
  content: string | null
  createdAt: string
}

/** ComplaintView.java */
export interface ComplaintView {
  id: number
  ticketId: number
  orderId: number
  /** 1用户 2商家 3骑手 4平台 */
  complainantType: number
  complainantId: number
  /** 2商家 3骑手 4平台 */
  targetType: number
  targetId: number
  reason: string
  /** 1商家 2骑手 3平台 4用户 */
  liability: number | null
  result: string | null
  /** 0无 1警告 2扣款 3封禁 */
  punishAction: number
  callbackAt: string | null
  callbackResult: string | null
  createdAt: string
}

/** TicketDetailView.java，无关联投诉时 complaint 为 null。 */
export interface TicketDetailView {
  ticket: TicketView
  records: TicketRecordView[]
  complaint: ComplaintView | null
}

/** TicketStatsView.java，solvedRate 为 0~1 四位小数。 */
export interface TicketStatsView {
  ticketCount: number
  solvedRate: number
  avgResponseMinutes: number
  satisfactionAvg: number
}

/** FaqView.java */
export interface FaqView {
  id: number
  category: string
  question: string
  answer: string
  sort: number
  viewCount: number
  /** 0启用 1停用 */
  status: number
  createdAt: string
  updatedAt: string
}

/** FeedbackView.java */
export interface FeedbackView {
  id: number
  userId: number
  content: string
  images: string[]
  contact: string | null
  /** 0待处理 1已回复 */
  status: number
  reply: string | null
  replyAt: string | null
  createdAt: string
}

export interface TicketListParams {
  status?: number
  ticketType?: number
  assigneeId?: number
  priority?: number
  /** yyyy-MM-dd */
  dateFrom?: string
  /** yyyy-MM-dd */
  dateTo?: string
  pageNum?: number
  pageSize?: number
}

export interface TicketStatsParams {
  /** yyyy-MM-dd */
  dateFrom: string
  /** yyyy-MM-dd */
  dateTo: string
}

export interface TicketAssignPayload {
  assigneeId: number
}

export interface TicketHandlePayload {
  content: string
}

export interface TicketCallbackPayload {
  content: string
  result?: string | null
}

export interface FaqListParams {
  category?: string
  keyword?: string
  /** 0启用 1停用 */
  status?: number
  pageNum?: number
  pageSize?: number
}

export interface FaqCommandPayload {
  category: string
  question: string
  answer: string
  sort?: number | null
  /** 0启用 1停用 */
  status: number
}

export interface FeedbackListParams {
  status?: number
  pageNum?: number
  pageSize?: number
}

export interface FeedbackReplyPayload {
  reply: string
}

export interface ComplaintJudgePayload {
  /** 1商家 2骑手 3平台 4用户 */
  liability: number
  result: string
  /** 0无 1警告 2扣款 3封禁 */
  punishAction: number
}

/** AgentSessionView.java，坐席端客服会话；agentId 为空表示待接待。 */
export interface AgentSessionView {
  id: number
  sessionNo: string
  userId: number
  orderId: number | null
  peerType: number
  peerId: number
  agentId: number | null
  lastMessage: string | null
  lastMessageAt: string | null
  userUnread: number
  /** 0进行中 1已结束 */
  status: number
  createdAt: string
}

/** ImMessageView.java，content 为纯文本，extra 为已解析的结构化对象。 */
export interface ImMessageView {
  id: number
  sessionId: number
  /** 1用户 2商家 3骑手 4平台 */
  senderType: number
  senderId: number
  /** 1文字 2图片 3订单卡片 4位置 5常用语 */
  msgType: number
  content: string
  extra: unknown
  readFlag: number
  createdAt: string
}

/** CsStatsView.java */
export interface CsStatsView {
  servedSessions: number
  activeSessions: number
  waitingSessions: number
}

/** QuickReplyView.java，status：0启用 1停用。 */
export interface QuickReplyView {
  id: number
  category: string
  content: string
  sort: number
  status: number
  createdAt: string
  updatedAt: string
}

export interface CsSessionListParams {
  status?: number
  agentId?: number
  pageNum?: number
  pageSize?: number
}

export interface CsMessageListParams {
  beforeMessageId?: number
  pageSize?: number
}

export interface CsSendMessagePayload {
  /** 1文字 2图片 3订单卡片 4位置 5常用语 */
  msgType: number
  content: string
  extraJson?: string
}

export interface TransferCsSessionPayload {
  toAgentId: number
}

export interface CsStatsParams {
  /** yyyy-MM-dd */
  dateFrom: string
  /** yyyy-MM-dd */
  dateTo: string
  agentId?: number
}

export interface QuickReplyCommandPayload {
  category: string
  content: string
  sort?: number | null
  /** 0启用 1停用 */
  status: number
}

/** GET /api/v1/admin/tickets */
export function listTickets(params: TicketListParams): Promise<PageResult<TicketView>> {
  return request<PageResult<TicketView>>({
    url: '/v1/admin/tickets',
    method: 'GET',
    params,
  })
}

/** GET /api/v1/admin/tickets/stats */
export function getTicketStats(params: TicketStatsParams): Promise<TicketStatsView> {
  return request<TicketStatsView>({
    url: '/v1/admin/tickets/stats',
    method: 'GET',
    params,
  })
}

/** GET /api/v1/admin/tickets/{id} */
export function getTicketDetail(id: number): Promise<TicketDetailView> {
  return request<TicketDetailView>({
    url: `/v1/admin/tickets/${id}`,
    method: 'GET',
  })
}

/** POST /api/v1/admin/tickets/{id}/accept */
export function acceptTicket(id: number): Promise<void> {
  return request<void>({
    url: `/v1/admin/tickets/${id}/accept`,
    method: 'POST',
  })
}

/** POST /api/v1/admin/tickets/{id}/assign */
export function assignTicket(id: number, payload: TicketAssignPayload): Promise<void> {
  return request<void>({
    url: `/v1/admin/tickets/${id}/assign`,
    method: 'POST',
    data: payload,
  })
}

/** POST /api/v1/admin/tickets/{id}/handle */
export function handleTicket(id: number, payload: TicketHandlePayload): Promise<void> {
  return request<void>({
    url: `/v1/admin/tickets/${id}/handle`,
    method: 'POST',
    data: payload,
  })
}

/** POST /api/v1/admin/tickets/{id}/callback */
export function callbackTicket(
  id: number,
  payload: TicketCallbackPayload,
): Promise<void> {
  return request<void>({
    url: `/v1/admin/tickets/${id}/callback`,
    method: 'POST',
    data: payload,
  })
}

/** POST /api/v1/admin/tickets/{id}/finish */
export function finishTicket(id: number): Promise<void> {
  return request<void>({
    url: `/v1/admin/tickets/${id}/finish`,
    method: 'POST',
  })
}

/** POST /api/v1/admin/tickets/{id}/close */
export function closeTicket(id: number): Promise<void> {
  return request<void>({
    url: `/v1/admin/tickets/${id}/close`,
    method: 'POST',
  })
}

/** GET /api/v1/admin/faqs */
export function listFaqs(params: FaqListParams): Promise<PageResult<FaqView>> {
  return request<PageResult<FaqView>>({
    url: '/v1/admin/faqs',
    method: 'GET',
    params,
  })
}

/** POST /api/v1/admin/faqs */
export function createFaq(payload: FaqCommandPayload): Promise<number> {
  return request<number>({
    url: '/v1/admin/faqs',
    method: 'POST',
    data: payload,
  })
}

/** PUT /api/v1/admin/faqs/{id} */
export function updateFaq(id: number, payload: FaqCommandPayload): Promise<void> {
  return request<void>({
    url: `/v1/admin/faqs/${id}`,
    method: 'PUT',
    data: payload,
  })
}

/** DELETE /api/v1/admin/faqs/{id} */
export function deleteFaq(id: number): Promise<void> {
  return request<void>({
    url: `/v1/admin/faqs/${id}`,
    method: 'DELETE',
  })
}

/** GET /api/v1/admin/feedbacks */
export function listFeedbacks(
  params: FeedbackListParams,
): Promise<PageResult<FeedbackView>> {
  return request<PageResult<FeedbackView>>({
    url: '/v1/admin/feedbacks',
    method: 'GET',
    params,
  })
}

/** PUT /api/v1/admin/feedbacks/{id}/reply */
export function replyFeedback(
  id: number,
  payload: FeedbackReplyPayload,
): Promise<void> {
  return request<void>({
    url: `/v1/admin/feedbacks/${id}/reply`,
    method: 'PUT',
    data: payload,
  })
}

/** PUT /api/v1/admin/complaints/{id}/judge */
export function judgeComplaint(
  id: number,
  payload: ComplaintJudgePayload,
): Promise<void> {
  return request<void>({
    url: `/v1/admin/complaints/${id}/judge`,
    method: 'PUT',
    data: payload,
  })
}

/** GET /api/v1/admin/cs/sessions */
export function listCsSessions(
  params: CsSessionListParams,
): Promise<PageResult<AgentSessionView>> {
  return request<PageResult<AgentSessionView>>({
    url: '/v1/admin/cs/sessions',
    method: 'GET',
    params,
  })
}

/** POST /api/v1/admin/cs/sessions/{id}/take */
export function takeCsSession(id: number): Promise<void> {
  return request<void>({
    url: `/v1/admin/cs/sessions/${id}/take`,
    method: 'POST',
  })
}

/** POST /api/v1/admin/cs/sessions/{id}/transfer */
export function transferCsSession(
  id: number,
  payload: TransferCsSessionPayload,
): Promise<void> {
  return request<void>({
    url: `/v1/admin/cs/sessions/${id}/transfer`,
    method: 'POST',
    data: payload,
  })
}

/** POST /api/v1/admin/cs/sessions/{id}/close */
export function closeCsSession(id: number): Promise<void> {
  return request<void>({
    url: `/v1/admin/cs/sessions/${id}/close`,
    method: 'POST',
  })
}

/** GET /api/v1/admin/cs/sessions/{id}/messages */
export function listCsMessages(
  id: number,
  params?: CsMessageListParams,
): Promise<ImMessageView[]> {
  return request<ImMessageView[]>({
    url: `/v1/admin/cs/sessions/${id}/messages`,
    method: 'GET',
    params,
  })
}

/** POST /api/v1/admin/cs/sessions/{id}/messages */
export function sendCsMessage(
  id: number,
  payload: CsSendMessagePayload,
): Promise<ImMessageView> {
  return request<ImMessageView>({
    url: `/v1/admin/cs/sessions/${id}/messages`,
    method: 'POST',
    data: payload,
  })
}

/** GET /api/v1/admin/cs/quick-replies */
export function listQuickReplies(category?: string): Promise<QuickReplyView[]> {
  return request<QuickReplyView[]>({
    url: '/v1/admin/cs/quick-replies',
    method: 'GET',
    params: { category },
  })
}

/** POST /api/v1/admin/cs/quick-replies */
export function createQuickReply(payload: QuickReplyCommandPayload): Promise<number> {
  return request<number>({
    url: '/v1/admin/cs/quick-replies',
    method: 'POST',
    data: payload,
  })
}

/** PUT /api/v1/admin/cs/quick-replies/{id} */
export function updateQuickReply(
  id: number,
  payload: QuickReplyCommandPayload,
): Promise<void> {
  return request<void>({
    url: `/v1/admin/cs/quick-replies/${id}`,
    method: 'PUT',
    data: payload,
  })
}

/** DELETE /api/v1/admin/cs/quick-replies/{id} */
export function deleteQuickReply(id: number): Promise<void> {
  return request<void>({
    url: `/v1/admin/cs/quick-replies/${id}`,
    method: 'DELETE',
  })
}

/** GET /api/v1/admin/cs/stats */
export function getCsStats(params: CsStatsParams): Promise<CsStatsView> {
  return request<CsStatsView>({
    url: '/v1/admin/cs/stats',
    method: 'GET',
    params,
  })
}
