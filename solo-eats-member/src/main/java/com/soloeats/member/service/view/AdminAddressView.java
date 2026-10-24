package com.soloeats.member.service.view;

import com.soloeats.member.domain.entity.MemberAddress;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 后台地址查看。receiverPhone 只返回掩码，后台接口同样不得返回明文。
 */
public record AdminAddressView(
        Long id,
        Long userId,
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
        boolean isDefault,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static AdminAddressView from(MemberAddress address) {
        return new AdminAddressView(
                address.getId(),
                address.getUserId(),
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
                address.getIsDefault() != null && address.getIsDefault() == 1,
                address.getCreatedAt(),
                address.getUpdatedAt());
    }
}
