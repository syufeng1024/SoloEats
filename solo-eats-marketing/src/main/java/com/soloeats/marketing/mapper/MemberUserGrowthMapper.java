package com.soloeats.marketing.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 回写 t_member_user 的成长值与等级。
 *
 * <p>solo-eats-marketing 只依赖 common（实测 pom 未声明 member 依赖），
 * 但任务卡允许 marketing → member。这里沿用 {@link PromotionMapper#selectMerchantIdByShop}
 * 的既有做法：跨表只写最小字段，不引入 member 模块实体与 Mapper，
 * 避免为了两个字段把整个 member 模块拉进 marketing 的编译依赖。
 */
@Mapper
public interface MemberUserGrowthMapper {

    /**
     * 条件更新固定带 deleted = 0；返回 0 由调用方转 MEMBER_NOT_FOUND。
     */
    @Update("""
            UPDATE t_member_user
            SET growth_value = #{growthValue},
                member_level_id = #{levelId},
                version = version + 1,
                updated_at = NOW(3)
            WHERE id = #{userId}
              AND deleted = 0
            """)
    int updateGrowthAndLevel(@Param("userId") Long userId,
                             @Param("growthValue") int growthValue,
                             @Param("levelId") Long levelId);
}
