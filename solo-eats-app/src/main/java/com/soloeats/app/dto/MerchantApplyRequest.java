package com.soloeats.app.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 入驻申请请求。手机号在服务层做全角转半角与格式校验，
 * 避免 Bean Validation 在归一化之前把合法输入挡掉。
 */
public record MerchantApplyRequest(
        @NotBlank(message = "merchantName 不能为空")
        @Size(max = 128, message = "merchantName 长度不能超过 128")
        String merchantName,

        @Size(max = 128, message = "brandName 长度不能超过 128")
        String brandName,

        @NotBlank(message = "contactName 不能为空")
        @Size(max = 64, message = "contactName 长度不能超过 64")
        String contactName,

        @NotBlank(message = "contactPhone 不能为空")
        @Size(max = 32, message = "contactPhone 长度不能超过 32")
        String contactPhone,

        @NotBlank(message = "smsCode 不能为空")
        @Size(max = 16, message = "smsCode 长度不能超过 16")
        String smsCode,

        Long categoryId
) {

    public record UpdateRequest(
            @Size(max = 64, message = "businessLicenseNo 长度不能超过 64")
            String businessLicenseNo,

            @Size(max = 64, message = "legalPerson 长度不能超过 64")
            String legalPerson,

            @Size(max = 32, message = "legalIdCard 长度不能超过 32")
            String legalIdCard,

            @Size(max = 64, message = "province 长度不能超过 64")
            String province,

            @Size(max = 64, message = "city 长度不能超过 64")
            String city,

            @Size(max = 64, message = "district 长度不能超过 64")
            String district,

            @Size(max = 255, message = "address 长度不能超过 255")
            String address
    ) {
    }

    public record QualificationRequest(
            @NotNull(message = "qualType 不能为空")
            @Min(value = 1, message = "qualType 只能是 1~7")
            @Max(value = 7, message = "qualType 只能是 1~7")
            Integer qualType,

            @NotNull(message = "fileId 不能为空")
            Long fileId
    ) {
    }
}
