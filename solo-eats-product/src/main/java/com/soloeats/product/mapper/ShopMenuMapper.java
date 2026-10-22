package com.soloeats.product.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 用户端菜单聚合查询。固定两条 SQL：主菜单 + 加料组，禁止 N+1。
 */
@Mapper
public interface ShopMenuMapper {

    @Select("""
            SELECT c.id AS category_id, c.category_name, c.sort AS category_sort,
                   sp.spu_id, sp.price_override, sp.status AS shop_spu_status,
                   sp.sort_weight AS spu_sort,
                   p.spu_name, p.subtitle, p.main_image, p.tags_json, p.packaging_fee,
                   p.min_purchase_qty, p.limit_per_order, p.month_sales,
                   k.id AS sku_id, k.sku_name, k.spec_json, k.image_url AS sku_image, k.price,
                   st.available_qty, st.sold_out
            FROM t_shop_spu sp
            JOIN t_spu p ON p.id = sp.spu_id AND p.deleted = 0 AND p.status = 2
            JOIN t_category c ON c.id = p.category_id AND c.deleted = 0 AND c.status = 0
            JOIN t_sku k ON k.spu_id = sp.spu_id AND k.shop_id = sp.shop_id AND k.status = 0
            LEFT JOIN t_sku_stock st ON st.shop_id = k.shop_id AND st.sku_id = k.id
            WHERE sp.shop_id = #{shopId}
              AND sp.deleted = 0
              AND sp.status IN (0, 2)
              AND sp.audit_status IN (0, 2)
            ORDER BY COALESCE(c.sort, 999999), sp.sort_weight DESC, sp.id, k.id
            """)
    List<MenuRow> selectMenuRows(@Param("shopId") Long shopId);

    @Select("""
            <script>
            SELECT r.spu_id, g.id AS group_id, g.group_name, g.min_select, g.max_select,
                   g.sort AS group_sort, a.id AS item_id, a.item_name,
                   a.price AS item_price, a.stock AS item_stock, a.sort AS item_sort
            FROM t_spu_addon_rel r
            JOIN t_addon_group g ON g.id = r.group_id AND g.deleted = 0
            LEFT JOIN t_addon_item a ON a.group_id = g.id AND a.deleted = 0 AND a.status = 0
            WHERE r.spu_id IN
              <foreach collection="spuIds" item="spuId" open="(" separator="," close=")">#{spuId}</foreach>
            ORDER BY r.spu_id, g.sort, a.sort
            </script>
            """)
    List<MenuAddonRow> selectAddonRows(@Param("spuIds") List<Long> spuIds);
}
