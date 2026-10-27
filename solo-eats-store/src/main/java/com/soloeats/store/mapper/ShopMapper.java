package com.soloeats.store.mapper;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Constants;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.soloeats.common.datascope.DataScope;
import com.soloeats.store.domain.entity.Shop;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;

@Mapper
public interface ShopMapper extends BaseMapper<Shop> {

    /**
     * 评价联动刷新门店评分。仅供内部调用（T05 卡：评价通过审核后按近 90 天均分更新），
     * 商家侧接口不允许改 rating。
     */
    @Update("""
            UPDATE t_shop
            SET rating = #{rating},
                rating_taste = #{ratingTaste},
                rating_package = #{ratingPackage},
                rating_delivery = #{ratingDelivery},
                updated_at = NOW(3)
            WHERE id = #{shopId}
              AND deleted = 0
            """)
    int refreshRating(@Param("shopId") Long shopId,
                      @Param("rating") BigDecimal rating,
                      @Param("ratingTaste") BigDecimal ratingTaste,
                      @Param("ratingPackage") BigDecimal ratingPackage,
                      @Param("ratingDelivery") BigDecimal ratingDelivery);

    /**
     * 平台后台门店列表。t_shop 以主键标识「本门店」，因此 shopColumn 配成 id。
     */
    @DataScope(deptAlias = "s", userAlias = "s", shopColumn = "id")
    @Select("SELECT * FROM t_shop s ${ew.customSqlSegment}")
    Page<Shop> selectScopedPage(Page<Shop> page, @Param(Constants.WRAPPER) Wrapper<Shop> wrapper);
}
