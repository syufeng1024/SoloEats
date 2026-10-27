package com.soloeats.marketing.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.soloeats.marketing.domain.entity.MemberGrowthLog;
import com.soloeats.marketing.domain.entity.UserGrowthSnapshot;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface MemberGrowthLogMapper extends BaseMapper<MemberGrowthLog> {

    /**
     * 近 365 天成长值合计，含负值。没有流水返回 0，调用方不用判空。
     */
    @Select("""
            SELECT COALESCE(SUM(growth), 0)
            FROM t_member_growth_log
            WHERE user_id = #{userId}
              AND effective_at >= #{since}
            """)
    int sumGrowthSince(@Param("userId") Long userId, @Param("since") LocalDateTime since);

    /**
     * 等级重算的批量扫描：一次取 500 个用户，避免 selectList 全量用户。
     *
     * <p>用 LEFT JOIN 而不是只查成长值流水，保证没有流水的用户也会被重算
     * （其成长值归 0，应回落到第一档）。按 u.id 游标翻页，防止深分页。
     */
    @Select("""
            SELECT u.id AS user_id,
                   u.growth_value AS old_growth_value,
                   u.member_level_id AS old_member_level_id,
                   COALESCE(SUM(g.growth), 0) AS growth
            FROM t_member_user u
            LEFT JOIN t_member_growth_log g
                   ON g.user_id = u.id
                  AND g.effective_at >= #{since}
            WHERE u.deleted = 0
              AND u.id > #{lastId}
            GROUP BY u.id, u.growth_value, u.member_level_id
            ORDER BY u.id ASC
            LIMIT #{limit}
            """)
    List<UserGrowthSnapshot> selectUserGrowthBatch(@Param("since") LocalDateTime since,
                                                   @Param("lastId") long lastId,
                                                   @Param("limit") int limit);
}
