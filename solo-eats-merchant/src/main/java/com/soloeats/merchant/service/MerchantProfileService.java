package com.soloeats.merchant.service;

import com.soloeats.merchant.service.command.SaveSettlementAccountCommand;
import com.soloeats.merchant.service.command.UpdateMerchantProfileCommand;
import com.soloeats.merchant.service.view.MerchantDetailView;
import com.soloeats.merchant.service.view.SettlementAccountView;

import java.util.List;

/**
 * 商家自助端。merchantId 必须来自登录令牌，不接受请求体传入。
 */
public interface MerchantProfileService {

    MerchantDetailView profile(Long merchantId);

    MerchantDetailView updateProfile(Long merchantId, UpdateMerchantProfileCommand command);

    List<SettlementAccountView> listSettlementAccounts(Long merchantId);

    SettlementAccountView createSettlementAccount(Long merchantId, SaveSettlementAccountCommand command);
}
