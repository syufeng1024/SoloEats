package com.soloeats.member.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.soloeats.member.domain.entity.MemberTag;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface MemberTagMapper extends BaseMapper<MemberTag> {

    /**
     * 后台会员详情用：一次取该用户的全部标签名。
     * 手写 SQL 不经过 @TableLogic，标签表的 deleted = 0 必须显式带上。
     */
    @Select("SELECT t.tag_name FROM t_member_tag t "
            + "JOIN t_member_tag_rel r ON r.tag_id = t.id "
            + "WHERE r.user_id = #{userId} AND t.deleted = 0 "
            + "ORDER BY t.id")
    List<String> findTagNamesByUserId(@Param("userId") Long userId);
}
