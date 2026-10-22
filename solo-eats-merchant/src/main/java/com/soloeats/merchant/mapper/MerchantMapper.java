package com.soloeats.merchant.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.soloeats.merchant.domain.entity.Merchant;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface MerchantMapper extends BaseMapper<Merchant> {
}
