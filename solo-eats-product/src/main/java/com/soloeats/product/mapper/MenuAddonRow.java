package com.soloeats.product.mapper;

import lombok.Data;

/**
 * 菜单加料组批量查询投影。一次查询覆盖本页所有 SPU，禁止在循环内查询。
 */
@Data
public class MenuAddonRow {

    private Long spuId;

    private Long groupId;

    private String groupName;

    private Integer minSelect;

    private Integer maxSelect;

    private Integer groupSort;

    private Long itemId;

    private String itemName;

    private Long itemPrice;

    private Integer itemStock;

    private Integer itemSort;
}
