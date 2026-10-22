# T02 system-租户 · 组织 · 审计日志 · 通知

> 优先级：**P0**（租户/部门/岗位/字典/操作日志/**DataScope 行级数据权限**）；**P1**（邮件、公告）
> 所属模块：`solo-eats-system` + `solo-eats-app`
> 分三段：**阶段A（租户/套餐/部门/岗位/字典）→ 阶段B（操作日志/登录日志/邮件/公告）→ 阶段C（DataScope 行级数据权限，P0 支撑项）**
>
> 说明：T01 声明「data_scope 本次只落库、强制生效在 T02 完成后收紧」，本卡阶段C 即兑现该承诺——落地 `data_scope` 五档的行级过滤，避免 RBAC 数据权限「只有落库没有生效」成为 P0 支撑项（规格书 1.6）的缺口。

---

## 目标

**一句话**：让"多租户隔离"从一句口号变成一行代码——`TenantContext.get()` 自动给所有新表写 `tenant_id`、自动在查询上加 `tenant_id` 过滤，同时把后台每一次写操作变成可追溯的审计日志。

**完成后系统能做什么**
1. 新增/查询业务数据时无需手写 `tenant_id`，由 `TenantLineInnerInterceptor` + `TenantContext` 自动完成，跨租户读写直接查不到/写不进。
2. 平台可以创建租户、绑定套餐（可访问菜单范围 + 有效期），到期自动失效。
3. 部门（组织树）+ 岗位可选配到 `t_sys_user.dept_id/post_id`，数据字典供全站枚举复用。
4. 每次带 `@OperationLog` 的写接口自动落一条操作日志（谁、什么时间、什么 IP、请求参数摘要、耗时、结果码）。
5. 登录成功/失败都落登录日志，支持按用户/时间/IP 检索。
6. 平台可发公告（富文本），站内已读统计；可配置 SMTP 邮件账号与模板并落发送日志。

---

## 规格书依据

| 章节 | 原文要点 |
|---|---|
| 4.12 系统管理 | 「部门与岗位：组织架构、岗位管理；字典与地区：数据字典、省市区行政数据；租户与套餐：租户（平台/商家）、套餐可访问范围、有效期；通知公告：公告发布与已读统计；邮件管理：邮箱账号（SMTP）、邮件模板、邮件日志；日志中心：登录日志、操作日志、API 访问日志、API 错误日志」 |
| 2.1 系统构成 | 「四个前端共用同一套后端服务与权限模型，通过角色与 `merchant_id`/`shop_id` 做数据隔离」 |
| 3.2 权限模型 | 数据权限范围 5 档（全平台/本租户/本商家/本门店/本人），由部门树与租户共同决定可见范围 |
| 9.3 安全 | 「数据脱敏与操作审计；日志留存练手环境 ≥ 30 天」 |
| 1.6 P0 范围 | 「支撑：RBAC、**多租户隔离**、MinIO、WebSocket 消息…」 |

---

## 前置依赖

| 依赖 | 具体依赖物 |
|---|---|
| **T01 阶段A/B** | `SysUser`/`SysRole`/`SysMenu` 实体与 Mapper；`AuthService`；`UserPrincipal.tenantId`（T01-C 已加） |
| **T01 阶段C** | `RequirePermission` 注解、`PermissionCache`（本卡的菜单范围校验复用它） |
| 既有表 | `t_sys_user`（补 `dept_id`/`post_id` 外键语义，另需 `t_sys_tenant`/`t_sys_dept`/`t_sys_post`/`t_sys_dict_type`/`t_sys_dict_item`） |
| 既有配置 | `application.yml` 的 `spring.mail.*`（**当前没有，需要新增**）、`app.jwt` |

---

## 数据模型

迁移脚本：`V5__system_tenant_org_dict.sql`（阶段A）、`V6__system_log_mail_notice.sql`（阶段B）

### 阶段A：5 张表

| 表 | 关键字段 | 索引/唯一键 |
|---|---|---|
| `t_sys_tenant` | id, tenant_code varchar(64), tenant_name, tenant_type tinyint(1平台 2商家), merchant_id, package_id, status(0启用 1停用 2过期), expire_at, contact_name, contact_phone, created_at/updated_at/deleted | `uk_tenant_code(tenant_code)` |
| `t_sys_package` | id, package_code, package_name, menu_ids json（可访问菜单范围）, max_shop int, max_user int, price bigint(分), valid_days int, status, remark, 审计字段 | `uk_package_code(package_code)` |
| `t_sys_dept` | id, tenant_id, parent_id, ancestors varchar(512)（祖级路径 `0,1,3`）, dept_name, leader, phone, sort, status, 审计字段 | `idx_parent(parent_id)`、`idx_ancestors(ancestors)` |
| `t_sys_post` | id, tenant_id, post_code, post_name, sort, status, remark, 审计字段 | `uk_tenant_post(tenant_id, post_code)` |
| `t_sys_dict_type` | id, tenant_id, dict_type varchar(64), dict_name, status, remark, 审计字段 | `uk_tenant_dict_type(tenant_id, dict_type)` |
| `t_sys_dict_item` | id, tenant_id, dict_type, dict_label, dict_value, dict_sort, css_class, is_default tinyint, status, remark, 审计字段 | `idx_type_sort(dict_type, dict_sort)` |

> 字典项也走 `deleted` 逻辑删除；`is_default=1` 在同一 `dict_type` 内最多 1 条（应用层保证，不建约束）。

### 阶段B：5 张表

| 表 | 关键字段 | 索引 |
|---|---|---|
| `t_sys_oper_log` | id, tenant_id, trace_id, module varchar(32), action varchar(32), method varchar(255), request_uri, request_method, request_params json（**脱敏后**）, operator_id, operator_name, operator_type, ip varchar(64), ua varchar(512), result_code int, error_msg varchar(512), cost_ms int, created_at | `idx_operator_time(operator_id, created_at)`、`idx_module_time(module, created_at)`、`idx_trace(trace_id)` |
| `t_sys_login_log` | id, tenant_id, user_id, username, login_type tinyint(1密码 2验证码 3mock), success tinyint, fail_reason varchar(128), ip, ua, created_at | `idx_user_time(user_id, created_at)`、`idx_time(created_at)` |
| `t_sys_mail_account` | id, tenant_id, account_name, host, port, username, password_cipher, from_addr, ssl tinyint, status, 审计字段 | `uk_tenant_account(tenant_id, account_name)` |
| `t_sys_mail_template` | id, tenant_id, template_code, template_name, subject, content text, status, 审计字段 | `uk_tenant_tpl(tenant_id, template_code)` |
| `t_sys_mail_log` | id, tenant_id, template_code, to_addr, subject, content text, status tinyint(0待发 1成功 2失败), error_msg, sent_at, created_at | `idx_status_time(status, created_at)` |
| `t_sys_notice` | id, tenant_id, title, content longtext, notice_type tinyint(1公告 2通知), target_type tinyint(1全部 2平台 3商家 4骑手), status tinyint(0草稿 1已发布 2已撤回), publish_at, expire_at, read_count int, 审计字段 | `idx_status_publish(status, publish_at)` |

---

## 接口清单

| # | 方法 | 路径 | 要点 | 权限 |
|---|---|---|---|---|
| 1 | GET | `/api/v1/admin/tenants` | `tenantType,status,keyword,pageNum,pageSize` → `PageResult<TenantView>` | `system:tenant:list` |
| 2 | POST | `/api/v1/admin/tenants` | `{tenantCode,tenantName,tenantType,merchantId,packageId,expireAt,contactName,contactPhone}` | `system:tenant:create` |
| 3 | PUT | `/api/v1/admin/tenants/{id}` | 同上 | `system:tenant:update` |
| 4 | PUT | `/api/v1/admin/tenants/{id}/status` | `{status}` | `system:tenant:update` |
| 5 | GET | `/api/v1/admin/packages` | 套餐列表 | `system:package:list` |
| 6 | POST/PUT | `/api/v1/admin/packages`、`/{id}` | `{packageCode,packageName,menuIds[],maxShop,maxUser,price,validDays}` | `system:package:*` |
| 7 | GET | `/api/v1/admin/depts/tree` | 部门树 | `system:dept:list` |
| 8 | POST/PUT/DELETE | `/api/v1/admin/depts`、`/{id}` | `{parentId,deptName,leader,phone,sort,status}`；删除前校验无子节点、无用户 | `system:dept:*` |
| 9 | GET | `/api/v1/admin/posts` | 岗位分页 | `system:post:list` |
| 10 | POST/PUT/DELETE | `/api/v1/admin/posts`、`/{id}` | `{postCode,postName,sort,status,remark}` | `system:post:*` |
| 11 | GET | `/api/v1/admin/dicts` | 字典类型分页 | `system:dict:list` |
| 12 | GET | `/api/v1/admin/dicts/{dictType}/items` | **免登录可读**（前端渲染枚举） | 白名单 |
| 13 | POST/PUT/DELETE | `/api/v1/admin/dicts`、`/items` | 字典类型 & 字典项维护 | `system:dict:*` |
| 14 | GET | `/api/v1/admin/logs/oper` | `module,operatorId,resultCode,dateFrom,dateTo,pageNum,pageSize` | `system:log:oper:list` |
| 15 | GET | `/api/v1/admin/logs/login` | `username,success,dateFrom,dateTo,pageNum,pageSize` | `system:log:login:list` |
| 16 | GET | `/api/v1/admin/mails/accounts` | 邮件账号列表 | `system:mail:list` |
| 17 | POST/PUT | `/api/v1/admin/mails/accounts`、`/{id}` | `{host,port,username,password,fromAddr,ssl}`；密码加密入库 | `system:mail:update` |
| 18 | POST | `/api/v1/admin/mails/test` | `{accountId,toAddr}` 发测试邮件 | `system:mail:test` |
| 19 | GET | `/api/v1/admin/mails/logs` | 邮件日志分页 | `system:mail:list` |
| 20 | GET | `/api/v1/admin/notices` | 公告分页 | `system:notice:list` |
| 21 | POST/PUT | `/api/v1/admin/notices`、`/{id}` | `{title,content,noticeType,targetType,publishAt,expireAt}` | `system:notice:*` |
| 22 | PUT | `/api/v1/admin/notices/{id}/publish` | 发布 / 撤回 | `system:notice:update` |
| 23 | GET | `/api/v1/c/notices` | 用户端可见公告（已发布+未过期） | 登录即可 |
| 24 | PUT | `/api/v1/c/notices/{id}/read` | 标记已读（`read_count` 自增） | 登录即可 |

---

## 需要新增/修改的文件

### 阶段A（17 个）

| 类型 | 路径 |
|---|---|
| 新 | `solo-eats-app/src/main/resources/db/migration/V5__system_tenant_org_dict.sql` |
| 新 | `solo-eats-system/src/main/java/com/soloeats/system/domain/entity/SysTenant.java`、`SysPackage.java`、`SysDept.java`、`SysPost.java`、`SysDictType.java`、`SysDictItem.java`（6） |
| 新 | `solo-eats-system/src/main/java/com/soloeats/system/mapper/` 下 6 个 Mapper |
| 新 | `solo-eats-system/src/main/java/com/soloeats/system/tenant/TenantContext.java`（ThreadLocal，含 `get()/set()/clear()`、`DEFAULT_TENANT_ID=1`） |
| 新 | `solo-eats-system/src/main/java/com/soloeats/system/tenant/TenantFilter.java`（从 `AuthContext` 取 `tenantId` 写入 `TenantContext`，`finally` 清理） |
| 新 | `solo-eats-system/src/main/java/com/soloeats/system/service/SysTenantService.java` + `impl` |
| 新 | `solo-eats-system/src/main/java/com/soloeats/system/service/SysOrgService.java` + `impl`（部门 + 岗位） |
| 新 | `solo-eats-system/src/main/java/com/soloeats/system/service/SysDictService.java` + `impl` |
| 新 | `solo-eats-app/src/main/java/com/soloeats/app/controller/AdminTenantController.java`、`AdminDeptController.java`、`AdminPostController.java`、`AdminDictController.java`（4） |
| 改 | `solo-eats-app/src/main/java/com/soloeats/app/config/MybatisPlusConfig.java`（注册 `TenantLineInnerInterceptor`，忽略表白名单 = **系统表** + **21 张既有业务表** + 框架表，见下方「关键实现约束 → 历史表不补列」的完整清单） |

### 阶段B（17 个）

| 类型 | 路径 |
|---|---|
| 新 | `solo-eats-app/src/main/resources/db/migration/V6__system_log_mail_notice.sql` |
| 新 | `solo-eats-system/src/main/java/com/soloeats/system/domain/entity/SysOperLog.java`、`SysLoginLog.java`、`SysMailAccount.java`、`SysMailTemplate.java`、`SysMailLog.java`、`SysNotice.java`（6） |
| 新 | 对应 6 个 Mapper |
| 新 | `solo-eats-common/src/main/java/com/soloeats/common/log/OperationLog.java`（注解：`module()`、`action()`、`recordParams()`） |
| 新 | `solo-eats-common/src/main/java/com/soloeats/common/log/OperationLogAspect.java`（`@Around`，异步落库，参数脱敏：`password|phone|idCard|bankAccount` → `***`） |
| 新 | `solo-eats-system/src/main/java/com/soloeats/system/service/SysLogService.java` + `impl` |
| 新 | `solo-eats-system/src/main/java/com/soloeats/system/service/SysMailService.java` + `impl`（`JavaMailSender`） |
| 新 | `solo-eats-system/src/main/java/com/soloeats/system/service/SysNoticeService.java` + `impl` |
| 新 | `solo-eats-app/src/main/java/com/soloeats/app/controller/AdminLogController.java`、`AdminMailController.java`、`AdminNoticeController.java`、`NoticeClientController.java`（4） |
| 改 | `solo-eats-app/src/main/resources/application.yml`（`spring.mail.host/port/username/password/properties.mail.smtp.auth`，全部走 `${MAIL_*:}` 环境变量；`app.auth.mock-login-enabled`、`app.tenant.enabled`） |
| 改 | `solo-eats-app/src/main/java/com/soloeats/app/controller/AuthController.java`（登录成功/失败写 `t_sys_login_log`） |

### 阶段C（约 6 个：DataScope 行级数据权限）

| 类型 | 路径 |
|---|---|
| 新 | `solo-eats-common/src/main/java/com/soloeats/common/datascope/DataScope.java`（注解：`deptAlias()`、`userAlias()`、`customSql()`，标注在需要数据权限过滤的 Mapper 方法上） |
| 新 | `solo-eats-common/src/main/java/com/soloeats/common/datascope/DataScopeInnerInterceptor.java`（MyBatis-Plus 拦截器：解析当前用户 `data_scope`，自动拼接 `WHERE` 子句；须排在 `TenantLineInnerInterceptor` 之后、分页插件之前） |
| 新 | `solo-eats-system/src/main/java/com/soloeats/system/service/DataScopeService.java` + `impl`（把 `data_scope` 档位 + 当前 `UserPrincipal` 解析为 SQL 过滤条件：`1全部` 不加、`2本租户`=`tenant_id=?`、`3本商家`=`merchant_id=?`、`4本门店`=`shop_id=?`、`5本人`=`user_id=?`/`rider_id=?`） |
| 改 | `solo-eats-app/src/main/java/com/soloeats/app/config/MybatisPlusConfig.java`（注册 `DataScopeInnerInterceptor`） |
| 改 | 各后台管理 Mapper/Service（商家列表/门店列表/订单列表/配送单列表/报表等只读接口）按需标注 `@DataScope`，明确列别名映射 |
| 新 | `solo-eats-system/src/test/java/com/soloeats/system/service/DataScopeServiceTest.java`（五档各一组断言） |

> 阶段C 依赖 T01 的 `SysUser`/`SysRole.data_scope` 字段与 `UserPrincipal`（T01-C 已加 `tenantId`，需在 T01 阶段C 补充 `merchantId/shopId/userId` 到 principal，见 T01 卡片「数据权限」约束的回写说明）。

---

## 关键实现约束

| 项 | 约束 |
|---|---|
| **租户隔离方式** | 用 MyBatis-Plus `TenantLineInnerInterceptor`，**不要**在每个 Mapper XML 手写 `tenant_id = ?`。`tenant_id` 取值来源优先级：`TenantContext.get()` → 请求头 `X-Tenant-Id`（仅 ADMIN）→ 默认 1 |
| **DataScope 行级权限** | `data_scope` 五档过滤用 `DataScopeInnerInterceptor` 统一实现，**不要**在每个 Service 手写 `merchant_id=?`/`shop_id=?` 判断：`1全部` 不加过滤；`2本租户` 拼 `tenant_id=?`；`3本商家` 拼 `merchant_id=?`（当前商家）；`4本门店` 拼 `shop_id=?`；`5本人` 拼 `user_id=?`（或骑手 `rider_id`）。当前商家/门店/用户取值来自 `UserPrincipal`（T01 阶段C 补充 `merchantId/shopId/userId`）。**注意**：`data_scope` 只约束**平台后台角色**；商家中心/骑手端角色由既有 `PermissionGuard` 的 `merchant_id/shop_id/user_id` 校验兜底，二者不重叠。拦截器须排在 `TenantLineInnerInterceptor` 之后、分页插件之前 |
| **历史表不补列** | **21 张既有业务表不加 `tenant_id`**（见 README Q1），隔离靠 `merchant_id/shop_id`。`TenantLineInnerInterceptor` 的忽略表必须**显式、完整**列出下列清单，否则会报 `Unknown column 'tenant_id'`。忽略表全集：<br>**① 21 张既有业务表**：`t_order`、`t_order_item`、`t_order_discount`、`t_order_status_log`、`t_sku`、`t_sku_stock`、`t_stock_lock`、`t_stock_log`、`t_coupon_template`、`t_user_coupon`、`t_user_coupon_log`、`t_payment_order`、`t_refund_order`、`t_refund_item`、`t_delivery_order`、`t_dispatch_record`、`t_order_exception`、`t_settlement_bill`、`t_settlement_item`、`t_rider_income`、`t_payout_order`<br>**② 公共/框架表**：`t_idempotency`、`t_outbox_event`、`flyway_schema_history`<br>**③ 系统表（无 tenant_id）**：`t_sys_menu`、`t_sys_package`（若有 tenant_id 则移出忽略表；`t_sys_tenant` 作为租户主表不参与行级过滤）<br>实现方式：维护一个 `Set<String>` 常量 `IGNORE_TABLES`，上述清单必须与阶段A 行 122 落地配置的列表**完全一致**，避免两处漂移 |
| **事务** | `TenantContext` 在 Filter 中 set/clear，与事务无关；**禁止**在 `@Async` 线程里直接依赖 ThreadLocal（需要显式传递，`TraceTaskDecorator` 已提供范式，可复用） |
| **日志异步** | `OperationLogAspect` 用 `@Async` + `AsyncConfig` 已有线程池；落库失败只 `log.warn`，**不得**影响主流程；参数落库前必须脱敏并截断到 2000 字符 |
| **日志不入日志表** | `/api/v1/admin/logs/**` 自身**不记**操作日志（避免日志风暴），用切面内 URI 前缀排除 |
| **部门树** | `ancestors` 采用 `0,1,3` 形式；移动部门时级联更新子部门 `ancestors`（同一事务）；删除前必须校验无子部门且无用户引用 |
| **字典缓存** | `dictType → items` 缓存 Redis `dict:{tenantId}:{dictType}`，TTL 1h；字典项变更时主动 `DEL` |
| **邮件密码** | `password_cipher` 用 AES-GCM 加密，密钥 `app.crypto.mail-key`（环境变量 `MAIL_KEY`），**禁止明文**；日志中不得出现 |
| **公告已读** | P0 只维护 `read_count`；按用户级已读明细（`t_sys_notice_read`）放 P1，本卡不建 |
| **套餐限制** | `max_shop`/`max_user` 只在"创建门店/创建账号"时做**软校验**（超限返回 `PARAM_INVALID` 带说明），不做硬拦截 |
| **登录日志** | 失败也要落（含 `fail_reason`）；同一 IP 1 分钟内失败 > 5 次 → 后续返回 `10006 AUTH_ACCOUNT_LOCKED`（用 Redis 计数，TTL 10min） |

---

## 验收标准

```bash
mvn -q -pl solo-eats-system -am compile
mvn -q -DskipTests package

TOKEN=<admin token>

# 租户
curl -s -X POST http://localhost:8080/api/v1/admin/tenants -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"tenantCode":"M0001","tenantName":"示例餐饮","tenantType":2,"packageId":1,"expireAt":"2027-12-31 23:59:59"}'
# 断言：code=0；返回 id

# 部门树
curl -s -X POST http://localhost:8080/api/v1/admin/depts -H "Authorization: Bearer $TOKEN" -d '{"parentId":0,"deptName":"运营中心","sort":1}'
curl -s http://localhost:8080/api/v1/admin/depts/tree -H "Authorization: Bearer $TOKEN"
# 断言：树包含"运营中心"

# 字典（免登录可读）
curl -s http://localhost:8080/api/v1/admin/dicts/order_status/items
# 断言：code=0；返回 13 个订单状态项（对齐规格书 9.9）

# 操作日志（先调用一个带 @OperationLog 的写接口，再查）
curl -s "http://localhost:8080/api/v1/admin/logs/oper?pageNum=1&pageSize=5" -H "Authorization: Bearer $TOKEN"
# 断言：total >= 1；request_params 中 phone/password 已脱敏为 ***；traceId 非空

# 登录日志
curl -s "http://localhost:8080/api/v1/admin/logs/login?pageNum=1&pageSize=5" -H "Authorization: Bearer $TOKEN"
# 断言：有 success=1 与 success=0 各至少 1 条（先故意错密码一次）

# 租户隔离（关键）
# 用 tenantB 的账号查询，断言看不到 tenantA 的数据
```

单元测试：
- `SysOrgServiceImplTest`：移动部门后子部门 `ancestors` 被更新
- `OperationLogAspectTest`：含 `password` 的参数序列化后不含原值

---

## 禁止事项

1. **不得给既有 21 张表加 `tenant_id`**。理由：`t_order` 有 3000 万行级增长假设，且 `uk_order_no` 等唯一键需重建，风险远大于收益；隔离用 `merchant_id/shop_id` 已足够（README Q1）。
2. 不得改动 `MybatisPlusConfig` 中既有的分页插件顺序（`TenantLine` 必须在分页插件**之前**注册）。
3. 不得让 `@OperationLog` 切面捕获并吞掉业务异常——异常必须继续向上抛给 `GlobalExceptionHandler`。
4. 不得在操作日志里存完整请求体（只存脱敏后的摘要）。
5. 不得用 `System.out` / `printStackTrace`。
6. 不得修改 `t_sys_user` 已有列的类型（只允许 `dept_id`/`post_id` 语义启用，不加列）。
7. 不得在本卡改动 order/payment/delivery/settlement/marketing 模块。
8. 不得把 SMTP 密码、租户密钥写进 `application.yml` 默认值。

---

## 执行记录

| 日期 | 会话范围 | 新增/修改文件 | 编译结果 | 遗留问题 |
|---|---|---|---|---|
| 2026-10-22 | T02 阶段A：租户/套餐/部门/岗位/字典 + 多租户隔离（47 个：新增 45，修改 2） | 新增：`V5__system_tenant_org_dict.sql`；`SysTenant/SysPackage/SysDept/SysPost/SysDictType/SysDictItem` 实体与 6 个 Mapper；`TenantContext`、`TenantFilter`；`SysTenantService`、`SysOrgService`、`SysDictService` 及 impl；6 个 command、6 个 view；`AdminTenant/AdminDept/AdminPost/AdminDict` 4 个 Controller；5 个 DTO；`TenantContextTest`、`SysDictServiceImplTest`、`SysOrgServiceImplTest`。修改：`MybatisPlusConfig`（注册 `TenantLineInnerInterceptor` + 27 张忽略表常量）、`SysMenuServiceImpl`（按租户套餐 `menu_ids` 收敛菜单树） | `mvn -q -pl solo-eats-system -am compile`、`mvn -q -pl solo-eats-app -am compile` 均 0 error；`mvn -q -DskipTests package` BUILD SUCCESS；`mvn -pl solo-eats-system -am test` 17 个用例全通过。另起真实 MySQL 容器做运行验证：Flyway V1~V5 全量迁移通过、V5 可重复执行；登录、租户/套餐/部门/岗位/字典 CRUD、`X-Tenant-Id` 隔离、部门移动级联、字典缓存 TTL 3389s 与变更删除缓存均符合预期 | ① T01 的 `SysUserServiceImpl`/`AuthServiceImpl` 仍固定 tenant 1（不在本卡阶段A 文件清单），ADMIN 带 `X-Tenant-Id` 新增账号仍写入 tenant 1，租户账号登录需阶段B 统一改造；② 套餐仅收敛菜单树可见范围，未过滤权限码集合（`selectPermsByUserId` 未按 `menu_ids` 收敛）；③ 部门联系电话明文入库、仅展示脱敏，未接手机号加密；④ `t_sys_dept` 未加 `version`，移动/改名依赖事务内行锁而非乐观锁 |
| 2026-10-22 | T02 阶段B：操作日志/登录日志/邮件/公告（48 个：新增 40，修改 8） | 新增：`V6__system_log_mail_notice.sql`（6 表 + 菜单 48~55 + 超管授权）；`SysOperLog/SysLoginLog/SysMailAccount/SysMailTemplate/SysMailLog/SysNotice` 实体与 6 个 Mapper；common `OperationLog`、`OperationLogRecord`、`OperationLogSink`、`OperationLogAspect`（参数脱敏 + 截断 + URI 排除）；`SysLogService`/`SysMailService`/`SysNoticeService` 及 impl（`persist` 走 `@Async("taskExecutor")`）；`MailCipher`（AES-GCM）；2 个 command、5 个 view；`AdminLog/AdminMail/AdminNotice/NoticeClient` 4 个 Controller；4 个 DTO；`OperationLogAspectTest`（2 用例）。修改：`AuthController`（成功/失败登录日志 + IP 失败锁定）、`AdminUser/AdminRole/AdminMenu` 回补 `@OperationLog`、`RoleAuthInterceptor`（`ADMIN*` 前缀放行 8 个平台角色）、`application.yml`（`spring.mail` 全环境变量 + `app.auth.mock-login-enabled`/`app.tenant.enabled`/`app.crypto.mail-key`）、`solo-eats-system/pom.xml`（mail starter）、`.env.example`（`MAIL_*`/`MAIL_KEY`/开关） | `mvn -q -pl solo-eats-system -am compile`、`mvn -q -pl solo-eats-app -am compile` 均 0 error；`mvn -q -DskipTests package` BUILD SUCCESS；`mvn -pl solo-eats-common -am test` 10 个用例、`mvn -pl solo-eats-system -am test` 18 个用例全部通过（含新增 `OperationLogAspectTest` 2 个）；`mvn -pl solo-eats-app -am test -Dtest=OrderLifecycleIntegrationTest` 7 个用例通过（真实 MySQL 容器，Flyway V1~V6 全量迁移通过，Spring 上下文装配通过） | ① T01 的 `AuthServiceImpl` 仍固定 tenant 1，租户账号登录与失败日志无法归属正确租户（本卡文件清单未含该类）；② `ADMIN*` 前缀只解决角色粗筛，`ADMIN_OPERATION` 等细分角色访问用户/角色/菜单/日志/邮件接口仍需在角色管理中授予对应 `system:*` 权限码；③ 公告已读只累加 `read_count`，重复调用会重复计数，用户级明细 `t_sys_notice_read` 留 P1；④ 邮件模板仅有实体/Mapper，接口清单未包含模板 CRUD；⑤ `MAIL_KEY` 轮换后历史 `password_cipher` 无法解密，暂无重加密迁移；⑥ `app.tenant.enabled` 已配置但 `TenantLineInnerInterceptor` 仍无条件注册，置 false 不会绕过租户过滤；⑦ 登录失败计数依赖 Redis，Redis 故障时按失败放行处理；⑧ 操作日志异步队列打满时仅 `log.warn` 丢弃，不阻塞业务（符合本卡约束）；⑨ `ssl` 是 MySQL 8 保留字，`t_sys_mail_account` 列名落地为 `ssl_enabled`，实体用 `@TableField("ssl_enabled")` 映射，对外 DTO/接口字段仍为 `ssl` |
| 2026-10-22 | T02 阶段C：DataScope 行级数据权限（10 个：新增 6，修改 4） | 新增：common `datascope/DataScope`（`deptAlias/userAlias/customSql` + `tenantColumn/merchantColumn/shopColumn/userColumn/riderColumn` 可选列映射）、`datascope/DataScopeInnerInterceptor`（按 `MappedStatement.id` 反射取注解并缓存，JSqlParser 改写 WHERE；无法解析时失败关闭）、`DataScopeService` + `impl`（五档解析 + 条件拼接；角色取数值最小即最宽松档，无角色回落 `t_sys_user.data_scope`，缺值拼 `1 = 0`）、`DataScopeServiceTest`（14 用例：五档 + 骑手 + id 映射 + 缺值 + customSql + 非平台身份 + 档位解析）、common `DataScopeInnerInterceptorTest`（4 用例：有/无 WHERE、未标注、无登录身份）。修改：`solo-eats-common/pom.xml`（新增 `mybatis-plus-jsqlparser`，仅拦截器用）、`MybatisPlusConfig`（TenantLine → DataScope → OptimisticLocker → Pagination；`ObjectProvider<DataScopeService>` 解启动期循环依赖）、`SysUserMapper`（新增 `selectScopedPage`：`@DataScope(deptAlias="u", userAlias="u", userColumn="id")`）、`SysUserServiceImpl.page` 改走该方法 | `mvn -q -pl solo-eats-common -am compile`、`mvn -q -pl solo-eats-system -am compile`、`mvn -q -pl solo-eats-app -am compile` 均 0 error；`mvn -q -DskipTests package` BUILD SUCCESS；`DataScopeServiceTest` 14 个断言通过；`mvn -Pisolated-build -pl solo-eats-common,solo-eats-system -am test` 46 个用例全绿（common 14 + system 32）；`OrderLifecycleIntegrationTest` 7 个用例通过（Spring 上下文装配 + Flyway V1~V6）；临时探针（已删除）在真实 MySQL 验证：角色档位 3 的账号查询 `t_sys_user` 仅返回 merchant_id=1 的 3 条，档位 1 的 admin 返回全部 6 条，分页 count 同步被改写 | ① 商家/门店/订单/配送后台列表未标 `@DataScope`：merchant/store 模块尚未建（T03/T04）；order/delivery 属本卡禁止事项 #7；且 21 张历史表无 `tenant_id`，档位 2 无法直接落列，需在 T03/T04/T05/T07 落地时用 `deptAlias/customSql` 明确列映射；② 每次带 `@DataScope` 的查询额外查 1 次 `t_sys_user` + 2 次角色表，未做请求级缓存，高 QPS 报表需补缓存并处理角色变更失效；③ 档位取「所有启用角色中最宽松」，`t_sys_user.data_scope` 仅在无角色时兜底，若要求用户级可收紧角色需改语义；④ 仅对角色码以 `ADMIN` 开头的平台身份生效，local/mock 登录未写 `ADMIN*` 角色时不会过滤；⑤ 拦截器对 CTE 等非 `PlainSelect/SetOperationList/ParenthesedSelect` 语句抛 `SYSTEM_ERROR`（失败关闭），后续新语法需扩展；⑥ `SysUserServiceImpl` 仍手写 `tenant_id=1` 过滤（既有遗留，不在本次范围） |
