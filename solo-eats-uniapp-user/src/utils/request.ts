/**
 * 传输层轻量工具：重试、幂等键、方法判定。
 * 这里不直接发请求，避免与 api/http.ts 形成循环依赖。
 */

export interface RetryableError {
  /** 由 http 层打在网络错误/超时错误上的标记，业务错误不带该标记 */
  retryable?: boolean
}

const IDEMPOTENCY_STORAGE_PREFIX = 'idem:'

let keySequence = 0

/** 时间戳 + 自增序号 + 随机串，满足「时间戳+随机数」且同毫秒内不重复。 */
export function generateIdempotencyKey(): string {
  keySequence = (keySequence + 1) % 1000
  return `${Date.now().toString(36)}${keySequence.toString(36)}${Math.random().toString(36).slice(2, 10)}`
}

/**
 * 取（或创建）某个业务场景的幂等键，并落到本地存储。
 * 失败重试复用同一个 key，后端 @Idempotent 才能识别为同一次请求；
 * 业务成功后调用 clearIdempotencyKey 清掉，下次下单生成新 key。
 */
export function issueIdempotencyKey(scope: string): string {
  const storageKey = `${IDEMPOTENCY_STORAGE_PREFIX}${scope}`
  const cached = uni.getStorageSync(storageKey)
  if (typeof cached === 'string' && cached.length > 0) {
    return cached
  }
  const key = generateIdempotencyKey()
  uni.setStorageSync(storageKey, key)
  return key
}

export function clearIdempotencyKey(scope: string): void {
  uni.removeStorageSync(`${IDEMPOTENCY_STORAGE_PREFIX}${scope}`)
}

export function isWriteMethod(method: string): boolean {
  return ['POST', 'PUT', 'PATCH', 'DELETE'].includes(String(method || '').toUpperCase())
}

export function isRetryableError(error: unknown): boolean {
  return Boolean(error && typeof error === 'object' && (error as RetryableError).retryable)
}

export function sleep(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms))
}

/**
 * 只对网络错误/超时重试，默认 1 次。
 * 写接口不得走这里（重复写会产生重复订单/支付），由 http.request 负责分流。
 */
export async function withRetry<T>(
  task: () => Promise<T>,
  retries = 1,
  delayMs = 300,
): Promise<T> {
  let attempt = 0
  for (;;) {
    try {
      return await task()
    } catch (error) {
      if (attempt >= retries || !isRetryableError(error)) {
        throw error
      }
      attempt += 1
      await sleep(delayMs)
    }
  }
}
