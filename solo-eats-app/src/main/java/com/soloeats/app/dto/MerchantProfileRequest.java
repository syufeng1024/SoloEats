package com.soloeats.app.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * 商家自助资料与结算账户请求。
 */
public record MerchantProfileRequest(
        @Size(max = 64, message = "contactName 长度不能超过 64")
        String contactName,

        @Size(max = 32, message = "contactPhone 长度不能超过 32")
        String contactPhone,

        @Email(message = "contactEmail 格式不正确")
        @Size(max = 128, message = "contactEmail 长度不能超过 128")
        String contactEmail,

        @Size(max = 64, message = "province 长度不能超过 64")
        String province,

        @Size(max = 64, message = "city 长度不能超过 64")
        String city,

        @Size(max = 64, message = "district 长度不能超过 64")
        String district,

        @Size(max = 255, message = "address 长度不能超过 255")
        String address
) {

    public record SettlementAccountRequest(
            @NotBlank(message = "accountName 不能为空")
            @Size(max = 128, message = "accountName 长度不能超过 128")
            String accountName,

            @NotBlank(message = "bankName 不能为空")
            @Size(max = 128, message = "bankName 长度不能超过 128")
            String bankName,

            @Size(max = 255, message = "bankBranch 长度不能超过 255")
            String bankBranch,

            @NotBlank(message = "bankAccountNo 不能为空")
            @Size(max = 64, message = "bankAccountNo 长度不能超过 64")
            String bankAccountNo,

            @Size(max = 128, message = "alipayAccount 长度不能超过 128")
            String alipayAccount,

            Long contractFileId,

            LocalDate effectiveFrom,

            LocalDate effectiveTo
    ) {
    }
}
