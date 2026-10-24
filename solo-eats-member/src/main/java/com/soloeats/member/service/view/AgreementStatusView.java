package com.soloeats.member.service.view;

import java.time.LocalDateTime;

/**
 * 隐私协议状态。agreed 只在「最新一条记录的版本等于当前版本且 agree=1」时为 true；
 * 撤回同意后 agreed 立即变 false。
 */
public record AgreementStatusView(
        String agreementVersion,
        boolean agreed,
        LocalDateTime confirmedAt
) {
}
