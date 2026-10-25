-- ============================================================
-- 数创星光外卖 种子数据补齐：演示商家
-- Flyway V20 / MySQL 8.0 / utf8mb4
-- 缺口：V4 的 t_sys_user(id=2 merchant1、id=4 merchant001) 已写入 merchant_id=1、shop_id=1，
--       V8 的 t_shop(id=1) 也挂在 merchant_id=1，但 V7 只建了 t_merchant 表结构，
--       从未插入 id=1 的商家行，导致商家端 GET /api/v1/merchant/shops 抛 70001「商家不存在」。
-- 说明：
--   1. status 取 2（已通过）。t_merchant.status 语义见 MerchantStatus：
--      0草稿 1待审核 2已通过 3已驳回 4已停用；ShopServiceImpl#requireMerchant
--      只放行 2，写 1（待审核）会抛 MERCHANT_STATUS_INVALID。
--   2. contact_phone 为明文。V7 只要求 legal_id_card_cipher / bank_account_no_cipher
--      为 AES-GCM 密文；MerchantProfileServiceImpl#updateProfile 对 contact_phone
--      按明文手机号（^1[3-9]\d{9}$）校验并写库。
--   3. t_merchant.contact_phone 只有普通索引 idx_phone，无唯一约束，不会与既有数据冲突；
--      唯一键 uk_merchant_no 由 M0001 占用。
--   4. 可重复执行：INSERT ... ON DUPLICATE KEY UPDATE id = id，只补缺失行，不覆盖既有行。
-- ============================================================

SET NAMES utf8mb4;
SET time_zone = '+08:00';

INSERT INTO t_merchant
  (id, tenant_id, merchant_no, merchant_name, contact_name, contact_phone,
   status, commission_rate, level, opened_at, version, deleted)
VALUES
  (1, 1, 'M0001', '演示商家', '张掌柜', '13800138000',
   2, 0.0600, 1, CURRENT_TIMESTAMP(3), 0, 0)
ON DUPLICATE KEY UPDATE id = id;
