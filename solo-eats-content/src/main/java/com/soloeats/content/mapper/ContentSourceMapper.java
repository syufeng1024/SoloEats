package com.soloeats.content.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 首页聚合只读数据源。
 *
 * <p>content 模块不能依赖 order / report，也没有「推荐店铺」「猜你喜欢」现成 service 方法。
 * 这里按 solo-eats-report 的 ReportSourceMapper 做法，直接对 t_shop / t_shop_spu / t_spu /
 * t_sku / t_sys_notice 写只读原生 SQL；不修改 store / product 的任何代码或实体。
 *
 * <p>所有查询都是单次批量（LIMIT 固定），禁止在楼层循环中逐条查库。
 */
@Mapper
public interface ContentSourceMapper {

    /**
     * 推荐店铺：t_shop.status=1（老板开关为营业）的门店，按综合权重与月售排序。
     * 营业时段（t_shop_business_hour）未叠加到聚合 SQL，避免每次首页请求做时段计算。
     */
    @Select("""
            SELECT id,
                   shop_name        AS shopName,
                   logo_url         AS logoUrl,
                   cover_url        AS coverUrl,
                   rating,
                   month_sales      AS monthSales,
                   min_order_amount AS minOrderAmount,
                   delivery_fee     AS deliveryFee,
                   prep_minutes     AS prepMinutes
              FROM t_shop
             WHERE deleted = 0
               AND status = 1
             ORDER BY sort_weight DESC, month_sales DESC, id DESC
             LIMIT #{limit}
            """)
    List<RecommendedShop> recommendedShops(@Param("limit") int limit);

    /**
     * 猜你喜欢：已上架 SPU + 门店已上架关系，按门店月售排序。
     * 价格取该 SPU 最低的在售 SKU 价（分）；没有 SKU 时为 NULL。
     */
    @Select("""
            SELECT s.id              AS spuId,
                   s.spu_name        AS spuName,
                   s.subtitle        AS subtitle,
                   s.main_image      AS mainImage,
                   sh.id             AS shopId,
                   sh.shop_name      AS shopName,
                   (SELECT MIN(k.price)
                      FROM t_sku k
                     WHERE k.spu_id = s.id
                       AND k.shop_id = ss.shop_id
                       AND k.status = 0) AS price,
                   ss.month_sales    AS monthSales
              FROM t_shop_spu ss
              JOIN t_spu s   ON s.id = ss.spu_id AND s.deleted = 0 AND s.status = 2
              JOIN t_shop sh ON sh.id = ss.shop_id AND sh.deleted = 0 AND sh.status = 1
             WHERE ss.deleted = 0
               AND ss.status = 0
               AND ss.audit_status IN (0, 2)
             ORDER BY ss.month_sales DESC, s.sort_weight DESC, s.id DESC
             LIMIT #{limit}
            """)
    List<RecommendedSpu> guessYouLike(@Param("limit") int limit);

    /** 公告条：复用 t_sys_notice，仅取已发布且未过期的公告。 */
    @Select("""
            SELECT id,
                   title,
                   publish_at AS publishAt
              FROM t_sys_notice
             WHERE deleted = 0
               AND status = 1
               AND target_type = 1
               AND (publish_at IS NULL OR publish_at <= #{now})
               AND (expire_at IS NULL OR expire_at > #{now})
             ORDER BY publish_at DESC, id DESC
             LIMIT #{limit}
            """)
    List<NoticeBrief> activeNotices(@Param("now") LocalDateTime now,
                                    @Param("limit") int limit);

    record RecommendedShop(Long id,
                           String shopName,
                           String logoUrl,
                           String coverUrl,
                           BigDecimal rating,
                           Integer monthSales,
                           Long minOrderAmount,
                           Long deliveryFee,
                           Integer prepMinutes) {
    }

    record RecommendedSpu(Long spuId,
                          String spuName,
                          String subtitle,
                          String mainImage,
                          Long shopId,
                          String shopName,
                          Long price,
                          Integer monthSales) {
    }

    record NoticeBrief(Long id, String title, LocalDateTime publishAt) {
    }
}
