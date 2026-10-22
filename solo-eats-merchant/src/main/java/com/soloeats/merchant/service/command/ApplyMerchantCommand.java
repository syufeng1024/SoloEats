package com.soloeats.merchant.service.command;

/**
 * 入驻申请创建入参。smsCode 在 T06 落地前由 app.auth.mock-sms-enabled 放行固定值。
 */
public record ApplyMerchantCommand(
        String merchantName,
        String brandName,
        String contactName,
        String contactPhone,
        String smsCode,
        Long categoryId
) {
}
