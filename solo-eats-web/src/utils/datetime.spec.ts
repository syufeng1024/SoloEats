import { describe, expect, it } from 'vitest'
import { EMPTY_DATETIME, formatDateTime } from './datetime'

describe('formatDateTime', () => {
  it('把 ISO LocalDateTime 重排为秒级时间，不做时区换算', () => {
    expect(formatDateTime('2026-10-21T17:29:13.881')).toBe('2026-10-21 17:29:13')
    expect(formatDateTime('2026-10-21 09:05')).toBe('2026-10-21 09:05:00')
  })

  it('空值返回占位符', () => {
    expect(formatDateTime(null)).toBe(EMPTY_DATETIME)
    expect(formatDateTime(undefined)).toBe(EMPTY_DATETIME)
  })

  it('无法识别的格式保持原样，便于暴露后端字段异常', () => {
    expect(formatDateTime('not-a-date')).toBe('not-a-date')
  })
})
