package com.soloeats.system.mapper;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Constants;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.soloeats.common.datascope.DataScope;
import com.soloeats.system.domain.entity.SysUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface SysUserMapper extends BaseMapper<SysUser> {

    /**
     * 后台账号列表：按当前平台角色的 data_scope 过滤。t_sys_user 以主键标识「本人」，
     * 因此 userColumn 配成 id；tenant_id / merchant_id / shop_id 同表可直接复用。
     */
    @DataScope(deptAlias = "u", userAlias = "u", userColumn = "id")
    @Select("SELECT * FROM t_sys_user u ${ew.customSqlSegment}")
    Page<SysUser> selectScopedPage(Page<SysUser> page, @Param(Constants.WRAPPER) Wrapper<SysUser> wrapper);
}
