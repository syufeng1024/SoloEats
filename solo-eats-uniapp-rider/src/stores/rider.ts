import { reactive } from 'vue'
import { fetchCertification } from '@/api/rider'
import type { IncomeOverviewVO, RiderCertStatusVO } from '@/api/types'

/**
 * 骑手工作状态。
 *
 * 口径说明：
 * - online 是本地镜像。后端没有「查询我是否在线」的接口，首页用 GET /rider/hall 的
 *   50007（RIDER_OFFLINE）做校准；其它错误不改本地状态，避免误判。
 * - certStatus=-1 表示尚未加载，0 未提交 / 1 待审核 / 2 已通过 / 3 已驳回（后端 t_rider.cert_status）。
 * - maxConcurrentOrders 后端未向骑手端下发，这里取 t_rider 表默认值 3 仅用于首页上限提示，
 *   真正的接单上限校验在服务端 assertCanTakeOrder 里，前端值不参与放行判断。
 * - todayOrderCount / todayAmount 来自 GET /rider/income/overview，随页面刷新更新。
 */

const ONLINE_KEY = 'riderOnline'
const LOCATION_KEY = 'lastLocation'

/** t_rider.max_concurrent_orders 建表默认值 */
export const DEFAULT_MAX_CONCURRENT_ORDERS = 3

export interface LastLocation {
  lng: number
  lat: number
}

function readOnline(): boolean {
  return uni.getStorageSync(ONLINE_KEY) === true
}

function readLocation(): LastLocation | null {
  const raw = uni.getStorageSync(LOCATION_KEY)
  if (!raw || typeof raw !== 'object') {
    return null
  }
  const lng = Number((raw as LastLocation).lng)
  const lat = Number((raw as LastLocation).lat)
  return Number.isFinite(lng) && Number.isFinite(lat) ? { lng, lat } : null
}

export interface RiderStore {
  online: boolean
  riderId: number | null
  riderNo: string
  realName: string
  /** -1 未加载 / 0 未提交 / 1 待审核 / 2 已通过 / 3 已驳回 */
  certStatus: number
  certRemark: string
  healthCertExpireAt: string | null
  vehicleType: number | null
  maxConcurrentOrders: number
  todayOrderCount: number
  todayAmount: number
  onTimeRate: number
  lastLocation: LastLocation | null
  certLoaded: boolean
  loadCertification(): Promise<RiderCertStatusVO>
  applyCertification(view: RiderCertStatusVO): void
  applyIncomeOverview(view: IncomeOverviewVO): void
  setOnline(online: boolean): void
  rememberLocation(location: LastLocation): void
  reset(): void
}

const state = reactive<RiderStore>({
  online: readOnline(),
  riderId: null,
  riderNo: '',
  realName: '',
  certStatus: -1,
  certRemark: '',
  healthCertExpireAt: null,
  vehicleType: null,
  maxConcurrentOrders: DEFAULT_MAX_CONCURRENT_ORDERS,
  todayOrderCount: 0,
  todayAmount: 0,
  onTimeRate: 0,
  lastLocation: readLocation(),
  certLoaded: false,

  async loadCertification(): Promise<RiderCertStatusVO> {
    const view = await fetchCertification()
    this.applyCertification(view)
    return view
  },

  applyCertification(view: RiderCertStatusVO): void {
    this.riderId = view.riderId ?? null
    this.riderNo = view.riderNo ?? ''
    this.realName = view.realName ?? ''
    this.certStatus = typeof view.certStatus === 'number' ? view.certStatus : 0
    this.certRemark = view.certRemark ?? ''
    this.healthCertExpireAt = view.healthCertExpireAt ?? null
    this.vehicleType = view.vehicleType ?? null
    this.certLoaded = true
  },

  applyIncomeOverview(view: IncomeOverviewVO): void {
    this.todayOrderCount = Number(view?.todayOrders ?? 0)
    this.todayAmount = Number(view?.todayAmount ?? 0)
    this.onTimeRate = Number(view?.onTimeRate ?? 0)
  },

  setOnline(online: boolean): void {
    this.online = online
    uni.setStorageSync(ONLINE_KEY, online)
  },

  rememberLocation(location: LastLocation): void {
    this.lastLocation = location
    uni.setStorageSync(LOCATION_KEY, { lng: location.lng, lat: location.lat, at: Date.now() })
  },

  reset(): void {
    this.online = false
    this.riderId = null
    this.riderNo = ''
    this.realName = ''
    this.certStatus = -1
    this.certRemark = ''
    this.healthCertExpireAt = null
    this.vehicleType = null
    this.maxConcurrentOrders = DEFAULT_MAX_CONCURRENT_ORDERS
    this.todayOrderCount = 0
    this.todayAmount = 0
    this.onTimeRate = 0
    this.lastLocation = null
    this.certLoaded = false
    uni.removeStorageSync(ONLINE_KEY)
    uni.removeStorageSync(LOCATION_KEY)
  },
})

export function useRiderStore(): RiderStore {
  return state
}

/** cert_status=2 已通过 */
export function isCertApproved(status: number): boolean {
  return status === 2
}
