import { request } from './http'
import type { PageResult } from './types'

/**
 * 消息中心：站内信 + IM 会话。
 * - 站内信 GET /messages：receiverType/receiverId 由后端按令牌解析（商家取 t_merchant.id）
 * - IM GET /im/sessions：商家侧拿到的会话 peer_type=2、peer_id=本人 merchantId，
 *   t_im_session.user_id 恒为消费者，因此对端展示统一为「用户 #userId」
 * - 后端没有 GET /messages/{id}，详情只能靠列表数据展开，不另发请求
 */

/** GET /messages → SiteMessageView */
export interface SiteMessageVO {
  id: number
  receiverType: number
  receiverId: number
  templateCode: string | null
  /** 1订单 2营销 3系统 */
  msgType: number
  title: string
  content: string
  bizType: string | null
  bizId: number | null
  /** 0未读 1已读 */
  readFlag: number
  readAt: string | null
  createdAt: string
}

/** GET /messages/unread-count → UnreadCountView */
export interface UnreadCountVO {
  total: number
  byType: {
    order: number
    marketing: number
    system: number
  }
}

export interface SiteMessageQuery {
  msgType?: number
  readFlag?: number
  pageNum?: number
  pageSize?: number
}

/** GET /im/sessions → ImSessionView */
export interface ImSessionVO {
  id: number
  sessionNo: string
  orderId: number | null
  /** 消费者 t_member_user.id；商家侧对端恒为用户 */
  userId: number
  /** 被咨询方类型：商家列表固定为 2 */
  peerType: number
  peerId: number
  lastMessage: string | null
  lastMessageAt: string | null
  /** 当前调用方（商家）自己的未读数 */
  unread: number
  /** 0进行中 1已结束 2只读 */
  status: number
  createdAt: string
}

/** GET /im/sessions/{id}/messages → ImMessageView */
export interface ImMessageVO {
  id: number
  sessionId: number
  /** 1用户 2商家 3骑手 4平台 */
  senderType: number
  senderId: number
  /** 1文字 2图片 3订单卡片 4位置 5常用语 */
  msgType: number
  content: string | null
  extra: unknown
  readFlag: number | null
  createdAt: string | null
}

/** GET /messages；silent 由页面自行渲染错误态，避免与 http 层 toast 重复 */
export function fetchSiteMessages(
  query: SiteMessageQuery = {},
  silent = false,
): Promise<PageResult<SiteMessageVO>> {
  const data: Record<string, unknown> = {
    pageNum: query.pageNum ?? 1,
    pageSize: query.pageSize ?? 20,
  }
  if (query.msgType !== undefined && query.msgType !== null) {
    data.msgType = query.msgType
  }
  if (query.readFlag !== undefined && query.readFlag !== null) {
    data.readFlag = query.readFlag
  }
  return request<PageResult<SiteMessageVO>>({ url: '/v1/messages', data, silent })
}

/** GET /messages/unread-count。silent 用于页头角标，避免和列表请求重复弹 toast。 */
export function fetchUnreadCount(silent = false): Promise<UnreadCountVO> {
  return request<UnreadCountVO>({ url: '/v1/messages/unread-count', silent })
}

/** PUT /messages/{id}/read；失败不打断阅读，故 silent。 */
export function markSiteMessageRead(id: number): Promise<void> {
  return request<void>({ url: `/v1/messages/${id}/read`, method: 'PUT', silent: true })
}

/** GET /im/sessions */
export function fetchImSessions(silent = false): Promise<ImSessionVO[]> {
  return request<ImSessionVO[]>({ url: '/v1/im/sessions', silent })
}

/**
 * GET /im/sessions/{id}/messages：时间正序，beforeMessageId 取当前最早一条的 id 翻更早。
 * silent=true 用于会话页 10s 轮询，避免网络抖动时每次轮询都弹一次 toast。
 */
export function fetchImMessages(
  sessionId: number,
  beforeMessageId?: number,
  pageSize = 20,
  silent = false,
): Promise<ImMessageVO[]> {
  const data: Record<string, unknown> = { pageSize }
  if (beforeMessageId !== undefined && beforeMessageId !== null) {
    data.beforeMessageId = beforeMessageId
  }
  return request<ImMessageVO[]>({
    url: `/v1/im/sessions/${sessionId}/messages`,
    data,
    silent,
  })
}

/** POST /im/sessions/{id}/messages；商家端只发文字，msgType 固定 1。 */
export function sendImMessage(
  sessionId: number,
  payload: { msgType?: number; content: string; extra?: Record<string, unknown> },
): Promise<ImMessageVO> {
  const data: Record<string, unknown> = {
    msgType: payload.msgType ?? 1,
    content: payload.content,
  }
  if (payload.extra) {
    data.extra = payload.extra
  }
  return request<ImMessageVO>({
    url: `/v1/im/sessions/${sessionId}/messages`,
    method: 'POST',
    data,
  })
}

/** PUT /im/sessions/{id}/read（后端为 PUT，不是 POST）。 */
export function markImSessionRead(sessionId: number): Promise<void> {
  return request<void>({
    url: `/v1/im/sessions/${sessionId}/read`,
    method: 'PUT',
    silent: true,
  })
}
