# T14 骑手端 UniApp（24 页，P0 先做 8 页）

> 优先级：**P0**（阶段A 8 页）/ **P1**（阶段B 其余 16 页）
> 所属：新建 `solo-eats-uniapp-rider/`（本仓库子目录）
> 分两段：**阶段A（认证→上线→听单→抢单→取货→送达→收入）→ 阶段B（排班/考勤/评分/申诉/帮助）**

---

## 目标

**一句话**：让骑手在手机上完成「认证 → 听单抢单 → 到店取货 → 送达 → 看收入提现申请」的全部动作。

**完成后系统能做什么**：骑手提交实名+健康证并通过审核；上线后接系统派单或抢单大厅抢单；任务卡片显示取送货地址、距离、金额、超时倒计时；可一键「我已取货」「确认送达」并上传留证；收入页看到今日/本周/本月与每单构成。

---

## 规格书依据

| 章节 | 原文要点 |
|---|---|
| 1.7 交付总览 | 「骑手端 App：UniApp，约 **24 页**，实名认证、听单抢单、配送任务、导航取货、送达留证、收入与提现」 |
| 6.1 信息架构 | 底部 Tab：接单 │ 任务 │ 收入 │ 我的 |
| 6.2.1 账号与认证 | 手机号登录（验证码替换）；实名认证【替换人工审核】；健康证（有效期、到期提醒）；审核状态（待审核/已通过/驳回含原因）；协议确认 |
| 6.2.2 接单（听单） | 听单开关（上下线含原因）；新单播报（语音 + 15s 倒计时）；抢单大厅（按距离/金额/时间排序）；系统派单（强提醒确认/拒绝含原因）；订单筛选；**接单上限**（后台可配） |
| 6.2.3 任务（配送单） | 任务列表 Tab（待取货/配送中/已完成/异常）；任务卡片；任务详情（商家信息/取货码、用户信息脱敏、商品件数）；到店取货（定位校验）；**联系优先 IM**；导航；送达（定位校验 + 拍照留证）；异常上报；超时预警（<5 分钟高亮）；批量配送 |
| 6.2.4 收入与结算 | 收入概览（今日/本周/本月、单量、准时率、平均单值）；收入明细（基础+距离+时段+天气+小费-罚款）；提现【替换模拟打款】；奖励；罚款扣款与申诉 |
| 6.2.5 我的 | 个人信息、我的评分、在线时长/考勤、消息通知、帮助与客服、设置（语音播报开关） |
| 14.2 页面清单（骑手端） | 24 页明细 |

---

## 前置依赖

| 依赖 | 具体依赖物 |
|---|---|
| **T07 阶段A** | `POST /rider/certification`、`GET /rider/certification`、`PUT /rider/online`、`GET /rider/orders/pending`、`GET /rider/hall`、`POST /rider/dispatch/{id}/accept|reject`、`POST /rider/deliveries/{id}/grab|pickup|delivered|anomaly` |
| **T07 阶段B** | `POST /rider/track`、`GET /rider/income/overview`、`GET /rider/income/details` |
| **T06** | 手机号验证码登录（骑手复用 C 端登录？**否** — 骑手走 `POST /auth/login` 的账号密码或 T07 的骑手验证码登录；见约束） |
| **T08** | `WS /ws`（新单强提醒、订单节点） |
| **T03** | `POST /files`（证件照、送达留证） |

---

## 数据模型（前端本地）

| Key | 内容 |
|---|---|
| `token` | 骑手 JWT（`roles` 含 `RIDER`，`riderId` 非空） |
| `online` | 听单开关本地镜像（真实状态以服务端为准，启动时校准） |
| `voiceEnabled` | 语音播报开关 |
| `lastLocation` | 最近上报坐标与时间（用于 5~10s 节流） |

---

## 接口清单

| # | 方法 | 路径 | 用途 |
|---|---|---|---|
| 1 | POST | `/api/v1/auth/login` | 骑手登录（账号密码 / 骑手手机号） |
| 2 | POST | `/api/v1/rider/certification` | 提交认证（证件照 fileId） |
| 3 | GET | `/api/v1/rider/certification` | 认证状态与驳回原因 |
| 4 | PUT | `/api/v1/rider/online` | 上线/下线（带 lng/lat/reason） |
| 5 | GET | `/api/v1/rider/orders/pending` | 系统派单待确认（含倒计时） |
| 6 | GET | `/api/v1/rider/hall` | 抢单大厅（`distanceM/sortBy`） |
| 7 | POST | `/api/v1/rider/dispatch/{deliveryId}/accept` | 接受派单 |
| 8 | POST | `/api/v1/rider/dispatch/{deliveryId}/reject` | 拒单（原因） |
| 9 | POST | `/api/v1/rider/deliveries/{id}/grab` | 抢单 |
| 10 | POST | `/api/v1/rider/deliveries/{id}/pickup` | 我已取货 |
| 11 | POST | `/api/v1/rider/deliveries/{id}/delivered` | 确认送达（lng/lat/photoFileId） |
| 12 | POST | `/api/v1/rider/deliveries/{id}/anomaly` | 异常上报 |
| 13 | POST | `/api/v1/rider/track` | 轨迹上报（批量 ≤20 点） |
| 14 | GET | `/api/v1/rider/income/overview` | 收入概览 |
| 15 | GET | `/api/v1/rider/income/details` | 收入明细 |
| 16 | POST | `/api/v1/files` | 上传证件/留证照片 |
| 17 | POST | `/api/v1/im/sessions`、`/im/sessions/{id}/messages` | 联系用户/商家（IM 优先） |
| 18 | WS | `/ws?token=` | 新单强提醒 + 状态推送 |

---

## 需要新增/修改的文件

### 工程骨架（7 个，与 T13 同构，`base` 改为 `/rider/`）

`solo-eats-uniapp-rider/{package.json, vite.config.ts, src/{manifest.json, pages.json, App.vue, main.ts, env.d.ts}}`

### 阶段A（8 页 + 6 基础 = 14 个）

| 路径 | 说明 |
|---|---|
| `src/api/http.ts`、`types.ts`、`rider.ts`、`order.ts`、`upload.ts` | 请求封装（token、401 跳登录、重试） |
| `src/stores/auth.ts`、`rider.ts` | 登录态、上线状态/接单上限 |
| `src/utils/location.ts` | 定位封装 + 上报节流（5~10s） |
| `src/utils/voice.ts` | TTS 播报（H5 用 `SpeechSynthesis`，App 用 plus，条件编译） |
| `src/pages/login/Login.vue` | 登录 |
| `src/pages/cert/Cert.vue` | 实名认证（身份证正反 + 健康证 + 有效期） |
| `src/pages/cert/CertStatus.vue` | 审核状态（待审/通过/驳回原因 + 重新提交） |
| `src/pages/home/Home.vue` | 接单首页（听单开关 + 今日概览 + 派单强提醒卡 + 待抢列表） |
| `src/pages/home/DispatchPopup.vue` | 15s 倒计时强提醒（接受/拒绝） |
| `src/pages/task/List.vue` | 任务列表（待取货/配送中/已完成/异常） |
| `src/pages/task/Detail.vue` | 任务详情（取货卡 + 送达卡 + 底部操作 + 地图） |
| `src/pages/income/Income.vue` | 收入概览 + 明细 |
| `src/pages/mine/Mine.vue` | 我的（个人信息、评分、设置、退出） |

### 阶段B（16 页，按模块）

`schedule/*`（3）、`rating/*`（2）、`anomaly/*`（2）、`im/*`（2）、`withdraw/*`（3）、`help/*`（3）、`settings/*`（1）

---

## 关键实现约束

| 项 | 约束 |
|---|---|
| **接单门槛前置** | 进首页先调 `GET /rider/certification`：`certStatus≠2` 时**强制跳认证页**，不能点听单开关 |
| **听单开关** | 开启时才申请定位权限并开始上报；关闭时停止上报并调 `/rider/online {online:false, reason}`。**不得**在关闭后继续上报（耗电 + 数据污染） |
| **轨迹上报节流** | 5~10s 一次；批量累积最多 20 点；网络失败本地缓存最多 200 点，恢复后补传；**不得**并发多条上报 |
| **派单倒计时** | 倒计时基于服务端返回的 `expireAt` 与**本地时钟差**，不信任本地 `now`；归零自动调 `reject(reason="超时未响应")` 并关闭弹窗 |
| **取货/送达校验** | 送达必须带定位；定位失败允许「无定位送达」但需拍照留证（`photoFileId` 非空）；服务端校验由 T07 负责，前端只做体验兜底 |
| **手机号脱敏** | 用户手机号严格按服务端返回的脱敏值展示；**不得**自行拼接或请求明文 |
| **联系优先 IM** | 「联系用户/商家」默认打开 IM 会话；拨号作为次级入口，且仅在服务端返回 `callEnabled=true` 时展示 |
| **超时预警** | 剩余 <5 分钟卡片变红 + 震动/提示音；超时后在任务卡展示「已超时 xx 分钟」 |
| **金额** | 收入全部按分展示转元；**不得**前端累加明细作为总额（以接口为准） |
| **提现** | P1 阶段做；仅展示「模拟打款」文案，不接真实通道 |
| **手势/适配** | 骑行场景：按钮 ≥48px 高、关键操作单手可达；深墨蓝 + 橙色强调（6.3） |
| **H5/App 差异** | 后台定位、语音播报需条件编译；H5 降级为前台定位 + 浏览器 TTS |
| **离线** | 断网时不允许「送达」（必须在线确认），可离线查看已缓存任务 |

---

## 验收标准

```bash
cd solo-eats-uniapp-rider && npm ci && npm run build:h5
# 断言：构建成功

# 链路（H5 + 后端 8080）
# 1) 骑手登录（rider1）→ 未认证 → 强制跳认证页
# 2) 提交认证（上传 2 张证件 + 健康证）→ 状态"待审核"
# 3) 后台审核通过 → 前端刷新显示"已通过"
# 4) 打开听单开关 → 弹定位授权 → 授权后 /rider/online 返回 0，首页显示"听单中"
# 5) 后台触发派单 → 15s 倒计时弹窗出现 + 语音播报 → 接受成功
# 6) 任务列表"待取货"出现该单 → 进详情 → 点"我已取货" → 状态变"配送中"
# 7) 点"确认送达" → 上传留证 → 状态变"已送达" → 任务进"已完成"
# 8) 收入页显示本单收入，明细含基础费/距离费
# 9) 抢单并发：两个骑手同时抢同一单 → 一个成功一个提示"已被抢"
# 10) 关掉听单开关 → 不再收到派单
```

---

## 禁止事项

1. 不得为前端新增后端接口（以 T07/T08 交付为准）。
2. 不得展示用户手机号明文。
3. 不得在听单关闭状态下继续上报轨迹。
4. 不得用本地时间计算派单倒计时。
5. 不得允许「送达」在没有定位也没有留证照片时通过（前端须拦截）。
6. 不得把收入明细前端求和当作总额展示。
7. 不得引入第二套 UI 框架；不得引入 Element Plus。
8. 不得在阶段A 实现排班/考勤/申诉/提现（P1 范围）。
9. 不得改动 `solo-eats-web` 与 `solo-eats-uniapp-user`。

---

## 执行记录

| 日期 | 会话范围 | 新增文件 | 构建结果 | 遗留问题 |
|---|---|---|---|---|
| _待填_ | | | | |
