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

// ------------------------------------------------------------
// 租户
// ------------------------------------------------------------

export interface AdminTenantListParams {
  /** 1平台 2商家 */
  tenantType?: number
  /** 0启用 1停用 2过期（派生） */
  status?: number
  keyword?: string
  pageNum?: number
  pageSize?: number
}

/** TenantView.java */
export interface AdminTenantView {
  id: number
  tenantCode: string
  tenantName: string
  /** 1平台 2商家 */
  tenantType: number
  merchantId: number | null
  packageId: number | null
  /** 0启用 1停用 2过期（由 expireAt 派生） */
  status: number
  expireAt: string | null
  contactName: string | null
  /** 已脱敏 */
  contactPhone: string | null
  createdAt: string
  updatedAt: string
}

/** SaveTenantRequest.java；更新时 tenantCode 被服务端忽略 */
export interface SaveTenantPayload {
  tenantCode: string
  tenantName: string
  tenantType: number
  merchantId?: number | null
  packageId?: number | null
  /** yyyy-MM-dd HH:mm:ss */
  expireAt?: string | null
  contactName?: string | null
  contactPhone?: string | null
}

export interface UpdateTenantStatusPayload {
  status: 0 | 1
}

/** GET /api/v1/admin/tenants */
export function listAdminTenants(
  params: AdminTenantListParams,
): Promise<PageResult<AdminTenantView>> {
  return request<PageResult<AdminTenantView>>({
    url: '/v1/admin/tenants',
    method: 'GET',
    params,
  })
}

/** POST /api/v1/admin/tenants */
export function createAdminTenant(
  payload: SaveTenantPayload,
): Promise<AdminTenantView> {
  return request<AdminTenantView>({
    url: '/v1/admin/tenants',
    method: 'POST',
    data: payload,
  })
}

/** PUT /api/v1/admin/tenants/{id} */
export function updateAdminTenant(
  id: number,
  payload: SaveTenantPayload,
): Promise<AdminTenantView> {
  return request<AdminTenantView>({
    url: `/v1/admin/tenants/${id}`,
    method: 'PUT',
    data: payload,
  })
}

/** PUT /api/v1/admin/tenants/{id}/status */
export function updateAdminTenantStatus(id: number, status: 0 | 1): Promise<void> {
  const payload: UpdateTenantStatusPayload = { status }
  return request<void>({
    url: `/v1/admin/tenants/${id}/status`,
    method: 'PUT',
    data: payload,
  })
}

// ------------------------------------------------------------
// 套餐
// ------------------------------------------------------------

/** PackageView.java */
export interface AdminPackageView {
  id: number
  packageCode: string
  packageName: string
  /** null 表示不限制菜单范围 */
  menuIds: number[] | null
  /** 0 表示不限制 */
  maxShop: number
  /** 0 表示不限制 */
  maxUser: number
  /** 单位：分 */
  price: number
  /** 0 表示长期有效 */
  validDays: number
  /** 0启用 1停用 */
  status: number
  remark: string | null
  createdAt: string
  updatedAt: string
}

/** SavePackageRequest.java；更新时 packageCode 被服务端忽略 */
export interface SavePackagePayload {
  packageCode: string
  packageName: string
  /** 新增 null=不限制；更新 null=保持原值，[]=清空 */
  menuIds?: number[] | null
  maxShop?: number | null
  maxUser?: number | null
  price?: number | null
  validDays?: number | null
  status?: number | null
  remark?: string | null
}

/** GET /api/v1/admin/packages，不分页 */
export function listAdminPackages(): Promise<AdminPackageView[]> {
  return request<AdminPackageView[]>({
    url: '/v1/admin/packages',
    method: 'GET',
  })
}

/** POST /api/v1/admin/packages */
export function createAdminPackage(
  payload: SavePackagePayload,
): Promise<AdminPackageView> {
  return request<AdminPackageView>({
    url: '/v1/admin/packages',
    method: 'POST',
    data: payload,
  })
}

/** PUT /api/v1/admin/packages/{id} */
export function updateAdminPackage(
  id: number,
  payload: SavePackagePayload,
): Promise<AdminPackageView> {
  return request<AdminPackageView>({
    url: `/v1/admin/packages/${id}`,
    method: 'PUT',
    data: payload,
  })
}

// ------------------------------------------------------------
// 字典
// ------------------------------------------------------------

export interface AdminDictTypeListParams {
  /** 后端对 dictType/dictName 做模糊匹配 */
  dictType?: string
  status?: number
  pageNum?: number
  pageSize?: number
}

/** DictTypeView.java */
export interface AdminDictTypeView {
  id: number
  dictType: string
  dictName: string
  /** 0启用 1停用 */
  status: number
  remark: string | null
  createdAt: string
  updatedAt: string
}

/** SaveDictRequest.java（类型）；更新时 dictType 禁止变更 */
export interface SaveDictTypePayload {
  dictType: string
  dictName: string
  status?: number | null
  remark?: string | null
}

/** DictItemView.java */
export interface AdminDictItemView {
  id: number
  dictType: string
  dictLabel: string
  dictValue: string
  dictSort: number
  cssClass: string | null
  /** 1 默认项 */
  isDefault: number
  /** 0启用 1停用 */
  status: number
  remark: string | null
  createdAt: string
  updatedAt: string
}

/** SaveDictRequest.DictItemRequest.java；更新时 dictType 禁止变更 */
export interface SaveDictItemPayload {
  dictType: string
  dictLabel: string
  dictValue: string
  dictSort?: number | null
  cssClass?: string | null
  isDefault?: number | null
  status?: number | null
  remark?: string | null
}

/** GET /api/v1/admin/dicts */
export function listAdminDictTypes(
  params: AdminDictTypeListParams,
): Promise<PageResult<AdminDictTypeView>> {
  return request<PageResult<AdminDictTypeView>>({
    url: '/v1/admin/dicts',
    method: 'GET',
    params,
  })
}

/** POST /api/v1/admin/dicts */
export function createAdminDictType(
  payload: SaveDictTypePayload,
): Promise<AdminDictTypeView> {
  return request<AdminDictTypeView>({
    url: '/v1/admin/dicts',
    method: 'POST',
    data: payload,
  })
}

/** PUT /api/v1/admin/dicts/{id} */
export function updateAdminDictType(
  id: number,
  payload: SaveDictTypePayload,
): Promise<AdminDictTypeView> {
  return request<AdminDictTypeView>({
    url: `/v1/admin/dicts/${id}`,
    method: 'PUT',
    data: payload,
  })
}

/** DELETE /api/v1/admin/dicts/{id}，级联逻辑删除字典项 */
export function deleteAdminDictType(id: number): Promise<void> {
  return request<void>({
    url: `/v1/admin/dicts/${id}`,
    method: 'DELETE',
  })
}

/** GET /api/v1/admin/dicts/{dictType}/items，只返回启用项且不分页 */
export function listAdminDictItems(dictType: string): Promise<AdminDictItemView[]> {
  return request<AdminDictItemView[]>({
    url: `/v1/admin/dicts/${encodeURIComponent(dictType)}/items`,
    method: 'GET',
  })
}

/** POST /api/v1/admin/dicts/items */
export function createAdminDictItem(
  payload: SaveDictItemPayload,
): Promise<AdminDictItemView> {
  return request<AdminDictItemView>({
    url: '/v1/admin/dicts/items',
    method: 'POST',
    data: payload,
  })
}

/** PUT /api/v1/admin/dicts/items/{id} */
export function updateAdminDictItem(
  id: number,
  payload: SaveDictItemPayload,
): Promise<AdminDictItemView> {
  return request<AdminDictItemView>({
    url: `/v1/admin/dicts/items/${id}`,
    method: 'PUT',
    data: payload,
  })
}

/** DELETE /api/v1/admin/dicts/items/{id} */
export function deleteAdminDictItem(id: number): Promise<void> {
  return request<void>({
    url: `/v1/admin/dicts/items/${id}`,
    method: 'DELETE',
  })
}

// ------------------------------------------------------------
// 日志
// ------------------------------------------------------------

export interface AdminOperLogListParams {
  /** 后端精确匹配 */
  module?: string
  operatorId?: number
  /** 0 成功，非 0 为具体错误码 */
  resultCode?: number
  /** yyyy-MM-dd 或 yyyy-MM-dd HH:mm:ss */
  dateFrom?: string
  dateTo?: string
  pageNum?: number
  pageSize?: number
}

/** OperLogView.java；无返回结果字段 */
export interface AdminOperLogView {
  id: number
  traceId: string | null
  module: string
  action: string
  method: string | null
  requestUri: string | null
  requestMethod: string | null
  requestParams: string | null
  operatorId: number | null
  operatorName: string | null
  operatorType: string | null
  ip: string | null
  ua: string | null
  /** 0 成功，非 0 失败 */
  resultCode: number
  errorMsg: string | null
  costMs: number
  createdAt: string
}

export interface AdminLoginLogListParams {
  username?: string
  /** 1成功 0失败 */
  success?: number
  dateFrom?: string
  dateTo?: string
  pageNum?: number
  pageSize?: number
}

/** LoginLogView.java */
export interface AdminLoginLogView {
  id: number
  userId: number | null
  username: string | null
  /** 1密码 2验证码 3mock */
  loginType: number
  /** 1成功 0失败 */
  success: number
  failReason: string | null
  ip: string | null
  ua: string | null
  createdAt: string
}

/** GET /api/v1/admin/logs/oper */
export function listAdminOperLogs(
  params: AdminOperLogListParams,
): Promise<PageResult<AdminOperLogView>> {
  return request<PageResult<AdminOperLogView>>({
    url: '/v1/admin/logs/oper',
    method: 'GET',
    params,
  })
}

/** GET /api/v1/admin/logs/login */
export function listAdminLoginLogs(
  params: AdminLoginLogListParams,
): Promise<PageResult<AdminLoginLogView>> {
  return request<PageResult<AdminLoginLogView>>({
    url: '/v1/admin/logs/login',
    method: 'GET',
    params,
  })
}

// ------------------------------------------------------------
// 邮件
// ------------------------------------------------------------

/** MailAccountView.java；不返回密码 */
export interface AdminMailAccountView {
  id: number
  accountName: string
  host: string
  port: number
  username: string
  fromAddr: string
  /** 0关闭 1 SSL */
  ssl: number
  /** 0启用 1停用 */
  status: number
  createdAt: string
  updatedAt: string
}

/** SaveMailAccountRequest.java；password 更新留空保持 */
export interface SaveMailAccountPayload {
  accountName: string
  host: string
  port: number
  username: string
  password?: string | null
  fromAddr: string
  ssl: number
  status: number
}

/** MailTestRequest.java */
export interface SendTestMailPayload {
  accountId: number
  toAddr: string
}

export interface AdminMailLogListParams {
  /** 0待发 1成功 2失败 */
  status?: number
  pageNum?: number
  pageSize?: number
}

/** MailLogView.java；不返回正文 content */
export interface AdminMailLogView {
  id: number
  templateCode: string | null
  toAddr: string
  subject: string | null
  /** 0待发 1成功 2失败 */
  status: number
  errorMsg: string | null
  sentAt: string | null
  createdAt: string
}

/** GET /api/v1/admin/mails/accounts，不分页 */
export function listAdminMailAccounts(): Promise<AdminMailAccountView[]> {
  return request<AdminMailAccountView[]>({
    url: '/v1/admin/mails/accounts',
    method: 'GET',
  })
}

/** POST /api/v1/admin/mails/accounts */
export function createAdminMailAccount(
  payload: SaveMailAccountPayload,
): Promise<AdminMailAccountView> {
  return request<AdminMailAccountView>({
    url: '/v1/admin/mails/accounts',
    method: 'POST',
    data: payload,
  })
}

/** PUT /api/v1/admin/mails/accounts/{id} */
export function updateAdminMailAccount(
  id: number,
  payload: SaveMailAccountPayload,
): Promise<AdminMailAccountView> {
  return request<AdminMailAccountView>({
    url: `/v1/admin/mails/accounts/${id}`,
    method: 'PUT',
    data: payload,
  })
}

/** POST /api/v1/admin/mails/test；主题/正文由后端固定 */
export function sendAdminTestMail(payload: SendTestMailPayload): Promise<void> {
  return request<void>({
    url: '/v1/admin/mails/test',
    method: 'POST',
    data: payload,
  })
}

/** GET /api/v1/admin/mails/logs */
export function listAdminMailLogs(
  params: AdminMailLogListParams,
): Promise<PageResult<AdminMailLogView>> {
  return request<PageResult<AdminMailLogView>>({
    url: '/v1/admin/mails/logs',
    method: 'GET',
    params,
  })
}

// ------------------------------------------------------------
// 公告
// ------------------------------------------------------------

export interface AdminNoticeListParams {
  /** 0草稿 1已发布 2已撤回 */
  status?: number
  /** 1公告 2通知 */
  noticeType?: number
  /** 1全部 2平台 3商家 4骑手 */
  targetType?: number
  keyword?: string
  pageNum?: number
  pageSize?: number
}

/** NoticeView.java；无发布人字段 */
export interface AdminNoticeView {
  id: number
  title: string
  content: string
  /** 1公告 2通知 */
  noticeType: number
  /** 1全部 2平台 3商家 4骑手 */
  targetType: number
  /** 0草稿 1已发布 2已撤回 */
  status: number
  publishAt: string | null
  expireAt: string | null
  readCount: number
  createdAt: string
  updatedAt: string
}

/** SaveNoticeRequest.java；创建固定草稿，status 不在该 DTO 内 */
export interface SaveNoticePayload {
  title: string
  content: string
  noticeType: number
  targetType: number
  /** yyyy-MM-dd HH:mm:ss */
  publishAt?: string | null
  expireAt?: string | null
}

/** NoticePublishRequest.java；1发布 2撤回 */
export interface PublishNoticePayload {
  status: 1 | 2
}

/** GET /api/v1/admin/notices */
export function listAdminNotices(
  params: AdminNoticeListParams,
): Promise<PageResult<AdminNoticeView>> {
  return request<PageResult<AdminNoticeView>>({
    url: '/v1/admin/notices',
    method: 'GET',
    params,
  })
}

/** POST /api/v1/admin/notices */
export function createAdminNotice(
  payload: SaveNoticePayload,
): Promise<AdminNoticeView> {
  return request<AdminNoticeView>({
    url: '/v1/admin/notices',
    method: 'POST',
    data: payload,
  })
}

/** PUT /api/v1/admin/notices/{id} */
export function updateAdminNotice(
  id: number,
  payload: SaveNoticePayload,
): Promise<AdminNoticeView> {
  return request<AdminNoticeView>({
    url: `/v1/admin/notices/${id}`,
    method: 'PUT',
    data: payload,
  })
}

/** PUT /api/v1/admin/notices/{id}/publish，body {status} */
export function publishAdminNotice(id: number, status: 1 | 2): Promise<void> {
  const payload: PublishNoticePayload = { status }
  return request<void>({
    url: `/v1/admin/notices/${id}/publish`,
    method: 'PUT',
    data: payload,
  })
}
