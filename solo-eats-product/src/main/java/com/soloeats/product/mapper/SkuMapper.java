package com.soloeats.product.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.soloeats.product.domain.entity.Sku;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface SkuMapper extends BaseMapper<Sku> {
}
