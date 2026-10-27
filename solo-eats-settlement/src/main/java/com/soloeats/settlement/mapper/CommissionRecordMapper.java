package com.soloeats.settlement.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.soloeats.settlement.domain.entity.CommissionRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 抽佣明细读写。分页查询直接用 MyBatis-Plus 条件构造器，无需自定义 SQL；
 * 这里只补两个必须自定义的方法。
 */
@Mapper
public interface CommissionRecordMapper extends BaseMapper<CommissionRecord> {

    /**
     * 按订单取已落库的抽佣明细，用于 uk_order 冲突后的幂等读取。
     */
    @Select("""
            SELECT *
            FROM t_commission_record
            WHERE order_id = #{orderId}
            LIMIT 1
            """)
    CommissionRecord findByOrderId(@Param("orderId") Long orderId);

    /**
     * 结算单生成后回填归属账单，便于「账单 → 抽佣明细」溯源。
     * 只回填空值，不覆盖已有归属。
     */
    @Update("""
            <script>
            UPDATE t_commission_record
            SET settlement_bill_id = #{billId}
            WHERE settlement_bill_id IS NULL
              AND order_id IN
            <foreach collection="orderIds" item="orderId" open="(" separator="," close=")">
              #{orderId}
            </foreach>
            </script>
            """)
    int bindSettlementBill(@Param("billId") Long billId,
                           @Param("orderIds") List<Long> orderIds);
}
