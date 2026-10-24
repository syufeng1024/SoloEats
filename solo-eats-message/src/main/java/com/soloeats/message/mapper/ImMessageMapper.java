package com.soloeats.message.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.soloeats.message.domain.entity.ImMessage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ImMessageMapper extends BaseMapper<ImMessage> {

    /**
     * 游标分页：按 id 倒序取 beforeId 之前的一页，服务层反转成时间正序返回。
     * 走 idx_session_time(session_id, created_at)，不偏移 OFFSET。
     */
    @Select("<script>SELECT * FROM t_im_message WHERE session_id = #{sessionId}"
            + "<if test='beforeId != null'> AND id &lt; #{beforeId}</if>"
            + " ORDER BY id DESC LIMIT #{limit}</script>")
    List<ImMessage> selectPage(@Param("sessionId") Long sessionId,
                               @Param("beforeId") Long beforeId,
                               @Param("limit") int limit);
}
