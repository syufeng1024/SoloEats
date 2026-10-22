package com.soloeats.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.soloeats.common.api.PageResult;
import com.soloeats.common.exception.BizException;
import com.soloeats.common.exception.ErrorCode;
import com.soloeats.system.domain.entity.SysMailAccount;
import com.soloeats.system.domain.entity.SysMailLog;
import com.soloeats.system.mapper.SysMailAccountMapper;
import com.soloeats.system.mapper.SysMailLogMapper;
import com.soloeats.system.service.SysMailService;
import com.soloeats.system.service.command.SaveMailAccountCommand;
import com.soloeats.system.service.view.MailAccountView;
import com.soloeats.system.service.view.MailLogView;
import com.soloeats.system.support.MailCipher;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Properties;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class SysMailServiceImpl implements SysMailService {

    private static final long DEFAULT_PAGE_SIZE = 20L;
    private static final long MAX_PAGE_SIZE = 100L;

    private static final int DEFAULT_PORT = 465;
    private static final int MAX_ACCOUNT_NAME_LENGTH = 64;
    private static final int MAX_HOST_LENGTH = 128;
    private static final int MAX_USERNAME_LENGTH = 128;
    private static final int MAX_PASSWORD_LENGTH = 256;
    private static final int MAX_ADDRESS_LENGTH = 128;
    private static final int MAX_LOG_CONTENT_LENGTH = 60000;
    private static final int MAX_ERROR_LENGTH = 512;

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private final SysMailAccountMapper sysMailAccountMapper;
    private final SysMailLogMapper sysMailLogMapper;
    private final MailCipher mailCipher;

    @Override
    public List<MailAccountView> listAccounts() {
        List<SysMailAccount> accounts = sysMailAccountMapper.selectList(
                new LambdaQueryWrapper<SysMailAccount>()
                        .eq(SysMailAccount::getDeleted, 0)
                        .orderByAsc(SysMailAccount::getId));
        return accounts.stream().map(this::toView).toList();
    }

    @Override
    public MailAccountView createAccount(SaveMailAccountCommand command) {
        if (command == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请求不能为空");
        }
        SysMailAccount account = new SysMailAccount();
        account.setAccountName(requireText(
                command.accountName(), MAX_ACCOUNT_NAME_LENGTH, "accountName"));
        account.setHost(requireText(command.host(), MAX_HOST_LENGTH, "host"));
        account.setPort(requirePort(command.port()));
        account.setUsername(requireText(
                command.username(), MAX_USERNAME_LENGTH, "username"));
        account.setPasswordCipher(mailCipher.encrypt(
                requireText(command.password(), MAX_PASSWORD_LENGTH, "password")));
        account.setFromAddr(requireEmail(command.fromAddr(), "fromAddr"));
        account.setSsl(requireFlag(command.ssl(), 1, "ssl"));
        account.setStatus(requireFlag(command.status(), 0, "status"));
        account.setDeleted(0);

        try {
            sysMailAccountMapper.insert(account);
        } catch (DuplicateKeyException ex) {
            throw new BizException(ErrorCode.PARAM_INVALID, "该租户下邮件账号名称已存在");
        }
        log.info("mail account created accountId={} accountName={}",
                account.getId(), account.getAccountName());
        return toView(account);
    }

    @Override
    public MailAccountView updateAccount(Long id, SaveMailAccountCommand command) {
        if (command == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请求不能为空");
        }
        SysMailAccount account = requireAccount(id);

        if (command.accountName() != null) {
            account.setAccountName(requireText(
                    command.accountName(), MAX_ACCOUNT_NAME_LENGTH, "accountName"));
        }
        if (command.host() != null) {
            account.setHost(requireText(command.host(), MAX_HOST_LENGTH, "host"));
        }
        if (command.port() != null) {
            account.setPort(requirePort(command.port()));
        }
        if (command.username() != null) {
            account.setUsername(requireText(
                    command.username(), MAX_USERNAME_LENGTH, "username"));
        }
        if (StringUtils.hasText(command.password())) {
            // 留空表示保持原密码；只要传值就必须重新加密，禁止明文落库
            account.setPasswordCipher(mailCipher.encrypt(
                    requireText(command.password(), MAX_PASSWORD_LENGTH, "password")));
        }
        if (command.fromAddr() != null) {
            account.setFromAddr(requireEmail(command.fromAddr(), "fromAddr"));
        }
        if (command.ssl() != null) {
            account.setSsl(requireFlag(command.ssl(), account.getSsl(), "ssl"));
        }
        if (command.status() != null) {
            account.setStatus(requireFlag(command.status(), account.getStatus(), "status"));
        }

        try {
            sysMailAccountMapper.updateById(account);
        } catch (DuplicateKeyException ex) {
            throw new BizException(ErrorCode.PARAM_INVALID, "该租户下邮件账号名称已存在");
        }
        log.info("mail account updated accountId={}", account.getId());
        return toView(account);
    }

    @Override
    public void sendTest(Long accountId, String toAddr) {
        SysMailAccount account = requireAccount(accountId);
        if (account.getStatus() != null && account.getStatus() != 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "邮件账号已停用");
        }
        String recipient = requireEmail(toAddr, "toAddr");
        String subject = "SoloEats SMTP 测试邮件";
        String content = "<p>这是一封来自 SoloEats 的 SMTP 测试邮件，收到即表示账号配置可用。</p>";

        // 先落“待发”日志，发送结束后回写成功/失败，避免外部调用失败时无记录
        SysMailLog mailLog = new SysMailLog();
        mailLog.setTemplateCode("TEST");
        mailLog.setToAddr(recipient);
        mailLog.setSubject(subject);
        mailLog.setContent(content);
        mailLog.setStatus(0);
        sysMailLogMapper.insert(mailLog);

        String password = null;
        try {
            password = mailCipher.decrypt(account.getPasswordCipher());
            send(account, password, recipient, subject, content);
            sysMailLogMapper.update(null, new LambdaUpdateWrapper<SysMailLog>()
                    .eq(SysMailLog::getId, mailLog.getId())
                    .set(SysMailLog::getStatus, 1)
                    .set(SysMailLog::getSentAt, LocalDateTime.now()));
            log.info("test mail sent accountId={} to={}", account.getId(), maskAddress(recipient));
        } catch (Exception ex) {
            String error = truncate(safeMessage(ex), MAX_ERROR_LENGTH);
            // SMTP 异常文本可能回显认证信息，落库与告警前再次剔除明文密码
            if (StringUtils.hasText(password)) {
                error = error.replace(password, "***");
            }
            sysMailLogMapper.update(null, new LambdaUpdateWrapper<SysMailLog>()
                    .eq(SysMailLog::getId, mailLog.getId())
                    .set(SysMailLog::getStatus, 2)
                    .set(SysMailLog::getErrorMsg, error));
            log.warn("test mail failed accountId={} to={} reason={}",
                    account.getId(), maskAddress(recipient), error);
            if (ex instanceof BizException bizException) {
                throw bizException;
            }
            throw new BizException(ErrorCode.SYSTEM_ERROR, "邮件发送失败，请检查 SMTP 主机、账号与端口配置");
        }
    }

    @Override
    public PageResult<MailLogView> pageLogs(Integer status, long pageNum, long pageSize) {
        if (status != null && (status < 0 || status > 2)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "status 只能是 0待发 1成功 2失败");
        }
        long normalizedPageNum = pageNum < 1L ? 1L : pageNum;
        long normalizedPageSize = normalizePageSize(pageSize);

        Page<SysMailLog> page = sysMailLogMapper.selectPage(
                new Page<>(normalizedPageNum, normalizedPageSize),
                new LambdaQueryWrapper<SysMailLog>()
                        .eq(status != null, SysMailLog::getStatus, status)
                        .orderByDesc(SysMailLog::getCreatedAt)
                        .orderByDesc(SysMailLog::getId));
        return PageResult.of(
                page.getRecords().stream().map(this::toView).toList(),
                page.getTotal(),
                normalizedPageNum,
                normalizedPageSize);
    }

    private void send(SysMailAccount account,
                      String password,
                      String toAddr,
                      String subject,
                      String content) throws Exception {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(account.getHost());
        sender.setPort(account.getPort() == null ? DEFAULT_PORT : account.getPort());
        sender.setUsername(account.getUsername());
        sender.setPassword(password);
        sender.setDefaultEncoding("UTF-8");

        Properties properties = sender.getJavaMailProperties();
        properties.put("mail.smtp.auth", "true");
        properties.put("mail.smtp.connectiontimeout", "5000");
        properties.put("mail.smtp.timeout", "5000");
        properties.put("mail.smtp.writetimeout", "5000");
        if (account.getSsl() != null && account.getSsl() == 1) {
            properties.put("mail.smtp.ssl.enable", "true");
        } else {
            properties.put("mail.smtp.starttls.enable", "true");
        }

        MimeMessage message = sender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
        helper.setFrom(account.getFromAddr());
        helper.setTo(toAddr);
        helper.setSubject(truncate(subject, 255));
        helper.setText(truncate(content, MAX_LOG_CONTENT_LENGTH), true);
        sender.send(message);
    }

    private SysMailAccount requireAccount(Long id) {
        if (id == null || id <= 0L) {
            throw new BizException(ErrorCode.PARAM_INVALID, "accountId 必须是正整数");
        }
        SysMailAccount account = sysMailAccountMapper.selectOne(
                new LambdaQueryWrapper<SysMailAccount>()
                        .eq(SysMailAccount::getId, id)
                        .eq(SysMailAccount::getDeleted, 0)
                        .last("LIMIT 1"));
        if (account == null) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND, "邮件账号不存在");
        }
        return account;
    }

    private MailAccountView toView(SysMailAccount account) {
        return new MailAccountView(
                account.getId(),
                account.getAccountName(),
                account.getHost(),
                account.getPort(),
                account.getUsername(),
                account.getFromAddr(),
                account.getSsl(),
                account.getStatus(),
                account.getCreatedAt(),
                account.getUpdatedAt());
    }

    private MailLogView toView(SysMailLog logEntity) {
        return new MailLogView(
                logEntity.getId(),
                logEntity.getTemplateCode(),
                logEntity.getToAddr(),
                logEntity.getSubject(),
                logEntity.getStatus(),
                logEntity.getErrorMsg(),
                logEntity.getSentAt(),
                logEntity.getCreatedAt());
    }

    private String requireText(String value, int maxLength, String field) {
        if (!StringUtils.hasText(value)) {
            throw new BizException(ErrorCode.PARAM_INVALID, field + " 不能为空");
        }
        String trimmed = value.trim();
        if (trimmed.length() > maxLength) {
            throw new BizException(ErrorCode.PARAM_INVALID, field + " 长度不能超过 " + maxLength);
        }
        return trimmed;
    }

    private int requirePort(Integer port) {
        int value = port == null ? DEFAULT_PORT : port;
        if (value < 1 || value > 65535) {
            throw new BizException(ErrorCode.PARAM_INVALID, "port 必须在 1~65535");
        }
        return value;
    }

    private int requireFlag(Integer value, Integer defaultValue, String field) {
        int effective = value == null
                ? (defaultValue == null ? 0 : defaultValue)
                : value;
        if (effective != 0 && effective != 1) {
            throw new BizException(ErrorCode.PARAM_INVALID, field + " 只能是 0 或 1");
        }
        return effective;
    }

    private String requireEmail(String value, String field) {
        String trimmed = requireText(value, MAX_ADDRESS_LENGTH, field);
        if (!EMAIL_PATTERN.matcher(trimmed).matches()) {
            throw new BizException(ErrorCode.PARAM_INVALID, field + " 不是合法邮箱地址");
        }
        return trimmed;
    }

    private long normalizePageSize(long pageSize) {
        if (pageSize < 1L) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(pageSize, MAX_PAGE_SIZE);
    }

    private String safeMessage(Throwable ex) {
        String message = ex.getMessage();
        return StringUtils.hasText(message) ? message : ex.getClass().getSimpleName();
    }

    private String truncate(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    private String maskAddress(String address) {
        if (!StringUtils.hasText(address)) {
            return null;
        }
        int at = address.indexOf('@');
        if (at <= 0) {
            return "***";
        }
        return address.charAt(0) + "***" + address.substring(at);
    }
}
