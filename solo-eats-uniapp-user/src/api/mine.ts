import { request } from './http'
import type { PageResult } from './types'

/**
 * 「我的」相关接口。字段口径对齐后端 record：
 * - WalletLogView / MemberProfileView / PointAccountView / PointLogView
 * - CheckinStatusView / CheckinResultView / FaqView
 * - TicketView / TicketRecordView / TicketDetailView
 * 金额为分（number），时间为 LocalDateTime ISO 字符串。
 */

/* ------------------------------------------------------------------ */
/* 钱包流水                                                            */
/* ------------------------------------------------------------------ */

/** GET /c/wallet/logs → WalletLogView。direction：1 收入 / 2 支出，amount 恒为正数。 */
export interface WalletLogVO {
  id: number
  bizType: string
  bizId: number | null
  direction: number
  amount: number
  beforeBalance: number
  afterBalance: number
  remark: string | null
  operatorName: string | null
  createdAt: string | null
}

/** GET /c/wallet/logs?pageNum=&pageSize= */
export function fetchWalletLogs(
  pageNum = 1,
  pageSize = 20,
  silent = false,
): Promise<PageResult<WalletLogVO>> {
  return request<PageResult<WalletLogVO>>({
    url: '/v1/c/wallet/logs',
    data: { pageNum, pageSize },
    silent,
  })
}

/* ------------------------------------------------------------------ */
/* 会员等级 / 积分                                                     */
/* ------------------------------------------------------------------ */

/**
 * GET /c/member/profile → MemberProfileView。
 * growth 是近 365 天有效成长值合计；nextThreshold 是下一档门槛（不是百分比）。
 */
export interface MemberProfileVO {
  userId: number
  levelId: number | null
  levelCode: string | null
  levelName: string | null
  levelNo: number | null
  star: number | null
  growth: number
  nextThreshold: number
  growthToNext: number
  maxLevel: boolean
  benefitsJson: string | null
}

/** GET /c/points → PointAccountView */
export interface PointAccountVO {
  userId: number
  balance: number
  totalEarned: number
  totalConsumed: number
  updatedAt: string | null
}

/** GET /c/points/logs → PointLogView，point 可负（兑换、过期）。 */
export interface PointLogVO {
  id: number
  /** 1下单 2签到 3评价 4邀请 5兑换 6过期 7手工 */
  source: number
  bizId: number | null
  point: number
  beforeBalance: number
  afterBalance: number
  expireAt: string | null
  remark: string | null
  createdAt: string | null
}

export function fetchMemberProfile(silent = false): Promise<MemberProfileVO> {
  return request<MemberProfileVO>({ url: '/v1/c/member/profile', silent })
}

export function fetchPointAccount(silent = false): Promise<PointAccountVO> {
  return request<PointAccountVO>({ url: '/v1/c/points', silent })
}

export function fetchPointLogs(
  pageNum = 1,
  pageSize = 10,
  silent = false,
): Promise<PageResult<PointLogVO>> {
  return request<PageResult<PointLogVO>>({
    url: '/v1/c/points/logs',
    data: { pageNum, pageSize },
    silent,
  })
}

/* ------------------------------------------------------------------ */
/* 签到                                                                */
/* ------------------------------------------------------------------ */

/** GET /c/checkin/status → CheckinStatusView */
export interface CheckinStatusVO {
  userId: number
  today: string
  checkedInToday: boolean
  continuousDays: number
  todayPointAward: number | null
  lastCheckinDate: string | null
  monthCompensationsUsed: number
  monthCompensationLimit: number
  compensableFrom: string | null
  compensableTo: string | null
}

/** POST /c/checkin → CheckinResultView。同日重复签到后端返回 40207。 */
export interface CheckinResultVO {
  recordId: number
  checkinDate: string
  continuousDays: number
  pointAward: number
  growthAward: number
}

export function fetchCheckinStatus(silent = false): Promise<CheckinStatusVO> {
  return request<CheckinStatusVO>({ url: '/v1/c/checkin/status', silent })
}

export function checkin(): Promise<CheckinResultVO> {
  return request<CheckinResultVO>({ url: '/v1/c/checkin', method: 'POST' })
}

/* ------------------------------------------------------------------ */
/* 帮助中心                                                            */
/* ------------------------------------------------------------------ */

/** GET /c/faqs → FaqView（只返回启用中的 FAQ，无需登录） */
export interface FaqVO {
  id: number
  category: string
  question: string
  answer: string
  sort: number | null
  viewCount: number | null
  status: number | null
  createdAt: string | null
  updatedAt: string | null
}

/** GET /c/faqs?category=&keyword=，后端对 question/answer 做 LIKE。 */
export function fetchFaqs(params: { category?: string; keyword?: string } = {}): Promise<FaqVO[]> {
  const data: Record<string, unknown> = {}
  if (params.category) {
    data.category = params.category
  }
  if (params.keyword) {
    data.keyword = params.keyword
  }
  return request<FaqVO[]>({ url: '/v1/c/faqs', data })
}

/* ------------------------------------------------------------------ */
/* 工单                                                                */
/* ------------------------------------------------------------------ */

/** GET /c/tickets → TicketView */
export interface TicketVO {
  id: number
  ticketNo: string
  /** 1订单问题 2退款 3配送 4投诉 5建议 */
  ticketType: number
  source: number | null
  submitterType: number | null
  submitterId: number | null
  orderId: number | null
  subject: string
  content: string
  images: string[] | null
  /** 0普通 1紧急 2特急 */
  priority: number | null
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

/** 工单流转记录，后端按 created_at, id 正序返回。 */
export interface TicketRecordVO {
  id: number
  fromStatus: number | null
  toStatus: number | null
  action: string | null
  operatorId: number | null
  operatorName: string | null
  content: string | null
  createdAt: string | null
}

/** GET /c/tickets/{id} → TicketDetailView */
export interface TicketDetailVO {
  ticket: TicketVO
  records: TicketRecordVO[]
  complaint: unknown | null
}

/** GET /c/tickets?status=&pageNum=&pageSize=，status 为空表示全部。 */
export function fetchTickets(
  status: number | undefined,
  pageNum: number,
  pageSize: number,
): Promise<PageResult<TicketVO>> {
  const data: Record<string, unknown> = { pageNum, pageSize }
  if (status !== undefined && status !== null) {
    data.status = status
  }
  return request<PageResult<TicketVO>>({ url: '/v1/c/tickets', data })
}

/** GET /c/tickets/{id}，归属不符后端返回 90012。 */
export function fetchTicketDetail(ticketId: number): Promise<TicketDetailVO> {
  return request<TicketDetailVO>({ url: `/v1/c/tickets/${ticketId}` })
}

/** PUT /c/tickets/{id}/satisfaction，仅「已办结且未评价」的工单可提交。 */
export function submitTicketSatisfaction(
  ticketId: number,
  satisfaction: number,
  remark?: string | null,
): Promise<void> {
  return request<void>({
    url: `/v1/c/tickets/${ticketId}/satisfaction`,
    method: 'PUT',
    data: { satisfaction, satisfactionRemark: remark || null },
  })
}

/** PUT /c/tickets/{id}/close，仅待受理工单可撤单。 */
export function closeTicket(ticketId: number): Promise<void> {
  return request<void>({ url: `/v1/c/tickets/${ticketId}/close`, method: 'PUT' })
}

/* ------------------------------------------------------------------ */
/* 意见反馈                                                            */
/* ------------------------------------------------------------------ */

/** POST /c/feedbacks，content ≤ 1000 字，contact ≤ 64 字，images ≤ 9 张。 */
export function submitFeedback(payload: {
  content: string
  contact?: string | null
}): Promise<number> {
  return request<number>({
    url: '/v1/c/feedbacks',
    method: 'POST',
    data: {
      content: payload.content,
      images: [],
      contact: payload.contact || null,
    },
  })
}
