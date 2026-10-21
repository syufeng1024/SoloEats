package com.soloeats.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.soloeats.system.domain.entity.SysRole;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface SysRoleMapper extends BaseMapper<SysRole> {

    /**
     * 阶段A 只读角色码；user_role / role_menu 的写操作在阶段B 落地。
     */
    @Select("""
            SELECT r.role_code
            FROM t_sys_role r
            INNER JOIN t_sys_user_role ur
                    ON ur.role_id = r.id
                   AND ur.tenant_id = r.tenant_id
            WHERE ur.user_id = #{userId}
              AND ur.tenant_id = #{tenantId}
              AND r.status = 0
              AND r.deleted = 0
            ORDER BY r.sort ASC, r.id ASC
            """)
    List<String> selectRoleCodesByUserId(@Param("userId") Long userId,
                                         @Param("tenantId") Long tenantId);
}
