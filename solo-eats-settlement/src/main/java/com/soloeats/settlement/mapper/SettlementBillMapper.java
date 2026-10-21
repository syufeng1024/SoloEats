package com.soloeats.settlement.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.soloeats.settlement.domain.entity.SettlementBill;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;

@Mapper
public interface SettlementBillMapper extends BaseMapper<SettlementBill> {

    @Select("""
            SELECT *
            FROM t_settlement_bill
            WHERE merchant_id = #{merchantId}
              AND bill_date = #{billDate}
            LIMIT 1
            """)
    SettlementBill findByMerchantAndDate(@Param("merchantId") Long merchantId,
                                         @Param("billDate") LocalDate billDate);
}
