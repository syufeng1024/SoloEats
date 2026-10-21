# T04 store-门店（CRUD · 营业状态 · 配送围栏 · 门店账号 · 到店自取配置）

> 优先级：**P0** · 所属模块：`solo-eats-store`（新建）+ `solo-eats-app`
> 单段执行（约 22 个文件），是本项目**第一个真正"用户端能看见"的业务主数据**。

---

## 目标

**一句话**：把「商家」落成「门店」——一个带经纬度、营业时段、配送围栏、起送价/配送费、自取配置的门店实体，并且门店账号能直接登录并只看自己门店的数据。

**完成后系统能做什么**
1. 商家管理员能创建/编辑门店（地图选点经纬度、多时段营业时间、头图/门头照）。
2. 平台可启用/停用门店；门店可自助切换「营业 / 休息 / 打烊」。
3. 用户端能按「经纬度 + 半径」查附近门店，并按综合/距离/销量/评分排序（先做前 3 种）。
4. 下单前能调用「配送范围校验」接口：返回是否在围栏内、是否达起送价、预估配送费与预计送达时间。
5. 到店自取可开关，可配取餐码前缀/位数/有效期/备餐时长/自取立减。
6. 门店账号（店长/店员）能登录，token 带 `shopId`，`PermissionGuard.resolveShopId()` 通过。

---

## 规格书依据

| 章节 | 原文要点 |
|---|---|
| 4.2 门店管理 | 「门店列表、增删改、地址与经纬度（地图选点）、**营业时间（支持多时段）**、**营业状态（营业/打烊/休息）**、联系电话、门店图片/视频、配送方式（平台配送 / 到店自取；本期不含商家自配送）、**起送价、配送费、打包费、预计出餐时长**、门店公告」 |
| 4.2 到店自取 | 「自取开关、自取营业时段、**取餐码规则（前缀/位数/有效期）**、自取备餐时长、自取优惠设置（如自取立减）、自取订单核销记录与超时未取处理」 |
| 4.2 配送范围 | 「门店配送围栏（**圆形/多边形地图绘制**）、起送距离、超范围提示（**自取订单不受配送围栏限制**）」 |
| 4.2 连锁管理 | 「品牌 → 门店层级、品牌统一配置下发」 |
| 5.2.2 首页 | 「附近好店推荐：卡片流：头图、名称、评分、月售、起送价、配送费、预计送达、距离、活动标签；排序筛选：综合/距离最近/销量最高/评分最高/起送价最低」 |
| 2.5 核心领域模型 | 「商家 1:N 门店；门店 1:1 结算账户」 |
| 8.6 到店自取流程 | 自取单不校验配送围栏、免配送费、取餐码唯一（`uk_pickup_code(shop_id, pickup_code_date, pickup_code)` 已存在于 `t_order`） |

---

## 前置依赖

| 依赖 | 具体依赖物 |
|---|---|
| **T01** | `@RequireRole`/`@RequirePermission`；`SysUserService`、`SysUserRoleMapper`（建门店账号时要绑 `MERCHANT_CLERK` 角色）；`PasswordHasher` |
| **T02 阶段A** | `TenantContext`、`t_sys_tenant` |
| **T03** | `Merchant` 实体 + `MerchantMapper`（门店必须归属一个已通过的商家）；`FileStoragePort`（门店图片）；`t_file` |
| 既有表 | `t_order` 已有 `shop_id`、`pickup_code`、`pickup_code_date` 等列，**门店主数据缺失导致这些列目前只能靠 demo 常量（shop_id=1）** |
| 既有类 | `common/api/PageResult`、`BizException`、`ErrorCode`、`Idempotent`、`AuthContext`、`UserPrincipal` |

---

## 数据模型

迁移脚本：`V8__store.sql`

| 表 | 关键字段 | 索引 |
|---|---|---|
| `t_shop` | id, tenant_id, merchant_id, shop_no varchar(32), shop_name, brand_id, category_id, province, city, district, address, **lng decimal(10,6)**, **lat decimal(10,6)**, contact_name, contact_phone, logo_url, cover_url, image_urls json, announcement varchar(500), **status tinyint(0休息 1营业 2打烊 3停用)**, delivery_types tinyint（位掩码：1平台配送 2到店自取）, **min_order_amount bigint（起送价，分）**, **delivery_fee bigint（配送费，分）**, **packaging_fee bigint（打包费，分）**, **prep_minutes int（预计出餐时长）**, delivery_radius_m int, rating decimal(3,2), rating_taste/rating_package/rating_delivery decimal(3,2), month_sales int, sort_weight int, **pickup_enabled tinyint**, pickup_prepare_minutes int, pickup_timeout_minutes int, **pickup_code_prefix varchar(8)**, **pickup_code_length tinyint DEFAULT 6**, **pickup_discount bigint DEFAULT 0（自取立减，分）**, settle_account_id, version, created_at/updated_at/deleted | `uk_shop_no(shop_no)`、`idx_merchant(merchant_id)`、`idx_city_status(city, status)`、`idx_geo(lat, lng)`、`idx_sort(status, sort_weight, month_sales)` |
| `t_shop_business_hour` | id, tenant_id, shop_id, day_of_week tinyint(1~7), start_time time, end_time time, cross_day tinyint（跨天，如 22:00-02:00）, created_at | `idx_shop_day(shop_id, day_of_week)` |
| `t_shop_delivery_area` | id, tenant_id, shop_id, **area_type tinyint(1圆形 2多边形)**, center_lng decimal(10,6), center_lat decimal(10,6), **radius_m int**, **polygon_json json**（`[[lng,lat],...]`）, status tinyint(0启用 1停用), created_at/updated_at/deleted | `idx_shop(shop_id)` |
| `t_shop_account` | id, tenant_id, shop_id, merchant_id, user_id, account_role tinyint(1店长 2店员), is_default tinyint（登录默认门店）, status, created_at/updated_at/deleted | `uk_shop_user(shop_id, user_id)`、`idx_user(user_id)` |
| `t_shop_decoration` | id, tenant_id, shop_id, config_json json（招牌/橱窗/推荐分类/活动位）, version int, status, created_at/updated_at | `uk_shop(shop_id)` |

> `t_shop_decoration` 本卡只建表 + 提供读写接口，前端所见即所得预览放 T10。

---

## 接口清单

| # | 方法 | 路径 | 要点 | 权限 |
|---|---|---|---|---|
| 1 | GET | `/api/v1/admin/shops` | `merchantId`, `status`, `city`, `keyword`, `pageNum/pageSize` → `PageResult<ShopView>` | `store:shop:list` |
| 2 | POST | `/api/v1/admin/shops` | `{merchantId,shopName,address,lng,lat,contactName,contactPhone,deliveryTypes,minOrderAmount,deliveryFee,packagingFee,prepMinutes}` | `store:shop:create` |
| 3 | PUT | `/api/v1/admin/shops/{id}` | 同上 | `store:shop:update` |
| 4 | PUT | `/api/v1/admin/shops/{id}/status` | `{status}` 0休息 1营业 2打烊 3停用 | `store:shop:update` |
| 5 | PUT | `/api/v1/admin/shops/{id}/delivery-area` | `{areaType,centerLng,centerLat,radiusM,polygon:[{lng,lat}...]}` | `store:shop:update` |
| 6 | GET | `/api/v1/admin/shops/{id}/delivery-area` | 围栏详情 | `store:shop:list` |
| 7 | GET | `/api/v1/merchant/shops` | 本商家门店列表（`merchantId` 取自 token） | `MERCHANT` |
| 8 | POST | `/api/v1/merchant/shops` | 商家自建门店（受 `t_sys_package.max_shop` 软校验） | `MERCHANT` |
| 9 | PUT | `/api/v1/merchant/shops/{id}` | 编辑（**不允许**改 `merchantId`、`rating`、`month_sales`） | `MERCHANT` |
| 10 | PUT | `/api/v1/merchant/shops/{id}/business-hours` | `{hours:[{dayOfWeek,startTime,endTime}]}` 全量替换 | `MERCHANT` |
| 11 | PUT | `/api/v1/merchant/shops/{id}/status` | 营业状态开关（营业/休息/打烊） | `MERCHANT` |
| 12 | PUT | `/api/v1/merchant/shops/{id}/pickup` | `{pickupEnabled,pickupPrepareMinutes,pickupTimeoutMinutes,pickupCodePrefix,pickupCodeLength,pickupDiscount}` | `MERCHANT` |
| 13 | POST | `/api/v1/merchant/shops/{id}/accounts` | `{username,password,nickname,phone,accountRole}` → 建账号 + 绑门店 | `MERCHANT` |
| 14 | GET | `/api/v1/merchant/shops/{id}/accounts` | 门店账号列表 | `MERCHANT` |
| 15 | DELETE | `/api/v1/merchant/shops/{id}/accounts/{userId}` | 解绑（不删用户） | `MERCHANT` |
| 16 | GET | `/api/v1/merchant/shops/{id}/decoration` | 门店装修配置 | `MERCHANT` |
| 17 | PUT | `/api/v1/merchant/shops/{id}/decoration` | 保存装修配置 | `MERCHANT` |
| 18 | GET | `/api/v1/c/shops/{id}` | 用户端门店详情（营业状态、营业时间、公告、评分、起送、配送费、自取开关） | 白名单 |
| 19 | GET | `/api/v1/c/shops` | `lng,lat,radius,keyword,sortBy(comprehensive/distance/sales)`, `pageNum/pageSize` → 附近门店 | 白名单 |
| 20 | POST | `/api/v1/c/shops/{id}/delivery-check` | `{lng,lat,goodsAmount,fulfillmentType}` → `{inRange,reason,minOrderAmount,deliveryFee,expectedDeliveryAt,prepMinutes,deliveryDistanceM}` | 白名单 |
| 21 | GET | `/api/v1/c/shops/{id}/business-status` | 当前是否可下单 + 下一个营业时段 | 白名单 |

**新增错误码**（7xxxx 段）：`70101 SHOP_NOT_FOUND`、`70102 SHOP_CLOSED`（未营业/打烊）、`70103 SHOP_OUT_OF_RANGE`（超配送范围）、`70104 SHOP_BELOW_MIN_ORDER`（未达起送价）、`70105 SHOP_PICKUP_DISABLED`。

---

## 需要新增/修改的文件

| 类型 | 路径 |
|---|---|
| 改 | `pom.xml`（`<modules>` 增 `solo-eats-store`；`dependencyManagement` 增坐标） |
| 新 | `solo-eats-store/pom.xml`（依赖 `solo-eats-common`、`solo-eats-system`、`solo-eats-merchant`、`mybatis-plus`、`lombok`） |
| 改 | `solo-eats-app/pom.xml`（依赖 `solo-eats-store`） |
| 新 | `solo-eats-app/src/main/resources/db/migration/V8__store.sql` |
| 新 | `solo-eats-store/src/main/java/com/soloeats/store/domain/entity/Shop.java` |
| 新 | `solo-eats-store/src/main/java/com/soloeats/store/domain/entity/ShopBusinessHour.java` |
| 新 | `solo-eats-store/src/main/java/com/soloeats/store/domain/entity/ShopDeliveryArea.java` |
| 新 | `solo-eats-store/src/main/java/com/soloeats/store/domain/entity/ShopAccount.java` |
| 新 | `solo-eats-store/src/main/java/com/soloeats/store/domain/entity/ShopDecoration.java` |
| 新 | `solo-eats-store/src/main/java/com/soloeats/store/domain/enums/ShopStatus.java` |
| 新 | `solo-eats-store/src/main/java/com/soloeats/store/domain/enums/DeliveryType.java`（位掩码工具：`hasDelivery()/hasPickup()`） |
| 新 | `solo-eats-store/src/main/java/com/soloeats/store/mapper/ShopMapper.java`、`ShopBusinessHourMapper.java`、`ShopDeliveryAreaMapper.java`、`ShopAccountMapper.java`、`ShopDecorationMapper.java` |
| 新 | `solo-eats-store/src/main/java/com/soloeats/store/service/ShopService.java` + `impl/ShopServiceImpl.java`（门店 CRUD + 营业状态 + 营业时段） |
| 新 | `solo-eats-store/src/main/java/com/soloeats/store/service/ShopGeoService.java` + `impl`（围栏校验、附近查询、距离计算） |
| 新 | `solo-eats-store/src/main/java/com/soloeats/store/service/ShopAccountService.java` + `impl` |
| 新 | `solo-eats-store/src/main/java/com/soloeats/store/service/ShopDecorationService.java` + `impl` |
| 新 | `solo-eats-store/src/main/java/com/soloeats/store/service/command/`（`SaveShopCommand`、`SaveDeliveryAreaCommand`、`SaveBusinessHourCommand`、`CreateShopAccountCommand`、`DeliveryCheckCommand`） |
| 新 | `solo-eats-store/src/main/java/com/soloeats/store/service/view/`（`ShopView`、`ShopDetailView`、`DeliveryCheckResult`、`NearbyShopView`、`ShopAccountView`） |
| 新 | `solo-eats-store/src/main/java/com/soloeats/store/support/GeoUtils.java`（Haversine 距离 + 射线法多边形判定） |
| 新 | `solo-eats-app/src/main/java/com/soloeats/app/controller/AdminShopController.java` |
| 新 | `solo-eats-app/src/main/java/com/soloeats/app/controller/MerchantShopController.java` |
| 新 | `solo-eats-app/src/main/java/com/soloeats/app/controller/ShopClientController.java` |
| 新 | `solo-eats-app/src/main/java/com/soloeats/app/dto/SaveShopRequest.java`、`SaveDeliveryAreaRequest.java`、`DeliveryCheckRequest.java`、`SaveBusinessHourRequest.java`、`CreateShopAccountRequest.java` |
| 新 | `solo-eats-store/src/test/java/com/soloeats/store/support/GeoUtilsTest.java`（圆内/圆外/多边形内/多边形外 4 组用例） |

---

## 关键实现约束

| 项 | 约束 |
|---|---|
| **围栏判定** | 圆形：Haversine 距离 ≤ `radius_m`。多边形：**射线法**，`polygon_json` 至少 3 个点、最多 50 个点；首尾点自动闭合。**自取订单跳过围栏校验**（`fulfillmentType=2` 直接 `inRange=true`） |
| **距离计算** | 用 Haversine（地球半径 6371000m），**不要**引入第三方地图 SDK 做距离（地图 Key 只用于前端展示），保证离线可测 |
| **附近查询** | P0 用「矩形预筛 + 内存精筛」：`WHERE lat BETWEEN ? AND ? AND lng BETWEEN ? AND ?`（按 `radius` 换算经纬度增量），再在 Java 里算精确距离排序。**不做** MySQL 空间索引（P1 再上 `POINT` + `ST_Distance_Sphere`） |
| **营业状态** | `status` 三值人工开关；**是否真正可下单** = `status=1` **且** 当前时间落在 `t_shop_business_hour` 任一区间内。两个判断分离：`status` 是老板的意愿，营业时段是规则。`business-status` 接口返回二者合成结论 |
| **跨天时段** | `cross_day=1` 时区间为 `[start_time, 24:00) ∪ [00:00, end_time)`，判定必须正确 |
| **配送费计算** | P0 固定取 `t_shop.delivery_fee`（不走运费模板，运费模板在 T07）；自取单 `deliveryFee=0`。**金额单位统一分** |
| **起送价** | 按**商品小计**（不含打包费/配送费）判断，符合 9.8「门槛按折后商品小计判断，不含配送费与打包费」 |
| **门店账号** | 建账号复用 `SysUserService.create()`（`user_type=2`，`shop_id=?`，`merchant_id=?`），并绑 `MERCHANT_CLERK` 角色；**同一 user 可关联多门店**（`t_shop_account`），多门店切换用请求头 `X-Shop-Id`（见 README Q4；本卡先落表，切换在 T15 落地） |
| **唯一性** | `shop_no` 生成规则 `S{merchantId 4位}{自增 4位}`，冲突重试 3 次 |
| **逻辑删除** | `t_shop` 删除前必须校验：无进行中订单（`t_order WHERE shop_id=? AND status NOT IN (已完成/已取消/已退款)`）、无在售商品；否则抛 `PARAM_INVALID` 带原因 |
| **禁用词** | 门店名称/公告需过滤 HTML（防 XSS），公告长度 ≤500 |
| **多租户** | 全部表带 `tenant_id`，自动填充；跨租户不可见由 `TenantLineInnerInterceptor` 保证 |
| **金额字段** | `min_order_amount`/`delivery_fee`/`packaging_fee`/`pickup_discount` 全部 `bigint` 分；接口出入参也用 `long`，**禁止** `BigDecimal` 传金额 |
| **坐标精度** | `lng/lat` 用 `BigDecimal(10,6)`；接口用 `double` 会丢精度，必须用 `BigDecimal` |

---

## 验收标准

```bash
mvn -q -pl solo-eats-store -am compile
mvn -q -DskipTests package

# 1) 商家建店
curl -s -X POST http://localhost:8080/api/v1/merchant/shops -H "Authorization: Bearer $MERCHANT_TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"shopName":"示例餐饮·望京店","address":"北京市朝阳区望京SOHO","lng":116.481499,"lat":39.997632,
       "contactName":"张三","contactPhone":"13800000001","deliveryTypes":3,
       "minOrderAmount":2000,"deliveryFee":300,"packagingFee":100,"prepMinutes":15}'
# 断言：code=0；shop_no 形如 S00010001

# 2) 营业时段（含跨天）
curl -s -X PUT http://localhost:8080/api/v1/merchant/shops/1/business-hours -H "Authorization: Bearer $MERCHANT_TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"hours":[{"dayOfWeek":1,"startTime":"09:00","endTime":"22:00"},{"dayOfWeek":1,"startTime":"22:00","endTime":"02:00"}]}'

# 3) 围栏（圆形 3km）
curl -s -X PUT http://localhost:8080/api/v1/admin/shops/1/delivery-area -H "Authorization: Bearer $ADMIN_TOKEN" \
  -d '{"areaType":1,"centerLng":116.481499,"centerLat":39.997632,"radiusM":3000}'
# 断言：code=0

# 4) 范围内
curl -s -X POST http://localhost:8080/api/v1/c/shops/1/delivery-check \
  -d '{"lng":116.490000,"lat":39.999000,"goodsAmount":2500,"fulfillmentType":1}'
# 断言：inRange=true；deliveryFee=300

# 5) 范围外
curl -s -X POST http://localhost:8080/api/v1/c/shops/1/delivery-check \
  -d '{"lng":116.600000,"lat":40.100000,"goodsAmount":2500,"fulfillmentType":1}'
# 断言：inRange=false；reason 含"超出配送范围"

# 6) 自取跳过围栏 + 免配送费
curl -s -X POST http://localhost:8080/api/v1/c/shops/1/delivery-check \
  -d '{"lng":116.600000,"lat":40.100000,"goodsAmount":1500,"fulfillmentType":2}'
# 断言：inRange=true；deliveryFee=0；inRange 为 true 但 minOrderAmount 校验仍生效

# 7) 未达起送价
curl -s -X POST http://localhost:8080/api/v1/c/shops/1/delivery-check -d '{"goodsAmount":1000,"fulfillmentType":1,...}'
# 断言：code=70104

# 8) 附近门店
curl -s "http://localhost:8080/api/v1/c/shops?lng=116.48&lat=39.99&radius=5000&sortBy=distance"
# 断言：list 非空；按 distance 升序

# 9) 门店账号登录并只看到自己的店
curl -s -X POST http://localhost:8080/api/v1/auth/login -d '{"username":"shop1-clerk","password":"..."}'
curl -s "http://localhost:8080/api/v1/merchant/shops" -H "Authorization: Bearer $CLERK_TOKEN"
# 断言：只返回 shopId=1，请求 shopId=2 返回 10004
```

---

## 禁止事项

1. **不得修改 `t_order`**（含 `pickup_code` 相关列）。取餐码唯一约束已存在，门店只需提供前缀/位数配置。
2. **不得**在本卡实现运费模板（距离区间计价/时段加价/天气加价）——那是 T07 的范围；本卡只用门店固定 `delivery_fee`。
3. 不得引入第三方地图 SDK 到后端（GeoUtils 自实现），也不得把地图 Key 写进后端代码。
4. 不得让 `solo-eats-store` 依赖 `product`/`order`/`delivery`（依赖方向：`store → merchant → system → common`）。
5. 不得把 `shop_id=1` 之类的 demo 常量删除——现有 `V2__sku_and_demo_data.sql`、`OrderLifecycleIntegrationTest` 依赖门店 1 存在。**新增门店必须不破坏 shop_id=1 的既有数据。**
6. 不得用 `double` 存经纬度或计算金额。
7. 不得让「营业状态开关」直接影响历史订单状态（只影响能否下新单）。
8. 不得删除门店账号时连带删除 `t_sys_user`（可能该 user 还关联其他门店）。
9. 不得在本卡改动 `solo-eats-product`/`order`/`delivery`/`settlement`/`marketing`。

---

## 执行记录

| 日期 | 会话范围 | 新增/修改文件 | 编译结果 | 遗留问题 |
|---|---|---|---|---|
| _待填_ | | | | |
