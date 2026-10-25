-- ============================================================
-- 数创星光外卖 种子数据补齐：演示门店及其营业时段/配送围栏/结算账户
-- Flyway V21 / MySQL 8.0 / utf8mb4
-- 缺口：V2 的 t_sku(shop_id=1)、V9 的 t_shop_spu/t_shop_category(shop_id=1)、
--       V20 的 t_merchant(id=1) 都依赖门店 1，但 V1~V20 从未插入 t_shop 行。
--       按 Flyway 从空库重建后，用户端 GET /api/v1/c/shops 查不到营业门店，
--       商品数据成为孤儿，整条用户下单链路断开。
-- 说明：
--   1. 门店 status 语义见 ShopStatus：0休息 1营业 2打烊 3停用，这里取 1（营业）。
--   2. delivery_types 是位掩码：1平台配送 2到店自取。演示门店只开配送（1），
--      因此 pickup_enabled 保持 0；未配置自取却把 pickup_enabled 置 1，
--      delivery-check?fulfillmentType=2 会在 SHOP_PICKUP_DISABLED 之外产生误导。
--   3. 营业时段 day_of_week 1~7 对应周一~周日，09:00~22:00 不跨天（cross_day=0）。
--   4. 配送围栏 t_shop_delivery_area：area_type=1 圆形，status=0 为启用。
--      ShopGeoServiceImpl#inRange 优先读启用围栏，未配置时回落 t_shop.delivery_radius_m。
--   5. t_merchant_settlement_account.bank_account_no_cipher 为 NOT NULL 且只允许
--      AES-GCM 密文。Flyway 执行期拿不到运行时 PII_KEY（当前未配置），无法生成密文；
--      这里写入非明文占位符。读取路径 MerchantProfileServiceImpl#maskBankAccountSafely
--      捕获解密异常并降级返回 "****"，不会让商家资料接口 500。
--      真实卡号必须由商家在商家中心重新录入，走 PiiCipher.encrypt 落库。
--   6. 全部语句 INSERT ... ON DUPLICATE KEY UPDATE id = id，只补缺失行，不覆盖既有行。
--      t_shop_business_hour / t_shop_delivery_area 无业务唯一键，故显式指定主键保证幂等。
-- ============================================================

SET NAMES utf8mb4;
SET time_zone = '+08:00';

-- ------------------------------------------------------------
-- 1. 演示门店（V2/V9 的 shop_id=1 商品数据挂载点）
-- ------------------------------------------------------------
INSERT INTO t_shop
  (id, tenant_id, merchant_id, shop_no, shop_name, city, district, address, lng, lat,
   status, delivery_types, min_order_amount, delivery_fee, packaging_fee, prep_minutes,
   delivery_radius_m, rating, rating_taste, rating_package, rating_delivery,
   month_sales, sort_weight, pickup_enabled, settle_account_id, version, deleted)
VALUES
  (1, 1, 1, 'S0001', '演示门店', '北京市', '朝阳区', '望京SOHO T1', 116.470000, 39.990000,
   1, 1, 0, 0, 0, 15,
   3000, 5.00, 5.00, 5.00, 5.00,
   0, 0, 0, 2101, 0, 0)
ON DUPLICATE KEY UPDATE id = id;

-- ------------------------------------------------------------
-- 2. 营业时段：周一~周日 09:00~22:00（共 7 条）
-- ------------------------------------------------------------
INSERT INTO t_shop_business_hour
  (id, tenant_id, shop_id, day_of_week, start_time, end_time, cross_day)
VALUES
  (2101, 1, 1, 1, '09:00:00', '22:00:00', 0),
  (2102, 1, 1, 2, '09:00:00', '22:00:00', 0),
  (2103, 1, 1, 3, '09:00:00', '22:00:00', 0),
  (2104, 1, 1, 4, '09:00:00', '22:00:00', 0),
  (2105, 1, 1, 5, '09:00:00', '22:00:00', 0),
  (2106, 1, 1, 6, '09:00:00', '22:00:00', 0),
  (2107, 1, 1, 7, '09:00:00', '22:00:00', 0)
ON DUPLICATE KEY UPDATE id = id;

-- ------------------------------------------------------------
-- 3. 商家 1 默认结算账户（status=1 生效，is_default=1）
--    卡号密文占位，原因见文件头第 5 条
-- ------------------------------------------------------------
INSERT INTO t_merchant_settlement_account
  (id, tenant_id, merchant_id, account_name, bank_name, bank_branch,
   bank_account_no_cipher, alipay_account, effective_from, effective_to,
   is_default, status, deleted)
VALUES
  (2101, 1, 1, '演示商家', '中国银行', '北京望京支行',
   'PENDING_PII_ENCRYPTION', NULL, '2026-01-01', NULL,
   1, 1, 0)
ON DUPLICATE KEY UPDATE id = id;

-- ------------------------------------------------------------
-- 4. 配送围栏：以门店坐标为圆心、半径 3000 米的圆形区域
-- ------------------------------------------------------------
INSERT INTO t_shop_delivery_area
  (id, tenant_id, shop_id, area_type, center_lng, center_lat, radius_m,
   polygon_json, status, deleted)
VALUES
  (2101, 1, 1, 1, 116.470000, 39.990000, 3000,
   NULL, 0, 0)
ON DUPLICATE KEY UPDATE id = id;
