package com.soloeats.member.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.soloeats.member.domain.entity.MemberTagRel;
import lombok.Data;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface MemberTagRelMapper extends BaseMapper<MemberTagRel> {

    /**
     * 各标签绑定人数。一次分组查询代替按标签逐条 count，避免标签列表 N+1。
     */
    @Select("SELECT tag_id AS tagId, COUNT(*) AS memberCount "
            + "FROM t_member_tag_rel GROUP BY tag_id")
    List<TagMemberCount> countMembersByTag();

    /**
     * 全量覆盖打标时的批量插入。调用方保证 items 非空且已去重。
     */
    @Insert("<script>"
            + "INSERT INTO t_member_tag_rel (user_id, tag_id) VALUES "
            + "<foreach collection='items' item='item' separator=','>"
            + "(#{item.userId}, #{item.tagId})"
            + "</foreach>"
            + "</script>")
    int insertBatch(@Param("items") List<MemberTagRel> items);

    @Data
    class TagMemberCount {

        private Long tagId;

        private Long memberCount;
    }
}
