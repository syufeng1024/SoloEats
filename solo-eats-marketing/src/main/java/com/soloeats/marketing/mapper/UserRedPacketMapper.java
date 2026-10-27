package com.soloeats.marketing.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.soloeats.marketing.domain.entity.UserRedPacket;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface UserRedPacketMapper extends BaseMapper<UserRedPacket> {

    @Select("""
            SELECT *
            FROM t_user_red_packet
            WHERE packet_id = #{packetId}
              AND user_id = #{userId}
            LIMIT 1
            """)
    UserRedPacket selectByPacketAndUser(@Param("packetId") Long packetId,
                                        @Param("userId") Long userId);

    /**
     * 已发金额合计，用于派生剩余金额：
     * {@code remaining = t_red_packet.total_amount - sumAmountByPacket(packetId)}。
     */
    @Select("""
            SELECT COALESCE(SUM(amount), 0)
            FROM t_user_red_packet
            WHERE packet_id = #{packetId}
            """)
    long sumAmountByPacket(@Param("packetId") Long packetId);

    @Select("""
            SELECT packet_id
            FROM t_user_red_packet
            WHERE user_id = #{userId}
            """)
    List<Long> selectPacketIdsByUser(@Param("userId") Long userId);
}
