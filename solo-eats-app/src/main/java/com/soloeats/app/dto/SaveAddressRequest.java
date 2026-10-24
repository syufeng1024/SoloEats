package com.soloeats.app.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * 新增 / 修改收货地址。isDefault 为空时：新增沿用「首条自动默认」，修改保持原值。
 */
public record SaveAddressRequest(
        @NotBlank(message = "收货人不能为空")
        @Size(max = 64, message = "收货人最长 64 个字符")
        String receiverName,
        @NotBlank(message = "收货人手机号不能为空")
        @Pattern(regexp = "^1[3-9]\\d{9}$", message = "收货人手机号格式不正确")
        String receiverPhone,
        @Size(max = 64, message = "省份最长 64 个字符")
        String province,
        @Size(max = 64, message = "城市最长 64 个字符")
        String city,
        @Size(max = 64, message = "区县最长 64 个字符")
        String district,
        @NotBlank(message = "详细地址不能为空")
        @Size(max = 255, message = "详细地址最长 255 个字符")
        String address,
        @Size(max = 64, message = "门牌号最长 64 个字符")
        String houseNumber,
        @DecimalMin(value = "-180.0", message = "经度取值范围 -180 ~ 180")
        @DecimalMax(value = "180.0", message = "经度取值范围 -180 ~ 180")
        BigDecimal lng,
        @DecimalMin(value = "-90.0", message = "纬度取值范围 -90 ~ 90")
        @DecimalMax(value = "90.0", message = "纬度取值范围 -90 ~ 90")
        BigDecimal lat,
        @Size(max = 16, message = "标签最长 16 个字符")
        String tag,
        Boolean isDefault
) {
}
