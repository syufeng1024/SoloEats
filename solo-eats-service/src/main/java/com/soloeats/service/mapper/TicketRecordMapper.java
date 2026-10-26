package com.soloeats.service.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.soloeats.service.domain.entity.TicketRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface TicketRecordMapper extends BaseMapper<TicketRecord> {

    /** 按时间正序返回，供详情页直接渲染时间线。 */
    @Select("SELECT * FROM t_ticket_record WHERE ticket_id = #{ticketId} ORDER BY created_at, id")
    List<TicketRecord> selectByTicketId(@Param("ticketId") Long ticketId);
}
