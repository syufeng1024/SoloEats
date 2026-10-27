import { describe, expect, it } from 'vitest'
import { mergeRoleMenuIds, planRoleMenuSelection, type MenuPermissionNode } from './menu-perms'

/**
 * 菜单树接口只返回目录/菜单；按钮 ID 只能从角色已有授权中带回来。
 * 1 目录 -> 2 菜单、3 菜单；21/22 是菜单 2 下的按钮，不在树中。
 */
const tree: MenuPermissionNode[] = [
  {
    id: 1,
    children: [
      { id: 2, children: [] },
      { id: 3, children: [] },
    ],
  },
]

describe('planRoleMenuSelection', () => {
  it('保留树中不可见的按钮 ID，级联回填只取已授权子树前沿', () => {
    const plan = planRoleMenuSelection(tree, [1, 2, 3, 21, 22])

    expect(plan.strictMode).toBe(false)
    expect(plan.checkedKeys).toEqual([2, 3])
    expect(plan.hiddenMenuIds).toEqual([21, 22])
  })

  it('保存时把隐藏按钮 ID 合并回 menuIds，不会清空按钮权限', () => {
    const plan = planRoleMenuSelection(tree, [1, 2, 21, 22])
    const menuIds = mergeRoleMenuIds(plan.checkedKeys, [1], plan.hiddenMenuIds)

    expect(menuIds).toEqual([1, 2, 21, 22])
    expect(menuIds).toContain(21)
    expect(menuIds).toContain(22)
  })

  it('只授权父目录时切换精确模式，不级联勾选子菜单', () => {
    const plan = planRoleMenuSelection(tree, [1])

    expect(plan.strictMode).toBe(true)
    expect(plan.checkedKeys).toEqual([1])
    expect(mergeRoleMenuIds(plan.checkedKeys, [], plan.hiddenMenuIds)).toEqual([1])
  })

  it('只授权子菜单、父目录未授权时切换精确模式，不补父节点', () => {
    const plan = planRoleMenuSelection(tree, [2])

    expect(plan.strictMode).toBe(true)
    expect(plan.checkedKeys).toEqual([2])
    expect(mergeRoleMenuIds(plan.checkedKeys, [], plan.hiddenMenuIds)).toEqual([2])
  })

  it('部分子菜单授权时只回填前沿节点，父节点由 halfChecked 补回', () => {
    const plan = planRoleMenuSelection(tree, [1, 2])

    expect(plan.strictMode).toBe(false)
    expect(plan.checkedKeys).toEqual([2])
    expect(mergeRoleMenuIds(plan.checkedKeys, [1], plan.hiddenMenuIds)).toEqual([1, 2])
    expect(mergeRoleMenuIds(plan.checkedKeys, [1], plan.hiddenMenuIds)).not.toContain(3)
  })
})

describe('mergeRoleMenuIds', () => {
  it('去重并按 ID 升序输出', () => {
    expect(mergeRoleMenuIds([3, 1, 3], [2, 1], [22, 21, 22])).toEqual([1, 2, 3, 21, 22])
  })

  it('输入为空时返回空数组', () => {
    expect(mergeRoleMenuIds([], [], [])).toEqual([])
  })
})
