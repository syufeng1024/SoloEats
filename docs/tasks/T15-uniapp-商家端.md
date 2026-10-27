# T15 商家端 UniApp（9 页，P0 先做 7 页）

> 优先级：**P0**（阶段A 7 页）/ **P1**（阶段B 其余 3 页）
> 所属：新建 `solo-eats-uniapp-merchant/`（本仓库子目录）
> 分两段：**阶段A（工作台→接单/拒单→出餐→自取核销→商品/门店设置）→ 阶段B（财务/评价/营销/消息）**

---

## 目标

**一句话**：让店长在手机上完成「开门营业 → 接单 → 出餐 → 自取核销」的日常闭环，并看到今天的营业额。

**完成后系统能做什么**：登录后看到今日营业额/订单量/待接单数；新订单语音播报，可接单（可开自动接单）或拒单（填原因）；可标记出餐；自取订单可扫码或手动输码核销；可切营业状态、上下架商品、估清、改库存。

---

## 规格书依据

| 章节 | 原文要点 |
|---|---|
| 1.7 交付总览 | 「商家端 App：UniApp，约 **9 页**，经营工作台、接单拒单、出餐、小票预览、自取核销、数据概览」 |
| 7.1 功能明细 | 「登录（账号密码/验证码，**多门店切换**）；首页工作台（今日营业额、订单量、待接单数、营业状态开关）；接单（语音播报、接单/拒单含原因、自动接单开关）；订单管理（待接单/待出餐/待取货/已完成/退款 Tab、详情、**小票预览**、呼叫骑手）；出餐管理（标记出餐、超时提醒）；自取订单（标识与筛选、备餐完成通知、**取餐码核销（扫码/手动输码）**、超时未取处理）；商品管理（上下架、估清、改价、库存）；门店管理（营业时间、营业状态、配送范围、起送价、配送费）；营销；财务；评价管理；消息」 |
| 7.2 原型 | 深青绿头部主题；「新订单强提醒卡（含"到店自取"标签、商品摘要与备注、拒单/接单并开始备餐）」 |
| 7.1 小票 | 【替换】云打印 → **小票预览 + 模拟打印（生成图片/PDF 可下载）** |

---

## 前置依赖

| 依赖 | 具体依赖物 |
|---|---|
| **T01** | `POST /auth/login`（商家账号，token 带 `merchantId` + `shopId`） |
| **T04** | `GET/PUT /merchant/shops/{id}`、`PUT /merchant/shops/{id}/status`、`GET /merchant/shops`（多门店切换） |
| **T05** | `GET /merchant/shop-spus`、`PUT /merchant/shop-spus/{id}`、`PUT /merchant/shop-spus/{id}/status`、`POST/DELETE /merchant/sold-out`、`PUT /merchant/skus/{skuId}/stock`（既有 `MerchantSkuController`） |
| **T08** | `WS /ws`（新单强提醒） |
| 既有后端 | `GET /api/v1/merchant/orders`（`MerchantOrderController` 仅列表）；接单/拒单/出餐/呼叫骑手在 `OrderController`：`POST /api/v1/orders/{orderId}/accept`、`/reject`、`/prepared`（注意：是 `prepared` 不是 `ready`）、`POST /api/v1/orders/{orderId}/merchant/call-rider` |
| **自取核销（待补）** | `POST /api/v1/merchant/orders/{id}/pickup/verify` **当前后端不存在**，且全任务板无卡承接。须回写 T04 或 order 模块补充（含幂等键，见规格书 5.3.8）；本卡接口 #10 依赖此补项 |

---

## 数据模型（前端本地）

| Key | 内容 |
|---|---|
| `token` | 商家 JWT |
| `shopId` | 当前门店（多门店切换，请求头 `X-Shop-Id`，见 README Q4） |
| `autoAccept` | 自动接单开关（本地镜像，服务端配置为准） |
| `voiceEnabled` | 语音播报开关 |

---

## 接口清单

| # | 方法 | 路径 | 用途 |
|---|---|---|---|
| 1 | POST | `/api/v1/auth/login` | 商家登录 |
| 2 | GET | `/api/v1/merchant/shops` | 多门店列表（切换） |
| 3 | PUT | `/api/v1/merchant/shops/{id}/status` | 营业状态开关 |
| 4 | GET | `/api/v1/merchant/report/dashboard` | 工作台数据（T11，P0 阶段未完成前**只展示今日订单量/待接单数**等可直查指标，**营业额等聚合金额待 T11 就绪后再展示**，不得前端求和） |
| 5 | GET | `/api/v1/merchant/orders?status=` | 订单列表（Tab） |
| 6 | GET | `/api/v1/merchant/orders/{id}` | 订单详情 |
| 7 | POST | `/api/v1/orders/{orderId}/accept` | 接单（`OrderController`，非 `/merchant/orders/...`） |
| 8 | POST | `/api/v1/orders/{orderId}/reject` | 拒单（原因） |
| 9 | POST | `/api/v1/orders/{orderId}/prepared` | 标记出餐/备餐完成（动词为 `prepared`） |
| 10 | POST | `/api/v1/merchant/orders/{id}/pickup/verify` | 自取核销（取餐码）——**待后端补充，见「前置依赖」** |
| 11 | GET | `/api/v1/merchant/shop-spus?status=` | 商品列表 |
| 12 | PUT | `/api/v1/merchant/shop-spus/{id}/status` | 上下架/估清 |
| 13 | POST | `/api/v1/merchant/sold-out` | 批量估清 |
| 14 | PUT | `/api/v1/merchant/skus/{skuId}/stock` | 改库存（既有接口） |
| 15 | POST | `/api/v1/files` | 上传（暂不必须，小票生成用前端 Canvas） |
| 16 | WS | `/ws?token=` | 新单强提醒 |

---

## 需要新增/修改的文件

### 工程骨架（7 个，`base` 改为 `/merchant-app/`）

`solo-eats-uniapp-merchant/{package.json, vite.config.ts, src/{manifest.json, pages.json, App.vue, main.ts, env.d.ts}}`

### 阶段A（6 页 + 6 基础 = 12 个）

| 路径 | 说明 |
|---|---|
| `src/api/http.ts`、`types.ts`、order.ts`、`product.ts`、`shop.ts` | 请求封装（token、`X-Shop-Id`、401 跳登录） |
| `src/stores/auth.ts`、`shop.ts` | 登录态、当前门店、营业状态 |
| `src/utils/voice.ts` | 新单语音播报 |
| `src/utils/receipt.ts` | 小票预览渲染（Canvas → 图片，可保存） |
| `src/pages/login/Login.vue` | 登录 + 门店选择 |
| `src/pages/home/Home.vue` | 工作台（数据卡 + 营业状态开关 + 强提醒卡 + 待处理列表） |
| `src/pages/order/List.vue` | 订单 Tab（待接单/待出餐/待取货/已完成/退款） |
| `src/pages/order/Detail.vue` | 订单详情 + 操作按钮 + 小票预览入口 |
| `src/pages/order/Receipt.vue` | 小票预览（模拟打印：生成图片可下载） |
| `src/pages/pickup/Verify.vue` | 自取核销（扫码 + 手动输码） |
| `src/pages/product/List.vue` | 商品管理（上下架/估清/库存） |
| `src/pages/shop/ShopSetting.vue` | 门店基础设置（营业时间、营业状态、起送价、配送费）——**P0 项（规格书 1.6 商家端 P0 含"门店基础设置"），从阶段B 提入** |

### 阶段B（3 页）

| 路径 | 说明 |
|---|---|
| `src/pages/finance/Finance.vue` | 营业额/抽佣/结算明细（T16 + 既有 `/merchant/settlements`） |
| `src/pages/review/Review.vue` | 评价查看与回复（T05） |
| `src/pages/mine/Mine.vue` | 我的（消息、退出、账号） |

---

## 关键实现约束

| 项 | 约束 |
|---|---|
| **多门店** | 登录后若 `GET /merchant/shops` 返回 >1 家，必须弹选择；选定后所有请求带 `X-Shop-Id`；**不得**在 URL 里传 `shopId` 作为权限来源（后端以 token + header 校验） |
| **接单/拒单** | 接单必须幂等（重复点不报错，返回当前状态）；拒单原因必填且从服务端字典取（`order_reject_reason`） |
| **自动接单** | 开关只控制前端是否弹强提醒；真正的自动接单由后端订单规则配置决定（4.4「自动接单开关」）。前端**不得**自行 auto-accept |
| **出餐** | 仅「备餐中」可标记出餐；已出餐再点返回错误提示而非静默 |
| **自取核销** | 扫码用 `uni.scanCode`；H5 无扫码能力时降级为手动输码；核销成功必须展示订单号 + 商品摘要 + 时间，防误核销 |
| **小票** | 纯前端 Canvas 渲染（订单号、门店、商品、金额、备注、二维码）；**不做**真实打印；可「保存图片」 |
| **估清** | 估清与库存分离：估清=按钮开关（`sold-out` 接口），库存=数字输入（`/skus/{skuId}/stock`）；两者都改时先库存后估清 |
| **金额** | 展示转元，**不得**前端累加作为营业额（以接口为准） |
| **强提醒** | WS 收到新单 → 弹卡 + 播报 + 震动；30s 无操作不自动拒单（由后端超时规则处理），前端只提示 |
| **超时提醒** | 待接单超过 `merchant-accept-timeout-minutes`（默认 10）卡片标红 |
| **权限** | 店员（`MERCHANT_CLERK`）不可见「改价」「财务」，仅店长可见（按 `roles` 判断） |

---

## 验收标准

```bash
cd solo-eats-uniapp-merchant && npm ci && npm run build:h5

# 链路
# 1) 商家登录（merchant1）→ 单门店直接进工作台
# 2) 工作台显示今日营业额/订单量/待接单数（与后端一致）
# 3) 点营业状态开关 → 变"休息" → 用户端该店不可下单
# 4) 用户下单 → WS 强提醒卡出现 + 语音播报（含"到店自取"标签）
# 5) 点"接单并开始备餐" → 订单进"待出餐"
# 6) 点"出餐" → 订单进"待取货"
# 7) 自取单：进核销页 → 手动输入 6 位取餐码 → 核销成功 → 订单"已完成"
# 8) 商品页：把某商品估清 → 用户端菜单显示"已售完"
# 9) 点"小票预览" → 生成图片可保存
# 10) 拒单：不填原因不能提交；提交后订单进"已取消"且用户端可见原因
```

---

## 禁止事项

1. 不得为前端新增后端接口（自取核销接口若后端缺失，**先记入 T04/order 补充项**，不在前端绕过）。
2. 不得在 URL/请求体里用 `shopId` 作为权限依据（必须以 `X-Shop-Id` + token 校验）。
3. 不得前端自动接单。
4. 不得展示/请求非本门店数据。
5. 不得用前端求和作为营业额。
6. 不得实现真实打印（只做预览 + 图片导出）。
7. 不得引入第二套 UI 框架；不得引入 Element Plus。
8. 不得在阶段A 实现财务/评价/营销（P1 范围）。
9. 不得改动 `solo-eats-web` 的商家中心页面（Web 与 App 各自独立，但共用后端）。

---

## 执行记录

| 日期 | 会话范围 | 新增文件 | 构建结果 | 遗留问题 |
|---|---|---|---|---|
| 2026-10-25 | 阶段A：8 页 + api/stores/utils 基础设施 | 19 新建 / 6 修改 / 2 删除（`src/api/user.ts`、`pay.ts` 为用户端语义，已删除） | `npm run build:h5` 通过；`pages.json` 注册 8 页 + 3 tab 全部存在（missing=0）；Playwright 拦截商家接口后跑通登录→工作台→订单列表/详情→接单/拒单→小票预览→自取核销→商品改库存/批量估清→门店设置保存 | 1) `GET /merchant/report/dashboard` 不接收 shopId，统计固定按令牌内门店，多门店切换后与列表口径不一致（前端已提示）；2) 后端未实现 `X-Shop-Id` 解析，切换到非令牌绑定门店的读写接口会 10004；3) 扫码核销未做，阶段A 仅手动输码（4~8 位）；4) 本机库缺 `t_merchant` 种子，merchant001 登录后 `GET /merchant/shops` 返回「商家不存在」，真实链路需补种子；5) 阶段B 财务/评价/我的未做 |
| 2026-10-27 | 阶段B：财务/评价/我的 3 页 + `GET /merchant/reviews` | 7 新建 / 6 修改 | `pages.json` 8→11；`npm run build:h5` 通过；`mvn -q -pl solo-eats-app -am compile`、`mvn -q -DskipTests package` 通过；`ProductReview*Test` 通过 | 1) `GET /merchant/settlements` 无 pageNum/pageSize，前端按 10 条切片并明确标注口径，summary 仍取接口聚合值；2) 后端仍未解析 `X-Shop-Id`，评价列表按商家全部门店聚合（service 先 `listByMerchant` 再按 shopId 过滤）；3) 评价列表只返回已审核通过（status=1）评价，未审核评价无法回复故不下发；4) 结算单接口无 statusName，状态中文映射维护在 Finance.vue |
| 2026-10-27 | 消息中心：新增 `pages/message/Message.vue`（用户会话 + 平台通知双 Tab）与 `pages/message/Chat.vue`（文字收发、10s 轮询、已读回写），补 `api/message.ts`；`Mine.vue` 加「消息中心」入口 | 新增 3 / 修改 2（`pages.json`、`Mine.vue`） | `pages.json` 11→13；`npm run build:h5` 通过，产出 `pages-message-Message` / `pages-message-Chat` chunk | ① 后端无 `GET /messages/{id}`，通知详情用列表数据就地展开，展开即调 `PUT /messages/{id}/read`；② IM 无 WS 通道，会话页用 10s 轮询（silent）；③ 会话接口不下发对端昵称/头像，商家侧按「用户 #userId」展示，不伪造昵称 |
