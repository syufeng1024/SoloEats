# T10 content-首页装修 · 广告位 · 搜索运营 · 公告 · App 版本

> 优先级：**P1** · 所属模块：`solo-eats-content`（新建）+ `solo-eats-app`
> 单段执行（约 16 个文件）

---

## 落地前核对与勘误（2026-10-27）

### 1. 迁移号是 `V26`，不是本文件写的 `V16`

`V16` 已被 `V16__delivery_freight_income.sql` 占用；当前实际最大为 **`V25`**（T09 阶段A 刚落）。
故本卡用 **`V26__content.sql`**。动手前先跑 `ls solo-eats-app/src/main/resources/db/migration/ | sort -V | tail -3` 复核，以**实际最大 +1** 为准。
`CLAUDE.md` 第 6 节「当前最大 `V3`」是过期描述，不要照抄。

### 2. 菜单权限种子：现最大 id = 130，本卡从 133 起

`t_sys_menu` 一级「内容运营」= **id 9**。T09 阶段A 已占用 120~130，T09 阶段B 预留 131~132，故本卡从 **133** 起：

| id | parent | 名称 | type | path | perms |
|---|---|---|---|---|---|
| 133 | 9 | 首页装修 | 2 | `/content/home` | `content:home:list` |
| 134 | 133 | 楼层编辑 | 3 | NULL | `content:home:update` |
| 135 | 133 | 首页发布 | 3 | NULL | `content:home:publish` |
| 136 | 9 | 广告位管理 | 2 | `/content/banner` | `content:banner:list` |
| 137 | 136 | 广告位编辑 | 3 | NULL | `content:banner:save` |
| 138 | 9 | 搜索运营 | 2 | `/content/search` | `content:search:list` |
| 139 | 138 | 搜索词编辑 | 3 | NULL | `content:search:save` |
| 140 | 9 | App 版本 | 2 | `/content/version` | `content:version:list` |
| 141 | 140 | 版本编辑 | 3 | NULL | `content:version:save` |

本卡「接口清单」里的 `content:banner:*` / `content:search:*` / `content:version:*` 通配写法，落地为上面具体的 `:list` / `:save` 两个码。
种子一律 `ON DUPLICATE KEY UPDATE id = id`，并给 `tenant_id=1, role_id=1` 授 `t_sys_role_menu`——**漏授会全部 10004**。

### 3. 依赖调用方式：校验走 service 接口，聚合走自建只读 Mapper

`CLAUDE.md` 第 2 节红线：领域模块之间**只能依赖 service 接口**，不得直接依赖对方的 `domain` 实体或 `mapper`。已核对的可用方法：

| 用途 | 既有方法 |
|---|---|
| 校验 `categoryId` 存在 | `product` → `CategoryService.tree()`（返回树，需自行摊平查找） |
| 校验 `shopId` 存在且营业 | `store` → `ShopService.detail(shopId)`、`ShopService.businessStatus(shopId)` |
| 校验 `spuId` 存在 | `product` → `SpuService.detail(spuId)` |

**注意：没有**「推荐店铺列表」「猜你喜欢商品列表」这类现成方法，也没有 `CategoryService.getById`。
所以首页聚合里的「推荐店铺 / 猜你喜欢」**由 content 模块自建只读 Mapper 写原生 SQL 直查 `t_shop` / `t_shop_spu`**（与 `solo-eats-report` 的 `ReportSourceMapper` 同法），**禁止**去改 `store` / `product` 的代码或实体。校验类走上面的 service 接口。

### 4. 公告不建表

`t_sys_notice` 已由 T02 建好（已核实存在）。`floor_type=7 公告条` 只读引用，**不得**新建公告表。

### 5. 错误码不冲突

本卡 `90020 CONTENT_FLOOR_INVALID`、`90021 CONTENT_VERSION_EXISTS` 与 T09 的 `90010~90013` 不冲突，可照用。

---

## 目标

**一句话**：让用户端首页**不是写死的**——Banner、金刚区、楼层、搜索词、公告、版本更新全部由后台配置并按生效时间/人群下发。

**完成后系统能做什么**：后台可拖拽式配置首页楼层（Banner/金刚区/秒杀位/推荐店铺/猜你喜欢）、广告位、默认与热搜搜索词；用户端一次请求拿到完整首页配置，且能按生效时间与投放人群过滤；App 启动可查最新版本与强制更新标记。

---

## 规格书依据

| 章节 | 原文要点 |
|---|---|
| 4.9 内容运营 | 「首页装修：轮播 Banner、金刚区（品类入口，关联 4.3 平台品类）、楼层（秒杀/神券/推荐店铺/猜你喜欢）、推荐位排序、跳转配置（商品/店铺/活动/页面）、**生效时间、投放人群、所见即所得预览**；广告位管理：广告位定义（首页轮播/弹窗/启动图/店铺页）、素材、跳转、生效时间、投放统计；搜索运营：默认搜索词、热搜词、联想词、置顶结果、搜索屏蔽词；公告管理；**App 版本管理：端（用户端/骑手端/商家端）、版本号、更新说明、下载地址、强制更新、灰度比例**」 |
| 5.2.2 首页 | 「轮播广告（后台 4.9 配置）、金刚区分类（后台 4.3 平台品类驱动）、限时活动区、附近好店推荐、猜你喜欢、排序筛选」 |
| 5.2.3 搜索 | 「搜索建议：联想词、历史搜索、热门搜索（后台 4.9 配置）」 |
| 1.6 P1 | 「消息模板、**App 版本管理**」 |

---

## 前置依赖

| 依赖 | 具体依赖物 |
|---|---|
| **T04** | `Shop`（楼层"推荐店铺"引用 `shop_id`） |
| **T05** | `Category`（金刚区引用 `category_id`）、`Spu`（推荐商品引用 `spu_id`） |
| **T03** | `FileStoragePort`（Banner/素材图） |
| **T01/T02** | 鉴权、`TenantContext` |
| 依赖方向 | `content → store/product → ...`；**不得**被 `store/product` 反向依赖 |

---

## 数据模型

迁移脚本：`V16__content.sql`

| 表 | 关键字段 | 索引 |
|---|---|---|
| `t_home_page` | id, tenant_id, page_code varchar(32)（`USER_HOME`/`USER_MINE`）, page_name, **status tinyint(0草稿 1已发布 2已下线)**, publish_at, version int, created_at/updated_at/deleted | `uk_tenant_page(tenant_id, page_code)` |
| `t_home_floor` | id, tenant_id, page_id, **floor_type tinyint(1轮播 2金刚区 3秒杀 4神券 5推荐店铺 6猜你喜欢 7公告条)**, title, **config_json json**, sort, **effective_from**, **effective_to**, **audience tinyint(0全部 1新客 2会员 3指定用户)**, audience_json json, status, created_at/updated_at/deleted | `idx_page_sort(page_id, sort)`、`idx_effective(status, effective_from, effective_to)` |
| `t_banner` | id, tenant_id, **slot_code varchar(32)**（`HOME_TOP`/`HOME_POPUP`/`SPLASH`/`SHOP_TOP`）, title, image_url, **jump_type tinyint(1商品 2店铺 3活动 4页面 5无)**, jump_value varchar(255), sort, effective_from, effective_to, **click_count bigint**, **view_count bigint**, status, created_at/updated_at/deleted | `idx_slot_sort(slot_code, sort)`、`idx_effective(status, effective_from, effective_to)` |
| `t_search_keyword` | id, tenant_id, keyword varchar(64), **keyword_type tinyint(1默认词 2热搜词 3联想词 4屏蔽词)**, weight int, status, created_at/updated_at/deleted | `uk_tenant_kw(tenant_id, keyword, keyword_type)`、`idx_type_weight(keyword_type, weight)` |
| `t_app_version` | id, tenant_id, **app_type tinyint(1用户端 2骑手端 3商家端)**, version_name varchar(32), version_code int, update_desc varchar(1000), download_url varchar(512), **force_update tinyint**, **gray_percent tinyint(0~100)**, status, publish_at, created_at/updated_at/deleted | `uk_type_code(tenant_id, app_type, version_code)` |

> `t_announcement` 已在 T02 用 `t_sys_notice` 承载，本卡**不重复建表**，只在首页楼层 `floor_type=7` 里引用公告列表。

---

## 接口清单

| # | 方法 | 路径 | 要点 | 权限 |
|---|---|---|---|---|
| 1 | GET | `/api/v1/admin/content/home?pageCode=USER_HOME` | 页面 + 楼层列表（含 `config_json`） | `content:home:list` |
| 2 | POST/PUT | `/api/v1/admin/content/home/floors`（+`/{id}`） | 新增/编辑楼层 `{floorType,title,config,sort,effectiveFrom,effectiveTo,audience}` | `content:home:update` |
| 3 | DELETE | `/api/v1/admin/content/home/floors/{id}` | 删楼层 | `content:home:update` |
| 4 | PUT | `/api/v1/admin/content/home/floors/sort` | `{ids:[...]}` 批量排序 | `content:home:update` |
| 5 | POST | `/api/v1/admin/content/home/publish` | `{pageCode}` 发布（校验金刚区类目/店铺/商品存在） | `content:home:publish` |
| 6 | GET | `/api/v1/admin/content/home/preview?pageCode=USER_HOME` | 所见即所得预览（返回渲染数据，不落库） | `content:home:list` |
| 7 | GET/POST/PUT/DELETE | `/api/v1/admin/banners`（+`/{id}`） | `{slotCode,title,imageUrl,jumpType,jumpValue,sort,effectiveFrom,effectiveTo}` | `content:banner:*` |
| 8 | GET | `/api/v1/admin/search-keywords` | `keywordType` 筛选 | `content:search:list` |
| 9 | POST/PUT/DELETE | `/api/v1/admin/search-keywords`（+`/{id}`） | 关键词维护（含屏蔽词） | `content:search:*` |
| 10 | GET/POST/PUT | `/api/v1/admin/app-versions`（+`/{id}`） | 版本维护（`appType` 区分三端） | `content:version:*` |
| 11 | GET | `/api/v1/c/home?pageCode=USER_HOME` | **用户端首页聚合**：楼层 + Banner + 金刚区 + 推荐店铺/商品 | 白名单（可选 token） |
| 12 | GET | `/api/v1/c/banners?slotCode=HOME_TOP` | 按生效时间过滤的 Banner | 白名单 |
| 13 | GET | `/api/v1/c/search/suggest?keyword=` | 联想词（含热搜 Top10 + 默认词 + 屏蔽词过滤） | 白名单 |
| 14 | GET | `/api/v1/c/app/version?appType=1&currentCode=100` | 最新版本 + `forceUpdate` + `hasUpdate`；灰度按 `userId % 100 < grayPercent` 判定 | 白名单 |

**新增错误码**（9xxxx 段追加，与 T08/T09 同段不同码值）：`90020 CONTENT_FLOOR_INVALID`（引用的类目/店铺/商品不存在）、`90021 CONTENT_VERSION_EXISTS`。

---

## 需要新增/修改的文件

| 类型 | 路径 |
|---|---|
| 改 | `pom.xml`（`<modules>` + `dependencyManagement` 增 `solo-eats-content`） |
| 新 | `solo-eats-content/pom.xml`（依赖 `solo-eats-common`、`solo-eats-system`、`solo-eats-store`、`solo-eats-product`、`mybatis-plus`、`lombok`） |
| 改 | `solo-eats-app/pom.xml` |
| 新 | `solo-eats-app/src/main/resources/db/migration/V16__content.sql` |
| 新 | `solo-eats-content/src/main/java/com/soloeats/content/domain/entity/` `HomePage`、`HomeFloor`、`Banner`、`SearchKeyword`、`AppVersion`（5） |
| 新 | `.../domain/enums/FloorType.java`、`JumpType.java`、`AppType.java`、`KeywordType.java` |
| 新 | 5 个 Mapper |
| 新 | `.../service/HomePageService.java` + `impl`（含 `config_json` 引用校验 + 用户端聚合） |
| 新 | `.../service/BannerService.java` + `impl` |
| 新 | `.../service/SearchKeywordService.java` + `impl` |
| 新 | `.../service/AppVersionService.java` + `impl` |
| 新 | `.../service/view/`（`HomePageView`、`FloorView`、`BannerView`、`AppVersionCheckResult`、`SearchSuggestView`） |
| 新 | `.../service/command/`（`SaveFloorCommand`、`SaveBannerCommand`） |
| 新 | `solo-eats-app/src/main/java/com/soloeats/app/controller/AdminContentController.java` |
| 新 | `solo-eats-app/src/main/java/com/soloeats/app/controller/HomeClientController.java` |
| 新 | `solo-eats-content/src/test/java/com/soloeats/content/service/impl/AppVersionServiceImplTest.java`（灰度判定 100 组用例） |

---

## 关键实现约束

| 项 | 约束 |
|---|---|
| **首页聚合性能** | `GET /c/home` P95 ≤300ms：一次性查 5 张表后用内存组装，**禁止**在楼层循环里查店铺/商品；推荐店铺/商品各限 10 条；结果缓存 Redis `home:USER_HOME:v{publishVersion}`，TTL 5min，发布时 `DEL` |
| **生效时间** | 所有内容按 `status=1` **且** `now BETWEEN effective_from AND effective_to`（`effective_to` 为 NULL 视为长期）过滤；SQL 里判断，不在 Java 过滤 |
| **投放人群** | P0 只实现 `audience=0 全部` / `1 新客` / `2 会员`；`3 指定用户` 落库但**不生效**（`TODO(P1)`），前端不会看到 |
| **引用校验** | 发布前必须校验 `config_json` 里的 `categoryId/shopId/spuId` 存在且已上架/营业，否则 `90020` 并列出缺失项 |
| **版本灰度** | `grayPercent=0` 全量不发；`100` 全量发；其余按 `userId % 100 < grayPercent`；**未登录用 `deviceId` hash** |
| **强制更新** | `forceUpdate=1` 且 `hasUpdate=true` 时前端阻断；**必须**同时校验 `versionCode > currentCode` |
| **Banner 统计** | `view_count` 在 `/c/banners` 里异步 +1（Redis 累加，定时任务每 5min 刷回 DB），`click_count` 由前端埋点接口 +1 |
| **搜索屏蔽词** | 联想词生成时先剔除命中屏蔽词的候选；屏蔽词不返回给前端 |
| **XSS** | `title`/`update_desc` 纯文本；`config_json` 里的富文本必须先过白名单 |
| **多租户** | 全部表带 `tenant_id` |
| **逻辑删除** | 全部用 `deleted`；已发布页面删除必须先去发布（`status=2`） |

---

## 验收标准

```bash
mvn -q -pl solo-eats-content -am compile && mvn -q -DskipTests package

# 建楼层
curl -s -X POST http://localhost:8080/api/v1/admin/content/home/floors -H "Authorization: Bearer $ADMIN_TOKEN" \
  -d '{"pageCode":"USER_HOME","floorType":2,"title":"金刚区",
       "config":{"items":[{"categoryId":1,"icon":"https://..."}]},"sort":1,
       "effectiveFrom":"2025-01-01 00:00:00","effectiveTo":null,"audience":0}'
# 断言：code=0

# 发布（引用不存在类目 → 失败）
curl -s -X POST http://localhost:8080/api/v1/admin/content/home/publish -H "Authorization: Bearer $ADMIN_TOKEN" \
  -d '{"pageCode":"USER_HOME"}'
# 断言：code=90020（若 categoryId 不存在）

# 用户端首页
curl -s "http://localhost:8080/api/v1/c/home?pageCode=USER_HOME"
# 断言：code=0；floors 按 sort 升序；金刚区含 categoryId

# 过期楼层不下发
# 把 effectiveTo 改为昨天 → 再请求 → 该楼层消失

# 搜索建议
curl -s "http://localhost:8080/api/v1/c/search/suggest?keyword=牛"
# 断言：含联想词；屏蔽词不出现

# 版本检查（灰度）
curl -s "http://localhost:8080/api/v1/c/app/version?appType=1&currentCode=100"
# 断言：grayPercent=0 时 hasUpdate=false；=100 时 true；=50 时按 userId 判定稳定
```

---

## 禁止事项

1. 不得在 `content` 模块**重复建公告表**（复用 T02 `t_sys_notice`）。
2. 不得让 `content` 被 `store`/`product` 依赖（依赖方向单向）。
3. 不得在首页聚合里出现 N+1 查询。
4. 不得把 `config_json` 的结构散落在 Java 常量里而不做校验（发布前必须校验引用）。
5. 不得在 `/c/home` 返回未发布（`status≠1`）或已过期的内容。
6. 不得信任前端传来的 `currentVersionCode` 让后端直接"通过"强制更新判定（必须与库内最新版本比较）。
7. 不得改动 T04/T05 的实体与接口。
8. 不得把 Banner 图片存本地磁盘（走 MinIO / `t_file`）。

---

## 执行记录

| 日期 | 会话范围 | 新增/修改文件 | 编译结果 | 遗留问题 |
|---|---|---|---|---|
| _待填_ | | | | |
