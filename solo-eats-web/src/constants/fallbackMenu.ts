import type { Component } from 'vue'
import {
  Checked,
  DataLine,
  Goods,
  MapLocation,
  Money,
  OfficeBuilding,
  PriceTag,
  RefreshLeft,
  SetUp,
  Shop,
  Tickets,
  Wallet,
  Warning,
} from '@element-plus/icons-vue'
import { ROLES } from './roles'

/**
 * 菜单接口不可用时的兜底导航。后端 /api/v1/auth/menus 失败或过滤后没有可路由菜单时启用，
 * 保证后台不会因为菜单接口异常而失去全部入口。
 */
export interface FallbackMenuItem {
  path: string
  label: string
  icon: Component
  roles: string[]
}

export const FALLBACK_MENU_ITEMS: FallbackMenuItem[] = [
  { path: '/merchant/orders', label: '订单管理', icon: Tickets, roles: [ROLES.MERCHANT] },
  { path: '/merchant/products', label: '商品与库存', icon: Goods, roles: [ROLES.MERCHANT] },
  { path: '/merchant/settlements', label: '财务结算', icon: Wallet, roles: [ROLES.MERCHANT] },
  { path: '/admin/overview', label: '运营看板', icon: DataLine, roles: [ROLES.ADMIN] },
  { path: '/order/list', label: '订单列表', icon: Tickets, roles: [ROLES.ADMIN] },
  { path: '/refund/list', label: '售后管理', icon: RefreshLeft, roles: [ROLES.ADMIN] },
  { path: '/order/exception', label: '异常订单池', icon: Warning, roles: [ROLES.ADMIN] },
  { path: '/merchant/apply', label: '商家入驻审核', icon: Shop, roles: [ROLES.ADMIN] },
  { path: '/merchant/manage', label: '商家列表', icon: Shop, roles: [ROLES.ADMIN] },
  { path: '/store/shop', label: '门店管理', icon: OfficeBuilding, roles: [ROLES.ADMIN] },
  { path: '/store/delivery-area', label: '配送区域', icon: MapLocation, roles: [ROLES.ADMIN] },
  { path: '/product/spu', label: '商品库', icon: Goods, roles: [ROLES.ADMIN] },
  { path: '/product/spu/new', label: '新建商品', icon: Goods, roles: [ROLES.ADMIN] },
  { path: '/delivery/dispatch', label: '派单中心', icon: SetUp, roles: [ROLES.ADMIN] },
  { path: '/delivery/rider-audit', label: '骑手审核', icon: Checked, roles: [ROLES.ADMIN] },
  { path: '/marketing/coupon', label: '券管理', icon: PriceTag, roles: [ROLES.ADMIN] },
  { path: '/admin/settlements', label: '抽成结算', icon: Money, roles: [ROLES.ADMIN] },
]
