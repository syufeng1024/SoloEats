import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { login as loginApi } from '@/api/auth'
import type { LoginPayload } from '@/api/types'
import {
  clearAuth,
  isAuthExpired,
  loadAuth,
  saveAuth,
  type PersistedAuth,
} from '@/utils/authStorage'

export const useAuthStore = defineStore('auth', () => {
  const session = ref<PersistedAuth | null>(loadAuth())

  const isAuthenticated = computed(
    () => session.value !== null && !isAuthExpired(session.value),
  )
  const roles = computed(() => session.value?.roles ?? [])
  const primaryRole = computed(() => roles.value[0] ?? '')
  const merchantId = computed(() => session.value?.merchantId ?? null)
  const shopId = computed(() => session.value?.shopId ?? null)

  async function login(payload: LoginPayload): Promise<PersistedAuth> {
    const response = await loginApi(payload)
    const persisted: PersistedAuth = {
      token: response.token,
      tokenType: response.tokenType,
      expiresAt: Date.now() + response.expiresIn * 1000,
      roles: response.roles,
      userId: payload.userId ?? null,
      merchantId: payload.merchantId ?? null,
      shopId: payload.shopId ?? null,
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
    return roles.value.includes(role)
  }

  return {
    session,
    isAuthenticated,
    roles,
    primaryRole,
    merchantId,
    shopId,
    login,
    logout,
    hasRole,
  }
})
