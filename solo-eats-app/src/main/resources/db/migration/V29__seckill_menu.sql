-- ============================================================
-- 数创星光外卖 T12 阶段A：秒杀后台菜单种子
-- Flyway V29 / MySQL 8.0 / utf8mb4
-- 说明：
--   1. 表结构已在 V22__marketing_promotion.sql 建好（t_seckill_activity /
--      t_seckill_item / t_seckill_order），本脚本只插菜单，不建表、不改表。
--   2. 菜单 id 从 148 起（145 已被 V27 占用，146/147 已被 V28 占用），
--      挂在「营销中心」(id = 6) 下，授平台超管 role_id = 1。
--   3. 全部使用 ON DUPLICATE KEY UPDATE，空库重建与重复执行均安全。
-- ============================================================

SET NAMES utf8mb4;
SET time_zone = '+08:00';

INSERT INTO t_sys_menu
  (id, tenant_id, parent_id, menu_name, menu_type, path, component, perms, icon, sort, visible, status)
VALUES
  (148, 1, 6,   '秒杀活动', 2, '/marketing/seckill', 'marketing/seckill/index', 'marketing:seckill:list',   'clock', 2, 0, 0),
  (149, 1, 148, '场次维护', 3, NULL,                 NULL,                      'marketing:seckill:update', NULL,    1, 1, 0)
ON DUPLICATE KEY UPDATE id = id;

INSERT INTO t_sys_role_menu (tenant_id, role_id, menu_id)
SELECT 1, 1, id FROM t_sys_menu WHERE tenant_id = 1 AND id BETWEEN 148 AND 149
ON DUPLICATE KEY UPDATE t_sys_role_menu.id = t_sys_role_menu.id;
