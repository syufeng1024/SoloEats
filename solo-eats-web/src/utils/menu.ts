import type { Component } from 'vue'
import {
  Avatar,
  Bell,
  Box,
  Clock,
  Collection,
  DataLine,
  Document,
  Edit,
  Goods,
  Iphone,
  List,
  Menu,
  Message,
  Money,
  OfficeBuilding,
  Picture,
  Postcard,
  PriceTag,
  QuestionFilled,
  Search,
  Service,
  Setting,
  Shop,
  Tickets,
  TrendCharts,
  User,
  Van,
  Wallet,
  Warning,
} from '@element-plus/icons-vue'
import type { MenuNode } from '@/api/types'

/**
 * 后端 icon 字段是自由文本（money/list/user/chart/clock/service …），
 * 这里只做名称到 Element Plus 组件的映射，未命中统一退回 Menu，不引入图标库。
 */
const MENU_ICONS: Record<string, Component> = {
  dashboard: DataLine,
  shop: Shop,
  goods: Goods,
  order: Tickets,
  delivery: Van,
  marketing: PriceTag,
  user: User,
  money: Money,
  wallet: Wallet,
  content: Document,
  service: Service,
  report: TrendCharts,
  chart: TrendCharts,
  system: Setting,
  setting: Setting,
  role: Avatar,
  menu: Menu,
  list: List,
  freight: Van,
  email: Message,
  mail: Message,
  picture: Picture,
  search: Search,
  phone: Iphone,
  coupon: PriceTag,
  clock: Clock,
  warning: Warning,
  question: QuestionFilled,
  edit: Edit,
  tenant: OfficeBuilding,
  dept: OfficeBuilding,
  package: Box,
  post: Postcard,
  dict: Collection,
  log: Document,
  notice: Bell,
}

export function resolveMenuIcon(name: string | null | undefined): Component {
  const key = (name ?? '').trim().toLowerCase()
  return MENU_ICONS[key] ?? Menu
}

/** 统一成以 / 开头的绝对路径；空路径返回空串，调用方据此跳过。 */
export function normalizeMenuPath(path: string | null | undefined): string {
  if (!path) {
    return ''
  }
  const trimmed = path.trim().replace(/\\/g, '/')
  if (!trimmed) {
    return ''
  }
  return trimmed.startsWith('/') ? trimmed : `/${trimmed}`
}

/** 深度优先展开菜单树；按 id 去重，防止脏数据里的环导致死循环。 */
export function flattenMenuTree(nodes: MenuNode[] | null | undefined): MenuNode[] {
  const result: MenuNode[] = []
  const seen = new Set<number>()
  const walk = (items: MenuNode[]): void => {
    for (const item of items) {
      if (seen.has(item.id)) {
        continue
      }
      seen.add(item.id)
      result.push(item)
      if (Array.isArray(item.children) && item.children.length > 0) {
        walk(item.children)
      }
    }
  }
  walk(Array.isArray(nodes) ? nodes : [])
  return result
}

/** 侧边栏只展示「菜单(2) + 有路由地址」的节点，目录与按钮不生成入口。 */
export function isSideMenuItem(node: MenuNode): boolean {
  return node.menuType === 2 && normalizeMenuPath(node.path).length > 0
}
