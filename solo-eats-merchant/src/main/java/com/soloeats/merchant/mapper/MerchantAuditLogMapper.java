package com.soloeats.merchant.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.soloeats.merchant.domain.entity.MerchantAuditLog;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface MerchantAuditLogMapper extends BaseMapper<MerchantAuditLog> {
}
