package com.soloeats.content.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.soloeats.content.domain.entity.HomePage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

@Mapper
public interface HomePageMapper extends BaseMapper<HomePage> {

    /**
     * 发布：version 作为乐观条件，并发发布只有一个能成功。
     * 手写 SQL 不经过 @TableLogic，故显式带 deleted = 0。
     */
    @Update("""
            UPDATE t_home_page
               SET status = 1,
                   publish_at = #{publishAt},
                   version = version + 1,
                   updated_at = CURRENT_TIMESTAMP(3)
             WHERE id = #{id}
               AND version = #{expectedVersion}
               AND deleted = 0
            """)
    int publish(@Param("id") Long id,
                @Param("expectedVersion") Integer expectedVersion,
                @Param("publishAt") LocalDateTime publishAt);

    /** 下线：仅已发布页面可下线，重复调用影响行数为 0。 */
    @Update("""
            UPDATE t_home_page
               SET status = 2,
                   updated_at = CURRENT_TIMESTAMP(3)
             WHERE id = #{id}
               AND status = 1
               AND deleted = 0
            """)
    int offline(@Param("id") Long id);
}
