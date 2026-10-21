export const EMPTY_DATETIME = '—'

/**
 * 后端 LocalDateTime 序列化为 ISO 字符串（无时区偏移），
 * 这里按字面量重排为 yyyy-MM-dd HH:mm:ss，避免浏览器时区换算导致时间漂移。
 */
export function formatDateTime(value: string | null | undefined): string {
  if (!value) {
    return EMPTY_DATETIME
  }
  const match = /^(\d{4})-(\d{2})-(\d{2})[T ](\d{2}):(\d{2})(?::(\d{2}))?/.exec(value)
  if (!match) {
    return value
  }
  const [, year, month, day, hour, minute, second = '00'] = match
  return `${year}-${month}-${day} ${hour}:${minute}:${second}`
}
