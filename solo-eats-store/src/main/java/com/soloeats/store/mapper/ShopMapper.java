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

@Mapper
public interface ShopMapper extends BaseMapper<Shop> {

    /**
     * 平台后台门店列表。t_shop 以主键标识「本门店」，因此 shopColumn 配成 id。
     */
    @DataScope(deptAlias = "s", userAlias = "s", shopColumn = "id")
    @Select("SELECT * FROM t_shop s ${ew.customSqlSegment}")
    Page<Shop> selectScopedPage(Page<Shop> page, @Param(Constants.WRAPPER) Wrapper<Shop> wrapper);
}
