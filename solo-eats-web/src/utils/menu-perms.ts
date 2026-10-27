/**
 * 角色菜单授权回填/保存的纯函数。
 *
 * 背景：GET /admin/menus/tree 只返回目录(1)与菜单(2)，不返回按钮(3)；
 * 但 GET /admin/roles/{id}/menus 返回的 menuIds 包含按钮，
 * 且 PUT /admin/roles/{id}/menus 是全量替换。直接把树勾选结果提交会清空按钮授权。
 *
 * 这里把「树中可见的授权」与「树中不可见的授权」分开：
 * - 可见部分用于 el-tree 回填；
 * - 不可见部分原样保留，保存时合并回 menuIds。
 */
export interface MenuPermissionNode {
  id: number
  children?: MenuPermissionNode[] | null
}

export interface RoleMenuSelection {
  /** 传给 el-tree.setCheckedKeys 的 keys */
  checkedKeys: number[]
  /** 授权中不在菜单树里的 ID（按钮或套餐外节点），保存时必须原样保留 */
  hiddenMenuIds: number[]
  /** true 表示级联勾选无法精确表达原授权，树需切到 check-strictly 模式 */
  strictMode: boolean
}

function sortIds(ids: Iterable<number>): number[] {
  return [...new Set(ids)].sort((left, right) => left - right)
}

/**
 * 计算 el-tree 回填参数，并识别必须保留的隐藏授权 ID。
 */
export function planRoleMenuSelection(
  tree: MenuPermissionNode[],
  grantedIds: number[],
): RoleMenuSelection {
  const byId = new Map<number, MenuPermissionNode>()
  const parentById = new Map<number, number>()
  const childrenById = new Map<number, MenuPermissionNode[]>()

  const walk = (nodes: MenuPermissionNode[], parentId: number): void => {
    for (const node of nodes) {
      if (byId.has(node.id)) {
        // 脏数据出现环时跳过，避免递归不终止
        continue
      }
      byId.set(node.id, node)
      parentById.set(node.id, parentId)
      const children = Array.isArray(node.children) ? node.children : []
      childrenById.set(node.id, children)
      walk(children, node.id)
    }
  }
  walk(tree, 0)

  const grantedSet = new Set(grantedIds)
  const visibleIds = [...grantedSet].filter((id) => byId.has(id))
  const hiddenMenuIds = sortIds([...grantedSet].filter((id) => !byId.has(id)))
  const visibleSet = new Set(visibleIds)

  /**
   * 级联模式能精确表达的条件：
   * 1. 已授权节点在树中的祖先也全部已授权；
   * 2. 已授权的非叶子节点至少有一个已授权子节点。
   * 否则勾选/取消父节点会连带改变未授权兄弟节点，必须改用精确模式。
   */
  const representable = visibleIds.every((id) => {
    let parentId = parentById.get(id) ?? 0
    while (parentId !== 0) {
      if (!visibleSet.has(parentId)) {
        return false
      }
      parentId = parentById.get(parentId) ?? 0
    }
    const children = childrenById.get(id) ?? []
    return children.length === 0 || children.some((child) => visibleSet.has(child.id))
  })

  if (!representable) {
    return {
      checkedKeys: sortIds(visibleSet),
      hiddenMenuIds,
      strictMode: true,
    }
  }

  // 只回填已授权子树的前沿节点，父节点由级联自动 half/full，不会勾到未授权兄弟。
  const checkedKeys = sortIds(
    visibleIds.filter((id) => {
      const children = childrenById.get(id) ?? []
      return !children.some((child) => visibleSet.has(child.id))
    }),
  )

  return { checkedKeys, hiddenMenuIds, strictMode: false }
}

/** 保存时合并树勾选结果与必须保留的隐藏 ID。 */
export function mergeRoleMenuIds(
  checkedIds: number[],
  halfCheckedIds: number[],
  hiddenMenuIds: number[],
): number[] {
  return sortIds([...checkedIds, ...halfCheckedIds, ...hiddenMenuIds])
}
