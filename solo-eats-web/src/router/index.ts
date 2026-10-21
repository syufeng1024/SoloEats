import { createRouter, createWebHistory } from 'vue-router'
import AppLayout from '@/layouts/AppLayout.vue'
import { useAuthStore } from '@/stores/auth'
import { homeForRole } from './home'

declare module 'vue-router' {
  interface RouteMeta {
    public?: boolean
    roles?: string[]
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
    if (to.name === 'login' && auth.isAuthenticated && auth.roles.length > 0) {
      return homeForRole(auth.roles)
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

  return true
})

export default router
