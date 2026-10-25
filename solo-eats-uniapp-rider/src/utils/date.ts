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

/** 后端 LocalDateTime（2026-10-25T12:03:00 / 2026-10-25 12:03:00）→ Date，非法返回 null */
export function parseServerDateTime(value: string | null | undefined): Date | null {
  if (!value) {
    return null
  }
  const normalized = String(value).trim().replace(' ', 'T')
  const parsed = new Date(normalized)
  return Number.isNaN(parsed.getTime()) ? null : parsed
}

/** 今天的 yyyy-MM-dd，用于日期选择器 start 与默认值 */
export function todayString(): string {
  const now = new Date()
  const month = String(now.getMonth() + 1).padStart(2, '0')
  const day = String(now.getDate()).padStart(2, '0')
  return `${now.getFullYear()}-${month}-${day}`
}

/**
 * 后端 LocalDateTime 入参格式，轨迹上报用。
 * 不用 toISOString()：它会转成 UTC，服务端按东八区写入会整体偏移 8 小时。
 */
export function toServerDateTime(date: Date): string {
  const pad = (value: number): string => String(value).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`
}
