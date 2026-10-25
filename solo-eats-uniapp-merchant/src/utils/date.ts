/**
 * 时间展示。后端 LocalDateTime 序列化为 2026-10-25T12:03:00，
 * 直接 new Date() 在部分 Safari 上会得到 Invalid Date，这里做纯字符串裁剪。
 */

export function formatDateTime(value: string | null | undefined): string {
  if (!value) {
    return '--'
  }
  const normalized = String(value).replace('T', ' ')
  return normalized.length >= 16 ? normalized.slice(0, 16) : normalized
}

export function formatShortDateTime(value: string | null | undefined): string {
  if (!value) {
    return '--'
  }
  const normalized = String(value).replace('T', ' ')
  return normalized.length >= 16 ? normalized.slice(5, 16) : normalized
}

export function formatTime(value: string | null | undefined): string {
  if (!value) {
    return '--'
  }
  const normalized = String(value).replace('T', ' ')
  return normalized.length >= 16 ? normalized.slice(11, 16) : normalized
}

/**
 * 把后端 LocalDateTime 串解析成本地 Date，用于计算「已等待多久」。
 * 不用 new Date(str)：Safari 对 2026-10-25T12:03:00 的解析行为不一致。
 */
export function parseServerDateTime(value: string | null | undefined): Date | null {
  if (!value) {
    return null
  }
  const match = /^(\d{4})-(\d{2})-(\d{2})[T ](\d{2}):(\d{2})(?::(\d{2}))?/.exec(String(value))
  if (!match) {
    return null
  }
  const date = new Date(
    Number(match[1]),
    Number(match[2]) - 1,
    Number(match[3]),
    Number(match[4]),
    Number(match[5]),
    Number(match[6] ?? 0),
  )
  return Number.isNaN(date.getTime()) ? null : date
}
