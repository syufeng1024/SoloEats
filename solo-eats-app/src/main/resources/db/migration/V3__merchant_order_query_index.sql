-- ============================================================
-- 商家订单列表查询索引
-- 查询条件：merchant_id = ? [AND status = ?] ORDER BY created_at DESC
-- 现有 idx_shop_status 以 shop_id 开头，无法覆盖仅按商家查询的场景。
-- 只新增索引，不修改已有列，向下兼容。
-- 已有库通常设置 FLYWAY_ENABLED=false，需要手动执行本文件。
-- ============================================================

CREATE INDEX idx_merchant_status_created
    ON t_order (merchant_id, status, created_at);
