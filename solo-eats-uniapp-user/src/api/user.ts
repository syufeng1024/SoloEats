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

/** 对齐后端 SaveAddressRequest：receiverName/receiverPhone/address 必填。 */
export interface SaveAddressPayload {
  receiverName: string
  receiverPhone: string
  province?: string | null
  city?: string | null
  district?: string | null
  address: string
  houseNumber?: string | null
  lng?: number | null
  lat?: number | null
  tag?: string | null
  isDefault?: boolean | null
}

/** POST /c/addresses，首条地址后端自动设为默认。 */
export function createAddress(payload: SaveAddressPayload): Promise<AddressVO> {
  return request<AddressVO>({
    url: '/v1/c/addresses',
    method: 'POST',
    data: payload,
  })
}

/** PUT /c/addresses/{id}，归属不符后端返回 10004。 */
export function updateAddress(id: number, payload: SaveAddressPayload): Promise<AddressVO> {
  return request<AddressVO>({
    url: `/v1/c/addresses/${id}`,
    method: 'PUT',
    data: payload,
  })
}

/** DELETE /c/addresses/{id} */
export function deleteAddress(id: number): Promise<void> {
  return request<void>({ url: `/v1/c/addresses/${id}`, method: 'DELETE' })
}

/** PUT /c/addresses/{id}/default */
export function setDefaultAddress(id: number): Promise<void> {
  return request<void>({ url: `/v1/c/addresses/${id}/default`, method: 'PUT' })
}

/** GET /c/wallet。silent 用于「我的」聚合加载，避免多个请求同时失败时连弹 toast。 */
export function fetchWallet(silent = false): Promise<WalletVO> {
  return request<WalletVO>({ url: '/v1/c/wallet', silent })
}

/**
 * POST /c/wallet/recharge。amount 单位为分，后端限制 1 ~ 100000（即 0.01 ~ 1000.00 元），
 * 越界返回 80007；同一 Idempotency-Key 重复提交返回当前钱包，不会重复入账。
 * 返回充值后的 WalletView。
 */
export function rechargeWallet(amountFen: number, idempotencyKey?: string): Promise<WalletVO> {
  return request<WalletVO>({
    url: '/v1/c/wallet/recharge',
    method: 'POST',
    data: { amount: amountFen },
    idempotencyKey,
  })
}
