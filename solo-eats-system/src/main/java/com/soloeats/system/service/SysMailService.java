package com.soloeats.system.service;

import com.soloeats.common.api.PageResult;
import com.soloeats.system.service.command.SaveMailAccountCommand;
import com.soloeats.system.service.view.MailAccountView;
import com.soloeats.system.service.view.MailLogView;

import java.util.List;

/**
 * SMTP 账号配置与测试发送。密码只在写入时加密、发送时解密，任何出参都不返回密码。
 */
public interface SysMailService {

    List<MailAccountView> listAccounts();

    MailAccountView createAccount(SaveMailAccountCommand command);

    MailAccountView updateAccount(Long id, SaveMailAccountCommand command);

    /**
     * 发送测试邮件并落 t_sys_mail_log；发送属于外部调用，不加事务。
     */
    void sendTest(Long accountId, String toAddr);

    PageResult<MailLogView> pageLogs(Integer status, long pageNum, long pageSize);
}
