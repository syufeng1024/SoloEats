import axios, { AxiosError, type AxiosRequestConfig } from 'axios'
import { ElMessage } from 'element-plus'
import { clearAuth, loadAuth } from '../utils/authStorage'
import { matchesRole } from '../constants/roles'
import type { ApiResponse } from './types'

/**
 * 商家中心当前门店的本地存储键。商家中心写入门店选择后，
 * 请求拦截器按该值注入 X-Shop-Id；运营后台不写该键，不会带上此头。
 */
export const MERCHANT_SHOP_STORAGE_KEY = 'solo-eats.merchant.shopId'

function readCurrentShopId(): number | null {
  try {
    const raw = localStorage.getItem(MERCHANT_SHOP_STORAGE_KEY)
    if (!raw || !/^[1-9]\d*$/.test(raw)) {
      return null
    }
    const parsed = Number(raw)
    return Number.isSafeInteger(parsed) ? parsed : null
  } catch {
    return null
  }
}

export class ApiError extends Error {
  readonly code: number
  readonly httpStatus?: number
  readonly traceId?: string

  constructor(code: number, message: string, httpStatus?: number, traceId?: string) {
    super(message)
    this.name = 'ApiError'
    this.code = code
    this.httpStatus = httpStatus
    this.traceId = traceId
  }
}

const http = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api',
  timeout: 15_000,
})

http.interceptors.request.use((config) => {
  const auth = loadAuth()
  if (auth?.token) {
    config.headers.Authorization = `Bearer ${auth.token}`
    // 多门店切换：仅商家角色注入，避免运营后台误带历史门店 ID
    if (auth.roles.some((role) => matchesRole(role, 'MERCHANT'))) {
      const shopId = readCurrentShopId()
      if (shopId !== null) {
        config.headers['X-Shop-Id'] = String(shopId)
      }
    }
  }
  return config
})

function isApiResponse(value: unknown): value is ApiResponse<unknown> {
  if (typeof value !== 'object' || value === null) {
    return false
  }
  const candidate = value as Record<string, unknown>
  return typeof candidate.code === 'number' && typeof candidate.message === 'string'
}

http.interceptors.response.use(
  (response) => {
    const body: unknown = response.data
    // ApiResponse.code = 0 表示成功；非 0 即使 HTTP 200 也按业务失败处理
    if (isApiResponse(body) && body.code !== 0) {
      const error = new ApiError(
        body.code,
        body.message || '业务处理失败',
        response.status,
        body.traceId,
      )
      ElMessage.error(error.message)
      return Promise.reject(error)
    }
    return response
  },
  (error: AxiosError) => {
    const status = error.response?.status
    const body: unknown = error.response?.data

    let apiError: ApiError
    if (isApiResponse(body)) {
      apiError = new ApiError(body.code, body.message || '请求失败', status, body.traceId)
    } else if (status !== undefined) {
      apiError = new ApiError(status, `请求失败（HTTP ${status}）`, status)
    } else if (error.code === 'ECONNABORTED') {
      apiError = new ApiError(-1, '请求超时（15 秒），请检查后端服务与网络')
    } else {
      apiError = new ApiError(-1, '无法连接后端服务，请确认 http://localhost:8080 已启动')
    }

    if (status === 401 || apiError.code === 10003) {
      clearAuth()
      ElMessage.error('登录状态已失效，请重新登录')
      const loginPath = `${import.meta.env.BASE_URL}login`
      if (!window.location.pathname.startsWith(loginPath)) {
        const redirect = encodeURIComponent(window.location.pathname + window.location.search)
        window.location.assign(`${loginPath}?redirect=${redirect}`)
      }
      return Promise.reject(apiError)
    }

    ElMessage.error(apiError.message)
    return Promise.reject(apiError)
  },
)

/** 统一解开 { code, message, data, traceId } 信封，调用方直接拿 data。 */
export async function request<T>(config: AxiosRequestConfig): Promise<T> {
  const response = await http.request<ApiResponse<T>>(config)
  return response.data.data
}

export { http }
