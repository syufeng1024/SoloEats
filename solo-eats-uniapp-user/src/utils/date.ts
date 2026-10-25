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
