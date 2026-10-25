import { ApiError } from '@/api/http'
import { reportTracks } from '@/api/rider'
import { useRiderStore } from '@/stores/rider'
import type { RiderTrackPointVO } from '@/api/types'
import { toServerDateTime } from './date'

/**
 * 定位 + 轨迹上报。
 *
 * 节流与批量策略：
 * - 采样间隔 5s（SAMPLE_INTERVAL_MS），另有 lastSampleAt 二次守卫，防止时钟回拨或重复触发
 * - 点位先进内存队列并落 Storage，攒批后一次最多上报 20 点（后端 ReportTrackRequest @Size(max=20)）
 * - 单飞：flushing 标记保证同一时刻只有一条 /rider/track 在途，绝不并发上报
 * - 上报失败不弹 toast、不清队列，等下一次采样重试；本地队列上限 200 点，超出丢最旧的
 * - stopTrackReport 只停采样并尝试最后一次补传，未成功的点留在本地，下次 start 同单时补传
 */

export interface GeoPoint {
  lng: number
  lat: number
  accuracy?: number
  speed?: number
}

interface QueuedPoint {
  deliveryId: number
  lng: number
  lat: number
  speed?: number | null
  accuracy?: number | null
  reportAt: string
}

interface TrackSession {
  deliveryId: number
  timer: ReturnType<typeof setInterval> | null
}

const SAMPLE_INTERVAL_MS = 5_000
const MAX_BATCH_POINTS = 20
const MAX_LOCAL_POINTS = 200
const QUEUE_STORAGE_KEY = 'rider:track:queue'
const LOCATION_STORAGE_KEY = 'lastLocation'

/** 10003 未登录 / 10004 无权访问 / 50003 配送单不存在：重试也不会成功 */
const DROP_ON_ERROR_CODES = new Set([10003, 10004, 50003])

let session: TrackSession | null = null
let queue: QueuedPoint[] = []
let lastSampleAt = 0
let flushing = false

/* ------------------------------ 定位 ------------------------------ */

function describeLocationError(error: unknown): string {
  const message = String(
    (error as { errMsg?: string; message?: string })?.errMsg ||
      (error as { message?: string })?.message ||
      '',
  )
  if (/auth|deny|permission|authorize/i.test(message)) {
    return '定位权限未开启，请在系统或浏览器设置中允许定位'
  }
  if (/timeout|超时/i.test(message)) {
    return '定位超时，请到信号较好的位置重试'
  }
  return '无法获取当前位置，请检查 GPS 与网络'
}

/**
 * 获取当前定位。失败抛出中文 Error，由页面决定兜底（如送达时改为拍照留证）。
 */
export function getCurrentLocation(): Promise<GeoPoint> {
  return new Promise<GeoPoint>((resolve, reject) => {
    uni.getLocation({
      type: 'gcj02',
      isHighAccuracy: true,
      highAccuracyExpireTime: 4_000,
      success: (result) => {
        const lng = Number(result.longitude)
        const lat = Number(result.latitude)
        if (!Number.isFinite(lng) || !Number.isFinite(lat)) {
          reject(new Error('定位返回坐标无效'))
          return
        }
        const point: GeoPoint = {
          lng,
          lat,
          accuracy: Number.isFinite(Number(result.accuracy)) ? Number(result.accuracy) : undefined,
          speed: Number.isFinite(Number(result.speed)) ? Number(result.speed) : undefined,
        }
        rememberLocation(point)
        resolve(point)
      },
      fail: (error) => reject(new Error(describeLocationError(error))),
    })
  })
}

/** 最近一次定位落本地，供听单/大厅查询复用，同时写入 rider store 供页面展示 */
function rememberLocation(point: GeoPoint): void {
  const stored = { lng: point.lng, lat: point.lat, at: Date.now() }
  try {
    uni.setStorageSync(LOCATION_STORAGE_KEY, stored)
  } catch (error) {
    // 存储失败不影响定位结果
  }
  try {
    useRiderStore().rememberLocation({ lng: point.lng, lat: point.lat })
  } catch (error) {
    // store 未初始化时忽略（如工具单测环境）
  }
}

/* ---------------------------- 本地队列 ---------------------------- */

function loadQueue(): QueuedPoint[] {
  try {
    const raw = uni.getStorageSync(QUEUE_STORAGE_KEY)
    if (Array.isArray(raw)) {
      return raw.filter(
        (item): item is QueuedPoint =>
          Boolean(item) &&
          typeof item.deliveryId === 'number' &&
          Number.isFinite(Number(item.lng)) &&
          Number.isFinite(Number(item.lat)),
      )
    }
  } catch (error) {
    // 脏数据直接丢弃
  }
  return []
}

function persistQueue(): void {
  try {
    uni.setStorageSync(QUEUE_STORAGE_KEY, queue)
  } catch (error) {
    // 本地存储满时仅保留内存队列，不阻断上报
  }
}

function pushPoint(point: QueuedPoint): void {
  queue.push(point)
  if (queue.length > MAX_LOCAL_POINTS) {
    queue = queue.slice(queue.length - MAX_LOCAL_POINTS)
  }
  persistQueue()
}

function toPayload(point: QueuedPoint): RiderTrackPointVO {
  return {
    lng: point.lng,
    lat: point.lat,
    speed: point.speed ?? null,
    accuracy: point.accuracy ?? null,
    reportAt: point.reportAt,
  }
}

/* ---------------------------- 采样与上报 ---------------------------- */

async function sampleOnce(): Promise<void> {
  if (!session) {
    return
  }
  const now = Date.now()
  if (now - lastSampleAt < SAMPLE_INTERVAL_MS - 200) {
    return
  }
  lastSampleAt = now

  const deliveryId = session.deliveryId
  try {
    const location = await getCurrentLocation()
    // 采样期间可能已 stop/切换配送单，丢弃过期点
    if (!session || session.deliveryId !== deliveryId) {
      return
    }
    pushPoint({
      deliveryId,
      lng: location.lng,
      lat: location.lat,
      speed: location.speed ?? null,
      accuracy: location.accuracy ?? null,
      reportAt: toServerDateTime(new Date()),
    })
  } catch (error) {
    // 定位失败不攒假点，等下一轮；不提示骑手，避免骑行中刷屏
    return
  }
  await flush()
}

/**
 * 按 deliveryId 分组顺序补传。单飞 + 失败即停：
 * 一条失败后保留剩余点位，等下一次采样再试，避免断网时打出请求风暴。
 */
async function flush(): Promise<void> {
  if (flushing || queue.length === 0) {
    return
  }
  flushing = true
  try {
    while (queue.length > 0) {
      const deliveryId = queue[0].deliveryId
      const batch = queue
        .filter((point) => point.deliveryId === deliveryId)
        .slice(0, MAX_BATCH_POINTS)
      if (batch.length === 0) {
        // 理论上不会发生，兜底防止死循环
        queue.shift()
        persistQueue()
        continue
      }
      try {
        await reportTracks(deliveryId, batch.map(toPayload))
      } catch (error) {
        if (error instanceof ApiError && DROP_ON_ERROR_CODES.has(error.code)) {
          // 归属不符/配送单已删/登录失效：这批永远不会成功，丢弃以免堵住后续配送单
          const dropped = new Set(batch)
          queue = queue.filter((point) => !dropped.has(point))
          persistQueue()
          continue
        }
        // 网络/超时/服务端 5xx：保留点位，下一轮采样时重试
        break
      }
      const sent = new Set(batch)
      queue = queue.filter((point) => !sent.has(point))
      persistQueue()
    }
  } finally {
    flushing = false
  }
}

/**
 * 开始某配送单的轨迹上报。重复调用同一 deliveryId 为幂等操作。
 * 启动时只保留该单的历史积压点，其他配送单的陈旧点直接丢弃，避免污染新单轨迹。
 */
export function startTrackReport(deliveryId: number): void {
  if (!deliveryId) {
    return
  }
  if (session && session.deliveryId === deliveryId) {
    return
  }
  stopTimerOnly()
  queue = loadQueue().filter((point) => point.deliveryId === deliveryId)
  persistQueue()
  session = { deliveryId, timer: null }
  lastSampleAt = 0
  void sampleOnce()
  session.timer = setInterval(() => {
    void sampleOnce()
  }, SAMPLE_INTERVAL_MS)
}

/**
 * 停止上报。定时器立即清除；本地剩余点尝试补传一次（失败保留，下次 start 同单再传）。
 */
export function stopTrackReport(): void {
  if (!session) {
    return
  }
  stopTimerOnly()
  void flush()
}

function stopTimerOnly(): void {
  if (session?.timer) {
    clearInterval(session.timer)
  }
  session = null
}

/** 页面离开或切换账号时清空内存态；本地队列保留给下次启动补传 */
export function resetTrackReporter(): void {
  stopTimerOnly()
  queue = []
  lastSampleAt = 0
  try {
    uni.removeStorageSync(QUEUE_STORAGE_KEY)
  } catch (error) {
    // 忽略
  }
}
