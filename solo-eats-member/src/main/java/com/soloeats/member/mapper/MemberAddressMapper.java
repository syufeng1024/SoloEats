package com.soloeats.member.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.soloeats.member.domain.entity.MemberAddress;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface MemberAddressMapper extends BaseMapper<MemberAddress> {

    /**
     * 清除同用户下的默认标记。设默认 / 新增默认地址前必须在同一事务内调用，
     * 保证任一时刻最多一条 is_default = 1。
     */
    @Update("UPDATE t_member_address SET is_default = 0 WHERE user_id = #{userId} AND is_default = 1")
    int clearDefault(@Param("userId") Long userId);
}
