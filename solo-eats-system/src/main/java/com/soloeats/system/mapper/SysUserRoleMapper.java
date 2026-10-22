package com.soloeats.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.soloeats.system.domain.entity.SysUserRole;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface SysUserRoleMapper extends BaseMapper<SysUserRole> {

    /**
     * 账号已绑定的角色 ID，按角色排序返回，供后台回显与全量替换前比对。
     */
    @Select("""
            SELECT ur.role_id
            FROM t_sys_user_role ur
            INNER JOIN t_sys_role r
                    ON r.id = ur.role_id
                   AND r.tenant_id = ur.tenant_id
            WHERE ur.user_id = #{userId}
              AND ur.tenant_id = #{tenantId}
              AND r.deleted = 0
            ORDER BY r.sort ASC, r.id ASC
            """)
    List<Long> selectRoleIdsByUserId(@Param("userId") Long userId,
                                     @Param("tenantId") Long tenantId);
}
