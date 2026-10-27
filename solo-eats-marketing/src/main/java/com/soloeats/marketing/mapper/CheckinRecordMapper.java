package com.soloeats.marketing.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.soloeats.marketing.domain.entity.CheckinRecord;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Mapper
public interface CheckinRecordMapper extends BaseMapper<CheckinRecord> {

    /**
     * 按 uk_user_date(user_id, checkin_date) 幂等插入。
     * 插入成功返回 1 并回填 id；撞唯一键被 IGNORE 返回 0。
     */
    @Insert("""
            INSERT IGNORE INTO t_checkin_record
              (user_id, checkin_date, continuous_days, point_award, created_at, updated_at)
            VALUES
              (#{userId}, #{checkinDate}, #{continuousDays}, #{pointAward}, #{createdAt}, #{updatedAt})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertIdempotent(CheckinRecord record);

    @Select("""
            SELECT *
            FROM t_checkin_record
            WHERE user_id = #{userId}
              AND checkin_date = #{checkinDate}
            LIMIT 1
            """)
    CheckinRecord selectByUserAndDate(@Param("userId") Long userId,
                                      @Param("checkinDate") LocalDate checkinDate);

    @Select("""
            SELECT *
            FROM t_checkin_record
            WHERE user_id = #{userId}
            ORDER BY checkin_date DESC
            LIMIT 1
            """)
    CheckinRecord selectLatest(@Param("userId") Long userId);

    /**
     * 当月补签次数。表无 type 列，用「签到日期早于创建日期」识别补签；
     * created_at 由应用显式写入，避免午夜跨天误判。
     */
    @Select("""
            SELECT COUNT(*)
            FROM t_checkin_record
            WHERE user_id = #{userId}
              AND created_at >= #{monthStart}
              AND created_at < #{nextMonthStart}
              AND checkin_date < DATE(created_at)
            """)
    int countCompensationsInMonth(@Param("userId") Long userId,
                                  @Param("monthStart") LocalDateTime monthStart,
                                  @Param("nextMonthStart") LocalDateTime nextMonthStart);

    /** 补签后修复后续连续天数，不改 point_award、不重复发奖。 */
    @Update("""
            UPDATE t_checkin_record
            SET continuous_days = #{continuousDays}, updated_at = #{now}
            WHERE id = #{id}
            """)
    int updateContinuousDays(@Param("id") Long id,
                             @Param("continuousDays") int continuousDays,
                             @Param("now") LocalDateTime now);
}
