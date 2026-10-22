-- ============================================================
-- 数创星光外卖 T05 阶段B：规格组/加料组/估清/评价/评价标签/素材库
-- Flyway V10 / MySQL 8.0 / utf8mb4
-- 说明：
--   1. 规格组与加料组支持 is_template=1 的模板复用：模板组本身不直接绑定 SPU，
--      由 t_spu_spec_rel / t_spu_addon_rel 建立 SPU 关联。
--   2. 估清必须三方一致：t_shop_spu.status=2 且 t_sku_stock.sold_out=1 且
--      t_sold_out_record 有未恢复记录；恢复为反向操作。
--   3. 评价以 t_product_review.uk_order_item 兜底“同一订单明细只能评价一次”；
--      评价不可编辑，追评写 append_content/append_at 且只允许一次。
--   4. 素材库 t_media_item.file_id 引用 T03 的 t_file.id，不复制文件元数据。
--   5. 全部新表带 tenant_id；金额 bigint（分）。
--   6. 可重复执行：CREATE TABLE IF NOT EXISTS + 种子 INSERT ... ON DUPLICATE KEY UPDATE id = id。
-- ============================================================

SET NAMES utf8mb4;
SET time_zone = '+08:00';

-- ------------------------------------------------------------
-- 1. 规格组（杯型/温度/糖度）
--    索引：idx_tenant_sort(tenant_id, sort) + idx_template(is_template, deleted)
--    不建 idx_tenant(group_id)：本表无 group_id 列。
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS t_spec_group (
  id          bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '规格组ID',
  tenant_id   bigint unsigned NOT NULL DEFAULT 1 COMMENT '租户ID',
  group_name  varchar(64) NOT NULL COMMENT '规格组名称',
  group_type  tinyint NOT NULL DEFAULT 1 COMMENT '1单选 2多选',
  is_required tinyint NOT NULL DEFAULT 0 COMMENT '1必选 0可选',
  sort        int NOT NULL DEFAULT 0 COMMENT '排序，升序',
  is_template tinyint NOT NULL DEFAULT 1 COMMENT '1模板组 0私有组',
  created_at  datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at  datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  deleted     tinyint NOT NULL DEFAULT 0 COMMENT '0正常 1删除',
  PRIMARY KEY (id),
  KEY idx_tenant_sort (tenant_id, sort),
  KEY idx_template (is_template, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='规格组';

-- ------------------------------------------------------------
-- 2. 规格选项（price_delta 允许为负，如小杯 -200）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS t_spec_option (
  id          bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '规格选项ID',
  tenant_id   bigint unsigned NOT NULL DEFAULT 1 COMMENT '租户ID',
  group_id    bigint unsigned NOT NULL COMMENT '规格组ID',
  option_name varchar(64) NOT NULL COMMENT '选项名称',
  price_delta bigint NOT NULL DEFAULT 0 COMMENT '加价（分），允许为负',
  sort        int NOT NULL DEFAULT 0 COMMENT '排序，升序',
  status      tinyint NOT NULL DEFAULT 0 COMMENT '0启用 1停用',
  created_at  datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at  datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  deleted     tinyint NOT NULL DEFAULT 0 COMMENT '0正常 1删除',
  PRIMARY KEY (id),
  KEY idx_group (group_id, sort)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='规格选项';

-- ------------------------------------------------------------
-- 3. SPU-规格组关联
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS t_spu_spec_rel (
  id        bigint unsigned NOT NULL AUTO_INCREMENT,
  tenant_id bigint unsigned NOT NULL DEFAULT 1 COMMENT '租户ID',
  spu_id    bigint unsigned NOT NULL COMMENT 'SPU ID',
  group_id  bigint unsigned NOT NULL COMMENT '规格组ID',
  sort      int NOT NULL DEFAULT 0 COMMENT '组内排序',
  PRIMARY KEY (id),
  UNIQUE KEY uk_spu_group (spu_id, group_id),
  KEY idx_group (group_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='SPU规格组关联';

-- ------------------------------------------------------------
-- 4. 加料组
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS t_addon_group (
  id         bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '加料组ID',
  tenant_id  bigint unsigned NOT NULL DEFAULT 1 COMMENT '租户ID',
  group_name varchar(64) NOT NULL COMMENT '加料组名称',
  min_select int NOT NULL DEFAULT 0 COMMENT '最少选择数',
  max_select int NOT NULL DEFAULT 0 COMMENT '最多选择数，0=不限',
  sort       int NOT NULL DEFAULT 0 COMMENT '排序，升序',
  is_template tinyint NOT NULL DEFAULT 1 COMMENT '1模板组 0私有组',
  created_at datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  deleted    tinyint NOT NULL DEFAULT 0 COMMENT '0正常 1删除',
  PRIMARY KEY (id),
  KEY idx_template (is_template, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='加料组';

-- ------------------------------------------------------------
-- 5. 加料项（price 必须 ≥0）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS t_addon_item (
  id        bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '加料项ID',
  tenant_id bigint unsigned NOT NULL DEFAULT 1 COMMENT '租户ID',
  group_id  bigint unsigned NOT NULL COMMENT '加料组ID',
  item_name varchar(64) NOT NULL COMMENT '加料名称',
  price     bigint NOT NULL DEFAULT 0 COMMENT '加料价（分），必须≥0',
  stock     int NULL COMMENT '加料库存；NULL=不限',
  sort      int NOT NULL DEFAULT 0 COMMENT '排序，升序',
  status    tinyint NOT NULL DEFAULT 0 COMMENT '0启用 1停用',
  created_at datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  deleted   tinyint NOT NULL DEFAULT 0 COMMENT '0正常 1删除',
  PRIMARY KEY (id),
  KEY idx_group (group_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='加料项';

-- ------------------------------------------------------------
-- 6. SPU-加料组关联
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS t_spu_addon_rel (
  id        bigint unsigned NOT NULL AUTO_INCREMENT,
  tenant_id bigint unsigned NOT NULL DEFAULT 1 COMMENT '租户ID',
  spu_id    bigint unsigned NOT NULL COMMENT 'SPU ID',
  group_id  bigint unsigned NOT NULL COMMENT '加料组ID',
  PRIMARY KEY (id),
  UNIQUE KEY uk_spu_addon (spu_id, group_id),
  KEY idx_group (group_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='SPU加料组关联';

-- ------------------------------------------------------------
-- 7. 估清记录（不做物理删除）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS t_sold_out_record (
  id            bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '估清记录ID',
  tenant_id     bigint unsigned NOT NULL DEFAULT 1 COMMENT '租户ID',
  shop_id       bigint unsigned NOT NULL COMMENT '门店ID',
  sku_id        bigint unsigned NOT NULL COMMENT 'SKU ID',
  spu_id        bigint unsigned NOT NULL COMMENT 'SPU ID',
  reason        varchar(128) NOT NULL COMMENT '估清原因',
  sold_out_at   datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '估清时间',
  restored_at   datetime(3) NULL COMMENT '恢复时间；NULL=仍估清',
  operator_id   bigint unsigned NULL COMMENT '操作人账号ID',
  operator_name varchar(64) NULL COMMENT '操作人名称',
  created_at    datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_shop_time (shop_id, sold_out_at),
  KEY idx_sku (sku_id, restored_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='估清记录';

-- ------------------------------------------------------------
-- 8. 商品评价
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS t_product_review (
  id             bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '评价ID',
  tenant_id      bigint unsigned NOT NULL DEFAULT 1 COMMENT '租户ID',
  order_id       bigint unsigned NOT NULL COMMENT '订单ID',
  order_item_id  bigint unsigned NOT NULL COMMENT '订单明细ID',
  user_id        bigint unsigned NOT NULL COMMENT '评价用户ID',
  shop_id        bigint unsigned NOT NULL COMMENT '门店ID',
  spu_id         bigint unsigned NOT NULL COMMENT 'SPU ID',
  sku_id         bigint unsigned NOT NULL COMMENT 'SKU ID',
  rider_id       bigint unsigned NULL COMMENT '骑手ID（骑手评价预留）',
  overall_score  tinyint NOT NULL COMMENT '综合评分 1~5',
  taste_score    tinyint NULL COMMENT '口味评分 1~5',
  package_score  tinyint NULL COMMENT '包装评分 1~5',
  delivery_score tinyint NULL COMMENT '配送评分 1~5',
  content        varchar(500) NULL COMMENT '评价内容（纯文本转义）',
  images_json    json NULL COMMENT '图片URL数组',
  tags_json      json NULL COMMENT '标签数组',
  is_anonymous   tinyint NOT NULL DEFAULT 0 COMMENT '1匿名',
  status         tinyint NOT NULL DEFAULT 0 COMMENT '0待审 1已通过 2已隐藏',
  top_flag       tinyint NOT NULL DEFAULT 0 COMMENT '1置顶',
  merchant_reply varchar(500) NULL COMMENT '商家回复（纯文本转义）',
  reply_at       datetime(3) NULL COMMENT '回复时间',
  append_content varchar(500) NULL COMMENT '追评内容',
  append_at      datetime(3) NULL COMMENT '追评时间',
  created_at     datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at     datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  deleted        tinyint NOT NULL DEFAULT 0 COMMENT '0正常 1删除',
  PRIMARY KEY (id),
  UNIQUE KEY uk_order_item (order_item_id),
  KEY idx_shop_status (shop_id, status, created_at),
  KEY idx_spu (spu_id, status),
  KEY idx_user (user_id, created_at),
  KEY idx_rider (rider_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='商品评价';

-- ------------------------------------------------------------
-- 9. 评价标签库
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS t_review_tag (
  id         bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '标签ID',
  tenant_id  bigint unsigned NOT NULL DEFAULT 1 COMMENT '租户ID',
  tag_name   varchar(32) NOT NULL COMMENT '标签名称',
  tag_type   tinyint NOT NULL DEFAULT 1 COMMENT '1好评 2差评',
  sort       int NOT NULL DEFAULT 0 COMMENT '排序，升序',
  status     tinyint NOT NULL DEFAULT 0 COMMENT '0启用 1停用',
  created_at datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_type_sort (tag_type, sort)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='评价标签';

-- ------------------------------------------------------------
-- 10. 素材分组
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS t_media_group (
  id         bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '素材分组ID',
  tenant_id  bigint unsigned NOT NULL DEFAULT 1 COMMENT '租户ID',
  group_name varchar(64) NOT NULL COMMENT '分组名称',
  parent_id  bigint unsigned NOT NULL DEFAULT 0 COMMENT '父分组，0为根',
  sort       int NOT NULL DEFAULT 0 COMMENT '排序，升序',
  status     tinyint NOT NULL DEFAULT 0 COMMENT '0启用 1停用',
  created_at datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  deleted    tinyint NOT NULL DEFAULT 0 COMMENT '0正常 1删除',
  PRIMARY KEY (id),
  KEY idx_parent_sort (parent_id, sort)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='素材分组';

-- ------------------------------------------------------------
-- 11. 素材项（引用 t_file）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS t_media_item (
  id         bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '素材ID',
  tenant_id  bigint unsigned NOT NULL DEFAULT 1 COMMENT '租户ID',
  group_id   bigint unsigned NOT NULL COMMENT '素材分组ID',
  file_id    bigint unsigned NOT NULL COMMENT 't_file.id',
  media_type tinyint NOT NULL DEFAULT 1 COMMENT '1图片 2视频',
  sort       int NOT NULL DEFAULT 0 COMMENT '排序，升序',
  status     tinyint NOT NULL DEFAULT 0 COMMENT '0启用 1停用',
  created_at datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  deleted    tinyint NOT NULL DEFAULT 0 COMMENT '0正常 1删除',
  PRIMARY KEY (id),
  KEY idx_group_sort (group_id, sort),
  KEY idx_file (file_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='素材项';

-- ------------------------------------------------------------
-- 12. 种子：规格模板（杯型/温度/糖度）
-- ------------------------------------------------------------
INSERT INTO t_spec_group (id, tenant_id, group_name, group_type, is_required, sort, is_template)
VALUES
  (1, 1, '杯型', 1, 1, 1, 1),
  (2, 1, '温度', 1, 1, 2, 1),
  (3, 1, '糖度', 1, 1, 3, 1)
ON DUPLICATE KEY UPDATE id = id;

INSERT INTO t_spec_option (id, tenant_id, group_id, option_name, price_delta, sort, status)
VALUES
  (1, 1, 1, '小杯', -200, 1, 0),
  (2, 1, 1, '中杯', 0,    2, 0),
  (3, 1, 1, '大杯', 300,  3, 0),
  (4, 1, 2, '常温', 0,    1, 0),
  (5, 1, 2, '少冰', 0,    2, 0),
  (6, 1, 2, '去冰', 0,    3, 0),
  (7, 1, 3, '全糖', 0,    1, 0),
  (8, 1, 3, '七分糖', 0,  2, 0),
  (9, 1, 3, '三分糖', 0,  3, 0),
  (10, 1, 3, '无糖', 0,   4, 0)
ON DUPLICATE KEY UPDATE id = id;

-- 冰柠檬茶（SPU 2）绑定三组规格
INSERT INTO t_spu_spec_rel (tenant_id, spu_id, group_id, sort)
VALUES
  (1, 2, 1, 1),
  (1, 2, 2, 2),
  (1, 2, 3, 3)
ON DUPLICATE KEY UPDATE id = id;

-- ------------------------------------------------------------
-- 13. 种子：加料模板
-- ------------------------------------------------------------
INSERT INTO t_addon_group (id, tenant_id, group_name, min_select, max_select, sort, is_template)
VALUES
  (1, 1, '加料', 0, 3, 1, 1)
ON DUPLICATE KEY UPDATE id = id;

INSERT INTO t_addon_item (id, tenant_id, group_id, item_name, price, stock, sort, status)
VALUES
  (1, 1, 1, '珍珠', 200, NULL, 1, 0),
  (2, 1, 1, '椰果', 200, NULL, 2, 0),
  (3, 1, 1, '布丁', 300, NULL, 3, 0)
ON DUPLICATE KEY UPDATE id = id;

INSERT INTO t_spu_addon_rel (tenant_id, spu_id, group_id)
VALUES
  (1, 2, 1)
ON DUPLICATE KEY UPDATE id = id;

-- ------------------------------------------------------------
-- 14. 种子：评价标签
-- ------------------------------------------------------------
INSERT INTO t_review_tag (id, tenant_id, tag_name, tag_type, sort, status)
VALUES
  (1, 1, '分量足', 1, 1, 0),
  (2, 1, '味道好', 1, 2, 0),
  (3, 1, '包装好', 1, 3, 0),
  (4, 1, '配送快', 1, 4, 0),
  (5, 1, '分量少', 2, 1, 0),
  (6, 1, '味道差', 2, 2, 0)
ON DUPLICATE KEY UPDATE id = id;

-- ------------------------------------------------------------
-- 15. 商品中心菜单与权限码（V9 占用 63~71，本卡从 72 起）
-- ------------------------------------------------------------
INSERT INTO t_sys_menu
  (id, tenant_id, parent_id, menu_name, menu_type, path, component, perms, icon, sort, visible, status)
VALUES
  (72, 1, 3, '规格模板', 2, '/product/spec-template',  'product/spec-template/index',  'product:template:list', 'goods', 3, 0, 0),
  (73, 1, 72, '模板新增', 3, NULL, NULL, 'product:template:create', NULL, 1, 1, 0),
  (74, 1, 72, '模板修改', 3, NULL, NULL, 'product:template:update', NULL, 2, 1, 0),
  (75, 1, 72, '模板删除', 3, NULL, NULL, 'product:template:delete', NULL, 3, 1, 0),
  (76, 1, 3, '估清管理', 2, '/product/sold-out',      'product/sold-out/index',       'product:soldout:list',  'goods', 4, 0, 0),
  (77, 1, 3, '评价管理', 2, '/product/review',        'product/review/index',         'product:review:list',   'goods', 5, 0, 0),
  (78, 1, 77, '评价审核', 3, NULL, NULL, 'product:review:audit', NULL, 1, 1, 0),
  (79, 1, 3, '素材库',   2, '/product/media',         'product/media/index',          'product:media:list',    'goods', 6, 0, 0),
  (80, 1, 79, '素材新增', 3, NULL, NULL, 'product:media:create', NULL, 1, 1, 0),
  (81, 1, 79, '素材修改', 3, NULL, NULL, 'product:media:update', NULL, 2, 1, 0),
  (82, 1, 79, '素材删除', 3, NULL, NULL, 'product:media:delete', NULL, 3, 1, 0)
ON DUPLICATE KEY UPDATE id = id;

INSERT INTO t_sys_role_menu (tenant_id, role_id, menu_id)
SELECT 1, 1, id FROM t_sys_menu WHERE tenant_id = 1 AND id BETWEEN 72 AND 82
ON DUPLICATE KEY UPDATE t_sys_role_menu.id = t_sys_role_menu.id;

INSERT INTO t_sys_role_menu (tenant_id, role_id, menu_id)
SELECT 1, 3, id FROM t_sys_menu WHERE tenant_id = 1 AND id BETWEEN 72 AND 82
ON DUPLICATE KEY UPDATE t_sys_role_menu.id = t_sys_role_menu.id;
