import { describe, expect, it } from 'vitest'
import { orderStatusMeta } from './orderStatus'
import { settlementStatusMeta } from './settlementStatus'

describe('status metadata', () => {
  it('订单状态与 OrderStatus.java 对齐', () => {
    expect(orderStatusMeta(10)).toEqual({ code: 10, label: '待接单', tag: 'danger' })
    expect(orderStatusMeta(20).label).toBe('备餐中')
    expect(orderStatusMeta(60).tag).toBe('success')
  })

  it('未知订单状态保留原始 code，便于排查后端新增枚举', () => {
    const meta = orderStatusMeta(999)
    expect(meta.code).toBe(999)
    expect(meta.label).toContain('999')
  })

  it('结算状态与 t_settlement_bill.status 注释对齐', () => {
    expect(settlementStatusMeta(0).label).toBe('已生成')
    expect(settlementStatusMeta(30).label).toBe('已打款')
    expect(settlementStatusMeta(null).label).toBe('未知状态')
  })
})
