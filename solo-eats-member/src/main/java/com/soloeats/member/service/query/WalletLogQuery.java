package com.soloeats.member.service.query;

import java.time.LocalDate;

/**
 * 钱包流水查询条件。dateTo 含当天，服务层转换为 [dateFrom 00:00, dateTo+1 00:00)。
 */
public record WalletLogQuery(String bizType, LocalDate dateFrom, LocalDate dateTo) {

    public static WalletLogQuery empty() {
        return new WalletLogQuery(null, null, null);
    }
}
