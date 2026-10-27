import type { Component } from 'vue'
import { createRouter, createWebHistory } from 'vue-router'
import AppLayout from '@/layouts/AppLayout.vue'
import { useAuthStore } from '@/stores/auth'
import { useMenuStore } from '@/stores/menu'
import type { MenuNode } from '@/api/types'
import { flattenMenuTree, normalizeMenuPath } from '@/utils/menu'
import { homeForRole } from './home'

declare module 'vue-router' {
  interface RouteMeta {
    public?: boolean
    roles?: string[]
    title?: string
    menuId?: number
  }
}

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/login',
      name: 'login',
      component: () => import('@/views/LoginView.vue'),
      meta: { public: true, title: '登录' },
    },
    {
      path: '/',
      name: 'AppLayout',
      component: AppLayout,
      children: [
        {
          path: '',
          name: 'home',
          redirect: () => homeForRole(useAuthStore().roles),
          meta: { title: '首页' },
        },
        {
          path: 'merchant/orders',
          name: 'merchant-orders',
          component: () => import('@/views/merchant/MerchantOrdersView.vue'),
          meta: { roles: ['MERCHANT'], title: '订单管理' },
        },
        {
          path: 'merchant/products',
          name: 'merchant-products',
          component: () => import('@/views/merchant/MerchantProductsView.vue'),
          meta: { roles: ['MERCHANT'], title: '商品与库存' },
        },
        {
          path: 'merchant/settlements',
          name: 'merchant-settlements',
          component: () => import('@/views/merchant/MerchantSettlementsView.vue'),
          meta: { roles: ['MERCHANT'], title: '财务结算' },
        },
        {
          path: 'admin/overview',
          name: 'admin-overview',
          component: () => import('@/views/admin/AdminOverviewView.vue'),
          meta: { roles: ['ADMIN'], title: '运营看板' },
        },
        {
          path: 'admin/settlements',
          name: 'admin-settlements',
          component: () => import('@/views/admin/AdminSettlementsView.vue'),
          meta: { roles: ['ADMIN'], title: '抽成结算' },
        },
        {
          path: 'merchant/apply',
          name: 'admin-merchant-apply',
          component: () => import('@/views/admin/merchant/ApplyListView.vue'),
          meta: { roles: ['ADMIN'], title: '商家入驻审核' },
        },
        {
          path: 'merchant/apply/:id',
          name: 'admin-merchant-apply-audit',
          component: () => import('@/views/admin/merchant/AuditView.vue'),
          meta: { roles: ['ADMIN'], title: '商家审核详情' },
        },
        {
          path: 'store/shop',
          name: 'admin-shop-list',
          component: () => import('@/views/admin/store/ShopListView.vue'),
          meta: { roles: ['ADMIN'], title: '门店管理' },
        },
        {
          path: 'store/shop/new',
          name: 'admin-shop-create',
          component: () => import('@/views/admin/store/ShopFormView.vue'),
          meta: { roles: ['ADMIN'], title: '新增门店' },
        },
        {
          path: 'store/shop/:id/edit',
          name: 'admin-shop-edit',
          component: () => import('@/views/admin/store/ShopFormView.vue'),
          meta: { roles: ['ADMIN'], title: '编辑门店' },
        },
        {
          path: 'product/spu',
          name: 'admin-spu-list',
          component: () => import('@/views/admin/product/SpuListView.vue'),
          meta: { roles: ['ADMIN'], title: '商品库' },
        },
        {
          path: 'marketing/coupon',
          name: 'admin-coupon',
          component: () => import('@/views/admin/marketing/CouponView.vue'),
          meta: { roles: ['ADMIN'], title: '券管理' },
        },
        // 后端菜单 path 别名：t_sys_menu 里商家/门店的 path 是 /merchant/list、/store/list，
        // 与早期静态路由 /merchant/apply、/store/shop 不同。这里补别名，
        // 让菜单驱动侧边栏时能落到已有页面，而不是解析不到组件走占位页。
        {
          path: 'store/list',
          name: 'admin-store-list',
          component: () => import('@/views/admin/store/ShopListView.vue'),
          meta: { roles: ['ADMIN'], title: '门店管理' },
        },
        {
          path: 'merchant/list',
          name: 'admin-merchant-list',
          component: () => import('@/views/admin/merchant/ApplyListView.vue'),
          meta: { roles: ['ADMIN'], title: '商家管理' },
        },
      ],
    },
    {
      path: '/:pathMatch(.*)*',
      name: 'not-found',
      component: () => import('@/views/NotFoundView.vue'),
      meta: { public: true, title: '页面不存在' },
    },
  ],
})

type ViewLoader = () => Promise<Component>

/** 构建期收集所有页面模块，后端 component 字段据此解析，无需逐条静态声明。 */
const viewModules = import.meta.glob('/src/views/**/*.vue') as unknown as Record<
  string,
  ViewLoader
>
const viewKeys = Object.keys(viewModules)
const viewKeyIndex = new Map(viewKeys.map((key) => [key.toLowerCase(), key]))

function normalizeComponent(component: string): string {
  return component
    .trim()
    .replace(/\\/g, '/')
    .replace(/^\/+|\/+$/g, '')
    .replace(/\.vue$/i, '')
}

/**
 * 候选顺序：精确 path.vue → 忽略大小写 path.vue → 精确 path/index.vue → 忽略大小写 path/index.vue。
 * 全部未命中返回 null，由调用方挂占位页。
 */
function resolveMenuComponent(component: string): ViewLoader | null {
  const normalized = normalizeComponent(component)
  if (!normalized) {
    return null
  }

  const candidates = [
    `/src/views/${normalized}.vue`,
    `/src/views/${normalized}/index.vue`,
  ]
  for (const candidate of candidates) {
    if (viewModules[candidate]) {
      return viewModules[candidate]
    }
    const caseInsensitiveKey = viewKeyIndex.get(candidate.toLowerCase())
    if (caseInsensitiveKey) {
      return viewModules[caseInsensitiveKey]
    }
  }
  return null
}

/**
 * 把后端菜单注册为 AppLayout 的子路由。只追加，不覆盖：
 * 同 path 的静态路由优先，解析不到组件的菜单挂 ViewPlaceholder。
 */
export function registerMenuRoutes(menus: MenuNode[]): void {
  const existingPaths = new Set(
    router.getRoutes().map((route) => normalizeMenuPath(route.path)),
  )

  for (const node of flattenMenuTree(menus)) {
    if (!node.component) {
      continue
    }
    const path = normalizeMenuPath(node.path)
    if (!path) {
      continue
    }
    const name = `menu-${node.id}`
    if (existingPaths.has(path) || router.hasRoute(name)) {
      continue
    }

    router.addRoute('AppLayout', {
      path: path.replace(/^\//, ''),
      name,
      component:
        resolveMenuComponent(node.component) ??
        (() => import('@/views/ViewPlaceholder.vue')),
      meta: { title: node.menuName, menuId: node.id },
    })
    existingPaths.add(path)
  }
}

router.beforeEach(async (to) => {
  const auth = useAuthStore()

  if (to.meta.public) {
    if (to.name === 'login' && auth.isAuthenticated && auth.roles.length > 0) {
      return homeForRole(auth.roles)
    }

    // 动态菜单路由未注册前，深链接会先落到 404。仅对已登录且尚未加载过菜单的 404
    // 做一次注册并重新解析；loaded 标记保证不会形成循环，登录页不触发。
    const menuStore = useMenuStore()
    if (
      to.name === 'not-found' &&
      auth.isAuthenticated &&
      auth.roles.length > 0 &&
      !menuStore.loaded
    ) {
      await menuStore.load(auth.roles)
      registerMenuRoutes(menuStore.menus)
      return { path: to.fullPath, replace: true }
    }
    return true
  }

  if (!auth.isAuthenticated) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }

  if (auth.roles.length === 0) {
    auth.logout()
    return { name: 'login' }
  }

  const allowedRoles = to.meta.roles
  if (allowedRoles && !allowedRoles.some((role) => auth.hasRole(role))) {
    return homeForRole(auth.roles)
  }

  const menuStore = useMenuStore()
  if (!menuStore.loaded) {
    await menuStore.load(auth.roles)
    registerMenuRoutes(menuStore.menus)
    // 首轮匹配可能已经落到 404，注册后按原地址重新解析一次
    return { path: to.fullPath, replace: true }
  }

  return true
})

export default router
