package com.soloeats.marketing.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.soloeats.marketing.domain.entity.PromotionScope;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 活动范围读写。除子表 CRUD 外，另含四个只读反查：
 * 活动覆盖的 sku/品类，以及订单 sku 反查品类、spu 反查 sku。
 *
 * <p>反查一律写成 {@code @Select} 直接读表，不引入 product 模块的实体与 mapper：
 * t_sku 无 tenant_id（已在 {@code MybatisPlusConfig.IGNORE_TABLES} 中登记），
 * t_spu / t_promotion_scope 的 tenant_id 由 TenantLineInnerInterceptor 统一追加。
 */
@Mapper
public interface PromotionScopeMapper extends BaseMapper<PromotionScope> {

    /**
     * 活动覆盖的 sku 集合：ref_type=5 直接存 sku，ref_type=4 存 spu 需展开成 sku。
     * 供 scopeType=5（指定商品）与本次订单 sku 集合求交集。
     */
    @Select("""
            SELECT DISTINCT sku.id
            FROM t_sku sku
            JOIN t_promotion_scope s ON s.promotion_id = #{promotionId}
            WHERE (s.ref_type = 5 AND s.ref_id = sku.id)
               OR (s.ref_type = 4 AND s.ref_id = sku.spu_id)
            """)
    List<Long> selectScopeSkuIds(@Param("promotionId") Long promotionId);

    /**
     * 活动覆盖的品类 ID（ref_type=3）。供 scopeType=4（指定品类）求交集。
     */
    @Select("""
            SELECT DISTINCT ref_id
            FROM t_promotion_scope
            WHERE promotion_id = #{promotionId}
              AND ref_type = 3
            """)
    List<Long> selectScopeCategoryIds(@Param("promotionId") Long promotionId);

    /**
     * 本次订单 sku 集合所属品类。skuIds 不能为空，调用方需先判空——
     * 空集合会拼出 {@code IN ()} 语法错误。
     */
    @Select("""
            <script>
            SELECT DISTINCT spu.category_id
            FROM t_sku sku
            JOIN t_spu spu ON spu.id = sku.spu_id
            WHERE sku.id IN
            <foreach collection="skuIds" item="skuId" open="(" separator="," close=")">#{skuId}</foreach>
            </script>
            """)
    List<Long> selectCategoryIdsBySkuIds(@Param("skuIds") List<Long> skuIds);

    /**
     * spu 集合展开成 sku 集合。C 端活动列表只拿到 spuIds，需要先展开再判定商品范围。
     * spuIds 不能为空，调用方需先判空。
     */
    @Select("""
            <script>
            SELECT id
            FROM t_sku
            WHERE spu_id IN
            <foreach collection="spuIds" item="spuId" open="(" separator="," close=")">#{spuId}</foreach>
            </script>
            """)
    List<Long> selectSkuIdsBySpuIds(@Param("spuIds") List<Long> spuIds);

    /**
     * 活动的全部范围项，供后台详情回显。
     */
    @Select("""
            SELECT *
            FROM t_promotion_scope
            WHERE promotion_id = #{promotionId}
            ORDER BY ref_type ASC, ref_id ASC
            """)
    List<PromotionScope> selectByPromotionId(@Param("promotionId") Long promotionId);

    /**
     * 物理删除活动范围项。t_promotion_scope 是纯子表，无 deleted 列，编辑时先删后插；
     * uk_promo_ref 保证同一活动内 (ref_type, ref_id) 不重复。
     */
    @Delete("DELETE FROM t_promotion_scope WHERE promotion_id = #{promotionId}")
    int deleteByPromotionId(@Param("promotionId") Long promotionId);
}
