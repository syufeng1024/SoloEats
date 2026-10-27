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
