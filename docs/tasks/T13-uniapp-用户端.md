# T13 用户端 UniApp（49 页，P0 先做 12 页）

> 优先级：**P0**（阶段A 12 页）/ **P1**（阶段B 其余 37 页）
> 所属：新建 `solo-eats-uniapp-user/`（本仓库子目录，见 README Q5）
> 分两段：**阶段A（首页→店铺→购物车→下单→支付→订单/取餐码）→ 阶段B（搜索/消息/会员/评价/售后）**

---

## 目标

**一句话**：让消费者能在 H5 里完成「打开 App → 找到店 → 点餐 → 下单支付 → 看到骑手/取餐码 → 评价」这一整条链路。

**完成后系统能做什么**：H5 打开即是附近好店首页；进店可加购并选规格加料、看到估清；下单可切「配送/自取」，自取显示取餐码；支付走 Mock 网关；订单列表/详情有完整时间轴；能催单、取消、申请退款、评价。

---

## 规格书依据

| 章节 | 原文要点 |
|---|---|
| 1.7 交付总览 | 「用户端 App/H5：UniApp + H5，约 **49 页**，首页与附近、店铺点餐、双履约下单与支付、订单与配送追踪、取餐码、消息 IM、会员中心、评价」 |
| 5.1 信息架构 | 底部 Tab（首页/附近/订单/消息/我的） |
| 5.2.1~5.2.11 | 启动与账号、首页、搜索、店铺详情、购物车与结算、订单与配送、消息中心、评价体系、我的、营销互动、会员体系 |
| 5.4 设计规范 | 品牌橙金渐变、方正小圆角、紧凑黑体、细描边线性图标 |
| 1.6 P0 | 「用户端 P0：登录、首页、店铺、购物车、下单、支付宝沙箱支付、订单列表/详情、自取取餐码、评价」 |
| 14.1 页面清单（用户端） | 49 页明细 |

---

## 前置依赖

| 依赖 | 具体依赖物 |
|---|---|
| **T06** | `POST /c/auth/sms-code`、`POST /c/auth/login`、`/c/users/me`、`/c/addresses`、`/c/wallet` |
| **T05** | `GET /c/shops/{shopId}/menu`、`GET /c/spus/{spuId}`、`POST /c/reviews` |
| **T04** | `GET /c/shops`、`GET /c/shops/{id}`、`POST /c/shops/{id}/delivery-check` |
| **T10** | `GET /c/home?pageCode=USER_HOME`（首页楼层；未完成前可用静态兜底） |
| **T08** | `WS /ws`、`/messages`（消息中心；未完成前用轮询） |
| 既有后端 | `POST /api/v1/orders`、`GET /api/v1/orders`、`GET /api/v1/orders/{id}`、`POST /api/v1/orders/{id}/cancel`、`POST /api/v1/payments/{orderNo}/pay`、`POST /api/v1/payments/callback`（Mock） |

---

## 数据模型（前端本地存储）

| Key | 内容 | 说明 |
|---|---|---|
| `token` / `tokenExpireAt` | JWT | 复用 `solo-eats-web` 的 `authStorage` 逻辑风格 |
| `cart:{shopId}` | 购物车（skuId, qty, addons[], specJson, price） | **纯前端**，下单时快照提交 |
| `address:last` | 上次收货地址 id | |
| `location` | `{lng, lat, city}` | 定位失败降级为手动选城市（单城市本期写死"北京"） |

> 购物车不落后端（规格书未要求服务端购物车）；本地存储即可，避免新增表。

---

## 接口清单（前端调用，均为后端已有/他卡交付）

| # | 方法 | 路径 | 用途 |
|---|---|---|---|
| 1 | POST | `/api/v1/c/auth/sms-code` | 发验证码 |
| 2 | POST | `/api/v1/c/auth/login` | 登录（自动注册） |
| 3 | GET | `/api/v1/c/home?pageCode=USER_HOME` | 首页楼层 |
| 4 | GET | `/api/v1/c/shops?lng=&lat=&radius=&sortBy=` | 附近好店 |
| 5 | GET | `/api/v1/c/shops/{id}` | 店铺详情 |
| 6 | GET | `/api/v1/c/shops/{shopId}/menu` | 店铺菜单 |
| 7 | GET | `/api/v1/c/spus/{spuId}` | 商品详情（规格/加料） |
| 8 | POST | `/api/v1/c/shops/{id}/delivery-check` | 配送范围/起送价校验 |
| 9 | POST | `/api/v1/c/freight/estimate` | 配送费试算 |
| 10 | POST | `/api/v1/orders` | 下单（带 `Idempotency-Key`） |
| 11 | POST | `/api/v1/payments/{orderNo}/pay` | 发起支付 |
| 12 | GET | `/api/v1/orders?status=` | 订单列表（Tab 筛选） |
| 13 | GET | `/api/v1/orders/{id}` | 订单详情 + 时间轴 |
| 14 | POST | `/api/v1/orders/{id}/cancel` | 取消订单 |
| 15 | GET | `/api/v1/c/deliveries/{deliveryId}/track` | 配送追踪 |
| 16 | POST | `/api/v1/c/reviews` | 评价 |
| 17 | GET | `/api/v1/messages`、`/messages/unread-count` | 消息中心 |
| 18 | GET | `/api/v1/c/addresses` | 地址簿 |
| 19 | GET | `/api/v1/c/wallet` | 钱包 |
| 20 | WS | `/ws?token=` | 订单节点实时推送 |

---

## 需要新增/修改的文件

### 工程骨架（阶段A 前置，7 个）

| 路径 |
|---|
| `solo-eats-uniapp-user/package.json`（uni-app 3 + vue3 + pinia） |
| `solo-eats-uniapp-user/vite.config.ts`（H5 base `/`，dev proxy → `http://localhost:8080`） |
| `solo-eats-uniapp-user/src/manifest.json`、`pages.json`、`App.vue`、`main.ts`、`uni.scss` |
| `solo-eats-uniapp-user/src/env.d.ts` |

### 阶段A 页面（12 页 + 6 基础文件 = 18 个）

| 路径 | 说明 |
|---|---|
| `src/api/http.ts` | 请求封装（注入 token、统一解包 `ApiResponse`、401 跳登录、`X-Idempotency-Key` 生成） |
| `src/api/types.ts` | 与后端 DTO 对齐的 TS 类型（`ShopVO`/`ShopMenuVO`/`OrderVO`/`OrderItemVO`/`AddressVO`） |
| `src/api/user.ts`、`shop.ts`、`order.ts`、`pay.ts` | 按域拆分的 API |
| `src/stores/auth.ts`、`cart.ts` | Pinia：登录态、购物车 |
| `src/utils/money.ts` | `fenToYuan(yuan)` / `yuanToFen()`（**展示转元、提交传分**） |
| `src/utils/request.ts` | 重试与超时 |
| `src/pages/login/Login.vue` | 手机号 + 验证码登录 |
| `src/pages/index/Index.vue` | 首页（Banner/金刚区/附近好店/猜你喜欢） |
| `src/pages/shop/Shop.vue` | 店铺详情（左分类右商品、加购、估清置灰） |
| `src/pages/shop/SkuPopup.vue` | 规格/加料弹窗 |
| `src/pages/cart/Cart.vue` | 购物车（增减、清空、金额明细） |
| `src/pages/order/Confirm.vue` | 确认订单（配送/自取切换、地址/自取手机号、优惠、费用明细） |
| `src/pages/order/PayResult.vue` | 支付结果（成功→订单详情/取餐码；失败→重试） |
| `src/pages/order/List.vue` | 订单列表（全部/待付款/待接单/进行中/待评价/售后） |
| `src/pages/order/Detail.vue` | 订单详情 + 时间轴（配送/自取两套） |
| `src/pages/order/PickupCode.vue` | 取餐码（大字 + 二维码 + 门店导航 + 我已取餐） |
| `src/pages/order/Track.vue` | 配送追踪（地图 + 骑手卡片 + 预计送达） |

### 阶段B 页面（37 页，按模块分组，每批 6~8 页）

`search/*`（4）、`message/*`（5）、`mine/*`（8）、`address/*`（3）、`review/*`（3）、`afterSale/*`（4）、`member/*`（5）、`marketing/*`（5）

---

## 关键实现约束

| 项 | 约束 |
|---|---|
| **金额** | 前端只做**展示**转换（`fen/100`，保留 2 位）；**下单提交一律传分（整数）**；前端计算金额只用于展示，最终以服务端返回为准（9.7） |
| **幂等** | 下单/支付前生成 `Idempotency-Key` 并**落本地**（失败重试复用同一 key），成功后清除 |
| **登录态** | token 过期（401/`10003`）统一跳登录并保留 `redirect`；**不做**静默续期（后端未提供 refresh token） |
| **自取/配送切换** | 切换后**必须**重新调用 `/delivery-check` 与 `/freight/estimate`；自取隐藏地址块、显示门店信息与自取人手机号，`deliveryFee` 强制显示 0 |
| **估清** | `soldOut=true` 的 SKU 置灰不可加购，按钮文案「已售完」；已在购物车的估清商品在下单前二次校验并移除 |
| **起送价** | 未达起送价时「去结算」置灰并提示差额（金额来自 `/delivery-check`） |
| **订单 Tab 口径** | 严格按 5.2.6 注释：配送单「进行中」= 备餐中/待取货/配送中/已送达；自取单 = 备餐中/待取餐；**自取单无「待收货」** |
| **WS 降级** | WS 连不上时自动降级为 15s 轮询订单详情；**不得**因 WS 失败阻塞页面 |
| **状态刷新** | 订单详情页在前台每 10s 拉一次（退款/骑手位置变化）；退到后台停止 |
| **定位** | 首次进首页申请定位；拒绝则弹城市选择（本期单城市，选中即写死"北京"），**不阻断**浏览 |
| **隐私** | 手机号展示必须脱敏；评价/头像上传走 `POST /api/v1/files`（T03） |
| **H5 兼容** | 微信内置浏览器 / Safari / Chrome；不使用仅 App 支持的 API（`plus.*` 需条件编译） |
| **首屏** | ≤1.5s（4G）：首页接口必须一次请求拿全楼层（T10 `/c/home`），**禁止** N 个接口串行 |

---

## 验收标准

```bash
cd solo-eats-uniapp-user && npm ci && npm run build:h5
# 断言：构建成功，dist/build/h5/index.html 存在

# 手动链路（H5 dev server + 后端 8080）
# 1) 登录：手机号 13800000002 + 000000 → 进入首页
# 2) 首页：显示 Banner + 金刚区 + 至少 1 家门店卡片（含起送价/配送费/距离）
# 3) 进店：左分类点击右侧滚动联动；估清商品显示"已售完"且不可加购
# 4) 加购：打开规格弹窗选"大杯/少糖" + 加料，小计正确
# 5) 结算：切"到店自取" → 地址块变门店信息、配送费显示 0、出现自取人手机号
# 6) 切回"外卖配送" → 调 delivery-check，超范围时提示且不能提交
# 7) 下单：提交成功跳支付 → 点"模拟支付成功" → 跳订单详情
# 8) 自取单：订单详情显示取餐码（6 位），可点"我已取餐"
# 9) 订单列表：6 个 Tab 数量与后端一致
# 10) 评价（阶段B）：完成后可提交 5 星 + 标签，重复提交不新增
```

---

## 禁止事项

1. 不得为前端新增后端接口（后端接口以 T04/T05/T06/T08/T10 交付为准）；确需新增必须在任务卡记录并回写后端卡。
2. 不得在前端计算最终金额并直接提交（金额以服务端为准）。
3. 不得把 `fen` 与 `yuan` 混用；不得用浮点累加金额。
4. 不得写死门店/商品/类目 ID（首页数据必须来自接口；仅开发期 mock 可临时写死并标 `TODO`）。
5. 不得引入第二套 UI 框架（uni-ui 或自研组件；**不引入 Element Plus**）。
6. 不得跳过 `Idempotency-Key` 重复下单。
7. 不得在 H5 里使用 `plus.*` API 而不加条件编译。
8. 不得在阶段A 实现会员/积分/秒杀/红包页面（P1/P2 范围）。
9. 不得改动 `solo-eats-web`（那是运营后台+商家中心）。

---

## 执行记录

| 日期 | 会话范围 | 新增文件 | 构建结果 | 遗留问题 |
|---|---|---|---|---|
| _待填_ | | | | |
