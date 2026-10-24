package com.soloeats.member.service.view;

import com.soloeats.member.domain.entity.MemberAddress;

import java.math.BigDecimal;

/**
 * 收货地址展示体。receiverPhone 只返回脱敏值。
 */
public record AddressView(
        Long id,
        String receiverName,
        String receiverPhone,
        String province,
        String city,
        String district,
        String address,
        String houseNumber,
        BigDecimal lng,
        BigDecimal lat,
        String tag,
        boolean isDefault
) {

    public static AddressView from(MemberAddress address) {
        return new AddressView(
                address.getId(),
                address.getReceiverName(),
                address.getReceiverPhoneMask(),
                address.getProvince(),
                address.getCity(),
                address.getDistrict(),
                address.getAddress(),
                address.getHouseNumber(),
                address.getLng(),
                address.getLat(),
                address.getTag(),
                address.getIsDefault() != null && address.getIsDefault() == 1);
    }
}
