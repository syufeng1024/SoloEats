-- ============================================================
-- 后台菜单：把 4 条历史菜单的 component 对齐到真实页面文件
-- Flyway V32 / MySQL 8.0 / utf8mb4
-- 说明：
--   1. V7/V8/V9/V24 种的这 4 条菜单，component 写的是 `xxx/list/index`、
--      `product/spu/index` 这类占位路径，src/views 下并没有对应文件，
--      实际页面是 admin/ 目录下的 PascalCase 组件。
--   2. 之前靠 router 的静态路由（/marketing/coupon、/product/spu）与
--      V31 后的别名路由（/store/list、/merchant/list）才能访问。
--      一旦静态路由调整，这些菜单就会落到 ViewPlaceholder。
--   3. 本脚本只 UPDATE component 字段，不动 path / perms / 层级，
--      使「菜单 component → 真实 .vue 文件」全量可解析。
--   4. 幂等：重复执行结果一致。
-- ============================================================

SET NAMES utf8mb4;
SET time_zone = '+08:00';

-- 56 商家管理（V7）：/merchant/list
UPDATE t_sys_menu SET component = 'admin/merchant/ApplyListView'
WHERE tenant_id = 1 AND id = 56 AND component = 'merchant/list/index';

-- 60 门店管理（V8）：/store/list
UPDATE t_sys_menu SET component = 'admin/store/ShopListView'
WHERE tenant_id = 1 AND id = 60 AND component = 'store/list/index';

-- 67 商品库（V9）：/product/spu
UPDATE t_sys_menu SET component = 'admin/product/SpuListView'
WHERE tenant_id = 1 AND id = 67 AND component = 'product/spu/index';

-- 117 基础券（V24）：/marketing/coupon
UPDATE t_sys_menu SET component = 'admin/marketing/CouponView'
WHERE tenant_id = 1 AND id = 117 AND component = 'marketing/coupon/index';
