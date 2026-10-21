# T05 product-品类 · 商品库(SPU) · 门店商品 · 规格加料 · 估清 · 评价

> 优先级：**P0** · 所属模块：`solo-eats-product`（增量改造，**不重建**）+ `solo-eats-app`
> 分两段：**阶段A（品类树 + SPU 商品库 + 门店商品 + 用户端菜单）→ 阶段B（规格组/加料/估清/评价）**

---

## 目标

**一句话**：在不动 `t_sku` 结构的前提下，补齐「平台品类 → 商品库(SPU) → 门店商品 → 规格 SKU → 加料」的商品主数据链，让用户端能真正渲染出一张可点单的门店菜单。

**完成后系统能做什么**
1. 平台维护二级品类树，供首页金刚区与搜索使用。
2. 平台商品库维护 SPU（图片、详情富文本、标签、餐盒费、起送量、限购）。
3. 商家把 SPU 上架到门店（`t_shop_spu`），可覆盖价格、排序；可一键下架、估清、恢复。
4. 用户端 `GET /c/shops/{shopId}/menu` 一次拿到：分类 → 商品 → 规格 SKU → 加料组，含估清状态与起售量。
5. 已完成订单的消费者可提交评价（综合 + 口味/包装/配送分项 + 标签 + 图片 + 匿名），商家可回复，平台可审核/隐藏/置顶。

---

## 规格书依据

| 章节 | 原文要点 |
|---|---|
| 4.3 平台品类 | 「一级/二级品类树、图标、排序、启停（供首页金刚区与搜索使用）」 |
| 4.3 商品库 | 「平台商品模板库、商品列表、增删改、上下架、多规格 SKU、加料组、餐盒费、库存、限购、起售量、图片与详情富文本、标签（招牌/新品/热销）、排序权重」 |
| 4.3 门店商品审核 | 「门店上架商品/改价审核（可配置为免审）」 |
| 4.3 规格与加料模板 | 「模板复用（杯型/温度/糖度等）与模板管理」 |
| 4.3 估清管理 | 「估清商品查询、批量恢复、估清原因统计」 |
| 4.3 评价管理 | 「评价列表（按门店/商品/骑手维度）、标签库、商家回复查看、审核、隐藏、置顶、差评申诉与跟踪」 |
| 5.2.4 店铺详情页 | 「商品分类锚点：左分类 + 右商品联动；商品卡片：图片、名称、描述、月售、规格、加料、价格、加购按钮、**估清状态**；规格/加料弹窗」 |
| 5.2.8 评价体系 | 「订单评价：综合评分 + 口味/包装/配送分项 + 标签 + 图片 + 文字 + 匿名；骑手评价：单独评分；追评；我的评价」 |
| 2.5 核心领域模型 | 「品类、商品库(SPU)、规格(SKU)、加料组、门店商品、估清记录；**SPU 1:N SKU；门店商品引用商品库**」 |
| 技术架构 2.4 | 「`t_sku`（shop_id, spu_id, sku_name, spec_json, image_url, price, status）、`t_sku_stock`、`t_stock_lock`、`t_stock_log` **已存在**」 |

---

## 前置依赖

| 依赖 | 具体依赖物 |
|---|---|
| **T04** | `Shop` 实体 + `ShopService`（校验门店存在/营业）；`t_shop` |
| **T03** | `FileStoragePort`（商品图）；`t_file` |
| **T01/T02** | `@RequirePermission`、`TenantContext` |
| 既有表（**不可改结构**） | `t_sku`（`id, shop_id, spu_id, sku_name, spec_json, image_url, price, status`）、`t_sku_stock`（`uk_shop_sku`）、`t_stock_lock`、`t_stock_log` |
| 既有类（**不可改语义**） | `com.soloeats.product.domain.entity.Sku`、`SkuStock`、`StockLock`、`StockLog`；`service/SkuService`、`StockService`、`MerchantSkuService`（+ impl）；`mapper/*`；`solo-eats-app/controller/MerchantSkuController.java`；`dto/CreateSkuRequest.java`、`UpdateSkuRequest.java`、`UpdateSkuStockRequest.java`；测试 `MerchantSkuServiceImplTest`、`StockServiceImplTest` |
| 既有调用方 | `solo-eats-order` 下单时调用 `SkuService`/`StockService` 校验价格与锁库存；`OrderItem` 存 `spu_id`/`sku_id`/`spec_json` 快照 —— **字段必须能取到值，本卡不能改这些写入逻辑** |

### ⚠️ 兼容方案（本卡最关键的设计决策）

| 现状 | 结论 |
|---|---|
| `t_sku` 已含 `shop_id`，本质就是「门店 SKU」 | **保留 `t_sku` 作为门店 SKU 表，不新建 `t_shop_sku`，不重构表结构** |
| `t_sku.spu_id` 引用的 SPU 表**不存在**；`V2__sku_and_demo_data.sql` 里 `spu_id=1/2` 是裸值 | 本卡新建 `t_spu`，并在迁移脚本里**回填** `id=1`（招牌牛肉面）、`id=2`（冰柠檬茶）两条 SPU，使既有 demo 数据自洽 |
| `t_sku.status` 与「上架/估清」语义重合 | `t_sku.status`：0 上架 / 1 下架（**沿用，不改语义**）；估清走 `t_sku_stock.sold_out`（**已存在**）；`t_shop_spu.status` 只表示「SPU 在门店的上下架」，二者层级不同 |
| 商品审核（4.3「可配置为免审」） | P0 默认**免审**（`app.product.price-audit-enabled=false`），`t_shop_spu.audit_status` 直接写 2 通过 |

---

## 数据模型

迁移脚本：`V9__product_catalog.sql`（阶段A）、`V10__product_spec_addon_review.sql`（阶段B）

### 阶段A

| 表 | 关键字段 | 索引 |
|---|---|---|
| `t_category` | id, tenant_id, parent_id, category_name, icon_url, level tinyint(1/2), sort, status tinyint(0启用 1停用), created_at/updated_at/deleted | `uk_tenant_name(tenant_id, parent_id, category_name)`、`idx_parent_sort(parent_id, sort)` |
| `t_spu` | id, tenant_id, merchant_id（NULL=平台商品库公共模板）, category_id, spu_name, subtitle, description, **detail_html longtext**, main_image, images_json json, tags_json json（`["招牌","新品","热销"]`）, **packaging_fee bigint**, **min_purchase_qty int DEFAULT 1**, **limit_per_order int DEFAULT 0（0=不限）**, status tinyint(0草稿 1待审 2已上架 3已下架), sort_weight int, month_sales int, version, created_at/updated_at/deleted | `uk_tenant_name(tenant_id, merchant_id, spu_name)`、`idx_category_status(category_id, status)`、`idx_merchant(merchant_id)` |
| `t_shop_spu` | id, tenant_id, shop_id, spu_id, **price_override bigint NULL**（NULL=用 SKU 价）, **status tinyint(0上架 1下架 2估清)**, **audit_status tinyint(0免审 1待审 2通过 3驳回)**, sort_weight, month_sales, suspended_until datetime(3)（估清自动恢复时间）, remark, version, created_at/updated_at/deleted | `uk_shop_spu(shop_id, spu_id)`、`idx_shop_status(shop_id, status, sort_weight)` |
| `t_shop_category` | id, tenant_id, shop_id, category_name, sort, status, created_at/updated_at/deleted | `idx_shop_sort(shop_id, sort)` |

### 阶段B

| 表 | 关键字段 | 索引 |
|---|---|---|
| `t_spec_group` | id, tenant_id, group_name（杯型/温度/糖度）, group_type tinyint(1单选 2多选), required tinyint, sort, is_template tinyint, created_at/updated_at/deleted | `idx_tenant(group_id)` |
| `t_spec_option` | id, tenant_id, group_id, option_name, **price_delta bigint DEFAULT 0**, sort, status, created_at/updated_at/deleted | `idx_group(group_id, sort)` |
| `t_spu_spec_rel` | id, tenant_id, spu_id, group_id, sort | `uk_spu_group(spu_id, group_id)` |
| `t_addon_group` | id, tenant_id, group_name, min_select int, max_select int, sort, is_template tinyint, created_at/updated_at/deleted | — |
| `t_addon_item` | id, tenant_id, group_id, item_name, **price bigint**, stock int NULL, sort, status, created_at/updated_at/deleted | `idx_group(group_id)` |
| `t_spu_addon_rel` | id, tenant_id, spu_id, group_id | `uk_spu_addon(spu_id, group_id)` |
| `t_sold_out_record` | id, tenant_id, shop_id, sku_id, spu_id, reason varchar(128), **sold_out_at**, **restored_at**, operator_id, operator_name, created_at | `idx_shop_time(shop_id, sold_out_at)`、`idx_sku(sku_id)` |
| `t_product_review` | id, tenant_id, order_id, order_item_id, user_id, shop_id, spu_id, sku_id, rider_id, **overall_score tinyint(1~5)**, taste_score, package_score, delivery_score, content varchar(500), images_json json, tags_json json, anonymous tinyint, **status tinyint(0待审 1已通过 2已隐藏)**, **top_flag tinyint**, merchant_reply varchar(500), reply_at, append_content varchar(500), append_at, created_at/updated_at/deleted | `uk_order_item(order_item_id)`、`idx_shop_status(shop_id, status, created_at)`、`idx_spu(spu_id, status)`、`idx_user(user_id, created_at)`、`idx_rider(rider_id)` |
| `t_review_tag` | id, tenant_id, tag_name, tag_type tinyint(1好评 2差评), sort, status, created_at | `idx_type_sort(tag_type, sort)` |

---

## 接口清单

| # | 方法 | 路径 | 要点 | 权限 |
|---|---|---|---|---|
| 1 | GET | `/api/v1/admin/categories/tree` | 品类树（含 icons/sort） | `product:category:list` |
| 2 | POST/PUT/DELETE | `/api/v1/admin/categories`、`/{id}` | `{parentId,categoryName,iconUrl,sort,status}`；最多两级，删除校验无子级/无 SPU 引用 | `product:category:*` |
| 3 | GET | `/api/v1/admin/spus` | `categoryId,status,keyword,pageNum,pageSize` → `PageResult<SpuView>` | `product:spu:list` |
| 4 | POST/PUT | `/api/v1/admin/spus`、`/{id}` | SPU 全量字段（含 `images[]`、`tags[]`、`packagingFee`、`minPurchaseQty`、`limitPerOrder`） | `product:spu:*` |
| 5 | PUT | `/api/v1/admin/spus/{id}/status` | 上架/下架/待审 | `product:spu:update` |
| 6 | GET | `/api/v1/merchant/spus` | 商家可用商品库（平台公共 + 本商家私有） | `MERCHANT` |
| 7 | POST | `/api/v1/merchant/shop-spus` | `{shopId,spuId,priceOverride,sortWeight}` 上架到门店；重复上架返回已有记录 | `MERCHANT` |
| 8 | GET | `/api/v1/merchant/shop-spus` | `shopId,status,keyword,pageNum,pageSize` | `MERCHANT` |
| 9 | PUT | `/api/v1/merchant/shop-spus/{id}` | 改 `priceOverride`/`sortWeight`（受 `price-audit-enabled` 影响，P0 免审） | `MERCHANT` |
| 10 | PUT | `/api/v1/merchant/shop-spus/{id}/status` | `{status}` 0上架 1下架 2估清 | `MERCHANT` |
| 11 | GET | `/api/v1/c/shops/{shopId}/menu` | **一次返回**：`[{categoryName, items:[{spuId,spuName,mainImage,minPrice,monthSales,tags,soldOut,skus:[{skuId,skuName,specJson,price,availableQty,soldOut}], addonGroups:[...]}]}]` | 白名单 |
| 12 | GET | `/api/v1/c/spus/{spuId}` | 商品详情（含加料组、规格组、富文本详情） | 白名单 |
| 13 | POST | `/api/v1/merchant/sold-out` | `{shopId,skuIds[],reason}` 批量估清（同步置 `t_sku_stock.sold_out=1` + 写 `t_sold_out_record`） | `MERCHANT` |
| 14 | DELETE | `/api/v1/merchant/sold-out` | `{shopId,skuIds[]}` 批量恢复 | `MERCHANT` |
| 15 | GET | `/api/v1/admin/sold-out` | 估清查询 + 原因统计（`groupBy reason`） | `product:soldout:list` |
| 16 | GET | `/api/v1/admin/spec-templates`、`/addon-templates` | 规格/加料模板列表 | `product:template:list` |
| 17 | POST/PUT/DELETE | `/api/v1/admin/spec-templates`、`/addon-templates`（+`/{id}`） | 模板维护（组 + 选项一体化提交） | `product:template:*` |
| 18 | POST | `/api/v1/c/reviews` | `{orderId,orderItemId,overallScore,tasteScore,packageScore,deliveryScore,content,images[],tags[],anonymous}` 幂等 | `C_USER` + `@Idempotent` |
| 19 | GET | `/api/v1/c/shops/{shopId}/reviews` | `tag,hasImage,pageNum,pageSize` | 白名单 |
| 20 | GET | `/api/v1/c/reviews/mine` | 我的评价（含商家回复） | `C_USER` |
| 21 | POST | `/api/v1/merchant/reviews/{id}/reply` | `{reply}`（≤500 字，仅一次） | `MERCHANT` |
| 22 | GET | `/api/v1/admin/reviews` | `shopId,spuId,riderId,status,scoreLevel,pageNum,pageSize` | `product:review:list` |
| 23 | PUT | `/api/v1/admin/reviews/{id}/status` | `{status}` 通过/隐藏；`{topFlag}` 置顶 | `product:review:audit` |

**新增错误码**（4xxxx 段追加）：`40101 CATEGORY_NOT_FOUND`、`40102 CATEGORY_HAS_CHILDREN`、`40103 SPU_NOT_FOUND`、`40104 SHOP_SPU_EXISTS`、`40105 SPU_NOT_ON_SALE`、`40106 REVIEW_NOT_ALLOWED`（订单未完成/已评价/超期）、`40107 REVIEW_EXISTS`。

---

## 需要新增/修改的文件

### 阶段A（16 个）

| 类型 | 路径 |
|---|---|
| 新 | `solo-eats-app/src/main/resources/db/migration/V9__product_catalog.sql`（建 3 表 + **回填 `t_spu` id=1,2 对应既有 `t_sku.spu_id`** + 建 `t_shop_spu` 关联既有 SKU 所属门店） |
| 新 | `solo-eats-product/src/main/java/com/soloeats/product/domain/entity/Category.java` |
| 新 | `solo-eats-product/src/main/java/com/soloeats/product/domain/entity/Spu.java` |
| 新 | `solo-eats-product/src/main/java/com/soloeats/product/domain/entity/ShopSpu.java` |
| 新 | `solo-eats-product/src/main/java/com/soloeats/product/domain/entity/ShopCategory.java` |
| 新 | `solo-eats-product/src/main/java/com/soloeats/product/mapper/CategoryMapper.java`、`SpuMapper.java`、`ShopSpuMapper.java`、`ShopCategoryMapper.java` |
| 新 | `solo-eats-product/src/main/java/com/soloeats/product/service/CategoryService.java` + `impl` |
| 新 | `solo-eats-product/src/main/java/com/soloeats/product/service/SpuService.java` + `impl` |
| 新 | `solo-eats-product/src/main/java/com/soloeats/product/service/ShopSpuService.java` + `impl` |
| 新 | `solo-eats-product/src/main/java/com/soloeats/product/service/ShopMenuService.java` + `impl`（**用户端菜单聚合**，是本卡性能关键路径） |
| 新 | `solo-eats-product/src/main/java/com/soloeats/product/service/command/SaveSpuCommand.java`、`SaveCategoryCommand.java`、`PutOnShopCommand.java` |
| 新 | `solo-eats-product/src/main/java/com/soloeats/product/service/view/SpuView.java`、`CategoryNode.java`、`ShopMenuView.java`（含嵌套 `MenuItemView`/`MenuItemSkuView`） |
| 新 | `solo-eats-app/src/main/java/com/soloeats/app/controller/AdminCategoryController.java` |
| 新 | `solo-eats-app/src/main/java/com/soloeats/app/controller/AdminSpuController.java` |
| 新 | `solo-eats-app/src/main/java/com/soloeats/app/controller/MerchantSpuController.java` |
| 新 | `solo-eats-app/src/main/java/com/soloeats/app/controller/ShopMenuController.java`（`/c/**`） |

### 阶段B（16 个）

| 类型 | 路径 |
|---|---|
| 新 | `solo-eats-app/src/main/resources/db/migration/V10__product_spec_addon_review.sql` |
| 新 | `solo-eats-product/src/main/java/com/soloeats/product/domain/entity/SpecGroup.java`、`SpecOption.java`、`SpuSpecRel.java`、`AddonGroup.java`、`AddonItem.java`、`SpuAddonRel.java`、`SoldOutRecord.java`、`ProductReview.java`、`ReviewTag.java`（9） |
| 新 | 对应 9 个 Mapper |
| 新 | `solo-eats-product/src/main/java/com/soloeats/product/service/SpecTemplateService.java` + `impl` |
| 新 | `solo-eats-product/src/main/java/com/soloeats/product/service/SoldOutService.java` + `impl`（**内部必须复用既有 `SkuStockMapper` 的条件更新**） |
| 新 | `solo-eats-product/src/main/java/com/soloeats/product/service/ProductReviewService.java` + `impl` |
| 新 | `solo-eats-app/src/main/java/com/soloeats/app/controller/AdminTemplateController.java` |
| 新 | `solo-eats-app/src/main/java/com/soloeats/app/controller/MerchantSoldOutController.java` |
| 新 | `solo-eats-app/src/main/java/com/soloeats/app/controller/ReviewController.java`（`/c/reviews` + `/merchant/reviews` + `/admin/reviews` 三个 mapping 可拆 3 个文件） |
| 改 | `solo-eats-product/src/main/java/com/soloeats/product/service/impl/MerchantSkuServiceImpl.java`（在 `updateStock` 中估清时同步写 `t_sold_out_record`，**不得改变原有返回值与异常语义**） |
| 新 | `solo-eats-product/src/test/java/com/soloeats/product/service/impl/SoldOutServiceImplTest.java` |
| 新 | `solo-eats-product/src/test/java/com/soloeats/product/service/impl/ProductReviewServiceImplTest.java` |

---

## 关键实现约束

| 项 | 约束 |
|---|---|
| **`t_sku` 零结构变更** | 只允许 `INSERT`/`UPDATE` 数据，**禁止** `ALTER TABLE t_sku`。`spec_json` 继续存规格快照（`{"杯型":"大杯","糖度":"少糖"}`），格式与现有 `CreateSkuCommand.specJson()` 一致 |
| **菜单接口性能** | `GET /c/shops/{shopId}/menu` 是本卡 P95 关键路径（要求 ≤300ms）：必须**一次 SQL 批量查**（`shop_spu join spu join sku left join sku_stock`），**禁止 N+1**；结果缓存 Redis `menu:shop:{shopId}:v{version}`，TTL 5min，门店/商品变更时按 key 前缀 `DEL` |
| **估清一致性** | 估清 = `t_shop_spu.status=2` **且** `t_sku_stock.sold_out=1` **且** 写 `t_sold_out_record`；三者必须同一事务。恢复时三步反向。**估清不影响已下单未支付订单的库存锁定**（锁定走 `t_stock_lock`，与估清正交） |
| **下单兼容** | `solo-eats-order` 下单走 `SkuService` 取价、`StockService` 锁库存。本卡新增的 `price_override` **不得**在 `SkuService` 里生效（P0 简化：门店改价直接改 `t_sku.price`，`price_override` 仅记录，P1 再启用），否则会打破现有下单价格断言与 `MerchantSkuServiceImplTest` |
| **价格精度** | 所有金额 `bigint` 分；`price_delta` 可为负（如"小杯 -200"），加料 `price` 必须 ≥0 |
| **限购/起售** | 起售量与限购在**下单时**校验（`order` 模块），本卡只提供字段；`GET /c/spus/{id}` 返回 `minPurchaseQty/limitPerOrder` 供前端提示 |
| **评价资格** | 必须满足：订单 `status = 已完成`、`order_item_id` 属于该订单、订单 `completed_at + app.order.after-sales-days` 内、同一 `order_item_id` 未评价（`uk_order_item` 兜底）。任一不满足抛 `40106` |
| **评价幂等** | `POST /c/reviews` 加 `@Idempotent(bizType="product-review")`；`DuplicateKeyException` 转 `40107` |
| **评分联动** | 评价通过审核后异步更新 `t_shop.rating/rating_taste/rating_package/rating_delivery`（取近 90 天平均）与 `t_spu.month_sales`。**异步失败不回滚评价**，靠定时任务兜底 |
| **多租户** | 全部新表带 `tenant_id`；`t_category`/`t_spu`（`merchant_id IS NULL` 的平台公共库）需在 `TenantLineInnerInterceptor` 的忽略表里？→ **不忽略**，平台库同样带 `tenant_id=1` |
| **逻辑删除** | 全部用 `deleted`；`t_sold_out_record` 与 `t_product_review` 不做物理删除 |
| **XSS** | `detail_html` 入库前用白名单标签过滤（`p,br,img,strong,em,ul,ol,li`），`content`/`reply` 纯文本转义 |
| **图片** | `main_image`/`images_json` 必须是 T03 的 `t_file.url`，不接受任意外链（校验域名前缀） |

---

## 验收标准

```bash
mvn -q -pl solo-eats-product -am compile
mvn -q -DskipTests package
# 既有测试必须全绿（重点）：
mvn -q -pl solo-eats-product test -Dtest='MerchantSkuServiceImplTest,StockServiceImplTest'
mvn -q -pl solo-eats-order test -Dtest='OrderStateMachineTest,RefundServiceImplTest'

# 1) 品类
curl -s -X POST http://localhost:8080/api/v1/admin/categories -H "Authorization: Bearer $ADMIN_TOKEN" \
  -d '{"parentId":0,"categoryName":"美食餐饮","iconUrl":"https://...","sort":1}'

# 2) 商品库
curl -s -X POST http://localhost:8080/api/v1/admin/spus -H "Authorization: Bearer $ADMIN_TOKEN" \
  -d '{"categoryId":1,"spuName":"招牌牛肉面","mainImage":"https://...","packagingFee":100,
       "minPurchaseQty":1,"limitPerOrder":5,"tags":["招牌"]}'
# 断言：code=0

# 3) 上架到门店
curl -s -X POST http://localhost:8080/api/v1/merchant/shop-spus -H "Authorization: Bearer $MERCHANT_TOKEN" \
  -d '{"shopId":1,"spuId":1,"sortWeight":100}'
# 断言：code=0；重复上架 code=40104

# 4) 用户端菜单（一次拿全）
curl -s http://localhost:8080/api/v1/c/shops/1/menu
# 断言：code=0；返回 2 个分类、至少 1 个商品；每个商品含 skus[] 且 sku 含 price(分) 与 soldOut

# 5) 估清 → 菜单里 soldOut=true
curl -s -X POST http://localhost:8080/api/v1/merchant/sold-out -H "Authorization: Bearer $MERCHANT_TOKEN" \
  -d '{"shopId":1,"skuIds":[1001],"reason":"牛肉售完"}'
curl -s http://localhost:8080/api/v1/c/shops/1/menu | grep -o '"soldOut":true'
# 断言：命中；且 t_sku_stock.sold_out=1

# 6) 估清后下单 → 仍可锁库存（估清与锁定正交，由既有逻辑决定）
# 7) 下单不会因为 price_override 改变金额（回归）
curl -s -X POST http://localhost:8080/api/v1/orders ... # 用 docs/订单接口联调与验收.md 的用例
# 断言：金额与既有断言一致

# 8) 评价
curl -s -X POST http://localhost:8080/api/v1/c/reviews -H "Authorization: Bearer $USER_TOKEN" \
  -H "Idempotency-Key: rev-001" \
  -d '{"orderId":1,"orderItemId":1,"overallScore":5,"tasteScore":5,"packageScore":5,"deliveryScore":4,
       "content":"好吃","tags":["分量足"],"anonymous":0}'
# 断言：code=0；重复提交 code=0 且不新增记录（幂等）

# 9) 未完成订单评价 → 拒绝
# 断言：code=40106

# 10) 商家回复
curl -s -X POST http://localhost:8080/api/v1/merchant/reviews/1/reply -H "Authorization: Bearer $MERCHANT_TOKEN" \
  -d '{"reply":"感谢支持"}'
# 断言：code=0；二次回复 code=40106/40001
```

---

## 禁止事项

1. **不得 `ALTER TABLE t_sku`**（增删改列一律禁止）。理由：`order` 模块的 `SkuService` 强依赖现有列，且 `V2` 已有数据。
2. **不得删除或重命名** `Sku`/`SkuStock`/`StockLock`/`StockLog` 实体与 `SkuService`/`StockService`/`MerchantSkuService` 的任何**已有方法签名**。
3. **不得修改** `/api/v1/merchant/skus` 下 4 个既有接口的路径、请求体、响应体（`MerchantSkuController` 与前端 `MerchantProductsView.vue` 依赖）。
4. **不得删除** `MerchantSkuServiceImplTest`、`StockServiceImplTest` 或修改其断言。
5. **不得**在 `SkuService` 里让 `price_override` 生效（会打破订单金额回归）。
6. 不得把 `t_sku.status` 与 `t_shop_spu.status`、`t_sku_stock.sold_out` 三者的语义混用或互相覆盖。
7. 不得让菜单接口出现 N+1 查询，也不得在循环里 `selectList`。
8. 不得把商品图存本地磁盘（必须走 MinIO）。
9. 不得允许评价修改（一旦提交不可编辑；追评单独字段且只能一次）。
10. 不得在本卡改动 `solo-eats-order`/`delivery`/`settlement`/`marketing` 的代码；如确需联动，用**接口预留 + TODO**，在后续卡实现。
11. 不得把 `detail_html` 原样返回而未做 XSS 过滤。

---

## 执行记录

| 日期 | 会话范围 | 新增/修改文件 | 编译结果 | 既有测试 | 遗留问题 |
|---|---|---|---|---|---|
| _待填_ | | | | | |
