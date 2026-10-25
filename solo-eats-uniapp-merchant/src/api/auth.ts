import { request } from './http'
import type { LoginVO } from './types'

/** POST /auth/login，账号密码登录（联调账号 merchant001 / Merchant@123） */
export function loginByPassword(username: string, password: string): Promise<LoginVO> {
  return request<LoginVO>({
    url: '/v1/auth/login',
    method: 'POST',
    data: { username: username.trim(), password },
  })
}
