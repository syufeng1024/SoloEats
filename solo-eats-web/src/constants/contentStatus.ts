import type { TagType } from './orderStatus'

export interface ContentStatusMeta {
  label: string
  type: TagType
}

export type ContentStatusMap = Record<number, ContentStatusMeta>

function buildMap(entries: Array<[number, string, TagType]>): ContentStatusMap {
  return Object.fromEntries(
    entries.map(([code, label, type]) => [code, { label, type }]),
  )
}

function buildOptions(map: ContentStatusMap): Array<{ value: number; label: string }> {
  return Object.entries(map).map(([value, meta]) => ({
    value: Number(value),
    label: meta.label,
  }))
}

/** t_home_floor.floor_type：FloorType.java / V26__content.sql */
export const FLOOR_TYPE = buildMap([
  [1, '轮播', 'primary'],
  [2, '金刚区', 'warning'],
  [3, '秒杀', 'danger'],
  [4, '神券', 'warning'],
  [5, '推荐店铺', 'success'],
  [6, '猜你喜欢', 'info'],
  [7, '公告条', 'info'],
])

export const FLOOR_TYPE_OPTIONS = buildOptions(FLOOR_TYPE)

/** t_home_floor.audience：0全部 1新客 2会员 3指定用户 */
export const FLOOR_AUDIENCE = buildMap([
  [0, '全部', 'info'],
  [1, '新客', 'success'],
  [2, '会员', 'warning'],
  [3, '指定用户', 'primary'],
])

export const FLOOR_AUDIENCE_OPTIONS = buildOptions(FLOOR_AUDIENCE)

/** t_banner.jump_type：JumpType.java / V26__content.sql */
export const JUMP_TYPE = buildMap([
  [1, '商品', 'primary'],
  [2, '店铺', 'success'],
  [3, '活动', 'warning'],
  [4, '页面', 'info'],
  [5, '无跳转', 'info'],
])

export const JUMP_TYPE_OPTIONS = buildOptions(JUMP_TYPE)

/** t_banner.slot_code：V26__content.sql */
export const BANNER_SLOTS: Array<{ value: string; label: string }> = [
  { value: 'HOME_TOP', label: '首页轮播' },
  { value: 'HOME_POPUP', label: '首页弹窗' },
  { value: 'SPLASH', label: '启动图' },
  { value: 'SHOP_TOP', label: '店铺页顶部' },
]

const BANNER_SLOT_LABELS: Record<string, string> = Object.fromEntries(
  BANNER_SLOTS.map((item) => [item.value, item.label]),
)

export function bannerSlotLabel(value: string | null | undefined): string {
  if (!value) return '—'
  return BANNER_SLOT_LABELS[value] ?? value
}

/** t_search_keyword.keyword_type：KeywordType.java / V26__content.sql */
export const KEYWORD_TYPE = buildMap([
  [1, '默认词', 'info'],
  [2, '热搜词', 'danger'],
  [3, '联想词', 'primary'],
  [4, '屏蔽词', 'warning'],
])

export const KEYWORD_TYPE_OPTIONS = buildOptions(KEYWORD_TYPE)

/** t_app_version.app_type：AppType.java / V26__content.sql */
export const APP_TYPE = buildMap([
  [1, '用户端', 'primary'],
  [2, '骑手端', 'warning'],
  [3, '商家端', 'success'],
])

export const APP_TYPE_OPTIONS = buildOptions(APP_TYPE)

/** 内容类表 status：0停用 1启用 */
export const CONTENT_STATUS = buildMap([
  [0, '停用', 'info'],
  [1, '启用', 'success'],
])

export const CONTENT_STATUS_OPTIONS = buildOptions(CONTENT_STATUS)

/** t_home_page.status：0草稿 1已发布 2已下线 */
export const HOME_PAGE_STATUS = buildMap([
  [0, '草稿', 'info'],
  [1, '已发布', 'success'],
  [2, '已下线', 'danger'],
])

/** t_message_template.template_type：1订单 2营销 3系统 4客服 */
export const MESSAGE_TEMPLATE_TYPE = buildMap([
  [1, '订单', 'primary'],
  [2, '营销', 'warning'],
  [3, '系统', 'info'],
  [4, '客服', 'success'],
])

export const MESSAGE_TEMPLATE_TYPE_OPTIONS = buildOptions(MESSAGE_TEMPLATE_TYPE)

/** t_message_template.status：0启用 1停用（与内容表相反） */
export const MESSAGE_TEMPLATE_STATUS = buildMap([
  [0, '启用', 'success'],
  [1, '停用', 'info'],
])

export const MESSAGE_TEMPLATE_STATUS_OPTIONS = buildOptions(MESSAGE_TEMPLATE_STATUS)

/** t_message_template.channels：逗号分隔 SITE/WS/PUSH */
export const MESSAGE_CHANNELS: Array<{ value: string; label: string }> = [
  { value: 'SITE', label: '站内信' },
  { value: 'WS', label: 'WebSocket' },
  { value: 'PUSH', label: '推送' },
]

const MESSAGE_CHANNEL_LABELS: Record<string, string> = Object.fromEntries(
  MESSAGE_CHANNELS.map((item) => [item.value, item.label]),
)

export function channelLabel(value: string): string {
  return MESSAGE_CHANNEL_LABELS[value] ?? value
}

export function formatChannels(value: string | null | undefined): string {
  if (!value) return '—'
  const labels = value
    .split(',')
    .map((item) => item.trim())
    .filter(Boolean)
    .map(channelLabel)
  return labels.length > 0 ? labels.join('、') : '—'
}

export function labelOf(
  map: ContentStatusMap,
  value: number | null | undefined,
): string {
  if (value === null || value === undefined) return '—'
  return map[value]?.label ?? '—'
}

export function tagOf(
  map: ContentStatusMap,
  value: number | null | undefined,
): TagType {
  if (value === null || value === undefined) return 'info'
  return map[value]?.type ?? 'info'
}
