package com.soloeats.merchant.service.impl;

import com.soloeats.merchant.service.MerchantNotifyPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class NoopMerchantNotifyImpl implements MerchantNotifyPort {

    @Override
    public void notifyAuditResult(Long merchantId, Integer status, String remark) {
        // TODO(T08): 接入站内信 / WebSocket 后发送审核结果；当前只记录定位信息
        log.info("merchant audit notify skipped (T08 pending) merchantId={} status={}", merchantId, status);
    }
}
