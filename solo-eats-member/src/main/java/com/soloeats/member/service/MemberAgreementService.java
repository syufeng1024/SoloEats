package com.soloeats.member.service;

import com.soloeats.member.service.command.AgreementConfirmCommand;
import com.soloeats.member.service.view.AgreementStatusView;

/**
 * 隐私协议确认与撤回。记录只增不删，便于合规追溯。
 */
public interface MemberAgreementService {

    /** 当前协议版本 + 用户是否已同意当前版本。 */
    AgreementStatusView getStatus(Long userId);

    /** 记录一次确认（agree=true）或撤回（agree=false），每次插新行。 */
    AgreementStatusView confirm(Long userId, AgreementConfirmCommand command);
}
