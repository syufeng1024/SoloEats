import { reactive } from 'vue'
import { loginByPassword } from '@/api/auth'
import type { LoginVO } from '@/api/types'

/**
 * 商家登录态。
 *
 * 工程没有安装 pinia（禁止改 package.json 依赖），与骑手端一致用 reactive 单例。
 * 登录响应只带 token/roles，userId/merchantId/shopId 从 JWT payload 解析：
 * - merchantId 是 t_merchant.id（订单归属校验用）
 * - shopId 是令牌绑定的门店（t_sys_user.shop_id），多门店切换时另存到 shop store
 * - 角色码是细粒度族码（MERCHANT_MANAGER/MERCHANT_CLERK），判定必须按族匹配
 */

const TOKEN_KEY = 'token'
const TOKEN_EXPIRE_KEY = 'tokenExpireAt'
const USER_ID_KEY = 'userId'
const MERCHANT_ID_KEY = 'merchantId'
const TOKEN_SHOP_ID_KEY = 'tokenShopId'
const NICKNAME_KEY = 'nickname'

interface JwtPayload {
  userId?: number
  merchantId?: number
  shopId?: number
  username?: string
}

function readString(key: string): string {
  const value = uni.getStorageSync(key)
  return typeof value === 'string' ? value : ''
}

function readNumber(key: string): number | null {
  const value = Number(uni.getStorageSync(key))
  return Number.isFinite(value) && value > 0 ? value : null
}

/** base64url 解 JWT payload；解不出不抛错，页面退化为只用 roles 判定 */
function decodeJwtPayload(token: string): JwtPayload {
  try {
    const segment = token.split('.')[1]
    if (!segment) {
      return {}
    }
    const normalized = segment.replace(/-/g, '+').replace(/_/g, '/')
    const padded = normalized + '='.repeat((4 - (normalized.length % 4)) % 4)
    const raw = typeof atob === 'function' ? atob(padded) : ''
    return raw ? (JSON.parse(raw) as JwtPayload) : {}
  } catch (error) {
    return {}
  }
}

/** 角色族匹配：MERCHANT 命中 MERCHANT / MERCHANT_MANAGER / MERCHANT_CLERK */
export function hasRoleFamily(roles: string[] | null | undefined, family: string): boolean {
  return (roles || []).some((role) => role === family || role.startsWith(`${family}_`))
}

export interface AuthStore {
  token: string
  tokenExpireAt: number
  userId: number | null
  merchantId: number | null
  /** 令牌内绑定的门店；多门店切换的当前门店见 stores/shop.ts */
  shopId: number | null
  nickname: string
  roles: string[]
  readonly isLogin: boolean
  login(username: string, password: string): Promise<LoginVO>
  applyLogin(result: LoginVO): void
  logout(): void
  clearLocal(): void
  ensureLogin(redirect?: string): boolean
}

const state = reactive<AuthStore>({
  token: readString(TOKEN_KEY),
  tokenExpireAt: Number(uni.getStorageSync(TOKEN_EXPIRE_KEY)) || 0,
  userId: readNumber(USER_ID_KEY),
  merchantId: readNumber(MERCHANT_ID_KEY),
  shopId: readNumber(TOKEN_SHOP_ID_KEY),
  nickname: readString(NICKNAME_KEY),
  roles: [],

  get isLogin(): boolean {
    if (!this.token) {
      return false
    }
    // tokenExpireAt=0 为历史数据，交给后端 10003 兜底
    return !this.tokenExpireAt || Date.now() < this.tokenExpireAt
  },

  async login(username: string, password: string): Promise<LoginVO> {
    const result = await loginByPassword(username, password)
    this.applyLogin(result)
    return result
  },

  applyLogin(result: LoginVO): void {
    const payload = decodeJwtPayload(result.token)
    this.token = result.token
    this.tokenExpireAt = result.expiresIn > 0 ? Date.now() + result.expiresIn * 1000 : 0
    this.userId = typeof payload.userId === 'number' ? payload.userId : null
    this.merchantId = typeof payload.merchantId === 'number' ? payload.merchantId : null
    this.shopId = typeof payload.shopId === 'number' ? payload.shopId : null
    this.nickname = payload.username || this.nickname || '商家'
    this.roles = Array.isArray(result.roles) ? result.roles : []
    uni.setStorageSync(TOKEN_KEY, this.token)
    uni.setStorageSync(TOKEN_EXPIRE_KEY, this.tokenExpireAt)
    uni.setStorageSync(USER_ID_KEY, this.userId ?? '')
    uni.setStorageSync(MERCHANT_ID_KEY, this.merchantId ?? '')
    uni.setStorageSync(TOKEN_SHOP_ID_KEY, this.shopId ?? '')
    uni.setStorageSync(NICKNAME_KEY, this.nickname)
  },

  logout(): void {
    this.clearLocal()
  },

  clearLocal(): void {
    this.token = ''
    this.tokenExpireAt = 0
    this.userId = null
    this.merchantId = null
    this.shopId = null
    this.nickname = ''
    this.roles = []
    uni.removeStorageSync(TOKEN_KEY)
    uni.removeStorageSync(TOKEN_EXPIRE_KEY)
    uni.removeStorageSync(USER_ID_KEY)
    uni.removeStorageSync(MERCHANT_ID_KEY)
    uni.removeStorageSync(TOKEN_SHOP_ID_KEY)
    uni.removeStorageSync(NICKNAME_KEY)
  },

  ensureLogin(redirect?: string): boolean {
    if (this.isLogin) {
      return true
    }
    const target = redirect ? `?redirect=${encodeURIComponent(redirect)}` : ''
    uni.reLaunch({ url: `/pages/login/Login${target}` })
    return false
  },
})

export function useAuthStore(): AuthStore {
  return state
}
