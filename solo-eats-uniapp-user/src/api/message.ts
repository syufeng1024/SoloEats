import { request } from './http'
import type { PageResult } from './types'

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

/** GET /im/sessions → ImSessionView，peerType：2商家 3骑手 4客服 */
export interface ImSessionVO {
  id: number
  sessionNo: string
  orderId: number | null
  userId: number
  peerType: number
  peerId: number
  lastMessage: string | null
  lastMessageAt: string | null
  /** 当前调用方自己的未读数 */
  unread: number
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
  /** 0未读 1已读 */
  readFlag: number
  createdAt: string
}

/** GET /notify/settings → NotifySettingView，notifyType：1订单 2营销 3系统 4会话 */
export interface NotifySettingVO {
  notifyType: number
  notifyTypeName: string
  enabled: boolean
}

/** GET /messages */
export function fetchSiteMessages(query: SiteMessageQuery = {}): Promise<PageResult<SiteMessageVO>> {
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
  return request<PageResult<SiteMessageVO>>({ url: '/v1/messages', data })
}

/** GET /messages/unread-count。silent 用于列表页顶部角标，避免和列表请求重复弹 toast。 */
export function fetchUnreadCount(silent = false): Promise<UnreadCountVO> {
  return request<UnreadCountVO>({ url: '/v1/messages/unread-count', silent })
}

/**
 * PUT /messages/{id}/read。后端没有「按 ID 查单条」的接口，
 * 详情页只能靠列表页缓存或逐页扫描拿到内容，已读状态仍可单独更新。
 * 标已读失败不打断阅读，因此 silent。
 */
export function markSiteMessageRead(id: number): Promise<void> {
  return request<void>({ url: `/v1/messages/${id}/read`, method: 'PUT', silent: true })
}

/**
 * 详情页兜底：后端未提供 GET /messages/{id}，这里最多扫 5 页（250 条）找目标消息。
 * 仅用于直接进入详情页、本地没有列表页缓存的情况；找不到返回 null，由页面提示。
 */
export async function findSiteMessageById(id: number): Promise<SiteMessageVO | null> {
  const pageSize = 50
  const maxPages = 5
  for (let pageNum = 1; pageNum <= maxPages; pageNum += 1) {
    const page = await fetchSiteMessages({ pageNum, pageSize })
    const hit = page.list.find((item) => item.id === id)
    if (hit) {
      return hit
    }
    if (page.list.length < pageSize || pageNum * pageSize >= page.total) {
      break
    }
  }
  return null
}

/** GET /im/sessions */
export function fetchImSessions(silent = false): Promise<ImSessionVO[]> {
  return request<ImSessionVO[]>({ url: '/v1/im/sessions', silent })
}

/** POST /im/sessions：orderId 可选，同 (orderId, userId, peerType, peerId) 后端复用已有会话。 */
export function createImSession(payload: {
  orderId?: number
  peerType: number
  peerId: number
}): Promise<ImSessionVO> {
  const data: Record<string, unknown> = {
    peerType: payload.peerType,
    peerId: payload.peerId,
  }
  if (payload.orderId !== undefined && payload.orderId !== null) {
    data.orderId = payload.orderId
  }
  return request<ImSessionVO>({ url: '/v1/im/sessions', method: 'POST', data })
}

/** GET /im/sessions/{id}/messages：返回时间正序，beforeMessageId 取当前最早一条的 id 翻更早。 */
export function fetchImMessages(
  sessionId: number,
  beforeMessageId?: number,
  pageSize = 20,
): Promise<ImMessageVO[]> {
  const data: Record<string, unknown> = { pageSize }
  if (beforeMessageId !== undefined && beforeMessageId !== null) {
    data.beforeMessageId = beforeMessageId
  }
  return request<ImMessageVO[]>({ url: `/v1/im/sessions/${sessionId}/messages`, data })
}

/**
 * POST /im/sessions/{id}/messages，用户端只发文字，msgType 固定 1。
 * 后端 DTO 的附加字段名是 extra（不是 extraJson），当前页面不发图片/位置，留作扩展。
 */
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
  return request<ImMessageVO>({ url: `/v1/im/sessions/${sessionId}/messages`, method: 'POST', data })
}

/** PUT /im/sessions/{id}/read（后端为 PUT，不是 POST）。 */
export function markImSessionRead(sessionId: number): Promise<void> {
  return request<void>({ url: `/v1/im/sessions/${sessionId}/read`, method: 'PUT', silent: true })
}

/** GET /notify/settings */
export function fetchNotifySettings(): Promise<NotifySettingVO[]> {
  return request<NotifySettingVO[]>({ url: '/v1/notify/settings' })
}

/** PUT /notify/settings */
export function updateNotifySetting(notifyType: number, enabled: boolean): Promise<NotifySettingVO> {
  return request<NotifySettingVO>({
    url: '/v1/notify/settings',
    method: 'PUT',
    data: { notifyType, enabled },
  })
}
