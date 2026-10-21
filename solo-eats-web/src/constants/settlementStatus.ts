import type { TagType } from './orderStatus'

export interface SettlementStatusMeta {
  code: number
  label: string
  tag: TagType
}

/**
 * 与 t_settlement_bill.status 注释一致：
 * 0已生成 10待审核 20已审核 30已打款 40已驳回。
 */
export const SETTLEMENT_STATUS_MAP: Record<number, SettlementStatusMeta> = {
  0: { code: 0, label: '已生成', tag: 'info' },
  10: { code: 10, label: '待审核', tag: 'warning' },
  20: { code: 20, label: '已审核', tag: 'primary' },
  30: { code: 30, label: '已打款', tag: 'success' },
  40: { code: 40, label: '已驳回', tag: 'danger' },
}

export const SETTLEMENT_STATUS_OPTIONS = Object.values(SETTLEMENT_STATUS_MAP).map((item) => ({
  value: item.code,
  label: item.label,
}))

export function settlementStatusMeta(code: number | null | undefined): SettlementStatusMeta {
  if (code === null || code === undefined) {
    return { code: -1, label: '未知状态', tag: 'info' }
  }
  return SETTLEMENT_STATUS_MAP[code] ?? { code, label: `未知状态（${code}）`, tag: 'info' }
}
