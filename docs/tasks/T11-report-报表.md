# T11 report-经营 · 流量 · 商品 · 商家 · 骑手 · 财务报表

> 优先级：**P0（阶段A：工作台今日卡片 + 经营总览日粒度）/ P1（阶段B：完整 6 张报表 + 漏斗 + 排行）** · 所属模块：`solo-eats-report`（新建）+ `solo-eats-app`
> 分两段执行：**阶段A（约 5 个文件）→ 阶段B（约 9 个文件）**
> 说明：规格书 1.6 平台后台 P0 明确含「**基础报表**」，故拆出 P0 先行段（阶段A），在批次 4/5 提前交付工作台出数，确保 P0 验收不因本卡整体排 P1 而缺失。

---

## 目标

**一句话**：把散在订单/结算/配送/营销各表里的数字，预聚合成按天的报表表，让工作台五张卡与 6 张核心报表能在 500ms 内出数。

**完成后系统能做什么**：平台工作台看到今日 GMV/订单量/客单价/在线骑手/异常订单；可按日/周/月看经营趋势、流量转化漏斗、商品销量榜、商家排行、骑手准时率、佣金与利润概览；商家中心看本店经营看板。

---

## 规格书依据

| 章节 | 原文要点 |
|---|---|
| 4.11 数据报表 | 「经营总览（GMV/订单量/客单价/下单用户数/复购率/退款率，按日/周/月）；流量分析（首页曝光、点击、加购、下单转化漏斗）；商品分析（销量榜、滞销、品类占比、加料偏好）；商家/门店排行（营业额、订单量、评分、出餐时长、取消率）；用户分析（新增/活跃/留存/复购）；骑手分析（接单量、准时率、评分、在线时长）；营销分析；财务报表（佣金收入、结算支出、补贴与优惠成本、利润）；自定义报表与导出」 |
| 4.1 工作台 | 「实时数据卡：今日 GMV、订单量、客单价、新增用户、在线骑手、进行中订单、异常订单数；趋势图表（7/30 日折线、时段热力图、品类占比）」 |
| 9.10 埋点方案 | 「首页曝光/点击/加购/下单/支付的转化漏斗；埋点写独立表或日志文件，由报表任务聚合；练手环境可简化为『接口埋点 + 定时聚合』」 |
| 9.1 性能 | 「后台列表：万级数据分页查询 ≤500ms」 |
| 1.6 | 「平台后台 P0：基础报表」→ 本卡拆 P0 阶段A（工作台今日卡片 + 经营总览日粒度）优先交付，P1 阶段B 做完整版 |

---

## 前置依赖

| 依赖 | 具体依赖物 |
|---|---|
| **T05** | `t_spu`/`t_category`（商品/品类维度） |
| **T07** | `t_delivery_order`/`t_delivery_track`/`t_rider`（骑手维度） |
| **T16** | `t_settlement_bill`/`t_commission_record`（财务口径） |
| 既有表（只读） | `t_order`、`t_order_item`、`t_order_discount`、`t_refund_order`、`t_payment_order`、`t_settlement_bill`、`t_settlement_item`、`t_rider_income`、`t_user_coupon`、`t_member_user` |
| **依赖方向** | `report` **只读**其他模块表，**不写任何业务表**；其他模块**不得**依赖 `report` |

---

## 数据模型

迁移脚本：`V17__report.sql`

| 表 | 关键字段 | 索引 |
|---|---|---|
| `t_report_daily_platform` | id, tenant_id, **stat_date date**, **gmv bigint**, **order_count int**, **paid_order_count int**, **refund_amount bigint**, **refund_order_count int**, **new_user_count int**, **active_user_count int**, **avg_order_amount bigint**, **commission_income bigint**, **settlement_expense bigint**, **subsidy_cost bigint**, **gross_profit bigint**, created_at/updated_at | `uk_date(tenant_id, stat_date)` |
| `t_report_daily_shop` | id, tenant_id, stat_date, shop_id, merchant_id, **order_count**, **gmv**, **refund_amount**, **avg_prep_minutes**, **cancel_rate decimal(5,2)**, **rating decimal(3,2)**, **pickup_order_count**, **delivery_order_count**, created_at/updated_at | `uk_shop_date(shop_id, stat_date)`、`idx_merchant_date(merchant_id, stat_date)` |
| `t_report_daily_spu` | id, tenant_id, stat_date, shop_id, spu_id, category_id, **sale_qty int**, **sale_amount bigint**, **refund_qty int**, created_at/updated_at | `uk_shop_spu_date(shop_id, spu_id, stat_date)`、`idx_spu_date(spu_id, stat_date)` |
| `t_report_daily_rider` | id, tenant_id, stat_date, rider_id, **delivery_count int**, **on_time_count int**, **on_time_rate decimal(5,2)**, **online_minutes int**, **income_amount bigint**, **penalty_amount bigint**, **avg_delivery_minutes int**, **rating decimal(3,2)**, created_at/updated_at | `uk_rider_date(rider_id, stat_date)` |
| `t_track_event` | id, tenant_id, **event_type varchar(32)**（`HOME_EXPOSE`/`SHOP_CLICK`/`ADD_CART`/`ORDER_SUBMIT`/`PAY_SUCCESS`）, **user_id bigint NULL**, **device_id varchar(64)**, **biz_type varchar(32)**, **biz_id bigint**, **source varchar(32)**, extra_json json, **event_at datetime(3)**, created_at | `idx_type_time(event_type, event_at)`、`idx_user_time(user_id, event_at)` |
| `t_report_funnel_daily` | id, tenant_id, stat_date, **expose_count**, **click_count**, **cart_count**, **order_count**, **pay_count**, created_at | `uk_date(tenant_id, stat_date)` |

---

## 接口清单

| # | 方法 | 路径 | 要点 | 权限 |
|---|---|---|---|---|
| 1 | GET | `/api/v1/admin/report/dashboard` | 工作台五卡：`{todayGmv,orderCount,avgOrderAmount,onlineRiders,exceptionOrders,newUsers,inProgressOrders}` | `report:dashboard:view` |
| 2 | GET | `/api/v1/admin/report/overview` | `dateFrom,dateTo,granularity(day/week/month)` → GMV/订单/客单价/复购率/退款率序列 | `report:overview:view` |
| 3 | GET | `/api/v1/admin/report/funnel` | `dateFrom,dateTo` → 曝光/点击/加购/下单/支付五级漏斗与转化率 | `report:funnel:view` |
| 4 | GET | `/api/v1/admin/report/products` | `dateFrom,dateTo,sortBy(saleAmount/saleQty),categoryId,pageNum,pageSize` 商品榜 | `report:product:view` |
| 5 | GET | `/api/v1/admin/report/shops` | 商家/门店排行（营业额、单量、评分、出餐时长、取消率） | `report:shop:view` |
| 6 | GET | `/api/v1/admin/report/riders` | 骑手分析（接单量、准时率、评分、在线时长分布） | `report:rider:view` |
| 7 | GET | `/api/v1/admin/report/finance` | 财务报表：佣金收入、结算支出、补贴与优惠成本、毛利润 | `report:finance:view` |
| 8 | GET | `/api/v1/admin/report/users` | 新增/活跃/留存/复购 | `report:user:view` |
| 9 | POST | `/api/v1/c/track` | 埋点上报 `{eventType,bizType,bizId,source,extra}`（前端批量，≤20 条） | 白名单（可带 token） |
| 10 | POST | `/api/v1/admin/report/rebuild` | `{date}` 手动重算某日聚合（管理员兜底） | `report:rebuild` |
| 11 | GET | `/api/v1/merchant/report/dashboard` | 商家中心看板（今日营业额/订单/待处理/热销 TOP5/近 7 日出餐时长） | `MERCHANT` |
| 12 | GET | `/api/v1/merchant/report/orders` | `dateFrom,dateTo` 本店订单趋势 | `MERCHANT` |

**新增错误码**（10xxxx 段，报表专属）：`100010 REPORT_DATE_INVALID`、`100011 REPORT_NOT_READY`（当日聚合未完成）。

---

## 需要新增/修改的文件

### 阶段A（P0，约 5 个：工作台今日卡片 + 经营总览日粒度）

| 类型 | 路径 |
|---|---|
| 改 | `pom.xml`（`<modules>` + `dependencyManagement` 增 `solo-eats-report`） |
| 新 | `solo-eats-report/pom.xml`（依赖 `solo-eats-common`、`solo-eats-system`、`mybatis-plus`、`lombok`；**不依赖** order/product/delivery/settlement 模块，只读表） |
| 改 | `solo-eats-app/pom.xml` |
| 新 | `solo-eats-app/src/main/resources/db/migration/V17__report.sql`（`t_report_daily_platform`、`t_report_daily_shop` 两张 + 其余四张一并建好但阶段A 不填充） |
| 新 | `solo-eats-report/src/main/java/com/soloeats/report/domain/entity/ReportDailyPlatform.java`、`ReportDailyShop.java` |
| 新 | 对应 2 个 Mapper |
| 新 | `solo-eats-report/src/main/java/com/soloeats/report/job/ReportAggregateJob.java`（`@Scheduled(cron="0 30 2 * * ?")` 每日 02:30 聚合前一日——**必须在商家/骑手账单 02:00 生成之后**，否则财务字段缺数；`@Scheduled` 每 5 分钟刷新工作台今日实时卡） |
| 新 | `solo-eats-report/src/main/java/com/soloeats/report/service/ReportService.java` + `impl`（dashboard + overview） |
| 新 | `solo-eats-report/src/main/java/com/soloeats/report/service/view/DashboardView.java`、`OverviewSeries.java` |
| 新 | `solo-eats-app/src/main/java/com/soloeats/app/controller/AdminReportController.java`（dashboard/overview 两端点） |
| 新 | `solo-eats-app/src/main/java/com/soloeats/app/controller/MerchantReportController.java`（商家中心看板今日卡片） |

### 阶段B（P1，约 9 个：完整 6 张报表 + 漏斗 + 排行 + 埋点）

| 类型 | 路径 |
|---|---|
| 新 | `solo-eats-report/src/main/java/com/soloeats/report/domain/entity/` `ReportDailySpu`、`ReportDailyRider`、`TrackEvent`、`ReportFunnelDaily`（4） |
| 新 | 对应 4 个 Mapper（含自定义聚合 SQL） |
| 新 | `solo-eats-report/src/main/java/com/soloeats/report/service/ReportService.java` 追加（funnel/products/shops/riders/finance/users + rebuild） |
| 新 | `solo-eats-report/src/main/java/com/soloeats/report/service/TrackService.java` + `impl`（埋点批量落库） |
| 新 | `solo-eats-report/src/main/java/com/soloeats/report/service/view/`（`FunnelView`、`ProductRankView`、`ShopRankView`、`RiderAnalysisView`、`FinanceReportView`） |
| 新 | `solo-eats-app/src/main/java/com/soloeats/app/controller/AdminReportController.java`（追加其余端点） |
| 新 | `solo-eats-app/src/main/java/com/soloeats/app/controller/TrackController.java` |
| 新 | `solo-eats-report/src/test/java/com/soloeats/report/service/impl/ReportServiceImplTest.java`（空数据、单日、跨月三组） |

---

## 关键实现约束

| 项 | 约束 |
|---|---|
| **只读原则** | `report` 模块**只 SELECT**，不写任何业务表。聚合结果只写 `t_report_*` 与 `t_track_event` |
| **聚合口径** | GMV = 已完成订单 `pay_amount` 之和（**不含**已取消/已退款）；退款率 = 退款单数 / 支付单数；客单价 = GMV / 已完成订单数（分母 0 时返回 0 而非 NULL）；佣金收入取 `t_settlement_item` 的佣金项之和 |
| **准时率数据源（8.7 衔接）** | 骑手 `on_time_count`/`on_time_rate` 依赖 **T07 阶段B 落地的 `t_delivery_order.promise_deliver_at`**：`on_time_count = 当日 delivered_at <= promise_deliver_at 的配送单数`，`on_time_rate = on_time_count / delivery_count`。若 T07 未回填该字段，准时率恒为 0，**本卡须在依赖表中确认 T07 已交付**（见 T07「承诺送达时间」约束）；骑手 `rating` 取 `t_rider.rating` 快照 |
| **时效** | 当日数据用「实时卡 SQL 直查」（限制单日、走 `idx_shop_status`/`idx_user_status`）而非聚合表；历史用聚合表。二者接口同一个，内部按 `stat_date` 是否今天分流 |
| **幂等重算** | `rebuild(date)` 先 `DELETE FROM t_report_daily_* WHERE stat_date=?` 再重算，**同一事务**；重复调用结果一致 |
| **长事务** | 聚合任务**必须分页**扫订单（每页 1000，`id > lastId`），**禁止**一次性 `selectList` 全量 |
| **索引友好** | 所有聚合 SQL 必须能走既有索引（`idx_shop_status`、`idx_merchant_settle`、`idx_user_status`）；新增查询前用 `EXPLAIN` 验证，**禁止**无索引全表扫 |
| **埋点量级** | `t_track_event` 按日增长；保留 90 天，定时任务清理；单次上报 ≤20 条，异步落库（`@Async`） |
| **多租户** | 全部表带 `tenant_id`；聚合时按 `tenant_id` 分组 |
| **导出** | 本卡**不做** Excel 导出（P2），只返回 JSON |
| **时区** | `stat_date` 按 `Asia/Shanghai` 自然日切分；跨时区不做处理 |

---

## 验收标准

```bash
mvn -q -pl solo-eats-report -am compile && mvn -q -DskipTests package

# 工作台
curl -s http://localhost:8080/api/v1/admin/report/dashboard -H "Authorization: Bearer $ADMIN_TOKEN"
# 断言：7 个字段齐全；todayGmv 与 SELECT SUM(pay_amount) FROM t_order WHERE DATE(completed_at)=CURDATE() 一致

# 趋势
curl -s "http://localhost:8080/api/v1/admin/report/overview?dateFrom=2025-01-01&dateTo=2025-01-07&granularity=day" \
  -H "Authorization: Bearer $ADMIN_TOKEN"
# 断言：返回 7 个点（无数据日期补 0）

# 漏斗
curl -s "http://localhost:8080/api/v1/admin/report/funnel?dateFrom=2025-01-01&dateTo=2025-01-01" -H "Authorization: Bearer $ADMIN_TOKEN"
# 断言：五级 count 单调不增；转化率 = 下一级/上一级

# 手动重算幂等
curl -s -X POST http://localhost:8080/api/v1/admin/report/rebuild -H "Authorization: Bearer $ADMIN_TOKEN" -d '{"date":"2025-01-01"}'
# 连调两次 → t_report_daily_platform 该日仅 1 行，数值一致

# 埋点
curl -s -X POST http://localhost:8080/api/v1/c/track -d '{"events":[{"eventType":"HOME_EXPOSE","deviceId":"d1"}]}'
# 断言：code=0；t_track_event 新增 1 行
```

---

## 禁止事项

1. **不得**让 `report` 模块写任何业务表（含 `t_order`/`t_settlement_*`）。
2. **不得**让其他模块依赖 `solo-eats-report`（避免循环）。
3. 不得在聚合任务里一次性拉全量订单到内存。
4. 不得用 `SELECT *` 做聚合查询（只取需要的列）。
5. 不得在 `report` 里复制订单业务逻辑（状态枚举必须用常量字符串 `'已完成'` 对应的 `OrderStatus` 值，**只读引用既有枚举类的静态值，不修改它**）。
6. 不得把报表查询做成实时全表扫（超过 100 万行必须走聚合表）。
7. 不得在 `t_track_event` 里存个人信息明文（`phone`/`idCard` 禁止）。
8. 不得改动 `order`/`settlement`/`delivery` 的任何代码。

---

## 执行记录

| 日期 | 会话范围 | 新增/修改文件 | 编译结果 | 遗留问题 |
|---|---|---|---|---|
| 2026-10-26 | 阶段A | 新增 15 / 修改 5 | 通过：`mvn -q -pl solo-eats-report -am compile`、`mvn -q -DskipTests package` 均 exit 0 | ① 阶段B（漏斗、商品/商家/骑手/财务/用户、埋点、rebuild、单测）未做；② 定时任务无请求上下文，`TenantContext` 取默认租户 1，`t_order` 无 `tenant_id`，跨租户聚合待后续扩展；③ `avg_prep_minutes` 用 `created_at → pickup_ready_at` 近似（`t_order` 无接单时间列）；④ V23 为源表追加 5 个索引（`t_order` 3、`t_payment_order` 1、`t_refund_order` 1），需确认生产执行窗口；⑤ `MerchantReportController` 未改动，沿用既有 `OrderQueryService#dashboard`。 |
| 2026-10-27 | 阶段B | 新增 23 / 修改 6 | 通过：`mvn -q -pl solo-eats-report -am compile` exit 0；`mvn -pl solo-eats-report test` Tests run: 5, Failures: 0, Errors: 0；`mvn -q -DskipTests package` exit 0 | ① 商品/门店/骑手榜实时聚合业务表，商品与门店 GMV 统一用 `completed_at`（与 `buildPlatform` 同口径），任务卡示例 SQL 的 `created_at` 未采用；② 财务取 `t_fund_flow`，历史区间若 T16 前的数据未补流水则为 0；③ 骑手在线时长按 `t_rider_online_log` 配对并回看 7 天，超过 7 天的连续在线不计入；④ 多租户仍沿用阶段A 的默认租户 1；⑤ `V30__report_menu.sql` 额外补 154/155/156 三个权限项（门店/骑手/重算），否则对应接口无权限码可授权。 |
