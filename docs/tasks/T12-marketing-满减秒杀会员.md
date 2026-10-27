# T12 marketing-满减 · 折扣 · 秒杀 · 红包 · 签到 · 邀请 · 会员 · 积分

> 优先级：**P0（阶段A-0：平台级单档满减 + 试算/分摊，供下单链路）** / **P1（阶段A：满减/折扣 + 秒杀）→ 阶段B（会员等级/成长值/权益 + 积分/签到/邀请/红包）**
> 所属模块：`solo-eats-marketing`（增量改造）+ `solo-eats-app`
> 分三段：**阶段A-0（P0 提前包，约 3 个文件）→ 阶段A（满减/折扣 + 秒杀）→ 阶段B（会员等级/成长值/权益 + 积分/签到/邀请/红包）**
> 说明：README Q3 已确认「P0 只做平台级单档满减」；9.8 优惠分摊规则要求满减参与分摊，故必须在下单链路（批次 4 前）提前交付阶段A-0，否则 P0 满减分摊无着落。规格书 1.6 平台后台 P0 亦含「基础券」——券模板/用户券**实体与 C 端核销逻辑**已既有实现，本卡不重复；其**管理页面**归 T17 阶段A 提级（见 T17）。
>
> **2026-10-26 勘误**：原文「券模板/用户券后端已既有实现」只对了一半。实测 `CouponService` 只有 C 端 `lock/release/consume/rollback` 四个方法，`CouponTemplateMapper` 没有对外 CRUD，全仓库 61 个 Controller 中**没有任何券后台接口**（无 `AdminCouponController`、无 `/api/v1/admin/coupons`）。因此 T17 的券管理页无接口可调。为此本卡新增 **阶段A-1（券后台管理）**，补齐券模板 CRUD + 定向发券 + 领取记录查询，作为 T17 批次0 券页的前置。

---

## 目标

**一句话**：在既有「券模板 + 用户券」之上，补齐满减/折扣/秒杀的**活动引擎**与会员/积分的**用户资产引擎**，并保证秒杀不超卖、优惠分摊可复算。

**完成后系统能做什么**
1. 平台/商家可建满减（阶梯）与折扣活动，绑定商品范围，配叠加开关。
2. 秒杀场次开卖时 200 QPS 不超卖，Redis 预扣 + DB 条件更新双保险。
3. 六档会员等级按近 365 天成长值季度更新，权益（神券张数/免配送费次数）按等级可配。
4. 积分可获取/消耗/过期，签到连续加码，邀请有礼防刷。
5. 下单时营销试算接口返回可用活动 + 分摊明细，供 `order` 模块写 `t_order_discount`。

---

## 规格书依据

| 章节 | 原文要点 |
|---|---|
| 4.6 营销中心 | 「满减活动：平台/商家、阶梯满减、叠加规则、活动时间；折扣活动：单品/分类折扣、限购；秒杀/限时特价：场次配置、商品与数量、限购、预热提醒、**库存独立**（练习重点：并发扣减）；红包：拼手气/分享/新人，领取核销、风控防刷；会员等级体系：**六档（普通/白银/黄金/铂金/黑金/黑钻）**、成长值门槛、小星级、升降级；会员权益：神券张数、免配送费次数、专享价、专属客服、生日礼遇；成长值规则：消费 1 元=1 成长值、签到+10、评价+20/单、邀请+50/人，近 365 天、季度更新；积分体系：获取（下单/签到/评价/邀请）、消耗、过期；签到有礼：连续签到、补签；邀请有礼：邀请码/海报、双方奖励、防刷」 |
| 9.8 优惠叠加与分摊 | 「券、满减、会员价、活动价默认互斥：同单最多 1 张券 + 1 个满减，由后台『叠加开关』控制；优先级：会员价 > 活动价 > 券 > 满减；门槛按**折后商品小计**判断，不含配送费与打包费；成本归属：平台券→平台，商家券→商家；分摊结果写入订单明细；保底实付 ≥ 0.01 元」 |
| 9.7 金额与精度 | 「优惠分摊按商品折后金额比例分摊，余数（±分）归入金额最大的商品，保证明细合计=实付；只允许服务端最终计算一次」 |
| 1.6 优先级 | P1：满减、会员体系（等级/成长值/神券）；P2：秒杀、红包、签到/邀请、积分商城、充值 |
| 2.5 核心领域模型 | 「券模板、用户券、活动（满减/折扣/秒杀）、红包、会员神券包、积分账户、积分流水；用户券 N:1 券模板；活动 N:M 商品」 |

---

## 前置依赖

| 依赖 | 具体依赖物 |
|---|---|
| **T05** | `Spu`/`Sku`/`Category`（活动商品范围） |
| **T06** | `MemberUser`（成长值/等级字段已在 T06 预留 `member_level_id`/`growth_value`） |
| **T01/T02** | 鉴权、`TenantContext` |
| 既有表（**不可改结构**） | `t_coupon_template`、`t_user_coupon`、`t_user_coupon_log` |
| 既有类（**不可改签名**） | `com.soloeats.marketing.domain.entity.CouponTemplate`、`UserCoupon`；`service/CouponService`、`CouponLockResult`、`impl/CouponServiceImpl`；`mapper/CouponTemplateMapper`、`UserCouponMapper`；测试 `CouponServiceImplTest` |
| 既有调用方 | `solo-eats-order` 下单时调用 `CouponService.lock()/use()/rollback()`；`t_order_discount.discount_type` 已定义 `1券 2满减 3会员 4自取立减` |
| 依赖方向 | `marketing → product, member`；**禁止** `marketing → order` |

---

## 数据模型

迁移脚本：`V18__marketing_promotion.sql`（阶段A）、`V19__marketing_member_point.sql`（阶段B）

### 阶段A

| 表 | 关键字段 | 索引 |
|---|---|---|
| `t_promotion` | id, tenant_id, **promotion_type tinyint(1满减 2折扣 3新人立减)**, promotion_name, **bearer tinyint(1平台 2商家)**, merchant_id NULL, shop_id NULL, **scope_type tinyint(1全平台 2指定商家 3指定门店 4指定品类 5指定商品)**, scope_json json, **start_at**, **end_at**, **stackable tinyint**（是否可与其他活动叠加）, **priority int**, status tinyint(0草稿 1进行中 2已结束 3暂停), total_budget bigint NULL, used_budget bigint, created_at/updated_at/deleted | `idx_status_time(status, start_at, end_at)`、`idx_shop(shop_id)` |
| `t_promotion_rule` | id, tenant_id, promotion_id, **threshold_amount bigint**（门槛，分）, **discount_amount bigint NULL**（满减金额）, **discount_rate decimal(4,2) NULL**（折扣率 0.85）, **max_discount bigint NULL**（折扣封顶）, sort, created_at | `idx_promotion_sort(promotion_id, sort)` |
| `t_promotion_scope` | id, tenant_id, promotion_id, **ref_type tinyint(1商家 2门店 3品类 4SPU 5SKU)**, ref_id bigint | `uk_promo_ref(promotion_id, ref_type, ref_id)`、`idx_ref(ref_type, ref_id)` |
| `t_seckill_activity` | id, tenant_id, activity_name, **session_start**, **session_end**, **preheat_at**, status tinyint(0待开始 1进行中 2已结束), created_at/updated_at/deleted | `idx_time(session_start, session_end)` |
| `t_seckill_item` | id, tenant_id, activity_id, shop_id, spu_id, sku_id, **seckill_price bigint**, **seckill_stock int**, **sold_qty int**, **limit_per_user int**, version, created_at/updated_at | `uk_activity_sku(activity_id, sku_id)`、`idx_activity(activity_id, sold_qty)` |
| `t_seckill_order` | id, tenant_id, activity_id, item_id, user_id, order_id NULL, qty, status tinyint(0预占 1已支付 2已释放), created_at | `uk_user_item(user_id, item_id)`（每人每场限购兜底） |

### 阶段B

| 表 | 关键字段 | 索引 |
|---|---|---|
| `t_member_level` | id, tenant_id, **level_code varchar(16)**, level_name, **level_no tinyint(1~6)**, **growth_threshold int**, **star tinyint(1~3)**, **benefits_json json**（`{"couponCount":4,"freeDeliveryTimes":3,"exclusiveService":true}`）, icon_url, sort, status, created_at/updated_at/deleted | `uk_tenant_level(tenant_id, level_code)`、`idx_no(level_no)` |
| `t_member_growth_log` | id, tenant_id, user_id, **source tinyint(1消费 2签到 3评价 4邀请 5手工调整)**, biz_id, **growth int**（可负）, **before_value**, **after_value**, effective_at, expire_at（消费类 365 天后失效）, created_at | `idx_user_time(user_id, created_at)`、`idx_expire(expire_at)` |
| `t_member_benefit_grant` | id, tenant_id, user_id, level_id, **benefit_type tinyint(1神券 2免配送费 3专属客服 4生日礼遇)**, **period varchar(8)**（`2025-01`）, grant_qty int, used_qty int, created_at | `uk_user_benefit(user_id, benefit_type, period)` |
| `t_point_account` | id, tenant_id, user_id, **balance int**, total_earned bigint, total_consumed bigint, version, created_at/updated_at | `uk_user(user_id)` |
| `t_point_log` | id, tenant_id, user_id, **source tinyint(1下单 2签到 3评价 4邀请 5兑换 6过期 7手工)**, biz_id, **point int**（可负）, before_balance, after_balance, expire_at, created_at | `uk_biz(source, biz_id)`（幂等）、`idx_user_time(user_id, created_at)` |
| `t_checkin_record` | id, tenant_id, user_id, **checkin_date date**, **continuous_days int**, point_award int, created_at | `uk_user_date(user_id, checkin_date)` |
| `t_invite_record` | id, tenant_id, inviter_id, **invitee_id**, **invite_code varchar(16)**, **status tinyint(0已注册 1首单已完成 2无效)**, reward_point int, reward_growth int, created_at | `uk_invitee(invitee_id)`、`idx_inviter(inviter_id)` |
| `t_red_packet` | id, tenant_id, **packet_type tinyint(1拼手气 2新人 3分享)**, total_amount bigint, total_qty int, remain_qty int, start_at, end_at, status, created_at/updated_at | `idx_status_time(status, start_at, end_at)` |
| `t_user_red_packet` | id, tenant_id, packet_id, user_id, **amount bigint**, status tinyint(0未用 1已用 2过期), order_id NULL, expire_at, created_at | `idx_user_status(user_id, status, expire_at)` |

---

## 接口清单

| # | 方法 | 路径 | 要点 | 权限 |
|---|---|---|---|---|
| 1 | GET/POST/PUT | `/api/v1/admin/promotions`（+`/{id}`） | 满减/折扣活动 CRUD（含 `rules[]` 阶梯、`scope`） | `marketing:promotion:*` |
| 2 | PUT | `/api/v1/admin/promotions/{id}/status` | 开始/暂停/结束 | `marketing:promotion:update` |
| 3 | GET | `/api/v1/c/promotions?shopId=&spuIds=` | 用户端可见活动（进行中 + 命中范围） | 白名单 |
| 4 | POST | `/api/v1/c/marketing/calculate` | `{shopId,items[{skuId,qty}],userCouponId?,addressOk}` → `{promotions[],discountTotal,apportion[]}` **营销试算** | `C_USER` |
| 5 | GET/POST | `/api/v1/admin/seckill/activities`（+`/{id}`） | 秒杀场次 + 商品 | `marketing:seckill:*` |
| 6 | GET | `/api/v1/c/seckill/sessions` | 场次列表（含预热中/进行中） | 白名单 |
| 7 | POST | `/api/v1/c/seckill/{itemId}/reserve` | 预占库存（Redis `DECR` + DB 条件更新），返回 `reserveToken` | `C_USER` + 限流 |
| 8 | POST | `/api/v1/c/seckill/{itemId}/release` | 释放预占（下单失败/超时） | `C_USER` |
| 9 | GET/PUT | `/api/v1/admin/member-levels`（+`/{id}`） | 六档等级与权益配置 | `marketing:member:*` |
| 10 | GET | `/api/v1/c/member/profile` | 我的等级、成长值、距下一级差值、权益 | `C_USER` |
| 11 | GET | `/api/v1/c/member/growth-logs` | 成长值明细 | `C_USER` |
| 12 | GET | `/api/v1/c/points`、`/points/logs` | 积分余额与明细 | `C_USER` |
| 13 | POST | `/api/v1/c/checkin` | 签到（连续加码）；`/checkin/compensate` 补签 | `C_USER` + `@Idempotent` |
| 14 | GET | `/api/v1/c/invite/code`、`POST /api/v1/c/invite/bind` | 邀请码生成/绑定 | `C_USER` |
| 15 | GET | `/api/v1/c/red-packets`、`POST /api/v1/c/red-packets/{id}/grab` | 红包列表/领取（拼手气：二倍均值法） | `C_USER` |
| 16 | GET | `/api/v1/admin/marketing/stats` | 券核销率、活动带来的订单与 GMV、成本 ROI | `marketing:stats:view` |
| 17 | POST | `/api/v1/admin/member-levels/recalc` | 手动触发季度等级重算 | `marketing:member:update` |

**新增错误码**（4xxxx 追加）：`40201 PROMOTION_NOT_AVAILABLE`、`40202 PROMOTION_THRESHOLD_NOT_MET`、`40203 SECKILL_OUT_OF_STOCK`、`40204 SECKILL_LIMIT_EXCEEDED`、`40205 SECKILL_NOT_STARTED`、`40206 POINT_NOT_ENOUGH`、`40207 CHECKIN_ALREADY_DONE`、`40208 INVITE_INVALID`。

---

## 需要新增/修改的文件

### 阶段A-0（P0 提前包，约 3 个：平台级单档满减 + 试算/分摊）

| 类型 | 路径 |
|---|---|
| 新 | `solo-eats-app/src/main/resources/db/migration/V18__marketing_promotion.sql`（`t_promotion`、`t_promotion_rule`、`t_promotion_scope`、`t_seckill_activity`、`t_seckill_item`、`t_seckill_order` 六张一次建齐；阶段A-0 只填充满减相关，秒杀表由阶段A 使用） |
| 新 | `solo-eats-marketing/src/main/java/com/soloeats/marketing/domain/entity/Promotion.java`、`PromotionRule.java`、`PromotionScope.java` |
| 新 | 对应 3 个 Mapper |
| 新 | `.../service/PromotionService.java` + `impl`（**P0 版仅支持平台级单档满减**：活动匹配 + 试算 + 分摊；阶梯/折扣/商家满减在阶段A 补齐） |
| 新 | `.../service/DiscountApportioner.java`（分摊算法：按折后金额比例 + 余数归最大项，**与 9.7 一致**） |
| 新 | `.../service/view/MarketingCalculateResult.java`、`DiscountApportionItem.java` |
| 新 | `solo-eats-app/src/main/java/com/soloeats/app/controller/MarketingClientController.java`（`POST /c/marketing/calculate` 试算，供下单链路调用） |
| 新 | `solo-eats-marketing/src/test/java/com/soloeats/marketing/service/DiscountApportionerTest.java`（余数归最大项、合计=总额、单项全免三组） |

> 阶段A-0 必须在 **order 模块下单链路（批次 4）** 前完成；否则 P0 满减无法参与 9.8 分摊。

### 阶段A-1（P0 补充包，约 7 个：券后台管理）

> 交付理由见本卡开头 2026-10-26 勘误。T17 批次0 的「基础券管理页」依赖本阶段接口，故必须先于 T17 批次0 交付。
> **只补后台读写入口**：`t_coupon_template` / `t_user_coupon` / `t_user_coupon_log` 三张表结构与 `CouponService` 的 C 端四个方法（`lock/release/consume/rollback`）**一律不动**。

**接口清单**

| # | 方法 | 路径 | 要点 | 权限 |
|---|---|---|---|---|
| A1-1 | GET | `/api/v1/admin/coupons` | 模板分页列表，筛选 `name?`/`status?`/`couponType?`/`bearer?`；返回 `issuedQty`/`totalQty` | `marketing:coupon:list` |
| A1-2 | GET | `/api/v1/admin/coupons/{id}` | 模板详情 | `marketing:coupon:list` |
| A1-3 | POST | `/api/v1/admin/coupons` | 新建模板；`issuedQty` 由系统维护，**拒绝**客户端传入 | `marketing:coupon:save` |
| A1-4 | PUT | `/api/v1/admin/coupons/{id}` | 编辑模板；**只允许**改 `name`/`thresholdAmount`/`discountAmount`/`discountRate`/`totalQty`/`validStart`/`validEnd`/`validDays`；`totalQty` 不得小于已发放 `issuedQty` | `marketing:coupon:save` |
| A1-5 | PUT | `/api/v1/admin/coupons/{id}/status` | 启用/停用（`0启用 1停用`，注意是反直觉取值） | `marketing:coupon:save` |
| A1-6 | POST | `/api/v1/admin/coupons/{id}/grant` | 定向发券 `{userIds:[...]}`，单次 ≤500 人；按 `valid_days` 计算 `expire_at`，未配置则回落模板 `valid_end` | `marketing:coupon:grant` |
| A1-7 | GET | `/api/v1/admin/coupons/{id}/records` | 该模板的用户券记录分页（`userId`/`status`/`expireAt`/`usedAt`/`orderId`），支持 `status?` 筛选 | `marketing:coupon:list` |

**需要新增/修改的文件**

| 类型 | 路径 |
|---|---|
| 新 | `solo-eats-marketing/src/main/java/com/soloeats/marketing/service/CouponAdminService.java` + `.../service/impl/CouponAdminServiceImpl.java` |
| 新 | `.../service/command/CouponTemplateSaveCommand.java`、`.../service/view/CouponTemplateView.java`、`.../service/view/CouponGrantRecordView.java` |
| 改 | `.../mapper/CouponTemplateMapper.java`（仅新增条件更新方法 `deductIssueQty(id, qty)`，不改既有方法） |
| 新 | `solo-eats-app/src/main/java/com/soloeats/app/controller/AdminCouponController.java` |
| 新 | `solo-eats-app/src/main/java/com/soloeats/app/dto/CouponTemplateSaveRequest.java`、`CouponGrantRequest.java` |
| 新 | `solo-eats-app/src/main/resources/db/migration/V24__coupon_admin_menu.sql`（**只插 `t_sys_menu` + `t_sys_role_menu` 种子**，不动任何既有表结构） |
| 新 | `solo-eats-marketing/src/test/java/com/soloeats/marketing/service/impl/CouponAdminServiceImplTest.java` |

**关键约束（本阶段专属）**

| 项 | 约束 |
|---|---|
| **迁移序号** | 派发时先 `ls solo-eats-app/src/main/resources/db/migration/ \| sort -V` 取实际最大序号 +1。若 T11 阶段A 已占用 `V23`，本阶段用 **`V24__coupon_admin_menu.sql`**；若最大仍是 `V22` 则用 `V23`。**不要**照抄本卡写的 V24 |
| **不建表** | `t_coupon_template`/`t_user_coupon`/`t_user_coupon_log` 是 `V1__init_schema.sql` 既有表，**禁止** `ALTER`，也**禁止**改 V1 原文（本卡禁止事项 10）。故本阶段迁移脚本**只含菜单种子 INSERT** |
| **无 tenant_id** | `t_coupon_template` **没有** `tenant_id` 列（V1 建表即如此）。**不要**给实体加 `tenantId` 字段，也不要手写租户条件，按平台级全局表处理 |
| **发券并发** | `issued_qty` 必须条件更新，禁止「先查余量再 UPDATE」：<br>`UPDATE t_coupon_template SET issued_qty = issued_qty + #{qty} WHERE id = #{id} AND issued_qty + #{qty} <= total_qty`<br>影响行数为 0 → 抛 `COUPON_STOCK_NOT_ENOUGH`。批量发券**整体一个事务**，任一张不足则全量回滚 |
| **停用模板** | `status != 0` 的模板禁止发券，抛 `COUPON_TEMPLATE_STATUS_INVALID` |
| **不改券语义** | 新增能力只走 `CouponAdminService`，**不得**往 `CouponService` 接口里加方法，不得改 `CouponLockResult`、`UserCoupon.status` 的取值语义。`CouponServiceImplTest` 13 个断言必须继续全绿 |
| **菜单 ID** | 现有 `t_sys_menu` 最大 id = **114**。本阶段从 **115** 起，挂在「营销中心」`parent_id=6` 下：115 `基础券`（menu_type 2, perms `marketing:coupon:list`）、116 `券模板维护`（3, `marketing:coupon:save`）、117 `定向发券`（3, `marketing:coupon:grant`）。种子用 `ON DUPLICATE KEY UPDATE id = id` 保持可重复执行 |
| **新增错误码** | 追加到 `ErrorCode`（4xxxx 段）：`40210 COUPON_TEMPLATE_NOT_FOUND`、`40211 COUPON_STOCK_NOT_ENOUGH`、`40212 COUPON_TEMPLATE_STATUS_INVALID` |
| **金额与折扣** | `threshold_amount`/`discount_amount` 用 `long` 分；`discount_rate` 用 `BigDecimal` + `setScale(4, HALF_UP)`，范围 `(0,1]` |

**验收标准**

```bash
mvn -q -pl solo-eats-marketing -am compile && mvn -q -DskipTests package
mvn -q -pl solo-eats-marketing test -Dtest='CouponServiceImplTest'   # 13/13 必须仍全绿

# 模板 CRUD
curl -s -X POST http://localhost:8080/api/v1/admin/coupons -H "Authorization: Bearer $ADMIN_TOKEN" \
  -d '{"name":"新人5元","couponType":3,"thresholdAmount":0,"discountAmount":500,"bearer":1,"totalQty":100,"validDays":7}'
# 断言：code=0；t_coupon_template 新增 1 行，issued_qty=0，status=0

# 超发拒绝（totalQty=1 的模板连发 2 张）
curl -s -X POST http://localhost:8080/api/v1/admin/coupons/{id}/grant -H "Authorization: Bearer $ADMIN_TOKEN" \
  -d '{"userIds":[1001,1002]}'
# 断言：code=40211；t_user_coupon 该模板行数为 0（整批回滚），issued_qty 仍为 0

# 发券成功 + 过期时间
curl -s -X POST http://localhost:8080/api/v1/admin/coupons/{id}/grant -H "Authorization: Bearer $ADMIN_TOKEN" -d '{"userIds":[1001]}'
# 断言：t_user_coupon 新增 1 行，status=0，expire_at ≈ now + validDays；issued_qty=1

# 停用后发券
curl -s -X PUT http://localhost:8080/api/v1/admin/coupons/{id}/status -H "Authorization: Bearer $ADMIN_TOKEN" -d '{"status":1}'
curl -s -X POST http://localhost:8080/api/v1/admin/coupons/{id}/grant -H "Authorization: Bearer $ADMIN_TOKEN" -d '{"userIds":[1002]}'
# 断言：code=40212

# 权限
curl -s http://localhost:8080/api/v1/admin/coupons -H "Authorization: Bearer $MERCHANT_TOKEN"
# 断言：code=10004（商家不可访问平台券管理）
```


### 阶段A（15 个）

| 类型 | 路径 |
|---|---|
| 新 | `solo-eats-marketing/src/main/java/com/soloeats/marketing/domain/entity/` `SeckillActivity`、`SeckillItem`、`SeckillOrder`（3） |
| 新 | `.../domain/enums/PromotionType.java`、`PromotionStatus.java`、`SeckillStatus.java` |
| 新 | 3 个 Mapper |
| 新 | `.../service/PromotionService.java`（**扩展**：阶梯满减/折扣/商家满减/叠加开关） |
| 新 | `.../service/SeckillService.java` + `impl`（预占/释放/回补） |
| 新 | `.../service/view/PromotionView.java`、`SeckillSessionView.java` |
| 新 | `solo-eats-app/src/main/java/com/soloeats/app/controller/AdminPromotionController.java` |
| 新 | `solo-eats-app/src/main/java/com/soloeats/app/controller/AdminSeckillController.java` |
| 新 | `solo-eats-marketing/src/test/java/com/soloeats/marketing/service/impl/SeckillServiceImplTest.java`（并发 200 不超卖） |

### 阶段B（15 个）

| 类型 | 路径 |
|---|---|
| 新 | `solo-eats-app/src/main/resources/db/migration/V19__marketing_member_point.sql` |
| 新 | 实体：`MemberLevel`、`MemberGrowthLog`、`MemberBenefitGrant`、`PointAccount`、`PointLog`、`CheckinRecord`、`InviteRecord`、`RedPacket`、`UserRedPacket`（9） |
| 新 | 9 个 Mapper |
| 新 | `.../service/MemberLevelService.java` + `impl`（等级判定 + 季度重算 + 权益发放） |
| 新 | `.../service/PointService.java` + `impl`（加减 + 过期 + 幂等） |
| 新 | `.../service/CheckinService.java` + `impl` |
| 新 | `.../service/InviteService.java` + `impl`（防刷：同设备/同 IP 24h 内 ≤3） |
| 新 | `.../service/RedPacketService.java` + `impl`（二倍均值法） |
| 新 | `.../job/MemberLevelRecalcJob.java`（季度首日 02:00）+ `PointExpireJob.java`（每日 03:00） |
| 新 | `solo-eats-app/src/main/java/com/soloeats/app/controller/AdminMemberMarketingController.java` |
| 新 | `solo-eats-app/src/main/java/com/soloeats/app/controller/MemberClientController.java`（等级/成长值/积分/签到/邀请/红包） |
| 新 | `solo-eats-marketing/src/test/java/com/soloeats/marketing/service/impl/MemberLevelServiceImplTest.java` |
| 新 | `solo-eats-marketing/src/test/java/com/soloeats/marketing/service/impl/PointServiceImplTest.java` |

---

## 关键实现约束

| 项 | 约束 |
|---|---|
| **券逻辑零改动** | 既有 `CouponService`/`CouponLockResult`/`UserCoupon` 的签名与语义**不得修改**；`CouponServiceImplTest` 断言不得改。新增的满减走**独立的** `PromotionService`，不并入券逻辑 |
| **叠加规则** | 同单最多 1 券 + 1 满减（9.8）；命中多个满减时按 `priority DESC` 取第一个可用；`stackable=1` 才允许与券叠加。**券 > 满减的优先级由 `order` 模块决定**，本卡只返回"可用活动 + 金额" |
| **分摊算法** | 按每个明细的**折后金额**占比分摊，余数（±分）全部归金额最大的那一项；结果必须满足 `Σ分摊 == 优惠总额`；实付保底 `≥ 1 分`（9.8 保底） |
| **门槛口径** | 按**折后商品小计**判断，**不含**打包费与配送费（9.8） |
| **秒杀不超卖** | 三段式：① Redis `DECR seckill:stock:{itemId}` 原子预扣（<0 立即 `INCR` 回补并抛 40203）；② DB 条件更新 `UPDATE t_seckill_item SET sold_qty=sold_qty+? WHERE id=? AND sold_qty+? <= seckill_stock`；③ 下单失败/超时释放（`t_seckill_order.status=2` + `sold_qty` 回补 + Redis `INCR`）。**Redis 与 DB 不一致时以 DB 为准并告警** |
| **限购** | `uk_user_item(user_id, item_id)` 兜底；限购数从 `limit_per_user` 读，**不硬编码** |
| **等级判定** | 成长值取**近 365 天**有效流水合计（`SUM(growth) WHERE effective_at >= now-365d`，含负值）；按 `growth_threshold` 从高到低匹配等级；**季度首日 02:00** 全量重算，单次分页 500 |
| **成长值只增不减语义** | 「只累加、不消耗」指不主动扣减用于消费；但过期（365 天）与手工调整可减。**积分与成长值必须是两套账户**（D3 明确） |
| **积分过期** | `t_point_log.expire_at`；每日 03:00 扫描过期批次，生成负数流水（`source=过期`），`uk_biz(source,biz_id)` 保证只过期一次 |
| **签到** | `uk_user_date(user_id, checkin_date)` 兜底；连续天数 = 昨日有记录则 +1 否则重置 1；奖励 = 基础 10 + `min(连续天数,7)*2` 积分（可配） |
| **邀请防刷** | 同 `invitee` 只能绑一次（`uk_invitee`）；同设备/同 IP 24h 内 ≤3 次；被邀请人**首单完成**后才发奖（状态 `0→1`） |
| **红包算法** | 拼手气：二倍均值法，每人 `[1, 2*remain/remainQty]` 随机，最后一人取余；总额与总额度必须精确相等（`Σ=total_amount`），用整数分计算 |
| **金额精度** | 全部 `bigint` 分；`discount_rate` 用 `BigDecimal` + `setScale(4, HALF_UP)`；**禁止** `double` |
| **多租户** | 全部表带 `tenant_id` |
| **与 order 的边界** | `marketing` **不得依赖 `order`**；`order` 通过 `PromotionService.calculate()` 拿结果并自己写 `t_order_discount`。本卡不改 `order` 代码 |

---

## 验收标准

```bash
mvn -q -pl solo-eats-marketing -am compile && mvn -q -DskipTests package
mvn -q -pl solo-eats-marketing test -Dtest='CouponServiceImplTest'   # 既有测试必须绿

# 满减试算：满100减15，商品小计 10800
curl -s -X POST http://localhost:8080/api/v1/c/marketing/calculate -H "Authorization: Bearer $USER_TOKEN" \
  -d '{"shopId":1,"items":[{"skuId":1001,"qty":3}]}'
# 断言：discountTotal=1500；apportion 合计=1500；每项金额均 ≥0

# 门槛按折后小计（含券 + 满减）不重复计算
# 断言：先扣券再判满减门槛，不会双重达成

# 保底实付
# 构造商品 100 分 + 满减 10000 分 → 断言实付 ≥1 分

# 秒杀并发（200 线程抢 10 件）
ab -n 200 -c 200 -p body.json -T application/json \
   -H "Authorization: Bearer $USER_TOKEN" http://localhost:8080/api/v1/c/seckill/1/reserve
# 断言：成功数 == 10；t_seckill_item.sold_qty == 10；Redis 库存 == 0；无超卖

# 重复预占
# 同 user 第二次 → code=40204

# 会员等级
curl -s http://localhost:8080/api/v1/c/member/profile -H "Authorization: Bearer $USER_TOKEN"
# 断言：返回 levelName、growth、nextThreshold、benefits

# 签到幂等
curl -s -X POST http://localhost:8080/api/v1/c/checkin -H "Authorization: Bearer $USER_TOKEN"
curl -s -X POST http://localhost:8080/api/v1/c/checkin -H "Authorization: Bearer $USER_TOKEN"
# 断言：第二次 code=40207；t_checkin_record 仅 1 条

# 积分过期只发生一次
# 手动把 expire_at 设为昨天并跑 job 两次 → t_point_log 只有 1 条过期负数流水
```

---

## 禁止事项

1. **不得修改** `CouponService`/`CouponServiceImpl`/`CouponTemplate`/`UserCoupon` 的已有方法签名或表结构；`CouponServiceImplTest` 不得改。
2. **不得**把满减逻辑塞进 `CouponService`（会造成券语义污染与既有测试回归）。
3. **不得**修改 `t_order_discount.discount_type` 的既有取值（1券 2满减 3会员 4自取立减）。
4. 不得让 `marketing` 依赖 `order`/`delivery`/`settlement`。
5. 不得用 `double`/`float` 做优惠分摊或红包金额计算。
6. 不得用「先查库存再扣」的非原子方式做秒杀。
7. 不得把会员等级/成长值门槛、签到奖励、红包额度写死在 Java 常量里（规格书要求后台可配）。
8. 不得把成长值与积分合并成一个账户。
9. 不得在 P0 阶段实现秒杀/红包/签到/邀请（按 1.6，这些是 P2；本卡阶段B标注为 P1/P2 可选，需主理人确认 Q3）。
10. 不得删除或修改既有 `V1__init_schema.sql` 中 `t_coupon_template`/`t_user_coupon`/`t_user_coupon_log` 的建表语句。

---

## 执行记录

| 日期 | 会话范围 | 新增/修改文件 | 编译结果 | 既有测试 | 遗留问题 |
|---|---|---|---|---|---|
| 2026-10-26 | T12 阶段A-0：平台级单档满减 + 试算/分摊 | 新增 19 个：`V22__marketing_promotion.sql`（库实际到 V21，任务卡 V18 已过时）、3 实体 + 3 Mapper、`PromotionService`/`Impl`、`DiscountApportioner`、`MarketingCalculateCommand`、4 个 view、2 个测试、`MarketingClientController` + 2 个 DTO | `mvn -q -DskipTests package` BUILD SUCCESS | `CouponServiceImplTest` 13/13 通过；marketing 模块 29/29 通过 | 阶梯/折扣/商家满减/秒杀留阶段A；券分摊仍由券/order 模块负责，本接口只返回满减分摊 |
| 2026-10-26 | T12 阶段A-1：券后台管理 | 新增 10 个：`CouponAdminService` + `Impl`、`CouponTemplateSaveCommand`、`CouponTemplateView`、`CouponGrantRecordView`、`AdminCouponController`、`CouponTemplateSaveRequest`、`CouponGrantRequest`、`V24__coupon_admin_menu.sql`、`CouponAdminServiceImplTest`；修改 2 个：`ErrorCode`（追加 40210/40211/40212）、`CouponTemplateMapper`（新增 `deductIssueQty`） | `mvn -q -DskipTests package` BUILD SUCCESS；`mvn -pl solo-eats-marketing -am compile` 通过 | `CouponServiceImplTest` 13/13 通过；marketing 模块全量 54/54 通过 | ① 迁移序号按实际最大 V23 +1 取 **V24**；菜单 ID 任务卡写 115~117，但 V23 报表已占用 115~116，改为 **117~119**（同 ID 会被 `ON DUPLICATE KEY UPDATE id = id` 静默丢弃）；② 启停请求体 `CouponStatusRequest` 以嵌套 record 放在 `AdminCouponController`，避免超出本阶段 DTO 文件清单；③ 未做 HTTP 级联调（需 MySQL/Redis 起服务），验收用 curl 待 T17 批次0 前补；④ 模板无删除端点（任务卡未要求） |
| 2026-10-27 | T12 阶段A·满减：后台 CRUD、状态变更、C 端可见活动、试算扩展 | 新增 8 个：`PromotionAdminService` + `PromotionAdminServiceImpl`、`PromotionSaveCommand`、`PromotionView`、`PromotionAvailableView`、`AdminPromotionController`、`PromotionSaveRequest`、`PromotionAdminServiceImplTest`；修改 8 个：`MarketingCalculateCommand`（追加可空 `merchantId` + 保留 6 参兼容构造）、`PromotionMapper`（新增 `selectCandidates`）、`PromotionRuleMapper`（新增 `selectByPromotionId`/`deleteByPromotionId`）、`PromotionScopeMapper`（新增 4 个范围反查与子表读删）、`PromotionService`/`PromotionServiceImpl`、`MarketingClientController`、`PromotionServiceImplTest` | `mvn -q -pl solo-eats-marketing -am compile` 通过；`mvn -q -pl solo-eats-marketing test` 77/77 通过；`mvn -q -DskipTests package` BUILD SUCCESS | `CouponServiceImplTest` 13/13、`DiscountApportionerTest` 8/8 未回归；`PromotionServiceImplTest` 19/19、`PromotionAdminServiceImplTest` 12/12、`CouponAdminServiceImplTest` 25/25 | ① 秒杀部分未做（接口 5~8 与 Seckill 实体/Mapper/Service/Controller 留待下个单元）；② `scopeType=4/5` 依赖 `t_sku JOIN t_spu` 反查，未走 product 模块接口；③ `MarketingCalculateCommand` 新增可空 `merchantId`，旧 6 参构造保留兼容；④ 后台页面未做（T17 范畴，菜单 113/114 已存在但无前端页面）；⑤ `/api/v1/c/promotions` 与 `/api/v1/c/marketing` 前缀不同，`MarketingClientController` 类级前缀改为 `/api/v1/c`，`calculate` 实际 URL 不变；⑥ 商家满减已修复为服务端反查：`calculate` / `listAvailable` 在无 `merchantId` 时按 `shopId` 查 `t_shop.merchant_id`（每请求一次），反查不到则跳过，C 端不再需要传 `merchantId`；⑦ `PromotionServiceImplTest` 原 8 个用例断言未改，仅把 mock 目标从 `selectActivePlatformPromotions` 换成 `selectCandidates` |
| 2026-10-27 | T12 阶段A·秒杀：场次/商品后台维护、C 端场次列表、预占/释放三段式库存 | 新增 17 个：`V29__seckill_menu.sql`（菜单 148/149）、`SeckillActivity`/`SeckillItem`/`SeckillOrder` 3 实体、`SeckillActivityMapper`/`SeckillItemMapper`/`SeckillOrderMapper`、`SeckillActivitySaveCommand`、`SeckillActivityView`/`SeckillSessionView`、`SeckillReserveResult`、`SeckillService`/`SeckillServiceImpl`、`AdminSeckillController`、`SeckillActivitySaveRequest`/`SeckillReserveRequest`、`SeckillServiceImplTest`；修改 2 个：`ErrorCode`（追加 40203/40204/40205）、`MarketingClientController`（追加 sessions/reserve/release 三个方法，不动既有 calculate/promotions） | `mvn -q -pl solo-eats-marketing -am compile` 通过；`mvn -q -pl solo-eats-marketing test` 89/89 通过；`mvn -q -DskipTests package` BUILD SUCCESS | `SeckillServiceImplTest` 10/10；既有 5 类未回归：`DiscountApportionerTest` 8/8、`CouponAdminServiceImplTest` 25/25、`CouponServiceImplTest` 13/13、`PromotionAdminServiceImplTest` 12/12、`PromotionServiceImplTest` 21/21 | ① 已补「200 线程抢 10 件」并发单测（内存 CAS 模拟 DB 条件更新 + 内存 Redis），验证不超卖与 Redis 无泄漏；**仍未做真库/真 Redis 集成压测**，待联调阶段补；② Redis 与 DB 不一致时以 DB 为准，但未实现自动对账/告警任务；③ 预占超时自动释放（如 15 分钟未支付）未实现，需定时任务，待与 order 链路联调时补；④ `t_seckill_order` 未回写 `order_id`（下单链路在 order 模块，本卡不改 order）；⑤ 后台页面未做（T17 范畴）；⑥ Redis 用 `StringRedisTemplate` 而非 Redisson：实测 redisson 3.52 的 `RAtomicLong` 无 `setIfAbsent(long)`，无法表达「key 不存在才初始化」，改用 `setIfAbsent` + `increment`；Redis 不可用时降级为只靠 DB 条件更新；⑦ 场次 status 为写入时快照，读取按 `session_start/session_end` 推导，因此无定时翻转任务也不会漏卖进行中场次；后台调整秒杀库存后不主动刷新 Redis 余量（调高会少卖、调低由 DB 条件更新兜底，不会超卖），留待对账任务一并处理；⑧ 未起真实 MySQL/Redis 做 HTTP 联调，接口验收待阶段A 汇总时补 |
