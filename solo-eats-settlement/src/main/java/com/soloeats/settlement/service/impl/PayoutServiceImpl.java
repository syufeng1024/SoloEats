package com.soloeats.settlement.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.soloeats.common.api.PageResult;
import com.soloeats.common.exception.BizException;
import com.soloeats.common.exception.ErrorCode;
import com.soloeats.common.idempotency.Idempotent;
import com.soloeats.common.lock.DistributedLock;
import com.soloeats.settlement.domain.entity.PayoutOrder;
import com.soloeats.settlement.domain.enums.PayoutStatus;
import com.soloeats.settlement.mapper.PayoutOrderMapper;
import com.soloeats.settlement.service.FundFlowService;
import com.soloeats.settlement.service.PayoutService;
import com.soloeats.settlement.service.view.PayoutView;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

@Slf4j
@Service
@RequiredArgsConstructor
public class PayoutServiceImpl implements PayoutService {

    /** 单笔上限 100 万元（分）。 */
    static final long MAX_PAYOUT_AMOUNT = 100_000_000L;

    /** 账户级锁等待秒数：拿不到锁直接快速失败，避免请求堆积。 */
    static final long LOCK_WAIT_SECONDS = 3L;
    /** 账户级锁持有时长：覆盖「查余额 → 校验 → 落单」三步，异常退出自动释放。 */
    static final long LOCK_LEASE_SECONDS = 10L;

    private static final int DEFAULT_CHANNEL = 1;
    private static final int MAX_NO_RETRY = 3;
    private static final DateTimeFormatter NO_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final PayoutOrderMapper payoutOrderMapper;

    /** 主体级分布式锁，key = payout:apply:{subjectType}:{subjectId}。 */
    private final DistributedLock distributedLock;

    /** 资金流水：到账后记提现支出。 */
    private final FundFlowService fundFlowService;

    /**
     * 防重窗口 5 秒，key 由方法签名 + 入参哈希生成（含 subjectType/subjectId/amount）。
     * 不用固定 key，否则任一主体发起后 5 秒内其他主体的申请都会被误判为重复提交。
     *
     * <p>并发超提防护：「查余额 → 校验 → 落单」三步在主体级分布式锁内完成，
     * key 为 {@code payout:apply:{subjectType}:{subjectId}}；数据库条件更新仍是最终防线。
     */
    @Override
    @Idempotent(ttlSeconds = 5)
    public PayoutView apply(Integer subjectType, Long subjectId, Long amount, Integer channel) {
        validateSubject(subjectType, subjectId);
        if (amount == null || amount <= 0L) {
            throw new BizException(ErrorCode.PARAM_INVALID, "提现金额必须大于 0");
        }
        if (amount > MAX_PAYOUT_AMOUNT) {
            throw new BizException(ErrorCode.PARAM_INVALID, "单笔提现金额不能超过 1000000 元");
        }
        int resolvedChannel = channel == null ? DEFAULT_CHANNEL : channel;
        if (resolvedChannel != 1 && resolvedChannel != 2) {
            throw new BizException(ErrorCode.PARAM_INVALID, "channel 只能是 1支付宝 或 2银行卡");
        }

        String lockKey = "payout:apply:" + subjectType + ":" + subjectId;
        return withSubjectLock(lockKey, () -> doApply(subjectType, subjectId, amount, resolvedChannel));
    }

    /**
     * 账户校验 + 余额校验 + 落单必须在同一把锁内，否则两个并发请求会读到同一份可用余额
     * 而双双通过校验，造成超额提现。
     */
    private PayoutView doApply(Integer subjectType, Long subjectId, Long amount, int channel) {
        requireSettlementAccount(subjectType, subjectId);
        long available = availableBalance(subjectType, subjectId);
        if (amount > available) {
            throw new BizException(ErrorCode.PAYOUT_AMOUNT_EXCEEDED,
                    "可提现余额不足，当前可提现余额 " + available + " 分");
        }

        PayoutOrder order = new PayoutOrder();
        order.setSubjectType(subjectType);
        order.setSubjectId(subjectId);
        order.setAmount(amount);
        order.setChannel(channel);
        order.setStatus(PayoutStatus.PENDING.getCode());

        for (int attempt = 1; attempt <= MAX_NO_RETRY; attempt++) {
            order.setPayoutNo(generatePayoutNo());
            try {
                payoutOrderMapper.insert(order);
                log.info("payout applied payoutId={} payoutNo={} subjectType={} amount={}",
                        order.getId(), order.getPayoutNo(), subjectType, amount);
                return toView(order);
            } catch (DuplicateKeyException ex) {
                if (attempt == MAX_NO_RETRY) {
                    throw ex;
                }
                log.warn("payout no collision payoutNo={} attempt={}", order.getPayoutNo(), attempt);
            }
        }
        throw new BizException(ErrorCode.SYSTEM_ERROR, "提现单号生成失败，请重试");
    }

    /**
     * 商家提现必须有生效中的结算收款账户，否则无处打款；骑手侧无账户表，不校验。
     * 放在锁内，与余额校验同一次串行执行。
     */
    private void requireSettlementAccount(Integer subjectType, Long subjectId) {
        if (subjectType != SUBJECT_TYPE_MERCHANT) {
            return;
        }
        if (payoutOrderMapper.countActiveSettlementAccount(subjectId) <= 0) {
            throw new BizException(ErrorCode.PAYOUT_ACCOUNT_REQUIRED,
                    "请先配置生效中的结算收款账户后再申请提现");
        }
    }

    /**
     * 主体级分布式锁；锁组件为必需依赖，永不判空。
     */
    private <T> T withSubjectLock(String key, Supplier<T> action) {
        return distributedLock.executeWithLock(key, LOCK_WAIT_SECONDS, LOCK_LEASE_SECONDS, action);
    }

    @Override
    public PageResult<PayoutView> pageMine(Integer subjectType, Long subjectId, Integer status,
                                           long pageNum, long pageSize) {
        validateSubject(subjectType, subjectId);
        long normalizedPageNum = normalizePageNum(pageNum);
        long normalizedPageSize = normalizePageSize(pageSize);
        LambdaQueryWrapper<PayoutOrder> wrapper = new LambdaQueryWrapper<PayoutOrder>()
                .eq(PayoutOrder::getSubjectType, subjectType)
                .eq(PayoutOrder::getSubjectId, subjectId)
                .eq(status != null, PayoutOrder::getStatus, status)
                .orderByDesc(PayoutOrder::getId);
        Page<PayoutOrder> page = payoutOrderMapper.selectPage(
                new Page<>(normalizedPageNum, normalizedPageSize), wrapper);
        return PageResult.of(page.getRecords().stream().map(this::toView).toList(),
                page.getTotal(), normalizedPageNum, normalizedPageSize);
    }

    @Override
    public PageResult<PayoutView> adminPage(Integer subjectType, Long subjectId, Integer status,
                                            LocalDate dateFrom, LocalDate dateTo,
                                            long pageNum, long pageSize) {
        if (dateFrom != null && dateTo != null && dateFrom.isAfter(dateTo)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "dateFrom 不能晚于 dateTo");
        }
        long normalizedPageNum = normalizePageNum(pageNum);
        long normalizedPageSize = normalizePageSize(pageSize);
        LambdaQueryWrapper<PayoutOrder> wrapper = new LambdaQueryWrapper<PayoutOrder>()
                .eq(subjectType != null, PayoutOrder::getSubjectType, subjectType)
                .eq(subjectId != null, PayoutOrder::getSubjectId, subjectId)
                .eq(status != null, PayoutOrder::getStatus, status)
                .ge(dateFrom != null, PayoutOrder::getCreatedAt,
                        dateFrom == null ? null : dateFrom.atStartOfDay())
                .lt(dateTo != null, PayoutOrder::getCreatedAt,
                        dateTo == null ? null : dateTo.plusDays(1).atStartOfDay())
                .orderByDesc(PayoutOrder::getId);
        Page<PayoutOrder> page = payoutOrderMapper.selectPage(
                new Page<>(normalizedPageNum, normalizedPageSize), wrapper);
        return PageResult.of(page.getRecords().stream().map(this::toView).toList(),
                page.getTotal(), normalizedPageNum, normalizedPageSize);
    }

    @Override
    public void audit(Long id, boolean pass, String remark, Long auditorId) {
        if (id == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "id 不能为空");
        }
        if (remark != null && remark.length() > 255) {
            throw new BizException(ErrorCode.PARAM_INVALID, "审核意见不能超过 255 个字符");
        }
        int target = pass ? PayoutStatus.APPROVED.getCode() : PayoutStatus.REJECTED.getCode();
        if (!PayoutStatus.canTransit(PayoutStatus.PENDING.getCode(), target)) {
            throw new BizException(ErrorCode.PAYOUT_STATE_INVALID);
        }
        LocalDateTime now = LocalDateTime.now();
        int rows = payoutOrderMapper.update(null, new LambdaUpdateWrapper<PayoutOrder>()
                .eq(PayoutOrder::getId, id)
                .eq(PayoutOrder::getStatus, PayoutStatus.PENDING.getCode())
                .set(PayoutOrder::getStatus, target)
                .set(PayoutOrder::getAuditBy, auditorId)
                .set(PayoutOrder::getAuditAt, now)
                .set(PayoutOrder::getAuditRemark, remark)
                .set(PayoutOrder::getUpdatedAt, now));
        if (rows == 0) {
            throw new BizException(ErrorCode.PAYOUT_STATE_INVALID, "仅待审核的提现单可审核");
        }
        log.info("payout audited payoutId={} pass={} auditorId={}", id, pass, auditorId);
    }

    @Override
    public void pay(Long id) {
        transit(id, PayoutStatus.APPROVED, PayoutStatus.PAYING, true,
                "仅审核通过的提现单可打款");
    }

    @Override
    public void arrive(Long id) {
        transit(id, PayoutStatus.PAYING, PayoutStatus.ARRIVED, false,
                "仅打款中的提现单可登记到账");
        recordPayoutExpense(id);
    }

    /**
     * 到账后记一条提现支出流水（flow_type = 5）。uk_biz 保证重复到账不会重复记账。
     */
    private void recordPayoutExpense(Long id) {
        PayoutOrder order = payoutOrderMapper.selectById(id);
        if (order == null) {
            // 数据一致性兜底：条件更新成功即代表单据存在，查不到只可能是脏数据，只记告警
            log.warn("payout order missing after arrive payoutId={}", id);
            return;
        }
        fundFlowService.recordPayoutExpense(order);
    }

    @Override
    public long availableBalance(Integer subjectType, Long subjectId) {
        validateSubject(subjectType, subjectId);
        long income = subjectType == SUBJECT_TYPE_MERCHANT
                ? nz(payoutOrderMapper.sumMerchantSettledPayable(subjectId))
                : nz(payoutOrderMapper.sumRiderIncome(subjectId));
        long occupied = nz(payoutOrderMapper.sumActivePayoutAmount(subjectType, subjectId));
        return Math.max(0L, income - occupied);
    }

    private void transit(Long id, PayoutStatus from, PayoutStatus to, boolean markPaidAt,
                         String invalidMessage) {
        if (id == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "id 不能为空");
        }
        if (!PayoutStatus.canTransit(from.getCode(), to.getCode())) {
            throw new BizException(ErrorCode.PAYOUT_STATE_INVALID, invalidMessage);
        }
        LocalDateTime now = LocalDateTime.now();
        LambdaUpdateWrapper<PayoutOrder> wrapper = new LambdaUpdateWrapper<PayoutOrder>()
                .eq(PayoutOrder::getId, id)
                .eq(PayoutOrder::getStatus, from.getCode())
                .set(PayoutOrder::getStatus, to.getCode())
                .set(PayoutOrder::getUpdatedAt, now);
        if (markPaidAt) {
            wrapper.set(PayoutOrder::getPaidAt, now);
        }
        int rows = payoutOrderMapper.update(null, wrapper);
        if (rows == 0) {
            throw new BizException(ErrorCode.PAYOUT_STATE_INVALID, invalidMessage);
        }
        log.info("payout transit payoutId={} from={} to={}", id, from.getCode(), to.getCode());
    }

    private void validateSubject(Integer subjectType, Long subjectId) {
        if (subjectType == null
                || (subjectType != SUBJECT_TYPE_MERCHANT && subjectType != SUBJECT_TYPE_RIDER)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "subjectType 只能是 1商家 或 2骑手");
        }
        if (subjectId == null || subjectId <= 0L) {
            throw new BizException(ErrorCode.PARAM_INVALID, "subjectId 不合法");
        }
    }

    private long normalizePageNum(long pageNum) {
        return pageNum < 1L ? 1L : pageNum;
    }

    private long normalizePageSize(long pageSize) {
        return pageSize < 1L ? 20L : Math.min(pageSize, 100L);
    }

    private long nz(Long value) {
        return value == null ? 0L : value;
    }

    private String generatePayoutNo() {
        String time = NO_TIME_FORMAT.format(LocalDateTime.now());
        int random = ThreadLocalRandom.current().nextInt(1000, 10000);
        return "PO" + time + random;
    }

    private PayoutView toView(PayoutOrder order) {
        return new PayoutView(
                order.getId(),
                order.getPayoutNo(),
                order.getSubjectType(),
                order.getSubjectId(),
                order.getAmount(),
                order.getChannel(),
                order.getStatus(),
                order.getAuditRemark(),
                order.getAuditBy(),
                order.getAuditAt(),
                order.getPaidAt(),
                order.getCreatedAt()
        );
    }
}
