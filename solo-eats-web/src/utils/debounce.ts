/**
 * 前端异步检索统一 300ms 防抖，避免输入过程中重复触发请求或重算。
 */
export function debounce<A extends unknown[]>(
  fn: (...args: A) => void,
  wait = 300,
): (...args: A) => void {
  let timer: ReturnType<typeof setTimeout> | undefined

  return (...args: A) => {
    if (timer !== undefined) {
      clearTimeout(timer)
    }
    timer = setTimeout(() => {
      timer = undefined
      fn(...args)
    }, wait)
  }
}
