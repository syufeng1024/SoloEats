-- ============================================================
-- 数创星光外卖 T07 阶段B-2：运费模板与试算、骑手收入费率
-- Flyway V16 / MySQL 8.0 / utf8mb4
-- 说明：
--   1. 库已到 V15（V14 阶段A、V15 阶段B-1），本脚本从 V16 起。
--   2. 四张表均带 tenant_id，由 TenantLineInnerInterceptor 自动读写，
--      业务 SQL 不手写 tenant_id 条件。
--   3. 菜单权限 id 从 101 起（V15 已用到 100），授平台超管 role_id = 1。
--   4. 金额字段一律 bigint 分；不新增/修改 t_rider_income 结构。
-- ============================================================

SET NAMES utf8mb4;
SET time_zone = '+08:00';

-- ------------------------------------------------------------
-- 1. 运费模板：起步价 + 距离阶梯 + 满额免配送费
--    free_over_amount 为 NULL 表示不提供满额减免。
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS t_freight_template (
  id                  bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '模板ID',
  tenant_id           bigint unsigned NOT NULL DEFAULT 1 COMMENT '租户ID',
  template_name       varchar(64) NOT NULL COMMENT '模板名称',
  base_fee            bigint NOT NULL DEFAULT 0 COMMENT '起步价（分）',
  base_distance_m     int NOT NULL DEFAULT 3000 COMMENT '起步距离（米）',
  per_step_distance_m int NOT NULL DEFAULT 500 COMMENT '距离阶梯（米）',
  per_step_fee        bigint NOT NULL DEFAULT 0 COMMENT '每阶梯加价（分）',
  max_distance_m      int NOT NULL DEFAULT 10000 COMMENT '最大配送距离（米），超出拒单',
  free_over_amount    bigint NULL COMMENT '满额免配送费门槛（分），NULL=不减免',
  status              tinyint NOT NULL DEFAULT 0 COMMENT '0启用 1停用',
  created_at          datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at          datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  deleted             tinyint NOT NULL DEFAULT 0 COMMENT '0正常 1删除',
  PRIMARY KEY (id),
  KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='运费模板';

-- ------------------------------------------------------------
-- 2. 运费规则：时段 / 天气 / 重量 / 楼层附加
--    rule_type：1距离区间 2时段加价 3恶劣天气 4重量 5楼层
--    condition_json 约定：
--      2 时段：{"windows":[{"start":"11:00","end":"13:00"}]}
--      3 天气：{"enabled":true|false}
--      4 重量：{"minWeightKg":2.0,"stepKg":1}
--      5 楼层：{"freeFloor":1}
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS t_freight_rule (
  id             bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '规则ID',
  tenant_id      bigint unsigned NOT NULL DEFAULT 1 COMMENT '租户ID',
  template_id    bigint unsigned NOT NULL COMMENT '所属运费模板ID',
  rule_type      tinyint NOT NULL COMMENT '1距离区间 2时段加价 3恶劣天气 4重量 5楼层',
  condition_json json NULL COMMENT '规则条件',
  fee_delta      bigint NOT NULL DEFAULT 0 COMMENT '加价金额（分）',
  sort           int NOT NULL DEFAULT 0 COMMENT '同模板内排序',
  status         tinyint NOT NULL DEFAULT 0 COMMENT '0启用 1停用',
  created_at     datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at     datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_template_type (template_id, rule_type, sort)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='运费加价规则';

-- ------------------------------------------------------------
-- 3. 门店运费模板绑定：一店一模板
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS t_shop_freight_rel (
  id          bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '绑定ID',
  tenant_id   bigint unsigned NOT NULL DEFAULT 1 COMMENT '租户ID',
  shop_id     bigint unsigned NOT NULL COMMENT '门店ID',
  template_id bigint unsigned NOT NULL COMMENT '运费模板ID',
  created_at  datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at  datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_shop (shop_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='门店运费模板绑定';

-- ------------------------------------------------------------
-- 4. 骑手配送费规则：rider_id 为 NULL 表示全局费率
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS t_rider_fee_rule (
  id                bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '费率ID',
  tenant_id         bigint unsigned NOT NULL DEFAULT 1 COMMENT '租户ID',
  rider_id          bigint unsigned NULL COMMENT '骑手ID，NULL=全局',
  base_fee          bigint NOT NULL DEFAULT 0 COMMENT '基础配送费（分）',
  per_km_fee        bigint NOT NULL DEFAULT 0 COMMENT '每公里距离费（分），不足 1km 按 1km',
  peak_delta        bigint NOT NULL DEFAULT 0 COMMENT '高峰时段补贴（分）',
  bad_weather_delta bigint NOT NULL DEFAULT 0 COMMENT '恶劣天气补贴（分）',
  timeout_penalty   bigint NOT NULL DEFAULT 0 COMMENT '超时罚款（分）',
  effective_from    date NULL COMMENT '生效起始日，NULL=立即生效',
  effective_to      date NULL COMMENT '生效截止日，NULL=长期有效',
  status            tinyint NOT NULL DEFAULT 0 COMMENT '0启用 1停用',
  created_at        datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at        datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_rider_status (rider_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='骑手配送费规则';

-- ------------------------------------------------------------
-- 5. 种子数据（可重复执行）
-- ------------------------------------------------------------
-- 默认模板：起步 5 元 / 3km，之后每 500m 加 1 元，10km 封顶，满 30 元免配送费
INSERT INTO t_freight_template
  (id, tenant_id, template_name, base_fee, base_distance_m, per_step_distance_m,
   per_step_fee, max_distance_m, free_over_amount, status)
VALUES
  (1, 1, '默认运费模板', 500, 3000, 500, 100, 10000, 3000, 0)
ON DUPLICATE KEY UPDATE id = id;

-- 高峰时段加价 +1 元；恶劣天气加价 +2 元，默认关闭（由运营在后台开启）
INSERT INTO t_freight_rule
  (id, tenant_id, template_id, rule_type, condition_json, fee_delta, sort, status)
VALUES
  (1, 1, 1, 2,
   '{"windows":[{"start":"11:00","end":"13:00"},{"start":"17:00","end":"19:00"}]}',
   100, 1, 0),
  (2, 1, 1, 3, '{"enabled": false}', 200, 2, 0)
ON DUPLICATE KEY UPDATE id = id;

-- 全局骑手费率：基础 4 元 + 1 元/km，高峰 +0.8 元，恶劣天气 +1.5 元，超时罚 2 元
INSERT INTO t_rider_fee_rule
  (id, tenant_id, rider_id, base_fee, per_km_fee, peak_delta, bad_weather_delta,
   timeout_penalty, effective_from, effective_to, status)
VALUES
  (1, 1, NULL, 400, 100, 80, 150, 200, NULL, NULL, 0)
ON DUPLICATE KEY UPDATE id = id;

-- ------------------------------------------------------------
-- 6. 后台菜单与权限：挂在既有「配送中心」目录（id = 5）下
--    菜单 id 段：V14 用到 92~97、V15 用到 98~100，本脚本从 101 起。
-- ------------------------------------------------------------
INSERT INTO t_sys_menu
  (id, tenant_id, parent_id, menu_name, menu_type, path, component, perms, icon, sort, visible, status)
VALUES
  (101, 1, 5,   '运费模板',     2, '/delivery/freight', 'delivery/freight/index', 'delivery:freight:list',   'freight', 4, 0, 0),
  (102, 1, 101, '运费模板维护', 3, NULL,                NULL,                     'delivery:freight:update', NULL,      1, 1, 0),
  (103, 1, 5,   '骑手收入',     2, '/delivery/income',  'delivery/income/index',  'delivery:income:list',    'money',   5, 0, 0)
ON DUPLICATE KEY UPDATE id = id;

INSERT INTO t_sys_role_menu (tenant_id, role_id, menu_id)
SELECT 1, 1, id FROM t_sys_menu WHERE tenant_id = 1 AND id BETWEEN 101 AND 103
ON DUPLICATE KEY UPDATE t_sys_role_menu.id = t_sys_role_menu.id;
