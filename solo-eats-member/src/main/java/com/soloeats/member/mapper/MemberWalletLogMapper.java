package com.soloeats.member.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.soloeats.member.domain.entity.MemberWalletLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface MemberWalletLogMapper extends BaseMapper<MemberWalletLog> {

    /**
     * 幂等预查。命中 uk_biz(biz_type, biz_id, request_id) 说明同一请求已入账，
     * 调用方直接返回当前结果，不再动余额。
     */
    @Select("SELECT * FROM t_member_wallet_log "
            + "WHERE biz_type = #{bizType} AND biz_id = #{bizId} AND request_id = #{requestId} "
            + "LIMIT 1")
    MemberWalletLog findByRequestId(@Param("bizType") String bizType,
                                    @Param("bizId") Long bizId,
                                    @Param("requestId") String requestId);
}
