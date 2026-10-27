import { createRouter, createWebHistory } from 'vue-router'
import MerchantLayout from '@/layouts/MerchantLayout.vue'
import { useAuthStore } from '@/stores/auth'
import { ROLES } from '@/constants/roles'
import { homeForRole } from './home'

declare module 'vue-router' {
  interface RouteMeta {
    public?: boolean
    title?: string
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
      component: MerchantLayout,
      children: [
        {
          path: '',
          name: 'home',
          redirect: '/orders',
          meta: { title: '首页' },
        },
        {
          path: 'orders',
          name: 'merchant-orders',
          component: () => import('@/views/MerchantOrdersView.vue'),
          meta: { title: '订单管理' },
        },
        {
          path: 'orders/:orderId/receipt',
          name: 'merchant-order-receipt',
          component: () => import('@/views/OrderReceiptView.vue'),
          meta: { title: '接单小票' },
        },
        {
          path: 'pickup',
          name: 'merchant-pickup-verify',
          component: () => import('@/views/PickupVerifyView.vue'),
          meta: { title: '取餐核销' },
        },
        {
          path: 'products',
          name: 'merchant-products',
          component: () => import('@/views/MerchantProductsView.vue'),
          meta: { title: '商品与库存' },
        },
        {
          path: 'settlements',
          name: 'merchant-settlements',
          component: () => import('@/views/MerchantSettlementsView.vue'),
          meta: { title: '财务结算' },
        },
        {
          path: 'shop/setting',
          name: 'merchant-shop-setting',
          component: () => import('@/views/ShopSettingView.vue'),
          meta: { title: '门店设置' },
        },
        {
          path: 'shop/pickup',
          name: 'merchant-shop-pickup',
          component: () => import('@/views/PickupSettingView.vue'),
          meta: { title: '自取设置' },
        },
        {
          path: 'shop/decoration',
          name: 'merchant-shop-decoration',
          component: () => import('@/views/ShopDecorationView.vue'),
          meta: { title: '门店装修' },
        },
        {
          path: 'shop/accounts',
          name: 'merchant-shop-accounts',
          component: () => import('@/views/ShopAccountView.vue'),
          meta: { title: '门店账号' },
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

router.beforeEach((to) => {
  const auth = useAuthStore()

  if (to.meta.public) {
    if (to.name === 'login' && auth.isAuthenticated && auth.hasRole(ROLES.MERCHANT)) {
      return homeForRole(auth.roles)
    }
    return true
  }

  if (!auth.isAuthenticated) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }

  // 运营后台账号不能进入商家中心；细粒度角色按族匹配（MERCHANT_MANAGER 等）
  if (!auth.hasRole(ROLES.MERCHANT)) {
    auth.logout()
    return { name: 'login' }
  }

  return true
})

export default router
