import { request } from './http'
import type { LoginPayload, LoginResponse } from './types'

/** POST /api/v1/auth/login（仅 local profile 生效） */
export function login(payload: LoginPayload): Promise<LoginResponse> {
  return request<LoginResponse>({
    url: '/v1/auth/login',
    method: 'POST',
    data: payload,
  })
}
