import type { TagType } from './orderStatus'

/**
 * 订单中心枚举字典。
 * 取值以 V1__init_schema.sql 的列注释与 OrderStatus.java / RefundStatus.java 为准，
 * 前端只做展示映射，不自行推断业务含义。
 */
export interface OrderAdminStatusMeta {
  label: string
  type: TagType
}

export type OrderAdminStatusMap = Record<number, OrderAdminStatusMeta>

function buildMap(entries: Array<[number, string, TagType]>): OrderAdminStatusMap {
  return Object.fromEntries(
    entries.map(([code, label, type]) => [code, { label, type }]),
  )
}

function buildOptions(map: OrderAdminStatusMap): Array<{ value: number; label: string }> {
  return Object.entries(map).map(([value, meta]) => ({
    value: Number(value),
    label: meta.label,
  }))
}

/** t_refund_order.status：0申请 10商家同意 11商家拒绝 12平台仲裁 20退款中 30成功 31失败 40取消 50自动同意 */
export const REFUND_STATUS = buildMap([
  [0, '申请中', 'warning'],
  [10, '商家同意', 'primary'],
  [11, '商家拒绝', 'danger'],
  [12, '平台仲裁', 'warning'],
  [20, '退款中', 'primary'],
  [30, '退款成功', 'success'],
  [31, '退款失败', 'danger'],
  [40, '已取消', 'info'],
  [50, '自动同意', 'success'],
])

export const REFUND_STATUS_OPTIONS = buildOptions(REFUND_STATUS)

/** t_refund_order.refund_type：1全额 2部分 3迟到支付 */
export const REFUND_TYPE = buildMap([
  [1, '全额退款', 'primary'],
  [2, '部分退款', 'warning'],
  [3, '迟到支付退款', 'info'],
])

export const REFUND_TYPE_OPTIONS = buildOptions(REFUND_TYPE)

/** t_order.exception_type：1无骑手 2联系不上 3地址异常 4餐品破损 5商家超时 6自取超时 7其他 */
export const EXCEPTION_TYPE = buildMap([
  [1, '无骑手', 'danger'],
  [2, '联系不上', 'warning'],
  [3, '地址异常', 'warning'],
  [4, '餐品破损', 'danger'],
  [5, '商家超时', 'warning'],
  [6, '自取超时', 'warning'],
  [7, '其他', 'info'],
])

export const EXCEPTION_TYPE_OPTIONS = buildOptions(EXCEPTION_TYPE)

/** t_order.fulfillment_type：1配送 2自取 */
export const FULFILLMENT_TYPE = buildMap([
  [1, '配送', 'primary'],
  [2, '自取', 'success'],
])

/** t_order.payment_status：0未支付 1已支付 2已退款 3部分退款 */
export const PAYMENT_STATUS = buildMap([
  [0, '未支付', 'info'],
  [1, '已支付', 'success'],
  [2, '已退款', 'danger'],
  [3, '部分退款', 'warning'],
])

/** t_order.refund_status：0无 1部分 2全额 */
export const ORDER_REFUND_STATUS = buildMap([
  [0, '无退款', 'info'],
  [1, '部分退款', 'warning'],
  [2, '全额退款', 'danger'],
])

/** t_order.settlement_status：0待结算 1已结算 2冻结 */
export const SETTLEMENT_STATUS = buildMap([
  [0, '待结算', 'info'],
  [1, '已结算', 'success'],
  [2, '冻结', 'danger'],
])

/** t_order_status_log.operator_type：1用户 2商家 3骑手 4平台 5系统 */
export const OPERATOR_TYPE = buildMap([
  [1, '用户', 'info'],
  [2, '商家', 'primary'],
  [3, '骑手', 'success'],
  [4, '平台', 'warning'],
  [5, '系统', 'info'],
])

/** t_refund_item.restock_flag：0不回补 1回补 */
export const RESTOCK_FLAG = buildMap([
  [0, '不回补', 'info'],
  [1, '回补库存', 'success'],
])

export function labelOf(
  map: OrderAdminStatusMap,
  value: number | null | undefined,
): string {
  if (value === null || value === undefined) {
    return '—'
  }
  return map[value]?.label ?? `未知（${value}）`
}

export function tagOf(
  map: OrderAdminStatusMap,
  value: number | null | undefined,
): TagType {
  if (value === null || value === undefined) {
    return 'info'
  }
  return map[value]?.type ?? 'info'
}
