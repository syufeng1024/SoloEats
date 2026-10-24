-- ============================================================
-- 数创星光外卖 T07 阶段A：骑手档案、认证、排班、派单策略、在线日志
-- Flyway V14 / MySQL 8.0 / utf8mb4
-- 说明：
--   1. V12、V13 已被 T06 会员模块占用，本脚本从 V14 起；菜单 id 从 92 起。
--   2. 全部新表带 tenant_id，由 TenantLineInnerInterceptor 自动读写；
--      业务 SQL 不手写 tenant_id 条件。
--   3. t_delivery_order 只增列 promise_deliver_at，不改任何既有列；
--      DDL 通过 information_schema 判存在，可重复执行。
--   4. 种子数据保证既有 rider_id = 1 的配送单与 t_rider_income 有主。
-- ============================================================

SET NAMES utf8mb4;
SET time_zone = '+08:00';

-- ------------------------------------------------------------
-- 1. 骑手档案
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS t_rider (
  id                       bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '骑手ID',
  tenant_id                bigint unsigned NOT NULL DEFAULT 1 COMMENT '租户ID',
  user_id                  bigint unsigned NOT NULL COMMENT '关联 t_sys_user.id（user_type=3）',
  rider_no                 varchar(32) NOT NULL COMMENT '骑手编号 R + 序号',
  real_name                varchar(64) NULL COMMENT '真实姓名',
  id_card_cipher           varchar(512) NULL COMMENT '身份证 AES-GCM 密文',
  id_card_mask             varchar(32) NULL COMMENT '身份证脱敏展示值',
  phone_cipher             varchar(512) NULL COMMENT '手机号 AES-GCM 密文',
  phone_mask               varchar(32) NULL COMMENT '手机号脱敏展示值',
  avatar_url               varchar(512) NULL COMMENT '头像',
  vehicle_type             tinyint NOT NULL DEFAULT 1 COMMENT '1电动车 2摩托 3自行车 4步行',
  cert_status              tinyint NOT NULL DEFAULT 0 COMMENT '0未提交 1待审核 2已通过 3已驳回',
  cert_remark              varchar(255) NULL COMMENT '认证驳回原因',
  health_cert_expire_at    date NULL COMMENT '健康证到期日，过期拒绝新单',
  max_concurrent_orders    int NOT NULL DEFAULT 3 COMMENT '同时进行中订单上限',
  pickup_radius_m          int NOT NULL DEFAULT 3000 COMMENT '接单半径（米）',
  status                   tinyint NOT NULL DEFAULT 0 COMMENT '0正常 1停用 2拉黑',
  rating                   decimal(3,2) NOT NULL DEFAULT 5.00 COMMENT '骑手评分',
  on_time_rate             decimal(5,2) NOT NULL DEFAULT 100.00 COMMENT '准时率（%）',
  total_orders             int NOT NULL DEFAULT 0 COMMENT '累计完成单量',
  online_status            tinyint NOT NULL DEFAULT 0 COMMENT '0离线 1在线 2接单中（DB 兜底）',
  last_online_at           datetime(3) NULL COMMENT '最近上线时间',
  version                  int unsigned NOT NULL DEFAULT 0,
  created_at               datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at               datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  deleted                  tinyint NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_rider_no (rider_no),
  UNIQUE KEY uk_user (user_id),
  KEY idx_cert_status (cert_status),
  KEY idx_status_online (status, online_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='骑手档案';

-- ------------------------------------------------------------
-- 2. 骑手认证记录（每次提交一条，保留审核历史）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS t_rider_certification (
  id                       bigint unsigned NOT NULL AUTO_INCREMENT,
  tenant_id                bigint unsigned NOT NULL DEFAULT 1,
  rider_id                 bigint unsigned NOT NULL,
  real_name                varchar(64) NOT NULL COMMENT '提交时的真实姓名',
  id_card_cipher           varchar(512) NOT NULL COMMENT '身份证 AES-GCM 密文',
  id_card_front_file_id    bigint unsigned NULL COMMENT '身份证正面文件ID',
  id_card_back_file_id     bigint unsigned NULL COMMENT '身份证反面文件ID',
  health_cert_file_id      bigint unsigned NULL COMMENT '健康证文件ID',
  health_cert_expire_at    date NULL COMMENT '健康证到期日',
  status                   tinyint NOT NULL DEFAULT 0 COMMENT '0待审 1通过 2驳回',
  audit_by                 bigint unsigned NULL COMMENT '审核人 t_sys_user.id',
  audit_at                 datetime(3) NULL COMMENT '审核时间',
  audit_remark             varchar(255) NULL COMMENT '审核备注/驳回原因',
  created_at               datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at               datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_rider_time (rider_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='骑手认证记录';

-- ------------------------------------------------------------
-- 3. 骑手排班
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS t_rider_schedule (
  id                       bigint unsigned NOT NULL AUTO_INCREMENT,
  tenant_id                bigint unsigned NOT NULL DEFAULT 1,
  rider_id                 bigint unsigned NOT NULL,
  work_date                date NOT NULL COMMENT '排班日期',
  start_time               time NOT NULL COMMENT '开始时间',
  end_time                 time NOT NULL COMMENT '结束时间',
  status                   tinyint NOT NULL DEFAULT 0 COMMENT '0待确认 1已确认 2请假',
  created_at               datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at               datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_rider_date (rider_id, work_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='骑手排班';

-- ------------------------------------------------------------
-- 4. 派单策略配置（全局 -> 城市 -> 门店覆盖）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS t_dispatch_config (
  id                       bigint unsigned NOT NULL AUTO_INCREMENT,
  tenant_id                bigint unsigned NOT NULL DEFAULT 1,
  config_scope             tinyint NOT NULL DEFAULT 1 COMMENT '1全局 2城市 3门店',
  shop_id                  bigint unsigned NULL COMMENT 'config_scope=3 时必填',
  distance_weight          decimal(4,2) NOT NULL DEFAULT 0.40 COMMENT '距离权重',
  load_weight              decimal(4,2) NOT NULL DEFAULT 0.30 COMMENT '负载权重',
  rating_weight            decimal(4,2) NOT NULL DEFAULT 0.20 COMMENT '评分权重',
  timeout_weight           decimal(4,2) NOT NULL DEFAULT 0.10 COMMENT '超时优先权重',
  dispatch_expire_seconds  int NOT NULL DEFAULT 15 COMMENT '派单倒计时（秒）',
  max_retry                int NOT NULL DEFAULT 3 COMMENT '最大重试次数',
  grab_radius_m            int NOT NULL DEFAULT 3000 COMMENT '抢单大厅半径（米）',
  grab_max_orders          int NOT NULL DEFAULT 3 COMMENT '抢单时骑手进行中订单上限',
  grab_enabled             tinyint NOT NULL DEFAULT 1 COMMENT '1开启抢单大厅',
  auto_dispatch_enabled    tinyint NOT NULL DEFAULT 1 COMMENT '1开启自动派单',
  status                   tinyint NOT NULL DEFAULT 0 COMMENT '0启用 1停用',
  created_at               datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at               datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_scope_shop (config_scope, shop_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='派单策略配置';

-- ------------------------------------------------------------
-- 5. 骑手上下线日志
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS t_rider_online_log (
  id                       bigint unsigned NOT NULL AUTO_INCREMENT,
  tenant_id                bigint unsigned NOT NULL DEFAULT 1,
  rider_id                 bigint unsigned NOT NULL,
  action                   tinyint NOT NULL COMMENT '1上线 2下线',
  lng                      decimal(10,6) NULL COMMENT '上线/下线位置经度',
  lat                      decimal(10,6) NULL COMMENT '上线/下线位置纬度',
  reason                   varchar(128) NULL COMMENT '下线原因',
  created_at               datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at               datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_rider_time (rider_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='骑手上下线日志';

-- ------------------------------------------------------------
-- 6. 既有 t_delivery_order Expand 增列：承诺送达时间
--    仅新增列，不改任何既有列；information_schema 判重后执行，可重复运行。
-- ------------------------------------------------------------
SET @has_promise_col := (
  SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = DATABASE()
    AND table_name = 't_delivery_order'
    AND column_name = 'promise_deliver_at'
);
SET @ddl := IF(@has_promise_col = 0,
  'ALTER TABLE t_delivery_order ADD COLUMN promise_deliver_at datetime(3) NULL COMMENT ''承诺送达时间'' AFTER dispatch_expire_at',
  'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ------------------------------------------------------------
-- 7. 种子：rider_id = 1 的档案（既有配送单与 t_rider_income 有主）
--    user_id 取 t_sys_user 中 user_type=3 的 rider001（V4 种子 id=5）；
--    查不到时回退固定值 5，并在执行记录说明。
-- ------------------------------------------------------------
INSERT INTO t_rider
  (id, tenant_id, user_id, rider_no, real_name, cert_status, health_cert_expire_at,
   max_concurrent_orders, pickup_radius_m, status, rating, on_time_rate, total_orders,
   online_status, version, deleted)
VALUES
  (1, 1,
   COALESCE((SELECT u.id FROM t_sys_user u
             WHERE u.tenant_id = 1 AND u.user_type = 3 AND u.username = 'rider001'
             LIMIT 1), 5),
   'R0001', '骑手001', 2, '2027-12-31', 3, 3000, 0, 5.00, 100.00, 0, 0, 0, 0)
ON DUPLICATE KEY UPDATE id = id;

-- ------------------------------------------------------------
-- 8. 种子：全局派单策略（权重合计 1.00）
-- ------------------------------------------------------------
INSERT INTO t_dispatch_config
  (id, tenant_id, config_scope, shop_id, distance_weight, load_weight, rating_weight,
   timeout_weight, dispatch_expire_seconds, max_retry, grab_radius_m, grab_max_orders,
   grab_enabled, auto_dispatch_enabled, status)
VALUES
  (1, 1, 1, NULL, 0.40, 0.30, 0.20, 0.10, 15, 3, 3000, 3, 1, 1, 0)
ON DUPLICATE KEY UPDATE id = id;

-- ------------------------------------------------------------
-- 9. 后台菜单与权限：挂在既有「配送中心」目录（id = 5）下
--    菜单 id 段：V12 用到 83~88、V13 用到 89~91，本脚本从 92 起。
-- ------------------------------------------------------------
INSERT INTO t_sys_menu
  (id, tenant_id, parent_id, menu_name, menu_type, path, component, perms, icon, sort, visible, status)
VALUES
  (92, 1, 5,  '骑手管理', 2, '/delivery/rider',  'delivery/rider/index',  'delivery:rider:list',   'user',  1, 0, 0),
  (93, 1, 92, '骑手详情', 3, NULL, NULL, 'delivery:rider:detail', NULL, 1, 1, 0),
  (94, 1, 92, '认证审核', 3, NULL, NULL, 'delivery:rider:audit',  NULL, 2, 1, 0),
  (95, 1, 92, '骑手编辑', 3, NULL, NULL, 'delivery:rider:update', NULL, 3, 1, 0),
  (96, 1, 5,  '派单策略', 2, '/delivery/config', 'delivery/config/index', 'delivery:config:list',  'setting', 2, 0, 0),
  (97, 1, 96, '策略编辑', 3, NULL, NULL, 'delivery:config:update', NULL, 1, 1, 0)
ON DUPLICATE KEY UPDATE id = id;

-- 平台超管（role_id = 1）授予全部骑手与派单策略权限
INSERT INTO t_sys_role_menu (tenant_id, role_id, menu_id)
SELECT 1, 1, id FROM t_sys_menu WHERE tenant_id = 1 AND id BETWEEN 92 AND 97
ON DUPLICATE KEY UPDATE t_sys_role_menu.id = t_sys_role_menu.id;
