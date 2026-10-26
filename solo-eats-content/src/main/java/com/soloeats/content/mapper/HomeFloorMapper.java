package com.soloeats.content.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.soloeats.content.domain.entity.HomeFloor;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface HomeFloorMapper extends BaseMapper<HomeFloor> {

    /** 后台/预览：全部未删除楼层，按 sort 升序。 */
    @Select("""
            SELECT *
              FROM t_home_floor
             WHERE page_id = #{pageId}
               AND deleted = 0
             ORDER BY sort ASC, id ASC
            """)
    List<HomeFloor> selectAllByPageId(@Param("pageId") Long pageId);

    /** 预览：只取启用楼层，不看生效时间（方便排期内容提前预览）。 */
    @Select("""
            SELECT *
              FROM t_home_floor
             WHERE page_id = #{pageId}
               AND status = 1
               AND deleted = 0
             ORDER BY sort ASC, id ASC
            """)
    List<HomeFloor> selectEnabledByPageId(@Param("pageId") Long pageId);

    /**
     * 用户端：状态与生效时间全部在 SQL 里过滤。
     * effective_to IS NULL 视为长期有效。
     */
    @Select("""
            SELECT *
              FROM t_home_floor
             WHERE page_id = #{pageId}
               AND status = 1
               AND deleted = 0
               AND effective_from <= #{now}
               AND (effective_to IS NULL OR effective_to >= #{now})
             ORDER BY sort ASC, id ASC
            """)
    List<HomeFloor> selectActiveByPageId(@Param("pageId") Long pageId,
                                         @Param("now") LocalDateTime now);

    @Update("""
            UPDATE t_home_floor
               SET sort = #{sort},
                   updated_at = CURRENT_TIMESTAMP(3)
             WHERE id = #{id}
               AND deleted = 0
            """)
    int updateSort(@Param("id") Long id, @Param("sort") Integer sort);
}
