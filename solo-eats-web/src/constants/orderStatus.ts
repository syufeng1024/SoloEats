export type TagType = 'primary' | 'success' | 'warning' | 'info' | 'danger'

export interface OrderStatusMeta {
  code: number
  label: string
  tag: TagType
}

/**
 * 与 solo-eats-order/domain/enums/OrderStatus.java 一一对应。
 * 后端只返回 code，文案在前端集中维护。
 */
export const ORDER_STATUS_MAP: Record<number, OrderStatusMeta> = {
  0: { code: 0, label: '待支付', tag: 'warning' },
  10: { code: 10, label: '待接单', tag: 'danger' },
  20: { code: 20, label: '备餐中', tag: 'warning' },
  30: { code: 30, label: '待骑手取货', tag: 'primary' },
  31: { code: 31, label: '待用户取餐', tag: 'primary' },
  40: { code: 40, label: '配送中', tag: 'primary' },
  50: { code: 50, label: '已送达', tag: 'success' },
  51: { code: 51, label: '已取餐', tag: 'success' },
  60: { code: 60, label: '已完成', tag: 'success' },
  70: { code: 70, label: '已取消', tag: 'info' },
}

export const ORDER_STATUS_OPTIONS = Object.values(ORDER_STATUS_MAP).map((item) => ({
  value: item.code,
  label: item.label,
}))

export function orderStatusMeta(code: number | null | undefined): OrderStatusMeta {
  if (code === null || code === undefined) {
    return { code: -1, label: '未知状态', tag: 'info' }
  }
  return ORDER_STATUS_MAP[code] ?? { code, label: `未知状态（${code}）`, tag: 'info' }
}
