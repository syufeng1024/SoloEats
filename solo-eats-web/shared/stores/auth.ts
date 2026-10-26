import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { login as loginApi } from '../api/auth'
import type { LoginPayload } from '../api/types'
import { matchesRole } from '../constants/roles'
import {
  clearAuth,
  isAuthExpired,
  loadAuth,
  saveAuth,
  type PersistedAuth,
} from '../utils/authStorage'
import { decodeJwtClaims } from '../utils/jwt'

export const useAuthStore = defineStore('auth', () => {
  const session = ref<PersistedAuth | null>(loadAuth())

  const isAuthenticated = computed(
    () => session.value !== null && !isAuthExpired(session.value),
  )
  const roles = computed(() => session.value?.roles ?? [])
  const permissions = computed(() => session.value?.permissions ?? [])
  const primaryRole = computed(() => roles.value[0] ?? '')
  const userId = computed(() => session.value?.userId ?? null)
  const merchantId = computed(() => session.value?.merchantId ?? null)
  const shopId = computed(() => session.value?.shopId ?? null)

  async function login(payload: LoginPayload): Promise<PersistedAuth> {
    const response = await loginApi(payload)
    // 账号密码登录的响应不含 merchantId/shopId，只能从 JWT 声明中读取
    const claims = decodeJwtClaims(response.token)
    const persisted: PersistedAuth = {
      token: response.token,
      tokenType: response.tokenType,
      expiresAt: Date.now() + response.expiresIn * 1000,
      roles: response.roles.length > 0 ? response.roles : (claims?.roles ?? []),
      permissions: response.permissions ?? [],
      userId: payload.userId ?? claims?.userId ?? null,
      merchantId: payload.merchantId ?? claims?.merchantId ?? null,
      shopId: payload.shopId ?? claims?.shopId ?? null,
    }
    saveAuth(persisted)
    session.value = persisted
    return persisted
  }

  function logout(): void {
    clearAuth()
    session.value = null
  }

  function hasRole(role: string): boolean {
    return roles.value.some((item) => matchesRole(item, role))
  }

  function hasPermission(code: string): boolean {
    return permissions.value.includes('*') || permissions.value.includes(code)
  }

  return {
    session,
    isAuthenticated,
    roles,
    permissions,
    primaryRole,
    userId,
    merchantId,
    shopId,
    login,
    logout,
    hasRole,
    hasPermission,
  }
})
