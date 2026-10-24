package com.soloeats.member.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.soloeats.common.api.PageResult;
import com.soloeats.common.exception.BizException;
import com.soloeats.common.exception.ErrorCode;
import com.soloeats.member.domain.entity.MemberWallet;
import com.soloeats.member.domain.entity.MemberWalletLog;
import com.soloeats.member.domain.enums.WalletBizType;
import com.soloeats.member.domain.enums.WalletDirection;
import com.soloeats.member.mapper.MemberWalletLogMapper;
import com.soloeats.member.mapper.MemberWalletMapper;
import com.soloeats.member.service.MemberWalletService;
import com.soloeats.member.service.query.WalletLogQuery;
import com.soloeats.member.service.view.WalletLogView;
import com.soloeats.member.service.view.WalletView;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

/**
 * 钱包余额只通过 deduct / credit 的条件更新修改：
 * 扣减 SQL 自带 balance >= amount，应用层不承担「余额不为负」的正确性。
 */
@Slf4j
@Service
public class MemberWalletServiceImpl implements MemberWalletService {

    private static final long MIN_AMOUNT = 1L;
    private static final long MAX_AMOUNT = 100_000L;
    /** 影响行数为 0 时最多重试次数（首次尝试之外）。 */
    private static final int MAX_RETRY = 3;
    private static final long DEFAULT_PAGE_SIZE = 20L;
    private static final long MAX_PAGE_SIZE = 100L;
    private static final int MAX_REMARK_LENGTH = 255;
    private static final int MAX_OPERATOR_NAME_LENGTH = 64;
    private static final int MAX_REQUEST_ID_LENGTH = 64;

    private final MemberWalletMapper memberWalletMapper;
    private final MemberWalletLogMapper memberWalletLogMapper;
    private final boolean mockRechargeEnabled;

    public MemberWalletServiceImpl(MemberWalletMapper memberWalletMapper,
                                   MemberWalletLogMapper memberWalletLogMapper,
                                   @Value("${app.wallet.mock-recharge-enabled:true}")
                                   boolean mockRechargeEnabled) {
        this.memberWalletMapper = memberWalletMapper;
        this.memberWalletLogMapper = memberWalletLogMapper;
        this.mockRechargeEnabled = mockRechargeEnabled;
    }

    @Override
    public WalletView getWallet(Long userId) {
        return WalletView.from(requireWallet(userId));
    }

    @Override
    public PageResult<WalletLogView> listLogs(Long userId, WalletLogQuery query,
                                              long pageNum, long pageSize) {
        if (userId == null) {
            throw new BizException(ErrorCode.MEMBER_NOT_FOUND);
        }
        WalletLogQuery condition = query == null ? WalletLogQuery.empty() : query;
        String bizType = normalizeBizType(condition.bizType());
        if (condition.dateFrom() != null && condition.dateTo() != null
                && condition.dateFrom().isAfter(condition.dateTo())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "dateFrom 不能晚于 dateTo");
        }

        LambdaQueryWrapper<MemberWalletLog> wrapper = new LambdaQueryWrapper<MemberWalletLog>()
                .eq(MemberWalletLog::getUserId, userId)
                .eq(bizType != null, MemberWalletLog::getBizType, bizType)
                .ge(condition.dateFrom() != null, MemberWalletLog::getCreatedAt,
                        condition.dateFrom() == null ? null : condition.dateFrom().atStartOfDay())
                .lt(condition.dateTo() != null, MemberWalletLog::getCreatedAt,
                        condition.dateTo() == null ? null : condition.dateTo().plusDays(1).atStartOfDay())
                .orderByDesc(MemberWalletLog::getCreatedAt)
                .orderByDesc(MemberWalletLog::getId);

        long normalizedPageNum = pageNum < 1L ? 1L : pageNum;
        long normalizedPageSize = pageSize < 1L ? DEFAULT_PAGE_SIZE : Math.min(pageSize, MAX_PAGE_SIZE);
        Page<MemberWalletLog> page = memberWalletLogMapper.selectPage(
                new Page<>(normalizedPageNum, normalizedPageSize), wrapper);
        List<WalletLogView> views = page.getRecords().stream().map(WalletLogView::from).toList();
        return PageResult.of(views, page.getTotal(), normalizedPageNum, normalizedPageSize);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public WalletView recharge(Long userId, Long amount, String requestId) {
        if (!mockRechargeEnabled) {
            throw new BizException(ErrorCode.PARAM_INVALID, "模拟充值功能未开启");
        }
        long value = requireRechargeAmount(amount);
        String idempotencyKey = normalizeRequestId(requestId);
        MemberWalletLog existing = findRepeated(WalletBizType.RECHARGE, userId, idempotencyKey);
        if (existing != null) {
            log.info("wallet recharge duplicated userId={} requestId={}", userId, idempotencyKey);
            return getWallet(userId);
        }

        BalanceChange change = creditWithRetry(userId, value, true);
        writeLog(userId, WalletBizType.RECHARGE, userId, idempotencyKey,
                WalletDirection.IN, value, change, null, null, null);
        log.info("wallet recharge success userId={} amount={} afterBalance={} requestId={}",
                userId, value, change.afterBalance(), idempotencyKey);
        return WalletView.from(change.wallet());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deduct(Long userId, Long amount, WalletBizType bizType, Long bizId, String requestId) {
        if (bizType == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "bizType 不能为空");
        }
        long value = requireDeductAmount(amount);
        long effectiveBizId = bizId == null ? 0L : bizId;
        String idempotencyKey = normalizeRequestId(requestId);
        if (idempotencyKey != null
                && memberWalletLogMapper.findByRequestId(
                        bizType.name(), effectiveBizId, idempotencyKey) != null) {
            log.info("wallet deduct duplicated userId={} bizType={} requestId={}",
                    userId, bizType, idempotencyKey);
            return;
        }

        boolean consume = bizType == WalletBizType.CONSUME;
        BalanceChange change = deductWithRetry(userId, value, consume);
        writeLog(userId, bizType, effectiveBizId, idempotencyKey,
                WalletDirection.OUT, value, change, null, null, null);
        log.info("wallet deduct success userId={} bizType={} bizId={} amount={} afterBalance={}",
                userId, bizType, effectiveBizId, value, change.afterBalance());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public WalletView adjust(Long userId, Long amount, String reason, String requestId,
                             Long operatorId, String operatorName) {
        long value = requireAdjustAmount(amount);
        String reasonText = requireText(reason, "调账原因不能为空");
        String idempotencyKey = normalizeRequestId(requestId);
        if (idempotencyKey == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "requestId 不能为空");
        }
        MemberWalletLog existing = findRepeated(WalletBizType.ADJUST, userId, idempotencyKey);
        if (existing != null) {
            log.info("wallet adjust duplicated userId={} requestId={}", userId, idempotencyKey);
            return getWallet(userId);
        }

        String operator = StringUtils.hasText(operatorName) ? operatorName.strip() : "未知操作人";
        if (operator.length() > MAX_OPERATOR_NAME_LENGTH) {
            operator = operator.substring(0, MAX_OPERATOR_NAME_LENGTH);
        }
        String remark = truncate(operator + "：" + reasonText);

        boolean credit = value > 0L;
        long absAmount = Math.abs(value);
        BalanceChange change = credit
                ? creditWithRetry(userId, absAmount, false)
                : deductWithRetry(userId, absAmount, false);
        writeLog(userId, WalletBizType.ADJUST, userId, idempotencyKey,
                credit ? WalletDirection.IN : WalletDirection.OUT,
                absAmount, change, remark, operatorId, operator);
        log.info("wallet adjust success userId={} amount={} operatorId={} requestId={} afterBalance={}",
                userId, value, operatorId, idempotencyKey, change.afterBalance());
        return WalletView.from(change.wallet());
    }

    /**
     * 原子入账 + 版本冲突重试。成功后按需累加 total_recharge，再读一次余额供流水落 before/after。
     * 更新语句已持有该行锁直至事务结束，重读结果不会被其他事务插队。
     */
    private BalanceChange creditWithRetry(Long userId, long amount, boolean countAsRecharge) {
        for (int attempt = 0; attempt <= MAX_RETRY; attempt++) {
            MemberWallet wallet = requireWallet(userId);
            if (memberWalletMapper.credit(userId, amount, wallet.getVersion()) == 1) {
                if (countAsRecharge) {
                    memberWalletMapper.addTotalRecharge(userId, amount);
                }
                MemberWallet after = requireWallet(userId);
                return new BalanceChange(after, after.getBalance() - amount);
            }
            log.warn("wallet credit version conflict userId={} attempt={}", userId, attempt + 1);
        }
        throw new BizException(ErrorCode.SYSTEM_ERROR, "钱包入账冲突，请稍后重试");
    }

    /**
     * 原子扣减 + 版本冲突重试。SELECT 只用于取 version 与快速判断余额，
     * 「余额不为负」由 SQL 的 balance >= amount 保证。成功后按需累加 total_consume。
     */
    private BalanceChange deductWithRetry(Long userId, long amount, boolean countAsConsume) {
        for (int attempt = 0; attempt <= MAX_RETRY; attempt++) {
            MemberWallet wallet = requireWallet(userId);
            if (wallet.getBalance() < amount) {
                throw new BizException(ErrorCode.WALLET_BALANCE_NOT_ENOUGH);
            }
            if (memberWalletMapper.deduct(userId, amount, wallet.getVersion()) == 1) {
                if (countAsConsume) {
                    memberWalletMapper.addTotalConsume(userId, amount);
                }
                MemberWallet after = requireWallet(userId);
                return new BalanceChange(after, after.getBalance() + amount);
            }
            log.warn("wallet deduct version conflict userId={} attempt={}", userId, attempt + 1);
        }
        throw new BizException(ErrorCode.WALLET_BALANCE_NOT_ENOUGH);
    }

    private MemberWallet requireWallet(Long userId) {
        if (userId == null) {
            throw new BizException(ErrorCode.MEMBER_NOT_FOUND);
        }
        MemberWallet wallet = memberWalletMapper.selectOne(new LambdaQueryWrapper<MemberWallet>()
                .eq(MemberWallet::getUserId, userId)
                .last("LIMIT 1"));
        if (wallet == null) {
            throw new BizException(ErrorCode.MEMBER_NOT_FOUND);
        }
        return wallet;
    }

    /** 充值/调账没有业务单，biz_id 复用 user_id，使 uk_biz 按用户隔离幂等键。 */
    private MemberWalletLog findRepeated(WalletBizType bizType, Long userId, String requestId) {
        if (!StringUtils.hasText(requestId)) {
            return null;
        }
        return memberWalletLogMapper.findByRequestId(bizType.name(), userId, requestId);
    }

    private void writeLog(Long userId, WalletBizType bizType, long bizId, String requestId,
                          WalletDirection direction, long amount, BalanceChange change,
                          String remark, Long operatorId, String operatorName) {
        MemberWalletLog record = new MemberWalletLog();
        record.setUserId(userId);
        record.setBizType(bizType.name());
        record.setBizId(bizId);
        record.setRequestId(StringUtils.hasText(requestId) ? requestId : null);
        record.setDirection(direction.code());
        record.setAmount(amount);
        record.setBeforeBalance(change.beforeBalance());
        record.setAfterBalance(change.afterBalance());
        record.setRemark(remark);
        record.setOperatorId(operatorId);
        record.setOperatorName(operatorName);
        try {
            memberWalletLogMapper.insert(record);
        } catch (DuplicateKeyException ex) {
            // 并发同 requestId：余额更新必须随事务一起回滚，不能静默返回
            throw new BizException(ErrorCode.IDEMPOTENCY_KEY_REUSED);
        }
    }

    private String normalizeBizType(String bizType) {
        if (!StringUtils.hasText(bizType)) {
            return null;
        }
        String normalized = bizType.strip().toUpperCase(Locale.ROOT);
        for (WalletBizType type : WalletBizType.values()) {
            if (type.name().equals(normalized)) {
                return normalized;
            }
        }
        throw new BizException(ErrorCode.PARAM_INVALID, "bizType 不合法: " + bizType);
    }

    private long requireRechargeAmount(Long amount) {
        if (amount == null || amount < MIN_AMOUNT || amount > MAX_AMOUNT) {
            throw new BizException(ErrorCode.WALLET_AMOUNT_INVALID,
                    "充值金额需在 1 ~ 100000 分之间");
        }
        return amount;
    }

    /** 扣减不设上限：余额不足必须由 deduct SQL 判定并返回 80006。 */
    private long requireDeductAmount(Long amount) {
        if (amount == null || amount < MIN_AMOUNT) {
            throw new BizException(ErrorCode.WALLET_AMOUNT_INVALID, "扣减金额必须大于 0");
        }
        return amount;
    }

    /**
     * 调账金额范围校验。用区间比较而不是 Math.abs：
     * Math.abs(Long.MIN_VALUE) 仍为负数，会绕过上限判断并让后续扣减溢出。
     */
    private long requireAdjustAmount(Long amount) {
        if (amount == null || amount == 0L
                || amount > MAX_AMOUNT || amount < -MAX_AMOUNT) {
            throw new BizException(ErrorCode.WALLET_AMOUNT_INVALID,
                    "调账金额不能为 0，且绝对值不超过 100000 分");
        }
        return amount;
    }

    /** request_id 落 varchar(64)；空值表示不做幂等，超长直接拒绝而不是让 MySQL 截断报错。 */
    private static String normalizeRequestId(String requestId) {
        if (!StringUtils.hasText(requestId)) {
            return null;
        }
        String trimmed = requestId.strip();
        if (trimmed.length() > MAX_REQUEST_ID_LENGTH) {
            throw new BizException(ErrorCode.PARAM_INVALID, "requestId 最长 64 个字符");
        }
        return trimmed;
    }

    private static String requireText(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new BizException(ErrorCode.PARAM_INVALID, message);
        }
        String cleaned = value.replaceAll("[\\r\\n\\t]", " ").strip();
        if (cleaned.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_INVALID, message);
        }
        return cleaned;
    }

    private static String truncate(String value) {
        return value.length() <= MAX_REMARK_LENGTH ? value : value.substring(0, MAX_REMARK_LENGTH);
    }

    private record BalanceChange(MemberWallet wallet, long beforeBalance) {

        long afterBalance() {
            return wallet.getBalance();
        }
    }
}
