package com.soloeats.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.soloeats.order.domain.entity.OrderDiscount;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface OrderDiscountMapper extends BaseMapper<OrderDiscount> {
}
