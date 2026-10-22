package com.soloeats.merchant.service.command;

/**
 * 补充入驻资料。legalIdCard 为明文，服务层加密后写入 legal_id_card_cipher。
 */
public record UpdateMerchantApplyCommand(
        String businessLicenseNo,
        String legalPerson,
        String legalIdCard,
        String province,
        String city,
        String district,
        String address
) {
}
