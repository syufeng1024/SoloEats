import { reactive } from 'vue'
import { loginByPassword } from '@/api/rider'
import type { LoginVO } from '@/api/types'

/**
 * 骑手登录态。
 *
 * 工程未安装 pinia（不得改 package.json），与 T13 用户端一致，用 reactive 单例。
 * JWT 里带 userId / username 两个声明，登录响应本身不含用户信息，这里解 payload 取值；
 * riderId 不取 JWT（密码登录时 t_sys_user.rider_id 可能为空），由 rider store 调
 * GET /rider/certification 拿服务端权威值。
 */

const TOKEN_KEY = 'token'
const TOKEN_EXPIRE_KEY = 'tokenExpireAt'
const USER_ID_KEY = 'userId'
const NICKNAME_KEY = 'nickname'

interface JwtPayload {
  userId?: number
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

/** base64url 解 JWT payload；解不出不抛错，页面退化为只显示账号名 */
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

export interface AuthStore {
  token: string
  tokenExpireAt: number
  userId: number | null
  nickname: string
  readonly isLogin: boolean
  login(username: string, password: string): Promise<LoginVO>
  applyLogin(result: LoginVO): void
  logout(): void
  clearLocal(): void
  /** 未登录时跳登录页并带回收站地址，返回 false */
  ensureLogin(redirect?: string): boolean
}

const state = reactive<AuthStore>({
  token: readString(TOKEN_KEY),
  tokenExpireAt: Number(uni.getStorageSync(TOKEN_EXPIRE_KEY)) || 0,
  userId: readNumber(USER_ID_KEY),
  nickname: readString(NICKNAME_KEY),

  get isLogin(): boolean {
    if (!this.token) {
      return false
    }
    // tokenExpireAt=0 为历史数据，交给后端 10003 兜底
    return !this.tokenExpireAt || Date.now() < this.tokenExpireAt
  },

  async login(username: string, password: string): Promise<LoginVO> {
    const result = await loginByPassword(username.trim(), password)
    this.applyLogin(result)
    return result
  },

  applyLogin(result: LoginVO): void {
    const payload = decodeJwtPayload(result.token)
    this.token = result.token
    this.tokenExpireAt = result.expiresIn > 0 ? Date.now() + result.expiresIn * 1000 : 0
    this.userId = typeof payload.userId === 'number' ? payload.userId : null
    this.nickname = payload.username || this.nickname || '骑手'
    uni.setStorageSync(TOKEN_KEY, this.token)
    uni.setStorageSync(TOKEN_EXPIRE_KEY, this.tokenExpireAt)
    uni.setStorageSync(USER_ID_KEY, this.userId ?? '')
    uni.setStorageSync(NICKNAME_KEY, this.nickname)
  },

  logout(): void {
    this.clearLocal()
  },

  clearLocal(): void {
    this.token = ''
    this.tokenExpireAt = 0
    this.userId = null
    this.nickname = ''
    uni.removeStorageSync(TOKEN_KEY)
    uni.removeStorageSync(TOKEN_EXPIRE_KEY)
    uni.removeStorageSync(USER_ID_KEY)
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
