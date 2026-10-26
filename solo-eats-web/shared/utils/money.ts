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

/**
 * 表单回填用：分 → 元数值（不带千分位）。仅用于 el-input-number 的 v-model。
 */
export function centsToYuan(cents: number | null | undefined): number {
  if (cents === null || cents === undefined || !Number.isFinite(cents)) {
    return 0
  }
  return cents / 100
}

/**
 * 表单提交用：元 → 分。使用 Math.round 规避 0.1 + 0.2 类浮点误差。
 */
export function yuanToFen(yuan: number | null | undefined): number {
  if (yuan === null || yuan === undefined || !Number.isFinite(yuan)) {
    return 0
  }
  return Math.round(yuan * 100)
}
