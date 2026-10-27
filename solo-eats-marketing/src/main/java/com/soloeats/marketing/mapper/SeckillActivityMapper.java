package com.soloeats.marketing.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.soloeats.marketing.domain.entity.SeckillActivity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface SeckillActivityMapper extends BaseMapper<SeckillActivity> {

    /**
     * C 端可展示场次：未人工结束（status 0/1）且尚未结束。
     * status=0 的场次可能是「已到开卖时间但快照未翻转」，因此 reserve 与视图统一按时间窗推导状态，
     * 本查询只用于排除人工结束（2）与已过结束时间的场次。
     * tenant_id 由 TenantLineInnerInterceptor 统一追加，禁止在 SQL 里手写。
     */
    @Select("""
            SELECT *
            FROM t_seckill_activity
            WHERE deleted = 0
              AND status IN (0, 1)
              AND session_end >= #{at}
            ORDER BY session_start ASC, id ASC
            """)
    List<SeckillActivity> selectDisplayableSessions(@Param("at") LocalDateTime at);
}
