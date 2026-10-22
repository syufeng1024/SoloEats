package com.soloeats.merchant.service;

/**
 * 审核结果通知商家的端口。站内信依赖 T08，实现留空避免 T03 阻塞。
 */
public interface MerchantNotifyPort {

    /**
     * 通知商家审核结果。
     *
     * @param merchantId 商家ID
     * @param status     变更后状态（2已通过 3已驳回）
     * @param remark     驳回原因或备注
     */
    void notifyAuditResult(Long merchantId, Integer status, String remark);
}
