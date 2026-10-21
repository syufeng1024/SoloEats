/**
 * 后端金额统一为 bigint，单位分（见 V1__init_schema.sql 头部说明）。
 * 展示层只在这里做分转元，业务代码不得直接除以 100。
 */
const currencyFormatter = new Intl.NumberFormat('zh-CN', {
  minimumFractionDigits: 2,
  maximumFractionDigits: 2,
})

export const EMPTY_AMOUNT = '—'

export function formatCents(cents: number | null | undefined): string {
  if (cents === null || cents === undefined || !Number.isFinite(cents)) {
    return EMPTY_AMOUNT
  }
  return currencyFormatter.format(cents / 100)
}

export function formatYuan(cents: number | null | undefined): string {
  const value = formatCents(cents)
  return value === EMPTY_AMOUNT ? value : `¥${value}`
}
