import { describe, expect, it } from 'vitest'
import { EMPTY_AMOUNT, formatCents, formatYuan } from './money'

describe('money', () => {
  it('把分转换为两位小数的元', () => {
    expect(formatCents(0)).toBe('0.00')
    expect(formatCents(2800)).toBe('28.00')
    expect(formatCents(123456)).toBe('1,234.56')
    expect(formatCents(-500)).toBe('-5.00')
  })

  it('空值与非法数值统一返回占位符', () => {
    expect(formatCents(null)).toBe(EMPTY_AMOUNT)
    expect(formatCents(undefined)).toBe(EMPTY_AMOUNT)
    expect(formatCents(Number.NaN)).toBe(EMPTY_AMOUNT)
    expect(formatCents(Number.POSITIVE_INFINITY)).toBe(EMPTY_AMOUNT)
  })

  it('金额前缀只加在有效数值上', () => {
    expect(formatYuan(2800)).toBe('¥28.00')
    expect(formatYuan(null)).toBe(EMPTY_AMOUNT)
  })
})
