# CLAUDE.md — SoloEats 项目约定（Claude Code 必读）

> 本文件是 Claude Code 在本仓库工作的**唯一约定入口**。动手前先读本文件 + 任务卡。

## 1. 项目定位与技术栈

「数创星光外卖」本地生活外卖点餐平台：**4 个业务端**（平台运营后台 / 商家中心 + 商家端 App / 用户端 App / 骑手端 App）+ 1 套 Spring Boot 模块化单体后端。

| 层 | 选型 |
|---|---|
| 后端 | JDK 17 · Spring Boot **3.5.16** · MyBatis-Plus **3.5.17** |
| 存储 | MySQL 8（Flyway 迁移）· Redis（缓存/锁）· RabbitMQ（异步/延迟）· MinIO（文件） |
| 前端后台 | Vue 3 + Vite 6 + Element Plus（`solo-eats-web`） |
| 移动端 | UniApp（Vue 3）→ `solo-eats-uniapp-{user,rider,merchant}` |
| 构建 | Maven 多模块，父 POM `com.soloeats:solo-eats-parent` |

## 2. 模块与职责边界

| 模块 | 职责 | 现状 |
|---|---|---|
| `solo-eats-common` | ApiResponse/PageResult/JWT/@RequireRole/幂等/分布式锁/限流/TraceId/Rabbit 常量/支付网关抽象/文件存储端口 | 已实现 |
| `solo-eats-system` | 账号、角色、菜单、部门、岗位、字典、租户、日志、邮件、公告 | **待建（T01/T02）** |
| `solo-eats-merchant` | 商家入驻/资质/审核/结算账户 | **待建（T03）** |
| `solo-eats-store` | 门店、营业状态、配送围栏、门店账号、装修 | **待建（T04）** |
| `solo-eats-product` | 品类、SPU、门店商品、SKU、加料、估清、评价 | 部分（仅 SKU+库存） |
| `solo-eats-order` | 购物车、下单、状态机、售后、退款 | 已实现（**禁止改状态机**） |
| `solo-eats-payment` | 支付单、Mock 网关、回调 | 已实现 |
| `solo-eats-delivery` | 骑手、调度派单、配送单、轨迹、运费 | 部分 |
| `solo-eats-settlement` | 抽佣、商家/骑手结算、提现、资金流水 | 部分 |
| `solo-eats-marketing` | 券、满减、秒杀、会员、积分 | 部分（仅券） |
| `solo-eats-member` | 用户、会员档案、地址、钱包 | **待建（T06）** |
| `solo-eats-message` | 站内信、模板、IM、WebSocket | **待建（T08）** |
| `solo-eats-service` | 客服、工单、投诉、FAQ | **待建（T09）** |
| `solo-eats-content` | 首页装修、广告位、搜索运营、版本 | **待建（T10）** |
| `solo-eats-report` | 经营/流量/商品/财务**只读**报表 | **待建（T11）** |
| `solo-eats-app` | 应用入口 + **全部 Controller/DTO/VO** | 已实现 |

**依赖红线**
- 领域模块之间**只能依赖 service 接口**，不得直接依赖对方的 `domain` 实体或 `mapper`。
- 禁止反向依赖：`product` ✗→ `order`；`marketing` ✗→ `order`；`delivery` ✗→ `order`；`content` ✗→ 被 `store/product` 依赖。
- `common` 不得依赖任何业务模块。

## 3. 目录与命名

```
solo-eats-<module>/src/main/java/com/soloeats/<module>/
├── domain/entity/        # @TableName 实体
├── domain/enums/         # 状态枚举（含合法流转表）
├── mapper/               # MyBatis-Plus BaseMapper
├── service/              # 接口 + command/ + view/
├── service/impl/
└── support/              # 模块内工具（GeoUtils / PhoneCipher …）

solo-eats-app/src/main/java/com/soloeats/app/
├── controller/           # 所有 Controller 只放这里
├── dto/                  # 请求体（record + jakarta.validation）
├── vo/                   # 响应体
├── config/               # 配置类
└── security/             # PermissionGuard

solo-eats-app/src/main/resources/db/migration/   # Flyway：V<序号>__<描述>.sql
```
- 表名 `t_` 前缀；字段 `snake_case`；索引 `idx_`；唯一键 `uk_`；Java 驼峰（自动映射）。
- 接口路径一律 `/api/v1/**`，按端分区：`/admin/**`、`/merchant/**`、`/rider/**`、`/c/**`。

## 4. 编码硬约束

1. **金额**：DB `bigint` 分；Java `long`。**禁止** `double/float` 存或算金额。费率用 `BigDecimal` + `setScale(4, HALF_UP)`。
2. **响应**：一律 `ApiResponse<T>{code,message,data,traceId}`，成功 `code=0`。
3. **分页**：一律 `PageResult<T>{list,total,pageNum,pageSize}`，`pageNum` 从 1 开始，`pageSize` 默认 20、上限 100。
4. **异常**：抛 `BizException(ErrorCode)`，**不得**直接返回 `ApiResponse.fail` 或 `throw new RuntimeException`。新增错误码只能**追加**到 `ErrorCode`。
5. **审计字段**：新表必须有 `created_at`、`updated_at`；配置类表加 `deleted tinyint DEFAULT 0`；关键表加 `version int unsigned DEFAULT 0`。
6. **状态流转**：必须条件更新 `WHERE id=? AND status=?`；不匹配抛对应 `*_STATE_INVALID`。**禁止**先 SELECT 再 UPDATE。
7. **幂等**：写接口加 `@Idempotent(key="...", ttlSeconds=...)`（注解只有 `key`/`ttlSeconds` 两个属性，**没有** `bizType`；`key` 留空则用方法签名+参数哈希）；下单/支付/退款/核销必带 `Idempotency-Key` 请求头。
8. **租户**：新表带 `tenant_id bigint unsigned NOT NULL DEFAULT 1`；隔离靠 `TenantLineInnerInterceptor`（T02 落地），**禁止**在 SQL 里手写 `tenant_id = ?`。
9. **脱敏**：手机号/身份证/银行卡加密入库、脱敏返回、**禁止**出现在日志。
10. **`@Transactional`**：只包 DB 操作；文件上传、MQ 发送、外部 HTTP 必须放事务**外**。
11. 方法参数名保留（`-parameters` 已开启，`@PathVariable Long id` 必须写名字）。
12. 不写 `System.out`/`printStackTrace`；日志用 `@Slf4j`，带 `traceId`。

## 4.1 身份与标识口径（强制，历史踩坑点）

> 这三条规则各自都曾被违反过，症状都是「单测全绿、真实链路不通」。改任何鉴权/消息/推送代码前先读本节。

### (1) 角色判定：一律用「角色族」匹配，禁止精确相等

账号的角色码是**细粒度**的（V4 种子：`ADMIN_OPERATION`、`ADMIN_FINANCE`、`MERCHANT_MANAGER`、`MERCHANT_CLERK`、`RIDER`），而代码里声明的是**粗粒度族码**（`Roles.ADMIN`/`MERCHANT`/`RIDER`）。

判定必须写成：`role.equals(required) || role.startsWith(required + "_")`。

- **禁止** `roles.contains(Roles.MERCHANT)`、`Set.of("MERCHANT").equals(...)` 之类精确匹配。
- 已有实现：`RoleAuthInterceptor#matches`、`ReceiverType#matchesFamily`。新增角色判定**必须复用**，不要另写。
- 踩坑记录：`@RequireRole(MERCHANT)` 精确匹配导致商家所有接口 10004；`ReceiverType.resolve` 同样写法导致商家收不到站内信。

### (2) 主体标识：一律用「业务 ID」，禁止混用系统用户 ID

| 身份 | 语义 ID | 取自 |
|---|---|---|
| `C_USER` 消费者 | `t_member_user.id` | `principal.userId()` |
| `MERCHANT` 商家 | `t_merchant.id` | `principal.merchantId()` |
| `RIDER` 骑手 | `t_rider.id` | 按 `principal.userId()` 反查 |
| `ADMIN` 平台 | `t_sys_user.id` | `principal.userId()` |

统一出口：`solo-eats-app/.../security/CurrentReceiver#receiverId(principal)`。

凡是用于以下用途的 id，**必须**走这个出口：

- `t_site_message.receiver_id`
- `t_notify_setting.receiver_id`
- `WebSocketSessionRegistry` 的 key（`{receiverType}:{receiverId}`）
- `t_im_session.user_id` / `peer_id`
- `PushService.push(receiverType, receiverId, ...)` 的 `receiverId`

**禁止**在这些位置直接用 `principal.userId()`——商家/骑手的系统用户 ID 与业务 ID 不是同一个值。

踩坑记录：WS 注册用 `userId`（商家=4）、推送用 `merchantId`（=1），两套 key 对不上，商家与骑手的实时推送全部静默丢失，`t_push_log` 里表现为 `status=2 error_msg=offline`。

### (3) 校验顺序：先鉴权，再查业务对象

越权类接口必须**先**校验归属再返回数据，且要以「资源不存在」与「不属于你」区分得当（归属不符统一 `10004`）：

- 查 `deliveryId` 轨迹前，必须校验该配送单的 `order_id` 属于当前用户（app 层通过 `OrderQueryService#belongsToUser`）。
- 操作 `t_member_address`、`t_im_session` 等带 `user_id` 的资源前，必须比对 `user_id == 当前主体业务 ID`，**不得**只按主键读写。

## 5. 构建与验证命令

```bash
# 单模块增量编译（最常用）
mvn -q -pl solo-eats-<module> -am compile

# 全量打包（提交前必跑，不得回归）
mvn -q -DskipTests package

# 指定测试
mvn -q -pl solo-eats-<module> test -Dtest='XxxServiceImplTest'

# 前端
cd solo-eats-web && npm run build      # 含 vue-tsc --noEmit
cd solo-eats-web && npm run test:unit

# 移动端（各自目录）
cd solo-eats-uniapp-user && npm run build:h5
```
> VSCode Java 语言服务器会并发写 `target/`，本地跑全量测试可加 `-Pisolated-build` 输出到 `target-mvn`。

## 6. 数据库迁移约定

- 目录：`solo-eats-app/src/main/resources/db/migration/`
- 命名：`V<序号>__<snake_case_描述>.sql`（当前最大 `V3`，新迁移从 `V4` 起，**按任务卡指定序号**，不要抢号）。
- 只增不改（Expand-Contract）：**禁止** `ALTER TABLE ... DROP/MODIFY` 既有列。
- 已有库默认 `FLYWAY_ENABLED=false`；`baseline-on-migrate=false` 不得修改。
- 种子数据用 `INSERT ... ON DUPLICATE KEY UPDATE id=id` 保持可重复执行。

## 7. 任务执行纪律

1. **一次只做一个任务卡的一个阶段**（如 `T01 阶段A`）。开始前 `Read`：本文件 → `docs/tasks/README.md` → `docs/tasks/T<nn>-*.md` 全文 → 卡内「前置依赖」列出的类。
2. **不得修改与本任务无关的文件**；不得顺手重构、不得改代码风格、不得升级依赖版本。
3. **不得删除或修改既有测试**（`OrderLifecycleIntegrationTest`、`OrderStateMachineTest`、`SettlementServiceImplTest`、`CouponServiceImplTest` 等）。
4. 完成后必须自检：本模块编译通过 → `mvn -q -DskipTests package` 通过 → 涉及前端则 `npm run build` 通过。
5. 完成后回写任务卡末尾「执行记录」：日期 / 会话范围 / 新增修改文件数 / 编译结果 / 遗留问题。
6. 一个会话单元一个 commit：`feat(T01-A): 账号密码登录落地`。
7. 遇到任务卡「禁止事项」与需求冲突时，**停下来记录为遗留问题**，不要擅自突破。

## 8. 已实现能力（不要重复实现）

- `ApiResponse`/`PageResult`/`BizException`/`ErrorCode`/`GlobalExceptionHandler`
- JWT 签发与校验（`JwtUtils`/`JwtAuthFilter`/`AuthContext`/`UserPrincipal`）
- `@RequireRole` + `RoleAuthInterceptor`（角色级）；`PermissionGuard`（数据级越权）
- `@Idempotent` + `IdempotentAspect`（基于 `t_idempotency`）
- `DistributedLock`（Redisson）、`@RateLimit`、`TraceId` 全链路
- 订单状态机 `OrderStateMachine`（**禁止修改**）、退款 `RefundService`、支付 Mock 网关
- 库存 `SkuService`/`StockService`（锁定/确认/释放/回补 + 流水）
- 结算 `SettlementService`（账单生成/查询）、券 `CouponService`（锁定/核销/回滚）
- 配送 `DeliveryService`（配送单状态流转）

## 9. 参考文档

| 文档 | 用途 |
|---|---|
| `docs/外卖需求规格说明书.md` | 业务真相源（优先级 1.6、流程 8.x、状态机 8.9、约定 9.x） |
| `docs/技术架构与数据库设计说明书.md` | 表结构（2.x）、伪代码与状态机（3.x）、并发规范（4.x） |
| `docs/tasks/README.md` | 模块拆解矩阵 + 任务总表 + 执行顺序 |
| `docs/tasks/T<nn>-*.md` | 单任务卡（目标/数据模型/接口/文件清单/约束/验收/禁止） |
| `docs/全栈启动与体验指南.md` | 本地起服务、端口、健康检查 |
| `docs/订单接口联调与验收.md` 等 | 既有验收用例（回归基线） |
