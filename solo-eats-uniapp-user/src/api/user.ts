import { request } from './http'
import type { AddressVO, MemberVO, SmsLoginVO, WalletVO } from './types'

/** 验证码场景：1 登录 2 入驻 3 改绑（后端 SmsScene） */
export const SMS_SCENE_LOGIN = 1

/** POST /c/auth/sms-code */
export function sendSmsCode(phone: string, scene = SMS_SCENE_LOGIN): Promise<void> {
  return request<void>({
    url: '/v1/c/auth/sms-code',
    method: 'POST',
    data: { phone, scene },
  })
}

/** POST /c/auth/login，用户不存在时后端自动注册 */
export function smsLogin(phone: string, code: string): Promise<SmsLoginVO> {
  return request<SmsLoginVO>({
    url: '/v1/c/auth/login',
    method: 'POST',
    data: { phone, code },
  })
}

/** GET /c/users/me，phone 为脱敏值 */
export function fetchMyProfile(): Promise<MemberVO> {
  return request<MemberVO>({ url: '/v1/c/users/me' })
}

/** GET /c/addresses，receiverPhone 为脱敏值，下单时不能直接复用 */
export function fetchAddresses(): Promise<AddressVO[]> {
  return request<AddressVO[]>({ url: '/v1/c/addresses' })
}

/** GET /c/wallet */
export function fetchWallet(): Promise<WalletVO> {
  return request<WalletVO>({ url: '/v1/c/wallet' })
}
