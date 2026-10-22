package com.soloeats.product.service.view;

import java.util.List;

/**
 * 用户端商品详情：SPU + 规格组 + 加料组。
 */
public record SpuDetailView(
        SpuView spu,
        List<SpecGroupView> specGroups,
        List<AddonGroupView> addonGroups
) {
}
