package com.soloeats.store.service;

import com.soloeats.common.api.PageResult;
import com.soloeats.store.service.command.DeliveryCheckCommand;
import com.soloeats.store.service.command.SaveDeliveryAreaCommand;
import com.soloeats.store.service.view.DeliveryCheckResult;
import com.soloeats.store.service.view.NearbyShopView;

import java.math.BigDecimal;
import java.util.List;

/**
 * 围栏、附近门店与配送范围校验。距离统一 Haversine，不依赖地图 SDK。
 */
public interface ShopGeoService {

    DeliveryAreaView saveDeliveryArea(Long shopId, SaveDeliveryAreaCommand command);

    DeliveryAreaView getDeliveryArea(Long shopId);

    /**
     * 自取（fulfillmentType=2）跳过围栏且 deliveryFee=0；起送价校验仍然生效。
     */
    DeliveryCheckResult check(Long shopId, DeliveryCheckCommand command);

    PageResult<NearbyShopView> nearby(BigDecimal lng, BigDecimal lat, Integer radiusM, String keyword,
                                      String sortBy, long pageNum, long pageSize);

    record DeliveryAreaView(
            Long id,
            Integer areaType,
            BigDecimal centerLng,
            BigDecimal centerLat,
            Integer radiusM,
            List<SaveDeliveryAreaCommand.Point> polygon,
            Integer status
    ) {
    }
}
