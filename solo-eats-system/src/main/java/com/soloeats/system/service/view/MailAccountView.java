package com.soloeats.system.service.view;

import java.time.LocalDateTime;

/**
 * 邮件账号出参。刻意不包含 password / passwordCipher。
 */
public record MailAccountView(
        Long id,
        String accountName,
        String host,
        Integer port,
        String username,
        String fromAddr,
        Integer ssl,
        Integer status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
