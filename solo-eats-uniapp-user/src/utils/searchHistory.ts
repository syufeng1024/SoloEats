/**
 * 搜索历史：只存字符串数组，key 固定为 search_history。
 * 读取时统一做清洗（去首尾空格、去重、截断 10 条），
 * 避免历史版本或被手工改坏的数据让页面渲染出空条目。
 */

export const SEARCH_HISTORY_KEY = 'search_history'
export const SEARCH_HISTORY_LIMIT = 10

function normalize(list: unknown): string[] {
  if (!Array.isArray(list)) {
    return []
  }
  const result: string[] = []
  list.forEach((item) => {
    if (typeof item !== 'string') {
      return
    }
    const keyword = item.trim()
    if (!keyword || result.includes(keyword) || result.length >= SEARCH_HISTORY_LIMIT) {
      return
    }
    result.push(keyword)
  })
  return result
}

export function readSearchHistory(): string[] {
  try {
    return normalize(uni.getStorageSync(SEARCH_HISTORY_KEY))
  } catch (error) {
    return []
  }
}

function writeSearchHistory(list: string[]): string[] {
  const cleaned = normalize(list)
  uni.setStorageSync(SEARCH_HISTORY_KEY, cleaned)
  return cleaned
}

/** 新词置顶；已存在则先移除再插回头部，保证最近搜索在最上面。 */
export function addSearchHistory(keyword: string): string[] {
  const value = (keyword || '').trim()
  if (!value) {
    return readSearchHistory()
  }
  const rest = readSearchHistory().filter((item) => item !== value)
  return writeSearchHistory([value, ...rest])
}

export function removeSearchHistory(keyword: string): string[] {
  return writeSearchHistory(readSearchHistory().filter((item) => item !== keyword))
}

export function clearSearchHistory(): string[] {
  uni.removeStorageSync(SEARCH_HISTORY_KEY)
  return []
}
