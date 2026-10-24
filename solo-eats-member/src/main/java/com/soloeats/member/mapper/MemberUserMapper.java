package com.soloeats.member.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.soloeats.member.domain.entity.MemberUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface MemberUserMapper extends BaseMapper<MemberUser> {

    /**
     * 后台会员分页。手机号检索固定走 phone_mask 后四位模糊匹配，
     * 不提供 phone_cipher / phone_hash 的明文检索入口。
     */
    @Select("<script>"
            + "SELECT * FROM t_member_user WHERE deleted = 0 "
            + "<if test='status != null'> AND status = #{status}</if>"
            + "<if test='phoneSuffix != null'>"
            + " AND phone_mask LIKE CONCAT('%', #{phoneSuffix}, '%')"
            + "</if>"
            + "<if test='levelId != null'> AND member_level_id = #{levelId}</if>"
            + " ORDER BY id DESC"
            + "</script>")
    Page<MemberUser> selectAdminPage(Page<MemberUser> page,
                                     @Param("status") Integer status,
                                     @Param("phoneSuffix") String phoneSuffix,
                                     @Param("levelId") Long levelId);

    /**
     * 后台状态变更。条件固定带 deleted = 0，影响行数为 0 即用户不存在或已删除；
     * 返回 0 由调用方转 80001，不做先 SELECT 再 UPDATE。
     */
    @Update("UPDATE t_member_user SET status = #{status}, version = version + 1 "
            + "WHERE id = #{id} AND deleted = 0")
    int updateStatus(@Param("id") Long id, @Param("status") Integer status);
}
