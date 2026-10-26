package com.soloeats.service.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.soloeats.service.domain.entity.Feedback;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

@Mapper
public interface FeedbackMapper extends BaseMapper<Feedback> {

    /** 同一条反馈只允许回复一次：status = 0 进 WHERE。 */
    @Update("UPDATE t_feedback "
            + "SET status = 1, reply = #{reply}, reply_at = #{replyAt}, "
            + "    updated_at = CURRENT_TIMESTAMP(3) "
            + "WHERE id = #{id} AND status = 0")
    int reply(@Param("id") Long id,
              @Param("reply") String reply,
              @Param("replyAt") LocalDateTime replyAt);
}
