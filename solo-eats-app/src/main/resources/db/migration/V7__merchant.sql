-- ============================================================
-- 数创星光外卖 T03：商家入驻审核 + 文件记录
-- Flyway V7 / MySQL 8.0 / utf8mb4
-- 说明：
--   1. 本脚本可重复执行（CREATE TABLE IF NOT EXISTS + 种子 INSERT ... ON DUPLICATE KEY UPDATE id = id）。
--   2. t_merchant / t_merchant_* / t_file 全部带 tenant_id，由 TenantLineInnerInterceptor
--      自动读写；SQL 与业务代码都不手写 tenant_id 条件。
--   3. legal_id_card_cipher / bank_account_no_cipher 只允许写入 AES-GCM 密文，禁止明文。
--   4. 金额无关；commission_rate 为 decimal(6,4)，取值 0.0000~0.5000。
--   5. 结算账户默认账户唯一性由应用层在事务内保证：不使用
--      UNIQUE(merchant_id, is_default) —— 该索引会让一个商家只能存在一条非默认账户。
--   6. 时间为 Asia/Shanghai。
-- ============================================================

SET NAMES utf8mb4;
SET time_zone = '+08:00';

-- ------------------------------------------------------------
-- 1. 商家主表
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS t_merchant (
  id                  bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '商家ID',
  tenant_id           bigint unsigned NOT NULL DEFAULT 1 COMMENT '租户ID',
  merchant_no         varchar(32) NOT NULL COMMENT '商家编号',
  merchant_name       varchar(128) NOT NULL COMMENT '商家名称',
  brand_name          varchar(128) NULL COMMENT '品牌名称',
  category_id         bigint unsigned NULL COMMENT '平台品类（T05 建 t_category 前只存值）',
  contact_name        varchar(64) NOT NULL COMMENT '联系人',
  contact_phone       varchar(32) NOT NULL COMMENT '联系电话',
  contact_email       varchar(128) NULL COMMENT '联系邮箱',
  business_license_no varchar(64) NULL COMMENT '营业执照号',
  legal_person        varchar(64) NULL COMMENT '法人姓名',
  legal_id_card_cipher varchar(512) NULL COMMENT '法人身份证密文（AES-GCM）',
  province            varchar(64) NULL COMMENT '省',
  city                varchar(64) NULL COMMENT '市',
  district            varchar(64) NULL COMMENT '区/县',
  address             varchar(255) NULL COMMENT '详细地址',
  status              tinyint NOT NULL DEFAULT 0 COMMENT '0草稿 1待审核 2已通过 3已驳回 4已停用',
  audit_by            bigint unsigned NULL COMMENT '审核人账号ID',
  audit_at            datetime(3) NULL COMMENT '审核时间',
  audit_remark        varchar(512) NULL COMMENT '审核备注/驳回原因',
  commission_rate     decimal(6,4) NOT NULL DEFAULT 0.0600 COMMENT '抽佣比例 0.0000~0.5000',
  level               tinyint NOT NULL DEFAULT 1 COMMENT '商家等级',
  opened_at           datetime(3) NULL COMMENT '开通时间',
  remark              varchar(255) NULL COMMENT '备注',
  version             int unsigned NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
  created_at          datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at          datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  deleted             tinyint NOT NULL DEFAULT 0 COMMENT '0正常 1删除',
  PRIMARY KEY (id),
  UNIQUE KEY uk_merchant_no (merchant_no),
  KEY idx_status (status, created_at),
  KEY idx_phone (contact_phone),
  KEY idx_category (category_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='商家（入驻主体）';

-- ------------------------------------------------------------
-- 2. 入驻资质
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS t_merchant_qualification (
  id           bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '资质ID',
  tenant_id    bigint unsigned NOT NULL DEFAULT 1 COMMENT '租户ID',
  merchant_id  bigint unsigned NOT NULL COMMENT '商家ID',
  qual_type    tinyint NOT NULL COMMENT '1营业执照 2食品经营许可证 3法人身份证正面 4法人身份证反面 5门店门头照 6店内照 7其他',
  file_id      bigint unsigned NOT NULL COMMENT 't_file.id',
  file_url     varchar(512) NULL COMMENT '文件访问地址（后端代理）',
  file_name    varchar(255) NULL COMMENT '原始文件名',
  expire_at    date NULL COMMENT '证照有效期',
  status       tinyint NOT NULL DEFAULT 0 COMMENT '0待审 1通过 2驳回',
  audit_remark varchar(512) NULL COMMENT '审核备注',
  created_at   datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at   datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  deleted      tinyint NOT NULL DEFAULT 0 COMMENT '0正常 1删除',
  PRIMARY KEY (id),
  KEY idx_merchant_type (merchant_id, qual_type),
  KEY idx_file (file_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='商家资质文件';

-- ------------------------------------------------------------
-- 3. 审核流水（审计表，不做逻辑删除）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS t_merchant_audit_log (
  id            bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '流水ID',
  tenant_id     bigint unsigned NOT NULL DEFAULT 1 COMMENT '租户ID',
  merchant_id   bigint unsigned NOT NULL COMMENT '商家ID',
  from_status   tinyint NOT NULL COMMENT '变更前状态',
  to_status     tinyint NOT NULL COMMENT '变更后状态',
  action        varchar(32) NOT NULL COMMENT 'SUBMIT/APPROVE/REJECT/ENABLE/DISABLE',
  operator_id   bigint unsigned NULL COMMENT '操作人账号ID（申请人为空）',
  operator_name varchar(64) NULL COMMENT '操作人名称（申请人为 APPLY）',
  remark        varchar(512) NULL COMMENT '备注',
  created_at    datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_merchant_time (merchant_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='商家审核流水';

-- ------------------------------------------------------------
-- 4. 结算账户
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS t_merchant_settlement_account (
  id                    bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '账户ID',
  tenant_id             bigint unsigned NOT NULL DEFAULT 1 COMMENT '租户ID',
  merchant_id           bigint unsigned NOT NULL COMMENT '商家ID',
  account_name          varchar(128) NOT NULL COMMENT '开户名',
  bank_name             varchar(128) NOT NULL COMMENT '开户行',
  bank_branch           varchar(255) NULL COMMENT '开户支行',
  bank_account_no_cipher varchar(512) NOT NULL COMMENT '银行卡号密文（AES-GCM）',
  alipay_account        varchar(128) NULL COMMENT '支付宝账号',
  contract_file_id      bigint unsigned NULL COMMENT '合同附件 t_file.id',
  contract_file_url     varchar(512) NULL COMMENT '合同附件访问地址',
  effective_from        date NULL COMMENT '生效开始日期',
  effective_to          date NULL COMMENT '生效结束日期',
  is_default            tinyint NOT NULL DEFAULT 0 COMMENT '1默认账户 0非默认（应用层保证每商家唯一）',
  status                tinyint NOT NULL DEFAULT 0 COMMENT '0待审 1生效 2停用',
  created_at            datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at            datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  deleted               tinyint NOT NULL DEFAULT 0 COMMENT '0正常 1删除',
  PRIMARY KEY (id),
  KEY idx_merchant (merchant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='商家结算账户';

-- ------------------------------------------------------------
-- 5. 文件记录（全站公共件）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS t_file (
  id            bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '文件ID',
  tenant_id     bigint unsigned NOT NULL DEFAULT 1 COMMENT '租户ID',
  file_key      varchar(255) NOT NULL COMMENT 'MinIO object key：{bizType}/{yyyyMM}/{uuid}.{ext}',
  bucket        varchar(64) NOT NULL COMMENT 'Bucket',
  original_name varchar(255) NULL COMMENT '原始文件名（已去路径）',
  content_type  varchar(64) NOT NULL COMMENT 'MIME',
  size_bytes    bigint NOT NULL DEFAULT 0 COMMENT '字节数',
  biz_type      varchar(32) NOT NULL COMMENT 'MERCHANT_QUAL/PRODUCT/AVATAR/TICKET/CONTENT/RECEIPT',
  biz_id        bigint unsigned NULL COMMENT '业务ID；申请阶段为 applyId',
  uploader_id   bigint unsigned NULL COMMENT '上传人账号ID；申请阶段为空',
  uploader_type varchar(16) NOT NULL DEFAULT 'USER' COMMENT 'USER/ADMIN/APPLY',
  url           varchar(512) NULL COMMENT '后端代理地址 /api/v1/files/{id}/raw',
  status        tinyint NOT NULL DEFAULT 0 COMMENT '0正常 1已删除',
  created_at    datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_file_key (file_key),
  KEY idx_biz (biz_type, biz_id),
  KEY idx_uploader (uploader_type, uploader_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='文件元数据';

-- ------------------------------------------------------------
-- 6. 字典类型（商家域）
-- ------------------------------------------------------------
INSERT INTO t_sys_dict_type (id, tenant_id, dict_type, dict_name, status, remark)
VALUES
  (5, 1, 'merchant_status',          '商家状态',     0, 'T03 商家入驻'),
  (6, 1, 'qual_type',                '资质类型',     0, 'T03 商家资质'),
  (7, 1, 'settlement_account_status','结算账户状态', 0, 'T03 商家结算账户'),
  (8, 1, 'file_biz_type',            '文件业务类型', 0, 'T03 公共文件服务')
ON DUPLICATE KEY UPDATE id = id;

-- ------------------------------------------------------------
-- 7. 字典项
-- ------------------------------------------------------------
INSERT INTO t_sys_dict_item
  (id, tenant_id, dict_type, dict_label, dict_value, dict_sort, is_default, status)
VALUES
  (51, 1, 'merchant_status', '草稿',   '0', 1, 1, 0),
  (52, 1, 'merchant_status', '待审核', '1', 2, 0, 0),
  (53, 1, 'merchant_status', '已通过', '2', 3, 0, 0),
  (54, 1, 'merchant_status', '已驳回', '3', 4, 0, 0),
  (55, 1, 'merchant_status', '已停用', '4', 5, 0, 0),

  (61, 1, 'qual_type', '营业执照',       '1', 1, 1, 0),
  (62, 1, 'qual_type', '食品经营许可证', '2', 2, 0, 0),
  (63, 1, 'qual_type', '法人身份证正面', '3', 3, 0, 0),
  (64, 1, 'qual_type', '法人身份证反面', '4', 4, 0, 0),
  (65, 1, 'qual_type', '门店门头照',     '5', 5, 0, 0),
  (66, 1, 'qual_type', '店内照',         '6', 6, 0, 0),
  (67, 1, 'qual_type', '其他',           '7', 7, 0, 0),

  (71, 1, 'settlement_account_status', '待审', '0', 1, 1, 0),
  (72, 1, 'settlement_account_status', '生效', '1', 2, 0, 0),
  (73, 1, 'settlement_account_status', '停用', '2', 3, 0, 0),

  (81, 1, 'file_biz_type', '商家资质', 'MERCHANT_QUAL', 1, 1, 0),
  (82, 1, 'file_biz_type', '商品图片', 'PRODUCT',       2, 0, 0),
  (83, 1, 'file_biz_type', '用户头像', 'AVATAR',        3, 0, 0),
  (84, 1, 'file_biz_type', '工单附件', 'TICKET',        4, 0, 0),
  (85, 1, 'file_biz_type', '内容素材', 'CONTENT',       5, 0, 0),
  (86, 1, 'file_biz_type', '结算单据', 'RECEIPT',       6, 0, 0)
ON DUPLICATE KEY UPDATE id = id;

-- ------------------------------------------------------------
-- 8. 商家管理菜单与按钮权限
--    权限码由 @RequirePermission 校验，缺种子会导致 ADMIN 也被 403。
-- ------------------------------------------------------------
-- V4 已占用 1~29，V5 占用 30~47，V6 占用 48~55，本卡从 56 起
INSERT INTO t_sys_menu
  (id, tenant_id, parent_id, menu_name, menu_type, path, component, perms, icon, sort, visible, status)
VALUES
  (56, 1, 2,  '商家管理',   2, '/merchant/list', 'merchant/list/index', 'merchant:list',   'shop', 1, 0, 0),
  (57, 1, 56, '商家详情',   3, NULL, NULL, 'merchant:detail',  NULL, 1, 1, 0),
  (58, 1, 56, '商家审核',   3, NULL, NULL, 'merchant:audit',   NULL, 2, 1, 0),
  (59, 1, 56, '商家修改',   3, NULL, NULL, 'merchant:update',  NULL, 3, 1, 0)
ON DUPLICATE KEY UPDATE id = id;

-- 超管：新增的全部商家菜单
INSERT INTO t_sys_role_menu (tenant_id, role_id, menu_id)
SELECT 1, 1, id FROM t_sys_menu WHERE tenant_id = 1 AND id IN (56, 57, 58, 59)
ON DUPLICATE KEY UPDATE t_sys_role_menu.id = t_sys_role_menu.id;

-- 招商运营（role_id=2）：审核与查看
INSERT INTO t_sys_role_menu (tenant_id, role_id, menu_id)
SELECT 1, 2, id FROM t_sys_menu WHERE tenant_id = 1 AND id IN (56, 57, 58)
ON DUPLICATE KEY UPDATE t_sys_role_menu.id = t_sys_role_menu.id;

-- 财务（role_id=7）：只允许改抽佣比例
INSERT INTO t_sys_role_menu (tenant_id, role_id, menu_id)
SELECT 1, 7, id FROM t_sys_menu WHERE tenant_id = 1 AND id IN (56, 59)
ON DUPLICATE KEY UPDATE t_sys_role_menu.id = t_sys_role_menu.id;
