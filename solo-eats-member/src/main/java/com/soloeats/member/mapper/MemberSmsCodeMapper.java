package com.soloeats.member.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.soloeats.member.domain.entity.MemberSmsCode;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface MemberSmsCodeMapper extends BaseMapper<MemberSmsCode> {

    /**
     * 验证码一次性使用：条件更新保证并发下只有一个请求能把 used 从 0 改为 1。
     * 返回 0 表示已被其他请求使用，调用方按验证码无效处理。
     */
    @Update("UPDATE t_member_sms_code SET used = 1 WHERE id = #{id} AND used = 0")
    int markUsed(@Param("id") Long id);
}
