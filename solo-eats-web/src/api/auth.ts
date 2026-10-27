import { request } from './http'
import type { LoginPayload, LoginResponse, MenuNode } from './types'

/** POST /api/v1/auth/login（仅 local profile 生效） */
export function login(payload: LoginPayload): Promise<LoginResponse> {
  return request<LoginResponse>({
    url: '/v1/auth/login',
    method: 'POST',
    data: payload,
  })
}

/** GET /api/v1/auth/menus，按 JWT 中的账号返回可访问菜单树 */
export function getMenus(): Promise<MenuNode[]> {
  return request<MenuNode[]>({
    url: '/v1/auth/menus',
    method: 'GET',
  })
}
