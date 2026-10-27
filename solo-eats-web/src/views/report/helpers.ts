const COUNT_FORMATTER = new Intl.NumberFormat('zh-CN')

function toDateInput(date: Date): string {
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

/** 生成本地自然日区间，含今天，共 dayCount 天。 */
export function defaultDateRange(dayCount: number): [string, string] {
  const end = new Date()
  const start = new Date()
  start.setDate(start.getDate() - Math.max(dayCount - 1, 0))
  return [toDateInput(start), toDateInput(end)]
}

export function formatCount(value: number | null | undefined): string {
  const numeric = Number(value)
  return Number.isFinite(numeric) ? COUNT_FORMATTER.format(numeric) : '0'
}

/**
 * 漏斗、财务、用户报表的转化率后端已经是百分比数值（10.00 表示 10.00%），
 * 这里只补 %，禁止再乘 100。
 */
export function formatPercent(value: number | null | undefined): string {
  if (value === null || value === undefined) {
    return '—'
  }
  const numeric = Number(value)
  return Number.isFinite(numeric) ? `${numeric.toFixed(2)}%` : '—'
}

/**
 * 后端按 0~1 比例返回的字段使用此函数：
 * 经营总览 refundRate / repurchaseRate、客服工单 solvedRate（0.0500 表示 5%）。
 */
export function formatRatioPercent(value: number | null | undefined): string {
  if (value === null || value === undefined) {
    return '—'
  }
  const numeric = Number(value)
  return Number.isFinite(numeric) ? `${(numeric * 100).toFixed(2)}%` : '—'
}

/** 由两个计数计算占比；用于漏斗「占曝光比例」等前端派生指标。 */
export function formatSharePercent(
  numerator: number | null | undefined,
  denominator: number | null | undefined,
): string {
  const part = Number(numerator)
  const total = Number(denominator)
  if (!Number.isFinite(part) || !Number.isFinite(total) || total <= 0) {
    return '0.00%'
  }
  return `${((part / total) * 100).toFixed(2)}%`
}

/** el-progress 只接受 0~100；原始值仍用于文字展示，这里只裁剪进度条长度。 */
export function progressValue(value: number | null | undefined): number {
  const numeric = Number(value)
  if (!Number.isFinite(numeric)) {
    return 0
  }
  return Math.min(Math.max(numeric, 0), 100)
}

export function errorMessage(error: unknown, fallback: string): string {
  return error instanceof Error ? error.message : fallback
}
