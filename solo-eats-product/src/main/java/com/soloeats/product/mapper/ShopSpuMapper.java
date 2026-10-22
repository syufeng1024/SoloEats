package com.soloeats.product.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.soloeats.product.domain.entity.ShopSpu;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ShopSpuMapper extends BaseMapper<ShopSpu> {

    /**
     * 门店商品分页：一次 JOIN 带出 SPU 名称/主图与最低 SKU 价，禁止循环回查。
     */
    @Select("""
            <script>
            SELECT sp.id, sp.shop_id, sp.spu_id, sp.price_override, sp.status, sp.audit_status,
                   sp.sort_weight, sp.month_sales, sp.suspended_until, sp.remark, sp.updated_at,
                   p.spu_name, p.main_image,
                   (SELECT MIN(k.price) FROM t_sku k
                     WHERE k.spu_id = sp.spu_id AND k.shop_id = sp.shop_id AND k.status = 0) AS sku_min_price
            FROM t_shop_spu sp
            JOIN t_spu p ON p.id = sp.spu_id AND p.deleted = 0
            WHERE sp.deleted = 0
              <if test="shopId != null">AND sp.shop_id = #{shopId}</if>
              <if test="status != null">AND sp.status = #{status}</if>
              <if test="auditStatus != null">AND sp.audit_status = #{auditStatus}</if>
              <if test="keyword != null and keyword != ''">
                AND p.spu_name LIKE CONCAT('%', #{keyword}, '%')
              </if>
            ORDER BY sp.sort_weight DESC, sp.id DESC
            </script>
            """)
    Page<ShopSpuRow> selectShopSpuPage(Page<ShopSpuRow> page,
                                       @Param("shopId") Long shopId,
                                       @Param("status") Integer status,
                                       @Param("auditStatus") Integer auditStatus,
                                       @Param("keyword") String keyword);
}
