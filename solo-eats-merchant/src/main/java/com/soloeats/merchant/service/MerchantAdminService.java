package com.soloeats.merchant.service;

import com.soloeats.common.api.PageResult;
import com.soloeats.merchant.service.command.AuditMerchantCommand;
import com.soloeats.merchant.service.view.MerchantDetailView;
import com.soloeats.merchant.service.view.MerchantView;

import java.math.BigDecimal;

/**
 * 平台侧商家管理：列表、详情、审核、启停、抽佣、结算账户审核。
 */
public interface MerchantAdminService {

    PageResult<MerchantView> page(Integer status, String keyword, Long categoryId,
                                  long pageNum, long pageSize);

    MerchantDetailView detail(Long id);

    /**
     * 审核通过：状态 1→2、写流水、建商家管理员账号、绑 MERCHANT_MANAGER、建商家租户。
     * 已通过的商家重复调用直接返回详情（不重复建账号）。
     */
    MerchantDetailView approve(Long id, AuditMerchantCommand command);

    MerchantDetailView reject(Long id, String remark, Long operatorId, String operatorName);

    void updateStatus(Long id, Integer status, Long operatorId, String operatorName);

    void updateCommission(Long id, BigDecimal commissionRate);

    void auditSettlementAccount(Long id, Integer status);
}
