# API 安全网关、RBAC 与高并发治理验收

> 关联模块：`solo-eats-common`、`solo-eats-app`
> 测试环境：MySQL 3307、Redis 6379、RabbitMQ 5672
> 本地登录：`POST /api/v1/auth/login`（仅 `local` profile）

## 1. JWT 与角色

### 1.1 角色

| 角色 | 标识 | 绑定字段 |
|---|---|---|
| C 端用户 | `C_USER` | `userId` |
| 商家 | `MERCHANT` | `merchantId`、可选 `shopId` |
| 骑手 | `RIDER` | `riderId` |
| 平台 | `ADMIN` | `userId` |

### 1.2 登录

```powershell
curl.exe -X POST http://localhost:8080/api/v1/auth/login `
  -H "Content-Type: application/json" `
  -d '{"role":"C_USER","userId":1}'
```

响应：

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "token": "eyJ...",
    "tokenType": "Bearer",
    "expiresIn": 7200,
    "roles": ["C_USER"]
  }
}
```

登录接口使用 IP 维度限流，默认 10 次/60 秒。该接口为本地联调用模拟登录，生产应替换为手机号/账号密码或第三方登录。

### 1.3 鉴权链路

```text
TraceIdFilter
  -> JwtAuthFilter
       Authorization: Bearer <token>
       验签、过期校验，写入 AuthContext
  -> RoleAuthInterceptor
       校验 @RequireRole
  -> Controller
       PermissionGuard 做数据级校验
  -> Service
```

- `JwtAuthFilter` 对无效 token 返回 401。
- `RoleAuthInterceptor` 对未登录返回 401，对角色不匹配返回 403。
- `PermissionGuard` 校验订单归属：
  - 用户只能操作自己的订单；
  - 商家只能操作本商家订单；
  - 骑手 ID 必须与 token 一致。
- 支付回调 `/api/v1/payments/callback` 不加角色注解，依靠签名校验。

### 1.4 角色—接口矩阵

| 接口 | 要求角色 |
|---|---|
| `POST /api/v1/orders` | C_USER |
| `POST /api/v1/orders/{id}/accept`、`prepared`、`reject` | MERCHANT |
| `POST /api/v1/orders/{id}/cancel` | C_USER 或 MERCHANT |
| `POST /api/v1/orders/{id}/refund` | C_USER |
| `POST /api/v1/orders/{id}/rider/*` | RIDER |
| `POST /api/v1/payments/pay` | C_USER |
| `GET /api/v1/merchant/settlements**` | MERCHANT |
| `POST /api/v1/admin/settlements/generate` | ADMIN |
| `POST /api/v1/payments/callback` | 无角色，签名校验 |
| `POST /api/v1/auth/login` | 无角色，IP 限流 |

## 2. 分布式限流与防刷

### 2.1 算法

Redis ZSET 滑动窗口 + Lua 原子执行：

```lua
local key = KEYS[1]
local now = tonumber(ARGV[1])
local window = tonumber(ARGV[2])
local limit = tonumber(ARGV[3])
local member = ARGV[4]
redis.call('ZREMRANGEBYSCORE', key, 0, now - window)
local count = redis.call('ZCARD', key)
if count >= limit then
    return 0
end
redis.call('ZADD', key, now, member)
redis.call('PEXPIRE', key, window)
return 1
```

限流维度：

| 维度 | 用途 |
|---|---|
| `USER` | 下单、支付等登录后接口，按 `userId` |
| `IP` | 登录、匿名接口，按 `X-Forwarded-For` 或 `remoteAddr` |
| `GLOBAL` | 全局限流预留 |

配置：

```yaml
app:
  rate-limit:
    order-create:
      limit: ${RATE_LIMIT_ORDER_CREATE:20}
      window-seconds: 60
    payment-pay:
      limit: ${RATE_LIMIT_PAYMENT_PAY:20}
      window-seconds: 60
    auth-login:
      limit: ${RATE_LIMIT_AUTH_LOGIN:10}
      window-seconds: 60
```

注解：

```java
@RateLimit(key = "order-create", limit = 20, windowSeconds = 60,
           dimension = RateLimitDimension.USER)
```

超限返回 HTTP 429：

```json
{"code":10005,"message":"请求过于频繁","data":null,"traceId":"..."}
```

Redis 不可用时采取 fail-open，记录 WARN 并放行，避免限流组件拖垮主流程。

## 3. TraceId 全链路

| 场景 | 实现 |
|---|---|
| HTTP 请求 | `TraceIdFilter` 读取或生成 `X-Trace-Id`，写 MDC + 响应头 |
| MQ 消息 | `OrderTimeoutMessage` / `OrderPaySuccessMessage` 携带 `traceId` |
| MQ 消费 | 消费者 `TraceContext.open(traceId)` 恢复 MDC，处理完恢复原上下文 |
| 线程池 | `TraceTaskDecorator` 复制 MDC 与 TraceId 到 `solo-async-*` 线程 |
| 异常响应 | `ApiResponse.traceId` 返回当前 TraceId |

## 4. 集成测试结果

测试脚本：`scripts/security-smoke-test.sh`

启动参数：

```powershell
java -jar solo-eats-app/target/solo-eats-app-1.0.0-SNAPSHOT.jar `
  --app.order.timeout-delay-seconds=3 `
  --app.rate-limit.order-create.limit=3 `
  --app.payment.callback-secret=test-secret-123
```

### 4.1 越权访问

| 场景 | 结果 |
|---|---|
| 无 token 访问商家结算列表 | 401 |
| C_USER 调用商家接单 | 403 |
| 商家 2 调用商家 1 的订单接单 | 403（数据级） |
| MERCHANT 调用用户退款 | 403 |
| RIDER 调用商家接单 | 403 |
| C_USER 调用骑手接单 | 403 |
| C_USER 调用平台结算触发 | 403 |
| ADMIN 调用平台结算触发 | 200 |
| 无 token 下单 | 401 |

### 4.2 限流

同一 C_USER（userId=2）连续下单，限流阈值 3：

| 请求 | 结果 |
|---|---|
| 第 1 次 | 200，orderId=16 |
| 第 2 次 | 200，orderId=17 |
| 第 3 次 | 200，orderId=18 |
| 第 4 次 | 429，code=10005 |

前 3 次成功创建订单，第 4 次在进入 Controller 前被 Redis Lua 拦截。

### 4.3 MQ TraceId

请求头：

```text
X-Trace-Id: trace-mq-1792552353
```

订单创建响应：

```json
{"traceId":"trace-mq-1792552353","data":{"orderId":15}}
```

超时消费者日志：

```text
INFO [trace-mq-1792552353] c.s.o.service.mq.OrderTimeoutConsumer - consume order.timeout order=15
```

### 4.4 线程池 TraceId

请求：

```text
GET /api/v1/debug/trace/async
X-Trace-Id: trace-async-1792552353
Authorization: Bearer <ADMIN>
```

响应：

```json
{"code":0,"data":"trace-async-1792552353","traceId":"trace-async-1792552353"}
```

异步线程日志：

```text
INFO [trace-async-1792552353] c.s.app.debug.TraceAsyncProbeService - async trace probe traceId=trace-async-1792552353
```

线程名为 `solo-async-1`，说明 `TraceTaskDecorator` 已复制上下文。

## 5. 代码入口

| 层 | 文件 |
|---|---|
| JWT 工具 | `solo-eats-common/src/main/java/com/soloeats/common/auth/JwtUtils.java` |
| JWT Filter | `solo-eats-common/src/main/java/com/soloeats/common/auth/JwtAuthFilter.java` |
| 角色拦截器 | `solo-eats-common/src/main/java/com/soloeats/common/auth/RoleAuthInterceptor.java` |
| 角色注解 | `solo-eats-common/src/main/java/com/soloeats/common/auth/RequireRole.java` |
| 登录接口 | `solo-eats-app/src/main/java/com/soloeats/app/controller/AuthController.java` |
| 数据权限 | `solo-eats-app/src/main/java/com/soloeats/app/security/PermissionGuard.java` |
| 限流切面 | `solo-eats-common/src/main/java/com/soloeats/common/ratelimit/RateLimitAspect.java` |
| Trace 上下文 | `solo-eats-common/src/main/java/com/soloeats/common/trace/TraceContext.java` |
| 线程池装饰器 | `solo-eats-common/src/main/java/com/soloeats/common/trace/TraceTaskDecorator.java` |
| 线程池配置 | `solo-eats-app/src/main/java/com/soloeats/app/config/AsyncConfig.java` |
| 联调脚本 | `scripts/security-smoke-test.sh` |

## 6. 已知边界

| 项 | 当前处理 |
|---|---|
| 登录方式 | `local` profile 模拟登录，无密码、无 refresh token、无 token 黑名单 |
| 安全框架 | 自定义 Servlet Filter + HandlerInterceptor，未引入 Spring Security |
| 数据权限 | 已覆盖订单归属和商家 ID；复杂数据权限（部门/自定义范围）未接 |
| 限流粒度 | 单机 Redis 共享计数；未接网关层全局限流 |
| 限流降级 | Redis 异常 fail-open；生产可改为本地令牌桶兜底 |
| 秒杀 | 当前只有下单/支付限流；秒杀接口可直接复用 `@RateLimit` |
| Trace 持久化 | TraceId 只进日志与响应，不落业务表 |
| 历史脚本 | 启用 JWT 后，原有 curl 示例需先登录并带 `Authorization` 头 |
