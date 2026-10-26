package com.soloeats.content.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.soloeats.content.domain.entity.Banner;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface BannerMapper extends BaseMapper<Banner> {

    /** 用户端按广告位取生效中的 Banner，时间条件在 SQL 中完成。 */
    @Select("""
            SELECT *
              FROM t_banner
             WHERE slot_code = #{slotCode}
               AND status = 1
               AND deleted = 0
               AND effective_from <= #{now}
               AND (effective_to IS NULL OR effective_to >= #{now})
             ORDER BY sort ASC, id ASC
            """)
    List<Banner> selectActiveBySlot(@Param("slotCode") String slotCode,
                                    @Param("now") LocalDateTime now);

    /** 首页一次性取 HOME_TOP / HOME_POPUP，避免按楼层循环查询。 */
    @Select("""
            SELECT *
              FROM t_banner
             WHERE slot_code IN ('HOME_TOP', 'HOME_POPUP')
               AND status = 1
               AND deleted = 0
               AND effective_from <= #{now}
               AND (effective_to IS NULL OR effective_to >= #{now})
             ORDER BY slot_code ASC, sort ASC, id ASC
            """)
    List<Banner> selectActiveForHome(@Param("now") LocalDateTime now);

    /** 曝光数由 Redis 汇总后定时刷回，数据库侧只做原子累加。 */
    @Update("""
            UPDATE t_banner
               SET view_count = view_count + #{delta}
             WHERE id = #{id}
               AND deleted = 0
            """)
    int increaseViewCount(@Param("id") Long id, @Param("delta") long delta);
}
