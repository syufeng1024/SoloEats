import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { debounce } from './debounce'

describe('debounce', () => {
  beforeEach(() => {
    vi.useFakeTimers()
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('300ms 内多次调用只执行最后一次', () => {
    const handler = vi.fn()
    const debounced = debounce(handler, 300)

    debounced('a')
    debounced('b')

    expect(handler).not.toHaveBeenCalled()

    vi.advanceTimersByTime(299)
    expect(handler).not.toHaveBeenCalled()

    vi.advanceTimersByTime(1)
    expect(handler).toHaveBeenCalledTimes(1)
    expect(handler).toHaveBeenCalledWith('b')
  })
})
