<script setup lang="ts">
import { computed, onMounted, type Component } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Goods, Shop, SwitchButton, Tickets, Wallet } from '@element-plus/icons-vue'
import { useAuthStore } from '@/stores/auth'
import { useShopStore } from '@/stores/shop'
import { ROLES, roleLabel } from '@/constants/roles'

interface MenuItem {
  path: string
  label: string
  icon: Component
}

const MENU_ITEMS: MenuItem[] = [
  { path: '/orders', label: '订单管理', icon: Tickets },
  { path: '/products', label: '商品与库存', icon: Goods },
  { path: '/settlements', label: '财务结算', icon: Wallet },
]

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const shop = useShopStore()

const menuItems = computed(() =>
  MENU_ITEMS.filter(() => auth.hasRole(ROLES.MERCHANT)),
)
const pageTitle = computed(() => route.meta.title ?? '商家中心')
const shopOptions = computed(() =>
  shop.shops.map((item) => ({
    id: item.id,
    name: item.shopName,
    statusText: item.statusText,
    bound: item.id === shop.boundShopId,
    disabled: shop.boundShopId !== null && item.id !== shop.boundShopId,
  })),
)

async function loadShops(): Promise<void> {
  try {
    await shop.loadShops()
  } catch {
    // 拦截器已提示；页面顶部保留 shop.error 供重试
  }
}

function handleShopChange(value: number | string): void {
  const nextId = Number(value)
  if (!shop.selectShop(nextId)) {
    ElMessage.warning('当前账号令牌只绑定一个门店，请使用该门店账号登录后再切换')
    return
  }
  ElMessage.success(`已切换至 ${shop.currentShop?.shopName ?? `门店 ${nextId}`}`)
}

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
  shop.reset()
  auth.logout()
  await router.replace('/login')
}

onMounted(loadShops)
</script>

<template>
  <div class="app-shell">
    <aside class="app-sidebar">
      <div class="brand">
        <div class="brand-mark">SE</div>
        <div>
          <div class="brand-name">数创星光外卖</div>
          <div class="brand-sub">商家中心</div>
        </div>
      </div>

      <el-menu
        :default-active="route.path"
        router
        class="app-menu"
        background-color="transparent"
        text-color="#b9d6d2"
        active-text-color="#ffffff"
      >
        <el-menu-item v-for="item in menuItems" :key="item.path" :index="item.path">
          <el-icon><component :is="item.icon" /></el-icon>
          <span>{{ item.label }}</span>
        </el-menu-item>
      </el-menu>

      <div class="sidebar-foot mono">v0.1.0 · 独立工程 /merchant/</div>
    </aside>

    <div class="app-main">
      <header class="app-header">
        <div class="header-title">
          <h1>{{ pageTitle }}</h1>
          <p class="header-sub">
            {{ roleLabel(auth.primaryRole) }}
            <template v-if="auth.merchantId"> · 商家 ID {{ auth.merchantId }}</template>
            <template v-if="auth.userId"> · 账号 {{ auth.userId }}</template>
          </p>
        </div>

        <div class="header-actions">
          <div class="shop-switch">
            <el-icon class="shop-icon"><Shop /></el-icon>
            <el-select
              :model-value="shop.currentShopId"
              :loading="shop.loading"
              class="shop-select"
              placeholder="选择门店"
              @change="handleShopChange"
            >
              <el-option
                v-for="item in shopOptions"
                :key="item.id"
                :label="`${item.name}（${item.id}）`"
                :value="item.id"
                :disabled="item.disabled"
              >
                <span class="shop-option">
                  <span>{{ item.name }}</span>
                  <span class="shop-option-meta">
                    {{ item.statusText }}
                    <span v-if="item.bound" class="bound-tag">账号绑定</span>
                  </span>
                </span>
              </el-option>
            </el-select>
          </div>

          <el-tooltip
            v-if="!shop.canSwitch"
            content="门店账号的令牌只绑定一个门店；切换门店请使用对应门店账号登录"
            placement="bottom"
          >
            <el-tag type="info" effect="plain" round>单店账号</el-tag>
          </el-tooltip>
          <el-tag v-else type="success" effect="plain" round>可切换门店</el-tag>

          <el-button text :icon="SwitchButton" @click="handleLogout">退出</el-button>
        </div>
      </header>

      <el-alert
        v-if="shop.error"
        class="shop-alert"
        type="warning"
        :closable="false"
        show-icon
        :title="shop.error"
      />

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
  font-size: 14px;
  font-weight: 600;
  letter-spacing: 0.2px;
}

.brand-sub {
  font-size: 11px;
  color: #9fc4be;
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
  color: #6f968f;
  padding: 0 12px;
}

.app-main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
}

.app-header {
  min-height: 64px;
  background: #fff;
  border-bottom: 1px solid var(--app-border);
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 10px 24px;
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
  gap: 10px;
  flex-wrap: wrap;
  justify-content: flex-end;
}

.shop-switch {
  display: flex;
  align-items: center;
  gap: 6px;
}

.shop-icon {
  color: var(--el-color-primary);
}

.shop-select {
  width: 220px;
}

.shop-option {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.shop-option-meta {
  color: var(--app-muted);
  font-size: 12px;
}

.bound-tag {
  margin-left: 6px;
  color: var(--el-color-primary);
}

.shop-alert {
  margin: 12px 24px 0;
  width: auto;
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

  .shop-select {
    width: 160px;
  }
}
</style>
