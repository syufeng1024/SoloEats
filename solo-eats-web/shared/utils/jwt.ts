/**
 * 从 JWT 载荷读取业务身份（不校验签名，签名由后端校验）。
 * 登录响应只返回 roles/permissions，merchantId/shopId 只存在于 token 声明中。
 */
export interface JwtClaims {
  userId: number | null
  merchantId: number | null
  shopId: number | null
  riderId: number | null
  tenantId: number | null
  username: string | null
  roles: string[]
}

function toId(value: unknown): number | null {
  if (typeof value === 'number' && Number.isSafeInteger(value) && value > 0) {
    return value
  }
  if (typeof value === 'string' && /^[1-9]\d*$/.test(value)) {
    const parsed = Number(value)
    return Number.isSafeInteger(parsed) ? parsed : null
  }
  return null
}

function decodeBase64Url(segment: string): string {
  const normalized = segment.replace(/-/g, '+').replace(/_/g, '/')
  const padded = normalized.padEnd(Math.ceil(normalized.length / 4) * 4, '=')
  const binary = atob(padded)
  const bytes = Uint8Array.from(binary, (char) => char.charCodeAt(0))
  return new TextDecoder('utf-8').decode(bytes)
}

export function decodeJwtClaims(token: string): JwtClaims | null {
  try {
    const segments = token.split('.')
    if (segments.length < 2) {
      return null
    }
    const parsed: unknown = JSON.parse(decodeBase64Url(segments[1]))
    if (typeof parsed !== 'object' || parsed === null) {
      return null
    }
    const payload = parsed as Record<string, unknown>
    const roles = Array.isArray(payload.roles)
      ? payload.roles.filter((role): role is string => typeof role === 'string')
      : []
    return {
      userId: toId(payload.userId),
      merchantId: toId(payload.merchantId),
      shopId: toId(payload.shopId),
      riderId: toId(payload.riderId),
      tenantId: toId(payload.tenantId),
      username: typeof payload.username === 'string' ? payload.username : null,
      roles,
    }
  } catch {
    return null
  }
}
