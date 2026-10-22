package com.soloeats.merchant.service;

import com.soloeats.merchant.service.command.AddQualificationCommand;
import com.soloeats.merchant.service.command.ApplyMerchantCommand;
import com.soloeats.merchant.service.command.UpdateMerchantApplyCommand;
import com.soloeats.merchant.service.view.ApplyResult;
import com.soloeats.merchant.service.view.MerchantProgressView;

/**
 * 未注册商家的入驻流程。全部写操作通过 applyToken 鉴权，不接受用户 token。
 */
public interface MerchantApplyService {

    ApplyResult apply(ApplyMerchantCommand command);

    MerchantProgressView updateApply(Long applyId, String applyToken, UpdateMerchantApplyCommand command);

    void addQualification(Long applyId, String applyToken, AddQualificationCommand command);

    void submit(Long applyId, String applyToken);

    MerchantProgressView progress(Long applyId, String applyToken);
}
