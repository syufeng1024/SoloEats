package com.soloeats.marketing.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.soloeats.marketing.domain.entity.MemberBenefitGrant;
import org.apache.ibatis.annotations.Mapper;

/**
 * 权益发放记录。本批只提供基础 CRUD，发放与核销在后续批次实现。
 */
@Mapper
public interface MemberBenefitGrantMapper extends BaseMapper<MemberBenefitGrant> {
}
