# T03 merchant-商家入驻审核 + MinIO 文件存储

> 优先级：**P0** · 所属模块：`solo-eats-merchant`（新建）+ `solo-eats-common` + `solo-eats-app`
> 分两段：**阶段A（MinIO 文件服务，被 T03/T05/T09/T10/T13 共用）→ 阶段B（入驻+资质+审核+结算账户）**

---

## 目标

**一句话**：打通「商家提交入驻 → 上传证照 → 平台审核 → 自动生成商家管理员账号 → 登记结算账户」这条招商闭环，并把文件存储能力做成全站公共件。

**完成后系统能做什么**
1. 任意模块可调用 `POST /api/v1/files` 上传图片/PDF 到 MinIO，拿到可公网预览的 URL；前端可用预签名直传。
2. 未注册商家可以用手机号提交入驻申请（草稿→提交），上传营业执照/食品经营许可证/身份证/门店照片。
3. 平台招商角色看到待审列表，点通过后：`t_merchant` 置为已通过、自动创建 `t_sys_user`（`user_type=2`）并绑定 `MERCHANT_MANAGER` 角色、写审核流水。
4. 驳回带原因，商家可修改资料再次提交。
5. 商家可维护结算账户（开户名/开户行/账号/合同附件），供 T16 结算打款使用。

---

## 规格书依据

| 章节 | 原文要点 |
|---|---|
| 4.2 商家招商 · 入驻申请 | 「申请列表、资料查看（营业执照、食品经营许可证、法人身份证、门店照片）、审核通过/驳回（含原因）、备注、通知商家」【保留】（替换：资质人工审核） |
| 4.2 商家管理 | 「商家列表、详情、启用/停用、商家等级、经营品类、联系人、**抽佣比例**、经营数据概览、账号管理」 |
| 4.2 商家结算账户 | 「账户信息、开户信息、合同附件、生效期」（替换：仅登记，不做真实打款） |
| 4.2 商家后台账号 | 「商家主账号、子账号与角色分配」 |
| 2.5 核心领域模型 | 「商家(merchant)、品牌、门店(shop)、资质文件、结算账户；商家 1:N 门店；门店 1:1 结算账户」 |
| 1.5 / 2.4 | MinIO【保留】：「图片/文件 → MinIO（自建）」 |
| 4.8 佣金规则 | 「按商家 / 品类 / 活动三级配置佣金比例…命中优先级：活动级 > 商家级 > 品类级 > 全局默认」→ 商家级费率落在 `t_merchant.commission_rate` |

---

## 前置依赖

| 依赖 | 具体依赖物 |
|---|---|
| **T01 阶段A/B** | `SysUser`/`SysRole`/`SysUserRole` Mapper 与 `SysUserService.create()`（审核通过时建账号）；`PasswordHasher` |
| **T01 阶段C** | `@RequirePermission`（`merchant:audit` 等） |
| **T02 阶段A** | `TenantContext`、`t_sys_tenant`（商家租户 1:1） |
| 既有配置 | `application.yml` 已有 `app.minio.{endpoint,access-key,secret-key,bucket,secure}`，**但没有任何 Java 代码使用它** |
| 既有类 | `common/api/ApiResponse`、`BizException`、`ErrorCode`、`Idempotent`、`TraceContext` |

---

## 数据模型

迁移脚本：`V7__merchant.sql`

| 表 | 关键字段 | 索引 |
|---|---|---|
| `t_merchant` | id, tenant_id, merchant_no varchar(32), merchant_name, brand_name, category_id（平台品类，T05 建表后启用，先存 bigint NULL）, contact_name, contact_phone, contact_email, business_license_no, legal_person, legal_id_card_cipher（加密）, province, city, district, address, status tinyint(0草稿 1待审核 2已通过 3已驳回 4已停用), audit_by, audit_at, audit_remark, **commission_rate decimal(6,4) NOT NULL DEFAULT 0.0600**, level tinyint, opened_at, remark, version, created_at/updated_at/deleted | `uk_merchant_no(merchant_no)`、`idx_status(status, created_at)`、`idx_phone(contact_phone)` |
| `t_merchant_qualification` | id, tenant_id, merchant_id, qual_type tinyint(1营业执照 2食品经营许可证 3法人身份证正面 4法人身份证反面 5门店门头照 6店内照 7其他), file_id, file_url, file_name, expire_at, status tinyint(0待审 1通过 2驳回), audit_remark, created_at/updated_at/deleted | `idx_merchant_type(merchant_id, qual_type)` |
| `t_merchant_audit_log` | id, tenant_id, merchant_id, from_status, to_status, action varchar(32)(SUBMIT/APPROVE/REJECT/ENABLE/DISABLE), operator_id, operator_name, remark, created_at | `idx_merchant_time(merchant_id, created_at)` |
| `t_merchant_settlement_account` | id, tenant_id, merchant_id, account_name, bank_name, bank_branch, bank_account_no_cipher, alipay_account, contract_file_id, contract_file_url, effective_from date, effective_to date, is_default tinyint, status tinyint(0待审 1生效 2停用), created_at/updated_at/deleted | `idx_merchant(merchant_id)`、`uk_merchant_default(merchant_id, is_default)`（应用层保证唯一） |
| `t_file` | id, tenant_id, file_key varchar(255)（MinIO object key）, bucket, original_name, content_type, size_bytes bigint, biz_type varchar(32)(MERCHANT_QUAL/PRODUCT/AVATAR/TICKET/CONTENT/RECEIPT), biz_id, uploader_id, uploader_type, url varchar(512), status tinyint(0正常 1已删除), created_at | `uk_file_key(file_key)`、`idx_biz(biz_type, biz_id)` |

**枚举补充**（写进 `t_sys_dict_type`）：`merchant_status`、`qual_type`、`settlement_account_status`、`file_biz_type`。

---

## 接口清单

| # | 方法 | 路径 | 请求/响应要点 | 权限 |
|---|---|---|---|---|
| 1 | POST | `/api/v1/files` | `multipart/form-data: file, bizType` → `{fileId,url,fileKey,sizeBytes}`；校验 MIME 白名单 `image/jpeg,png,webp,pdf`，≤10MB | 登录即可；**申请阶段（凭 `applyToken`）亦放行**（见下方「关键实现约束 → 文件上传鉴权」） |
| 2 | POST | `/api/v1/files/presign` | `{fileName,contentType,bizType}` → `{uploadUrl,fileKey,expiresIn}`（MinIO 预签名 PUT） | 登录即可；**申请阶段（凭 `applyToken`）亦放行** |
| 3 | DELETE | `/api/v1/files/{fileId}` | 逻辑删除 + 异步删对象 | 本人或 ADMIN |
| 4 | POST | `/api/v1/merchant/apply` | 免登录；`{merchantName,brandName,contactName,contactPhone,smsCode,categoryId}` → `{applyId,applyNo}` | 白名单 + 限流 5/分/IP |
| 5 | PUT | `/api/v1/merchant/apply/{applyId}` | 补充资料（`businessLicenseNo,legalPerson,legalIdCard,province,city,district,address`） | 凭 `applyToken`（申请时返回） |
| 6 | POST | `/api/v1/merchant/apply/{applyId}/qualifications` | `{qualType,fileId}` 新增资质 | 凭 `applyToken` |
| 7 | POST | `/api/v1/merchant/apply/{applyId}/submit` | 提交审核（草稿→待审核），校验必传资质齐全 | 凭 `applyToken` |
| 8 | GET | `/api/v1/merchant/apply/{applyId}` | 查询进度 + 驳回原因 | 凭 `applyToken` |
| 9 | GET | `/api/v1/admin/merchants` | `status,keyword,categoryId,pageNum,pageSize` → `PageResult<MerchantView>` | `merchant:list` |
| 10 | GET | `/api/v1/admin/merchants/{id}` | 详情（含资质列表、审核流水） | `merchant:detail` |
| 11 | POST | `/api/v1/admin/merchants/{id}/approve` | `{commissionRate, merchantLevel, adminUsername, initialPassword, remark}` → 建账号 + 置已通过 | `merchant:audit` |
| 12 | POST | `/api/v1/admin/merchants/{id}/reject` | `{remark}`（必填，≥5 字） | `merchant:audit` |
| 13 | PUT | `/api/v1/admin/merchants/{id}/status` | `{status}`（启用/停用） | `merchant:update` |
| 14 | PUT | `/api/v1/admin/merchants/{id}/commission` | `{commissionRate}`（0.0000~0.5000） | `merchant:update`（财务角色） |
| 15 | GET | `/api/v1/merchant/profile` | 当前商家资料（token 的 `merchantId`） | `MERCHANT` |
| 16 | PUT | `/api/v1/merchant/profile` | 修改联系人/地址/邮箱（不允许改 `merchant_no/status/commission_rate`） | `MERCHANT` |
| 17 | GET | `/api/v1/merchant/settlement-accounts` | 结算账户列表 | `MERCHANT` |
| 18 | POST | `/api/v1/merchant/settlement-accounts` | `{accountName,bankName,bankBranch,bankAccountNo,alipayAccount,contractFileId,effectiveFrom,effectiveTo}` | `MERCHANT` |
| 19 | PUT | `/api/v1/admin/merchant-accounts/{id}/audit` | `{status}` 结算账户审核 | `merchant:audit` |

**新增错误码**（`ErrorCode` 追加，7xxxx 段）：`70001 MERCHANT_NOT_FOUND`、`70002 MERCHANT_STATUS_INVALID`、`70003 MERCHANT_QUALIFICATION_MISSING`、`70004 FILE_UPLOAD_FAILED`、`70005 FILE_TYPE_NOT_ALLOWED`、`70006 MERCHANT_APPLY_FORBIDDEN`。

> 说明：`70004`/`70005` 是**文件服务公共错误码**（T03/T05/T09/T10/T13 共用），按 README 分段本应归 1xxxx 系统段；为降低对已引用的改动面，本卡沿用 7xxxx 码值，并在 `ErrorCode` 中标注 `// 公共文件服务，建议后续迁移 1xxxx` 注释。**后续新增文件类错误码一律用 1xxxx**。

---

## 需要新增/修改的文件

### 阶段A（10 个，MinIO）

| 类型 | 路径 |
|---|---|
| 新 | `pom.xml`（`<modules>` 增 `solo-eats-merchant`；`dependencyManagement` 增坐标） |
| 新 | `solo-eats-merchant/pom.xml`（依赖 `solo-eats-common`、`solo-eats-system`、`mybatis-plus`、`minio`、`lombok`） |
| 改 | `solo-eats-app/pom.xml`（依赖 `solo-eats-merchant`） |
| 新 | `solo-eats-app/src/main/resources/db/migration/V7__merchant.sql`（含 `t_file` 与 4 张商家表） |
| 新 | `solo-eats-common/src/main/java/com/soloeats/common/file/FileStoragePort.java`（`upload/delete/presign` 三方法） |
| 新 | `solo-eats-common/src/main/java/com/soloeats/common/file/MinioProperties.java`（`@ConfigurationProperties("app.minio")`） |
| 新 | `solo-eats-common/src/main/java/com/soloeats/common/file/MinioStorageAdapter.java`（实现端口，`MinioClient` 单例，启动时 `bucketExists` 否则 `makeBucket`） |
| 新 | `solo-eats-common/src/main/java/com/soloeats/common/file/FileTypeValidator.java`（MIME + 后缀 + 大小校验） |
| 新 | `solo-eats-app/src/main/java/com/soloeats/app/controller/FileController.java` |
| 新 | `solo-eats-app/src/main/java/com/soloeats/app/config/MinioConfig.java`（注册 `MinioClient` Bean，`@EnableConfigurationProperties(MinioProperties.class)`；启动时确保 bucket 存在） |

### 阶段B（15 个，入驻审核）

| 类型 | 路径 |
|---|---|
| 新 | `solo-eats-merchant/src/main/java/com/soloeats/merchant/domain/entity/Merchant.java`、`MerchantQualification.java`、`MerchantAuditLog.java`、`MerchantSettlementAccount.java`、`FileRecord.java`（5） |
| 新 | `solo-eats-merchant/src/main/java/com/soloeats/merchant/domain/enums/MerchantStatus.java`、`QualType.java`、`AuditAction.java`（3） |
| 新 | `solo-eats-merchant/src/main/java/com/soloeats/merchant/mapper/` 下 5 个 Mapper |
| 新 | `solo-eats-merchant/src/main/java/com/soloeats/merchant/service/MerchantApplyService.java` + `impl` |
| 新 | `solo-eats-merchant/src/main/java/com/soloeats/merchant/service/MerchantAdminService.java` + `impl` |
| 新 | `solo-eats-merchant/src/main/java/com/soloeats/merchant/service/MerchantProfileService.java` + `impl`（含结算账户） |
| 新 | `solo-eats-merchant/src/main/java/com/soloeats/merchant/service/command/`（`ApplyMerchantCommand`、`AuditMerchantCommand`、`SaveSettlementAccountCommand`） |
| 新 | `solo-eats-merchant/src/main/java/com/soloeats/merchant/service/view/`（`MerchantView`、`MerchantDetailView`、`ApplyResult`、`QualificationView`、`SettlementAccountView`） |
| 新 | `solo-eats-app/src/main/java/com/soloeats/app/controller/MerchantApplyController.java` |
| 新 | `solo-eats-app/src/main/java/com/soloeats/app/controller/AdminMerchantController.java` |
| 新 | `solo-eats-app/src/main/java/com/soloeats/app/controller/MerchantProfileController.java` |
| 新 | `solo-eats-merchant/src/test/java/com/soloeats/merchant/service/impl/MerchantAdminServiceImplTest.java` |

---

## 关键实现约束

| 项 | 约束 |
|---|---|
| **状态机** | 商家状态严格按 `0草稿 → 1待审核 → 2已通过 | 3已驳回`；`3已驳回 → 1待审核` 允许重提；`2已通过 → 4已停用 → 2已通过` 允许。**禁止** `0 → 2` 跳过审核。用条件更新 `WHERE id=? AND status=?`，不匹配抛 `70002` |
| **审核事务** | `approve()` 是**一个事务**：更新 `t_merchant` → 插入 `t_merchant_audit_log` → 调用 `SysUserService.create()` 建管理员账号 → 绑定 `MERCHANT_MANAGER` 角色 → 插入商家租户 `t_sys_tenant`（`tenant_type=2`）。任一步失败整体回滚 |
| **账号唯一** | 管理员用户名默认取 `merchant_no` 小写；重名自动追加 `_2`、`_3`（最多 5 次，仍冲突抛 `PARAM_INVALID`） |
| **文件** | 对象 key 规范 `{bizType}/{yyyyMM}/{uuid}.{ext}`；**不覆盖同名**；上传后必须落 `t_file` 表；删除先逻辑删表再异步删对象（失败只告警） |
| **文件安全** | MIME 白名单 + 魔数校验（`jpg/png/webp/pdf` 头字节）；文件名校验去路径穿越（`../`）；单文件 ≤10MB（`application.yml` 已配）；**禁止**把 `access-key/secret-key` 返回给前端 |
| **预签名** | 预签名有效期 10 分钟；`contentType` 必须与白名单匹配；预签名成功后前端直传，后端不中转 |
| **公开访问** | bucket 保持私有；返回的 `url` 是**后端代理地址** `GET /api/v1/files/{fileId}/raw`（走鉴权或短期签名），避免 MinIO 端口暴露。P0 可简化为 bucket 设为 `download` 策略，但**必须在任务卡执行记录里写明取舍** |
| **敏感字段** | `legal_id_card`、`bank_account_no` 用 AES-GCM 加密（`app.crypto.pii-key` 环境变量）；接口返回脱敏（身份证 `110***********1234`、卡号 `**** **** **** 1234`） |
| **入驻申请鉴权** | 入驻发生在"还没有账号"之前，用 `applyToken`（一次性 JWT，claim `applyId`，有效期 24h，签发时返回）而非用户 token；**不得**开放无鉴权的 `PUT /apply/{id}` |
| **文件上传鉴权** | `/api/v1/files`、`/files/presign` 默认「登录即可」，但**申请阶段没有用户账号**。因此这两个接口必须**额外接受 `applyToken`**（请求头 `Authorization: Bearer <applyToken>` 或 `X-Apply-Token`）：当令牌 `aud/type=apply` 且 `applyId` 有效时放行，`t_file.biz_id` 绑定到 `applyId`、`biz_type=MERCHANT_QUAL`、`uploader_type=APPLY`；否则走用户 token 鉴权。**未实现此放行前，接口 6「凭 applyToken 传 fileId」的资质上传链路走不通** |
| **手机号验证码** | P0 复用 T06 的 `smsCode` 机制；若 T06 尚未完成，本卡先允许 `smsCode = "000000"`（`app.auth.mock-sms-enabled=true`）并在代码标 `TODO(T06)` |
| **幂等** | `POST /merchant/apply`、`POST /admin/merchants/{id}/approve` 必须加 `@Idempotent`；`approve` 二次调用返回已有结果而非 500 |
| **抽佣率边界** | `0.0000 ≤ commission_rate ≤ 0.5000`，入库前 `setScale(4, RoundingMode.HALF_UP)`；默认取 `app.settlement.default-commission-rate` |
| **多租户** | `t_merchant`/`t_merchant_*`/`t_file` 全部带 `tenant_id`，由 T02 的拦截器自动填充 |
| **通知** | 审核结果通知商家（站内信）依赖 T08，本卡先留 `MerchantNotifyPort` 空实现 + `TODO(T08)` |

---

## 验收标准

```bash
mvn -q -pl solo-eats-merchant -am compile
mvn -q -DskipTests package

# 1) 文件上传
curl -s -X POST http://localhost:8080/api/v1/files \
  -H "Authorization: Bearer $TOKEN" \
  -F "file=@./docs/assets/p011_001.png" -F "bizType=MERCHANT_QUAL"
# 断言：code=0；url 可 GET 到 200

# 2) 非法类型
curl -s -X POST http://localhost:8080/api/v1/files -H "Authorization: Bearer $TOKEN" \
  -F "file=@./pom.xml" -F "bizType=MERCHANT_QUAL"
# 断言：code=70005

# 3) 入驻申请
curl -s -X POST http://localhost:8080/api/v1/merchant/apply -H 'Content-Type: application/json' \
  -d '{"merchantName":"示例餐饮","contactName":"张三","contactPhone":"13800000001","smsCode":"000000"}'
# 断言：code=0；返回 applyId + applyToken

# 4) 未传资质就提交 → 失败
curl -s -X POST http://localhost:8080/api/v1/merchant/apply/1/submit -H "X-Apply-Token: $APPLY_TOKEN"
# 断言：code=70003

# 5) 资质齐全后提交 → 待审核
# 6) 审核通过 → 生成账号
curl -s -X POST http://localhost:8080/api/v1/admin/merchants/1/approve -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"commissionRate":0.0600,"adminUsername":"demo-merchant","initialPassword":"Demo@1234"}'
# 断言：code=0；随后可用 demo-merchant 登录成功；t_merchant.status=2

# 7) 跳过审核直接通过 → 失败
curl -s -X POST http://localhost:8080/api/v1/admin/merchants/999/approve -H "Authorization: Bearer $ADMIN_TOKEN"
# 断言：code=70001 或 70002

# 8) 幂等
# 同一 Idempotency-Key 重复 approve → 返回首次结果，不重复建账号
```

---

## 禁止事项

1. **不得修改 `t_sku`/`t_order` 等既有表**；`t_merchant.category_id` 在 T05 建 `t_category` 前只存值不做外键语义。
2. **不得把 MinIO 凭证或预签名 URL 暴露到日志/接口响应**。
3. 不得让 `solo-eats-common` 依赖 `solo-eats-merchant`（依赖方向只能 merchant → common）。
4. **不得**在审核通过时把初始密码明文返回给前端之外的任何地方；接口只返回一次，其后不可再查。
5. 不得用 `@Transactional` 包裹文件上传（对象存储是外部调用，必须在事务**之外**或事务后）。
6. 不得删除 `t_merchant_audit_log` 的任何记录（审计表不做逻辑删除）。
7. 不得改写 `t_file` 已上传的 `file_key`（对象存储不可变，换图必须新建记录）。
8. 不得让入驻申请接口可查询他人申请（`applyToken` 的 `applyId` 必须与路径一致）。
9. 不得在本卡改动 `solo-eats-product`/`order`/`settlement`/`delivery`。
10. 不得为图省事把 `commission_rate` 写死在 Java 常量里（规格书 9.5 明确要求"业务规则后台可配"）。

---

## 执行记录

| 日期 | 会话范围 | 新增/修改文件 | 编译结果 | 遗留问题 |
|---|---|---|---|---|
| 2026-10-22 | T03 阶段A+B | 修改 6：`pom.xml`、`solo-eats-common/pom.xml`、`solo-eats-app/pom.xml`、`ErrorCode.java`、`application.yml`、`.env.example`；新增 59：`solo-eats-merchant` 全模块（pom、5 实体、3 枚举、5 Mapper、5 Service 接口 + 5 实现、6 Command、9 View、4 support、2 测试，共 45）、`V7__merchant.sql`、common `file/` 4 类、app `MinioConfig` + 4 Controller + 4 DTO | `mvn -q -pl solo-eats-merchant -am compile` 0 error；`mvn -q -pl solo-eats-app -am compile` 0 error；`mvn -q -DskipTests package` BUILD SUCCESS；`MerchantStatusTest` 3 + `MerchantAdminServiceImplTest` 4 全部通过 | 见下方「实现取舍与遗留」 |

### 实现取舍与遗留

1. **商家登录租户**：现有 `SysUserService.create()` / `AuthServiceImpl` 固定 `tenant_id=1`，因此管理员账号与 `t_merchant.tenant_id` 均回填为账号实际登录租户 `1`；`t_sys_tenant(tenant_type=2)` 行已按审核事务创建并绑定 `merchant_id`，但暂不作为登录租户，待 T02 租户登录改造后统一切换。若直接回填新租户 ID，商家登录后会被 `TenantLineInnerInterceptor` 过滤掉自己的商家行。
2. **`MERCHANT_MANAGER` 角色**：按 `role_code` 通过 `SysRoleService.page` 查询，未硬编码角色 ID；商家自助接口同时接受 `MERCHANT`（mock token）与 `MERCHANT_MANAGER`（真实账号）。既有 `MerchantSkuController` / `MerchantOrderController` 仍只声明 `MERCHANT`，真实商家账号访问会 403，属 T04/T05 需统一处理的既有问题。
3. **文件访问**：bucket 保持私有；`t_file.url` 只存后端代理地址 `/api/v1/files/{id}/raw`。`POST /files`、`/files/presign` 接受用户 token 或 `X-Apply-Token`（兼容 `Authorization: Bearer <applyToken>`）；申请阶段 `biz_id=applyId`、`uploader_type=APPLY`，资质绑定会校验文件归属。`GET /files/{id}/raw` 额外接受 `?token=`，便于 `<img>` 直接加载；未引入短期签名 URL。
4. **预签名**：只返回 `uploadUrl/fileKey/expiresIn`，不落 `t_file`（接口未定义 fileId）；直传模块需在后续调用中补登记。MinIO 侧不在预签名阶段强制 `Content-Type`，仅后端白名单 + 后缀校验。
5. **幂等**：`POST /merchant/apply` 60s 防重；`approve` 5s 并发防重，窗口外重复调用由 service 的「已通过直接返回详情」兜底，不会重复建账号/租户。公共 `IdempotentAspect` 不支持响应回放，窗口内重复请求返回 `40004`。
6. **敏感字段**：`legal_id_card`、`bank_account_no` 使用 AES-GCM（`PII_KEY`）加密，返回脱敏；`PII_KEY` 未配置时拒绝加解密（不会明文落库）。`contact_phone` 按本卡约定以业务联系方式明文存储并建索引。
7. **MinIO 启动**：`MinioConfig` 初始化 bucket 失败只告警，不阻断应用启动；上传失败返回 `70004`。
8. **通知**：`MerchantNotifyPort` 空实现 + `TODO(T08)`；接入站内信后应改为事务提交后异步发送。
9. **依赖**：MinIO 8.6.0 的 okhttp 5.1.0 在 Maven 下是 KMP 元数据包，补充了 `okhttp-jvm` 并将 `kotlin.version` 提升至 2.2.0（`okio-jvm 3.15.0` / `okhttp-jvm 5.1.0` 的运行时要求）。
