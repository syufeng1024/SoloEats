# T16 settlement-抽佣规则 · 提现审核 · 资金流水（补充卡）

> 优先级：**P1** · 所属模块：`solo-eats-settlement`（增量改造）+ `solo-eats-app`
> 单段执行（约 16 个文件）
> 说明：本卡不在团队最初的 T01~T15 清单内，是架构师识别的**缺口**——现状只有「结算单 + 明细」，缺规格书 4.8 明确要求的**三级抽佣规则、提现审核、资金流水**。

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
| **T07** | `t_rider`、`t_rider_income`（骑手收入）、`t_rider_fee_rule`（罚款/补贴） |
| **T05** | `t_category`（品类级费率） |
| **T12** | `t_promotion`（活动级费率，`promotion.extra_commission_rate` 若存在；否则本卡在 `t_commission_rule` 里以 `rule_scope=3` + `ref_id=promotionId` 承载） |
| 既有表（**不可改语义**） | `t_settlement_bill`（`uk_merchant_date`）、`t_settlement_item`、`t_rider_income`（`uk_delivery`）、`t_payout_order`、`t_order`（`settlement_status`、`settle_eligible_at`） |
| 既有类（**不可改签名**） | `SettlementBill`、`SettlementItem` 实体；`SettlementService` + `impl`；`SettlementBillMapper`、`SettlementItemMapper`；`app/controller/AdminSettlementController.java`、`MerchantSettlementController.java`；测试 `SettlementServiceImplTest` |

---

## 数据模型

迁移脚本：`V20__settlement_commission_payout.sql`

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

| 类型 | 路径 |
|---|---|
| 新 | `solo-eats-app/src/main/resources/db/migration/V20__settlement_commission_payout.sql` |
| 新 | `solo-eats-settlement/src/main/java/com/soloeats/settlement/domain/entity/CommissionRule.java` |
| 新 | `solo-eats-settlement/src/main/java/com/soloeats/settlement/domain/entity/CommissionRecord.java` |
| 新 | `solo-eats-settlement/src/main/java/com/soloeats/settlement/domain/entity/FundFlow.java` |
| 新 | `solo-eats-settlement/src/main/java/com/soloeats/settlement/domain/enums/RuleScope.java`、`FlowType.java`、`PayoutStatus.java`（含合法流转表） |
| 新 | `solo-eats-settlement/src/main/java/com/soloeats/settlement/mapper/CommissionRuleMapper.java`、`CommissionRecordMapper.java`、`FundFlowMapper.java` |
| 新 | `solo-eats-settlement/src/main/java/com/soloeats/settlement/service/CommissionService.java` + `impl`（**规则命中 + 落抽佣记录**） |
| 新 | `solo-eats-settlement/src/main/java/com/soloeats/settlement/service/PayoutService.java` + `impl`（申请/审核/打款/到账） |
| 新 | `solo-eats-settlement/src/main/java/com/soloeats/settlement/service/FundFlowService.java` + `impl` |
| 新 | `solo-eats-settlement/src/main/java/com/soloeats/settlement/service/view/`（`CommissionRuleView`、`CommissionResolveResult`、`CommissionRecordView`、`PayoutView`、`FundFlowSummary`） |
| 改 | `solo-eats-settlement/src/main/java/com/soloeats/settlement/service/impl/SettlementServiceImpl.java`（**在生成账单明细时改为调用 `CommissionService.resolveAndRecord()`**，替换原固定 `default-commission-rate` 逻辑；**返回值与异常语义不变**） |
| 改 | `solo-eats-app/src/main/java/com/soloeats/app/controller/AdminSettlementController.java`（**追加** 3 个端点：commission-rules / payouts / fund-flows；不改已有 `/generate`、`/stats`） |
| 新 | `solo-eats-app/src/main/java/com/soloeats/app/controller/AdminCommissionController.java` |
| 新 | `solo-eats-app/src/main/java/com/soloeats/app/controller/AdminPayoutController.java` |
| 新 | `solo-eats-app/src/main/java/com/soloeats/app/controller/MerchantPayoutController.java`（含骑手提现，按角色分流） |
| 新 | `solo-eats-settlement/src/test/java/com/soloeats/settlement/service/impl/CommissionServiceImplTest.java`（优先级命中 4 组 + 生效期边界） |
| 新 | `solo-eats-settlement/src/test/java/com/soloeats/settlement/service/impl/PayoutServiceImplTest.java`（状态机非法流转 + 超额提现） |

---

## 关键实现约束

| 项 | 约束 |
|---|---|
| **优先级命中** | 严格 `活动级 > 商家级 > 品类级 > 全局默认`；同 scope 多条取 `priority DESC, effective_from DESC` 第一条；无任何命中时用 `app.settlement.default-commission-rate`（默认 `0.0600`）并记 `rule_id=NULL` |
| **抽佣基数** | **商品实付**（`Σ order_item.payable_amount`），**不含**配送费与打包费；自取单同样抽佣（规格书 4.8「含自取订单」），但**不计履约服务费**（P0 履约服务费恒为 0） |
| **可复算** | `t_commission_record` 必须落 `base_amount/rule_id/rule_scope/commission_rate/commission_amount`，满足"任意时刻可用订单数据重算出同一金额"。**金额计算一次，禁止多处重算** |
| **结算公式** | `应结 = 营业额(商品实付) − 抽佣 − 平台承担优惠 + 商家承担优惠 − 退款冲抵`，其中"平台/商家承担优惠"取自 `t_order_discount.bearer` 与 `t_order.platform_discount/merchant_discount` |
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
| _待填_ | | | | | |
