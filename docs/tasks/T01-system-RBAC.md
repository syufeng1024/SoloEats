# T01 system-RBAC（账号 · 角色 · 菜单 · 登录鉴权落地）

> 优先级：**P0** · 所属模块：`solo-eats-system`（新建）+ `solo-eats-app` + `solo-eats-common`  
> 分三段执行：**阶段A（登录打通）→ 阶段B（用户/角色/菜单 CRUD）→ 阶段C（权限注解）**  
> 每个阶段单独编译通过、单独 commit。

---

## 目标

**一句话**：把当前 `@Profile("local")` 的假登录换成真实的「账号 + 密码 + 角色 + 菜单权限」体系，让平台后台、商家中心、骑手端第一次拥有"谁在操作"的可信来源。

**完成后系统能做什么**

1. 用 `admin / Admin@123` 登录拿到 JWT，token 内携带 `userId / tenantId / merchantId / shopId / riderId / roles`。
2. 平台超管能创建后台用户、分配角色、按角色勾选菜单，被分配的人能登录并看到自己的菜单树（前端可直接渲染路由）。
3. 接口能声明 `@RequirePermission("system:user:create")`，无权限返回 `10004 AUTH_FORBIDDEN`。
4. **旧契约不破**：`POST /api/v1/auth/login` 传 `{role, userId, merchantId, shopId, riderId}` 仍能登录（受开关 `app.auth.mock-login-enabled` 控制，默认 local=true）。

---

## 规格书依据

| 章节         | 原文要点                                                                           |
| ---------- | ------------------------------------------------------------------------------ |
| 3.1 角色清单   | 平台 8 个后台角色（超管/招商/商品运营/营销运营/调度/客服/财务/数据分析）+ 商家管理员/店长/店员 + 消费者 + 骑手              |
| 3.2 权限模型   | 用户 N:N 角色，角色 N:N 菜单；数据权限范围（全平台 / 本租户 / 本商家 / 本门店 / 本人）                         |
| 4.12 系统管理  | 「用户管理：后台用户、部门关联、分配角色、重置密码；角色与权限：角色、菜单权限、按钮权限、数据权限范围；菜单管理：菜单树、路由/组件/权限标识、排序、显隐」 |
| 2.5 核心领域模型 | 「账号权限：平台用户、角色、菜单、租户、商家账号、骑手账号；用户 N:N 角色；账号归属租户/商家/门店」                          |
| 9.3 安全     | 「后台越权重点：商家只能看到自己门店数据，骑手只能看到自己的配送单」                                             |
| 9.6 接口约定   | 「鉴权：Authorization: Bearer <token>；后台与商家端区分 token 受众；错误码按模块分段（1xxxx 系统）」        |

---

## 前置依赖

| 依赖             | 说明                                                                                                                                                                                                                                                                        |
| -------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| 无前置任务          | 本卡是全部任务的地基                                                                                                                                                                                                                                                                |
| 既有类（只读理解，不改语义） | `solo-eats-common/.../auth/JwtUtils.java`、`JwtAuthFilter.java`、`AuthContext.java`、`UserPrincipal.java`、`Roles.java`、`RequireRole.java`、`RoleAuthInterceptor.java`、`WebMvcConfig.java`、`common/api/ApiResponse.java`、`common/exception/ErrorCode.java`、`BizException.java` |
| 既有类（要改）        | `solo-eats-app/.../controller/AuthController.java`、`.../dto/LoginRequest.java`、`.../vo/LoginResponse.java`、`.../security/PermissionGuard.java`                                                                                                                            |
| 既有表            | 无（本卡首次建账号表）                                                                                                                                                                                                                                                               |
| 现状事实           | 当前**没有任何一张账号表**，`AuthController` 直接按 `role` 拼 `UserPrincipal` 签发 JWT；`Roles` 只有 4 个粗粒度角色码                                                                                                                                                                                 |

---

## 数据模型

迁移脚本：`solo-eats-app/src/main/resources/db/migration/V4__system_rbac.sql`

### t_sys_user 后台/商家/骑手账号

| 字段                                | 类型                                 | 说明                                                    |
| --------------------------------- | ---------------------------------- | ----------------------------------------------------- |
| id                                | bigint unsigned PK                 |                                                       |
| tenant_id                         | bigint unsigned NOT NULL DEFAULT 1 | 租户                                                    |
| username                          | varchar(64) NOT NULL               | 登录名                                                   |
| password_hash                     | varchar(100) NOT NULL              | `bcrypt`（前缀 `$2a$`）                                   |
| nickname                          | varchar(64)                        |                                                       |
| phone                             | varchar(20)                        | 加密存储，唯一                                               |
| email                             | varchar(128)                       |                                                       |
| avatar_url                        | varchar(512)                       |                                                       |
| user_type                         | tinyint NOT NULL                   | 1平台 2商家 3骑手（**不含 C 端消费者**，消费者在 `t_member_user`，见 T06） |
| merchant_id                       | bigint unsigned NULL               | 商家账号绑定的商家                                             |
| shop_id                           | bigint unsigned NULL               | 门店账号绑定的门店                                             |
| rider_id                          | bigint unsigned NULL               | 骑手账号绑定的骑手                                             |
| dept_id                           | bigint unsigned NULL               | 部门（T02 建表后启用）                                         |
| post_id                           | bigint unsigned NULL               | 岗位（T02）                                               |
| data_scope                        | tinyint NOT NULL DEFAULT 5         | 1全部 2本租户 3本商家 4本门店 5本人                                |
| status                            | tinyint NOT NULL DEFAULT 0         | 0启用 1停用                                               |
| last_login_at                     | datetime(3)                        |                                                       |
| last_login_ip                     | varchar(64)                        |                                                       |
| version                           | int unsigned NOT NULL DEFAULT 0    |                                                       |
| created_at / updated_at / deleted |                                    | 审计字段                                                  |

索引：`uk_tenant_username(tenant_id, username)`、`uk_tenant_phone(tenant_id, phone)`、`idx_merchant(merchant_id)`、`idx_shop(shop_id)`、`idx_rider(rider_id)`、`idx_type_status(user_type, status)`

### t_sys_role 角色

| 字段                                | 类型                                 | 说明                                                     |
| --------------------------------- | ---------------------------------- | ------------------------------------------------------ |
| id                                | bigint unsigned PK                 |                                                        |
| tenant_id                         | bigint unsigned NOT NULL DEFAULT 1 |                                                        |
| role_code                         | varchar(64) NOT NULL               | 如 `ADMIN`、`ADMIN_OPERATION`、`MERCHANT_MANAGER`、`RIDER` |
| role_name                         | varchar(64) NOT NULL               |                                                        |
| role_type                         | tinyint NOT NULL                   | 1平台 2商家 3骑手                                            |
| data_scope                        | tinyint NOT NULL DEFAULT 5         | 同 user                                                 |
| sort                              | int NOT NULL DEFAULT 0             |                                                        |
| status                            | tinyint NOT NULL DEFAULT 0         |                                                        |
| remark                            | varchar(255)                       |                                                        |
| created_at / updated_at / deleted |                                    |                                                        |

索引：`uk_tenant_role_code(tenant_id, role_code)`

### t_sys_menu 菜单/权限

| 字段                                | 类型                                 | 说明                          |
| --------------------------------- | ---------------------------------- | --------------------------- |
| id                                | bigint unsigned PK                 |                             |
| tenant_id                         | bigint unsigned NOT NULL DEFAULT 1 |                             |
| parent_id                         | bigint unsigned NOT NULL DEFAULT 0 | 0 为根                        |
| menu_name                         | varchar(64) NOT NULL               |                             |
| menu_type                         | tinyint NOT NULL                   | 1目录 2菜单 3按钮                 |
| path                              | varchar(200)                       | 前端路由，如 `/system/user`       |
| component                         | varchar(200)                       | 组件路径                        |
| perms                             | varchar(128)                       | 权限标识，如 `system:user:create` |
| icon                              | varchar(64)                        |                             |
| sort                              | int NOT NULL DEFAULT 0             |                             |
| visible                           | tinyint NOT NULL DEFAULT 0         | 0显示 1隐藏                     |
| status                            | tinyint NOT NULL DEFAULT 0         |                             |
| created_at / updated_at / deleted |                                    |                             |

索引：`idx_parent(parent_id, sort)`、`idx_perms(perms)`

### t_sys_user_role / t_sys_role_menu（关联表）

| 表                 | 字段                                          | 索引                               |
| ----------------- | ------------------------------------------- | -------------------------------- |
| `t_sys_user_role` | id, tenant_id, user_id, role_id, created_at | `uk_user_role(user_id, role_id)` |
| `t_sys_role_menu` | id, tenant_id, role_id, menu_id, created_at | `uk_role_menu(role_id, menu_id)` |

### 初始化数据（同脚本内 INSERT）

| 数据 | 内容                                                                                                                                                                                                             |
| -- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| 角色 | `ADMIN`(超管)、`ADMIN_OPERATION`(招商)、`ADMIN_PRODUCT`(商品运营)、`ADMIN_MARKETING`(营销运营)、`ADMIN_DISPATCH`(调度)、`ADMIN_SERVICE`(客服)、`ADMIN_FINANCE`(财务)、`ADMIN_ANALYST`(数据分析)、`MERCHANT_MANAGER`、`MERCHANT_CLERK`、`RIDER` |
| 用户 | `admin / Admin@123`（user_type=1，绑定 ADMIN）；`merchant1 / Merchant@123`（user_type=2, merchant_id=1, shop_id=1）；`rider1 / Rider@123`（user_type=3, rider_id=1）                                                      |
| 菜单 | 按规格书 4.0 导航结构插入一级目录（工作台/商家招商/商品中心/订单中心/配送中心/营销中心/用户中心/资金中心/内容运营/客服与工单/数据报表/系统管理）及其 `system:*` 子权限                                                                                                              |

> **种子密码必须用 bcrypt 哈希字面量写死**（`$2a$10$...`），不得在 SQL 里做函数运算。

---

## 接口清单

> 统一前缀 `/api/v1`，统一 `ApiResponse<T>` 包装。`Auth*` 为免登录白名单（`/api/v1/auth/**`）。

| #  | 方法              | 路径                                               | 请求要点                                                                                               | 响应要点                                                                                       | 权限                                     |
| -- | --------------- | ------------------------------------------------ | -------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------ | -------------------------------------- |
| 1  | POST            | `/api/v1/auth/login`                             | `{username,password,tokenType}` **或** 旧格式 `{role,userId,merchantId,shopId,riderId}`                | `{token,tokenType,expiresIn,roles,permissions}`                                            | 白名单，限流 10 次/分/IP                       |
| 2  | GET             | `/api/v1/auth/me`                                | Header `Authorization`                                                                             | `{userId,username,nickname,userType,tenantId,merchantId,shopId,riderId,roles,permissions}` | 登录即可                                   |
| 3  | GET             | `/api/v1/auth/menus`                             | —                                                                                                  | 当前用户可见**菜单树**（目录/菜单，不含按钮）                                                                  | 登录即可                                   |
| 4  | POST            | `/api/v1/auth/logout`                            | —                                                                                                  | 空                                                                                          | 登录即可；写 Redis 黑名单 `jwt:blacklist:{jti}` |
| 5  | POST            | `/api/v1/auth/password`                          | `{oldPassword,newPassword}`                                                                        | 空                                                                                          | 登录即可                                   |
| 6  | GET             | `/api/v1/admin/users`                            | `userType,merchantId,shopId,status,keyword,pageNum,pageSize`                                       | `PageResult<SysUserView>`                                                                  | `system:user:list`                     |
| 7  | POST            | `/api/v1/admin/users`                            | `{username,password,nickname,phone,userType,merchantId,shopId,riderId,deptId,dataScope,roleIds[]}` | `SysUserView`                                                                              | `system:user:create`                   |
| 8  | PUT             | `/api/v1/admin/users/{id}`                       | 同上（不含 username/password）                                                                           | `SysUserView`                                                                              | `system:user:update`                   |
| 9  | PUT             | `/api/v1/admin/users/{id}/status`                | `{status}` 0启用 1停用                                                                                 | 空                                                                                          | `system:user:update`                   |
| 10 | PUT             | `/api/v1/admin/users/{id}/roles`                 | `{roleIds:[]}`（全量替换）                                                                               | 空                                                                                          | `system:user:assign`                   |
| 11 | POST            | `/api/v1/admin/users/{id}/reset-password`        | `{newPassword}`                                                                                    | 空                                                                                          | `system:user:reset`                    |
| 12 | DELETE          | `/api/v1/admin/users/{id}`                       | 逻辑删除                                                                                               | 空                                                                                          | `system:user:delete`                   |
| 13 | GET             | `/api/v1/admin/roles`                            | `roleType,status,pageNum,pageSize`                                                                 | `PageResult<SysRoleView>`                                                                  | `system:role:list`                     |
| 14 | POST/PUT        | `/api/v1/admin/roles`、`/api/v1/admin/roles/{id}` | `{roleCode,roleName,roleType,dataScope,sort,status,remark}`                                        | `SysRoleView`                                                                              | `system:role:create/update`            |
| 15 | GET             | `/api/v1/admin/roles/{id}/menus`                 | —                                                                                                  | `{menuIds:[]}`                                                                             | `system:role:list`                     |
| 16 | PUT             | `/api/v1/admin/roles/{id}/menus`                 | `{menuIds:[]}`（全量替换）                                                                               | 空                                                                                          | `system:role:assign`                   |
| 17 | GET             | `/api/v1/admin/menus/tree`                       | `menuType?`                                                                                        | `MenuNode[]`（含 `children`）                                                                 | `system:menu:list`                     |
| 18 | POST/PUT/DELETE | `/api/v1/admin/menus`、`/{id}`                    | `{parentId,menuName,menuType,path,component,perms,icon,sort,visible,status}`                       | `MenuNode`                                                                                 | `system:menu:*`                        |

**错误码复用**：`10003 AUTH_UNAUTHORIZED`（无 token/过期/黑名单）、`10004 AUTH_FORBIDDEN`（角色或权限不足）、`10001 PARAM_INVALID`（账号密码为空）。  
**新增错误码**（追加到 `ErrorCode`，不得改已有码值）：`10006 AUTH_ACCOUNT_LOCKED`、`10007 AUTH_BAD_CREDENTIALS`、`10008 AUTH_ACCOUNT_DISABLED`。

---

## 需要新增/修改的文件

### 阶段A（18 个，登录打通）

| 类型 | 路径                                                                                                                                                 |
| -- | -------------------------------------------------------------------------------------------------------------------------------------------------- |
| 改  | `pom.xml`（`<modules>` 增 `solo-eats-system`；`<dependencyManagement>` 增坐标）                                                                           |
| 新  | `solo-eats-system/pom.xml`（依赖 `solo-eats-common`、`mybatis-plus-spring-boot3-starter`、`lombok`、`spring-security-crypto`、`spring-boot-starter-test`） |
| 改  | `solo-eats-app/pom.xml`（依赖 `solo-eats-system`）                                                                                                     |
| 新  | `solo-eats-app/src/main/resources/db/migration/V4__system_rbac.sql`                                                                                |
| 新  | `solo-eats-system/src/main/java/com/soloeats/system/domain/entity/SysUser.java`                                                                    |
| 新  | `solo-eats-system/src/main/java/com/soloeats/system/domain/entity/SysRole.java`                                                                    |
| 新  | `solo-eats-system/src/main/java/com/soloeats/system/domain/entity/SysMenu.java`                                                                    |
| 新  | `solo-eats-system/src/main/java/com/soloeats/system/mapper/SysUserMapper.java`                                                                     |
| 新  | `solo-eats-system/src/main/java/com/soloeats/system/mapper/SysRoleMapper.java`                                                                     |
| 新  | `solo-eats-system/src/main/java/com/soloeats/system/mapper/SysMenuMapper.java`                                                                     |
| 新  | `solo-eats-system/src/main/java/com/soloeats/system/support/PasswordHasher.java`（`BCryptPasswordEncoder` 封装）                                       |
| 新  | `solo-eats-system/src/main/java/com/soloeats/system/service/AuthService.java`                                                                      |
| 新  | `solo-eats-system/src/main/java/com/soloeats/system/service/impl/AuthServiceImpl.java`                                                             |
| 新  | `solo-eats-system/src/main/java/com/soloeats/system/service/command/PasswordLoginCommand.java`                                                     |
| 新  | `solo-eats-system/src/main/java/com/soloeats/system/service/view/LoginResult.java`                                                                 |
| 改  | `solo-eats-app/src/main/java/com/soloeats/app/dto/LoginRequest.java`（加可选 `username`、`password`；`role` 去掉 `@NotBlank`，改为两者互斥校验）                     |
| 改  | `solo-eats-app/src/main/java/com/soloeats/app/controller/AuthController.java`（去掉 `@Profile("local")` 或改为双模式；委托 `AuthService`）                      |
| 改  | `solo-eats-app/src/main/java/com/soloeats/app/vo/LoginResponse.java`（加 `permissions` 字段，保持 `token/tokenType/expiresIn/roles` 不变）                   |
| 改  | `solo-eats-common/src/main/java/com/soloeats/common/exception/ErrorCode.java`（追加 3 个认证码）                                                           |
| 新  | `solo-eats-system/src/test/java/com/soloeats/system/service/impl/AuthServiceImplTest.java`                                                         |

### 阶段B（18 个，CRUD）

| 类型 | 路径                                                                                                                    |
| -- | --------------------------------------------------------------------------------------------------------------------- |
| 新  | `solo-eats-system/src/main/java/com/soloeats/system/domain/entity/SysUserRole.java`                                   |
| 新  | `solo-eats-system/src/main/java/com/soloeats/system/domain/entity/SysRoleMenu.java`                                   |
| 新  | `solo-eats-system/src/main/java/com/soloeats/system/mapper/SysUserRoleMapper.java`                                    |
| 新  | `solo-eats-system/src/main/java/com/soloeats/system/mapper/SysRoleMenuMapper.java`                                    |
| 新  | `solo-eats-system/src/main/java/com/soloeats/system/service/SysUserService.java` + `impl/SysUserServiceImpl.java`     |
| 新  | `solo-eats-system/src/main/java/com/soloeats/system/service/SysRoleService.java` + `impl/SysRoleServiceImpl.java`     |
| 新  | `solo-eats-system/src/main/java/com/soloeats/system/service/SysMenuService.java` + `impl/SysMenuServiceImpl.java`     |
| 新  | `.../service/command/CreateUserCommand.java`、`UpdateUserCommand.java`、`SaveRoleCommand.java`、`SaveMenuCommand.java`   |
| 新  | `.../service/view/SysUserView.java`、`SysRoleView.java`、`MenuNode.java`                                                |
| 新  | `solo-eats-app/src/main/java/com/soloeats/app/controller/AdminUserController.java`                                    |
| 新  | `solo-eats-app/src/main/java/com/soloeats/app/controller/AdminRoleController.java`                                    |
| 新  | `solo-eats-app/src/main/java/com/soloeats/app/controller/AdminMenuController.java`                                    |
| 新  | `solo-eats-app/src/main/java/com/soloeats/app/dto/SaveUserRequest.java`、`SaveRoleRequest.java`、`SaveMenuRequest.java` |


### 阶段C（9 个，权限注解）

| 类型 | 路径                                                                                                                           |
| -- | ---------------------------------------------------------------------------------------------------------------------------- |
| 新  | `solo-eats-common/src/main/java/com/soloeats/common/auth/RequirePermission.java`                                             |
| 新  | `solo-eats-common/src/main/java/com/soloeats/common/auth/PermissionInterceptor.java`                                         |
| 新  | `solo-eats-common/src/main/java/com/soloeats/common/auth/PermissionCache.java`（Redis，key `perm:user:{userId}`，TTL 30min）     |
| 改  | `solo-eats-common/src/main/java/com/soloeats/common/auth/WebMvcConfig.java`（注册新拦截器，排在 `RoleAuthInterceptor` 之后）              |
| 改  | `solo-eats-common/src/main/java/com/soloeats/common/auth/UserPrincipal.java`（加 `tenantId`、`username`）                        |
| 改  | `solo-eats-common/src/main/java/com/soloeats/common/auth/JwtUtils.java`（签发/解析新 claim）                                        |
| 改  | `solo-eats-common/src/main/java/com/soloeats/common/auth/JwtAuthFilter.java`（黑名单校验）                                          |
| 改  | `solo-eats-app/src/main/java/com/soloeats/app/security/PermissionGuard.java`（加 `requireTenantId()`、`assertSameTenant(Long)`） |
| 改  | `solo-eats-app/src/main/java/com/soloeats/app/controller/AdminUserController.java` 等（在方法上加 `@RequirePermission`）             |

---

## 关键实现约束

| 项            | 约束                                                                                                                                                                           |
| ------------ | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **契约兼容**     | `POST /api/v1/auth/login` 的旧请求体 `{role,userId,merchantId,shopId,riderId}` **必须继续可用**；由配置 `app.auth.mock-login-enabled`（local=true，其他=false）守卫；关闭时该分支返回 `10001 PARAM_INVALID` |
| **密码**       | 只存 `bcrypt`（强度 10），**禁止** MD5/SHA 裸哈希；`password_hash` 禁止出现在任何 VO / 日志中                                                                                                       |
| **JWT**      | 沿用 `JwtUtils` HS256；新 claim：`tenantId`、`username`；**权限码不放进 JWT**（可能上百条），登录时写 Redis `perm:user:{userId}`                                                                      |
| **token 受众** | 平台后台 / 商家端 / 骑手端 / 用户端共用一套 token，靠 `roles` + `userType` 区分；不引入多套签发器                                                                                                          |
| **数据权限**     | `data_scope` 本次只落库不强制生效；强制生效在 T02 完成后按 `tenant_id/merchant_id/shop_id` 收紧。**但商家/骑手越权校验必须在本卡就用 `PermissionGuard` 兜住**                                                         |
| **事务边界**     | `AuthServiceImpl.login()` 不加事务（只读 + 缓存写）；`sysUserService.create()` 内「插用户 + 插 user_role」必须在同一 `@Transactional`                                                                |
| **唯一性**      | 用户名/手机号唯一性靠**数据库唯一键**兜底，先查后插只做友好提示；捕获 `DuplicateKeyException` 转 `10001 PARAM_INVALID`                                                                                        |
| **逻辑删除**     | `t_sys_user` 用 `deleted`；删除用户同时软删 `t_sys_user_role` 关联（同一事务）                                                                                                                 |
| **租户**       | 阶段A 全部写死 `tenant_id = 1`；阶段B 起由 `TenantContext`（T02）填充。**本卡不建 `TenantContext`**，只留 `TODO(T02)` 注释                                                                            |
| **幂等/限流**    | 登录接口已有 `@RateLimit(key="auth-login", 10/60s, IP)`，保留；`POST /admin/users` 加 `@Idempotent(bizType="sys-user:create")`                                                          |
| **审计**       | 阶段B 的写接口暂不写操作日志（T02 落 AOP），但方法上留 `@OperationLog(module="system",action="create")` 注解占位（注解本体在 T02 建，本卡**不**引用，避免编译依赖）→ 实际做法：本卡不写注解，T02 统一补                                    |
| **循环依赖**     | `common` 不得依赖 `system`；`PermissionCache` 放 `common` 但通过 `CachePort` 接口由 `system` 提供实现，或直接在 `common` 里用 `StringRedisTemplate`（本项目已引入 Redisson/Redis，取后者更简单）                   |

---

## 验收标准

```bash
# 1) 编译
mvn -q -pl solo-eats-common -am compile
mvn -q -pl solo-eats-system -am compile
mvn -q -pl solo-eats-app -am compile
mvn -q -DskipTests package            # 必须 BUILD SUCCESS

# 2) 阶段A：账号密码登录
curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"Admin@123"}'
# 断言：code=0；data.token 非空；data.roles 含 "ADMIN"；data.permissions 非空

# 3) 错误密码
curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H 'Content-Type: application/json' -d '{"username":"admin","password":"wrong"}'
# 断言：code=10007

# 4) 阶段A：旧契约不回归（用商家 demo 账号）
curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"role":"MERCHANT","merchantId":1,"shopId":1}'
# 断言：code=0（local profile 下 mock 分支仍生效）

# 5) 当前用户 + 菜单树
TOKEN=<上一步的 data.token>
curl -s http://localhost:8080/api/v1/auth/me -H "Authorization: Bearer $TOKEN"
curl -s http://localhost:8080/api/v1/auth/menus -H "Authorization: Bearer $TOKEN"
# 断言：menus 为树形，根节点含 "系统管理"

# 6) 阶段B：用户列表
curl -s "http://localhost:8080/api/v1/admin/users?pageNum=1&pageSize=20" -H "Authorization: Bearer $TOKEN"
# 断言：code=0；total >= 3；不含 password_hash 字段

# 7) 阶段C：越权
curl -s -X POST http://localhost:8080/api/v1/admin/users -H "Authorization: Bearer $MERCHANT_TOKEN" ...
# 断言：HTTP 403 + code=10004
```

单元测试断言（`AuthServiceImplTest`）：

- 账号不存在 → `BizException(10007)`
- 密码错误 → `BizException(10007)`
- `status=1` → `BizException(10008)`
- 正确登录 → `LoginResult.roles` 含角色码、`token` 非空

---

## 禁止事项

1. **不得删除或改造现有 `@Profile("local")` 的 mock 登录行为**——它是现有 demo 脚本、`solo-eats-web` 的 `LoginView`、以及 `docs/订单接口联调与验收.md` 等验收文档依赖的入口。只能**增加**密码分支与开关。
2. **不得修改** `LoginResponse` 已有字段名/顺序（`token, tokenType, expiresIn, roles`），只能在末尾追加。
3. **不得修改** `ApiResponse`、`PageResult` 的结构。
4. **不得修改 `ErrorCode` 已有任何枚举值或码值**，只能追加。
5. 不得在 `solo-eats-common` 里引入 MyBatis-Plus、Spring Data Redis 之外的业务依赖，也不能让 `common` 反向依赖 `system`。
6. 不得把 `password_hash`、手机号明文写进日志（`log.info` 里出现密码字段即视为缺陷）。
7. 不得为省事把菜单权限硬编码成 Java 常量；菜单与权限码必须落库。
8. 不得在本卡改动 `solo-eats-product` / `solo-eats-order` / `solo-eats-payment` 的任何文件。
9. 不得删改既有测试（`OrderLifecycleIntegrationTest`、`OrderStateMachineTest` 等）。
10. 不得在 `t_sys_user` 里存 C 端消费者账号（消费者归 T06 的 `t_member_user`），否则 T06 会产生两套用户体系冲突。

---

## 执行记录

| 日期 | 会话范围 | 新增/修改文件 | 编译结果 | 遗留问题 |
| ---- | ---- | ------- | ---- | ---- |
| 2026-10-21 | T01 阶段A（Claude Code 无头执行，被用户中断于收尾处，编译与单测由主理人复验） | 新增 12：`solo-eats-system/pom.xml` + `domain/entity/{SysUser,SysRole,SysMenu}.java` + `mapper/{SysUser,SysRole,SysMenu}Mapper.java` + `support/PasswordHasher.java` + `service/AuthService.java` + `service/impl/AuthServiceImpl.java` + `service/command/PasswordLoginCommand.java` + `service/view/LoginResult.java` + `AuthServiceImplTest.java`；另新增 `db/migration/V4__system_rbac.sql`。修改 7：根 `pom.xml`、`solo-eats-app/pom.xml`、`AuthController.java`、`dto/LoginRequest.java`、`vo/LoginResponse.java`、`common/auth/JwtUtils.java`、`common/exception/ErrorCode.java` | `mvn -q -DskipTests package` BUILD SUCCESS；`AuthServiceImplTest` Tests run: 4, Failures: 0 | ① 阶段B/C 未做（无 CRUD、无 `@RequirePermission`）；② `t_sys_user_role`/`t_sys_role_menu` 建表在 V4 中已含，但实体与 Mapper 留待阶段B；③ 执行期间误格式化 `docs/外卖需求规格说明书.md`（522 增 546 删，纯表格对齐），已 `git checkout` 还原；④ 未 git commit |
