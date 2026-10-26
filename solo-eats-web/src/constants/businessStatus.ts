import type { TagType } from './orderStatus'

export interface StatusMeta {
  code: number
  label: string
  tag: TagType
}

function buildMap(entries: Array<[number, string, TagType]>): Record<number, StatusMeta> {
  return Object.fromEntries(
    entries.map(([code, label, tag]) => [code, { code, label, tag }]),
  )
}

/** MerchantStatus.java：0草稿 1待审核 2已通过 3已驳回 4已停用 */
export const MERCHANT_STATUS_MAP = buildMap([
  [0, '草稿', 'info'],
  [1, '待审核', 'warning'],
  [2, '已通过', 'success'],
  [3, '已驳回', 'danger'],
  [4, '已停用', 'info'],
])

export const MERCHANT_STATUS_OPTIONS = Object.values(MERCHANT_STATUS_MAP).map((item) => ({
  value: item.code,
  label: item.label,
}))

/** ShopStatus.java：0休息 1营业 2打烊 3停用 */
export const SHOP_STATUS_MAP = buildMap([
  [0, '休息', 'info'],
  [1, '营业', 'success'],
  [2, '打烊', 'warning'],
  [3, '停用', 'danger'],
])

export const SHOP_STATUS_OPTIONS = Object.values(SHOP_STATUS_MAP).map((item) => ({
  value: item.code,
  label: item.label,
}))

/** t_spu.status：0草稿 1待审核 2上架 3下架 */
export const SPU_STATUS_MAP = buildMap([
  [0, '草稿', 'info'],
  [1, '待审核', 'warning'],
  [2, '上架', 'success'],
  [3, '下架', 'info'],
])

export const SPU_STATUS_OPTIONS = Object.values(SPU_STATUS_MAP).map((item) => ({
  value: item.code,
  label: item.label,
}))

/** t_coupon_template.status：0启用 1停用（取值反直觉，后端注释为准） */
export const COUPON_STATUS_MAP = buildMap([
  [0, '启用', 'success'],
  [1, '停用', 'info'],
])

export const COUPON_STATUS_OPTIONS = Object.values(COUPON_STATUS_MAP).map((item) => ({
  value: item.code,
  label: item.label,
}))

/** t_coupon_template.coupon_type：1满减 2折扣 3无门槛 */
export const COUPON_TYPE_LABELS: Record<number, string> = {
  1: '满减',
  2: '折扣',
  3: '无门槛',
}

export const COUPON_TYPE_OPTIONS = Object.entries(COUPON_TYPE_LABELS).map(([value, label]) => ({
  value: Number(value),
  label,
}))

/** t_coupon_template.bearer：1平台 2商家 */
export const COUPON_BEARER_LABELS: Record<number, string> = {
  1: '平台',
  2: '商家',
}

export const COUPON_BEARER_OPTIONS = Object.entries(COUPON_BEARER_LABELS).map(
  ([value, label]) => ({ value: Number(value), label }),
)

/** t_user_coupon.status：0未用 1锁定 2已用 3过期 4作废 5已退款 */
export const USER_COUPON_STATUS_MAP = buildMap([
  [0, '未使用', 'primary'],
  [1, '锁定', 'warning'],
  [2, '已使用', 'success'],
  [3, '已过期', 'info'],
  [4, '已作废', 'danger'],
  [5, '已退款', 'info'],
])

export const USER_COUPON_STATUS_OPTIONS = Object.values(USER_COUPON_STATUS_MAP).map(
  (item) => ({ value: item.code, label: item.label }),
)

/** 履约方式位掩码：1平台配送 2到店自取 */
export const DELIVERY_TYPE_OPTIONS = [
  { value: 1, label: '平台配送' },
  { value: 2, label: '到店自取' },
] as const

export function deliveryTypesLabel(mask: number | null | undefined): string {
  if (!mask) {
    return '—'
  }
  const labels: string[] = []
  if ((mask & 1) === 1) {
    labels.push('配送')
  }
  if ((mask & 2) === 2) {
    labels.push('自取')
  }
  return labels.length ? labels.join(' + ') : `未知（${mask}）`
}

export function statusMeta(
  map: Record<number, StatusMeta>,
  code: number | null | undefined,
): StatusMeta {
  if (code === null || code === undefined) {
    return { code: -1, label: '未知状态', tag: 'info' }
  }
  return map[code] ?? { code, label: `未知状态（${code}）`, tag: 'info' }
}
