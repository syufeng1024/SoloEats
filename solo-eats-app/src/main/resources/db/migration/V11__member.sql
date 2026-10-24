-- ============================================================
-- 数创星光外卖 T06：C 端用户 / 收货地址 / 钱包 / 验证码 / 协议确认 / 标签
-- Flyway V11 / MySQL 8.0 / utf8mb4
-- 说明：
--   1. 本脚本一次性建齐 T06 阶段A/B/C 所需 8 张表，后续阶段不再抢号。
--   2. 可重复执行：CREATE TABLE IF NOT EXISTS + 种子 INSERT ... ON DUPLICATE KEY UPDATE id = id。
--   3. 手机号三列方案：phone_cipher（AES-GCM 密文）/ phone_hash（SHA-256 加盐，唯一键与查询用）
--      / phone_mask（展示脱敏）。任何接口与日志不得出现明文手机号。
--   4. 金额一律 bigint 分，禁止 double / float。
--   5. t_member_user.id = 1 是演示用户，t_order.user_id / t_user_coupon.user_id /
--      OrderLifecycleIntegrationTest 全部依赖它，禁止删除。
-- ============================================================

SET NAMES utf8mb4;
SET time_zone = '+08:00';

-- ------------------------------------------------------------
-- 1. C 端用户（与 t_sys_user 严格分离）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS t_member_user (
  id                bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '用户ID',
  tenant_id         bigint unsigned NOT NULL DEFAULT 1 COMMENT '租户ID',
  phone_cipher      varchar(128) NULL COMMENT '手机号 AES-GCM 密文，Base64(iv||cipher||tag)',
  phone_hash        char(64) NOT NULL COMMENT 'SHA-256(手机号+盐) 小写 hex，唯一键与查询用',
  phone_mask        varchar(20) NULL COMMENT '展示用脱敏手机号，如 138****8000',
  nickname          varchar(64) NULL COMMENT '昵称',
  avatar_url        varchar(512) NULL COMMENT '头像',
  gender            tinyint NOT NULL DEFAULT 0 COMMENT '0未知 1男 2女',
  birthday          date NULL COMMENT '生日',
  status            tinyint NOT NULL DEFAULT 0 COMMENT '0正常 1禁用 2已注销/拉黑',
  register_source   tinyint NOT NULL DEFAULT 1 COMMENT '1手机号 2后台导入',
  member_level_id   bigint unsigned NULL COMMENT '会员等级ID（T12）',
  growth_value      int NOT NULL DEFAULT 0 COMMENT '成长值（T12）',
  order_count       int NOT NULL DEFAULT 0 COMMENT '累计订单数',
  total_amount      bigint NOT NULL DEFAULT 0 COMMENT '累计消费，分',
  last_order_at     datetime(3) NULL COMMENT '最近下单时间',
  last_login_at     datetime(3) NULL COMMENT '最近登录时间',
  cancel_apply_at   datetime(3) NULL COMMENT '注销申请时间，冷静期起点',
  version           int unsigned NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
  created_at        datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at        datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  deleted           tinyint NOT NULL DEFAULT 0 COMMENT '0正常 1删除',
  PRIMARY KEY (id),
  UNIQUE KEY uk_phone_hash (tenant_id, phone_hash),
  KEY idx_status (status),
  KEY idx_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='C 端消费者账号';

-- ------------------------------------------------------------
-- 2. 收货地址
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS t_member_address (
  id                      bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '地址ID',
  tenant_id               bigint unsigned NOT NULL DEFAULT 1 COMMENT '租户ID',
  user_id                 bigint unsigned NOT NULL COMMENT '用户ID',
  receiver_name           varchar(64) NOT NULL COMMENT '收货人',
  receiver_phone_cipher   varchar(128) NULL COMMENT '收货人手机号 AES-GCM 密文',
  receiver_phone_mask     varchar(20) NOT NULL COMMENT '收货人手机号脱敏',
  province                varchar(64) NULL COMMENT '省',
  city                    varchar(64) NULL COMMENT '市',
  district                varchar(64) NULL COMMENT '区县',
  address                 varchar(255) NOT NULL COMMENT '详细地址',
  house_number            varchar(64) NULL COMMENT '门牌号',
  lng                     decimal(10,6) NULL COMMENT '经度',
  lat                     decimal(10,6) NULL COMMENT '纬度',
  tag                     varchar(16) NULL COMMENT '标签：家/公司/学校',
  is_default              tinyint NOT NULL DEFAULT 0 COMMENT '0否 1默认',
  deleted                 tinyint NOT NULL DEFAULT 0 COMMENT '0正常 1删除',
  created_at              datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at              datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_user (user_id, is_default),
  UNIQUE KEY uk_user_phone_addr (user_id, receiver_phone_mask, address)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='C 端收货地址';

-- ------------------------------------------------------------
-- 3. 钱包（用户 1:1）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS t_member_wallet (
  id              bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '钱包ID',
  tenant_id       bigint unsigned NOT NULL DEFAULT 1 COMMENT '租户ID',
  user_id         bigint unsigned NOT NULL COMMENT '用户ID',
  balance         bigint NOT NULL DEFAULT 0 COMMENT '可用余额，分',
  frozen_amount   bigint NOT NULL DEFAULT 0 COMMENT '冻结金额，分',
  total_recharge  bigint NOT NULL DEFAULT 0 COMMENT '累计充值，分',
  total_consume   bigint NOT NULL DEFAULT 0 COMMENT '累计消费，分',
  version         int unsigned NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
  created_at      datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at      datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='C 端钱包';

-- ------------------------------------------------------------
-- 4. 钱包流水
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS t_member_wallet_log (
  id              bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '流水ID',
  tenant_id       bigint unsigned NOT NULL DEFAULT 1 COMMENT '租户ID',
  user_id         bigint unsigned NOT NULL COMMENT '用户ID',
  biz_type        varchar(16) NOT NULL COMMENT 'RECHARGE/CONSUME/REFUND/ADJUST/WITHDRAW/FREEZE/UNFREEZE',
  biz_id          bigint NOT NULL DEFAULT 0 COMMENT '业务单ID',
  request_id      varchar(64) NULL COMMENT '幂等键；为空时不做去重',
  direction       tinyint NOT NULL COMMENT '1收入 2支出',
  amount          bigint NOT NULL COMMENT '变动金额，分，恒为正',
  before_balance  bigint NOT NULL COMMENT '变动前余额，分',
  after_balance   bigint NOT NULL COMMENT '变动后余额，分',
  remark          varchar(255) NULL COMMENT '备注（调账记操作人+原因）',
  operator_id     bigint unsigned NULL COMMENT '后台操作人ID',
  operator_name   varchar(64) NULL COMMENT '后台操作人名称',
  created_at      datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at      datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_biz (biz_type, biz_id, request_id),
  KEY idx_user_time (user_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='C 端钱包流水';

-- ------------------------------------------------------------
-- 5. 短信验证码（只存哈希，不存手机号明文）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS t_member_sms_code (
  id          bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '验证码ID',
  tenant_id   bigint unsigned NOT NULL DEFAULT 1 COMMENT '租户ID',
  phone_hash  char(64) NOT NULL COMMENT 'SHA-256(手机号+盐) 小写 hex',
  code        varchar(8) NOT NULL COMMENT '验证码',
  scene       tinyint NOT NULL COMMENT '1登录 2入驻 3改绑',
  expire_at   datetime(3) NOT NULL COMMENT '过期时间',
  used        tinyint NOT NULL DEFAULT 0 COMMENT '0未用 1已用',
  ip          varchar(64) NULL COMMENT '请求IP',
  created_at  datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at  datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_phone_scene (phone_hash, scene, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='C 端短信验证码';

-- ------------------------------------------------------------
-- 6. 隐私协议确认记录（含撤回同意，同一版本可多条）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS t_member_agreement_confirm (
  id                 bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '记录ID',
  tenant_id          bigint unsigned NOT NULL DEFAULT 1 COMMENT '租户ID',
  user_id            bigint unsigned NOT NULL COMMENT '用户ID',
  agreement_version  varchar(32) NOT NULL COMMENT '协议版本号',
  agree              tinyint NOT NULL DEFAULT 1 COMMENT '1同意 0撤回',
  confirmed_at       datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '确认时间',
  created_at         datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at         datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_user_version (user_id, agreement_version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='C 端隐私协议确认记录';

-- ------------------------------------------------------------
-- 7. 用户标签定义
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS t_member_tag (
  id          bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '标签ID',
  tenant_id   bigint unsigned NOT NULL DEFAULT 1 COMMENT '租户ID',
  tag_name    varchar(64) NOT NULL COMMENT '标签名称',
  tag_rule    varchar(255) NULL COMMENT '分群规则；自动打标 TODO',
  auto_tag    tinyint NOT NULL DEFAULT 0 COMMENT '0手动 1自动',
  status      tinyint NOT NULL DEFAULT 0 COMMENT '0启用 1停用',
  deleted     tinyint NOT NULL DEFAULT 0 COMMENT '0正常 1删除',
  created_at  datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at  datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_tag_name (tenant_id, tag_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='C 端用户标签定义';

-- ------------------------------------------------------------
-- 8. 用户-标签关联
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS t_member_tag_rel (
  id          bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '关联ID',
  tenant_id   bigint unsigned NOT NULL DEFAULT 1 COMMENT '租户ID',
  user_id     bigint unsigned NOT NULL COMMENT '用户ID',
  tag_id      bigint unsigned NOT NULL COMMENT '标签ID',
  created_at  datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at  datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_user_tag (user_id, tag_id),
  KEY idx_tag (tag_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='C 端用户-标签关联';

-- ------------------------------------------------------------
-- 9. 种子数据：演示用户 id=1 + 其钱包
--    phone_hash 为占位值（非真实手机号派生），仅用于让 t_order.user_id=1 有主；
--    phone_cipher 留空，避免写入无法解密的伪密文。
-- ------------------------------------------------------------
INSERT INTO t_member_user
  (id, tenant_id, phone_cipher, phone_hash, phone_mask, nickname, gender, status, register_source)
VALUES
  (1, 1, NULL, '0000000000000000000000000000000000000000000000000000000000000001',
   '138****8000', '演示用户', 0, 0, 1)
ON DUPLICATE KEY UPDATE id = id;

INSERT INTO t_member_wallet
  (tenant_id, user_id, balance, frozen_amount, total_recharge, total_consume, version)
VALUES
  (1, 1, 0, 0, 0, 0, 0)
ON DUPLICATE KEY UPDATE id = id;
