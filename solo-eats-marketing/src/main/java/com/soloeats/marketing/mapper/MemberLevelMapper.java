package com.soloeats.marketing.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.soloeats.marketing.domain.entity.MemberLevel;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface MemberLevelMapper extends BaseMapper<MemberLevel> {

    /**
     * 启用中的等级，按成长值门槛从高到低。等级判定取第一个
     * {@code growth >= growth_threshold} 的档位。
     * 租户条件由 TenantLineInnerInterceptor 追加，SQL 内不手写。
     */
    @Select("""
            SELECT *
            FROM t_member_level
            WHERE status = 0
              AND deleted = 0
            ORDER BY growth_threshold DESC, level_no DESC
            """)
    List<MemberLevel> selectEnabledDesc();
}
