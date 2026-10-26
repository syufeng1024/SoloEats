/**
 * 后端对取消/拒单接口要求 Idempotency-Key 请求头。
 * 这里按业务前缀 + 随机 UUID 生成，保证同一次用户操作只提交一次。
 */
export function createIdempotencyKey(prefix: string): string {
  const random =
    typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function'
      ? crypto.randomUUID()
      : `${Date.now()}-${Math.random().toString(16).slice(2)}`
  return `${prefix}-${random}`
}
