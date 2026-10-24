-- ============================================================
-- 数创星光外卖 T07 阶段B-1：配送轨迹与骑手异常上报
-- Flyway V15 / MySQL 8.0 / utf8mb4
-- 说明：
--   1. V12/V13 已被 T06 会员占用，V14 为 T07 阶段A，本脚本从 V15 起。
--   2. 两张表均带 tenant_id，由 TenantLineInnerInterceptor 自动读写；
--      业务 SQL 不手写 tenant_id 条件。
--   3. 菜单权限 id 从 98 起（V14 已用到 97），授平台超管 role_id = 1。
--   4. 只新增表与菜单，不改 t_delivery_order / t_order_exception / t_rider_income 结构。
-- ============================================================

SET NAMES utf8mb4;
SET time_zone = '+08:00';

-- ------------------------------------------------------------
-- 1. 配送轨迹点（骑手端 5~10s 批量上报，后台回放 / 用户端实时查看）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS t_delivery_track (
  id           bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '轨迹点ID',
  tenant_id    bigint unsigned NOT NULL DEFAULT 1 COMMENT '租户ID',
  delivery_id  bigint unsigned NOT NULL COMMENT '配送单ID',
  rider_id     bigint unsigned NOT NULL COMMENT '上报骑手ID',
  lng          decimal(10,6) NOT NULL COMMENT '经度',
  lat          decimal(10,6) NOT NULL COMMENT '纬度',
  speed        decimal(6,2) NULL COMMENT '瞬时速度 m/s',
  accuracy_m   decimal(6,2) NULL COMMENT '定位精度（米）',
  report_at    datetime(3) NOT NULL COMMENT '设备上报时间，允许乱序',
  created_at   datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_delivery_time (delivery_id, report_at),
  KEY idx_rider_time (rider_id, report_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='配送轨迹点';

-- ------------------------------------------------------------
-- 2. 骑手异常上报
--    type：1超时 2联系不上用户 3地址错误 4餐品破损 5商家出餐慢 6用户拒收
--    images_json 只存文件 ID / URL 数组，不存二进制。
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS t_rider_ping_anomaly (
  id           bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '异常ID',
  tenant_id    bigint unsigned NOT NULL DEFAULT 1 COMMENT '租户ID',
  rider_id     bigint unsigned NOT NULL COMMENT '上报骑手ID',
  delivery_id  bigint unsigned NOT NULL COMMENT '配送单ID',
  type         tinyint NOT NULL COMMENT '1超时 2联系不上用户 3地址错误 4餐品破损 5商家出餐慢 6用户拒收',
  remark       varchar(255) NULL COMMENT '补充说明',
  images_json  json NULL COMMENT '现场照片文件ID/URL数组',
  created_at   datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_delivery (delivery_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='骑手异常上报';

-- ------------------------------------------------------------
-- 3. 后台菜单与权限：挂在既有「配送中心」目录（id = 5）下
--    菜单 id 段：V12 用到 83~88、V13 用到 89~91、V14 用到 92~97，本脚本从 98 起。
-- ------------------------------------------------------------
INSERT INTO t_sys_menu
  (id, tenant_id, parent_id, menu_name, menu_type, path, component, perms, icon, sort, visible, status)
VALUES
  (98,  1, 5,  '配送单管理', 2, '/delivery/order', 'delivery/order/index', 'delivery:order:list',     'list',    3, 0, 0),
  (99,  1, 98, '配送改派',   3, NULL, NULL, 'delivery:order:dispatch', NULL,   1, 1, 0),
  (100, 1, 98, '轨迹回放',   3, NULL, NULL, 'delivery:track:view',     NULL,   2, 1, 0)
ON DUPLICATE KEY UPDATE id = id;

-- 平台超管授予配送单管理 / 改派 / 轨迹回放权限
INSERT INTO t_sys_role_menu (tenant_id, role_id, menu_id)
SELECT 1, 1, id FROM t_sys_menu WHERE tenant_id = 1 AND id BETWEEN 98 AND 100
ON DUPLICATE KEY UPDATE t_sys_role_menu.id = t_sys_role_menu.id;
