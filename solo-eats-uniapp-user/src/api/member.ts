import { request } from './http'
import type { CheckinResultVO } from './mine'

/**
 * 会员成长值 / 补签 / 邀请接口。
 *
 * 与 mine.ts 的分工：会员档案、积分、签到状态与今日签到已在 mine.ts；
 * 这里只补 T13-B3 新增页面用到的「成长值明细、补签、邀请码」。
 */

/* ------------------------------------------------------------------ */
/* 成长值明细                                                          */
/* ------------------------------------------------------------------ */

/**
 * GET /c/member/growth-logs → GrowthLogView[]。
 * source：1消费 2签到 3评价 4邀请 5手工调整；growth 可负。
 *
 * 注意：后端返回的是裸数组，没有 PageResult 的 total，
 * 调用方只能用「本次返回条数 == pageSize」推断是否还有下一页。
 */
export interface GrowthLogVO {
  id: number
  source: number
  bizId: number | null
  growth: number
  beforeValue: number | null
  afterValue: number | null
  effectiveAt: string | null
  expireAt: string | null
  remark: string | null
  createdAt: string | null
}

export function fetchGrowthLogs(
  pageNum = 1,
  pageSize = 20,
  silent = false,
): Promise<GrowthLogVO[]> {
  return request<GrowthLogVO[]>({
    url: '/v1/c/member/growth-logs',
    data: { pageNum, pageSize },
    silent,
  })
}

/* ------------------------------------------------------------------ */
/* 补签                                                                */
/* ------------------------------------------------------------------ */

/** POST /c/checkin/compensate，date 为 yyyy-MM-dd，仅允许最近 7 天内的过去日期。 */
export function compensateCheckin(date: string): Promise<CheckinResultVO> {
  return request<CheckinResultVO>({
    url: '/v1/c/checkin/compensate',
    method: 'POST',
    data: { date },
  })
}

/* ------------------------------------------------------------------ */
/* 邀请                                                                */
/* ------------------------------------------------------------------ */

/** GET /c/invite/code → Base62 邀请码（大小写敏感，不能自行转大写）。 */
export function fetchInviteCode(silent = false): Promise<string> {
  return request<string>({ url: '/v1/c/invite/code', silent })
}

/** POST /c/invite/bind。已绑定过、绑定自己、码非法后端返回 INVITE_INVALID。 */
export function bindInviteCode(inviteCode: string): Promise<void> {
  return request<void>({
    url: '/v1/c/invite/bind',
    method: 'POST',
    data: { inviteCode },
  })
}
