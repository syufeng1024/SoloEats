import { reactive } from 'vue'
import { smsLogin } from '@/api/user'
import type { SmsLoginVO } from '@/api/types'

/**
 * 登录态。
 *
 * 说明：工程 package.json 未安装 pinia（T13 卡允许的状态容器，但本次禁止改依赖），
 * 这里用 Vue reactive 单例实现同等能力的 store：页面 `const auth = useAuthStore()`
 * 后直接读写 `auth.token` / `auth.isLogin`，与 pinia 的用法一致，且不引入新依赖。
 */

const TOKEN_KEY = 'token'
const TOKEN_EXPIRE_KEY = 'tokenExpireAt'
const USER_ID_KEY = 'userId'
const NICKNAME_KEY = 'nickname'

export interface AuthStore {
  token: string
  tokenExpireAt: number
  userId: number | null
  nickname: string
  readonly isLogin: boolean
  login(phone: string, code: string): Promise<SmsLoginVO>
  applyLogin(result: SmsLoginVO): void
  logout(): void
  clearLocal(): void
  /** 未登录时跳登录页并保留回跳地址，返回 false */
  ensureLogin(redirect?: string): boolean
}

function readString(key: string): string {
  const value = uni.getStorageSync(key)
  return typeof value === 'string' ? value : ''
}

function readNumber(key: string): number | null {
  const value = Number(uni.getStorageSync(key))
  return Number.isFinite(value) && value > 0 ? value : null
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
    // tokenExpireAt 为 0 表示历史数据没有过期时间，交由后端 10003 兜底
    return !this.tokenExpireAt || Date.now() < this.tokenExpireAt
  },

  async login(phone: string, code: string): Promise<SmsLoginVO> {
    const result = await smsLogin(phone, code)
    this.applyLogin(result)
    return result
  },

  applyLogin(result: SmsLoginVO): void {
    this.token = result.token
    this.tokenExpireAt = result.expiresIn > 0 ? Date.now() + result.expiresIn * 1000 : 0
    this.userId = result.userId
    this.nickname = result.nickname || ''
    uni.setStorageSync(TOKEN_KEY, this.token)
    uni.setStorageSync(TOKEN_EXPIRE_KEY, this.tokenExpireAt)
    uni.setStorageSync(USER_ID_KEY, this.userId)
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
