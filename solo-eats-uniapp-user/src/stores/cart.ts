import { reactive } from 'vue'

/**
 * 购物车：纯前端，按门店分桶存 storage `cart:{shopId}`。
 *
 * 与 pinia 的关系同 auth.ts：工程未装 pinia，用 reactive 单例提供等价 API。
 * 金额只用整数分，禁止浮点累加。
 *
 * 注意：加料（addons）只做展示与规格快照。后端 CreateOrderCommand 只接收
 * skuId + quantity，没有加料字段，因此下单金额只按 SKU 单价计算，
 * 加料金额不参与本单计价（Confirm 页也会再次提示）。
 */

export interface CartAddon {
  itemId: number
  itemName: string
  price: number
}

export interface CartLine {
  skuId: number
  skuName: string
  qty: number
  /** SKU 单价（分），不含加料 */
  price: number
  specJson: string
  addons: CartAddon[]
  spuId?: number
  spuName?: string
  imageUrl?: string
}

const STORAGE_PREFIX = 'cart:'
const MAX_QTY = 99

/**
 * 规格展示文案。后端 specJson 可能是 JSON 字符串（如 {"杯型":"大杯"}），
 * 原样渲染对用户不友好，这里只展示非 JSON 的纯文本规格。
 */
export function cartSpecText(line: CartLine): string {
  const spec = (line.specJson || '').trim()
  if (!spec) {
    return ''
  }
  return spec.startsWith('{') || spec.startsWith('[') ? '' : spec
}

/** 同一 SKU + 同一规格 + 同一组加料才算同一行，用于合并与定位 */
export function cartLineKey(line: CartLine): string {
  const addonIds = (line.addons || [])
    .map((addon) => addon.itemId)
    .sort((a, b) => a - b)
    .join(',')
  return `${line.skuId}|${line.specJson || ''}|${addonIds}`
}

function storageKey(shopId: string | number): string {
  return `${STORAGE_PREFIX}${shopId}`
}

function normalizeLine(raw: unknown): CartLine | null {
  if (!raw || typeof raw !== 'object') {
    return null
  }
  const candidate = raw as Record<string, unknown>
  const skuId = Number(candidate.skuId)
  const qty = Math.trunc(Number(candidate.qty))
  const price = Math.trunc(Number(candidate.price))
  if (!Number.isFinite(skuId) || !Number.isFinite(qty) || qty <= 0 || !Number.isFinite(price)) {
    return null
  }
  const addons = Array.isArray(candidate.addons)
    ? candidate.addons
        .map((addon) => {
          const item = addon as Record<string, unknown>
          const itemId = Number(item.itemId)
          if (!Number.isFinite(itemId)) {
            return null
          }
          return {
            itemId,
            itemName: String(item.itemName || ''),
            price: Math.trunc(Number(item.price || 0)),
          }
        })
        .filter((addon): addon is CartAddon => addon !== null)
    : []
  return {
    skuId,
    skuName: String(candidate.skuName || ''),
    qty: Math.min(qty, MAX_QTY),
    price,
    specJson: String(candidate.specJson || ''),
    addons,
    spuId: Number.isFinite(Number(candidate.spuId)) ? Number(candidate.spuId) : undefined,
    spuName: candidate.spuName ? String(candidate.spuName) : undefined,
    imageUrl: candidate.imageUrl ? String(candidate.imageUrl) : undefined,
  }
}

function readFromStorage(shopId: string | number): CartLine[] {
  const key = storageKey(shopId)
  const raw = uni.getStorageSync(key)
  if (!Array.isArray(raw)) {
    if (raw) {
      uni.removeStorageSync(key)
    }
    return []
  }
  return raw.map(normalizeLine).filter((line): line is CartLine => line !== null)
}

function writeToStorage(shopId: string | number, lines: CartLine[]): void {
  const key = storageKey(shopId)
  if (lines.length === 0) {
    uni.removeStorageSync(key)
    return
  }
  uni.setStorageSync(key, lines)
}

export interface CartStore {
  /** shopId → 行数组，reactive 以驱动页面更新 */
  buckets: Record<string, CartLine[]>
  /** 从 storage 载入门店购物车；页面 onLoad 调用，不要在 computed 里触发写状态 */
  load(shopId: string | number): CartLine[]
  /** 只读当前内存桶；未 load 过时返回空数组 */
  lines(shopId: string | number): CartLine[]
  /** 加购；同 key 合并数量 */
  add(shopId: string | number, line: CartLine): void
  inc(shopId: string | number, lineKey: string): void
  dec(shopId: string | number, lineKey: string): void
  remove(shopId: string | number, lineKey: string): void
  clear(shopId: string | number): void
  /** 商品件数 */
  totalCount(shopId: string | number): number
  /** 商品金额（分），按 SKU 价 × 数量，不含加料 */
  totalAmount(shopId: string | number): number
  /** 是否含加料行，用于确认页提示「加料不计价」 */
  hasAddons(shopId: string | number): boolean
}

const state = reactive<CartStore>({
  buckets: {},

  load(shopId: string | number): CartLine[] {
    const key = String(shopId)
    if (!this.buckets[key]) {
      this.buckets[key] = readFromStorage(key)
    }
    return this.buckets[key]
  },

  lines(shopId: string | number): CartLine[] {
    return this.buckets[String(shopId)] || []
  },

  add(shopId: string | number, line: CartLine): void {
    const shopKey = String(shopId)
    const lines = this.load(shopKey)
    const key = cartLineKey(line)
    const existing = lines.find((item) => cartLineKey(item) === key)
    if (existing) {
      existing.qty = Math.min(existing.qty + line.qty, MAX_QTY)
    } else {
      lines.push({
        ...line,
        qty: Math.min(Math.max(1, Math.trunc(line.qty)), MAX_QTY),
        addons: line.addons || [],
      })
    }
    writeToStorage(shopKey, lines)
  },

  inc(shopId: string | number, lineKey: string): void {
    const shopKey = String(shopId)
    const lines = this.load(shopKey)
    const target = lines.find((item) => cartLineKey(item) === lineKey)
    if (!target) {
      return
    }
    target.qty = Math.min(target.qty + 1, MAX_QTY)
    writeToStorage(shopKey, lines)
  },

  dec(shopId: string | number, lineKey: string): void {
    const shopKey = String(shopId)
    const lines = this.load(shopKey)
    const index = lines.findIndex((item) => cartLineKey(item) === lineKey)
    if (index < 0) {
      return
    }
    if (lines[index].qty <= 1) {
      lines.splice(index, 1)
    } else {
      lines[index].qty -= 1
    }
    writeToStorage(shopKey, lines)
  },

  remove(shopId: string | number, lineKey: string): void {
    const shopKey = String(shopId)
    const lines = this.load(shopKey)
    const index = lines.findIndex((item) => cartLineKey(item) === lineKey)
    if (index >= 0) {
      lines.splice(index, 1)
    }
    writeToStorage(shopKey, lines)
  },

  clear(shopId: string | number): void {
    const shopKey = String(shopId)
    this.buckets[shopKey] = []
    writeToStorage(shopKey, [])
  },

  totalCount(shopId: string | number): number {
    return this.lines(shopId).reduce((total, line) => total + line.qty, 0)
  },

  totalAmount(shopId: string | number): number {
    return this.lines(shopId).reduce((total, line) => total + line.price * line.qty, 0)
  },

  hasAddons(shopId: string | number): boolean {
    return this.lines(shopId).some((line) => line.addons.length > 0)
  },
})

export function useCartStore(): CartStore {
  return state
}
