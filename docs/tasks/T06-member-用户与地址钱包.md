# T06 member-用户 · 收货地址 · 钱包账单

> 优先级：**P0** · 所属模块：`solo-eats-member`（新建）+ `solo-eats-app`
> 单段执行（约 20 个文件）

---

## 目标

**一句话**：建立 C 端消费者账号体系（手机号 + 模拟验证码登录）、收货地址簿、钱包余额与流水，并让 `t_order.user_id` 有一个**真实存在**的用户源。

**完成后系统能做什么**
1. 用户用手机号 + 6 位验证码登录（开发环境验证码固定 `000000` 或走邮件），拿到 `roles=[C_USER]` 的 token，`userId` 即 `t_member_user.id`。
2. 用户维护收货地址（最多 20 条、1 条默认、支持地图选点经纬度与标签）。
3. 用户查看钱包余额与收支流水；P0 支持「模拟充值」入账（供支付方式“余额支付”使用）。
4. 平台可查 C 端用户列表、禁用/拉黑、手工调账（写审批日志）。
5. **下单链路不回归**：现有 `CreateOrderRequest`/`OrderService` 用 `userId` 的逻辑保持不变，demo 用户 `id=1` 迁移脚本保证存在。

---

## 规格书依据

| 章节 | 原文要点 |
|---|---|
| 5.2.1 启动与账号 | 「手机号登录：验证码登录/注册一体」（替换：开发环境固定验证码或邮件验证码）；「注销账号：注销申请与冷静期」 |
| 5.2.5 购物车与结算 · 收货地址 | 「选择/新增、地图选点、门牌号、标签（家/公司）、定位门牌号」 |
| 5.2.9 我的 | 「个人信息：头像、昵称、手机号、性别、生日；我的钱包：余额、充值（档位+赠送）、余额明细；收货地址：列表、增删改、设为默认」 |
| 4.7 用户中心 | 「用户管理：用户列表、详情（消费画像：订单数/客单价/最近下单/偏好品类）、编辑、禁用、拉黑；地址管理；钱包与余额：余额查询、充值记录、消费/退款/调整流水，**手工调账（需审批与日志）**」 |
| 2.5 核心领域模型 | 「用户、会员档案（等级/成长值）、地址、钱包、账单、标签分群；用户 1:1 钱包；用户 1:N 地址」 |
| 9.7 金额规范 | 「金额一律以分（整数）存储与计算」 |
| 9.11 合规 | 手机号属个人信息，需加密存储 + 展示脱敏 + 留存可追溯 |

---

## 前置依赖

| 依赖 | 具体依赖物 |
|---|---|
| **T01** | `JwtUtils`（签发 C_USER token）、`AuthContext`、`@RequireRole(Roles.C_USER)` |
| **T02 阶段A** | `TenantContext`（新表带 `tenant_id`） |
| **T03 阶段A** | `FileStoragePort`（头像上传） |
| 既有表 | `t_order.user_id`、`t_user_coupon.user_id`、`t_payment_order`（可能有 `user_id`）—— **都引用一个不存在的用户表**，本卡必须回填 `t_member_user.id=1` |
| 既有代码（不可改语义） | `solo-eats-order/service/OrderApplicationService.java`（取 `AuthContext` 的 `userId` 下单）、`solo-eats-app/controller/AuthController.java`（**新增 C 端登录不能与它冲突**） |
| 既有文档 | `docs/订单接口联调与验收.md` 用 `userId=1` 下单 |

---

## 数据模型

迁移脚本：`V11__member.sql`

| 表 | 关键字段 | 索引 |
|---|---|---|
| `t_member_user` | id, tenant_id, **phone_cipher varchar(128)**（AES-GCM）, **phone_hash char(64)**（唯一用，SHA-256(phone+salt)）, **phone_mask varchar(20)**（`138****8000`，展示用）, nickname, avatar_url, gender tinyint(0未知 1男 2女), birthday date, status tinyint(0正常 1禁用 2已注销), register_source tinyint(1手机号 2后台导入), member_level_id bigint NULL（P1 T12）, **growth_value int DEFAULT 0**, order_count int, total_amount bigint（累计消费，分）, last_order_at, last_login_at, cancel_apply_at（注销冷静期起点）, version, created_at/updated_at/deleted | `uk_phone_hash(tenant_id, phone_hash)`、`idx_status(status)`、`idx_created(created_at)` |
| `t_member_address` | id, tenant_id, user_id, receiver_name, **receiver_phone_cipher**, receiver_phone_mask, province, city, district, address, **house_number varchar(64)**, **lng decimal(10,6)**, **lat decimal(10,6)**, **tag varchar(16)**（家/公司/学校）, **is_default tinyint**, deleted, created_at/updated_at | `idx_user(user_id, is_default)`、`uk_user_phone_addr(user_id, receiver_phone_mask, address)` |
| `t_member_wallet` | id, tenant_id, user_id, **balance bigint NOT NULL DEFAULT 0**, **frozen_amount bigint NOT NULL DEFAULT 0**, total_recharge bigint, total_consume bigint, version, created_at/updated_at | `uk_user(user_id)` |
| `t_member_wallet_log` | id, tenant_id, user_id, **biz_type varchar(16)**（RECHARGE/CONSUME/REFUND/ADJUST/WITHDRAW/FREEZE/UNFREEZE）, **biz_id bigint**, **request_id varchar(64)**（幂等键）, **direction tinyint(1收入 2支出)**, **amount bigint**, **before_balance bigint**, **after_balance bigint**, remark varchar(255), operator_id, operator_name, created_at | `uk_biz(biz_type, biz_id, request_id)`、`idx_user_time(user_id, created_at)` |
| `t_member_sms_code` | id, tenant_id, phone_hash, code varchar(8), scene tinyint(1登录 2入驻 3改绑), expire_at, used tinyint, ip, created_at | `idx_phone_scene(phone_hash, scene, created_at)` |

> `t_member_user` 与 `t_sys_user` **严格分离**：`t_sys_user.user_type` 只有 1/2/3（平台/商家/骑手），消费者只在 `t_member_user`。两个体系靠 token 的 `roles` 区分（`C_USER` vs 其他）。

---

## 接口清单

| # | 方法 | 路径 | 要点 | 权限 |
|---|---|---|---|---|
| 1 | POST | `/api/v1/c/auth/sms-code` | `{phone, scene}` → 空；限流 1 次/分/手机号、5 次/小时/手机号；开发环境 `app.auth.mock-sms-enabled=true` 时固定 `000000` 且**不真发** | 白名单 |
| 2 | POST | `/api/v1/c/auth/login` | `{phone, code}` → `{token, tokenType, expiresIn, userId, nickname, isNewUser, roles}`；不存在则自动注册 | 白名单 + 限流 10/分/IP |
| 3 | POST | `/api/v1/c/auth/logout` | 写 JWT 黑名单 | `C_USER` |
| 4 | GET | `/api/v1/c/users/me` | 资料（手机号脱敏） | `C_USER` |
| 5 | PUT | `/api/v1/c/users/me` | `{nickname, avatarUrl, gender, birthday}` | `C_USER` |
| 6 | PUT | `/api/v1/c/users/me/phone` | `{newPhone, code}` 换绑 | `C_USER` |
| 7 | POST | `/api/v1/c/users/me/cancel` | 注销申请（写 `cancel_apply_at`，冷静期 7 天） | `C_USER` |
| 8 | GET | `/api/v1/c/addresses` | 地址列表（默认地址排首） | `C_USER` |
| 9 | POST | `/api/v1/c/addresses` | `{receiverName,receiverPhone,province,city,district,address,houseNumber,lng,lat,tag,isDefault}`；上限 20 条 | `C_USER` |
| 10 | PUT | `/api/v1/c/addresses/{id}` | 同上；越权（非本人）返回 10004 | `C_USER` |
| 11 | DELETE | `/api/v1/c/addresses/{id}` | 逻辑删除；被未完成订单引用时仅软删不阻断 | `C_USER` |
| 12 | PUT | `/api/v1/c/addresses/{id}/default` | 设默认（同用户其余置 0，同事务） | `C_USER` |
| 13 | GET | `/api/v1/c/wallet` | `{balance, frozenAmount, totalRecharge, totalConsume}` | `C_USER` |
| 14 | GET | `/api/v1/c/wallet/logs` | `bizType,dateFrom,dateTo,pageNum,pageSize` → `PageResult<WalletLogView>` | `C_USER` |
| 15 | POST | `/api/v1/c/wallet/recharge` | `{amount}` 模拟充值（`app.wallet.mock-recharge-enabled=true`） | `C_USER` + `@Idempotent` |
| 16 | GET | `/api/v1/admin/members` | `status,keyword(手机号后四位),levelId,pageNum,pageSize` | `member:user:list` |
| 17 | GET | `/api/v1/admin/members/{id}` | 详情（订单数/客单价/最近下单/地址数/钱包） | `member:user:detail` |
| 18 | PUT | `/api/v1/admin/members/{id}/status` | `{status}` 0正常 1禁用 | `member:user:update` |
| 19 | GET | `/api/v1/admin/members/{id}/wallet/logs` | 调账/流水审计 | `member:wallet:list` |
| 20 | POST | `/api/v1/admin/members/{id}/wallet/adjust` | `{amount（可负）,reason,requestId}` 手工调账 → 写流水 + `t_sys_oper_log` | `member:wallet:adjust` + `@Idempotent` |

**新增错误码**（8xxxx 或复用 1xxxx；建议新增 8xxxx 段）：`80001 MEMBER_NOT_FOUND`、`80002 MEMBER_DISABLED`、`80003 SMS_CODE_INVALID`、`80004 SMS_CODE_EXPIRED`、`80005 ADDRESS_LIMIT_EXCEEDED`、`80006 WALLET_BALANCE_NOT_ENOUGH`、`80007 WALLET_AMOUNT_INVALID`。

---

## 需要新增/修改的文件

| 类型 | 路径 |
|---|---|
| 改 | `pom.xml`（`<modules>` 增 `solo-eats-member`；`dependencyManagement` 增坐标） |
| 新 | `solo-eats-member/pom.xml`（依赖 `solo-eats-common`、`solo-eats-system`、`mybatis-plus`、`lombok`） |
| 改 | `solo-eats-app/pom.xml`（依赖 `solo-eats-member`） |
| 新 | `solo-eats-app/src/main/resources/db/migration/V11__member.sql`（含 **`t_member_user` id=1 回填**：`phone_mask='138****8000'`，保证 `t_order.user_id=1` 有主） |
| 新 | `solo-eats-member/src/main/java/com/soloeats/member/domain/entity/MemberUser.java` |
| 新 | `solo-eats-member/src/main/java/com/soloeats/member/domain/entity/MemberAddress.java` |
| 新 | `solo-eats-member/src/main/java/com/soloeats/member/domain/entity/MemberWallet.java` |
| 新 | `solo-eats-member/src/main/java/com/soloeats/member/domain/entity/MemberWalletLog.java` |
| 新 | `solo-eats-member/src/main/java/com/soloeats/member/domain/entity/MemberSmsCode.java` |
| 新 | `solo-eats-member/src/main/java/com/soloeats/member/domain/enums/WalletBizType.java`、`WalletDirection.java`、`SmsScene.java` |
| 新 | `solo-eats-member/src/main/java/com/soloeats/member/mapper/` 5 个 Mapper |
| 新 | `solo-eats-member/src/main/java/com/soloeats/member/support/PhoneCipher.java`（AES-GCM 加解密 + SHA-256 hash + 脱敏，**唯一入口**） |
| 新 | `solo-eats-member/src/main/java/com/soloeats/member/support/SmsCodeGenerator.java`（`mock-sms-enabled` 时固定 `000000`，否则 6 位随机） |
| 新 | `solo-eats-member/src/main/java/com/soloeats/member/service/MemberAuthService.java` + `impl` |
| 新 | `solo-eats-member/src/main/java/com/soloeats/member/service/MemberProfileService.java` + `impl` |
| 新 | `solo-eats-member/src/main/java/com/soloeats/member/service/MemberAddressService.java` + `impl` |
| 新 | `solo-eats-member/src/main/java/com/soloeats/member/service/MemberWalletService.java` + `impl` |
| 新 | `solo-eats-member/src/main/java/com/soloeats/member/service/command/`（`SmsLoginCommand`、`SaveAddressCommand`、`WalletChangeCommand`、`AdjustWalletCommand`） |
| 新 | `solo-eats-member/src/main/java/com/soloeats/member/service/view/`（`SmsLoginResult`、`MemberView`、`MemberDetailView`、`AddressView`、`WalletView`、`WalletLogView`） |
| 新 | `solo-eats-app/src/main/java/com/soloeats/app/controller/ConsumerAuthController.java`（`/api/v1/c/auth/**`） |
| 新 | `solo-eats-app/src/main/java/com/soloeats/app/controller/ConsumerUserController.java`（`/api/v1/c/users/**`） |
| 新 | `solo-eats-app/src/main/java/com/soloeats/app/controller/ConsumerAddressController.java` |
| 新 | `solo-eats-app/src/main/java/com/soloeats/app/controller/ConsumerWalletController.java` |
| 新 | `solo-eats-app/src/main/java/com/soloeats/app/controller/AdminMemberController.java` |
| 新 | `solo-eats-app/src/main/java/com/soloeats/app/dto/SmsCodeRequest.java`、`SmsLoginRequest.java`、`SaveAddressRequest.java`、`AdjustWalletRequest.java` |
| 新 | `solo-eats-member/src/test/java/com/soloeats/member/support/PhoneCipherTest.java` |
| 新 | `solo-eats-member/src/test/java/com/soloeats/member/service/impl/MemberWalletServiceImplTest.java`（并发扣减、余额不足、幂等） |

---

## 关键实现约束

| 项 | 约束 |
|---|---|
| **手机号存储** | 三列方案：`phone_cipher`（AES-GCM，可解密用于必要场景）、`phone_hash`（SHA-256 + 全局盐，用于唯一键与查询）、`phone_mask`（展示）。**任何接口都不得返回明文手机号**；内部服务用 `PhoneCipher.decrypt()` |
| **登录流程** | `sms-code` 生成 → 落 `t_member_sms_code`（`expire_at = now + 5min`，`used=0`）→ `login` 校验：存在、未用、未过期、code 一致 → 置 `used=1` → 查/建用户。**同一 code 只能用一次**（条件更新 `WHERE id=? AND used=0`，影响行数 0 即失败） |
| **验证码限流** | Redis 计数 `sms:limit:{phoneHash}`（1/分）、`sms:daily:{phoneHash}`（5/小时）；超限抛 `10005 RATE_LIMITED`。**不得**只靠接口层 `@RateLimit`（它是按 IP 的） |
| **自动注册** | 用户不存在时自动注册：`t_member_user` + `t_member_wallet`（余额 0）**同一事务**创建；并发首登靠 `uk_phone_hash` 兜底，捕获 `DuplicateKeyException` 后重查 |
| **token** | 复用 `JwtUtils.issue(new UserPrincipal(userId, null, null, null, Set.of(Roles.C_USER)))`；**不新增签发器**；token 与商家/平台 token 可互换解析，靠 `roles` 鉴权 |
| **地址默认** | 设默认必须同事务：`UPDATE ... SET is_default=0 WHERE user_id=?` 然后 `UPDATE ... SET is_default=1 WHERE id=? AND user_id=?`。**首个地址自动为默认** |
| **地址越权** | 所有 `/c/addresses/{id}` 操作必须先校验 `address.userId == AuthContext.userId()`，否则 `10004`；**不得**只按 id 更新 |
| **钱包并发** | 扣减必须 `UPDATE t_member_wallet SET balance = balance - ?, version = version + 1 WHERE user_id = ? AND balance >= ? AND version = ?`，影响行数 0 → 重试 3 次 → 抛 `80006`。**禁止**先 `SELECT` 再 `UPDATE` |
| **钱包流水** | 每次余额变动必须写一条 `t_member_wallet_log`，含 `before_balance/after_balance`，同事务；`uk_biz(biz_type, biz_id, request_id)` 保证幂等 |
| **金额边界** | `amount > 0`（调账可负但 `|amount| ≤ 100000 分` 即 1000 元）；充值 `1 分 ≤ amount ≤ 100000 分`；`balance` 任何时刻 ≥ 0 |
| **调账审计** | `adjust` 必须写 `t_sys_oper_log`（复用 T02 的 `@OperationLog(module="member", action="adjust")`），并在 `wallet_log.remark` 里记 `操作人 + 原因` |
| **注销** | P0 只登记 `cancel_apply_at` + `status=2`，**不物理删除**；冷静期内可登录撤销（`PUT /c/users/me/cancel` 反向）。数据保留策略留 P1 |
| **多租户** | 全部新表带 `tenant_id`；C 端用户按 `tenant_id` 隔离（当前单租户=1） |
| **缓存** | `t_member_user` 不缓存（写多读少）；手机号 hash 反查走唯一索引 |
| **消费画像** | `order_count/total_amount/last_order_at` 由订单完成事件异步更新；本卡提供 `MemberStatPort` 接口 + 空实现 + `TODO(T07/order)`，**不得**让 member 模块依赖 order 模块（依赖方向） |

---

## 验收标准

```bash
mvn -q -pl solo-eats-member -am compile
mvn -q -DskipTests package

# 1) 发验证码（mock 模式）
curl -s -X POST http://localhost:8080/api/v1/c/auth/sms-code \
  -d '{"phone":"13800000002","scene":1}'
# 断言：code=0；数据库 t_member_sms_code 新增 1 行，code='000000'

# 2) 登录（自动注册）
curl -s -X POST http://localhost:8080/api/v1/c/auth/login -d '{"phone":"13800000002","code":"000000"}'
# 断言：code=0；isNewUser=true；userId 非空；roles 含 C_USER

# 3) 复用同一验证码 → 失败
curl -s -X POST http://localhost:8080/api/v1/c/auth/login -d '{"phone":"13800000002","code":"000000"}'
# 断言：code=80003 或 80004

# 4) 手机号脱敏
curl -s http://localhost:8080/api/v1/c/users/me -H "Authorization: Bearer $USER_TOKEN"
# 断言：响应中 phone="138****0002"，无明文

# 5) 地址
curl -s -X POST http://localhost:8080/api/v1/c/addresses -H "Authorization: Bearer $USER_TOKEN" \
  -d '{"receiverName":"李四","receiverPhone":"13900000003","province":"北京市","city":"北京市",
       "district":"朝阳区","address":"望京SOHO T1","houseNumber":"1801","lng":116.481499,"lat":39.997632,"tag":"公司"}'
curl -s http://localhost:8080/api/v1/c/addresses -H "Authorization: Bearer $USER_TOKEN"
# 断言：第一条 isDefault=true

# 6) 越权
curl -s -X PUT http://localhost:8080/api/v1/c/addresses/999 -H "Authorization: Bearer $USER_TOKEN" -d '{...}'
# 断言：code=10004

# 7) 钱包
curl -s http://localhost:8080/api/v1/c/wallet -H "Authorization: Bearer $USER_TOKEN"
curl -s -X POST http://localhost:8080/api/v1/c/wallet/recharge -H "Authorization: Bearer $USER_TOKEN" \
  -H "Idempotency-Key: rc-001" -d '{"amount":10000}'
# 断言：balance=10000；重复同 key 不重复入账；t_member_wallet_log 仅 1 条

# 8) 余额不足
# 调 MemberWalletService.deduct(userId, 999999, ...) → 80006；余额不变

# 9) 订单链路不回归（关键）
curl -s -X POST http://localhost:8080/api/v1/orders -H "Authorization: Bearer $USER_TOKEN" \
  -H "Idempotency-Key: ord-regress-01" -d @docs/...（用现有下单用例，userId=1）
# 断言：code=0；t_member_user id=1 存在
```

单元测试断言：
- `PhoneCipherTest`：加密→解密回原文；同手机号 hash 稳定；mask 格式正确
- `MemberWalletServiceImplTest`：并发 10 线程扣 100 分 → 只成功 N 次，余额不为负；同 `requestId` 幂等

---

## 禁止事项

1. **不得删除 `t_member_user.id=1` 的种子数据**——`V2__sku_and_demo_data.sql` 的 `t_user_coupon.user_id=1`、`docs/订单接口联调与验收.md`、`OrderLifecycleIntegrationTest` 全部依赖它。
2. **不得**把 C 端用户塞进 `t_sys_user`（`user_type` 只允许 1/2/3），否则 T01 的角色校验与商家/骑手数据权限会串。
3. **不得修改** `/api/v1/auth/login`（mock 登录）与 `/api/v1/orders/**` 的任何契约。
4. **不得**明文存储或返回手机号；不得在日志打印手机号明文。
5. 不得用 `SELECT` + `UPDATE` 的非原子方式扣余额。
6. 不得让 `solo-eats-member` 依赖 `solo-eats-order`/`product`/`marketing`（用端口接口 + TODO 隔离）。
7. 不得在 `t_member_user` 上做物理删除。
8. 不得把 mock 验证码开关默认设为 `true` 后写入生产 profile（只允许 local/dev）。
9. 不得在本卡实现会员等级/成长值逻辑（T12 范围），只保留 `member_level_id`/`growth_value` 列。
10. 不得改动 `solo-eats-order`/`payment`/`settlement` 代码。

---

## 执行记录

| 日期 | 会话范围 | 新增/修改文件 | 编译结果 | 遗留问题 |
|---|---|---|---|---|
| _待填_ | | | | |
