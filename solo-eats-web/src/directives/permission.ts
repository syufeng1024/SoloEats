import { watch, type Directive, type WatchStopHandle } from 'vue'
import type { MenuNode } from '@/api/types'
import { useAuthStore } from '@/stores/auth'
import { useMenuStore } from '@/stores/menu'

/**
 * v-perm：按钮级权限。
 *
 * 用法：
 *   <el-button v-perm="'system:user:create'">新建用户</el-button>
 *   <el-button v-perm="['system:user:update', 'system:user:assign']">…</el-button>
 *
 * 权限来源（按顺序）：
 *   1. 登录响应下发的 permissions（账号密码登录）；
 *   2. /auth/menus 里 menuType=3 的按钮节点 perms（模拟登录/旧会话没有权限码）。
 *   3. 两者都不可用时放行——后端对 mock token 同样不校验权限码，
 *      不能在数据缺失时把整页按钮全部隐藏。
 *
 * 无权限时直接移除 DOM，避免按钮短暂闪现后再消失。
 */

interface PermissionElement extends HTMLElement {
  __permStop__?: WatchStopHandle
}

function normalizeCodes(value: unknown): string[] {
  if (Array.isArray(value)) {
    return value
      .filter((item): item is string => typeof item === 'string')
      .map((item) => item.trim())
      .filter((item) => item.length > 0)
  }
  return typeof value === 'string' && value.trim().length > 0 ? [value.trim()] : []
}

function collectButtonPermissions(
  nodes: MenuNode[] | null | undefined,
  acc: Set<string> = new Set(),
): Set<string> {
  if (!Array.isArray(nodes)) {
    return acc
  }
  for (const node of nodes) {
    if (node.menuType === 3 && typeof node.perms === 'string') {
      const code = node.perms.trim()
      if (code.length > 0) {
        acc.add(code)
      }
    }
    collectButtonPermissions(node.children, acc)
  }
  return acc
}

/** true 放行；false 移除；null 表示权限数据尚未就绪，等菜单加载后再判断。 */
function decide(required: string[]): boolean | null {
  const auth = useAuthStore()
  if (auth.permissions.length > 0) {
    return required.some((code) => auth.permissions.includes(code))
  }

  const menu = useMenuStore()
  if (!menu.loaded || menu.usingFallback) {
    // 菜单接口失败时会切到硬编码菜单，此时按钮权限未知，按放行处理
    return null
  }
  const granted = collectButtonPermissions(menu.menus)
  return required.some((code) => granted.has(code))
}

export const permission: Directive<PermissionElement, string | string[]> = {
  mounted(el, binding) {
    const required = normalizeCodes(binding.value)
    if (required.length === 0) {
      return
    }

    const evaluate = (): void => {
      if (decide(required) !== false) {
        return
      }
      el.remove()
      el.__permStop__?.()
      el.__permStop__ = undefined
    }

    evaluate()
    if (!el.isConnected) {
      return
    }

    // 路由守卫会先加载菜单，但登录后首个页面仍可能异步返回；这里兜底重判一次。
    const menu = useMenuStore()
    el.__permStop__ = watch(
      () => [menu.menus, menu.loaded, menu.usingFallback] as const,
      evaluate,
    )
  },

  unmounted(el) {
    el.__permStop__?.()
    el.__permStop__ = undefined
  },
}
