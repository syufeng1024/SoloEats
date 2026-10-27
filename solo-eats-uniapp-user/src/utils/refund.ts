/**
 * 退款口径，对齐后端 solo-eats-order：
 * - RefundStatus（退款单状态）：APPLIED(0) / REFUNDING(20) / SUCCESS(30) / FAILED(31) / CANCELLED(40)
 * - t_order.refund_status（订单级标识）：0 无 / 1 部分 / 2 全额，GET /orders/{id} 返回的是这一组
 * - RefundServiceImpl#assertRefundable：用户端只允许 WAIT_ACCEPT(10)、PREPARING(20) 整单退款
 * - 退款金额取 order.payAmount，不支持部分退款
 */
import { ORDER_STATUS } from '@/api/order'

export const ORDER_REFUND_STATUS = {
  NONE: 0,
  PARTIAL: 1,
  FULL: 2,
} as const

const ORDER_REFUND_LABELS: Record<number, string> = {
  [ORDER_REFUND_STATUS.NONE]: '无退款',
  [ORDER_REFUND_STATUS.PARTIAL]: '部分退款',
  [ORDER_REFUND_STATUS.FULL]: '全额退款',
}

/** 订单详情的 refundStatus 是订单级标识，不能套用退款单的 RefundStatus。 */
export function orderRefundStatusLabel(status: number | null | undefined): string {
  if (status === null || status === undefined) {
    return '订单详情未返回退款状态'
  }
  return ORDER_REFUND_LABELS[status] || `未知退款标识（${status}）`
}

export const REFUND_STATUS = {
  APPLIED: 0,
  REFUNDING: 20,
  SUCCESS: 30,
  FAILED: 31,
  CANCELLED: 40,
} as const

const REFUND_STATUS_LABELS: Record<number, string> = {
  [REFUND_STATUS.APPLIED]: '已申请',
  [REFUND_STATUS.REFUNDING]: '退款中',
  [REFUND_STATUS.SUCCESS]: '退款成功',
  [REFUND_STATUS.FAILED]: '退款失败',
  [REFUND_STATUS.CANCELLED]: '退款已取消',
}

/** 申请页的原因选项。后端 RefundRequest 只有 reason 一个字段（@NotBlank）。 */
export const REFUND_REASONS = [
  '临时有事，不想要了',
  '商家出餐太慢',
  '商品已售罄',
  '收货地址填错了',
  '重复下单',
  '其他原因',
]

export function refundStatusLabel(status: number | null | undefined): string {
  if (status === null || status === undefined) {
    return '无退款记录'
  }
  return REFUND_STATUS_LABELS[status] || `未知状态（${status}）`
}

/** 与 RefundServiceImpl#assertRefundable(order, false) 保持一致。 */
export function canApplyRefund(orderStatus: number): boolean {
  return (
    orderStatus === ORDER_STATUS.WAIT_ACCEPT || orderStatus === ORDER_STATUS.PREPARING
  )
}

/** 不可退款时给用户的具体原因，避免只显示「暂不可申请」。 */
export function refundBlockedReason(orderStatus: number): string {
  if (orderStatus === ORDER_STATUS.WAIT_PAY) {
    return '订单尚未支付，请直接在订单详情取消订单'
  }
  if (orderStatus === ORDER_STATUS.CANCELLED) {
    return '订单已取消，无需重复申请退款'
  }
  if (
    orderStatus === ORDER_STATUS.WAIT_RIDER_PICKUP ||
    orderStatus === ORDER_STATUS.WAIT_USER_PICKUP ||
    orderStatus === ORDER_STATUS.DELIVERING
  ) {
    return '餐品已进入制作完成或配送阶段，App 端不支持自助退款，请联系客服处理'
  }
  if (
    orderStatus === ORDER_STATUS.DELIVERED ||
    orderStatus === ORDER_STATUS.PICKED_UP ||
    orderStatus === ORDER_STATUS.COMPLETED
  ) {
    return '订单已完成，App 端不支持自助退款，可通过工单或客服反馈'
  }
  return '当前订单状态不支持自助退款'
}
