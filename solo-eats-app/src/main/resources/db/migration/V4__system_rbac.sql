-- ============================================================
-- 数创星光外卖 T01 阶段A：系统账号 / 角色 / 菜单（RBAC）
-- Flyway V4 / MySQL 8.0 / utf8mb4
-- 说明：
--   1. 本脚本可重复执行（CREATE TABLE IF NOT EXISTS + 种子 INSERT ... ON DUPLICATE KEY UPDATE id = id）。
--   2. 种子密码为 bcrypt 强度 10 的字面量，禁止在 SQL 内做哈希运算。
--   3. 阶段A 只落库与登录读取，不建 C 端消费者账号（消费者归 T06 的 t_member_user）。
--   4. 金额无关；时间为 Asia/Shanghai。
-- ============================================================

SET NAMES utf8mb4;
SET time_zone = '+08:00';

-- ------------------------------------------------------------
-- 1. 账号
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS t_sys_user (
  id            bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '账号ID',
  tenant_id     bigint unsigned NOT NULL DEFAULT 1 COMMENT '租户ID',
  username      varchar(64) NOT NULL COMMENT '登录名',
  password_hash varchar(100) NOT NULL COMMENT 'bcrypt 哈希（$2a$10$...）',
  nickname      varchar(64) NULL COMMENT '昵称',
  phone         varchar(128) NULL COMMENT '手机号密文；AES 密文长度超过 20，故预留 128',
  email         varchar(128) NULL COMMENT '邮箱',
  avatar_url    varchar(512) NULL COMMENT '头像',
  user_type     tinyint NOT NULL COMMENT '1平台 2商家 3骑手',
  merchant_id   bigint unsigned NULL COMMENT '绑定商家',
  shop_id       bigint unsigned NULL COMMENT '绑定门店',
  rider_id      bigint unsigned NULL COMMENT '绑定骑手',
  dept_id       bigint unsigned NULL COMMENT '部门（T02）',
  post_id       bigint unsigned NULL COMMENT '岗位（T02）',
  data_scope    tinyint NOT NULL DEFAULT 5 COMMENT '1全部 2本租户 3本商家 4本门店 5本人',
  status        tinyint NOT NULL DEFAULT 0 COMMENT '0启用 1停用',
  last_login_at datetime(3) NULL COMMENT '最后登录时间',
  last_login_ip varchar(64) NULL COMMENT '最后登录IP',
  version       int unsigned NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
  created_at    datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at    datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  deleted       tinyint NOT NULL DEFAULT 0 COMMENT '0正常 1删除',
  PRIMARY KEY (id),
  UNIQUE KEY uk_tenant_username (tenant_id, username),
  UNIQUE KEY uk_tenant_phone (tenant_id, phone),
  KEY idx_merchant (merchant_id),
  KEY idx_shop (shop_id),
  KEY idx_rider (rider_id),
  KEY idx_type_status (user_type, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='系统账号（平台/商家/骑手）';

-- ------------------------------------------------------------
-- 2. 角色
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS t_sys_role (
  id         bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '角色ID',
  tenant_id  bigint unsigned NOT NULL DEFAULT 1 COMMENT '租户ID',
  role_code  varchar(64) NOT NULL COMMENT '角色码，如 ADMIN',
  role_name  varchar(64) NOT NULL COMMENT '角色名称',
  role_type  tinyint NOT NULL COMMENT '1平台 2商家 3骑手',
  data_scope tinyint NOT NULL DEFAULT 5 COMMENT '数据范围，同 t_sys_user',
  sort       int NOT NULL DEFAULT 0 COMMENT '排序',
  status     tinyint NOT NULL DEFAULT 0 COMMENT '0启用 1停用',
  remark     varchar(255) NULL COMMENT '备注',
  created_at datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  deleted    tinyint NOT NULL DEFAULT 0 COMMENT '0正常 1删除',
  PRIMARY KEY (id),
  UNIQUE KEY uk_tenant_role_code (tenant_id, role_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='系统角色';

-- ------------------------------------------------------------
-- 3. 菜单 / 按钮权限
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS t_sys_menu (
  id         bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '菜单ID',
  tenant_id  bigint unsigned NOT NULL DEFAULT 1 COMMENT '租户ID',
  parent_id  bigint unsigned NOT NULL DEFAULT 0 COMMENT '父级，0为根',
  menu_name  varchar(64) NOT NULL COMMENT '菜单名称',
  menu_type  tinyint NOT NULL COMMENT '1目录 2菜单 3按钮',
  path       varchar(200) NULL COMMENT '前端路由',
  component  varchar(200) NULL COMMENT '组件路径',
  perms      varchar(128) NULL COMMENT '权限标识',
  icon       varchar(64) NULL COMMENT '图标',
  sort       int NOT NULL DEFAULT 0 COMMENT '排序',
  visible    tinyint NOT NULL DEFAULT 0 COMMENT '0显示 1隐藏',
  status     tinyint NOT NULL DEFAULT 0 COMMENT '0启用 1停用',
  created_at datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  deleted    tinyint NOT NULL DEFAULT 0 COMMENT '0正常 1删除',
  PRIMARY KEY (id),
  KEY idx_parent (parent_id, sort),
  KEY idx_perms (perms)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='系统菜单与按钮权限';

-- ------------------------------------------------------------
-- 4. 关联表
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS t_sys_user_role (
  id         bigint unsigned NOT NULL AUTO_INCREMENT,
  tenant_id  bigint unsigned NOT NULL DEFAULT 1 COMMENT '租户ID',
  user_id    bigint unsigned NOT NULL COMMENT '账号ID',
  role_id    bigint unsigned NOT NULL COMMENT '角色ID',
  created_at datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_user_role (user_id, role_id),
  KEY idx_role (role_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='账号-角色';

CREATE TABLE IF NOT EXISTS t_sys_role_menu (
  id         bigint unsigned NOT NULL AUTO_INCREMENT,
  tenant_id  bigint unsigned NOT NULL DEFAULT 1 COMMENT '租户ID',
  role_id    bigint unsigned NOT NULL COMMENT '角色ID',
  menu_id    bigint unsigned NOT NULL COMMENT '菜单ID',
  created_at datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_role_menu (role_id, menu_id),
  KEY idx_menu (menu_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='角色-菜单';

-- ------------------------------------------------------------
-- 5. 种子角色（固定 ID，供关联表引用）
-- ------------------------------------------------------------
INSERT INTO t_sys_role (id, tenant_id, role_code, role_name, role_type, data_scope, sort, status)
VALUES
  (1,  1, 'ADMIN',            '平台超级管理员', 1, 1, 1,  0),
  (2,  1, 'ADMIN_OPERATION',  '招商运营',       1, 2, 2,  0),
  (3,  1, 'ADMIN_PRODUCT',    '商品运营',       1, 2, 3,  0),
  (4,  1, 'ADMIN_MARKETING',  '营销运营',       1, 2, 4,  0),
  (5,  1, 'ADMIN_DISPATCH',   '调度',           1, 2, 5,  0),
  (6,  1, 'ADMIN_SERVICE',    '客服',           1, 2, 6,  0),
  (7,  1, 'ADMIN_FINANCE',    '财务',           1, 2, 7,  0),
  (8,  1, 'ADMIN_ANALYST',    '数据分析',       1, 2, 8,  0),
  (9,  1, 'MERCHANT_MANAGER', '商家管理员',     2, 3, 9,  0),
  (10, 1, 'MERCHANT_CLERK',   '门店店员',       2, 4, 10, 0),
  (11, 1, 'RIDER',            '骑手',           3, 5, 11, 0)
ON DUPLICATE KEY UPDATE id = id;

-- ------------------------------------------------------------
-- 6. 种子账号
--    admin/Admin@123、merchant1/Merchant@123、rider1/Rider@123
--    merchant001/rider001 为同一身份的兼容别名（任务卡用 merchant1/rider1，
--    联调口径同时保留 001 写法）。
-- ------------------------------------------------------------
INSERT INTO t_sys_user
  (id, tenant_id, username, password_hash, nickname, user_type, merchant_id, shop_id, rider_id, data_scope, status)
VALUES
  (1, 1, 'admin',       '$2a$10$p1/Ro5q89SQ.8XRMx5hiDud2acb4B2haAykz4/vEQ8NoQw4BVn39e', '平台超管',     1, NULL, NULL, NULL, 1, 0),
  (2, 1, 'merchant1',   '$2a$10$Q/CKxa2WL/CKrxbbM.GsF..MUZhomfCYtaZgPEZC5jPJPmVhe2/ZS', '商家管理员',   2, 1,    1,    NULL, 4, 0),
  (3, 1, 'rider1',      '$2a$10$xXdmbN0r5c5cJAaXkZ.21eS5dkFpKoOOUS7ZNG.NbZaiAE2K5hqwO', '骑手',         3, NULL, NULL, 1,    5, 0),
  (4, 1, 'merchant001', '$2a$10$Q/CKxa2WL/CKrxbbM.GsF..MUZhomfCYtaZgPEZC5jPJPmVhe2/ZS', '商家管理员001', 2, 1,    1,    NULL, 4, 0),
  (5, 1, 'rider001',    '$2a$10$xXdmbN0r5c5cJAaXkZ.21eS5dkFpKoOOUS7ZNG.NbZaiAE2K5hqwO', '骑手001',       3, NULL, NULL, 1,    5, 0)
ON DUPLICATE KEY UPDATE id = id;

-- ------------------------------------------------------------
-- 7. 种子菜单：一级目录（规格书 4.0 导航）
-- ------------------------------------------------------------
INSERT INTO t_sys_menu (id, tenant_id, parent_id, menu_name, menu_type, path, component, perms, icon, sort, visible, status)
VALUES
  (1,  1, 0, '工作台',     1, '/dashboard',  NULL, NULL, 'dashboard', 1,  0, 0),
  (2,  1, 0, '商家招商',   1, '/merchant',   NULL, NULL, 'shop',      2,  0, 0),
  (3,  1, 0, '商品中心',   1, '/product',    NULL, NULL, 'goods',     3,  0, 0),
  (4,  1, 0, '订单中心',   1, '/order',      NULL, NULL, 'order',     4,  0, 0),
  (5,  1, 0, '配送中心',   1, '/delivery',   NULL, NULL, 'delivery',  5,  0, 0),
  (6,  1, 0, '营销中心',   1, '/marketing',  NULL, NULL, 'marketing', 6,  0, 0),
  (7,  1, 0, '用户中心',   1, '/member',     NULL, NULL, 'user',      7,  0, 0),
  (8,  1, 0, '资金中心',   1, '/finance',    NULL, NULL, 'money',     8,  0, 0),
  (9,  1, 0, '内容运营',   1, '/content',    NULL, NULL, 'content',   9,  0, 0),
  (10, 1, 0, '客服与工单', 1, '/service',    NULL, NULL, 'service',   10, 0, 0),
  (11, 1, 0, '数据报表',   1, '/report',     NULL, NULL, 'report',    11, 0, 0),
  (12, 1, 0, '系统管理',   1, '/system',     NULL, NULL, 'system',    12, 0, 0)
ON DUPLICATE KEY UPDATE id = id;

-- ------------------------------------------------------------
-- 8. 种子菜单：系统管理子菜单
-- ------------------------------------------------------------
INSERT INTO t_sys_menu (id, tenant_id, parent_id, menu_name, menu_type, path, component, perms, icon, sort, visible, status)
VALUES
  (13, 1, 12, '用户管理', 2, '/system/user', 'system/user/index', 'system:user:list', 'user',   1, 0, 0),
  (14, 1, 12, '角色管理', 2, '/system/role', 'system/role/index', 'system:role:list', 'role',   2, 0, 0),
  (15, 1, 12, '菜单管理', 2, '/system/menu', 'system/menu/index', 'system:menu:list', 'menu',   3, 0, 0)
ON DUPLICATE KEY UPDATE id = id;

-- ------------------------------------------------------------
-- 9. 种子菜单：按钮权限
-- ------------------------------------------------------------
INSERT INTO t_sys_menu (id, tenant_id, parent_id, menu_name, menu_type, perms, sort, visible, status)
VALUES
  (16, 1, 13, '用户查询',     3, 'system:user:list',   1, 1, 0),
  (17, 1, 13, '用户新增',     3, 'system:user:create', 2, 1, 0),
  (18, 1, 13, '用户修改',     3, 'system:user:update', 3, 1, 0),
  (19, 1, 13, '用户分配角色', 3, 'system:user:assign', 4, 1, 0),
  (20, 1, 13, '重置密码',     3, 'system:user:reset',  5, 1, 0),
  (21, 1, 13, '用户删除',     3, 'system:user:delete', 6, 1, 0),
  (22, 1, 14, '角色查询',     3, 'system:role:list',   1, 1, 0),
  (23, 1, 14, '角色新增',     3, 'system:role:create', 2, 1, 0),
  (24, 1, 14, '角色修改',     3, 'system:role:update', 3, 1, 0),
  (25, 1, 14, '角色分配菜单', 3, 'system:role:assign', 4, 1, 0),
  (26, 1, 15, '菜单查询',     3, 'system:menu:list',   1, 1, 0),
  (27, 1, 15, '菜单新增',     3, 'system:menu:create', 2, 1, 0),
  (28, 1, 15, '菜单修改',     3, 'system:menu:update', 3, 1, 0),
  (29, 1, 15, '菜单删除',     3, 'system:menu:delete', 4, 1, 0)
ON DUPLICATE KEY UPDATE id = id;

-- ------------------------------------------------------------
-- 10. 账号-角色
-- ------------------------------------------------------------
INSERT INTO t_sys_user_role (tenant_id, user_id, role_id)
VALUES
  (1, 1, 1),
  (1, 2, 9),
  (1, 3, 11),
  (1, 4, 9),
  (1, 5, 11)
ON DUPLICATE KEY UPDATE id = id;

-- ------------------------------------------------------------
-- 11. 角色-菜单（超管全量；其余角色按规格书 3.3 最小可见集）
-- ------------------------------------------------------------
INSERT INTO t_sys_role_menu (tenant_id, role_id, menu_id)
SELECT 1, 1, id FROM t_sys_menu WHERE tenant_id = 1
ON DUPLICATE KEY UPDATE t_sys_role_menu.id = t_sys_role_menu.id;

INSERT INTO t_sys_role_menu (tenant_id, role_id, menu_id)
SELECT 1, 2, id FROM t_sys_menu WHERE tenant_id = 1 AND id IN (1, 2)
ON DUPLICATE KEY UPDATE t_sys_role_menu.id = t_sys_role_menu.id;

INSERT INTO t_sys_role_menu (tenant_id, role_id, menu_id)
SELECT 1, 3, id FROM t_sys_menu WHERE tenant_id = 1 AND id IN (1, 3)
ON DUPLICATE KEY UPDATE t_sys_role_menu.id = t_sys_role_menu.id;

INSERT INTO t_sys_role_menu (tenant_id, role_id, menu_id)
SELECT 1, 4, id FROM t_sys_menu WHERE tenant_id = 1 AND id IN (1, 6)
ON DUPLICATE KEY UPDATE t_sys_role_menu.id = t_sys_role_menu.id;

INSERT INTO t_sys_role_menu (tenant_id, role_id, menu_id)
SELECT 1, 5, id FROM t_sys_menu WHERE tenant_id = 1 AND id IN (1, 4, 5)
ON DUPLICATE KEY UPDATE t_sys_role_menu.id = t_sys_role_menu.id;

INSERT INTO t_sys_role_menu (tenant_id, role_id, menu_id)
SELECT 1, 6, id FROM t_sys_menu WHERE tenant_id = 1 AND id IN (1, 4, 10)
ON DUPLICATE KEY UPDATE t_sys_role_menu.id = t_sys_role_menu.id;

INSERT INTO t_sys_role_menu (tenant_id, role_id, menu_id)
SELECT 1, 7, id FROM t_sys_menu WHERE tenant_id = 1 AND id IN (1, 8, 11)
ON DUPLICATE KEY UPDATE t_sys_role_menu.id = t_sys_role_menu.id;

INSERT INTO t_sys_role_menu (tenant_id, role_id, menu_id)
SELECT 1, 8, id FROM t_sys_menu WHERE tenant_id = 1 AND id IN (1, 11)
ON DUPLICATE KEY UPDATE t_sys_role_menu.id = t_sys_role_menu.id;

INSERT INTO t_sys_role_menu (tenant_id, role_id, menu_id)
SELECT 1, 9, id FROM t_sys_menu WHERE tenant_id = 1 AND id IN (1, 3, 4, 8)
ON DUPLICATE KEY UPDATE t_sys_role_menu.id = t_sys_role_menu.id;

INSERT INTO t_sys_role_menu (tenant_id, role_id, menu_id)
SELECT 1, 10, id FROM t_sys_menu WHERE tenant_id = 1 AND id IN (1, 3, 4)
ON DUPLICATE KEY UPDATE t_sys_role_menu.id = t_sys_role_menu.id;

INSERT INTO t_sys_role_menu (tenant_id, role_id, menu_id)
SELECT 1, 11, id FROM t_sys_menu WHERE tenant_id = 1 AND id IN (1, 5)
ON DUPLICATE KEY UPDATE t_sys_role_menu.id = t_sys_role_menu.id;
