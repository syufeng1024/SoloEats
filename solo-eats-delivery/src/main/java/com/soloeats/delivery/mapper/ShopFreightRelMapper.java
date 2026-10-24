package com.soloeats.delivery.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.soloeats.delivery.domain.entity.ShopFreightRel;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ShopFreightRelMapper extends BaseMapper<ShopFreightRel> {

    @Select("SELECT * FROM t_shop_freight_rel WHERE shop_id = #{shopId} LIMIT 1")
    ShopFreightRel findByShopId(@Param("shopId") Long shopId);
}
