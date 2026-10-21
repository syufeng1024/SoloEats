package com.soloeats.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.soloeats.system.domain.entity.SysMenu;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface SysMenuMapper extends BaseMapper<SysMenu> {

    /**
     * 登录时取当前账号可用权限码。权限码不入 JWT，后续阶段C 会写入 Redis。
     */
    @Select("""
            SELECT DISTINCT m.perms
            FROM t_sys_menu m
            INNER JOIN t_sys_role_menu rm
                    ON rm.menu_id = m.id
                   AND rm.tenant_id = m.tenant_id
            INNER JOIN t_sys_user_role ur
                    ON ur.role_id = rm.role_id
                   AND ur.tenant_id = rm.tenant_id
            WHERE ur.user_id = #{userId}
              AND ur.tenant_id = #{tenantId}
              AND m.status = 0
              AND m.deleted = 0
              AND m.perms IS NOT NULL
              AND m.perms <> ''
            ORDER BY m.perms ASC
            """)
    List<String> selectPermsByUserId(@Param("userId") Long userId,
                                     @Param("tenantId") Long tenantId);
}
