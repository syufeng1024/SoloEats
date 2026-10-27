package com.soloeats.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.soloeats.system.domain.entity.SysRoleMenu;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface SysRoleMenuMapper extends BaseMapper<SysRoleMenu> {

    /**
     * 角色已授权的菜单 ID（含按钮），供授权弹窗回显。
     */
    @Select("""
            SELECT rm.menu_id
            FROM t_sys_role_menu rm
            INNER JOIN t_sys_menu m
                    ON m.id = rm.menu_id
                   AND m.tenant_id = rm.tenant_id
            WHERE rm.role_id = #{roleId}
              AND rm.tenant_id = #{tenantId}
              AND m.deleted = 0
            ORDER BY rm.menu_id ASC
            """)
    List<Long> selectMenuIdsByRoleId(@Param("roleId") Long roleId,
                                     @Param("tenantId") Long tenantId);

    /**
     * 账号经全部角色聚合后的菜单 ID（去重），供「当前用户菜单树」过滤。
     */
    @Select("""
            SELECT DISTINCT rm.menu_id
            FROM t_sys_role_menu rm
            INNER JOIN t_sys_user_role ur
                    ON ur.role_id = rm.role_id
                   AND ur.tenant_id = rm.tenant_id
            INNER JOIN t_sys_menu m
                    ON m.id = rm.menu_id
                   AND m.tenant_id = rm.tenant_id
            WHERE ur.user_id = #{userId}
              AND ur.tenant_id = #{tenantId}
              AND m.deleted = 0
            ORDER BY rm.menu_id ASC
            """)
    List<Long> selectMenuIdsByUserId(@Param("userId") Long userId,
                                     @Param("tenantId") Long tenantId);
}
