# T07 delivery-骑手 · 调度派单 · 轨迹 · 运费与围栏

> 优先级：**P0** · 所属模块：`solo-eats-delivery`（增量改造）+ `solo-eats-app`
> 分两段：**阶段A（骑手档案/认证 + 上线听单 + 抢单/派单）→ 阶段B（取货/送达/轨迹 + 运费模板 + 派单策略配置）**

---

## 目标

**一句话**：把现有「只有配送单和收入」的 delivery 模块补成完整的骑手履约闭环——骑手能认证、上线、被派单或抢单、取货、送达，平台能配派单策略和运费模板，轨迹能回放。

**完成后系统能做什么**
1. 骑手提交实名认证（姓名/身份证/证件照/健康证），平台人工审核通过后解锁接单。
2. 骑手上线/下线（在线状态 + 位置存 Redis），能听到派单或浏览抢单大厅并抢单。
3. 平台可配派单策略（距离权重/负载均衡/评分权重/超时优先/派单超时秒数/最大重试）与抢单半径、单量上限。
4. 骑手到店「我已取货」、送达「确认送达」，每次状态流转驱动 `t_delivery_order` 与订单主状态联动。
5. 骑手端每 5~10s 上报轨迹点，用户端可按 `deliveryId` 拉取轨迹；平台可回放。
6. 平台可配运费模板（起步价 + 距离区间 + 时段加价 + 恶劣天气加价 + 重量/楼层附加），提供运费试算接口。

---

## 规格书依据

| 章节 | 原文要点 |
|---|---|
| 4.5 配送中心 | 「骑手认证审核【替换人工审核】；骑手档案（实名、车型、接单上限、启用/停用、拉黑、评分与准时率）；健康证管理（有效期、到期提醒、**过期自动限制接单**）；骑手排班与考勤；调度中心（实时地图、**自动派单策略配置（距离权重、负载均衡、骑手评分、订单超时优先）**、手动派单/改派/撤销、转单、派单失败兜底）；抢单大厅配置（可抢单订单范围、抢单半径、单量上限、抢单优先级规则）；配送单管理（状态流转、**轨迹回放**、超时记录、申诉）；**运费与计费（运费模板：距离区间计价、时段加价、恶劣天气加价、楼层/重量附加；骑手配送费规则：基础费+距离费+时段/天气补贴；补贴与奖惩规则）**；配送范围（平台服务区域、门店配送围栏、禁送区域、超区拦截）」 |
| 8.3 派单流程 | 支付成功 → 生成配送单（待派单）→ 按策略推给候选骑手（15s 倒计时）→ 接受/拒绝 → 重试 N 次 → 进入抢单大厅 → 仍无人接 → 异常池 |
| 8.7 超时与准时达 | 「承诺送达时间」与超时判定规则 |
| 8.9 状态机 · 配送单 | 待接单 → 已接单 → 待取货 → 配送中 → 已送达；分支：已取消 / 已改派 / 异常 |
| 2.5 核心领域模型 | 「骑手、配送单、配送轨迹、派单记录；配送单 N:1 骑手；配送单 N:1 门店」 |
| 技术架构 3.9 | 「派单与配送单流转」；2.7 配送域 DDL（`t_delivery_order`、`t_dispatch_record` 已存在） |
| 9.5 可维护性 | 「业务规则（派单策略、运费模板、抽佣比例、订单规则）**全部后台可配，不写死在代码中**」 |

---

## 前置依赖

| 依赖 | 具体依赖物 |
|---|---|
| **T04** | `Shop`（取货地址经纬度）、`ShopGeoService`（距离/围栏） |
| **T05** | `ShopMenuService`/`Spu`（订单商品件数、餐品信息用于骑手任务卡） |
| **T01/T02** | `@RequireRole(Roles.RIDER)`、`@RequirePermission`、`TenantContext` |
| **T03** | `FileStoragePort`（骑手证件照、送达留证照片） |
| 既有表（**不可改结构语义**） | `t_delivery_order`（`delivery_no, order_id, shop_id, rider_id, status, pickup_*, receiver_*, distance_m, delivery_fee, dispatch_count, dispatch_expire_at, accepted_at, picked_up_at, delivered_at, cancel_reason, version`）、`t_dispatch_record`、`t_rider_income`（`uk_delivery`）、`t_order_exception` |
| 既有类（**不可改方法签名**） | `com.soloeats.delivery.domain.entity.DeliveryOrder`、`RiderIncome`；`domain/enums/DeliveryOrderStatus`；`service/DeliveryService` + `impl/DeliveryServiceImpl`；`mapper/DeliveryOrderMapper`、`RiderIncomeMapper`；测试 `DeliveryServiceImplTest` |
| 既有调用方 | `solo-eats-order` 的 `DeliveryFulfillmentApplicationService`、`RiderDispatchResult` 通过 `DeliveryService` 派单/接单；`solo-eats-app` 的 `OrderController` 有骑手动作接口（`RiderActionRequest`） |
| 既有配置 | `app.dispatch.expire-seconds=15`、`max-retry=3` |

---

## 数据模型

迁移脚本：`V12__delivery_rider.sql`（阶段A）、`V13__delivery_track_freight.sql`（阶段B）

### 阶段A

| 表 | 关键字段 | 索引 |
|---|---|---|
| `t_rider` | id, tenant_id, user_id（关联 `t_sys_user`）, **rider_no varchar(32)**, real_name, **id_card_cipher**, **id_card_mask**, phone_cipher, phone_mask, avatar_url, **vehicle_type tinyint(1电动车 2摩托 3自行车 4步行)**, **cert_status tinyint(0未提交 1待审核 2已通过 3已驳回)**, cert_remark, **health_cert_expire_at date**, **max_concurrent_orders int DEFAULT 3**, **pickup_radius_m int DEFAULT 3000**, **status tinyint(0正常 1停用 2拉黑)**, **rating decimal(3,2)**, **on_time_rate decimal(5,2)**, total_orders int, online_status tinyint(0离线 1在线 2接单中)（DB 兜底，实时状态在 Redis）, last_online_at, version, created_at/updated_at/deleted | `uk_rider_no(rider_no)`、`uk_user(user_id)`、`idx_cert_status(cert_status)`、`idx_status_online(status, online_status)` |
| `t_rider_certification` | id, tenant_id, rider_id, real_name, id_card_cipher, id_card_front_file_id, id_card_back_file_id, health_cert_file_id, **health_cert_expire_at**, status tinyint(0待审 1通过 2驳回), audit_by, audit_at, audit_remark, created_at | `idx_rider_time(rider_id, created_at)` |
| `t_rider_schedule` | id, tenant_id, rider_id, work_date date, start_time time, end_time time, status tinyint(0待确认 1已确认 2请假), created_at | `uk_rider_date(rider_id, work_date)` |
| `t_dispatch_config` | id, tenant_id, **config_scope tinyint(1全局 2城市 3门店)**, shop_id, **distance_weight decimal(4,2) DEFAULT 0.40**, **load_weight decimal(4,2) DEFAULT 0.30**, **rating_weight decimal(4,2) DEFAULT 0.20**, **timeout_weight decimal(4,2) DEFAULT 0.10**, **dispatch_expire_seconds int DEFAULT 15**, **max_retry int DEFAULT 3**, **grab_radius_m int DEFAULT 3000**, **grab_max_orders int DEFAULT 3**, **grab_enabled tinyint DEFAULT 1**, **auto_dispatch_enabled tinyint DEFAULT 1**, status, created_at/updated_at | `uk_scope_shop(config_scope, shop_id)` |
| `t_rider_online_log` | id, tenant_id, rider_id, action tinyint(1上线 2下线), lng, lat, reason varchar(128), created_at | `idx_rider_time(rider_id, created_at)` |

### 阶段B

| 表 | 关键字段 | 索引 |
|---|---|---|
| `t_delivery_track` | id, tenant_id, delivery_id, rider_id, **lng decimal(10,6)**, **lat decimal(10,6)**, **speed decimal(6,2)**, **accuracy_m decimal(6,2)**, **report_at datetime(3)**, created_at | `idx_delivery_time(delivery_id, report_at)`、`idx_rider_time(rider_id, report_at)` |
| `t_freight_template` | id, tenant_id, template_name, **base_fee bigint**, **base_distance_m int DEFAULT 3000**, **per_step_distance_m int DEFAULT 500**, **per_step_fee bigint**, **max_distance_m int**, **free_over_amount bigint NULL**（满额免配送费）, status, created_at/updated_at/deleted | `idx_status(status)` |
| `t_freight_rule` | id, tenant_id, template_id, **rule_type tinyint(1距离区间 2时段加价 3恶劣天气 4重量 5楼层)**, **condition_json json**, **fee_delta bigint**, sort, status, created_at/updated_at | `idx_template_type(template_id, rule_type, sort)` |
| `t_shop_freight_rel` | id, tenant_id, shop_id, template_id | `uk_shop(shop_id)` |
| `t_rider_fee_rule` | id, tenant_id, rider_id NULL（NULL=全局）, **base_fee bigint**, **per_km_fee bigint**, **peak_delta bigint**, **bad_weather_delta bigint**, **timeout_penalty bigint**, effective_from, effective_to, status, created_at/updated_at | `idx_rider_status(rider_id, status)` |
| `t_rider_ping_anomaly` | id, tenant_id, rider_id, delivery_id, type tinyint(1超时 2联系不上用户 3地址错误 4餐品破损 5商家出餐慢 6用户拒收), remark, images_json, created_at | `idx_delivery(delivery_id)` |

---

## 接口清单

| # | 方法 | 路径 | 要点 | 权限 |
|---|---|---|---|---|
| 1 | POST | `/api/v1/rider/certification` | `{realName,idCard,idCardFrontFileId,idCardBackFileId,healthCertFileId,healthCertExpireAt,vehicleType}` 提交认证 | `RIDER` + `@Idempotent` |
| 2 | GET | `/api/v1/rider/certification` | 我的认证状态 + 驳回原因 | `RIDER` |
| 3 | GET | `/api/v1/admin/riders` | `certStatus,status,keyword,pageNum,pageSize` | `delivery:rider:list` |
| 4 | GET | `/api/v1/admin/riders/{id}` | 档案（含认证记录、评分、准时率、在线时长） | `delivery:rider:detail` |
| 5 | POST | `/api/v1/admin/riders/{id}/certification/audit` | `{status, remark}` 通过/驳回 | `delivery:rider:audit` |
| 6 | PUT | `/api/v1/admin/riders/{id}/status` | `{status}` 正常/停用/拉黑 | `delivery:rider:update` |
| 7 | PUT | `/api/v1/admin/riders/{id}/limits` | `{maxConcurrentOrders,pickupRadiusM}` | `delivery:rider:update` |
| 8 | PUT | `/api/v1/rider/online` | `{online:true, lng, lat}` 上线（写 Redis `rider:geo` GEO + `rider:online:{id}`）；下线带 `{reason}` | `RIDER` |
| 9 | GET | `/api/v1/rider/orders/pending` | 系统派给我的待确认单（含 15s 倒计时剩余秒） | `RIDER` |
| 10 | GET | `/api/v1/rider/hall` | 抢单大厅：`lng,lat,distanceM,sortBy(distance/fee/time)` | `RIDER` |
| 11 | POST | `/api/v1/rider/dispatch/{deliveryId}/accept` | 接受派单（条件更新 `status` + `rider_id IS NULL`） | `RIDER` |
| 12 | POST | `/api/v1/rider/dispatch/{deliveryId}/reject` | `{reason}` 拒单（写 `t_dispatch_record`，`dispatch_count+1`） | `RIDER` |
| 13 | POST | `/api/v1/rider/deliveries/{id}/grab` | 抢单（Redis 分布式锁 `lock:delivery:{id}`） | `RIDER` |
| 14 | POST | `/api/v1/rider/deliveries/{id}/pickup` | 我已取货（校验骑手匹配 + 状态） | `RIDER` |
| 15 | POST | `/api/v1/rider/deliveries/{id}/delivered` | 确认送达（可选 `{lng,lat,photoFileId}`） | `RIDER` |
| 16 | POST | `/api/v1/rider/deliveries/{id}/anomaly` | `{type,remark,images[]}` 异常上报 | `RIDER` |
| 17 | POST | `/api/v1/rider/track` | `{deliveryId,lng,lat,speed,accuracy,reportAt}` 批量上报（一次最多 20 点） | `RIDER` |
| 18 | GET | `/api/v1/c/deliveries/{deliveryId}/track` | 用户端轨迹（最近 100 点 + 骑手实时位置） | `C_USER` |
| 19 | GET | `/api/v1/admin/deliveries` | 配送单列表 `status,shopId,riderId,dateFrom,dateTo` | `delivery:order:list` |
| 20 | GET | `/api/v1/admin/deliveries/{id}/track` | 轨迹回放（全量按时间升序） | `delivery:order:list` |
| 21 | POST | `/api/v1/admin/deliveries/{id}/reassign` | `{riderId,reason}` 改派 | `delivery:order:dispatch` |
| 22 | GET/PUT | `/api/v1/admin/dispatch-config` | 派单策略读写（全局 / 门店级） | `delivery:config:*` |
| 23 | GET/POST/PUT | `/api/v1/admin/freight-templates`（+`/{id}`） | 运费模板 + 规则维护 | `delivery:freight:*` |
| 24 | POST | `/api/v1/c/freight/estimate` | `{shopId,lng,lat,goodsAmount,weightKg,floor,deliverAt}` → `{deliveryFee,breakdown[],distanceM}` | 白名单 |
| 25 | GET | `/api/v1/rider/income/overview` | 今日/本周/本月收入、单量、准时率 | `RIDER` |
| 26 | GET | `/api/v1/rider/income/details` | 每单构成（基础+距离+时段+天气+小费-罚款） | `RIDER` |

**新增错误码**（5xxxx 段追加）：`50006 RIDER_NOT_CERTIFIED`、`50007 RIDER_OFFLINE`、`50008 RIDER_ORDER_LIMIT`、`50009 RIDER_HEALTH_CERT_EXPIRED`、`50010 DISPATCH_EXPIRED`、`50011 FREIGHT_OUT_OF_RANGE`。

---

## 需要新增/修改的文件

### 阶段A（14 个）

| 类型 | 路径 |
|---|---|
| 新 | `solo-eats-app/src/main/resources/db/migration/V12__delivery_rider.sql`（4 表 + 回填 `t_rider id=1` 对应既有 `rider_id=1`） |
| 新 | `solo-eats-delivery/src/main/java/com/soloeats/delivery/domain/entity/Rider.java` |
| 新 | `solo-eats-delivery/src/main/java/com/soloeats/delivery/domain/entity/RiderCertification.java` |
| 新 | `solo-eats-delivery/src/main/java/com/soloeats/delivery/domain/entity/RiderSchedule.java` |
| 新 | `solo-eats-delivery/src/main/java/com/soloeats/delivery/domain/entity/DispatchConfig.java` |
| 新 | `solo-eats-delivery/src/main/java/com/soloeats/delivery/domain/entity/RiderOnlineLog.java` |
| 新 | `solo-eats-delivery/src/main/java/com/soloeats/delivery/domain/enums/RiderCertStatus.java`、`VehicleType.java`、`RiderStatus.java` |
| 新 | `solo-eats-delivery/src/main/java/com/soloeats/delivery/mapper/` 5 个 Mapper |
| 新 | `solo-eats-delivery/src/main/java/com/soloeats/delivery/service/RiderService.java` + `impl` |
| 新 | `solo-eats-delivery/src/main/java/com/soloeats/delivery/service/RiderOnlineService.java` + `impl`（Redis GEO/在线状态） |
| 新 | `solo-eats-delivery/src/main/java/com/soloeats/delivery/service/RiderDispatchService.java` + `impl`（派单/抢单/拒单；**内部委托既有 `DeliveryService` 的更新方法，不重写状态机**） |
| 新 | `solo-eats-delivery/src/main/java/com/soloeats/delivery/service/DispatchStrategy.java`（策略接口 + `WeightedScoreStrategy`，权重从 `t_dispatch_config` 读） |
| 新 | `solo-eats-app/src/main/java/com/soloeats/app/controller/AdminRiderController.java` |
| 新 | `solo-eats-app/src/main/java/com/soloeats/app/controller/RiderDispatchController.java` |
| 改 | `solo-eats-delivery/pom.xml`（加 `solo-eats-store`、`solo-eats-system` 依赖） |
| 改 | `pom.xml` 无需改（delivery 已在 `<modules>`；但需在根 `dependencyManagement` 里确保 `solo-eats-store`/`solo-eats-system` 已声明，T01/T04 已完成） |

### 阶段B（13 个）

| 类型 | 路径 |
|---|---|
| 新 | `solo-eats-app/src/main/resources/db/migration/V13__delivery_track_freight.sql`（5 表） |
| 新 | `solo-eats-delivery/src/main/java/com/soloeats/delivery/domain/entity/DeliveryTrack.java`、`FreightTemplate.java`、`FreightRule.java`、`ShopFreightRel.java`、`RiderFeeRule.java`、`RiderPingAnomaly.java`（6） |
| 新 | 对应 6 个 Mapper |
| 新 | `solo-eats-delivery/src/main/java/com/soloeats/delivery/service/DeliveryTrackService.java` + `impl`（含批量上报 + 轨迹查询） |
| 新 | `solo-eats-delivery/src/main/java/com/soloeats/delivery/service/FreightService.java` + `impl`（**运费试算**） |
| 新 | `solo-eats-delivery/src/main/java/com/soloeats/delivery/service/command/`（`FreightEstimateCommand`、`SaveFreightTemplateCommand`） |
| 新 | `solo-eats-delivery/src/main/java/com/soloeats/delivery/service/view/`（`FreightEstimateView`、`TrackPointView`、`RiderIncomeOverview`、`DispatchConfigView`） |
| 新 | `solo-eats-app/src/main/java/com/soloeats/app/controller/AdminDeliveryController.java` |
| 新 | `solo-eats-app/src/main/java/com/soloeats/app/controller/AdminFreightController.java` |
| 新 | `solo-eats-app/src/main/java/com/soloeats/app/controller/ConsumerDeliveryController.java` |
| 新 | `solo-eats-app/src/main/java/com/soloeats/app/controller/RiderIncomeController.java` |
| 新 | `solo-eats-delivery/src/test/java/com/soloeats/delivery/service/impl/FreightServiceImplTest.java` |
| 新 | `solo-eats-delivery/src/test/java/com/soloeats/delivery/service/impl/DispatchStrategyTest.java` |

---

## 关键实现约束

| 项 | 约束 |
|---|---|
| **状态机复用** | 配送单 4 个状态流转**必须**复用既有 `DeliveryOrderStatus` 与 `DeliveryServiceImpl` 的条件更新逻辑，**新增方法只能编排，不得另写一套 UPDATE**。既有 `DeliveryServiceImplTest` 断言必须保持通过 |
| **抢单并发** | 抢单用 Redisson `RLock("lock:delivery:{id}")`（`DistributedLock` 已封装）+ 条件更新 `WHERE id=? AND rider_id IS NULL AND status=待接单`；双保险，锁超时 3s |
| **派单超时** | 推送后写 `dispatch_expire_at = now + config.dispatch_expire_seconds`；超时由 Rabbit 延迟消息（复用 `RabbitMqConfig`）触发重派，`dispatch_count < max_retry` 则重推，否则转抢单大厅 |
| **派单策略** | 权重必须从 `t_dispatch_config` 读，**禁止硬编码**。评分公式：`score = w_d * norm(1/distance) + w_l * norm(1-currentLoad) + w_r * norm(rating) + w_t * norm(remainingSeconds)`，取 Top-5 候选推送 |
| **在线状态** | Redis：`rider:geo`（GEO 集合，member=riderId）、`rider:online:{riderId}`（Hash：lng/lat/lastPing/load，TTL 120s）；DB `t_rider.online_status` 只做兜底展示，**不做实时判定** |
| **认证与接单门槛** | 接单/抢单前必须校验：`cert_status=2`、`status=0`、`health_cert_expire_at >= today`、`online`、`当前进行中单量 < max_concurrent_orders`。任一不满足抛对应错误码 |
| **健康证过期** | 定时任务（每日 06:00）扫描 `health_cert_expire_at < today + 7 days` 发站内信提醒；已过期自动置 `online_status=0` 并拒绝新单 |
| **轨迹上报** | 批量 `INSERT`（MyBatis-Plus `saveBatch`），单次 ≤20 点；**不校验每点合法性以外的业务规则**，避免拖慢；`report_at` 允许乱序，查询按 `report_at ASC` |
| **轨迹数据量** | 200 骑手 × 8s × 12h ≈ 86 万点/天。P0 保留 30 天（定时任务删除），P1 再考虑分区 |
| **运费试算** | 计算顺序：起步价 → 距离阶梯（每 `per_step_distance_m` 加 `per_step_fee`，封顶 `max_distance_m`）→ 时段加价（高峰 11:00-13:00 / 17:00-19:00、夜间 22:00-06:00）→ 恶劣天气（读配置开关）→ 重量/楼层 → 满额减免（`free_over_amount`）。**自取单直接返回 0**。全部 `bigint` 分 |
| **配送费写入订单** | 下单时（`order` 模块）调用 `FreightService.estimate()`；本卡只提供接口，**不改 `OrderApplicationService`**。若需要联动，在 T05/T07 之外单独开卡 |
| **改派** | 改派必须：校验原骑手状态允许释放 → `t_delivery_order.rider_id` 置空 + `status=待接单` + `dispatch_count=0` → 写 `t_dispatch_record(action=REASSIGN)`。**不得**改 `order_id`/`delivery_no` |
| **隐私** | 骑手看用户手机号必须脱敏（`138****8000`），日志不得明文；骑手身份证 `id_card_cipher` 加密 |
| **多租户** | 全部新表带 `tenant_id` |
| **异常上报** | 写 `t_rider_ping_anomaly` 同时写 `t_order_exception`（既有表），后者驱动平台异常订单池 |

---

## 验收标准

```bash
mvn -q -pl solo-eats-delivery -am compile
mvn -q -DskipTests package
mvn -q -pl solo-eats-delivery test -Dtest='DeliveryServiceImplTest'      # 既有测试必须绿
mvn -q -pl solo-eats-order test -Dtest='OrderStateMachineTest'            # 不回归

# 1) 骑手认证 → 未通过前不能抢单
curl -s -X POST http://localhost:8080/api/v1/rider/certification -H "Authorization: Bearer $RIDER_TOKEN" \
  -d '{"realName":"王五","idCard":"110101199001011234","vehicleType":1,"healthCertExpireAt":"2027-06-30"}'
curl -s http://localhost:8080/api/v1/rider/hall -H "Authorization: Bearer $RIDER_TOKEN"
# 断言：code=50006 RIDER_NOT_CERTIFIED

# 2) 审核通过
curl -s -X POST http://localhost:8080/api/v1/admin/riders/1/certification/audit -H "Authorization: Bearer $ADMIN_TOKEN" \
  -d '{"status":1}'

# 3) 上线
curl -s -X PUT http://localhost:8080/api/v1/rider/online -H "Authorization: Bearer $RIDER_TOKEN" \
  -d '{"online":true,"lng":116.481499,"lat":39.997632}'
# 断言：code=0；redis ZSCORE rider:geo 1 有值

# 4) 抢单（两个骑手并发，只成功 1 个）
for i in 1 2; do
  curl -s -X POST http://localhost:8080/api/v1/rider/deliveries/$DID/grab -H "Authorization: Bearer $RIDER${i}_TOKEN" &
done; wait
# 断言：1 个 code=0，1 个 code=50001 DISPATCH_TAKEN；t_delivery_order.rider_id 只被写一次

# 5) 取货 / 送达
curl -s -X POST http://localhost:8080/api/v1/rider/deliveries/$DID/pickup -H "Authorization: Bearer $RIDER_TOKEN"
curl -s -X POST http://localhost:8080/api/v1/rider/deliveries/$DID/delivered -H "Authorization: Bearer $RIDER_TOKEN" -d '{"lng":116.49,"lat":40.0}'
# 断言：状态依次 待取货→配送中→已送达；t_order.status 同步推进

# 6) 轨迹
curl -s -X POST http://localhost:8080/api/v1/rider/track -H "Authorization: Bearer $RIDER_TOKEN" \
  -d '{"deliveryId":'$DID',"points":[{"lng":116.48,"lat":39.99,"reportAt":"2025-01-01 10:00:00"}]}'
curl -s http://localhost:8080/api/v1/c/deliveries/$DID/track -H "Authorization: Bearer $USER_TOKEN"
# 断言：返回点按时间升序；骑手实时位置非空

# 7) 运费试算
curl -s -X POST http://localhost:8080/api/v1/c/freight/estimate \
  -d '{"shopId":1,"lng":116.49,"lat":40.0,"goodsAmount":5000,"weightKg":1.5,"floor":3,"deliverAt":"2025-01-01 12:00:00"}'
# 断言：breakdown 含 起步价/距离费/时段加价/楼层附加；合计 = sum(breakdown)

# 8) 超距离
curl -s -X POST http://localhost:8080/api/v1/c/freight/estimate -d '{"shopId":1,"lng":117.0,"lat":41.0,...}'
# 断言：code=50011

# 9) 改派
curl -s -X POST http://localhost:8080/api/v1/admin/deliveries/$DID/reassign -H "Authorization: Bearer $ADMIN_TOKEN" \
  -d '{"riderId":2,"reason":"原骑手车辆故障"}'
# 断言：code=0；rider_id=2 或 status=待接单（按策略）；dispatch_record 新增 REASSIGN 记录

# 10) 健康证过期 → 拒绝抢单
# 把 health_cert_expire_at 改为昨天后重试 → code=50009
```

---

## 禁止事项

1. **不得重写或绕过既有配送单状态机**：`t_delivery_order.status` 的流转必须走 `DeliveryServiceImpl` 已有方法；不得在 `RiderDispatchService` 里直接 `UPDATE t_delivery_order SET status=?`。
2. **不得修改** `DeliveryService` 接口已有方法签名、`DeliveryOrder`/`RiderIncome` 实体已有字段、`DeliveryOrderStatus` 枚举取值。
3. **不得删除或修改** `DeliveryServiceImplTest` 的断言。
4. **不得把派单权重、抢单半径、运费规则写死在 Java 里**（规格书 9.5 明确要求后台可配）。
5. 不得让 `solo-eats-delivery` 依赖 `solo-eats-order`（依赖方向：`order → delivery`，不可反向）。需要订单信息时用端口接口或直接查 `t_delivery_order` 自带字段。
6. 不得把骑手实时位置只存 DB（必须 Redis GEO + TTL），也不得只存 Redis 不落 `t_rider_online_log`。
7. 不得在抢单时用"先查后改"的非原子操作。
8. 不得明文存储/返回骑手身份证、用户手机号。
9. 不得删除 `t_delivery_order`/`t_dispatch_record` 的历史记录（履约审计）。
10. 不得在本卡改动 `solo-eats-order`/`payment`/`settlement`/`marketing`/`product`。
11. 不得把轨迹上报做成同步阻塞主链路（必须 `@Async` 或批量事务）。

---

## 执行记录

| 日期 | 会话范围 | 新增/修改文件 | 编译结果 | 既有测试 | 遗留问题 |
|---|---|---|---|---|---|
| _待填_ | | | | | |
