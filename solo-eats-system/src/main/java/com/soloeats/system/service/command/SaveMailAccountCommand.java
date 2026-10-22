package com.soloeats.system.service.command;

/**
 * 邮件账号保存命令。password 为明文入参，仅用于写入前加密，禁止记录日志。
 */
public record SaveMailAccountCommand(
        String accountName,
        String host,
        Integer port,
        String username,
        String password,
        String fromAddr,
        Integer ssl,
        Integer status
) {
}
