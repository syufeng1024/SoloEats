# T17 web-运营后台与商家中心页面（补充卡）

> 优先级：**P0（批次 0：支撑 P0 功能的配置界面）/ P1（批次 1~4：其余页面）** · 所属：`solo-eats-web`（Vue3 + Vite + Element Plus）
> 按批交付：每批 4~6 页（约 18 个文件/批），与后端卡一一对应
> 说明：本卡不在团队最初的 T01~T15 清单内，是架构师识别的缺口——规格书 4.0/4.13 要求运营后台约 82 页、商家中心约 13 页，现状只有 5 个业务页。规格书 13 章要求「后台每个可配置项必须真实驱动各端效果」，而支撑 P0 功能的配置（商家审核/门店/商品/基础券/工作台）若无界面，P0 验收（13 章「通过率 100%」）无法达成。故拆出「批次 0（P0 先行）」，在对应后端 P0 卡交付后即补齐其最小操作界面；其余完整页面按批次 1~4 排 P1。

---

## 商家中心独立工程（甲方已确认，本卡必须落实）

规格书 1.7:297、2.1:314-321、9.4:1458、9.5:1462、13:1847、14.6 甲方确认 #1 六处要求：**平台运营后台**与**商家中心 Web** 为两个**独立前端工程**（独立仓库/构建/部署/域名），共用同一后端与 RBAC。

**落地方案（本卡采用「同仓双入口」）**：
1. 现有 `solo-eats-web` 继续作为**平台运营后台**（`/admin/**` + 系统管理），保持既有 5 个业务页与路由不动。
2. 新建 `solo-eats-web-merchant/`（同仓库子目录，独立 `package.json` + `vite.config.ts` + `src/`），承载**商家中心**（`/merchant/**`）。两份工程各自独立 `npm run build`、独立产出 `dist/`、独立部署目录与访问域名，互不引用源码。
3. **共享层抽离**：把 `api/http.ts`、`utils/money.ts`、`stores/auth.ts`、`constants/*` 等通用件放 `solo-eats-web/shared/`（或独立 `packages/web-shared/`），两工程通过 npm workspace / 相对依赖复用，**禁止**商家中心 `import` 运营后台的页面组件。
4. **登录与 RBAC 复用**：商家中心仍调 `POST /api/v1/auth/login` + `GET /auth/menus`，靠 token 的 `userType/roles` 拿到「本商家/本门店」菜单；`X-Shop-Id` 多门店切换（见 README Q4）。
5. **验收口径**：两条 `npm run build`（`solo-eats-web` + `solo-eats-web-merchant`）必须分别通过，且商家中心可**独立部署**到 `/merchant/` 域名访问（Nginx 分离），满足 13 章「独立工程」。

> 本卡「批次 0」起的 `views/merchant/*` 页面一律归入 `solo-eats-web-merchant/`，不再写入 `solo-eats-web`。已有 `MerchantOrdersView` 等 3 个商家页面从 `solo-eats-web` 迁出到新工程（保留路径，避免回归）。

---

## 目标

**一句话**：每交付一个后端模块，就在 Web 端补齐"能看见、能操作"的列表页 + 表单页，避免后端接口无人调用。

**完成后系统能做什么**：运营人员能在浏览器里完成商家审核、门店管理、商品库维护、订单干预、骑手审核、派单配置、抽佣规则、提现审核、报表查看、系统管理；商家能在商家中心完成商品/门店/订单/自取核销/结算。

---

## 规格书依据

| 章节 | 原文要点 |
|---|---|
| 4.0 后台导航结构 | 「工作台 / 商家招商 / 商品中心 / 订单中心 / 配送中心 / 营销中心 / 用户中心 / 资金中心 / 内容运营 / 客服与工单 / 数据报表 / 系统管理」 |
| 4.13 商家中心 | 「深青绿主题，浅色内容区 + 深色侧边栏；工作台聚合今日营业额/订单量/待处理、今日订单流水、出餐与自取、热销 TOP5、近 7 日出餐时长」 |
| 1.7 技术栈 | 「平台运营后台 / 商家中心：Vue 3 + Vite + Element Plus + **ECharts**，两个独立前端工程」 |
| 4.12 | 系统管理 20 余个功能点（用户/角色/菜单/部门/岗位/字典/租户/日志/邮件/公告） |
| 9.4 兼容性 | 「Chrome/Edge 最新版，1366×768 起；商家中心 H5 自适应，最小 1024×768」 |

---

## 前置依赖

| 依赖 | 具体依赖物 |
|---|---|
| **T01/T02** | 登录（账号密码）+ `/auth/me` + `/auth/menus`（前端动态路由） |
| **T03/T04/T05** | 商家、门店、商品三组接口 |
| **T07/T16** | 骑手/配送/抽佣/提现接口 |
| **T11** | 报表接口（ECharts 数据源） |
| 既有前端 | `solo-eats-web/src/{api,stores,router,layouts,views,utils,constants}` 已有骨架；`LoginView.vue`、`AdminOverviewView.vue`、`MerchantOrdersView.vue`、`MerchantProductsView.vue`、`Admin/MerchantSettlementsView.vue` **不得重写**，只能扩展 |
| 既有工具（**复用**） | `api/http.ts`（axios 封装）、`utils/money.ts`、`utils/datetime.ts`、`utils/idempotency.ts`、`utils/debounce.ts`、`stores/auth.ts`、`constants/orderStatus.ts`、`constants/roles.ts`、`constants/settlementStatus.ts` |
| 需新增依赖 | `echarts` + `vue-echarts`（仅报表页用） |

---

## 数据模型（前端）

| 内容 | 说明 |
|---|---|
| 动态路由 | 登录后 `GET /auth/menus` → 转 `RouteRecordRaw`；本地静态路由只保留 `Login`/`NotFound`/`Layout` |
| 权限指令 | `v-perm="'system:user:create'"` → 无权限隐藏按钮；从 `store.auth.permissions` 读 |
| 统一列表页范式 | `useTable()` composable（分页、loading、筛选重置、导出占位） |
| 金额 | 一律 `money.ts` 的 `fenToYuan` 展示，提交 `yuanToFen` |

---

## 接口清单（本卡消费，均由前序卡提供）

| # | 方法 | 路径 | 用途 |
|---|---|---|---|
| 1 | POST | `/api/v1/auth/login` | 账号密码登录 |
| 2 | GET | `/api/v1/auth/me`、`/auth/menus` | 当前用户 + 菜单 |
| 3 | GET/POST/PUT | `/api/v1/admin/users`、`/admin/roles`、`/admin/menus/tree` | 系统管理 |
| 4 | GET | `/api/v1/admin/logs/oper`、`/admin/logs/login` | 日志中心 |
| 5 | GET/POST | `/api/v1/admin/merchants`、`/{id}/approve`、`/{id}/reject` | 商家招商 |
| 6 | GET/POST/PUT | `/api/v1/admin/shops`、`/{id}/delivery-area` | 门店管理 |
| 7 | GET/POST/PUT | `/api/v1/admin/categories`、`/admin/spus` | 商品中心 |
| 8 | GET/PUT | `/api/v1/admin/riders`、`/{id}/certification/audit` | 骑手管理 |
| 9 | GET/PUT | `/api/v1/admin/dispatch-config`、`/admin/freight-templates` | 配送中心 |
| 10 | GET/POST/PUT | `/api/v1/admin/commission-rules`、`/admin/payouts/{id}/audit` | 资金中心 |
| 11 | GET | `/api/v1/admin/report/dashboard`、`/overview`、`/funnel`、`/products`、`/shops`、`/riders`、`/finance` | 数据报表 |
| 12 | GET/PUT | `/api/v1/admin/tickets`、`/{id}/accept|handle|finish` | 客服工单 |
| 13 | GET/POST/PUT | `/api/v1/admin/content/home/floors`、`/admin/banners`、`/admin/app-versions` | 内容运营 |
| 14 | GET/POST/PUT | `/api/v1/admin/promotions`、`/admin/member-levels` | 营销中心 |

---

## 需要新增/修改的文件

### 批次 0（P0 先行：支撑 P0 功能的配置界面，约 8 个 + 商家中心工程骨架）

> 随对应后端 P0 卡交付后立即补齐，**不得等到批次 1~4**。目标：P0 验收时这些配置项可真实操作。

| 页面 | 对应后端卡 | 说明 |
|---|---|---|
| `views/admin/merchant/ApplyListView.vue`、`AuditView.vue` | T03 | 商家入驻审核（P0） |
| `views/admin/store/ShopListView.vue`、`ShopFormView.vue` | T04 | 门店管理（P0） |
| `views/admin/product/SpuListView.vue` | T05 | 商品库基础列表（P0） |
| `views/admin/marketing/CouponView.vue` | 既有券后端 | **基础券管理页（P0，1.6 平台后台 P0 含"基础券"）** |
| 改 `views/AdminOverviewView.vue` | T11 阶段A | 工作台今日卡片（P0） |

**商家中心独立工程骨架（批次 0 一并搭好）**：

| 类型 | 路径 | 说明 |
|---|---|---|
| 新 | `solo-eats-web-merchant/package.json`、`vite.config.ts`、`index.html` | 独立工程（base `/merchant/`） |
| 新 | `solo-eats-web-merchant/src/{main.ts,App.vue,router/index.ts,pages.json}` | 商家中心入口与路由 |
| 新 | `solo-eats-web-merchant/src/api/http.ts` | 复用共享层的 axios 封装（`X-Shop-Id` 头） |
| 改 | `solo-eats-web/shared/`（抽 `http.ts`/`money.ts`/`stores/auth.ts`/`constants/*`） | 两工程共用，禁止商家中心 import 运营后台页面组件 |
| 迁 | `MerchantOrdersView.vue`、`MerchantProductsView.vue`、`MerchantSettlementsView.vue` | 从 `solo-eats-web` 迁到 `solo-eats-web-merchant/`（保留路径与逻辑） |

### 批次 1：框架与系统管理（18 个）

| 类型 | 路径 |
|---|---|
| 新 | `solo-eats-web/src/api/system.ts`（用户/角色/菜单/部门/岗位/字典/日志） |
| 新 | `solo-eats-web/src/stores/menu.ts`（动态路由生成） |
| 新 | `solo-eats-web/src/directives/permission.ts`（`v-perm`） |
| 新 | `solo-eats-web/src/composables/useTable.ts` |
| 新 | `solo-eats-web/src/components/PageHeader.vue`、`SearchForm.vue`、`DataTable.vue`、`MoneyText.vue`、`StatusTag.vue` |
| 改 | `solo-eats-web/src/router/index.ts`（改为「静态 + 动态」混合；保留既有路由） |
| 改 | `solo-eats-web/src/layouts/AppLayout.vue`（菜单改为接口驱动，保留既有兜底菜单） |
| 改 | `solo-eats-web/src/views/LoginView.vue`（**保留**既有的 role+id 快捷登录，**新增**账号密码表单 tab） |
| 新 | `solo-eats-web/src/views/admin/system/UserView.vue`、`RoleView.vue`、`MenuView.vue`、`DeptView.vue`、`DictView.vue`、`OperLogView.vue`、`LoginLogView.vue`（7） |
| 新 | `solo-eats-web/src/api/http.ts`（**改**：加 `X-Shop-Id`、401 跳登录、错误码拦截；保持既有导出不变） |

### 批次 2：招商 · 门店 · 商品（16 个）

`views/admin/merchant/{ApplyListView,AuditView,MerchantListView}.vue`、`views/admin/store/{ShopListView,ShopFormView,DeliveryAreaView}.vue`、`views/admin/product/{CategoryView,SpuListView,SpuFormView,TemplateView,SoldOutView,ReviewView}.vue`、`api/{merchant,store,product}.ts`（扩展）；商家中心侧：`solo-eats-web-merchant/src/views/store/{ShopSettingView,PickupSettingView}.vue`

### 批次 3：订单 · 配送 · 骑手（14 个）

`views/admin/order/{OrderListView,OrderDetailView,AfterSaleView,ExceptionPoolView}.vue`、`views/admin/delivery/{RiderListView,RiderAuditView,DispatchCenterView,DeliveryListView,FreightTemplateView}.vue`、`api/delivery.ts`；商家中心侧：`solo-eats-web-merchant/src/views/order/{PickupVerifyView,ReceiptView}.vue`

### 批次 4 必选前置任务：菜单 component 与前端静态路由映射机制

**缺口现状**：后端 `t_sys_menu.component` 存的是前端组件路径，且已种入 4 个尚无对应页面的菜单——`finance/payout/index`（V19 迁移，菜单 id 110，`/finance/payout`）、`finance/commissionRule/index`（V27，id 142）、`finance/commissionRecord/index`（V27，id 144）、`finance/fundFlow/index`（V27，id 145）。而 `solo-eats-web/src/router/index.ts` 是逐条静态声明 `component: () => import('@/views/xxx.vue')`，**没有**用 `import.meta.glob` 解析后端下发的 `component` 字段；`solo-eats-web/src/views/` 下也不存在 `finance/` 目录（实测只有 `admin/`、`merchant/`、`LoginView.vue`、`NotFoundView.vue`）。

**后果**：这 4 个菜单点进去无匹配路由，落到 `NotFoundView`（白屏）。属既有架构缺口，非本轮引入。

**候选方案**：

| 方案 | 做法 | 改动面 | 风险 |
|---|---|---|---|
| A | 前端建 `import.meta.glob('/src/views/**/*.vue')` 映射表，按后端 `component` 字符串动态解析组件 | `router/index.ts` 路由装配 1 处；后端 `component` 必须与 `views/` 相对路径严格一致（大小写敏感，V27 种的是 `finance/commissionRule/index` 驼峰目录） | 解析不到时须回退 `NotFoundView` 并打 warning，否则菜单静默消失；glob 用 `{ eager: false }` 保持路由懒加载，避免首屏 chunk 膨胀；构建期缺文件只能在运行时报错，需补一条「菜单 component 全量可解析」的单测 |
| B | 保留静态路由，`t_sys_menu.component` 降级为纯展示字段，另建「菜单 → 路由名」映射清单（前端常量表） | 新增 1 个映射常量文件 + 后续每加菜单都要改它并重新发版 | 清单与 `router/index.ts` 两处维护，漏改一处同样白屏；后端新配菜单无法随配置生效，必须前端发版 |

**强制要求**：批次 4 任何页面开发开始前，必须先选定 A 或 B，并把上述 4 个历史菜单回填到所选机制中、实测点击可达。否则批次 4 新做的资金页面依旧点不开。

### 批次 4：资金 · 报表 · 客服 · 内容 · 营销（18 个）

`views/admin/finance/{CommissionRuleView,PayoutAuditView,FundFlowView}.vue`、`views/admin/report/{DashboardView,FunnelView,ProductRankView,FinanceReportView}.vue`、`views/admin/service/{TicketListView,TicketDetailView,FaqView}.vue`、`views/admin/content/{HomeDesignView,BannerView,AppVersionView}.vue`、`views/admin/marketing/{PromotionView,MemberLevelView}.vue`、`api/{settlement,report,service,content,marketing}.ts`（扩展）

---

## 关键实现约束

| 项 | 约束 |
|---|---|
| **不重写既有页面** | `LoginView`/`AdminOverviewView`/`MerchantOrdersView`/`MerchantProductsView`/`AdminSettlementsView`/`MerchantSettlementsView` **只能增量扩展**，不得删除既有 role 快捷登录、既有列与既有请求 |
| **路由兼容** | 新增动态路由不得破坏既有 `/admin/overview`、`/admin/settlements`、`/merchant/orders`、`/merchant/products`、`/merchant/settlements` 路径；`router/home.ts` 的 `homeForRole` 逻辑保留 |
| **登录双模式** | `LoginView` 保留 role+id 快捷登录（local profile 用），新增「账号密码」tab；两者都走 `POST /api/v1/auth/login`（后端双模式） |
| **动态菜单** | `GET /auth/menus` 拿不到时（后端未完成）**降级**为读取 `router/home.ts` 的静态菜单，不白屏 |
| **金额** | 一律 `MoneyText` 组件渲染（分→元，2 位）；表单输入元、提交转分；**禁止**在页面里手写 `/100` |
| **权限** | 按钮级用 `v-perm`；**不得**只靠隐藏按钮做安全（后端必须校验，前端只做体验） |
| **分页** | 统一 `pageNum/pageSize`，`pageSize` 选项 `10/20/50`（上限 100） |
| **错误处理** | `http.ts` 统一把 `code≠0` 转 `ElMessage.error(message)`；`10003` 跳登录；`10004` 提示无权限 |
| **幂等** | 提交类按钮用 `utils/idempotency.ts` 生成 key，防重复点击 |
| **大数据表格** | 列表 >200 行必须分页，不用前端虚拟滚动（P0） |
| **ECharts** | 只在报表页按需 `import`；不在首屏引入，保证首屏 ≤1.5s |
| **图表空数据** | 无数据必须显示空态而非报错 |
| **响应式** | 运营后台最小 1366×768；商家中心最小 1024×768 |
| **主题** | 运营后台橙色、商家中心深青绿（沿用既有 `styles/index.css` 变量，不新建主题体系） |
| **构建** | `cd solo-eats-web && npm run build`（含 `vue-tsc --noEmit`）必须通过；**商家中心工程** `cd solo-eats-web-merchant && npm run build` 同样必须通过；**新增页面必须有 TS 类型**，不得 `any` 泛滥 |

---

## 验收标准

```bash
cd solo-eats-web && npm ci && npm run build
# 断言：vue-tsc 无 error；dist 生成成功
cd solo-eats-web-merchant && npm ci && npm run build
# 断言：商家中心独立工程 vue-tsc 无 error；dist 生成成功（独立工程验收，13 章「独立工程」）
cd solo-eats-web && npm run test:unit
# 断言：既有 money.spec.ts / datetime.spec.ts / debounce.spec.ts / orderStatus.spec.ts 全绿

# 批次1 手工验收
# 1) 用 admin/Admin@123 登录 → 侧边栏出现"系统管理"及 7 个子菜单
# 2) 用户列表分页/搜索/新建用户 → 新用户可登录
# 3) 角色页勾选菜单保存 → 该角色用户登录后侧边栏变化
# 4) 操作日志页能按模块/时间筛选，参数列显示脱敏后的 ***
# 5) 既有"商家中心 → 订单"页面仍可正常打开（回归，不白屏）

# 批次2 手工验收
# 6) 商家待审列表 → 点审核 → 弹窗填抽佣率 → 通过 → 列表状态变"已通过"
# 7) 门店页地图选点保存经纬度 → 刷新后仍在
# 8) 商品库新建 SPU 上传主图 → 门店商品页可上架

# 批次4 手工验收
# 9) 报表工作台五卡与后端 /report/dashboard 数值一致
# 10) 漏斗图 5 级且转化率正确
# 11) 提现审核：审核→打款→到账 三步按钮按状态启用/禁用
```

---

## 禁止事项

1. 不得重写 `LoginView.vue` 的既有 role 快捷登录区块（`docs/全栈启动与体验指南.md` 依赖它）。
2. 不得删除或修改既有的 `constants/*.ts`、`utils/*.ts` 导出（有单测覆盖）。
3. 不得删除既有 `*.spec.ts` 或修改其断言。
4. 不得引入第二套组件库（只能 Element Plus + 自研 `components/`）。
5. 不得在首屏引入 ECharts。
6. 不得在页面里手写金额换算（统一 `money.ts`）。
7. 不得把 token 存进 `localStorage` 以外的位置（沿用 `authStorage`）。
8. 不得用 `v-perm` 代替后端权限校验。
9. 不得改动后端任何代码（本卡纯前端）。
10. 不得在菜单接口未就绪时直接白屏（必须降级静态菜单）。

---

## 执行记录

| 日期 | 批次 | 新增/修改文件 | 构建结果 | 遗留问题 |
|---|---|---|---|---|
| 2026-10-27 | 批次0（P0 先行） | 78 个（新增 70 / 修改 8 / 删除 0）：运营后台 6 页 + 5 个 API 模块 + `businessStatus.ts`；`AdminOverviewView` 增量加 7 张今日经营卡；路由/侧边栏/角色族匹配；`shared/` 共享层；`solo-eats-web-merchant/` 独立工程（含复制 3 个商家视图）。 | `solo-eats-web`: `npm run build` 通过（`vue-tsc --noEmit` 0 error，Vite 1737 modules，14.31s）；`npm run test:unit` 4 files / 10 tests 全通过；`solo-eats-web-merchant`: `npm run build` 通过（`vue-tsc --noEmit` 0 error，Vite 3323 modules，23.25s）。 | 1) 菜单种子仍为 `/store/list` 等旧路径，本轮按任务卡挂 `/merchant/apply`、`/store/shop`、`/product/spu`、`/marketing/coupon`，侧边栏为静态补充，批次1 接 API 菜单时需统一；2) 无 `GET /admin/shops/{id}`，编辑回显改用公开 `GET /c/shops/{id}`，`ShopDetailView` 无 `sortWeight` 且后端 update 忽略该字段，编辑页排序权重已禁用；3) `SaveShopRequest` 不接收营业时段，门店表单未提供营业时段与自取配置编辑；4) `MerchantView` 无 `categoryName`，入驻列表品类筛选只能填 `categoryId`；`ShopView`/`SpuView` 无商家名，门店与商品库只显示 `merchantId`；5) SPU 仅列表，品类名由品类树客户端映射，树加载失败降级 `品类 {id}`，无新建/编辑/上下架；6) 发放记录只有 `userId`，无法显示昵称/手机号，`GET /coupons/{id}` 未单独调用；7) 管理端 3 个商家视图原文件保留，`solo-eats-web` 尚未改为从 `shared/` 再导出，存在两份实现；8) 后端不读 `X-Shop-Id`，多门店切换仅对令牌无 `shopId` 的账号生效；9) 无 `v-perm` 指令，页面仅靠路由 `meta.roles` + 后端校验，未做按钮级权限；10) `solo-eats-web-merchant/src/pages.json` 为声明式快照，运行时以 `router/index.ts` 为准。安装依赖时本机 safe-delete 中断过，已从官方 tarball 补齐 `@element-plus/icons-vue` 的 `dist/types/index.d.ts` 与被截断的 rollup 原生二进制，未改 `package-lock.json`。 |
| 2026-10-27 | 批次3（订单中心，前后端联合） | 22 个代码文件（新增 18 / 修改 4）：后端新增 `AdminOrderReadService` + Impl、`AdminRefundController`、7 个 view record、2 个 query record，扩展 `AdminOrderController` 两个只读接口，`ErrorCode` 追加 `REFUND_NOT_FOUND(30008)`；前端新增 `api/order-admin.ts`、`constants/orderAdmin.ts`、`views/order/{index,detail,exception}.vue`、`views/refund/index.vue`，修改 `router/index.ts`（4 条 ADMIN 静态路由）与 `constants/fallbackMenu.ts`（3 条兜底入口）。 | 后端 `mvn -q -pl solo-eats-order -am test` 通过；`mvn -q -DskipTests package` 通过；前端 `cd solo-eats-web && npm run build` 通过（`vue-tsc --noEmit` 0 error，Vite 1922 modules，25.87s）。 | 1) 未加 `@RequirePermission`，因此未新增 V35 菜单种子；`t_sys_menu` 仅有 id=4「订单中心」目录、无子菜单，`/auth/menus` 驱动侧边栏时看不到这 4 页，只能通过静态路由直达或菜单接口降级后的兜底入口进入；2) 售后页只读，审核/仲裁动作未开放（任务卡明确本卡不做）；3) 异常池读 `t_order.exception_type`，未联查 `t_order_exception` 的处理状态与描述；4) 售后关键字用 `EXISTS` 子查询同时匹配退款单号与订单号，订单量大时依赖 `t_refund_order.idx_status_time` 与 `t_order.uk_order_no`；5) 新增只读服务未补单测，现有 `solo-eats-order` 测试全量通过。 |
| 2026-10-27 | 批次2·3 收尾（运营后台剩余 5 页，纯前端） | 10 个（新增 5 / 修改 5）：新增 `views/merchant/list.vue`、`views/store/delivery-area.vue`、`views/product/spu-form.vue`、`views/delivery/dispatch.vue`、`views/delivery/rider-audit.vue`；修改 `api/store.ts`（配送区域 DTO + GET/PUT）、`api/product.ts`（`AdminSpuSavePayload` + POST/PUT）、`router/index.ts`（6 条 ADMIN 静态路由）、`constants/fallbackMenu.ts`（5 条兜底入口）、`views/admin/product/SpuListView.vue`（仅加“新建商品”按钮与行内“编辑”，任务卡明确要求）。未改任何后端 Java/SQL。 | `vue-tsc --noEmit` 0 error；`vite build --emptyOutDir false` 通过（1937 modules，24.99s，仅既有 >500kB chunk 警告）。 | 1) `MerchantView.contactPhone` 后端返回明文，列表按前 3 + `****` + 后 4 展示，需后端补掩码；2) 管理端无 `GET /admin/spus/{id}`，编辑页以 `?name=` 调 `listSpus(keyword)` 回查，同名超过 100 条或改名后可能命中失败；3) `RiderView` 不含认证提交时间，列表列名为“注册时间”，提交时间在详情认证记录中展示；4) 门店未配置围栏时 `GET delivery-area` 返回 10002，`http.ts` 拦截器会先弹全局错误提示，页面随后按未配置处理；5) 派单中心直接复用 `views/delivery/config/index.vue` 与 `views/delivery/order/index.vue`，上下堆叠，未复制逻辑；6) 未加菜单种子，5 页仅靠静态路由与兜底菜单进入，与批次3口径一致。 |
| 2026-10-27 | 商家中心补页（纯前端 6 页） | 13 个（新增 6 / 修改 7）：新增 `solo-eats-web-merchant/src/views/{PickupVerifyView,OrderReceiptView,ShopSettingView,PickupSettingView,ShopDecorationView,ShopAccountView}.vue`；修改 `router/index.ts`（6 条路由）、`router/home.ts`（登录回跳白名单 +`/pickup`、`/shop`）、`layouts/MerchantLayout.vue`（5 条菜单 + `/orders/*` 高亮归并）、`views/MerchantOrdersView.vue`（仅行内加“小票”链接）、`../shared/api/{types,shop,orders}.ts`（补 `ShopAccountView`/`MerchantOrderDetailView` 等类型与 12 个商家接口封装）。未改后端 Java/SQL。 | `vue-tsc --noEmit` 0 error；`vite build --emptyOutDir false` 通过（3341 modules，24.39s，仅既有 >500kB chunk 警告）。 | 1) 后端无 `GET /merchant/shops/{id}`，门店设置与自取设置详情复用公开 `GET /c/shops/{id}`；2) 装修接口只校验 JSON 对象与序列化 ≤20000 字符，无字段 schema，页面只提供 JSON 编辑器；3) 小票路径含 `orderId`，无法做侧边栏固定入口，入口为订单列表行内“小票”按钮；4) 门店账号接口返回 List 无分页，页面不分页；5) `MerchantOrdersView` 仅新增“小票”链接，接单/拒单/派单逻辑未动。 |
