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
}
