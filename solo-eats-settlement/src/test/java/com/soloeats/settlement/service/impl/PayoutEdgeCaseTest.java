package com.soloeats.settlement.service.impl;

import com.soloeats.common.exception.BizException;
import com.soloeats.common.exception.ErrorCode;
import com.soloeats.common.lock.DistributedLock;
import com.soloeats.settlement.domain.entity.PayoutOrder;
import com.soloeats.settlement.domain.enums.PayoutStatus;
import com.soloeats.settlement.mapper.PayoutOrderMapper;
import com.soloeats.settlement.service.FundFlowService;
import com.soloeats.settlement.service.PayoutService;
import com.soloeats.settlement.service.view.PayoutView;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * T16 阶段B 提现边界与并发防线（QA 独立补充）。
 *
 * <p>重点：金额边界、锁超时时不落单、锁 key 与租约是否真的覆盖「查余额→校验→落单」、
 * 以及第二次申请能否看到第一次已占用的余额（锁内串行的关键前提）。
 */
@ExtendWith(MockitoExtension.class)
class PayoutEdgeCaseTest {

    private static final long MERCHANT_ID = 5001L;
    private static final long RIDER_ID = 6001L;

    @Mock
    private PayoutOrderMapper payoutOrderMapper;
    @Mock
    private DistributedLock distributedLock;
    @Mock
    private FundFlowService fundFlowService;

    private PayoutServiceImpl payoutService;

    @BeforeAll
    static void initMybatisPlusMetadata() {
        // 纯单测不启动 Spring，LambdaUpdateWrapper 需要显式注册实体列缓存
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), PayoutOrder.class);
    }

    private void initService() {
        payoutService = new PayoutServiceImpl(payoutOrderMapper, distributedLock, fundFlowService);
    }

    private void letLockRunSynchronously() {
        when(distributedLock.executeWithLock(
                anyString(), anyLong(), anyLong(), ArgumentMatchers.<Supplier<Object>>any()))
                .thenAnswer(invocation -> ((Supplier<Object>) invocation.getArgument(3)).get());
    }

    @Test
    @DisplayName("金额为 0 / 负数：参数错误，且在进入锁之前就拒绝")
    void nonPositiveAmountRejectedBeforeLock() {
        initService();

        assertThatThrownBy(() -> payoutService.apply(
                PayoutService.SUBJECT_TYPE_MERCHANT, MERCHANT_ID, 0L, 1))
                .isInstanceOfSatisfying(BizException.class, ex ->
                        assertThat(ex.getCode()).isEqualTo(ErrorCode.PARAM_INVALID.getCode()));
        assertThatThrownBy(() -> payoutService.apply(
                PayoutService.SUBJECT_TYPE_MERCHANT, MERCHANT_ID, -100L, 1))
                .isInstanceOfSatisfying(BizException.class, ex ->
                        assertThat(ex.getCode()).isEqualTo(ErrorCode.PARAM_INVALID.getCode()));

        verify(distributedLock, never()).executeWithLock(
                anyString(), anyLong(), anyLong(), ArgumentMatchers.<Supplier<Object>>any());
        verify(payoutOrderMapper, never()).insert(any(PayoutOrder.class));
    }

    @Test
    @DisplayName("超过单笔上限 100 万元（分）：参数错误")
    void amountAboveMaxRejected() {
        initService();

        assertThatThrownBy(() -> payoutService.apply(
                PayoutService.SUBJECT_TYPE_MERCHANT, MERCHANT_ID,
                PayoutServiceImpl.MAX_PAYOUT_AMOUNT + 1L, 1))
                .isInstanceOfSatisfying(BizException.class, ex ->
                        assertThat(ex.getCode()).isEqualTo(ErrorCode.PARAM_INVALID.getCode()));

        verify(payoutOrderMapper, never()).insert(any(PayoutOrder.class));
    }

    @Test
    @DisplayName("恰好等于单笔上限：金额校验通过，改由余额拦截（60004）")
    void amountEqualToMaxPassesAmountValidation() {
        initService();
        letLockRunSynchronously();
        when(payoutOrderMapper.countActiveSettlementAccount(MERCHANT_ID)).thenReturn(1);
        when(payoutOrderMapper.sumMerchantSettledPayable(MERCHANT_ID)).thenReturn(1_000L);
        when(payoutOrderMapper.sumActivePayoutAmount(1, MERCHANT_ID)).thenReturn(0L);

        assertThatThrownBy(() -> payoutService.apply(
                PayoutService.SUBJECT_TYPE_MERCHANT, MERCHANT_ID,
                PayoutServiceImpl.MAX_PAYOUT_AMOUNT, 1))
                .isInstanceOfSatisfying(BizException.class, ex ->
                        assertThat(ex.getCode()).isEqualTo(ErrorCode.PAYOUT_AMOUNT_EXCEEDED.getCode()));
    }

    @Test
    @DisplayName("锁获取超时：向上抛 40005，绝不静默继续落单")
    void lockTimeoutFailsFastWithoutInsert() {
        initService();
        when(distributedLock.executeWithLock(
                anyString(), anyLong(), anyLong(), ArgumentMatchers.<Supplier<Object>>any()))
                .thenThrow(new BizException(ErrorCode.LOCK_TIMEOUT));

        assertThatThrownBy(() -> payoutService.apply(
                PayoutService.SUBJECT_TYPE_MERCHANT, MERCHANT_ID, 1_000L, 1))
                .isInstanceOfSatisfying(BizException.class, ex ->
                        assertThat(ex.getCode()).isEqualTo(ErrorCode.LOCK_TIMEOUT.getCode()));

        verify(payoutOrderMapper, never()).insert(any(PayoutOrder.class));
        verify(payoutOrderMapper, never()).sumMerchantSettledPayable(any());
    }

    @Test
    @DisplayName("锁 key 为 payout:apply:{subjectType}:{subjectId}，租约需覆盖查余额→校验→落单")
    void lockKeyAndLeaseAreAccountScoped() {
        initService();
        letLockRunSynchronously();
        when(payoutOrderMapper.countActiveSettlementAccount(MERCHANT_ID)).thenReturn(1);
        when(payoutOrderMapper.sumMerchantSettledPayable(MERCHANT_ID)).thenReturn(10_000L);
        when(payoutOrderMapper.sumActivePayoutAmount(1, MERCHANT_ID)).thenReturn(0L);
        when(payoutOrderMapper.insert(any(PayoutOrder.class))).thenAnswer(invocation -> {
            ((PayoutOrder) invocation.getArgument(0)).setId(701L);
            return 1;
        });

        payoutService.apply(PayoutService.SUBJECT_TYPE_MERCHANT, MERCHANT_ID, 5_000L, 1);

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Long> waitCaptor = ArgumentCaptor.forClass(Long.class);
        ArgumentCaptor<Long> leaseCaptor = ArgumentCaptor.forClass(Long.class);
        verify(distributedLock).executeWithLock(
                keyCaptor.capture(), waitCaptor.capture(), leaseCaptor.capture(),
                ArgumentMatchers.<Supplier<Object>>any());

        assertThat(keyCaptor.getValue())
                .isEqualTo("payout:apply:" + PayoutService.SUBJECT_TYPE_MERCHANT + ":" + MERCHANT_ID);
        assertThat(waitCaptor.getValue()).isEqualTo(PayoutServiceImpl.LOCK_WAIT_SECONDS);
        // 租约为固定值（Redisson tryLock(wait, lease) 不会自动续期）：一旦 body 超过租约，
        // 锁会提前释放，第二个请求可再次通过余额校验 → 需靠放大租约或改用 watchdog 兜底。
        assertThat(leaseCaptor.getValue()).isEqualTo(PayoutServiceImpl.LOCK_LEASE_SECONDS);
    }

    @Test
    @DisplayName("锁内串行：第二笔申请能看到第一笔已占用金额，超额被 60004 拦住")
    void secondApplySeesAmountOccupiedByFirst() {
        initService();
        letLockRunSynchronously();
        when(payoutOrderMapper.countActiveSettlementAccount(MERCHANT_ID)).thenReturn(1);
        when(payoutOrderMapper.sumMerchantSettledPayable(MERCHANT_ID)).thenReturn(10_000L);
        // 第一次查询时未被占用，第一次落单后占用变 6000
        when(payoutOrderMapper.sumActivePayoutAmount(1, MERCHANT_ID)).thenReturn(0L, 6_000L);
        AtomicLong ids = new AtomicLong(700L);
        when(payoutOrderMapper.insert(any(PayoutOrder.class))).thenAnswer(invocation -> {
            ((PayoutOrder) invocation.getArgument(0)).setId(ids.incrementAndGet());
            return 1;
        });

        PayoutView first = payoutService.apply(
                PayoutService.SUBJECT_TYPE_MERCHANT, MERCHANT_ID, 6_000L, 1);
        assertThat(first.id()).isEqualTo(701L);

        assertThatThrownBy(() -> payoutService.apply(
                PayoutService.SUBJECT_TYPE_MERCHANT, MERCHANT_ID, 6_000L, 1))
                .isInstanceOfSatisfying(BizException.class, ex ->
                        assertThat(ex.getCode()).isEqualTo(ErrorCode.PAYOUT_AMOUNT_EXCEEDED.getCode()));

        // 只落了第一笔
        verify(payoutOrderMapper, org.mockito.Mockito.times(1)).insert(any(PayoutOrder.class));
    }

    @Test
    @DisplayName("骑手提现不校验结算账户")
    void riderDoesNotRequireSettlementAccount() {
        initService();
        letLockRunSynchronously();
        when(payoutOrderMapper.sumRiderIncome(RIDER_ID)).thenReturn(10_000L);
        when(payoutOrderMapper.sumActivePayoutAmount(2, RIDER_ID)).thenReturn(0L);
        when(payoutOrderMapper.insert(any(PayoutOrder.class))).thenAnswer(invocation -> {
            ((PayoutOrder) invocation.getArgument(0)).setId(702L);
            return 1;
        });

        PayoutView view = payoutService.apply(
                PayoutService.SUBJECT_TYPE_RIDER, RIDER_ID, 1_000L, 2);

        assertThat(view.id()).isEqualTo(702L);
        verify(payoutOrderMapper, never()).countActiveSettlementAccount(any());
    }

    @Test
    @DisplayName("状态机禁止跳级与逆向：0→30 / 10→30 / 20→10 / 30→任意 全部非法")
    void stateMachineForbidsSkipAndReverse() {
        assertThat(PayoutStatus.canTransit(0, PayoutStatus.APPROVED.getCode())).isTrue();
        assertThat(PayoutStatus.canTransit(0, PayoutStatus.REJECTED.getCode())).isTrue();
        assertThat(PayoutStatus.canTransit(10, PayoutStatus.PAYING.getCode())).isTrue();
        assertThat(PayoutStatus.canTransit(20, PayoutStatus.ARRIVED.getCode())).isTrue();

        assertThat(PayoutStatus.canTransit(0, PayoutStatus.ARRIVED.getCode())).isFalse();
        assertThat(PayoutStatus.canTransit(10, PayoutStatus.ARRIVED.getCode())).isFalse();
        assertThat(PayoutStatus.canTransit(20, PayoutStatus.APPROVED.getCode())).isFalse();
        assertThat(PayoutStatus.canTransit(30, PayoutStatus.APPROVED.getCode())).isFalse();
        assertThat(PayoutStatus.canTransit(40, PayoutStatus.PENDING.getCode())).isFalse();
        assertThat(PayoutStatus.canTransit(0, PayoutStatus.PENDING.getCode())).isFalse();
    }

    @Test
    @DisplayName("8 线程并发申请：锁体内串行，累计落单金额永不越过可用余额（无超提）")
    void concurrentAppliesNeverOverdraw() throws Exception {
        initService();
        ReentrantLock mutex = new ReentrantLock();
        when(distributedLock.executeWithLock(
                anyString(), anyLong(), anyLong(), ArgumentMatchers.<Supplier<Object>>any()))
                .thenAnswer(invocation -> {
                    mutex.lock();
                    try {
                        return ((Supplier<Object>) invocation.getArgument(3)).get();
                    } finally {
                        mutex.unlock();
                    }
                });
        when(payoutOrderMapper.countActiveSettlementAccount(MERCHANT_ID)).thenReturn(1);
        when(payoutOrderMapper.sumMerchantSettledPayable(MERCHANT_ID)).thenReturn(10_000L);
        AtomicLong occupied = new AtomicLong();
        when(payoutOrderMapper.sumActivePayoutAmount(1, MERCHANT_ID))
                .thenAnswer(invocation -> occupied.get());
        AtomicLong ids = new AtomicLong(800L);
        when(payoutOrderMapper.insert(any(PayoutOrder.class))).thenAnswer(invocation -> {
            PayoutOrder order = invocation.getArgument(0);
            order.setId(ids.incrementAndGet());
            occupied.addAndGet(order.getAmount());
            return 1;
        });

        int threads = 8;
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        AtomicInteger accepted = new AtomicInteger();
        AtomicInteger rejected = new AtomicInteger();
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        for (int i = 0; i < threads; i++) {
            pool.execute(() -> {
                try {
                    start.await();
                    payoutService.apply(PayoutService.SUBJECT_TYPE_MERCHANT, MERCHANT_ID, 3_000L, 1);
                    accepted.incrementAndGet();
                } catch (BizException ex) {
                    assertThat(ex.getCode()).isEqualTo(ErrorCode.PAYOUT_AMOUNT_EXCEEDED.getCode());
                    rejected.incrementAndGet();
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }
        start.countDown();
        assertThat(done.await(10, TimeUnit.SECONDS)).isTrue();
        pool.shutdownNow();

        // 可用余额 10000 分，单笔 3000 分 → 最多 3 笔通过
        assertThat(accepted.get()).isEqualTo(3);
        assertThat(rejected.get()).isEqualTo(5);
        assertThat(occupied.get()).isLessThanOrEqualTo(10_000L);
    }

    @Test
    @DisplayName("未审核直接登记到账：条件更新 0 行，抛 60005，且不写提现支出流水")
    void arriveWithoutPayingFailsAndWritesNoFlow() {
        initService();
        when(payoutOrderMapper.update(any(), any())).thenReturn(0);

        assertThatThrownBy(() -> payoutService.arrive(1L))
                .isInstanceOfSatisfying(BizException.class, ex ->
                        assertThat(ex.getCode()).isEqualTo(ErrorCode.PAYOUT_STATE_INVALID.getCode()));

        verify(fundFlowService, never()).recordPayoutExpense(any());
    }
}
