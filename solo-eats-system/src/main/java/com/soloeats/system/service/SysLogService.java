package com.soloeats.system.service;

import com.soloeats.common.api.PageResult;
import com.soloeats.system.service.view.LoginLogView;
import com.soloeats.system.service.view.OperLogView;

import java.time.LocalDateTime;

/**
 * 操作日志 / 登录日志查询与写入。
 *
 * <p>日志写入失败不得阻断业务：实现方内部捕获并告警，接口不抛出持久化异常。
 */
public interface SysLogService {

    PageResult<OperLogView> pageOper(String module,
                                     Long operatorId,
                                     Integer resultCode,
                                     LocalDateTime dateFrom,
                                     LocalDateTime dateTo,
                                     long pageNum,
                                     long pageSize);

    PageResult<LoginLogView> pageLogin(String username,
                                       Integer success,
                                       LocalDateTime dateFrom,
                                       LocalDateTime dateTo,
                                       long pageNum,
                                       long pageSize);

    /**
     * 写登录日志，成功与失败都落库。
     *
     * @param loginType 1 密码 2 验证码 3 mock
     */
    void recordLogin(Long tenantId,
                     Long userId,
                     String username,
                     int loginType,
                     boolean success,
                     String failReason,
                     String ip,
                     String ua);
}
