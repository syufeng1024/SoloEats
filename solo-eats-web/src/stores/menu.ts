import { computed, ref, type Component } from 'vue'
import { defineStore } from 'pinia'
import { getMenus } from '@/api/auth'
import type { MenuNode } from '@/api/types'
import { FALLBACK_MENU_ITEMS } from '@/constants/fallbackMenu'
import { hasAnyRole } from '@/constants/roles'
import {
  flattenMenuTree,
  isSideMenuItem,
  normalizeMenuPath,
  resolveMenuIcon,
} from '@/utils/menu'

export interface SideMenuItem {
  path: string
  label: string
  icon: Component
  sort: number
}

export const useMenuStore = defineStore('menu', () => {
  const menus = ref<MenuNode[]>([])
  const loaded = ref(false)
  const usingFallback = ref(false)
  const fallbackRoles = ref<string[]>([])

  /**
   * 后端菜单优先；接口失败、返回空树，或树里没有可路由的菜单(2)节点时，
   * 回退到硬编码菜单并按当前角色过滤。
   */
  const flatMenus = computed<SideMenuItem[]>(() => {
    if (usingFallback.value) {
      return FALLBACK_MENU_ITEMS.filter((item) =>
        item.roles.some((role) => hasAnyRole(fallbackRoles.value, role)),
      ).map((item, index) => ({
        path: item.path,
        label: item.label,
        icon: item.icon,
        sort: index,
      }))
    }

    return flattenMenuTree(menus.value)
      .filter(isSideMenuItem)
      .map((node) => ({
        path: normalizeMenuPath(node.path),
        label: node.menuName,
        icon: resolveMenuIcon(node.icon),
        sort: node.sort ?? 0,
      }))
      .sort((left, right) => left.sort - right.sort)
  })

  async function load(roles: string[]): Promise<void> {
    try {
      const tree = await getMenus()
      const hasSideMenu =
        Array.isArray(tree) && flattenMenuTree(tree).some(isSideMenuItem)

      if (hasSideMenu) {
        menus.value = tree
        usingFallback.value = false
      } else {
        // 超管以外的角色目前只授权到目录，扁平后侧边栏为空，同样按兜底处理
        menus.value = Array.isArray(tree) ? tree : []
        fallbackRoles.value = [...roles]
        usingFallback.value = true
      }
    } catch {
      // 错误提示由 Axios 拦截器统一弹出，这里只负责降级，不能阻断路由
      menus.value = []
      fallbackRoles.value = [...roles]
      usingFallback.value = true
    } finally {
      loaded.value = true
    }
  }

  function reset(): void {
    menus.value = []
    fallbackRoles.value = []
    usingFallback.value = false
    loaded.value = false
  }

  return {
    menus,
    loaded,
    usingFallback,
    flatMenus,
    load,
    reset,
  }
})
