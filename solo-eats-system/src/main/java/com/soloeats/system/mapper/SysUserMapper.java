package com.soloeats.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.soloeats.system.domain.entity.SysUser;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface SysUserMapper extends BaseMapper<SysUser> {
}
