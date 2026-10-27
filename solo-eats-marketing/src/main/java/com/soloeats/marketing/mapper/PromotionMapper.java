package com.soloeats.marketing.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.soloeats.marketing.domain.entity.Promotion;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface PromotionMapper extends BaseMapper<Promotion> {

    /**
     * 平台级全平台满减：进行中 + 当前时间落在 start_at/end_at 内，按 priority DESC。
     * tenant_id 由 TenantLineInnerInterceptor 统一追加，禁止在 SQL 里手写。
     */
    @Select("""
            SELECT *
            FROM t_promotion
            WHERE promotion_type = 1
              AND bearer = 1
              AND scope_type = 1
              AND status = 1
              AND deleted = 0
              AND start_at <= #{at}
              AND end_at >= #{at}
            ORDER BY priority DESC, id ASC
            """)
    List<Promotion> selectActivePlatformPromotions(@Param("at") LocalDateTime at);

    /**
     * 阶段A 候选活动：进行中 + 当前时间在区间内，不限活动类型/承担方/范围。
     * 类型、范围、叠加开关由 {@code PromotionServiceImpl} 逐条判定，因为范围判定依赖
     * 本次订单的 sku/品类，无法在一条 SQL 里表达。
     * tenant_id 由 TenantLineInnerInterceptor 统一追加，禁止在 SQL 里手写。
     */
    @Select("""
            SELECT *
            FROM t_promotion
            WHERE status = 1
              AND deleted = 0
              AND start_at <= #{at}
              AND end_at >= #{at}
            ORDER BY priority DESC, id ASC
            """)
    List<Promotion> selectCandidates(@Param("at") LocalDateTime at);

    /**
     * 按门店反查所属商家，供商家满减（scopeType=2）判定使用。
     * C 端请求体不接收 merchantId（可伪造），一律服务端反查；门店不存在或已删除返回 null。
     * 跨表只读，不引入 store 模块实体。
     */
    @Select("SELECT merchant_id FROM t_shop WHERE id = #{shopId} AND deleted = 0")
    Long selectMerchantIdByShop(@Param("shopId") Long shopId);
}
