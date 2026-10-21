# solo-eats-web

商家中心与平台运营后台前端。接口直接对准 `solo-eats-app` 现有控制器，不经过自建 BFF。

## 技术栈

| 项 | 版本 / 说明 |
|---|---|
| Vue | 3.5，`<script setup lang="ts">` |
| 构建 | Vite 6 + TypeScript 5.7 |
| UI | Element Plus 2.9，中文 locale |
| 状态 | Pinia 2 |
| 路由 | Vue Router 4，`createWebHistory` |
| 请求 | Axios 1，统一信封解包 + 401 处理 |
| 测试 | Vitest（金额换算、防抖、状态映射） |

## 启动

前置：Node.js ≥ 20，后端已在 `http://localhost:8080` 启动。

```bash
cd solo-eats-web
npm install
npm run dev
```

浏览器打开 `http://localhost:5173`。

后端启动方式（项目根目录）：

```bash
docker compose up -d
mvn -pl solo-eats-app -am spring-boot:run
```

`GET /healthz` 返回 `{"status":"UP"}` 即进程存活；`GET /readyz` 返回 200 才代表 MySQL / Redis / RabbitMQ 全部就绪。

## 开发代理与 CORS

后端 `WebMvcConfig` 只注册了角色拦截器，没有 `CorsConfiguration`。前端开发服务器通过 Vite 代理转发，浏览器只访问同源地址：

```
浏览器 → http://localhost:5173/api/v1/... → Vite proxy → http://localhost:8080/api/v1/...
```

代理配置在 [vite.config.ts](vite.config.ts)，目标地址可用环境变量 `VITE_PROXY_TARGET` 覆盖。不要直接把 `VITE_API_BASE_URL` 改成 `http://localhost:8080/api`，否则浏览器会因缺少 CORS 响应头拦截请求。

部署时把 `dist/` 交给 Nginx，并在同一 server 块内把 `/api/` 反代到后端，保持同源。路由使用 `createWebHistory`，Nginx 必须配置 `try_files $uri $uri/ /index.html;`，否则刷新 `/merchant/orders` 会返回 404。

## 环境变量

复制 `.env.example` 为 `.env.local`：

| 变量 | 默认 | 作用 |
|---|---|---|
| `VITE_API_BASE_URL` | `/api` | Axios `baseURL`，默认走同源代理 |
| `VITE_PROXY_TARGET` | `http://localhost:8080` | 仅 Vite 开发服务器读取 |

## 登录

`POST /api/v1/auth/login` 由 `AuthController` 提供，类上有 `@Profile("local")`，只在 `SPRING_PROFILES_ACTIVE=local` 时生效。请求体：

```json
{ "role": "MERCHANT", "merchantId": 1, "shopId": 1 }
```

| 角色 | 必填 | 说明 |
|---|---|---|
| `MERCHANT` | `merchantId` | `shopId` 可选；JWT 会携带 `merchantId`、`shopId` |
| `ADMIN` | 无 | `userId` 可选，后端缺省按 0 签发 |

登录成功后 Token 与角色写入 `localStorage` 的 `solo-eats.auth`，Axios 请求拦截器附加 `Authorization: Bearer <token>`。前端按 `expiresIn` 记录过期时间，提前 30 秒判定失效；收到 HTTP 401 或业务码 `10003` 时清空登录态并跳转登录页。

## 已实现页面

| 路径 | 角色 | 数据来源 |
|---|---|---|
| `/login` | 公开 | `POST /api/v1/auth/login` |
| `/merchant/orders` | MERCHANT | `GET /api/v1/merchant/orders`，以及接单、备餐完成、拒单、取消、呼叫骑手 |
| `/merchant/products` | MERCHANT | `GET/POST /api/v1/merchant/skus`、`PUT /api/v1/merchant/skus/{skuId}`、`PUT /api/v1/merchant/skus/{skuId}/stock` |
| `/merchant/settlements` | MERCHANT | `GET /api/v1/merchant/settlements`、`GET /api/v1/merchant/settlements/{billId}` |
| `/admin/overview` | ADMIN | `GET /api/v1/admin/orders/monitor` |
| `/admin/settlements` | ADMIN | `GET /api/v1/admin/settlements/stats`、`POST /api/v1/admin/settlements/generate` |

## 后端接口

### 商家订单

| 方法 | 路径 | 说明 |
|---|---|---|
| `GET` | `/api/v1/merchant/orders?shopId=&status=&keyword=&pageNum=&pageSize=` | merchantId 从 JWT 读取；status 可筛选 10/20/30/31/40/60/70；keyword 匹配订单号、收货人、手机号 |
| `POST` | `/api/v1/orders/{orderId}/accept` | 接单，`WAIT_ACCEPT(10) → PREPARING(20)` |
| `POST` | `/api/v1/orders/{orderId}/prepared` | 备餐完成，`PREPARING(20) → WAIT_RIDER_PICKUP(30)` 或 `WAIT_USER_PICKUP(31)` |
| `POST` | `/api/v1/orders/{orderId}/merchant/call-rider` | 商家呼叫骑手，推进配送单派单窗口 |
| `POST` | `/api/v1/orders/{orderId}/reject` | 拒单，需 `Idempotency-Key` |
| `POST` | `/api/v1/orders/{orderId}/cancel` | 商家取消，`operatorType=2`，需 `Idempotency-Key` |

`POST /api/v1/orders/{orderId}/rider/accept` 保持原有语义：仅 `RIDER` 角色、请求体必填 `riderId`，用于骑手接单。商家呼叫骑手不复用该路径，避免把商家令牌写进骑手接单链路。

### 商品与库存

| 方法 | 路径 | 说明 |
|---|---|---|
| `GET` | `/api/v1/merchant/skus?shopId=&status=&keyword=&pageNum=&pageSize=` | 返回 SKU 与库存合并视图；门店来自 JWT，传 shopId 必须与 JWT 一致 |
| `POST` | `/api/v1/merchant/skus` | 创建 SKU 并写入初始库存 |
| `PUT` | `/api/v1/merchant/skus/{skuId}` | 修改名称、规格 JSON、图片、价格、上下架状态 |
| `PUT` | `/api/v1/merchant/skus/{skuId}/stock` | 覆盖 `available_qty`、`sold_out`；锁定与已售库存由订单链路维护 |

价格单位为分，前端表单按元输入并 `Math.round(元 × 100)` 转分。`specJson` 由后端校验是否为合法 JSON。

### 平台运营

| 方法 | 路径 | 说明 |
|---|---|---|
| `GET` | `/api/v1/admin/orders/monitor?merchantId=&shopId=&status=&keyword=&dateFrom=&dateTo=&pageNum=&pageSize=` | 订单分页 + 同筛选条件下的状态计数、实付、退款汇总；收件手机号脱敏 |
| `GET` | `/api/v1/admin/settlements/stats?merchantId=&status=&dateFrom=&dateTo=&pageNum=&pageSize=` | 跨商家账单分页 + 佣金与应结合计 |
| `POST` | `/api/v1/admin/settlements/generate` | 归集当日可结算订单并生成账单 |

所有分页接口 `pageSize` 上限 100；keyword 按字面量匹配，后端会剔除 `%`、`_`、`\`，避免改变 LIKE 语义。

## 构建与校验

```bash
npm run typecheck   # vue-tsc --noEmit
npm run test:unit   # vitest run
npm run build       # typecheck + vite build，产物在 dist/
npm run preview     # 本地预览 dist/
```

## 目录

```
src/
├─ api/            Axios 实例、接口模块、后端类型
├─ constants/      角色、订单状态、结算状态
├─ layouts/        AppLayout（侧栏 + 顶栏）
├─ router/         路由表、登录守卫、角色首页
├─ stores/         Pinia auth
├─ styles/         全局样式与 Element Plus 主题变量
├─ utils/          金额换算、时间格式化、防抖、幂等键、登录态存储
└─ views/          login / merchant / admin
```

## 约定

- 金额字段一律是后端 `bigint` 分，只有 `src/utils/money.ts` 做分转元。
- 后端 `LocalDateTime` 按字面量格式化为 `yyyy-MM-dd HH:mm:ss`，不经浏览器时区换算。
- 订单、商品、管理看板的检索与分页由后端完成；结算页列表接口不分页，账单号过滤在前端按 300ms 防抖完成。
- 取消与拒单必须带 `Idempotency-Key`，由 `src/utils/idempotency.ts` 生成。
- 不提交 `.env.local`，不在代码里写死后端地址与密钥。
