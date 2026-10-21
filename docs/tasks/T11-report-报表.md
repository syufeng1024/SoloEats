# T11 report-经营 · 流量 · 商品 · 商家 · 骑手 · 财务报表

> 优先级：**P1** · 所属模块：`solo-eats-report`（新建）+ `solo-eats-app`
> 单段执行（约 14 个文件）

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
| 1.6 | 「平台后台 P0：基础报表」→ 本卡 P1 做完整版，P0 部分（今日卡片）优先交付 |

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
| `t_report_daily_rider` | id, tenant_id, stat_date, rider_id, **delivery_count int**, **on_time_count int**, **on_time_rate decimal(5,2)**, **online_minutes int**, **income_amount bigint**, **penalty_amount bigint**, **avg_delivery_minutes int**, created_at/updated_at | `uk_rider_date(rider_id, stat_date)` |
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

**新增错误码**：`80020 REPORT_DATE_INVALID`、`80021 REPORT_NOT_READY`（当日聚合未完成）。

---

## 需要新增/修改的文件

| 类型 | 路径 |
|---|---|
| 改 | `pom.xml`（`<modules>` + `dependencyManagement` 增 `solo-eats-report`） |
| 新 | `solo-eats-report/pom.xml`（依赖 `solo-eats-common`、`solo-eats-system`、`mybatis-plus`、`lombok`；**不依赖** order/product/delivery/settlement 模块，只读表） |
| 改 | `solo-eats-app/pom.xml` |
| 新 | `solo-eats-app/src/main/resources/db/migration/V17__report.sql` |
| 新 | `solo-eats-report/src/main/java/com/soloeats/report/domain/entity/` `ReportDailyPlatform`、`ReportDailyShop`、`ReportDailySpu`、`ReportDailyRider`、`TrackEvent`、`ReportFunnelDaily`（6） |
| 新 | 6 个 Mapper（其中 4 个含自定义聚合 SQL `@Select` 或 XML） |
| 新 | `solo-eats-report/src/main/java/com/soloeats/report/job/ReportAggregateJob.java`（`@Scheduled(cron="0 30 1 * * ?")` 每日 01:30 聚合前一日；`@Scheduled` 每 5 分钟刷新工作台今日实时卡） |
| 新 | `solo-eats-report/src/main/java/com/soloeats/report/service/ReportService.java` + `impl`（7 个查询 + 1 个重算） |
| 新 | `solo-eats-report/src/main/java/com/soloeats/report/service/TrackService.java` + `impl`（埋点批量落库） |
| 新 | `solo-eats-report/src/main/java/com/soloeats/report/service/view/`（`DashboardView`、`OverviewSeries`、`FunnelView`、`ProductRankView`、`ShopRankView`、`RiderAnalysisView`、`FinanceReportView`） |
| 新 | `solo-eats-app/src/main/java/com/soloeats/app/controller/AdminReportController.java` |
| 新 | `solo-eats-app/src/main/java/com/soloeats/app/controller/MerchantReportController.java` |
| 新 | `solo-eats-app/src/main/java/com/soloeats/app/controller/TrackController.java` |
| 新 | `solo-eats-report/src/test/java/com/soloeats/report/service/impl/ReportServiceImplTest.java`（空数据、单日、跨月三组） |

---

## 关键实现约束

| 项 | 约束 |
|---|---|
| **只读原则** | `report` 模块**只 SELECT**，不写任何业务表。聚合结果只写 `t_report_*` 与 `t_track_event` |
| **聚合口径** | GMV = 已完成订单 `pay_amount` 之和（**不含**已取消/已退款）；退款率 = 退款单数 / 支付单数；客单价 = GMV / 已完成订单数（分母 0 时返回 0 而非 NULL）；佣金收入取 `t_settlement_item` 的佣金项之和 |
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
| _待填_ | | | | |
