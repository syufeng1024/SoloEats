package com.soloeats.merchant.service.command;

/**
 * 商家自助修改资料。merchant_no / status / commission_rate 不允许商家修改。
 */
public record UpdateMerchantProfileCommand(
        String contactName,
        String contactPhone,
        String contactEmail,
        String province,
        String city,
        String district,
        String address
) {
}
