export const AUTH_STORAGE_KEY = 'solo-eats.auth'

export interface PersistedAuth {
  token: string
  tokenType: string
  expiresAt: number
  roles: string[]
  userId?: number | null
  merchantId?: number | null
  shopId?: number | null
}

export function loadAuth(): PersistedAuth | null {
  try {
    const raw = localStorage.getItem(AUTH_STORAGE_KEY)
    if (!raw) {
      return null
    }
    const parsed: unknown = JSON.parse(raw)
    if (!isPersistedAuth(parsed)) {
      localStorage.removeItem(AUTH_STORAGE_KEY)
      return null
    }
    return parsed
  } catch {
    localStorage.removeItem(AUTH_STORAGE_KEY)
    return null
  }
}

export function saveAuth(auth: PersistedAuth): void {
  localStorage.setItem(AUTH_STORAGE_KEY, JSON.stringify(auth))
}

export function clearAuth(): void {
  localStorage.removeItem(AUTH_STORAGE_KEY)
}

export function isAuthExpired(auth: PersistedAuth, skewMs = 30_000): boolean {
  return !Number.isFinite(auth.expiresAt) || auth.expiresAt <= Date.now() + skewMs
}

function isPersistedAuth(value: unknown): value is PersistedAuth {
  if (typeof value !== 'object' || value === null) {
    return false
  }
  const candidate = value as Record<string, unknown>
  return (
    typeof candidate.token === 'string' &&
    candidate.token.length > 0 &&
    typeof candidate.tokenType === 'string' &&
    typeof candidate.expiresAt === 'number' &&
    Array.isArray(candidate.roles) &&
    candidate.roles.every((role) => typeof role === 'string')
  )
}
