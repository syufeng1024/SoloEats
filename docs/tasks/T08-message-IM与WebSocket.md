# T08 message-站内信 · WebSocket · IM 会话

> 优先级：**P0**（站内信 + WebSocket 订单节点推送）/ **P1**（IM 会话）
> 所属模块：`solo-eats-message`（新建）+ `solo-eats-app`
> 分两段：**阶段A（WebSocket 通道 + 站内信 + 订单节点事件）→ 阶段B（IM 会话，三端共用）**

---

## 目标

**一句话**：让"订单状态变了"这件事在 2 秒内出现在用户/商家/骑手三个端的界面上，并留下可回溯的站内信；再把三端聊天收敛到同一条会话通道。

**完成后系统能做什么**
1. 前端用 `token` 连上 `ws://host/ws?token=...`，收到 `{type:"ORDER_STATUS", orderId, status, ts}` 推送。
2. 订单每次状态流转产生一条站内信，用户在消息中心看到"订单消息"，支持未读数、全部已读。
3. 消息模板可配置（变量占位 `{orderNo}`、`{amount}`），由后台维护。
4. 用户 ↔ 商家、用户 ↔ 骑手的 IM 会话共用一套表与通道；会话与订单强关联（N:1）。

---

## 规格书依据

| 章节 | 原文要点 |
|---|---|
| 5.2.7 消息中心 | 「消息聚合：系统通知/订单消息/优惠活动/会话；订单消息：订单状态变更、支付结果、退款进度、配送提醒；**IM 聊天：文字、表情、图片、订单卡片、常用语、位置；离线消息补推**；未读管理：红点/角标、全部已读、删除会话；推送：「WebSocket 长连接 + 轮询兜底 + 端上本地通知」」 |
| 4.9 内容运营 · 消息模板与推送 | 「站内信模板、消息模板（订单类/营销类/系统类）、变量配置、推送记录、**WebSocket 下发日志**」 |
| 6.2.3 会话归属 | 「骑手端 IM 与用户端/商家端共用同一后端会话通道（自建 WebSocket）；会话与订单强关联（N:1），订单完成后会话保留 N 天（后台可配）后转只读留档」 |
| 9.1 性能 | 「消息延迟：订单节点消息 ≤ 2s 到达（WebSocket 长连接）；多端界面状态一致 ≤ 3s」 |
| 2.3 后端模块 | 「message：站内信、消息模板、自建 IM 会话、WebSocket 推送」 |
| 技术架构 0.4 | 「消息 P0：WebSocket 订单节点通知 + 站内信；P1：自建 IM、客服会话」 |

---

## 前置依赖

| 依赖 | 具体依赖物 |
|---|---|
| **T01/T02** | `UserPrincipal`、`JwtUtils`（WS 握手校验 token）、`TenantContext` |
| **T06** | `t_member_user`（用户端消息的接收者） |
| **T03** | `FileStoragePort`（IM 图片消息） |
| 既有类（**复用，不改**） | `common/message/RabbitConstants.java`（已有 exchange/queue 常量）、`common/message/OrderPaySuccessMessage.java`、`OrderTimeoutMessage.java`；`solo-eats-app/config/RabbitMqConfig.java`；`solo-eats-order/service/mq/OrderPaySuccessConsumer.java`、`OrderTimeoutConsumer.java` |
| 既有事实 | 项目已引入 `spring-boot-starter-amqp`，**但未引入 `spring-boot-starter-websocket`**（需在 `solo-eats-message/pom.xml` 加） |

---

## 数据模型

迁移脚本：`V14__message.sql`

| 表 | 关键字段 | 索引 |
|---|---|---|
| `t_message_template` | id, tenant_id, template_code, template_name, **template_type tinyint(1订单 2营销 3系统 4客服)**, title_tpl varchar(200), content_tpl varchar(1000), channels varchar(64)（`SITE,WS,PUSH`）, status, created_at/updated_at/deleted | `uk_tenant_code(tenant_id, template_code)` |
| `t_site_message` | id, tenant_id, **receiver_type tinyint(1用户 2商家 3骑手 4平台)**, receiver_id, template_code, **msg_type tinyint(1订单 2营销 3系统)**, title, content, **biz_type varchar(32)**, **biz_id bigint**, **read_flag tinyint**, read_at, created_at | `idx_receiver(receiver_type, receiver_id, read_flag, created_at)`、`idx_biz(biz_type, biz_id)` |
| `t_push_log` | id, tenant_id, message_id, channel varchar(16)（WS/SITE/PUSH）, receiver_type, receiver_id, **status tinyint(0待发 1成功 2失败)**, error_msg, sent_at, created_at | `idx_status_time(status, created_at)` |
| `t_im_session` | id, tenant_id, **session_no varchar(32)**, **order_id bigint NULL**, **user_id**, **peer_type tinyint(2商家 3骑手 4客服)**, **peer_id**, **last_message varchar(200)**, **last_message_at**, user_unread int, peer_unread int, **status tinyint(0进行中 1已结束 2只读)**, created_at/updated_at | `uk_order_peer(order_id, user_id, peer_type, peer_id)`、`idx_user(user_id, last_message_at)`、`idx_peer(peer_type, peer_id, last_message_at)` |
| `t_im_message` | id, tenant_id, session_id, **sender_type tinyint(1用户 2商家 3骑手 4客服 5系统)**, sender_id, **msg_type tinyint(1文字 2图片 3订单卡片 4位置 5常用语)**, content varchar(2000), extra_json json, **read_flag tinyint**, created_at | `idx_session_time(session_id, created_at)` |
| `t_notify_setting` | id, tenant_id, **receiver_type tinyint(1用户 2商家 3骑手)**, receiver_id, **notify_type tinyint(1订单 2营销 3系统 4会话)**, **enabled tinyint DEFAULT 1**, created_at/updated_at | `uk_receiver_type(receiver_type, receiver_id, notify_type)` |

> `t_notify_setting` 补规格书 5.2.7:856「推送设置：用户可开关各类通知」。`PushService` 下发前必须查此表（默认开，无记录视为开启）。

---

## 接口清单

| # | 方法 | 路径 | 要点 | 权限 |
|---|---|---|---|---|
| 1 | WS | `/ws?token={jwt}` | 握手校验 token，绑定 `receiverType+receiverId`；心跳 `ping/pong` 30s | token |
| 2 | GET | `/api/v1/messages` | `msgType,readFlag,pageNum,pageSize` 当前登录者站内信 | 登录即可 |
| 3 | GET | `/api/v1/messages/unread-count` | `{total, byType:{order:3,marketing:1,system:0}}` | 登录即可 |
| 4 | PUT | `/api/v1/messages/{id}/read` | 标记单条已读 | 登录即可 |
| 5 | PUT | `/api/v1/messages/read-all` | `{msgType?}` 全部已读 | 登录即可 |
| 6 | GET | `/api/v1/admin/message-templates` | 模板分页 | `message:template:list` |
| 7 | POST/PUT | `/api/v1/admin/message-templates`、`/{id}` | `{templateCode,templateName,templateType,titleTpl,contentTpl,channels}` | `message:template:*` |
| 8 | POST | `/api/v1/admin/messages/send` | `{receiverType,receiverIds[],templateCode,params{}}` 定向发送 | `message:send` |
| 9 | GET | `/api/v1/im/sessions` | 会话列表（未读数、最近消息、对方信息） | 登录即可 |
| 10 | POST | `/api/v1/im/sessions` | `{orderId, peerType, peerId}` 创建/复用会话（幂等） | 登录即可 |
| 11 | GET | `/api/v1/im/sessions/{id}/messages` | `beforeMessageId,pageSize` 游标分页 | 会话参与者 |
| 12 | POST | `/api/v1/im/sessions/{id}/messages` | `{msgType,content,extra}` 发送（同时 WS 推送对方） | 会话参与者 |
| 13 | PUT | `/api/v1/im/sessions/{id}/read` | 清零未读 | 会话参与者 |
| 14 | DELETE | `/api/v1/im/sessions/{id}` | 删除会话（软删/仅隐藏本人） | 会话参与者 |
| 15 | GET | `/api/v1/notify/settings` | 当前登录者通知开关（各 `notifyType` 的 enabled） | 登录即可 |
| 16 | PUT | `/api/v1/notify/settings` | `{notifyType, enabled}` 更新单个开关 | 登录即可 |

**新增错误码**（9xxxx）：`90001 IM_SESSION_NOT_FOUND`、`90002 IM_SESSION_CLOSED`、`90003 IM_NOT_PARTICIPANT`、`90004 WS_UNAUTHORIZED`。

---

## 需要新增/修改的文件

| 类型 | 路径 |
|---|---|
| 改 | `pom.xml`（`<modules>` 增 `solo-eats-message`；`dependencyManagement` 增坐标） |
| 新 | `solo-eats-message/pom.xml`（依赖 `solo-eats-common`、`solo-eats-system`、`spring-boot-starter-websocket`、`mybatis-plus`、`amqp`、`lombok`） |
| 改 | `solo-eats-app/pom.xml`（依赖 `solo-eats-message`） |
| 新 | `solo-eats-app/src/main/resources/db/migration/V14__message.sql` |
| 新 | `solo-eats-message/src/main/java/com/soloeats/message/domain/entity/` `MessageTemplate`、`SiteMessage`、`PushLog`、`ImSession`、`ImMessage`（5） |
| 新 | 5 个 Mapper |
| 新 | `solo-eats-message/src/main/java/com/soloeats/message/ws/WsSessionRegistry.java`（`Map<receiverType:receiverId, Set<WebSocketSession>>`） |
| 新 | `solo-eats-message/src/main/java/com/soloeats/message/ws/MessageWebSocketHandler.java`（`TextWebSocketHandler`） |
| 新 | `solo-eats-message/src/main/java/com/soloeats/message/ws/WsHandshakeInterceptor.java`（解析 token、校验角色） |
| 新 | `solo-eats-app/src/main/java/com/soloeats/app/config/WebSocketConfig.java`（`@EnableWebSocket`，注册 `/ws` + 允许跨域） |
| 新 | `solo-eats-message/src/main/java/com/soloeats/message/service/MessageTemplateService.java` + `impl` |
| 新 | `solo-eats-message/src/main/java/com/soloeats/message/service/SiteMessageService.java` + `impl`（含模板渲染 + 站内信落库 + WS 推送 + 推日志） |
| 新 | `solo-eats-message/src/main/java/com/soloeats/message/service/PushService.java` + `impl`（统一出口：SITE/WS/PUSH） |
| 新 | `solo-eats-message/src/main/java/com/soloeats/message/service/ImService.java` + `impl` |
| 新 | `solo-eats-message/src/main/java/com/soloeats/message/mq/OrderStatusNotifyConsumer.java`（消费订单事件 → 站内信 + WS） |
| 新 | `solo-eats-message/src/main/java/com/soloeats/message/service/view/`（`SiteMessageView`、`UnreadCountView`、`ImSessionView`、`ImMessageView`） |
| 新 | `solo-eats-app/src/main/java/com/soloeats/app/controller/MessageController.java` |
| 新 | `solo-eats-app/src/main/java/com/soloeats/app/controller/ImController.java` |
| 新 | `solo-eats-app/src/main/java/com/soloeats/app/controller/AdminMessageController.java` |
| 改 | `solo-eats-common/src/main/java/com/soloeats/common/message/RabbitConstants.java`（**追加** `ORDER_STATUS_EXCHANGE/QUEUE/ROUTING_KEY`，不改已有常量） |
| 新 | `solo-eats-message/src/test/java/com/soloeats/message/service/impl/SiteMessageServiceImplTest.java` |

---

## 关键实现约束

| 项 | 约束 |
|---|---|
| **WS 鉴权** | 握手阶段 `WsHandshakeInterceptor` 解析 `?token=`，失败直接 `false`（HTTP 401）；**不得**在 query 里传 `userId` 并信任它 |
| **WS 多实例** | P0 单实例，`WsSessionRegistry` 用本地 Map 即可；**但必须**在 `PushService` 里留 Redis Pub/Sub 降级口（`TODO(P1 多实例)`），避免后续重写 |
| **消息延迟** | 订单状态变更 → `RabbitMQ` → `OrderStatusNotifyConsumer` → `PushService.pushToWs()`；全链路要求 ≤2s。**禁止**在订单主事务内同步发 WS |
| **离线兜底** | WS 不在线时**必须**落 `t_site_message`，前端轮询 `GET /messages/unread-count` 兜底（对齐 5.2.7「轮询兜底」） |
| **模板渲染** | 极简占位替换 `{key}`，**不引入模板引擎**；未提供的变量原样保留并 `log.warn` |
| **IM 权限** | 任何 `/im/sessions/{id}/**` 操作必须校验当前主体是 `user_id` 或 `peer`，否则 `90003` |
| **IM 会话唯一** | `uk_order_peer(order_id,user_id,peer_type,peer_id)`；创建会话先查后插，冲突则重查 |
| **IM 只读** | 订单完成后 N 天（`app.im.readonly-after-days`，默认 7）会话转 `status=2 只读`，发送返回 `90002`；定时任务每日扫描 |
| **未读计数** | 发送时对方 `unread+1`（`UPDATE ... SET user_unread = user_unread + 1`），已读时清零；**不得**用 `COUNT(*)` 实时算 |
| **消息体大小** | `content` ≤2000 字符；图片必须走 `t_file` 的 `fileId`，不接受外链 |
| **XSS** | 站内信/IM 内容纯文本转义，前端按纯文本渲染（订单卡片用 `extra_json` 结构化，不渲染 HTML） |
| **多租户** | 全部表带 `tenant_id`；WS 连接时把 `tenantId` 绑定到会话属性 |
| **消息保留** | 站内信保留 180 天（`app.message.retain-days`），定时清理；IM 消息保留 365 天 |

---

## 验收标准

```bash
mvn -q -pl solo-eats-message -am compile
mvn -q -DskipTests package

# 1) WS 连接（用 wscat 或 websocat）
websocat "ws://localhost:8080/ws?token=$USER_TOKEN"
# 断言：连接成功；30s 心跳不断

# 2) 触发订单状态变更 → 2s 内收到推送
curl -s -X POST http://localhost:8080/api/v1/merchant/orders/1/accept -H "Authorization: Bearer $MERCHANT_TOKEN"
# 断言：WS 收到 {"type":"ORDER_STATUS","orderId":1,"status":"备餐中"}

# 3) 站内信落库
curl -s "http://localhost:8080/api/v1/messages?msgType=1" -H "Authorization: Bearer $USER_TOKEN"
# 断言：至少 1 条，标题含订单号

# 4) 未读数
curl -s http://localhost:8080/api/v1/messages/unread-count -H "Authorization: Bearer $USER_TOKEN"
# 断言：total >= 1

# 5) WS 未连接时也有站内信（离线兜底）
# 6) 全部已读
curl -s -X PUT http://localhost:8080/api/v1/messages/read-all -H "Authorization: Bearer $USER_TOKEN"
curl -s http://localhost:8080/api/v1/messages/unread-count -H "Authorization: Bearer $USER_TOKEN"
# 断言：total=0

# 7) IM 会话幂等
curl -s -X POST http://localhost:8080/api/v1/im/sessions -H "Authorization: Bearer $USER_TOKEN" \
  -d '{"orderId":1,"peerType":2,"peerId":1}'
# 断言：两次调用返回同一 sessionId

# 8) 非参与者读消息
curl -s http://localhost:8080/api/v1/im/sessions/1/messages -H "Authorization: Bearer $OTHER_USER_TOKEN"
# 断言：code=90003
```

---

## 禁止事项

1. **不得修改** `RabbitConstants`、`OrderPaySuccessMessage`、`OrderTimeoutMessage` 的已有常量/字段；只允许追加。
2. **不得改动** `solo-eats-order` 的 MQ 生产者与消费者；订单事件通过新增 exchange/queue 旁路订阅（或用既有 `OrderPaySuccessMessage` 事件扩展）。
3. 不得把 WS 消息当作唯一通道（必须有站内信兜底，见 9.2「WebSocket 不作为消息唯一通道，需站内信兜底」）。
4. 不得在订单事务内同步推送 WS/MQ。
5. 不得信任前端传来的 `userId/receiverId`，一律取 `AuthContext`。
6. 不得引入第三方 IM SDK。
7. 不得用 `COUNT(*)` 实时算未读数。
8. 不得把 IM 内容渲染为 HTML（XSS）。
9. 不得在本卡改动 `product`/`delivery`/`settlement`/`marketing`。

---

## 执行记录

| 日期 | 会话范围 | 新增/修改文件 | 编译结果 | 遗留问题 |
|---|---|---|---|---|
| 2026-10-25 | T08 阶段A（站内信、WebSocket 通道、订单节点实时推送、消息模板、通知开关；不做 IM 会话） | 新增 45 / 修改 5（另本卡执行记录 1 处） | 本机 PowerShell 禁执行 `mvn`/`java`，构建经 `maven:3.9-eclipse-temurin-17` 容器对同一工作区执行：`mvn -B clean -DskipTests package` BUILD SUCCESS（14 模块，common 41 / message 29 / app 155 个源文件全量重编）；`mvn -pl solo-eats-message -am test` BUILD SUCCESS；`mvn -pl solo-eats-order -am test` BUILD SUCCESS（order 25 个测试全绿） | 迁移号改为 **V17**（V16 已被 T07-B2 占用），菜单权限 id 104~107，挂在「内容运营」(id=9) 下。新增测试 `MessageTemplateServiceImplTest` 4 + `SiteMessageServiceImplTest` 4 全绿。WS 走原生 `/ws?token=`，未引入 STOMP/SockJS；握手校验放在 `MessageWebSocketHandler.afterConnectionEstablished`（未单独建 `WsHandshakeInterceptor`），`JwtAuthFilter.shouldNotFilter` 放行 `/ws`。阶段A 只做接口 1~8、15~16；`t_im_session`/`t_im_message` 已建表但无读写代码（阶段B）。`PushService` 只直推本机会话，多实例 Redis Pub/Sub 广播留 `TODO(P1 多实例)`。订单状态仅 10/20/50 映射三张模板，其余状态只推 WS 不落站内信。`t_site_message`/`t_push_log` 按任务卡只建 `created_at`，未加 `updated_at`。消息保留期清理、心跳超时踢连接、站内信删除均未做 |
