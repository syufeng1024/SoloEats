package com.soloeats.marketing.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.soloeats.marketing.domain.entity.CouponTemplate;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface CouponTemplateMapper extends BaseMapper<CouponTemplate> {

    /**
     * 原子扣减可发数量：issued_qty 累加 qty，且累加后不得超过 total_qty。
     * 返回 0 表示余量不足或模板不存在，调用方据此抛 40211，禁止先查余量再更新。
     */
    @Update("""
            UPDATE t_coupon_template
            SET issued_qty = issued_qty + #{qty}
            WHERE id = #{id}
              AND issued_qty + #{qty} <= total_qty
            """)
    int deductIssueQty(@Param("id") Long id, @Param("qty") int qty);
}
