-- ============================================================
-- 数创星光外卖：C 端「我的收藏」（规格书 5.2.9）
-- Flyway V35 / MySQL 8.0 / utf8mb4
-- 说明：
--   1. 收藏是用户行为，归属 solo-eats-member；店铺/商品只存 target_id，不跨模块外键。
--   2. target_type：1 店铺，2 商品。同一用户对同一对象只能有一条记录。
--   3. 取消收藏为物理删除，因此不设 deleted 列；唯一键保证并发重复收藏只会落一条。
-- ============================================================

SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS t_favorite (
  id           bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '收藏ID',
  tenant_id    bigint unsigned NOT NULL DEFAULT 1 COMMENT '租户ID',
  user_id      bigint unsigned NOT NULL COMMENT '用户ID',
  target_type  tinyint NOT NULL COMMENT '1店铺 2商品',
  target_id    bigint unsigned NOT NULL COMMENT '收藏对象ID（t_shop.id / t_spu.id）',
  created_at   datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at   datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_user_target (user_id, target_type, target_id),
  KEY idx_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='C 端收藏（店铺/商品）';
