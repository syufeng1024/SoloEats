import type { TagType } from './orderStatus'

/**
 * 客服中心枚举字典。
 * 取值以 V25__service_ticket.sql 表注释为准，前端只做展示映射。
 */
export interface ServiceStatusMeta {
  label: string
  type: TagType
}

export type ServiceStatusMap = Record<number, ServiceStatusMeta>

function buildMap(entries: Array<[number, string, TagType]>): ServiceStatusMap {
  return Object.fromEntries(
    entries.map(([code, label, type]) => [code, { label, type }]),
  )
}

function buildOptions(map: ServiceStatusMap): Array<{ value: number; label: string }> {
  return Object.entries(map).map(([value, meta]) => ({
    value: Number(value),
    label: meta.label,
  }))
}

/** t_service_ticket.status：0待受理 1处理中 2待回访 3已办结 4已关闭 */
export const TICKET_STATUS = buildMap([
  [0, '待受理', 'warning'],
  [1, '处理中', 'primary'],
  [2, '待回访', 'warning'],
  [3, '已办结', 'success'],
  [4, '已关闭', 'info'],
])

export const TICKET_STATUS_OPTIONS = buildOptions(TICKET_STATUS)

/** t_service_ticket.ticket_type：1订单问题 2退款 3配送 4投诉 5建议 */
export const TICKET_TYPE = buildMap([
  [1, '订单问题', 'primary'],
  [2, '退款', 'danger'],
  [3, '配送', 'warning'],
  [4, '投诉', 'danger'],
  [5, '建议', 'info'],
])

export const TICKET_TYPE_OPTIONS = buildOptions(TICKET_TYPE)

/** t_service_ticket.priority：0普通 1紧急 2特急 */
export const TICKET_PRIORITY = buildMap([
  [0, '普通', 'info'],
  [1, '紧急', 'warning'],
  [2, '特急', 'danger'],
])

export const TICKET_PRIORITY_OPTIONS = buildOptions(TICKET_PRIORITY)

/** t_feedback.status：0待处理 1已回复 */
export const FEEDBACK_STATUS = buildMap([
  [0, '待处理', 'warning'],
  [1, '已回复', 'success'],
])

export const FEEDBACK_STATUS_OPTIONS = buildOptions(FEEDBACK_STATUS)

/** t_service_ticket.source / submitter_type：1用户 2商家 3骑手 4平台 */
export const TICKET_SOURCE = buildMap([
  [1, '用户', 'primary'],
  [2, '商家', 'success'],
  [3, '骑手', 'warning'],
  [4, '平台', 'info'],
])

/** t_im_session.status：0进行中 1已结束 */
export const CS_SESSION_STATUS = buildMap([
  [0, '进行中', 'primary'],
  [1, '已结束', 'info'],
])

export const CS_SESSION_STATUS_OPTIONS = buildOptions(CS_SESSION_STATUS)

/** t_complaint.target_type：2商家 3骑手 4平台 */
export const COMPLAINT_TARGET = buildMap([
  [2, '商家', 'success'],
  [3, '骑手', 'warning'],
  [4, '平台', 'info'],
])

export const COMPLAINT_TARGET_OPTIONS = buildOptions(COMPLAINT_TARGET)

/** t_complaint.liability：1商家 2骑手 3平台 4用户 */
export const LIABILITY = buildMap([
  [1, '商家责任', 'warning'],
  [2, '骑手责任', 'warning'],
  [3, '平台责任', 'primary'],
  [4, '用户责任', 'info'],
])

export const LIABILITY_OPTIONS = buildOptions(LIABILITY)

/** t_complaint.punish_action：0无 1警告 2扣款 3封禁 */
export const PUNISH_ACTION = buildMap([
  [0, '无', 'info'],
  [1, '警告', 'warning'],
  [2, '扣款', 'danger'],
  [3, '封禁', 'danger'],
])

export const PUNISH_ACTION_OPTIONS = buildOptions(PUNISH_ACTION)

/** t_im_message.msg_type：1文字 2图片 3订单卡片 4位置 5常用语 */
export const IM_MSG_TYPE = buildMap([
  [1, '文字', 'primary'],
  [2, '图片', 'info'],
  [3, '订单卡片', 'success'],
  [4, '位置', 'warning'],
  [5, '常用语', 'primary'],
])

/** t_im_message.sender_type：1用户 2商家 3骑手 4平台 */
export const SENDER_TYPE = buildMap([
  [1, '用户', 'primary'],
  [2, '商家', 'success'],
  [3, '骑手', 'warning'],
  [4, '平台', 'info'],
])

export function labelOf(
  map: ServiceStatusMap,
  value: number | null | undefined,
): string {
  if (value === null || value === undefined) {
    return '—'
  }
  return map[value]?.label ?? '—'
}

export function tagOf(
  map: ServiceStatusMap,
  value: number | null | undefined,
): TagType {
  if (value === null || value === undefined) {
    return 'info'
  }
  return map[value]?.type ?? 'info'
}
