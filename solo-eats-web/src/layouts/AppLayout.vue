<script setup lang="ts">
import { computed, type Component } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import {
  DataLine,
  Goods,
  Money,
  SwitchButton,
  Tickets,
  Wallet,
} from '@element-plus/icons-vue'
import { useAuthStore } from '@/stores/auth'
import { ROLES, roleLabel } from '@/constants/roles'

interface MenuItem {
  path: string
  label: string
  icon: Component
  roles: string[]
}

const MENU_ITEMS: MenuItem[] = [
  { path: '/merchant/orders', label: '订单管理', icon: Tickets, roles: [ROLES.MERCHANT] },
  { path: '/merchant/products', label: '商品与库存', icon: Goods, roles: [ROLES.MERCHANT] },
  { path: '/merchant/settlements', label: '财务结算', icon: Wallet, roles: [ROLES.MERCHANT] },
  { path: '/admin/overview', label: '运营看板', icon: DataLine, roles: [ROLES.ADMIN] },
  { path: '/admin/settlements', label: '抽成结算', icon: Money, roles: [ROLES.ADMIN] },
]

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const menuItems = computed(() =>
  MENU_ITEMS.filter((item) => item.roles.some((role) => auth.hasRole(role))),
)
const pageTitle = computed(() => route.meta.title ?? '控制台')

async function handleLogout(): Promise<void> {
  try {
    await ElMessageBox.confirm('退出后需要重新登录才能继续操作。', '确认退出', {
      confirmButtonText: '退出登录',
      cancelButtonText: '取消',
      type: 'warning',
    })
  } catch {
    return
  }
  auth.logout()
  await router.replace('/login')
}
</script>

<template>
  <div class="app-shell">
    <aside class="app-sidebar">
      <div class="brand">
        <div class="brand-mark">SE</div>
        <div>
          <div class="brand-name">SoloEats</div>
          <div class="brand-sub">商家与运营中心</div>
        </div>
      </div>

      <el-menu
        :default-active="route.path"
        router
        class="app-menu"
        background-color="transparent"
        text-color="#c7d2cc"
        active-text-color="#ffffff"
      >
        <el-menu-item v-for="item in menuItems" :key="item.path" :index="item.path">
          <el-icon><component :is="item.icon" /></el-icon>
          <span>{{ item.label }}</span>
        </el-menu-item>
      </el-menu>

      <div class="sidebar-foot mono">v0.1.0 · /api</div>
    </aside>

    <div class="app-main">
      <header class="app-header">
        <div>
          <h1>{{ pageTitle }}</h1>
          <p class="header-sub">
            {{ roleLabel(auth.primaryRole) }}
            <template v-if="auth.merchantId"> · 商家 ID {{ auth.merchantId }}</template>
            <template v-if="auth.shopId"> · 门店 ID {{ auth.shopId }}</template>
          </p>
        </div>
        <div class="header-actions">
          <el-tag effect="plain" round>{{ roleLabel(auth.primaryRole) }}</el-tag>
          <el-button text :icon="SwitchButton" @click="handleLogout">退出</el-button>
        </div>
      </header>

      <main class="app-content">
        <router-view />
      </main>
    </div>
  </div>
</template>

<style scoped>
.app-shell {
  display: flex;
  min-height: 100vh;
}

.app-sidebar {
  width: 224px;
  flex: 0 0 224px;
  background: var(--app-sidebar);
  color: #fff;
  display: flex;
  flex-direction: column;
  padding: 20px 12px 16px;
  position: sticky;
  top: 0;
  height: 100vh;
}

.brand {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 0 10px 22px;
}

.brand-mark {
  width: 36px;
  height: 36px;
  border-radius: 9px;
  background: var(--el-color-primary);
  display: grid;
  place-items: center;
  font-weight: 700;
  font-size: 14px;
  letter-spacing: 0.5px;
}

.brand-name {
  font-size: 15px;
  font-weight: 600;
  letter-spacing: 0.2px;
}

.brand-sub {
  font-size: 11px;
  color: #9fb0a7;
  margin-top: 2px;
}

.app-menu {
  border-right: none;
  flex: 1;
}

.app-menu :deep(.el-menu-item) {
  height: 42px;
  line-height: 42px;
  border-radius: 8px;
  margin-bottom: 4px;
  font-size: 13px;
}

.app-menu :deep(.el-menu-item:hover) {
  background: rgba(255, 255, 255, 0.07);
}

.app-menu :deep(.el-menu-item.is-active) {
  background: var(--el-color-primary);
}

.sidebar-foot {
  color: #7d8f86;
  padding: 0 12px;
}

.app-main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
}

.app-header {
  height: 64px;
  background: #fff;
  border-bottom: 1px solid var(--app-border);
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 24px;
  position: sticky;
  top: 0;
  z-index: 10;
}

.app-header h1 {
  font-size: 16px;
  font-weight: 600;
}

.header-sub {
  font-size: 12px;
  color: var(--app-muted);
  margin-top: 2px;
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.app-content {
  padding: 20px 24px 32px;
  max-width: 1440px;
  width: 100%;
}

@media (max-width: 900px) {
  .app-sidebar {
    width: 72px;
    flex-basis: 72px;
    padding: 16px 8px;
  }

  .brand > div:last-child,
  .sidebar-foot,
  .app-menu :deep(.el-menu-item span) {
    display: none;
  }

  .brand {
    justify-content: center;
    padding: 0 0 18px;
  }

  .app-menu :deep(.el-menu-item) {
    justify-content: center;
    padding: 0;
  }

  .app-content {
    padding: 16px;
  }
}
</style>
