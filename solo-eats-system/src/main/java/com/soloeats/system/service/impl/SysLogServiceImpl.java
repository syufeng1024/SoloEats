package com.soloeats.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.soloeats.common.api.PageResult;
import com.soloeats.common.exception.BizException;
import com.soloeats.common.exception.ErrorCode;
import com.soloeats.common.log.OperationLogRecord;
import com.soloeats.common.log.OperationLogSink;
import com.soloeats.system.domain.entity.SysLoginLog;
import com.soloeats.system.domain.entity.SysOperLog;
import com.soloeats.system.mapper.SysLoginLogMapper;
import com.soloeats.system.mapper.SysOperLogMapper;
import com.soloeats.system.service.SysLogService;
import com.soloeats.system.service.view.LoginLogView;
import com.soloeats.system.service.view.OperLogView;
import com.soloeats.system.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

/**
 * 日志服务。{@link #persist(OperationLogRecord)} 是 {@link OperationLogSink} 的异步实现：
 * 租户 ID 由切面在请求线程内采集后显式传入，异步线程内临时 set/clear，不依赖跨线程 ThreadLocal。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SysLogServiceImpl implements SysLogService, OperationLogSink {

    private static final long DEFAULT_PAGE_SIZE = 20L;
    private static final long MAX_PAGE_SIZE = 100L;

    private static final int MAX_USERNAME_LENGTH = 64;
    private static final int MAX_FAIL_REASON_LENGTH = 128;
    private static final int MAX_IP_LENGTH = 64;
    private static final int MAX_UA_LENGTH = 512;

    private final SysOperLogMapper sysOperLogMapper;
    private final SysLoginLogMapper sysLoginLogMapper;

    @Async("taskExecutor")
    @Override
    public void persist(OperationLogRecord record) {
        if (record == null) {
            return;
        }
        Long previousTenant = TenantContext.getOrNull();
        try {
            TenantContext.set(record.tenantId());
            SysOperLog entity = new SysOperLog();
            entity.setTenantId(record.tenantId());
            entity.setTraceId(record.traceId());
            entity.setModule(record.module());
            entity.setAction(record.action());
            entity.setMethod(record.method());
            entity.setRequestUri(record.requestUri());
            entity.setRequestMethod(record.requestMethod());
            entity.setRequestParams(record.requestParams());
            entity.setOperatorId(record.operatorId());
            entity.setOperatorName(record.operatorName());
            entity.setOperatorType(record.operatorType());
            entity.setIp(record.ip());
            entity.setUa(record.ua());
            entity.setResultCode(record.resultCode());
            entity.setErrorMsg(record.errorMsg());
            entity.setCostMs(record.costMs());
            sysOperLogMapper.insert(entity);
        } catch (RuntimeException ex) {
            // 落库失败只告警，绝不向业务线程抛回
            log.warn("save operation log failed module={} action={} reason={}",
                    record.module(), record.action(), ex.getMessage());
        } finally {
            if (previousTenant == null) {
                TenantContext.clear();
            } else {
                TenantContext.set(previousTenant);
            }
        }
    }

    @Override
    public void recordLogin(Long tenantId,
                            Long userId,
                            String username,
                            int loginType,
                            boolean success,
                            String failReason,
                            String ip,
                            String ua) {
        try {
            SysLoginLog entity = new SysLoginLog();
            entity.setTenantId(tenantId);
            entity.setUserId(userId);
            entity.setUsername(truncate(username, MAX_USERNAME_LENGTH));
            entity.setLoginType(loginType);
            entity.setSuccess(success ? 1 : 0);
            entity.setFailReason(truncate(failReason, MAX_FAIL_REASON_LENGTH));
            entity.setIp(truncate(ip, MAX_IP_LENGTH));
            entity.setUa(truncate(ua, MAX_UA_LENGTH));
            sysLoginLogMapper.insert(entity);
        } catch (RuntimeException ex) {
            // 登录日志失败不能影响登录结果
            log.warn("save login log failed username={} success={} reason={}",
                    username, success, ex.getMessage());
        }
    }

    @Override
    public PageResult<OperLogView> pageOper(String module,
                                            Long operatorId,
                                            Integer resultCode,
                                            LocalDateTime dateFrom,
                                            LocalDateTime dateTo,
                                            long pageNum,
                                            long pageSize) {
        long normalizedPageNum = pageNum < 1L ? 1L : pageNum;
        long normalizedPageSize = normalizePageSize(pageSize);

        LambdaQueryWrapper<SysOperLog> wrapper = new LambdaQueryWrapper<SysOperLog>()
                .eq(StringUtils.hasText(module), SysOperLog::getModule, trim(module))
                .eq(operatorId != null, SysOperLog::getOperatorId, operatorId)
                .eq(resultCode != null, SysOperLog::getResultCode, resultCode)
                .ge(dateFrom != null, SysOperLog::getCreatedAt, dateFrom)
                .le(dateTo != null, SysOperLog::getCreatedAt, dateTo)
                .orderByDesc(SysOperLog::getCreatedAt)
                .orderByDesc(SysOperLog::getId);

        Page<SysOperLog> page = sysOperLogMapper.selectPage(
                new Page<>(normalizedPageNum, normalizedPageSize), wrapper);
        return PageResult.of(
                page.getRecords().stream().map(this::toView).toList(),
                page.getTotal(),
                normalizedPageNum,
                normalizedPageSize);
    }

    @Override
    public PageResult<LoginLogView> pageLogin(String username,
                                              Integer success,
                                              LocalDateTime dateFrom,
                                              LocalDateTime dateTo,
                                              long pageNum,
                                              long pageSize) {
        if (success != null && success != 0 && success != 1) {
            throw new BizException(ErrorCode.PARAM_INVALID, "success 只能是 0 或 1");
        }
        long normalizedPageNum = pageNum < 1L ? 1L : pageNum;
        long normalizedPageSize = normalizePageSize(pageSize);

        LambdaQueryWrapper<SysLoginLog> wrapper = new LambdaQueryWrapper<SysLoginLog>()
                .like(StringUtils.hasText(username), SysLoginLog::getUsername, trim(username))
                .eq(success != null, SysLoginLog::getSuccess, success)
                .ge(dateFrom != null, SysLoginLog::getCreatedAt, dateFrom)
                .le(dateTo != null, SysLoginLog::getCreatedAt, dateTo)
                .orderByDesc(SysLoginLog::getCreatedAt)
                .orderByDesc(SysLoginLog::getId);

        Page<SysLoginLog> page = sysLoginLogMapper.selectPage(
                new Page<>(normalizedPageNum, normalizedPageSize), wrapper);
        return PageResult.of(
                page.getRecords().stream().map(this::toView).toList(),
                page.getTotal(),
                normalizedPageNum,
                normalizedPageSize);
    }

    private OperLogView toView(SysOperLog entity) {
        return new OperLogView(
                entity.getId(),
                entity.getTraceId(),
                entity.getModule(),
                entity.getAction(),
                entity.getMethod(),
                entity.getRequestUri(),
                entity.getRequestMethod(),
                entity.getRequestParams(),
                entity.getOperatorId(),
                entity.getOperatorName(),
                entity.getOperatorType(),
                entity.getIp(),
                entity.getUa(),
                entity.getResultCode(),
                entity.getErrorMsg(),
                entity.getCostMs(),
                entity.getCreatedAt());
    }

    private LoginLogView toView(SysLoginLog entity) {
        return new LoginLogView(
                entity.getId(),
                entity.getUserId(),
                entity.getUsername(),
                entity.getLoginType(),
                entity.getSuccess(),
                entity.getFailReason(),
                entity.getIp(),
                entity.getUa(),
                entity.getCreatedAt());
    }

    private long normalizePageSize(long pageSize) {
        if (pageSize < 1L) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(pageSize, MAX_PAGE_SIZE);
    }

    private String trim(String value) {
        return value == null ? null : value.trim();
    }

    private String truncate(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
