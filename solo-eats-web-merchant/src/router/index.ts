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
