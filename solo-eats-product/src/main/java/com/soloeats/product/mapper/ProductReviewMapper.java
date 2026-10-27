package com.soloeats.product.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.soloeats.product.domain.entity.ProductReview;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface ProductReviewMapper extends BaseMapper<ProductReview> {

    /**
     * 商家回复仅一次：条件更新 WHERE merchant_reply IS NULL，不先查后写。
     */
    @Update("""
            UPDATE t_product_review
            SET merchant_reply = #{reply},
                reply_at = NOW(3),
                updated_at = NOW(3)
            WHERE id = #{id}
              AND deleted = 0
              AND merchant_reply IS NULL
            """)
    int replyOnce(@Param("id") Long id, @Param("reply") String reply);

    /**
     * 追评仅一次：append_content 为空才允许写入。
     */
    @Update("""
            UPDATE t_product_review
            SET append_content = #{content},
                images_json = #{imagesJson},
                append_at = NOW(3),
                updated_at = NOW(3)
            WHERE id = #{id}
              AND deleted = 0
              AND append_content IS NULL
            """)
    int appendOnce(@Param("id") Long id,
                   @Param("content") String content,
                   @Param("imagesJson") String imagesJson);

    @Update("""
            UPDATE t_product_review
            SET status = #{status},
                top_flag = #{topFlag},
                updated_at = NOW(3)
            WHERE id = #{id}
              AND deleted = 0
            """)
    int audit(@Param("id") Long id,
              @Param("status") Integer status,
              @Param("topFlag") Integer topFlag);

    /**
     * 门店评分聚合：取近 {@code since} 之后、已审核通过的评价均值。
     * 无数据时各 AVG 返回 null（COUNT 为 0），调用方需据此保留门店原评分。
     */
    @Select("""
            SELECT COUNT(*)            AS review_count,
                   AVG(overall_score)  AS avg_overall,
                   AVG(taste_score)    AS avg_taste,
                   AVG(package_score)  AS avg_package,
                   AVG(delivery_score) AS avg_delivery
            FROM t_product_review
            WHERE shop_id = #{shopId}
              AND status = 1
              AND deleted = 0
              AND created_at >= #{since}
            """)
    ReviewRatingRow ratingOf(@Param("shopId") Long shopId,
                             @Param("since") LocalDateTime since);

    /** 定时兜底任务用：所有存在已审核评价的门店。 */
    @Select("""
            SELECT DISTINCT shop_id
            FROM t_product_review
            WHERE status = 1
              AND deleted = 0
            """)
    List<Long> reviewedShopIds();
}
