package com.soloeats.merchant.domain.enums;

/**
 * 审核流水动作，落到 t_merchant_audit_log.action。
 */
public enum AuditAction {

    SUBMIT,
    APPROVE,
    REJECT,
    ENABLE,
    DISABLE
}
