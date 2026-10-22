package com.soloeats.product.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.soloeats.product.domain.entity.ProductReview;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

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
}
