import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { login as loginApi } from '@/api/auth'
import type { LoginPayload } from '@/api/types'
import { matchesRole } from '@/constants/roles'
import { useMenuStore } from './menu'
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
    // 菜单按账号拉取，退出时必须清空，避免下一个账号复用上一个账号的菜单
    useMenuStore().reset()
  }

  function hasRole(role: string): boolean {
    return roles.value.some((item) => matchesRole(item, role))
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
