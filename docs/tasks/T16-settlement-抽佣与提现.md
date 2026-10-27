# T16 settlement-抽佣规则 · 提现审核 · 资金流水（补充卡）

> 优先级：**P0（阶段A：结算单审核→模拟打款 + 提现最小闭环）/ P1（阶段B：三级抽佣规则引擎 + 资金流水 + 提现审核完整）** · 所属模块：`solo-eats-settlement`（增量改造）+ `solo-eats-app`
> 分两段执行：**阶段A（约 8 个文件）→ 阶段B（约 8 个文件）**
> 说明：本卡不在团队最初的 T01~T15 清单内，是架构师识别的**缺口**——现状只有「结算单 + 明细」，缺规格书 4.8 明确要求的**三级抽佣规则、提现审核、资金流水**。规格书 1.6 平台后台 P0 明确含「**结算单（模拟打款）**」，故拆出 P0 先行段（阶段A），确保 P0 验收（13 章「通过率 100%」）不因本卡排 P1 而缺失。

---

## 目标

**一句话**：让「抽佣多少」变成一个可配、可追溯、可复算的规则引擎，并补齐「商家/骑手提现 → 审核 → 模拟打款」与平台资金流水账。

**完成后系统能做什么**
1. 平台按「活动级 > 商家级 > 品类级 > 全局默认」命中佣金率，每单生成一条抽佣明细（含命中规则 ID 与比例）。
2. 商家/骑手可发起提现，平台审核通过/驳回，状态按 `待审核 → 审核通过 → 打款中 → 已到账 / 已驳回` 流转。
3. 平台可查看资金流水（佣金收入、结算支出、补贴支出、提现支出），并为 T11 财务报表供数。
4. 历史结算单金额可复算：给定订单，能算出 `营业额 - 抽佣 - 平台承担优惠 + 商家承担优惠 + 退款冲抵`。

---

## 规格书依据

| 章节 | 原文要点 |
|---|---|
| 4.8 资金中心 · 技术服务费（佣金） | 「按**商家 / 品类 / 活动三级**配置佣金比例、生效时间与优先级；**命中优先级：活动级 > 商家级 > 品类级 > 全局默认**」 |
| 4.8 抽佣计算明细 | 「每单抽佣基数、**命中规则**、比例与金额明细，可按商家/品类/时间导出核对」 |
| 4.8 商家结算 | 「结算周期配置（默认 T+1，可改周结/月结）、账单生成、明细（营业额 − 抽佣 − 平台承担优惠 + 商家承担优惠 + 退款冲抵，含自取订单）、导出、打款登记」【替换：模拟打款】 |
| 4.8 骑手结算 | 「配送费明细、补贴、罚款扣款、结算周期（默认日结）、结算单、打款登记」【替换：模拟打款】 |
| 4.8 提现审核 | 「商家/骑手提现申请、审核（通过/驳回）、状态流转、到账登记」【替换：模拟打款】 |
| 4.8 资金流水与报表 | 「平台流水、佣金收入、补贴支出、优惠券成本、利润概览」 |
| 8.4 结算流程 / 8.8 售后期与结算冻结 | 「售后期内订单不参与结算；已结算订单发生退款计入下一期负项冲抵」 |
| 8.9 状态机 · 提现单 | 待审核 → 审核通过 → 打款中 → 已到账 / 已驳回 |
| 技术架构 3.10 | 「结算与抽佣」；2.8 结算域 DDL（`t_settlement_bill`、`t_settlement_item`、`t_payout_order` 已存在） |

---

## 前置依赖

| 依赖 | 具体依赖物 |
|---|---|
| **T03** | `Merchant.commission_rate`（商家级费率）、`t_merchant_settlement_account`（提现收款账户） |
| **T07** | `t_rider`、`t_rider_income`（骑手收入）、`t_rider_fee_rule`（罚款/补贴）。**超时罚款数据源**：骑手配送超时由 T07 阶段B 依据 `t_delivery_order.promise_deliver_at` 判定并写入 `t_rider_income` 罚款项，本卡结算时直接聚合，**不重复判定超时** |
| **T05** | `t_category`（品类级费率） |
| **T12** | `t_promotion`（活动级费率，`promotion.extra_commission_rate` 若存在；否则本卡在 `t_commission_rule` 里以 `rule_scope=4` + `ref_id=promotionId` 承载） |
| 既有表（**不可改语义**） | `t_settlement_bill`（`uk_merchant_date`）、`t_settlement_item`、`t_rider_income`（`uk_delivery`）、`t_payout_order`、`t_order`（`settlement_status`、`settle_eligible_at`） |
| 既有类（**不可改签名**） | `SettlementBill`、`SettlementItem` 实体；`SettlementService` + `impl`；`SettlementBillMapper`、`SettlementItemMapper`；`app/controller/AdminSettlementController.java`、`MerchantSettlementController.java`；测试 `SettlementServiceImplTest` |

---

## 数据模型

迁移脚本：阶段A `V20__settlement_payout.sql`（提现列 + 结算单打款登记）；阶段B `V21__settlement_commission_fundflow.sql`（抽佣规则/记录 + 资金流水）

| 表 | 关键字段 | 索引 |
|---|---|---|
| `t_commission_rule` | id, tenant_id, **rule_scope tinyint(1全局 2品类 3商家 4活动)**, **ref_id bigint NULL**（品类ID/商家ID/活动ID）, **commission_rate decimal(6,4)**, **min_amount bigint NULL** / **max_amount bigint NULL**（订单金额区间，NULL 不限）, **effective_from**, **effective_to**, **priority int DEFAULT 0**（同 scope 内排序）, status, remark, created_at/updated_at/deleted | `idx_scope_ref(rule_scope, ref_id, status)`、`idx_effective(status, effective_from, effective_to)` |
| `t_commission_record` | id, tenant_id, **order_id bigint**, merchant_id, shop_id, category_id, **base_amount bigint**（抽佣基数=商品实付，不含配送费/打包费）, **rule_id**, **rule_scope**, **commission_rate decimal(6,4)**, **commission_amount bigint**, settlement_bill_id NULL, created_at | `uk_order(order_id)`、`idx_merchant_time(merchant_id, created_at)`、`idx_bill(settlement_bill_id)` |
| `t_fund_flow` | id, tenant_id, **flow_type tinyint(1佣金收入 2结算支出 3补贴支出 4优惠成本 5提现支出 6退款冲抵)**, **biz_type varchar(32)**, **biz_id bigint**, **direction tinyint(1入 2出)**, **amount bigint**, balance_after bigint NULL, remark, created_at | `uk_biz(flow_type, biz_type, biz_id)`、`idx_type_time(flow_type, created_at)` |
| `t_payout_apply`（补充 `t_payout_order` 的业务扩展，或直接复用 `t_payout_order` + 新列） | 若复用 `t_payout_order`：只加 `apply_no`、`account_snapshot_json`、`audit_by`、`audit_at`、`audit_remark`、`paid_at`、`fail_reason`（**只增列**） | `uk_apply_no(apply_no)`、`idx_payee(payee_type, payee_id, status)` |

> **约束**：`t_payout_order` 已有结构以 `V1__init_schema.sql` 为准；本卡使用 **Expand 方式只增列**，不改原列。若原表已满足，则**不建新表**，仅补索引。

---

## 接口清单

| # | 方法 | 路径 | 要点 | 权限 |
|---|---|---|---|---|
| 1 | GET/POST/PUT | `/api/v1/admin/commission-rules`（+`/{id}`） | `{ruleScope,refId,commissionRate,minAmount,maxAmount,effectiveFrom,effectiveTo,priority}` | `settlement:commission:*` |
| 2 | GET | `/api/v1/admin/commission-rules/resolve?merchantId=&categoryId=&promotionId=&amount=` | **规则命中试算**：返回命中的 `ruleId/scope/rate/amount` | `settlement:commission:list` |
| 3 | GET | `/api/v1/admin/commission-records` | `merchantId,categoryId,dateFrom,dateTo,pageNum,pageSize` 抽佣明细 | `settlement:commission:list` |
| 4 | GET | `/api/v1/merchant/commission-details` | 商家自查抽佣明细（按需导出用） | `MERCHANT` |
| 5 | POST | `/api/v1/merchant/payouts` | `{amount}` 商家提现申请（校验可提现余额、结算账户生效） | `MERCHANT` + `@Idempotent` |
| 6 | POST | `/api/v1/rider/payouts` | `{amount}` 骑手提现申请 | `RIDER` + `@Idempotent` |
| 7 | GET | `/api/v1/merchant/payouts`、`/api/v1/rider/payouts` | 我的提现记录 | 对应角色 |
| 8 | GET | `/api/v1/admin/payouts` | `payeeType,status,dateFrom,dateTo,pageNum,pageSize` | `settlement:payout:list` |
| 9 | POST | `/api/v1/admin/payouts/{id}/audit` | `{pass:true/false, remark}` → `待审核 → 审核通过/已驳回` | `settlement:payout:audit` |
| 10 | POST | `/api/v1/admin/payouts/{id}/pay` | 模拟打款 → `打款中` | `settlement:payout:pay` |
| 11 | POST | `/api/v1/admin/payouts/{id}/arrive` | 登记到账 → `已到账`（写 `t_fund_flow`） | `settlement:payout:pay` |
| 12 | GET | `/api/v1/admin/fund-flows` | `flowType,dateFrom,dateTo,pageNum,pageSize` 平台资金流水 | `settlement:fund:list` |
| 13 | GET | `/api/v1/admin/fund-flows/summary` | `{commissionIncome,settlementExpense,subsidyCost,payoutExpense,profit}` | `settlement:fund:list` |
| 14 | GET | `/api/v1/admin/settlements/{billId}/recalc` | 重算指定账单（校验可复算，写审计日志） | `settlement:bill:recalc` |

**新增错误码**（6xxxx 追加）：`60003 COMMISSION_RULE_NOT_FOUND`（无任何规则时用全局默认而非报错——此码仅用于按 id 查不到）、`60004 PAYOUT_AMOUNT_EXCEEDED`、`60005 PAYOUT_STATE_INVALID`、`60006 PAYOUT_ACCOUNT_REQUIRED`、`60007 SETTLE_AMOUNT_MISMATCH`。

---

## 需要新增/修改的文件

### 阶段A（P0，约 8 个：结算单审核→模拟打款 + 提现最小闭环）

| 类型 | 路径 |
|---|---|
| 新 | `solo-eats-app/src/main/resources/db/migration/V20__settlement_payout.sql`（`t_payout_order` 增补列 + 索引；结算单打款登记列） |
| 新 | `solo-eats-settlement/src/main/java/com/soloeats/settlement/domain/enums/PayoutStatus.java`（含合法流转表：待审核→审核通过/已驳回、审核通过→打款中→已到账） |
| 改 | `solo-eats-app/src/main/java/com/soloeats/app/controller/AdminSettlementController.java`（**追加** 结算单审核/打款登记端点，不改已有 `/generate`、`/stats`） |
| 新 | `solo-eats-app/src/main/java/com/soloeats/app/controller/AdminPayoutController.java`（提现审核/打款/到账） |
| 新 | `solo-eats-app/src/main/java/com/soloeats/app/controller/MerchantPayoutController.java`（含骑手提现申请，按角色分流） |
| 新 | `solo-eats-settlement/src/main/java/com/soloeats/settlement/service/PayoutService.java` + `impl`（申请/审核/打款/到账，金额校验走既有余额） |
| 新 | `solo-eats-settlement/src/test/java/com/soloeats/settlement/service/impl/PayoutServiceImplTest.java`（状态机非法流转 + 超额提现） |

> 阶段A 的抽佣仍沿用既有 `default-commission-rate` 固定费率（不改），保证 P0「结算单模拟打款」先闭环；三级抽佣规则引擎在阶段B 替换。

### 阶段B（P1，约 8 个：三级抽佣规则引擎 + 资金流水 + 提现审核完整）

| 类型 | 路径 |
|---|---|
| 新 | `solo-eats-app/src/main/resources/db/migration/V21__settlement_commission_fundflow.sql` |
| 新 | `solo-eats-settlement/src/main/java/com/soloeats/settlement/domain/entity/CommissionRule.java` |
| 新 | `solo-eats-settlement/src/main/java/com/soloeats/settlement/domain/entity/CommissionRecord.java` |
| 新 | `solo-eats-settlement/src/main/java/com/soloeats/settlement/domain/entity/FundFlow.java` |
| 新 | `solo-eats-settlement/src/main/java/com/soloeats/settlement/domain/enums/RuleScope.java`、`FlowType.java` |
| 新 | `solo-eats-settlement/src/main/java/com/soloeats/settlement/mapper/CommissionRuleMapper.java`、`CommissionRecordMapper.java`、`FundFlowMapper.java` |
| 新 | `solo-eats-settlement/src/main/java/com/soloeats/settlement/service/CommissionService.java` + `impl`（**规则命中 + 落抽佣记录**） |
| 新 | `solo-eats-settlement/src/main/java/com/soloeats/settlement/service/FundFlowService.java` + `impl` |
| 新 | `solo-eats-settlement/src/main/java/com/soloeats/settlement/service/view/`（`CommissionRuleView`、`CommissionResolveResult`、`CommissionRecordView`、`PayoutView`、`FundFlowSummary`） |
| 改 | `solo-eats-settlement/src/main/java/com/soloeats/settlement/service/impl/SettlementServiceImpl.java`（**在生成账单明细时改为调用 `CommissionService.resolveAndRecord()`**，替换阶段A 固定 `default-commission-rate` 逻辑；**返回值与异常语义不变**） |
| 新 | `solo-eats-app/src/main/java/com/soloeats/app/controller/AdminCommissionController.java` |
| 新 | `solo-eats-settlement/src/test/java/com/soloeats/settlement/service/impl/CommissionServiceImplTest.java`（优先级命中 4 组 + 生效期边界） |

---

## 关键实现约束

| 项 | 约束 |
|---|---|
| **优先级命中** | 严格 `活动级 > 商家级 > 品类级 > 全局默认`；同 scope 多条取 `priority DESC, effective_from DESC` 第一条；无任何命中时用 `app.settlement.default-commission-rate`（默认 `0.0600`）并记 `rule_id=NULL` |
| **抽佣基数** | **商品实付**（`Σ order_item.payable_amount`），**不含**配送费与打包费；自取单同样抽佣（规格书 4.8「含自取订单」），但**不计履约服务费**（P0 履约服务费恒为 0）。<br>**落地口径（2026-10-27 口径校正，以此为准）**：实现取 `t_order.goods_amount − merchant_discount`，未拆 `order_item`；打包费**不计入抽佣基数**，但仍全额计入结算营业额 |
| **可复算** | `t_commission_record` 必须落 `base_amount/rule_id/rule_scope/commission_rate/commission_amount`，满足"任意时刻可用订单数据重算出同一金额"。**金额计算一次，禁止多处重算** |
| **结算公式** | `应结 = 营业额(商品实付) − 抽佣 − 平台承担优惠 + 商家承担优惠 + 退款冲抵`，其中"退款冲抵"为**负项入账**（退款金额以负数参与累加，故公式用 `+`；若实现为减法请注释清楚），"平台/商家承担优惠"取自 `t_order_discount.bearer` 与 `t_order.platform_discount/merchant_discount`。**口径必须与规格书 4.8「营业额 − 抽佣 − 平台承担优惠 + 商家承担优惠 + 退款冲抵」一致**。<br>**落地口径（2026-10-27 校正，以此为准）**：实现为 `gross − merchantDiscount + platformDiscount − commission`。本节公式中「− 平台承担优惠 + 商家承担优惠」两处符号与实现**方向相反**：财务上平台补贴的钱商家照收（加），商家自贴的钱要扣（减），故以落地口径为准 |
| **售后期冻结** | 沿用既有 `settle_eligible_at`（`completed_at + after_sales_days`）；本卡不得改冻结逻辑，只在其上叠加抽佣明细 |
| **提现门槛** | 商家可提现余额 = `Σ已结算已到账账单 − Σ已提现(非驳回)`；骑手可提现余额 = `Σ t_rider_income − Σ已提现`；超额抛 `60004`。商家无生效结算账户抛 `60006` |
| **提现状态机** | `0待审核 → 1审核通过 → 2打款中 → 3已到账`；`0/1 → 4已驳回`；**禁止**跳级（`0→3`）。条件更新 `WHERE id=? AND status=?` |
| **幂等** | 提现申请 `@Idempotent` + 表内 `apply_no` 唯一；`pay`/`arrive` 重复调用返回当前状态而非报错 |
| **资金流水** | 每次状态变更（到账、退款冲抵）写一条 `t_fund_flow`，`uk_biz(flow_type, biz_type, biz_id)` 保证幂等；**不记录余额快照**（平台无统一账户余额，`balance_after` 留 NULL） |
| **金额精度** | 全部 `bigint` 分；`commission_rate` 用 `BigDecimal` + `setScale(4, HALF_UP)`；`commission_amount = round(base * rate)` 用 `HALF_UP` 到分 |
| **不得破坏既有** | `SettlementServiceImplTest` 断言必须继续通过；若因改为规则引擎导致默认费率用例失败，**必须**保证无规则时回落 `0.0600` 使断言成立 |
| **多租户** | 全部新表带 `tenant_id` |

---

## 验收标准

```bash
mvn -q -pl solo-eats-settlement -am compile && mvn -q -DskipTests package
mvn -q -pl solo-eats-settlement test -Dtest='SettlementServiceImplTest'   # 既有测试必须绿

# 规则命中优先级
# 配：全局 0.0600 / 品类1 0.0700 / 商家1 0.0800 / 活动9 0.0500
curl -s "http://localhost:8080/api/v1/admin/commission-rules/resolve?merchantId=1&categoryId=1&promotionId=9&amount=10000" \
  -H "Authorization: Bearer $ADMIN_TOKEN"
# 断言：命中活动级，rate=0.0500，amount=500

# 无活动时
curl -s "...?merchantId=1&categoryId=1&amount=10000"
# 断言：命中商家级 0.0800，amount=800

# 无商家规则时 → 品类级；无品类 → 全局 0.0600

# 抽佣明细可复算
curl -s "http://localhost:8080/api/v1/admin/commission-records?merchantId=1&dateFrom=2025-01-01&dateTo=2025-01-31"
# 断言：每条 base_amount*commission_rate ≈ commission_amount（±1 分内且等于 HALF_UP 结果）

# 提现超额
curl -s -X POST http://localhost:8080/api/v1/merchant/payouts -H "Authorization: Bearer $MERCHANT_TOKEN" \
  -H "Idempotency-Key: po-001" -d '{"amount":999999999}'
# 断言：code=60004

# 无结算账户
# 断言：code=60006

# 状态机跳级
curl -s -X POST http://localhost:8080/api/v1/admin/payouts/1/arrive -H "Authorization: Bearer $ADMIN_TOKEN"
# 断言：code=60005（未审核/未打款时不能直接到账）

# 正常流转 + 资金流水
# 审核 → 打款 → 到账
curl -s "http://localhost:8080/api/v1/admin/fund-flows?flowType=5" -H "Authorization: Bearer $ADMIN_TOKEN"
# 断言：新增 1 条提现支出，amount 与申请一致

# 汇总
curl -s http://localhost:8080/api/v1/admin/fund-flows/summary -H "Authorization: Bearer $ADMIN_TOKEN"
# 断言：profit = commissionIncome - settlementExpense - subsidyCost - payoutExpense
```

---

## 禁止事项

1. **不得修改** `SettlementService`/`SettlementServiceImpl` 的已有方法签名与返回类型；只能在内部替换费率来源。
2. **不得删除或修改** `SettlementServiceImplTest` 的任何断言。
3. **不得修改** `t_settlement_bill`/`t_settlement_item`/`t_rider_income`/`t_payout_order` 的既有列（只允许 `ALTER TABLE ADD COLUMN`）。
4. 不得把费率写死在代码里（规格书 9.5 明确要求后台可配）。
5. 不得修改既有 `/api/v1/admin/settlements/generate`、`/stats`、`/api/v1/merchant/settlements` 的契约。
6. 不得用 `double` 计算佣金。
7. 不得允许提现状态跳级或逆向。
8. 不得让 `settlement` 依赖 `order`/`delivery` 之外的模块（现有 `settlement → order` 方向保留；新增对 `merchant`/`rider` 的引用只能读表或通过端口接口）。
9. 不得在 P0 阶段启用履约服务费（技术架构 1.6：「履约服务费 P0 固定 0」）。

---

## 执行记录

| 日期 | 会话范围 | 新增/修改文件 | 编译结果 | 既有测试 | 遗留问题 |
|---|---|---|---|---|---|
| 2026-10-25 | T16 阶段A：结算单审核→模拟打款、商家/骑手提现最小闭环 | 新增 14 / 修改 4（共 18） | `mvn -q -DskipTests package` BUILD SUCCESS；`mvn -pl solo-eats-settlement -am test` Tests run: 16, Failures: 0, Errors: 0 | SettlementServiceImplTest 5 例全绿 | 迁移号按实测库版本改为 V19（原卡写 V20）；阶段B 未做；提现余额校验为「SUM 查询 + INSERT」两步无账户级锁，并发下仍可能超提，待阶段B 补分布式锁；t_settlement_bill 无审核意见列，remark 仅进应用日志与 t_sys_oper_log |
| 2026-10-27 | T16 阶段B：三级抽佣规则引擎（活动>商家>品类>全局）+ 抽佣明细 + 平台资金流水 + 提现并发锁补全。**本次以甲方口径裁定为准，卡内「抽佣基数不含打包费」与「结算公式 −平台 +商家」两处描述按笔误处理，未采纳** | 新增 24 / 修改 3（共 27） | `mvn -q -pl solo-eats-settlement -am compile` 0 error；`mvn -q -DskipTests package` BUILD SUCCESS（16 模块聚合未破坏） | `mvn -pl solo-eats-settlement -am test` Tests run: 27, Failures: 0, Errors: 0（SettlementServiceImplTest 5 / SettlementBillAuditTest 4 / PayoutServiceImplTest 7 / 新增 CommissionServiceImplTest 11，既有断言零改动） | **口径裁定**：①抽佣基数仍为 `goods_amount + packaging_fee − merchant_discount`（含打包费），未改成 `Σ order_item.payable_amount`，未新增 order_item 查询依赖；②结算公式仍为 `gross − merchantDiscount + platformDiscount − commission`，未改成卡内写法；③阶段B 唯一口径变更是「费率来源」——由固定 `app.settlement.default-commission-rate` 改为 `CommissionService.resolveAndRecord()`，无规则命中回落 0.0600 且 `rule_id` 记 NULL。**实施偏差**：迁移号按实测最大版本用 `V27`（原卡 V21 已被 `V21__seed_demo_shop.sql` 占用，主理人指示的 V27 已核对）；菜单 id 从 **142** 起（主理人所说 113 起与 V22 113~114 冲突，实测 V26 后最大为 141）；`PayoutStatus` 现有取值为 0/10/20/30/40（非卡内 0/1/2/3/4），沿用既有值。**遗留问题**：①`t_commission_record` 的 `rule_scope` 在「内置默认费率」场景记 NULL，前端需把 null 展示为「默认费率」；②佣金收入流水在账单生成（抽佣记账）时即入账，结算支出流水尚未在账单打款时写入（`payBill` 未接流水，避免动 `SettlementBillAuditTest` 的 mock 集），故 `/fund-flows/summary` 的 `settlementExpense` 目前恒为 0；③补贴支出(3)/优惠成本(4)/退款冲抵(6)三类流水只有枚举与写入接口，尚无业务触发点（T07/T12 退款冲抵链路未接）；④商家无结算账户抛 60006 的校验仍未接入 `apply()`（需读 `t_merchant_settlement_account`，本轮未引入该依赖）；⑤提现账户快照 `account_snapshot_json` 未写入；⑥依赖 null 兜底问题已在同日「阶段B 返工」行消除 |
| 2026-10-27 | T16 阶段B 返工（主理人 review 意见）：①删除全部依赖 null 兜底，改 `final` 字段 + `@RequiredArgsConstructor` 必需注入，并改由「真实引擎 + mock Mapper」跑通既有测试；②`apply()` 锁内补 60006 商家结算账户校验；③`payBill()` 补结算支出流水（flow_type=2） | 修改 8（2 生产 ×2 + 3 测试 + 3 Mapper/接口常量 + 任务卡） | `mvn -q -pl solo-eats-settlement -am compile` 0 error；`mvn -q -DskipTests package` BUILD SUCCESS | `mvn -q -pl solo-eats-settlement test` `Tests run: 31, Failures: 0, Errors: 0`（CommissionServiceImplTest 11 / PayoutServiceImplTest 10 / SettlementBillAuditTest 5 / SettlementServiceImplTest 5；断言零改动，测试 diff 仅 +153 行、删除 1 行且该行为 setUp 里对已删除字段 `commissionRate` 的 setField） | **返工后已消除的遗留**：①依赖 null 兜底全部删除（`grep "== null"` 复核：只剩参数校验、记录不存在、`nz()` 三类业务判断）；②`settlementExpense` 不再恒 0，`payBill` 打款成功后写 `t_fund_flow(flow_type=2, direction=2, biz_type='settlement_bill', biz_id=billId, amount=payableAmount)`，与打款**不同事务**、失败只记 error 日志不阻断打款（取舍写在方法注释里，靠 `uk_biz` 幂等可补偿重试）；③商家无生效结算账户抛 60006，校验在锁内 `doApply()` 首步，骑手不校验。**注入写法**：`SettlementServiceImplTest` 注入真实 `CommissionServiceImpl`+`FundFlowServiceImpl`（构造真实引擎、规则 Mapper 全空 → 回落 0.0600，582/300/14700/14118 断言由真实链路算出）；`SettlementBillAuditTest` 注入真实 `FundFlowServiceImpl`；`PayoutServiceImplTest` mock `DistributedLock` 让锁体同步执行 + 真实 `FundFlowServiceImpl`。**仍未做**：`/admin/settlements/{billId}/recalc`（卡内 #14）、补贴/优惠/退款冲抵三类流水触发点（待 T11 阶段B 与售后接入）、提现账户快照 `account_snapshot_json`、`rule_scope` 在默认费率时记 NULL 需前端展示为「默认费率」 |
| 2026-10-27 | **T16 阶段B 口径校正（⚠️ 本条推翻本表上文「2026-10-27 T16 阶段B」行的口径裁定①——当时采纳「基数含打包费」，本条改为「不含打包费」，以本条为准）**：抽佣基数由 `goods_amount + packaging_fee − merchant_discount` 改为 `goods_amount − merchant_discount`（剔除打包费）；`gross`（含打包费）与结算公式 `gross − merchantDiscount + platformDiscount − commission` 未动。同步修正 4 条测试断言（甲方授权，超出这 4 条仍不得改） | 修改 5（生产 1 `SettlementServiceImpl` / 测试 1 `SettlementServiceImplTest` / 文档 3：本卡 + T17 + `docs/PROGRESS.md`） | `mvn -q -pl solo-eats-settlement -am compile` 0 error；`mvn -q -DskipTests package` BUILD SUCCESS | `mvn -pl solo-eats-settlement test` Tests run: 66, Failures: 0, Errors: 0（CommissionEdgeCaseTest 13 / CommissionServiceImplTest 11 / FundFlowEdgeCaseTest 10 / PayoutEdgeCaseTest 10 / PayoutServiceImplTest 10 / SettlementBillAuditTest 5 / SettlementPayEdgeCaseTest 2 / **SettlementServiceImplTest 5**） | **新断言值**：`commissionBase=14500`、`commissionAmount=870`、`payableAmount=14130`、佣金明细合计 `−870`，`grossAmount=15200` 不变（order1: base 10000−500=9500，抽佣 570，结算 10200−500+300−570=9430；order2: base 5000，抽佣 300，结算 4700）。`FundFlowEdgeCaseTest` 的 882/14118 为资金汇总公式的任意假数据，未动。**提现锁核查**（`PayoutServiceImpl`，本轮未改代码）：①key = `payout:apply:{subjectType}:{subjectId}` 主体粒度；②账户校验/查余额/超额校验/insert 全部在 `doApply()` 内由 `withSubjectLock` 包裹；③`DistributedLock#executeWithLock` 在 `tryLock` 失败时抛 `BizException(LOCK_TIMEOUT=40005「获取锁超时」)`，无静默继续；④wait 3s、lease 10s，锁体仅 3 次 SUM 查询 + 1 次 insert，10s 余量充足。**遗留项**：①`/admin/settlements/{billId}/recalc` 端点未做；②`t_sys_menu.component` 与 `solo-eats-web` 静态路由未打通——已种 `finance/payout/index`(V19)、`finance/commissionRule/index`、`finance/commissionRecord/index`、`finance/fundFlow/index`(V27)，但 `solo-eats-web/src/views/` 无 `finance/` 目录、`router/index.ts` 未用 `import.meta.glob`，点菜单落 `NotFoundView`，已登记为 T17 批次4 必选前置任务 |
