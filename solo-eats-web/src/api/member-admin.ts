import { request } from './http'
import { createIdempotencyKey } from '@/utils/idempotency'
import type { PageResult } from './types'

/**
 * 会员中心运营后台接口。
 * 字段与后端 view/DTO 一一对应：
 * - MemberView.java / MemberDetailView.java（手机号一律是掩码）
 * - AdminAddressView.java
 * - WalletLogView.java / WalletView.java
 * - TagView.java
 * - AdminMemberController.java / AdminMemberTagController.java
 * 金额字段单位为分（后端 long、bigint）。
 */

/** MemberView.java，列表接口只返回这些字段 */
export interface MemberView {
  userId: number
  /** 后端脱敏手机号，任何端点都不返回明文 */
  phone: string | null
  nickname: string | null
  avatarUrl: string | null
  /** 0 未知 1 男 2 女 */
  gender: number | null
  /** yyyy-MM-dd */
  birthday: string | null
  /** 0 正常 1 禁用 2 拉黑 */
  status: number
  orderCount: number | null
  /** 累计消费，分 */
  totalAmount: number | null
  lastOrderAt: string | null
  cancelApplyAt: string | null
  registeredAt: string | null
}

/** MemberDetailView.java，在 MemberView 基础上补地址数、钱包余额与标签名 */
export interface MemberDetailView extends MemberView {
  addressCount: number
  /** 钱包可用余额，分 */
  walletBalance: number
  tagNames: string[]
}

/** AdminAddressView.java，receiverPhone 仍是掩码 */
export interface AdminAddressView {
  id: number
  userId: number
  receiverName: string
  receiverPhone: string | null
  province: string | null
  city: string | null
  district: string | null
  address: string
  houseNumber: string | null
  lng: number | null
  lat: number | null
  tag: string | null
  isDefault: boolean
  createdAt: string | null
  updatedAt: string | null
}

/** WalletLogView.java，amount / beforeBalance / afterBalance 单位分，amount 恒为正 */
export interface WalletLogView {
  id: number
  /** RECHARGE / CONSUME / REFUND / ADJUST / WITHDRAW / FREEZE / UNFREEZE */
  bizType: string
  bizId: number | null
  /** 1 收入 2 支出 */
  direction: number
  /** 变动金额，分，恒为正 */
  amount: number
  beforeBalance: number
  afterBalance: number
  remark: string | null
  operatorName: string | null
  createdAt: string
}

/** WalletView.java，金额单位分 */
export interface WalletView {
  balance: number
  frozenAmount: number
  totalRecharge: number
  totalConsume: number
}

/** TagView.java，autoFlag 0 手动 1 自动，status 0 启用 1 停用 */
export interface TagView {
  id: number
  name: string
  ruleDesc: string | null
  autoFlag: number
  status: number
  memberCount: number
}

export interface MemberListParams {
  /** 0 正常 1 禁用 2 拉黑 */
  status?: number
  /** 只取数字的后四位，后端按 phone_mask 匹配 */
  keyword?: string
  levelId?: number
  pageNum?: number
  pageSize?: number
}

export interface WalletLogListParams {
  bizType?: string
  /** yyyy-MM-dd */
  dateFrom?: string
  /** yyyy-MM-dd */
  dateTo?: string
  pageNum?: number
  pageSize?: number
}

/** AdjustWalletRequest.java：amount 有符号，单位分；requestId 参与幂等 */
export interface AdjustWalletPayload {
  amount: number
  reason: string
  requestId: string
}

/** SaveTagRequest.java */
export interface SaveTagPayload {
  name: string
  ruleDesc?: string | null
  autoFlag: number
  status: number
}

/** GET /api/v1/admin/members，权限码 member:user:list */
export function listMembers(
  params: MemberListParams,
): Promise<PageResult<MemberView>> {
  return request<PageResult<MemberView>>({
    url: '/v1/admin/members',
    method: 'GET',
    params,
  })
}

/** GET /api/v1/admin/members/{id}，权限码 member:user:detail */
export function getMemberDetail(id: number): Promise<MemberDetailView> {
  return request<MemberDetailView>({
    url: `/v1/admin/members/${id}`,
    method: 'GET',
  })
}

/** PUT /api/v1/admin/members/{id}/status，权限码 member:user:update */
export function updateMemberStatus(id: number, status: number): Promise<void> {
  return request<void>({
    url: `/v1/admin/members/${id}/status`,
    method: 'PUT',
    data: { status },
  })
}

/** GET /api/v1/admin/members/{id}/addresses，权限码 member:user:detail */
export function listMemberAddresses(id: number): Promise<AdminAddressView[]> {
  return request<AdminAddressView[]>({
    url: `/v1/admin/members/${id}/addresses`,
    method: 'GET',
  })
}

/** POST /api/v1/admin/members/{id}/tags，全量覆盖；空数组表示清空 */
export function assignMemberTags(id: number, tagIds: number[]): Promise<void> {
  return request<void>({
    url: `/v1/admin/members/${id}/tags`,
    method: 'POST',
    data: { tagIds },
    headers: { 'Idempotency-Key': createIdempotencyKey(`member-tags-${id}`) },
  })
}

/** GET /api/v1/admin/members/{id}/wallet/logs，权限码 member:wallet:list */
export function listMemberWalletLogs(
  id: number,
  params: WalletLogListParams,
): Promise<PageResult<WalletLogView>> {
  return request<PageResult<WalletLogView>>({
    url: `/v1/admin/members/${id}/wallet/logs`,
    method: 'GET',
    params,
  })
}

/** POST /api/v1/admin/members/{id}/wallet/adjust，权限码 member:wallet:adjust */
export function adjustMemberWallet(
  id: number,
  payload: AdjustWalletPayload,
): Promise<WalletView> {
  return request<WalletView>({
    url: `/v1/admin/members/${id}/wallet/adjust`,
    method: 'POST',
    data: payload,
    headers: { 'Idempotency-Key': createIdempotencyKey(`member-adjust-${id}`) },
  })
}

/** GET /api/v1/admin/member-tags，权限码 member:tag:list。后端不分页，按 id 升序返回 */
export function listMemberTags(): Promise<TagView[]> {
  return request<TagView[]>({
    url: '/v1/admin/member-tags',
    method: 'GET',
  })
}

/** POST /api/v1/admin/member-tags，权限码 member:tag:create */
export function createMemberTag(payload: SaveTagPayload): Promise<TagView> {
  return request<TagView>({
    url: '/v1/admin/member-tags',
    method: 'POST',
    data: payload,
    headers: { 'Idempotency-Key': createIdempotencyKey('member-tag-create') },
  })
}

/** PUT /api/v1/admin/member-tags/{id}，权限码 member:tag:update。后端无 DELETE */
export function updateMemberTag(id: number, payload: SaveTagPayload): Promise<TagView> {
  return request<TagView>({
    url: `/v1/admin/member-tags/${id}`,
    method: 'PUT',
    data: payload,
  })
}
