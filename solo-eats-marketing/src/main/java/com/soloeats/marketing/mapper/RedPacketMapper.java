package com.soloeats.marketing.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.soloeats.marketing.domain.entity.RedPacket;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface RedPacketMapper extends BaseMapper<RedPacket> {

    @Select("""
            SELECT *
            FROM t_red_packet
            WHERE deleted = 0
              AND status IN (0, 1)
              AND start_at <= #{now}
              AND end_at > #{now}
              AND remain_qty > 0
            ORDER BY end_at ASC, id ASC
            """)
    List<RedPacket> selectAvailable(@Param("now") LocalDateTime now);

    /**
     * 抢红包前锁定红包主表行。二倍均值法需要先读「剩余金额/剩余份数」再算本次金额，
     * 纯条件更新无法原子完成这一步，因此用行锁把同一红包的读-算-扣-写串行化。
     * 只锁主表一行；调用方必须处在事务中。
     */
    @Select("""
            SELECT *
            FROM t_red_packet
            WHERE id = #{id} AND deleted = 0
            FOR UPDATE
            """)
    RedPacket selectForUpdate(@Param("id") Long id);

    /**
     * 条件预占一份。0 行表示已抢完；行锁场景下是第二道防线。
     */
    @Update("""
            UPDATE t_red_packet
            SET remain_qty = remain_qty - 1,
                version = version + 1,
                updated_at = NOW(3)
            WHERE id = #{id}
              AND remain_qty > 0
            """)
    int reserveOne(@Param("id") Long id);
}
