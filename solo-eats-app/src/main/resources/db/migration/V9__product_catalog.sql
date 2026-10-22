-- ============================================================
-- 数创星光外卖 T05 阶段A：品类树 · 商品库(SPU) · 门店商品 · 门店分类
-- Flyway V9 / MySQL 8.0 / utf8mb4
-- 说明：
--   1. t_sku 结构零变更。本脚本只新建 t_category/t_spu/t_shop_spu/t_shop_category，
--      并回填 V2 中 t_sku.spu_id=1/2 对应的 SPU 主数据，使既有 demo 数据自洽。
--   2. 金额一律 bigint（分）；t_spu.packaging_fee、t_shop_spu.price_override 同。
--   3. t_shop_spu.price_override 仅做门店改价记录，P0 不进 SkuService 下单价格链路。
--   4. 全部新表带 tenant_id，由 TenantLineInnerInterceptor 自动读写。
--   5. 可重复执行：CREATE TABLE IF NOT EXISTS + 种子 INSERT ... ON DUPLICATE KEY UPDATE id = id。
-- ============================================================

SET NAMES utf8mb4;
SET time_zone = '+08:00';

-- ------------------------------------------------------------
-- 1. 平台品类（最多两级）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS t_category (
  id            bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '品类ID',
  tenant_id     bigint unsigned NOT NULL DEFAULT 1 COMMENT '租户ID',
  parent_id     bigint unsigned NOT NULL DEFAULT 0 COMMENT '父级ID，0为一级',
  category_name varchar(64) NOT NULL COMMENT '品类名称',
  icon_url      varchar(512) NULL COMMENT '图标地址（t_file 代理地址）',
  level         tinyint NOT NULL DEFAULT 1 COMMENT '1一级 2二级',
  sort          int NOT NULL DEFAULT 0 COMMENT '排序，升序',
  status        tinyint NOT NULL DEFAULT 0 COMMENT '0启用 1停用',
  created_at    datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at    datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  deleted       tinyint NOT NULL DEFAULT 0 COMMENT '0正常 1删除',
  PRIMARY KEY (id),
  UNIQUE KEY uk_tenant_name (tenant_id, parent_id, category_name),
  KEY idx_parent_sort (parent_id, sort)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='平台品类';

-- ------------------------------------------------------------
-- 2. 商品库 SPU
--    merchant_id IS NULL = 平台公共商品库；非空 = 商家私有商品。
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS t_spu (
  id               bigint unsigned NOT NULL AUTO_INCREMENT COMMENT 'SPU ID',
  tenant_id        bigint unsigned NOT NULL DEFAULT 1 COMMENT '租户ID',
  merchant_id      bigint unsigned NULL COMMENT '商家ID；NULL=平台公共库',
  category_id      bigint unsigned NOT NULL COMMENT '品类ID（二级）',
  spu_name         varchar(128) NOT NULL COMMENT '商品名称',
  subtitle         varchar(128) NULL COMMENT '副标题',
  description      varchar(500) NULL COMMENT '简短描述（纯文本）',
  detail_html      longtext NULL COMMENT '详情富文本（白名单标签过滤后入库）',
  main_image       varchar(512) NULL COMMENT '主图（t_file 代理地址）',
  images_json      json NULL COMMENT '图集 JSON 数组',
  tags_json        json NULL COMMENT '标签 JSON 数组，如 ["招牌","新品","热销"]',
  packaging_fee    bigint NOT NULL DEFAULT 0 COMMENT '餐盒费（分）',
  min_purchase_qty int NOT NULL DEFAULT 1 COMMENT '起售量',
  limit_per_order  int NOT NULL DEFAULT 0 COMMENT '限购数量，0=不限',
  status           tinyint NOT NULL DEFAULT 0 COMMENT '0草稿 1待审 2已上架 3已下架',
  sort_weight      int NOT NULL DEFAULT 0 COMMENT '排序权重，越大越靠前',
  month_sales      int NOT NULL DEFAULT 0 COMMENT '月售',
  version          int unsigned NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
  created_at       datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at       datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  deleted          tinyint NOT NULL DEFAULT 0 COMMENT '0正常 1删除',
  PRIMARY KEY (id),
  UNIQUE KEY uk_tenant_name (tenant_id, merchant_id, spu_name),
  KEY idx_category_status (category_id, status),
  KEY idx_merchant (merchant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='商品库SPU';

-- ------------------------------------------------------------
-- 3. 门店商品（SPU 在门店的上下架/估清/审核）
--    status 只表示 SPU 在门店的上下架，与 t_sku.status、t_sku_stock.sold_out 语义分离。
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS t_shop_spu (
  id              bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '门店商品ID',
  tenant_id       bigint unsigned NOT NULL DEFAULT 1 COMMENT '租户ID',
  shop_id         bigint unsigned NOT NULL COMMENT '门店ID',
  spu_id          bigint unsigned NOT NULL COMMENT 'SPU ID',
  price_override  bigint NULL COMMENT '门店改价（分）；NULL=使用 SKU 价，P0 不进下单链路',
  status          tinyint NOT NULL DEFAULT 0 COMMENT '0上架 1下架 2估清',
  audit_status    tinyint NOT NULL DEFAULT 0 COMMENT '0免审 1待审 2通过 3驳回',
  sort_weight     int NOT NULL DEFAULT 0 COMMENT '门店内排序权重',
  month_sales     int NOT NULL DEFAULT 0 COMMENT '门店月售',
  suspended_until datetime(3) NULL COMMENT '估清自动恢复时间',
  remark          varchar(255) NULL COMMENT '审核备注',
  version         int unsigned NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
  created_at      datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at      datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  deleted         tinyint NOT NULL DEFAULT 0 COMMENT '0正常 1删除',
  PRIMARY KEY (id),
  UNIQUE KEY uk_shop_spu (shop_id, spu_id),
  KEY idx_shop_status (shop_id, status, sort_weight),
  KEY idx_audit (audit_status, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='门店商品';

-- ------------------------------------------------------------
-- 4. 门店自定义分类
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS t_shop_category (
  id            bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '门店分类ID',
  tenant_id     bigint unsigned NOT NULL DEFAULT 1 COMMENT '租户ID',
  shop_id       bigint unsigned NOT NULL COMMENT '门店ID',
  category_name varchar(64) NOT NULL COMMENT '分类名称',
  sort          int NOT NULL DEFAULT 0 COMMENT '排序，升序',
  status        tinyint NOT NULL DEFAULT 0 COMMENT '0启用 1停用',
  created_at    datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at    datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  deleted       tinyint NOT NULL DEFAULT 0 COMMENT '0正常 1删除',
  PRIMARY KEY (id),
  KEY idx_shop_sort (shop_id, sort)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='门店自定义分类';

-- ------------------------------------------------------------
-- 5. 种子：品类树（一级 + 二级）
-- ------------------------------------------------------------
INSERT INTO t_category (id, tenant_id, parent_id, category_name, icon_url, level, sort, status)
VALUES
  (1, 1, 0, '美食餐饮', NULL, 1, 1, 0),
  (2, 1, 1, '面食',     NULL, 2, 1, 0),
  (3, 1, 1, '饮品',     NULL, 2, 2, 0)
ON DUPLICATE KEY UPDATE id = id;

-- ------------------------------------------------------------
-- 6. 种子：回填 V2 中 t_sku.spu_id=1/2 的商品主数据
--    uk_tenant_name 含 merchant_id，merchant_id 为 NULL 时不参与唯一性冲突，
--    因此这里显式按 id 做空值保护。
-- ------------------------------------------------------------
INSERT INTO t_spu
  (id, tenant_id, merchant_id, category_id, spu_name, subtitle, description,
   packaging_fee, min_purchase_qty, limit_per_order, status, sort_weight, month_sales)
VALUES
  (1, 1, NULL, 2, '招牌牛肉面', '慢炖牛骨汤', '每日现炖牛骨汤，手工面。', 100, 1, 5, 2, 100, 0),
  (2, 1, NULL, 3, '冰柠檬茶', '手打鲜柠檬', '鲜柠檬现打，冰爽解腻。',     0, 1, 0, 2, 90,  0)
ON DUPLICATE KEY UPDATE id = id;

-- ------------------------------------------------------------
-- 7. 种子：既有 demo SKU 所属门店的 t_shop_spu 关联
--    shop_id=1 来自 V2 的 t_sku 演示数据；门店主数据由 T04 业务写入，
--    本表不建外键，故这里只补门店商品关系。
-- ------------------------------------------------------------
INSERT INTO t_shop_spu
  (tenant_id, shop_id, spu_id, price_override, status, audit_status, sort_weight, month_sales)
VALUES
  (1, 1, 1, NULL, 0, 2, 100, 0),
  (1, 1, 2, NULL, 0, 2, 90,  0)
ON DUPLICATE KEY UPDATE id = id;

-- ------------------------------------------------------------
-- 8. 种子：门店分类
-- ------------------------------------------------------------
INSERT INTO t_shop_category (id, tenant_id, shop_id, category_name, sort, status)
VALUES
  (1, 1, 1, '面食', 1, 0),
  (2, 1, 1, '饮品', 2, 0)
ON DUPLICATE KEY UPDATE id = id;

-- ------------------------------------------------------------
-- 9. 商品中心菜单与权限码
--    V4 占用 1~29，V5 30~47，V6 48~55，V7 56~59，V8 60~62，本卡从 63 起。
--    缺种子会导致 @RequirePermission 对超管也返回 10004。
-- ------------------------------------------------------------
INSERT INTO t_sys_menu
  (id, tenant_id, parent_id, menu_name, menu_type, path, component, perms, icon, sort, visible, status)
VALUES
  (63, 1, 3, '品类管理', 2, '/product/category', 'product/category/index', 'product:category:list',   'goods', 1, 0, 0),
  (64, 1, 63, '品类新增', 3, NULL, NULL, 'product:category:create', NULL, 1, 1, 0),
  (65, 1, 63, '品类修改', 3, NULL, NULL, 'product:category:update', NULL, 2, 1, 0),
  (66, 1, 63, '品类删除', 3, NULL, NULL, 'product:category:delete', NULL, 3, 1, 0),
  (67, 1, 3, '商品库',   2, '/product/spu',      'product/spu/index',      'product:spu:list',        'goods', 2, 0, 0),
  (68, 1, 67, '商品新增', 3, NULL, NULL, 'product:spu:create', NULL, 1, 1, 0),
  (69, 1, 67, '商品修改', 3, NULL, NULL, 'product:spu:update', NULL, 2, 1, 0),
  (70, 1, 67, '商品删除', 3, NULL, NULL, 'product:spu:delete', NULL, 3, 1, 0),
  (71, 1, 67, '门店商品审核', 3, NULL, NULL, 'product:shopspu:audit', NULL, 4, 1, 0)
ON DUPLICATE KEY UPDATE id = id;

-- 超管：全部商品权限
INSERT INTO t_sys_role_menu (tenant_id, role_id, menu_id)
SELECT 1, 1, id FROM t_sys_menu WHERE tenant_id = 1 AND id BETWEEN 63 AND 71
ON DUPLICATE KEY UPDATE t_sys_role_menu.id = t_sys_role_menu.id;

-- 商品运营（role_id=3）：全部商品权限
INSERT INTO t_sys_role_menu (tenant_id, role_id, menu_id)
SELECT 1, 3, id FROM t_sys_menu WHERE tenant_id = 1 AND id BETWEEN 63 AND 71
ON DUPLICATE KEY UPDATE t_sys_role_menu.id = t_sys_role_menu.id;

-- 招商运营（role_id=2）：只读商品库与品类
INSERT INTO t_sys_role_menu (tenant_id, role_id, menu_id)
SELECT 1, 2, id FROM t_sys_menu WHERE tenant_id = 1 AND id IN (63, 67)
ON DUPLICATE KEY UPDATE t_sys_role_menu.id = t_sys_role_menu.id;
