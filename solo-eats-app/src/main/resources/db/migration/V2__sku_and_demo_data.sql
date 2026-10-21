-- ============================================================
-- 数创星光外卖 P0 商品主数据补充 + 下单联调数据
-- 说明：V1 只定义了库存表，没有 SKU 主数据；本脚本补充 t_sku，
--       并灌入下单接口联调所需的 SKU、库存、优惠券。
-- 本脚本可重复执行（CREATE TABLE IF NOT EXISTS + 重复键 no-op）：
--   只补齐缺失的演示行，不覆盖已有库存、销量与优惠券状态；
--   空库由 Flyway 依次执行 V1/V2，已有库保持 FLYWAY_ENABLED=false。
-- ============================================================

SET NAMES utf8mb4;
SET time_zone = '+08:00';

CREATE TABLE IF NOT EXISTS t_sku (
  id          bigint unsigned NOT NULL AUTO_INCREMENT,
  shop_id     bigint unsigned NOT NULL,
  spu_id      bigint unsigned NOT NULL,
  sku_name    varchar(128) NOT NULL,
  spec_json   json NULL,
  image_url   varchar(512) NULL,
  price       bigint NOT NULL COMMENT '售价，分',
  status      tinyint NOT NULL DEFAULT 0 COMMENT '0上架 1下架',
  created_at  datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at  datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_shop_status (shop_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='SKU主数据';

INSERT INTO t_sku (id, shop_id, spu_id, sku_name, price, status)
VALUES
  (1001, 1, 1, '招牌牛肉面', 2800, 0),
  (1002, 1, 2, '冰柠檬茶', 1200, 0)
ON DUPLICATE KEY UPDATE id = id;

INSERT INTO t_sku_stock (shop_id, sku_id, available_qty, locked_qty, sold_qty, sold_out)
VALUES
  (1, 1001, 100, 0, 0, 0),
  (1, 1002, 50, 0, 0, 0)
ON DUPLICATE KEY UPDATE id = id;

INSERT INTO t_coupon_template
  (id, name, coupon_type, threshold_amount, discount_amount, bearer, total_qty, issued_qty, status)
VALUES
  (2001, '满30减5', 1, 3000, 500, 1, 1000, 1, 0)
ON DUPLICATE KEY UPDATE id = id;

INSERT INTO t_coupon_template
  (id, name, coupon_type, threshold_amount, discount_amount, bearer, total_qty, issued_qty, status)
VALUES
  (2003, '满30减5-商家券', 1, 3000, 500, 2, 1000, 1, 0)
ON DUPLICATE KEY UPDATE id = id;

INSERT INTO t_user_coupon
  (id, user_id, template_id, status, order_id, locked_at, used_at, expire_at, version)
VALUES
  (3001, 1, 2001, 0, NULL, NULL, NULL, DATE_ADD(NOW(3), INTERVAL 30 DAY), 0),
  (3003, 1, 2003, 0, NULL, NULL, NULL, DATE_ADD(NOW(3), INTERVAL 30 DAY), 0)
ON DUPLICATE KEY UPDATE id = id;
