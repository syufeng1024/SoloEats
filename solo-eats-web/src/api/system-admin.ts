import { request } from './http'
import type { MenuNode, PageResult } from './types'

/**
 * 系统管理运营后台接口。
 * 字段与后端 view/DTO 一一对应：
 * - AdminUserController / SaveUserRequest / SysUserView
 * - AdminRoleController / SaveRoleRequest / SysRoleView
 * - AdminMenuController / SaveMenuRequest / MenuNode
 * - AdminDeptController / SaveDeptRequest / DeptNode
 * - AdminPostController / SavePostRequest / PostView
 */

// ------------------------------------------------------------
// 用户
// ------------------------------------------------------------

export interface AdminUserListParams {
  /** 1平台 2商家 3骑手 */
  userType?: number
  merchantId?: number
  shopId?: number
  /** 0启用 1停用 */
  status?: number
  /** 后端只 like username 或 nickname，不匹配 phone */
  keyword?: string
  pageNum?: number
  pageSize?: number
}

/** SysUserView.java；phone 已脱敏，无 email/postId/角色名/部门名 */
export interface AdminUserView {
  id: number
  tenantId: number
  username: string
  nickname: string | null
  /** 已脱敏，如 138****8000 */
  phone: string | null
  /** 1平台 2商家 3骑手 */
  userType: number
  merchantId: number | null
  shopId: number | null
  riderId: number | null
  deptId: number | null
  /** 1全部 2本租户 3本商家 4本门店 5本人 */
  dataScope: number
  /** 0启用 1停用 */
  status: number
  lastLoginAt: string | null
  createdAt: string
}

/** SaveUserRequest.java */
export interface SaveUserPayload {
  username?: string | null
  password?: string | null
  nickname?: string | null
  phone?: string | null
  userType?: number | null
  merchantId?: number | null
  shopId?: number | null
  riderId?: number | null
  deptId?: number | null
  dataScope?: number | null
  /** 创建：角色 ID；更新：null 表示不动现有角色，[] 表示清空 */
  roleIds?: number[] | null
}

export interface UpdateUserStatusPayload {
  /** 0启用 1停用 */
  status: number
}

export interface AssignUserRolesPayload {
  roleIds: number[]
}

export interface ResetPasswordPayload {
  newPassword: string
}

/** GET /api/v1/admin/users */
export function listAdminUsers(
  params: AdminUserListParams,
): Promise<PageResult<AdminUserView>> {
  return request<PageResult<AdminUserView>>({
    url: '/v1/admin/users',
    method: 'GET',
    params,
  })
}

/** POST /api/v1/admin/users */
export function createAdminUser(payload: SaveUserPayload): Promise<AdminUserView> {
  return request<AdminUserView>({
    url: '/v1/admin/users',
    method: 'POST',
    data: payload,
  })
}

/** PUT /api/v1/admin/users/{id}；username/password 会被后端忽略 */
export function updateAdminUser(
  id: number,
  payload: SaveUserPayload,
): Promise<AdminUserView> {
  return request<AdminUserView>({
    url: `/v1/admin/users/${id}`,
    method: 'PUT',
    data: payload,
  })
}

/** PUT /api/v1/admin/users/{id}/status */
export function updateAdminUserStatus(id: number, status: number): Promise<void> {
  const payload: UpdateUserStatusPayload = { status }
  return request<void>({
    url: `/v1/admin/users/${id}/status`,
    method: 'PUT',
    data: payload,
  })
}

/** PUT /api/v1/admin/users/{id}/roles，body {roleIds}，全量替换 */
export function assignAdminUserRoles(id: number, roleIds: number[]): Promise<void> {
  const payload: AssignUserRolesPayload = { roleIds }
  return request<void>({
    url: `/v1/admin/users/${id}/roles`,
    method: 'PUT',
    data: payload,
  })
}

/** POST /api/v1/admin/users/{id}/reset-password，body {newPassword} */
export function resetAdminUserPassword(
  id: number,
  newPassword: string,
): Promise<void> {
  const payload: ResetPasswordPayload = { newPassword }
  return request<void>({
    url: `/v1/admin/users/${id}/reset-password`,
    method: 'POST',
    data: payload,
  })
}

/** DELETE /api/v1/admin/users/{id} */
export function deleteAdminUser(id: number): Promise<void> {
  return request<void>({
    url: `/v1/admin/users/${id}`,
    method: 'DELETE',
  })
}

// ------------------------------------------------------------
// 角色
// ------------------------------------------------------------

export interface AdminRoleListParams {
  /** 1平台 2商家 3骑手 */
  roleType?: number
  /** 0启用 1停用 */
  status?: number
  pageNum?: number
  pageSize?: number
}

/** SysRoleView.java */
export interface AdminRoleView {
  id: number
  tenantId: number
  roleCode: string
  roleName: string
  /** 1平台 2商家 3骑手 */
  roleType: number
  /** 1全部 2本租户 3本商家 4本门店 5本人 */
  dataScope: number
  sort: number
  /** 0启用 1停用 */
  status: number
  remark: string | null
  createdAt: string
}

/** SaveRoleRequest.java */
export interface SaveRolePayload {
  roleCode?: string | null
  roleName?: string | null
  roleType?: number | null
  dataScope?: number | null
  sort?: number | null
  status?: number | null
  remark?: string | null
}

/** GET /roles/{id}/menus 的 data；menuIds 包含按钮节点 */
export interface RoleMenuIds {
  menuIds: number[]
}

/** PUT /roles/{id}/menus 的 body */
export interface SaveRoleMenusPayload {
  menuIds: number[]
}

/** GET /api/v1/admin/roles */
export function listAdminRoles(
  params: AdminRoleListParams,
): Promise<PageResult<AdminRoleView>> {
  return request<PageResult<AdminRoleView>>({
    url: '/v1/admin/roles',
    method: 'GET',
    params,
  })
}

/** POST /api/v1/admin/roles */
export function createAdminRole(payload: SaveRolePayload): Promise<AdminRoleView> {
  return request<AdminRoleView>({
    url: '/v1/admin/roles',
    method: 'POST',
    data: payload,
  })
}

/** PUT /api/v1/admin/roles/{id} */
export function updateAdminRole(
  id: number,
  payload: SaveRolePayload,
): Promise<AdminRoleView> {
  return request<AdminRoleView>({
    url: `/v1/admin/roles/${id}`,
    method: 'PUT',
    data: payload,
  })
}

/** GET /api/v1/admin/roles/{id}/menus，返回 data.menuIds */
export async function getAdminRoleMenus(id: number): Promise<number[]> {
  const result = await request<RoleMenuIds>({
    url: `/v1/admin/roles/${id}/menus`,
    method: 'GET',
  })
  return result.menuIds ?? []
}

/** PUT /api/v1/admin/roles/{id}/menus，body {menuIds}，全量替换 */
export function assignAdminRoleMenus(
  id: number,
  menuIds: number[],
): Promise<void> {
  const payload: SaveRoleMenusPayload = { menuIds }
  return request<void>({
    url: `/v1/admin/roles/${id}/menus`,
    method: 'PUT',
    data: payload,
  })
}

// ------------------------------------------------------------
// 菜单
// ------------------------------------------------------------

/** SaveMenuRequest.java */
export interface SaveMenuPayload {
  /** 0 表示根级 */
  parentId?: number | null
  menuName: string
  /** 1目录 2菜单 3按钮；菜单管理页不新建按钮 */
  menuType: number
  path?: string | null
  component?: string | null
  perms?: string | null
  icon?: string | null
  sort?: number | null
  /** 0显示 1隐藏 */
  visible?: number | null
  /** 0启用 1停用 */
  status?: number | null
}

/**
 * GET /api/v1/admin/menus/tree
 * 不传 menuType 返回目录+菜单；传 3 后端会报 400。
 */
export function getAdminMenuTree(menuType?: 1 | 2): Promise<MenuNode[]> {
  return request<MenuNode[]>({
    url: '/v1/admin/menus/tree',
    method: 'GET',
    params: menuType === undefined ? undefined : { menuType },
  })
}

/** POST /api/v1/admin/menus */
export function createAdminMenu(payload: SaveMenuPayload): Promise<MenuNode> {
  return request<MenuNode>({
    url: '/v1/admin/menus',
    method: 'POST',
    data: payload,
  })
}

/** PUT /api/v1/admin/menus/{id}；字段传 null 表示保持原值，传空串表示清空 */
export function updateAdminMenu(
  id: number,
  payload: SaveMenuPayload,
): Promise<MenuNode> {
  return request<MenuNode>({
    url: `/v1/admin/menus/${id}`,
    method: 'PUT',
    data: payload,
  })
}

/** DELETE /api/v1/admin/menus/{id} */
export function deleteAdminMenu(id: number): Promise<void> {
  return request<void>({
    url: `/v1/admin/menus/${id}`,
    method: 'DELETE',
  })
}

// ------------------------------------------------------------
// 部门
// ------------------------------------------------------------

/** DeptNode.java；phone 已脱敏，children 恒为数组 */
export interface AdminDeptNode {
  id: number
  parentId: number
  ancestors: string
  deptName: string
  leader: string | null
  /** 已脱敏 */
  phone: string | null
  sort: number
  /** 0启用 1停用 */
  status: number
  children: AdminDeptNode[]
}

/** SaveDeptRequest.java */
export interface SaveDeptPayload {
  /** 0 表示根部门；更新时 null 表示保持原父级 */
  parentId?: number | null
  deptName: string
  leader?: string | null
  phone?: string | null
  sort?: number | null
  status?: number | null
}

/** GET /api/v1/admin/depts/tree */
export function getAdminDeptTree(status?: 0 | 1): Promise<AdminDeptNode[]> {
  return request<AdminDeptNode[]>({
    url: '/v1/admin/depts/tree',
    method: 'GET',
    params: status === undefined ? undefined : { status },
  })
}

/** POST /api/v1/admin/depts */
export function createAdminDept(payload: SaveDeptPayload): Promise<AdminDeptNode> {
  return request<AdminDeptNode>({
    url: '/v1/admin/depts',
    method: 'POST',
    data: payload,
  })
}

/** PUT /api/v1/admin/depts/{id} */
export function updateAdminDept(
  id: number,
  payload: SaveDeptPayload,
): Promise<AdminDeptNode> {
  return request<AdminDeptNode>({
    url: `/v1/admin/depts/${id}`,
    method: 'PUT',
    data: payload,
  })
}

/** DELETE /api/v1/admin/depts/{id} */
export function deleteAdminDept(id: number): Promise<void> {
  return request<void>({
    url: `/v1/admin/depts/${id}`,
    method: 'DELETE',
  })
}

// ------------------------------------------------------------
// 岗位
// ------------------------------------------------------------

export interface AdminPostListParams {
  /** 0启用 1停用 */
  status?: number
  /** 后端 like postCode 或 postName */
  keyword?: string
  pageNum?: number
  pageSize?: number
}

/** PostView.java */
export interface AdminPostView {
  id: number
  postCode: string
  postName: string
  sort: number
  /** 0启用 1停用 */
  status: number
  remark: string | null
  createdAt: string
}

/** SavePostRequest.java */
export interface SavePostPayload {
  postCode: string
  postName: string
  sort?: number | null
  status?: number | null
  remark?: string | null
}

/** GET /api/v1/admin/posts */
export function listAdminPosts(
  params: AdminPostListParams,
): Promise<PageResult<AdminPostView>> {
  return request<PageResult<AdminPostView>>({
    url: '/v1/admin/posts',
    method: 'GET',
    params,
  })
}

/** POST /api/v1/admin/posts */
export function createAdminPost(payload: SavePostPayload): Promise<AdminPostView> {
  return request<AdminPostView>({
    url: '/v1/admin/posts',
    method: 'POST',
    data: payload,
  })
}

/** PUT /api/v1/admin/posts/{id}；postCode 必传但服务端不会更新 */
export function updateAdminPost(
  id: number,
  payload: SavePostPayload,
): Promise<AdminPostView> {
  return request<AdminPostView>({
    url: `/v1/admin/posts/${id}`,
    method: 'PUT',
    data: payload,
  })
}

/** DELETE /api/v1/admin/posts/{id} */
export function deleteAdminPost(id: number): Promise<void> {
  return request<void>({
    url: `/v1/admin/posts/${id}`,
    method: 'DELETE',
  })
}
