package com.soloeats.marketing.domain.entity;

import lombok.Data;

/**
 * 等级重算的分页投影：用户当前落库值与近 365 天成长值合计。
 * 不是表实体，仅供 {@code MemberGrowthLogMapper#selectUserGrowthBatch} 映射。
 */
@Data
public class UserGrowthSnapshot {

    private Long userId;

    private Integer oldGrowthValue;

    private Long oldMemberLevelId;

    private Integer growth;
}
