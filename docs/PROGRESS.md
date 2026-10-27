# 项目完成度

> 更新于 2026-10-27 · 判定依据：任务卡执行记录 + 代码实测（非人工估算）

项目按 17 张任务卡（[`docs/tasks/`](tasks/)）推进。**当前阶段：P1 收尾中，T17 运营后台与商家中心页面补齐。**

## 总览

| 指标 | 数值 |
|---|---|
| 任务卡 | 17 张 |
| 会话单元（阶段） | 约 36 个 |
| **已完成单元** | **约 33 个（≈92%）** |
| 后端 Maven 模块 | **16 个（P1 规划的模块已全部建完）** |
| 数据库迁移 | Flyway V1~V34，约 120 张表 |
| 后端接口 | 80 个 Controller |
| 前端页面 | Web 后台 72 页 · 商家中心独立工程 11 页 · 用户端 47 页 · 骑手端 24 页 · 商家端 11 页 |

---

## 已交付

| 任务卡 | 已完成阶段 | 交付范围 |
|---|---|---|
| **T01** 账号 RBAC | A / B / C | 账号密码登录、用户角色菜单 CRUD、`@RequirePermission` 拦截器、Redis 权限缓存 |
| **T02** 租户组织日志 | A / B / C-2 | 租户套餐、部门岗位、数据字典、操作/登录日志、邮件公告、`DataScopeInnerInterceptor` 行级权限 |
| **T03** 商家入驻 | A / B | MinIO 文件服务、入驻申请→资质→审核→生成管理员账号、结算账户、PII 加密 |
| **T04** 门店 | 全 | 门店 CRUD、营业状态、配送围栏、门店账号 |
| **T05** 商品中心 | A / B | 品类树、SPU 商品库、门店商品、规格组、加料、估清、商品评价、素材库 |
| **T06** 用户会员 | A / B / C1 / C2 | C 端短信登录、用户资料、地址簿、隐私协议、钱包与后台调账、会员管理、标签分群 |
| **T07** 配送调度 | A / B1 / B2 | 骑手档案认证、上下线听单抢单、派单策略、取货送达、轨迹上报与回放、改派、运费模板、骑手收入结算 |
| **T08** 消息 | A / B | 站内信、WebSocket 实时推送、订单节点通知、IM 会话、消息模板、推送设置 |
| **T12** 营销 | A-0 / A-1 | 阶段A-0：平台级单档满减 + 试算/分摊；阶段A-1：**券后台管理**（模板 CRUD、启停、定向发券防超发、领取记录，7 个端点 `/api/v1/admin/coupons`），供 T17 券页调用 |
| **T13** 用户端 App | A | 登录、首页、门店点餐、SKU 弹窗、购物车、确认订单、支付结果、订单列表/详情、取餐码、配送追踪、评价（11 页） |
| **T14** 骑手端 App | A | 登录、听单大厅、任务列表/详情、取货、送达、收入、认证（8 页） |
| **T15** 商家端 App | A | 登录、订单列表/详情、接单备餐、自取核销、商品与估清、门店设置（8 页） |
| **T16** 结算 | A | 结算单审核→模拟打款、提现闭环 |
| **T11** 报表 | A | **阶段A 已交付**：`solo-eats-report` 模块、6 张报表表（阶段A 填充平台/门店日报）、工作台实时卡 `GET /api/v1/admin/report/dashboard`、经营总览 `GET /api/v1/admin/report/overview`（日/周/月）、02:30 日聚合 + 每 5 分钟当日刷新；另保留既有 `GET /api/v1/merchant/report/dashboard` |
| **T17** 运营后台与商家中心 | 批次0 | **P0 收口**：运营后台新增商家入驻审核、门店管理、商品库列表、基础券管理 4 组 6 页 + 工作台 7 张今日卡；`solo-eats-web/shared/` 共享层抽出；`solo-eats-web-merchant/` 独立商家中心工程（base `/merchant/`，复制 3 个商家视图）。两条 `npm run build` 均通过，单测 10/10 |
| **T09** 客服工单 | A / B | 新建 `solo-eats-service` 模块、6 张表（`t_service_ticket`/`t_ticket_record`/`t_complaint`/`t_faq`/`t_feedback`/`t_quick_reply`）；工单状态机 `0→1→2→3`、仅允许 `→4` 关闭；C 端提交/查询/撤单/评价、投诉、FAQ、意见反馈 8 端点；后台受理/指派/处理/回访/办结/关闭、投诉责任判定、FAQ 维护、反馈回复、工单统计 12 端点。**阶段B**：`t_im_session` 增量加 `agent_id`（V28，带 `idx_agent_status`）、坐席端会话列表/接待/转接/结束（条件更新防双接待，`90005`）、会话记录、快捷话术 CRUD（`t_quick_reply` 逻辑删除）、坐席统计 `GET /admin/cs/stats`、坐席发消息 `POST /admin/cs/sessions/{id}/messages`，共 8 端点（接口 21~28） |
| **T10** 内容装修 | 全 | 新建 `solo-eats-content` 模块、5 张表（`t_home_page`/`t_home_floor`/`t_banner`/`t_search_keyword`/`t_app_version`）；首页楼层 CRUD + 排序 + 发布（引用 categoryId/shopId/spuId 校验，失败 `90020`）、Banner 维护、搜索词四类维护 + 联想屏蔽词过滤、App 版本三端维护 + 灰度（`userId`/`deviceId` 取模）+ 强制更新；用户端 `/c/home` 聚合（Redis 缓存、无 N+1）、`/c/banners`、`/c/search/suggest`、`/c/app/version` |

**可完整跑通的主链路**：

```
登录 → 选店 → 加购 → 下单 → 支付 → 商家接单备餐
     → （配送上门 / 自提核销）→ 评价 → 结算打款
```

---

## 未交付

### P0 缺口

**已清零。** T17 批次0 于 2026-10-27 交付（commit 见 `feat(T17-B0)`），T11 阶段A 于 2026-10-26 交付。P0 范围内已无未交付项。

> 遗留说明：T17 批次0 的页面是"能看见能操作"的最小实现，任务卡执行记录里列了 10 条降级项（如门店表单未含营业时段、入驻列表品类筛选只能填 ID、无按钮级 `v-perm`），这些不阻断 P0 验收，但要进 P1 收口清单。

### P1 未启动

| 任务卡 | 缺口 |
|---|---|
| T09 客服工单 | **阶段A/B 均已完成（接口 1~28）**；遗留：坐席「接收会话消息」页面未做（属 T17），会话接待量未并入 `/admin/tickets/stats`（改由 `/admin/cs/stats` 提供） |
| T10 内容装修 | **已交付**（5 张表 + 14 端点）；遗留见 T10 执行记录：投放人群 `audience=1/2` 用未登录/已登录近似、`click_count` 无埋点自增接口、首页 P95 未压测 |
| T11 阶段B | **已交付**（经营/流量/商品/财务报表，`report/` 7 页） |
| T12 阶段A/B | **已交付**（满减、秒杀、会员等级、积分、签到，`marketing/` 3 页） |
| T13 阶段B | **已交付**（用户端 47 页：搜索/消息/会员/售后/营销/地址/评价） |
| T14 阶段B | **已交付**（骑手端 24 页） |
| T15 阶段B | **已交付**（商家端 11 页，财务/评价/我的） |
| T16 阶段B | **已交付**（三级抽佣规则引擎 + 资金流水，`finance/` 4 页） |
| T17 批次1~4 | **运营后台 72 页 + 商家中心 11 页，已基本补齐**；遗留见各批次执行记录（菜单 component 未接入 `/auth/menus` 动态菜单，仅靠静态路由 + 兜底菜单；无 `v-perm` 按钮级权限；少量页面后端接口缺失需回补） |

---

## 怎么复核

进度**唯一正本是各任务卡末尾的「执行记录」**，配合 Git 提交历史。需要重新核实时代码层面这样查：

```bash
# 模块是否建了
grep -c '<module>' pom.xml                        # 期望 16，P1 规划的模块已全部建完

# 数据库演进到哪一版
ls solo-eats-app/src/main/resources/db/migration/*.sql | wc -l   # 期望 34（V34）

# 接口规模
ls solo-eats-app/src/main/java/com/soloeats/app/controller/*.java | wc -l   # 期望 80

# 前端页面数
grep -c '"path"' solo-eats-uniapp-user/src/pages.json      # 47
grep -c '"path"' solo-eats-uniapp-rider/src/pages.json     # 24
grep -c '"path"' solo-eats-uniapp-merchant/src/pages.json  # 11
find solo-eats-web/src/views -name '*.vue' | wc -l         # 72（运营后台）
find solo-eats-web-merchant/src/views -name '*.vue' | wc -l # 11（商家中心）

# 各卡执行记录有无
grep -n '执行记录' -A 6 docs/tasks/T05-*.md
```

## 维护约定

- 每完成一个会话单元：更新**对应任务卡**的「执行记录」，再同步本文档的两张表。
- 一个会话单元一个 commit，信息格式 `feat(T03-A): ...`。
- 状态口径：`done` = 代码 + 编译 + 提交 + 执行记录全齐；`doing` = 有未提交/未验证；`todo` = 未派发。
- 本文档只记录**结论与依据**，具体实现细节看任务卡。
