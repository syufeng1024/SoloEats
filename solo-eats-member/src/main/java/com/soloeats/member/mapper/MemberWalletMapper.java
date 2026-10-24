package com.soloeats.member.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.soloeats.member.domain.entity.MemberWallet;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface MemberWalletMapper extends BaseMapper<MemberWallet> {

    /**
     * 原子扣减。balance >= amount 与 version 同时作为 WHERE 条件，任何一刻都不会扣成负数；
     * 返回 0 表示版本冲突或余额不足，由调用方重查 version 后重试。
     */
    @Update("UPDATE t_member_wallet SET balance = balance - #{amount}, version = version + 1 "
            + "WHERE user_id = #{userId} AND balance >= #{amount} AND version = #{version}")
    int deduct(@Param("userId") Long userId,
               @Param("amount") Long amount,
               @Param("version") Integer version);

    /**
     * 原子入账。返回 0 只可能是 version 冲突，调用方重查 version 后重试。
     */
    @Update("UPDATE t_member_wallet SET balance = balance + #{amount}, version = version + 1 "
            + "WHERE user_id = #{userId} AND version = #{version}")
    int credit(@Param("userId") Long userId,
               @Param("amount") Long amount,
               @Param("version") Integer version);

    /** 累计充值统计，与入账同事务；不参与余额条件判断。 */
    @Update("UPDATE t_member_wallet SET total_recharge = total_recharge + #{amount} "
            + "WHERE user_id = #{userId}")
    int addTotalRecharge(@Param("userId") Long userId, @Param("amount") Long amount);

    /** 累计消费统计，与扣减同事务；调账扣减不计入消费。 */
    @Update("UPDATE t_member_wallet SET total_consume = total_consume + #{amount} "
            + "WHERE user_id = #{userId}")
    int addTotalConsume(@Param("userId") Long userId, @Param("amount") Long amount);
}
