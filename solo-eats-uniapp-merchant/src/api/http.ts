import { generateIdempotencyKey, isWriteMethod, withRetry } from '@/utils/request'
import type { ApiResponse } from './types'

/**
 * 统一请求封装。
 * - baseURL /api：dev 下由 vite.config.js 代理到 http://localhost:8080
 * - 自动注入 Authorization: Bearer <token>
 * - 统一解包 { code, message, data, traceId }，成功直接返回 data
 * - 写接口自动带 Idempotency-Key（后端 @RequestHeader("Idempotency-Key") 只认这个头名）
 * - 10003（未登录/令牌无效）清本地登录态并 reLaunch 登录页
 */

const BASE_URL = '/api'
const TIMEOUT_MS = 10_000
const UNAUTHORIZED_CODE = 10003
const LOGIN_PAGE = '/pages/login/Login'
const SHOP_ID_KEY = 'shopId'
const AUTH_STORAGE_KEYS = [
  'token',
  'tokenExpireAt',
  'userId',
  'nickname',
  'merchantId',
  'tokenShopId',
  SHOP_ID_KEY,
]

/** 401 并发时避免多个请求各弹一次 toast、各跳一次登录页 */
let lastUnauthorizedAt = 0

export class ApiError extends Error {
  readonly code: number
  readonly httpStatus?: number
  readonly traceId?: string
  /** 网络错误/超时标记为可重试；业务错误一律不可重试 */
  readonly retryable: boolean

  constructor(
    code: number,
    message: string,
    options: { httpStatus?: number; traceId?: string; retryable?: boolean } = {},
  ) {
    super(message)
    this.name = 'ApiError'
    this.code = code
    this.httpStatus = options.httpStatus
    this.traceId = options.traceId
    this.retryable = options.retryable === true
  }
}

export type HttpMethod = 'GET' | 'POST' | 'PUT' | 'DELETE'

export interface RequestOptions {
  /** 以 /v1 开头的路径，例如 /v1/c/shops */
  url: string
  method?: HttpMethod
  data?: Record<string, unknown>
  header?: Record<string, string>
  /** 页面落本地复用的幂等键；不传则每次生成（写接口） */
  idempotencyKey?: string
  /** 不做 toast 提示，由页面自行渲染错误态 */
  silent?: boolean
}

export function getToken(): string {
  const token = uni.getStorageSync('token')
  return typeof token === 'string' ? token : ''
}

function isApiResponse<T>(value: unknown): value is ApiResponse<T> {
  if (!value || typeof value !== 'object') {
    return false
  }
  const candidate = value as Record<string, unknown>
  return typeof candidate.code === 'number' && typeof candidate.message === 'string'
}

function toast(message: string): void {
  uni.showToast({ title: message, icon: 'none', duration: 2200 })
}

function currentQueryString(): string {
  const pages = getCurrentPages()
  const current = pages[pages.length - 1] as unknown as {
    route?: string
    options?: Record<string, string>
  }
  const options = current?.options ?? {}
  const entries = Object.keys(options)
    .filter((key) => options[key] !== undefined && options[key] !== '')
    .map((key) => `${encodeURIComponent(key)}=${encodeURIComponent(String(options[key]))}`)
  return entries.length > 0 ? `?${entries.join('&')}` : ''
}

function handleUnauthorized(error: ApiError): void {
  AUTH_STORAGE_KEYS.forEach((key) => uni.removeStorageSync(key))
  // App.vue 监听该事件清空 Pinia（本项目为 reactive）中的登录态，避免页面继续显示旧昵称
  uni.$emit('auth:expired')

  const now = Date.now()
  if (now - lastUnauthorizedAt < 1000) {
    return
  }
  lastUnauthorizedAt = now

  toast(error.message || '登录状态已失效，请重新登录')

  const pages = getCurrentPages()
  const current = pages[pages.length - 1] as unknown as { route?: string } | undefined
  const route = current?.route ? `/${current.route}` : ''
  if (route === LOGIN_PAGE) {
    return
  }
  const redirect = route ? encodeURIComponent(`${route}${currentQueryString()}`) : ''
  uni.reLaunch({ url: redirect ? `${LOGIN_PAGE}?redirect=${redirect}` : LOGIN_PAGE })
}

function sendOnce<T>(options: RequestOptions, method: HttpMethod): Promise<T> {
  return new Promise<T>((resolve, reject) => {
    const header: Record<string, string> = {
      'Content-Type': 'application/json',
      ...(options.header || {}),
    }
    const token = getToken()
    if (token) {
      header.Authorization = `Bearer ${token}`
    }
    // 多门店切换：当前门店随请求头下发。后端当前按令牌内 shopId 校验，
    // 该头为 T15 约定的向前兼容字段（未实现的接口忽略它）。
    const shopId = uni.getStorageSync(SHOP_ID_KEY)
    if (shopId !== undefined && shopId !== null && String(shopId) !== '') {
      header['X-Shop-Id'] = String(shopId)
    }
    if (isWriteMethod(method)) {
      header['Idempotency-Key'] = options.idempotencyKey || generateIdempotencyKey()
    }

    uni.request({
      url: `${BASE_URL}${options.url}`,
      method,
      data: options.data,
      header,
      timeout: TIMEOUT_MS,
      success: (response) => {
        const status = response.statusCode
        const body = response.data as unknown

        if (!isApiResponse<T>(body)) {
          const message = status === 404 ? '接口不存在，请确认后端版本' : `服务异常（HTTP ${status}）`
          reject(new ApiError(status, message, { httpStatus: status }))
          return
        }

        if (body.code === 0) {
          resolve(body.data)
          return
        }

        const error = new ApiError(body.code, body.message || '业务处理失败', {
          httpStatus: status,
          traceId: body.traceId,
        })
        if (body.code === UNAUTHORIZED_CODE) {
          handleUnauthorized(error)
          reject(error)
          return
        }
        reject(error)
      },
      fail: (response) => {
        const errMsg = String((response as { errMsg?: string })?.errMsg || '')
        const timeout = /timeout|超时/i.test(errMsg)
        reject(
          new ApiError(
            -1,
            timeout ? '请求超时（10 秒），请检查网络后重试' : '无法连接服务器，请确认后端已启动',
            { retryable: true },
          ),
        )
      },
    })
  })
}

/**
 * 读接口：超时/断网自动重试 1 次。
 * 写接口：绝不重试（下单/支付/取消重复执行会造成重复业务），幂等由 Idempotency-Key 保证。
 */
export async function request<T>(options: RequestOptions): Promise<T> {
  const method = options.method ?? 'GET'
  const send = () => sendOnce<T>(options, method)
  try {
    return isWriteMethod(method) ? await send() : await withRetry(send, 1)
  } catch (error) {
    // 重试全部失败后才提示，避免「第一次超时弹错误、第二次成功」的误导
    if (!options.silent && error instanceof ApiError && error.code !== UNAUTHORIZED_CODE) {
      toast(error.message)
    }
    throw error
  }
}
