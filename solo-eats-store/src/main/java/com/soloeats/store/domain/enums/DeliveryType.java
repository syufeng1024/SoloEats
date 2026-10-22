package com.soloeats.store.domain.enums;

/**
 * 门店支持的履约方式位掩码：1 平台配送、2 到店自取。
 * 例：deliveryTypes=3 表示同时支持两种。
 */
public enum DeliveryType {

    PLATFORM(1),
    PICKUP(2);

    private final int bit;

    DeliveryType(int bit) {
        this.bit = bit;
    }

    public int bit() {
        return bit;
    }

    public static boolean hasDelivery(Integer deliveryTypes) {
        return has(deliveryTypes, PLATFORM.bit);
    }

    public static boolean hasPickup(Integer deliveryTypes) {
        return has(deliveryTypes, PICKUP.bit);
    }

    private static boolean has(Integer mask, int bit) {
        return mask != null && (mask & bit) == bit;
    }
}
