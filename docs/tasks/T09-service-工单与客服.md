# T09 service-客服工单 · 投诉 · FAQ · 意见反馈

> 优先级：**P1** · 所属模块：`solo-eats-service`（新建）+ `solo-eats-app`
> 原定「单段执行（约 14 个文件）」，实际 26 个端点 + 6 张表，已拆为 **阶段A / 阶段B**（见下）

---

## 阶段拆分与勘误（2026-10-27 落地前核对）

### 拆成两段的原因

客服工作台（接口 21~26）需要给 `t_im_session` 加坐席字段，属**改既有表结构**；与工单主干的建表是两类变更，混在一段里回归面太大。故：

| 阶段 | 范围 | 迁移 | 端点 |
|---|---|---|---|
| **阶段A** | 工单主干 + 投诉 + FAQ + 意见反馈 + 工单统计 | `V25__service_ticket.sql`（建 6 表 + 菜单种子） | 1~20 |
| **阶段B** | 客服工作台坐席端（会话列表/接待/转接/结束/快捷话术/会话记录） | **派发时取实际最大 +1**（原写 `V26`，但 T10 内容装修已占用 `V26`，预计为 `V27`） | 21~26 |

### 三处与仓库现状不符，以本节为准

1. **迁移序号**：本文件写的 `V15__service_ticket.sql` 已被 `V15__delivery_track_anomaly.sql` 占用。当前实际最大为 **`V24`**，故阶段A 用 **`V25`**，阶段B 用 **`V26`**。动手前用 `ls solo-eats-app/src/main/resources/db/migration/ | sort -V | tail -3` 复核。`CLAUDE.md` 第 6 节「当前最大 `V3`」是过期描述，不要照抄。

2. **`ImService` 不存在**：本文件多处写「复用 T08 `ImService`」，实际 T08 交付的接口是 **`ImSessionService`**（`solo-eats-message/service/ImSessionService.java`），只有 6 个方法：`getOrCreate / listMine / messages / send / markRead / hide`。坐席端需要的「全局会话列表、接待、转接、结束」**都没有**，阶段B 只能在 `ImSessionService` 上**新增方法**（禁止改既有签名）。

3. **`t_im_session` 没有坐席字段**：现有列为 `user_id / peer_type / peer_id / last_message / last_message_at / user_unread / peer_unread / status / user_hidden / peer_hidden`，**没有 `agent_id`**。阶段B 的「接待 / 转接 / 按坐席看会话」必须先 `ALTER TABLE t_im_session ADD COLUMN agent_id ...`（只加列，禁止 DROP/MODIFY 既有列）。

### 菜单与权限种子（现 `t_sys_menu` 最大 id = 119，一级「客服与工单」= id 10）

**阶段A（V25）** —— 全部挂 `parent_id=10` 下的两级树：

| id | parent | 名称 | type | path | perms |
|---|---|---|---|---|---|
| 120 | 10 | 工单管理 | 2 | `/service/ticket` | `service:ticket:list` |
| 121 | 120 | 工单详情 | 3 | NULL | `service:ticket:detail` |
| 122 | 120 | 工单处理 | 3 | NULL | `service:ticket:handle` |
| 123 | 120 | 工单指派 | 3 | NULL | `service:ticket:assign` |
| 124 | 120 | 工单关闭 | 3 | NULL | `service:ticket:close` |
| 125 | 10 | 投诉处理 | 2 | `/service/complaint` | `service:complaint:list` |
| 126 | 125 | 责任判定 | 3 | NULL | `service:complaint:judge` |
| 127 | 10 | FAQ 维护 | 2 | `/service/faq` | `service:faq:list` |
| 128 | 127 | FAQ 编辑 | 3 | NULL | `service:faq:save` |
| 129 | 10 | 意见反馈 | 2 | `/service/feedback` | `service:feedback:list` |
| 130 | 129 | 反馈回复 | 3 | NULL | `service:feedback:reply` |

**阶段B（V26）**：

| id | parent | 名称 | type | path | perms |
|---|---|---|---|---|---|
| 131 | 10 | 客服工作台 | 2 | `/service/cs` | `service:cs:list` |
| 132 | 131 | 会话接待 | 3 | NULL | `service:cs:handle` |

种子一律 `ON DUPLICATE KEY UPDATE id = id`，并给 `tenant_id=1, role_id=1` 授权。

---

## 目标

**一句话**：把「用户投诉 → 客服受理 → 处理 → 回访 → 办结」做成一条有状态、有时限、可统计的工单流水线。

**完成后系统能做什么**：用户能提交工单/投诉/意见反馈并查看进度；客服能受理、指派、处理、回访、办结；平台能维护 FAQ 并对接用户端帮助中心；工单办结率/响应时长可统计。

---

## 规格书依据

| 章节 | 原文要点 |
|---|---|
| 4.10 客服与工单 | 「在线客服工作台（会话列表、接待、转接、结束）；工单管理：类型（订单问题/退款/配送/投诉/建议）、流转与指派、**时限与升级**、处理记录；投诉处理：投诉单列表、责任判定、处理结果、回访、拉黑/封禁联动；常见问题：FAQ 分类与条目维护（对应用户端"帮助中心"）；客服绩效：接待量、响应时长、满意度、工单办结率」 |
| 8.9 状态机 · 工单 | 待受理 → 处理中 → 待回访 → 已办结 / 已关闭 |
| 5.2.9 我的 | 「客服与帮助：在线客服、常见问题、意见反馈、投诉建议」 |
| 2.5 核心领域模型 | 「客服会话、工单、投诉、FAQ；工单 N:1 订单」 |

---

## 前置依赖

| 依赖 | 具体依赖物 |
|---|---|
| **T08** | `ImService`（在线客服会话复用同一通道，`peer_type=4`）；`PushService`（工单进展通知） |
| **T01/T02** | 鉴权、`@RequirePermission`、`TenantContext` |
| **T03** | `FileStoragePort`（凭证图） |
| 既有表 | `t_order`（工单关联订单，只读） |
| 依赖方向 | `service → message → system → common`；**不得**依赖 `order`（订单信息用 `orderId` + 冗余快照） |

---

## 数据模型

迁移脚本：`V15__service_ticket.sql`

| 表 | 关键字段 | 索引 |
|---|---|---|
| `t_service_ticket` | id, tenant_id, ticket_no varchar(32), **ticket_type tinyint(1订单问题 2退款 3配送 4投诉 5建议)**, **source tinyint(1用户 2商家 3骑手 4平台)**, submitter_type, submitter_id, **order_id bigint NULL**, subject varchar(200), content varchar(2000), images_json json, **priority tinyint(0普通 1紧急 2特急)**, **status tinyint(0待受理 1处理中 2待回访 3已办结 4已关闭)**, **assignee_id**, **accept_at**, **deadline_at**, **finish_at**, **satisfaction tinyint NULL**, satisfaction_remark, created_at/updated_at/deleted | `uk_ticket_no(ticket_no)`、`idx_status_deadline(status, deadline_at)`、`idx_submitter(submitter_type, submitter_id, created_at)`、`idx_order(order_id)`、`idx_assignee(assignee_id, status)` |
| `t_ticket_record` | id, tenant_id, ticket_id, from_status, to_status, action varchar(32), operator_id, operator_name, content varchar(1000), created_at | `idx_ticket_time(ticket_id, created_at)` |
| `t_complaint` | id, tenant_id, ticket_id, **order_id**, complainant_type, complainant_id, **target_type tinyint(2商家 3骑手 4平台)**, target_id, reason varchar(500), **liability tinyint NULL(1商家 2骑手 3平台 4用户)**, **result varchar(500)**, **punish_action tinyint(0无 1警告 2扣款 3封禁)**, callback_at, callback_result, created_at/updated_at | `idx_ticket(ticket_id)`、`idx_target(target_type, target_id)` |
| `t_faq` | id, tenant_id, category varchar(64), question varchar(200), answer text, sort, **view_count int**, status, created_at/updated_at/deleted | `idx_category_sort(category, sort)` |
| `t_feedback` | id, tenant_id, user_id, content varchar(1000), images_json json, contact varchar(64), status tinyint(0待处理 1已回复), reply varchar(1000), reply_at, created_at | `idx_status_time(status, created_at)` |
| `t_quick_reply` | id, tenant_id, category varchar(64), content varchar(500), sort, status, created_at/updated_at/deleted | `idx_category_sort(category, sort)` |

> 客服会话（`peer_type=4`）复用 T08 的会话/消息表，本卡不重复建表，只新增坐席端接入逻辑与 `t_quick_reply`。

---

## 接口清单

| # | 方法 | 路径 | 要点 | 权限 |
|---|---|---|---|---|
| 1 | POST | `/api/v1/c/tickets` | `{ticketType,orderId?,subject,content,images[],priority}` → `{ticketId,ticketNo}` | `C_USER` + `@Idempotent` |
| 2 | GET | `/api/v1/c/tickets` | 我的工单分页（`status` 筛选） | `C_USER` |
| 3 | GET | `/api/v1/c/tickets/{id}` | 详情 + 流转记录 + 处理人 | 提交者本人 |
| 4 | PUT | `/api/v1/c/tickets/{id}/close` | 撤单（仅 `status=0`） | 提交者本人 |
| 5 | PUT | `/api/v1/c/tickets/{id}/satisfaction` | `{satisfaction,satisfactionRemark}`（仅 `status=3`） | 提交者本人 |
| 6 | POST | `/api/v1/c/complaints` | `{orderId,targetType,targetId,reason,images[]}` | `C_USER` |
| 7 | GET | `/api/v1/c/faqs` | `category,keyword` FAQ 列表 | 白名单 |
| 8 | POST | `/api/v1/c/feedbacks` | `{content,images[],contact}` | `C_USER` |
| 9 | GET | `/api/v1/admin/tickets` | `status,ticketType,assigneeId,priority,dateFrom,dateTo,pageNum,pageSize` | `service:ticket:list` |
| 10 | GET | `/api/v1/admin/tickets/{id}` | 详情 + 记录 + 关联投诉 | `service:ticket:detail` |
| 11 | POST | `/api/v1/admin/tickets/{id}/accept` | 受理（`0→1`，设 `deadline_at`） | `service:ticket:handle` |
| 12 | POST | `/api/v1/admin/tickets/{id}/assign` | `{assigneeId}` 指派/转派 | `service:ticket:assign` |
| 13 | POST | `/api/v1/admin/tickets/{id}/handle` | `{content}` 处理记录（`1→1` 追加记录） | `service:ticket:handle` |
| 14 | POST | `/api/v1/admin/tickets/{id}/callback` | `{content,result}` 进入待回访（`1→2`） | `service:ticket:handle` |
| 15 | POST | `/api/v1/admin/tickets/{id}/finish` | 办结（`2→3`） | `service:ticket:handle` |
| 16 | POST | `/api/v1/admin/tickets/{id}/close` | 关闭（任意 → `4`） | `service:ticket:close` |
| 17 | PUT | `/api/v1/admin/complaints/{id}/judge` | `{liability,result,punishAction}` 责任判定 | `service:complaint:judge` |
| 18 | GET/POST/PUT/DELETE | `/api/v1/admin/faqs`（+`/{id}`） | FAQ 维护 | `service:faq:*` |
| 19 | GET | `/api/v1/admin/feedbacks`、`PUT /{id}/reply` | 反馈处理 | `service:feedback:*` |
| 20 | GET | `/api/v1/admin/tickets/stats` | 接待量、平均响应时长、办结率、满意度 | `service:ticket:list` |
| 21 | GET | `/api/v1/admin/cs/sessions` | **客服工作台·会话列表**（`peer_type=4`，按 `status 待接待/接待中/已结束`、`waiting_count` 排序，含未读） | `service:cs:list` |
| 22 | POST | `/api/v1/admin/cs/sessions/{id}/take` | **接待**（空闲客服抢接，条件更新 `assignee_id=NULL → 本人`，防并发双接待） | `service:cs:handle` |
| 23 | POST | `/api/v1/admin/cs/sessions/{id}/transfer` | `{toAgentId}` **转接**（写会话记录，`assignee_id` 变更） | `service:cs:handle` |
| 24 | POST | `/api/v1/admin/cs/sessions/{id}/close` | **结束会话**（`peer_type=4` 会话置结束，用户侧只读） | `service:cs:handle` |
| 25 | GET | `/api/v1/admin/cs/quick-replies` | 快捷回复话术（分类 + 内容，`peer_type=4` 会话内快捷发送） | `service:cs:list` |
| 26 | GET | `/api/v1/admin/cs/sessions/{id}/messages` | 会话记录查询（复用 T08 `ImService` 消息分页） | `service:cs:list` |
| 28 | POST | `/api/v1/admin/cs/sessions/{id}/messages` | 坐席端发送消息（body: `msgType`/`content`/`extraJson`），已接待会话按 `agent_id` 判权，待接待会话返回 90003 | `service:cs:handle` |

> 接口 21~26 为**客服工作台坐席端**，复用 T08 的 `ImService`/`peer_type=4` 会话通道；T08 只提供 C 端 IM，坐席端由本卡落地，**消除 T08↔T09 之间的真空**。客服绩效 `stats`（接口 20）的「接待量」须同时统计工单量 + 会话接待量。

**新增错误码**（9xxxx 追加）：`90010 TICKET_NOT_FOUND`、`90011 TICKET_STATUS_INVALID`、`90012 TICKET_NOT_OWNER`、`90013 TICKET_ALREADY_ASSIGNED`。

---

## 需要新增/修改的文件

| 类型 | 路径 |
|---|---|
| 改 | `pom.xml`（`<modules>` + `dependencyManagement` 增 `solo-eats-service`） |
| 新 | `solo-eats-service/pom.xml`（依赖 `solo-eats-common`、`solo-eats-system`、`solo-eats-message`、`mybatis-plus`、`lombok`） |
| 改 | `solo-eats-app/pom.xml` |
| 新 | `solo-eats-app/src/main/resources/db/migration/V15__service_ticket.sql` |
| 新 | `solo-eats-service/src/main/java/com/soloeats/service/domain/entity/` `ServiceTicket`、`TicketRecord`、`Complaint`、`Faq`、`Feedback`（5） |
| 新 | `.../domain/enums/TicketStatus.java`、`TicketType.java`（含合法流转表 `canTransit(from,to)`） |
| 新 | 5 个 Mapper |
| 新 | `.../service/TicketService.java` + `impl` |
| 新 | `.../service/ComplaintService.java` + `impl` |
| 新 | `.../service/FaqService.java` + `impl`（含 `FeedbackService` 或独立） |
| 新 | `.../service/view/`（`TicketView`、`TicketDetailView`、`TicketStatsView`、`FaqView`） |
| 新 | `solo-eats-app/src/main/java/com/soloeats/app/controller/ConsumerTicketController.java` |
| 新 | `solo-eats-app/src/main/java/com/soloeats/app/controller/AdminTicketController.java` |
| 新 | `solo-eats-app/src/main/java/com/soloeats/app/controller/AdminFaqController.java` |
| 新 | `.../service/CsSessionService.java` + `impl`（客服工作台：会话列表/接待/转接/结束，复用 T08 `ImService`） |
| 新 | `.../service/QuickReplyService.java` + `impl`（快捷话术，`t_quick_reply` 表并入 V15 脚本） |
| 新 | `solo-eats-app/src/main/java/com/soloeats/app/controller/AdminCsController.java`（接口 21~26 坐席端） |
| 新 | `solo-eats-service/src/test/java/com/soloeats/service/domain/enums/TicketStatusTest.java` |

---

## 关键实现约束

| 项 | 约束 |
|---|---|
| **状态机** | `0待受理 → 1处理中 → 2待回访 → 3已办结`；`0/1/2 → 4已关闭` 允许；**禁止**跳级（`0→3`）。用条件更新 `WHERE id=? AND status=?`，不匹配抛 `90011` |
| **时限** | 受理时按 `priority` 设 `deadline_at`：普通 24h、紧急 4h、特急 1h；超时工单在列表标红（`overdue=true`），并由定时任务每日 09:00 推送提醒 |
| **一次受理** | `accept` 用条件更新 `WHERE status=0`，并发只成功一次；已被他人受理返回 `90013` |
| **责任判定** | `judge` 只对 `t_complaint` 生效，写 `liability` + `punish_action`；`punish_action=3 封禁` 只**登记**，实际封禁由 T07/T04 执行（通过端口接口 + TODO，避免跨模块强依赖） |
| **越权** | 所有 `/c/tickets/{id}` 必须校验 `submitter_id == AuthContext.userId()`，否则 `90012` |
| **工单与 IM** | 客服会话复用 T08 `ImService`（`peer_type=4`），本卡不另建会话表 |
| **FAQ** | 用户端只返回 `status=0`；`view_count` 用 `UPDATE ... SET view_count = view_count + 1`，不做实时排序 |
| **满意度** | 仅 `status=3` 可评一次，重复返回 `90011` |
| **多租户** | 全部表带 `tenant_id` |
| **统计** | `stats` 用 SQL 聚合（`AVG(TIMESTAMPDIFF(...))`、`SUM(CASE WHEN status=3 THEN 1 ELSE 0 END)/COUNT(*)`），不做实时大表扫描，加 `dateFrom/dateTo` 限制 |

---

## 验收标准

```bash
mvn -q -pl solo-eats-service -am compile && mvn -q -DskipTests package

# 提交工单
curl -s -X POST http://localhost:8080/api/v1/c/tickets -H "Authorization: Bearer $USER_TOKEN" \
  -H "Idempotency-Key: tk-001" \
  -d '{"ticketType":1,"orderId":1,"subject":"餐品少了","content":"少了一份主食","priority":1}'
# 断言：code=0；ticket_no 生成

# 跳级办结 → 拒绝
curl -s -X POST http://localhost:8080/api/v1/admin/tickets/1/finish -H "Authorization: Bearer $ADMIN_TOKEN"
# 断言：code=90011

# 正常流转 受理→处理→回访→办结
# 断言：t_ticket_record 每条流转一条记录

# 越权
curl -s http://localhost:8080/api/v1/c/tickets/1 -H "Authorization: Bearer $OTHER_USER_TOKEN"
# 断言：code=90012

# 统计
curl -s "http://localhost:8080/api/v1/admin/tickets/stats?dateFrom=2025-01-01&dateTo=2025-01-31" -H "Authorization: Bearer $ADMIN_TOKEN"
# 断言：含 ticketCount/solvedRate/avgResponseMinutes/satisfactionAvg
```

---

## 禁止事项

1. 不得新增第二套 IM 会话表（复用 T08）。
2. 不得在 `service` 模块依赖 `order`/`delivery`/`settlement`；责任判定的封禁/扣款用端口接口 + TODO。
3. 不得允许工单状态跳级或逆向回退（除关闭）。
4. 不得让 `/c/tickets` 可查他人工单。
5. 不得把工单内容渲染为 HTML（XSS）。
6. 不得改动 T08 的 `ImService` 接口签名（只可新增方法）。
7. 不得删除 `t_ticket_record`（审计流水）。

---

## 执行记录

| 日期 | 会话范围 | 新增/修改文件 | 编译结果 | 遗留问题 |
|---|---|---|---|---|
| 2026-10-27 | T09 阶段A：工单主干 + 投诉 + FAQ + 意见反馈 + 工单统计（接口 1~20） | 52（新增 49 / 修改 3） | 通过：`mvn -q -pl solo-eats-service -am compile`；`mvn -q -DskipTests package`；`TicketStatusTest` 7/7 | 阶段B（接口 21~26、`t_im_session` 加列、`QuickReplyService`/`AdminCsController`）未做；投诉的扣款/封禁仅登记 `punish_action`，执行端口待 T03/T04/T07；`stats` 接待量只含工单量 |
| 2026-10-27 | T09 阶段B：客服工作台坐席端（接口 21~28） | 22（新增 16 / 修改 6），另文档 2 | 通过：`mvn -q -pl solo-eats-message -am compile`、`mvn -q -pl solo-eats-service -am compile`、`mvn -q -pl solo-eats-message test`（33/33，`ImSessionServiceImplTest` 19/19）、`mvn -q -pl solo-eats-service test`（12/12，`QuickReplyServiceImplTest` 5/5）、`mvn -q -DskipTests package` 全部 rc=0 | 会话接待量未并入 `TicketService.stats`（阶段A 的 `TicketStatsView` 契约未动），改由新接口 `GET /admin/cs/stats` 单独提供；前端页面未做，属 T17 范畴；`GET /admin/cs/sessions/{id}/messages` 已修复：改用新增的 `ImSessionService.agentMessages(sessionId, agentId, ...)` 按 `agent_id` 判权，待接待会话（`agent_id IS NULL`）任意坐席可读，仅被其他坐席接待时返回 90003；坐席端**发消息**已补齐（接口 28 `POST /admin/cs/sessions/{id}/messages`）：`requireParticipantSession` 追加 `peer_type=4 && agent_id=当前主体` 分支，`send` 与 `messages` 同时受益，判权不再依赖 `peer_id` 占位值；待接待会话（`agent_id IS NULL`）仍拒绝发言并返回 90003，必须先接待；坐席名未回填（`AgentSessionView` 无 `agentName` 字段，且 service 模块不得直连 `SysUserMapper`） |
