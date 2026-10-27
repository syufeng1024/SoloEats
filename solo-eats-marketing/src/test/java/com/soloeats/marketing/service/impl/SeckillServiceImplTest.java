package com.soloeats.marketing.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.soloeats.common.exception.BizException;
import com.soloeats.common.exception.ErrorCode;
import com.soloeats.marketing.domain.entity.SeckillActivity;
import com.soloeats.marketing.domain.entity.SeckillItem;
import com.soloeats.marketing.domain.entity.SeckillOrder;
import com.soloeats.marketing.mapper.SeckillActivityMapper;
import com.soloeats.marketing.mapper.SeckillItemMapper;
import com.soloeats.marketing.mapper.SeckillOrderMapper;
import com.soloeats.marketing.service.SeckillReserveResult;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.LocalDateTime;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SeckillServiceImplTest {

    private static final long ACTIVITY_ID = 31L;
    private static final long ITEM_ID = 11L;
    private static final long USER_ID = 21L;
    private static final long ORDER_ID = 41L;
    private static final String STOCK_KEY = "seckill:stock:" + ITEM_ID;

    @Mock
    private SeckillActivityMapper activityMapper;

    @Mock
    private SeckillItemMapper itemMapper;

    @Mock
    private SeckillOrderMapper orderMapper;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @InjectMocks
    private SeckillServiceImpl seckillService;

    @BeforeAll
    static void initMybatisPlusMetadata() {
        // 纯单测不启动 Spring，Lambda 列名解析需要显式注册实体列缓存
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, SeckillActivity.class);
        TableInfoHelper.initTableInfo(assistant, SeckillItem.class);
        TableInfoHelper.initTableInfo(assistant, SeckillOrder.class);
    }

    // ---------------------------------------------------------------
    // 预占：正常路径
    // ---------------------------------------------------------------

    @Test
    @DisplayName("正常预占：Redis 预扣后余量≥0、DB 条件更新 1 行、insert 预占单，返回 SK+orderId")
    void reserveSucceeds() {
        when(itemMapper.selectById(ITEM_ID)).thenReturn(item(10, 0, 5));
        when(activityMapper.selectById(ACTIVITY_ID)).thenReturn(ongoingActivity());
        when(orderMapper.selectByUserAndItem(USER_ID, ITEM_ID)).thenReturn(null);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment(STOCK_KEY, -2L)).thenReturn(8L);
        when(itemMapper.increaseSoldQty(ITEM_ID, 2)).thenReturn(1);
        when(orderMapper.insert(any(SeckillOrder.class))).thenAnswer(invocation -> {
            ((SeckillOrder) invocation.getArgument(0)).setId(ORDER_ID);
            return 1;
        });

        SeckillReserveResult result = seckillService.reserve(ITEM_ID, USER_ID, 2);

        assertThat(result.orderId()).isEqualTo(ORDER_ID);
        assertThat(result.itemId()).isEqualTo(ITEM_ID);
        assertThat(result.qty()).isEqualTo(2);
        assertThat(result.seckillPrice()).isEqualTo(9900L);
        assertThat(result.reserveToken()).isEqualTo("SK" + ORDER_ID);

        // 余量按 DB 当前值 (10-0) 初始化，而不是无条件塞总库存
        verify(valueOperations).setIfAbsent(STOCK_KEY, "10");
        verify(valueOperations).increment(STOCK_KEY, -2L);
        verify(itemMapper).increaseSoldQty(ITEM_ID, 2);

        ArgumentCaptor<SeckillOrder> captor = ArgumentCaptor.forClass(SeckillOrder.class);
        verify(orderMapper).insert(captor.capture());
        SeckillOrder saved = captor.getValue();
        assertThat(saved.getActivityId()).isEqualTo(ACTIVITY_ID);
        assertThat(saved.getItemId()).isEqualTo(ITEM_ID);
        assertThat(saved.getUserId()).isEqualTo(USER_ID);
        assertThat(saved.getQty()).isEqualTo(2);
        assertThat(saved.getStatus()).isZero();
    }

    // ---------------------------------------------------------------
    // 预占：失败路径与回补
    // ---------------------------------------------------------------

    @Test
    @DisplayName("Redis 扣减后为负：回补 Redis 并抛 40203，DB 不更新")
    void reserveRollsBackRedisWhenOutOfStock() {
        when(itemMapper.selectById(ITEM_ID)).thenReturn(item(1, 1, 5));
        when(activityMapper.selectById(ACTIVITY_ID)).thenReturn(ongoingActivity());
        when(orderMapper.selectByUserAndItem(USER_ID, ITEM_ID)).thenReturn(null);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment(STOCK_KEY, -2L)).thenReturn(-2L);

        assertThatThrownBy(() -> seckillService.reserve(ITEM_ID, USER_ID, 2))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getCode())
                .isEqualTo(ErrorCode.SECKILL_OUT_OF_STOCK.getCode());

        // key 不存在时初始化 0，再扣减；为负后 INCR 回补
        verify(valueOperations).setIfAbsent(STOCK_KEY, "0");
        verify(valueOperations).increment(STOCK_KEY, -2L);
        verify(valueOperations).increment(STOCK_KEY, 2L);
        verify(itemMapper, never()).increaseSoldQty(anyLong(), anyInt());
        verify(orderMapper, never()).insert(any(SeckillOrder.class));
    }

    @Test
    @DisplayName("Redis 预扣成功但 DB 条件更新 0 行：回补 Redis 并抛 40203")
    void reserveRollsBackRedisWhenDbRejects() {
        when(itemMapper.selectById(ITEM_ID)).thenReturn(item(10, 9, 5));
        when(activityMapper.selectById(ACTIVITY_ID)).thenReturn(ongoingActivity());
        when(orderMapper.selectByUserAndItem(USER_ID, ITEM_ID)).thenReturn(null);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment(STOCK_KEY, -2L)).thenReturn(0L);
        when(itemMapper.increaseSoldQty(ITEM_ID, 2)).thenReturn(0);

        assertThatThrownBy(() -> seckillService.reserve(ITEM_ID, USER_ID, 2))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getCode())
                .isEqualTo(ErrorCode.SECKILL_OUT_OF_STOCK.getCode());

        verify(valueOperations).increment(STOCK_KEY, 2L);
        verify(itemMapper).increaseSoldQty(ITEM_ID, 2);
        verify(itemMapper, never()).decreaseSoldQty(anyLong(), anyInt());
        verify(orderMapper, never()).insert(any(SeckillOrder.class));
    }

    @Test
    @DisplayName("限购超限（limitPerUser=1，qty=2）：抛 40204，不触达 Redis")
    void reserveRejectsOverLimit() {
        when(itemMapper.selectById(ITEM_ID)).thenReturn(item(10, 0, 1));
        when(activityMapper.selectById(ACTIVITY_ID)).thenReturn(ongoingActivity());

        assertThatThrownBy(() -> seckillService.reserve(ITEM_ID, USER_ID, 2))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getCode())
                .isEqualTo(ErrorCode.SECKILL_LIMIT_EXCEEDED.getCode());

        verifyNoInteractions(redisTemplate);
        verify(itemMapper, never()).increaseSoldQty(anyLong(), anyInt());
    }

    @Test
    @DisplayName("场次已结束：抛 40205")
    void reserveRejectsEndedSession() {
        when(itemMapper.selectById(ITEM_ID)).thenReturn(item(10, 0, 5));
        SeckillActivity ended = ongoingActivity();
        ended.setSessionEnd(LocalDateTime.now().minusMinutes(1));
        when(activityMapper.selectById(ACTIVITY_ID)).thenReturn(ended);

        assertThatThrownBy(() -> seckillService.reserve(ITEM_ID, USER_ID, 1))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getCode())
                .isEqualTo(ErrorCode.SECKILL_NOT_STARTED.getCode());

        verifyNoInteractions(redisTemplate);
        verify(itemMapper, never()).increaseSoldQty(anyLong(), anyInt());
    }

    @Test
    @DisplayName("已存在 status=0 的预占记录：抛 40204")
    void reserveRejectsExistingReservation() {
        when(itemMapper.selectById(ITEM_ID)).thenReturn(item(10, 0, 5));
        when(activityMapper.selectById(ACTIVITY_ID)).thenReturn(ongoingActivity());
        when(orderMapper.selectByUserAndItem(USER_ID, ITEM_ID)).thenReturn(order(0, 1));

        assertThatThrownBy(() -> seckillService.reserve(ITEM_ID, USER_ID, 1))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getCode())
                .isEqualTo(ErrorCode.SECKILL_LIMIT_EXCEEDED.getCode());

        verifyNoInteractions(redisTemplate);
        verify(orderMapper, never()).insert(any(SeckillOrder.class));
    }

    @Test
    @DisplayName("已存在 status=2 的旧记录：UPDATE 复用，不 INSERT")
    void reserveReusesReleasedOrder() {
        when(itemMapper.selectById(ITEM_ID)).thenReturn(item(10, 5, 5));
        when(activityMapper.selectById(ACTIVITY_ID)).thenReturn(ongoingActivity());
        when(orderMapper.selectByUserAndItem(USER_ID, ITEM_ID)).thenReturn(order(2, 1));
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment(STOCK_KEY, -2L)).thenReturn(3L);
        when(itemMapper.increaseSoldQty(ITEM_ID, 2)).thenReturn(1);
        when(orderMapper.reactivate(ORDER_ID, 2)).thenReturn(1);

        SeckillReserveResult result = seckillService.reserve(ITEM_ID, USER_ID, 2);

        assertThat(result.orderId()).isEqualTo(ORDER_ID);
        assertThat(result.reserveToken()).isEqualTo("SK" + ORDER_ID);
        verify(valueOperations).setIfAbsent(STOCK_KEY, "5");
        verify(orderMapper).reactivate(ORDER_ID, 2);
        verify(orderMapper, never()).insert(any(SeckillOrder.class));
    }

    // ---------------------------------------------------------------
    // 释放
    // ---------------------------------------------------------------

    @Test
    @DisplayName("释放幂等：status 已为 2 时直接返回，不回补库存")
    void releaseIsIdempotentWhenAlreadyReleased() {
        when(orderMapper.selectByUserAndItem(USER_ID, ITEM_ID)).thenReturn(order(2, 2));

        seckillService.release(ITEM_ID, USER_ID);

        verify(orderMapper, never()).release(anyLong());
        verify(itemMapper, never()).decreaseSoldQty(anyLong(), anyInt());
        verifyNoInteractions(redisTemplate);
    }

    @Test
    @DisplayName("释放正常：status 置 2 + DB sold_qty 回补 + Redis 回补各一次")
    void releaseRestoresStock() {
        when(orderMapper.selectByUserAndItem(USER_ID, ITEM_ID)).thenReturn(order(0, 2));
        when(orderMapper.release(ORDER_ID)).thenReturn(1);
        when(itemMapper.decreaseSoldQty(ITEM_ID, 2)).thenReturn(1);
        when(redisTemplate.hasKey(STOCK_KEY)).thenReturn(true);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        seckillService.release(ITEM_ID, USER_ID);

        verify(orderMapper).release(ORDER_ID);
        verify(itemMapper).decreaseSoldQty(ITEM_ID, 2);
        verify(valueOperations).increment(STOCK_KEY, 2L);
    }

    // ---------------------------------------------------------------
    // 并发：200 线程抢 10 件
    // ---------------------------------------------------------------

    /**
     * 任务卡「并发 200 不超卖」的分支级验证：不连真实 MySQL/Redis，
     * 用原子变量模拟 DB 条件更新（CAS 循环）与内存 Redis（SETNX + INCRBY），
     * 200 个线程同时抢 10 件，断言恰好卖出 10 件、DB 与 Redis 均不超卖且无余量泄漏。
     *
     * <p>不依赖线程调度顺序、不使用 sleep：起跑用 CountDownLatch 对齐，
     * 所有桩的可变状态只用 AtomicInteger / AtomicLong / ConcurrentHashMap。
     */
    @Test
    @DisplayName("并发 200 线程抢 10 件：恰好卖出 10 件，DB 与 Redis 均不超卖")
    void concurrentReserveDoesNotOversell() throws Exception {
        final int stock = 10;
        final int threads = 200;

        // ---- DB 条件更新：sold_qty + qty <= seckill_stock 才允许，CAS 模拟行锁语义 ----
        AtomicInteger sold = new AtomicInteger();
        when(itemMapper.selectById(ITEM_ID)).thenReturn(item(stock, 0, threads));
        when(activityMapper.selectById(ACTIVITY_ID)).thenReturn(ongoingActivity());
        when(itemMapper.increaseSoldQty(eq(ITEM_ID), anyInt())).thenAnswer(invocation -> {
            int qty = invocation.getArgument(1);
            while (true) {
                int current = sold.get();
                if (current + qty > stock) {
                    return 0;
                }
                if (sold.compareAndSet(current, current + qty)) {
                    return 1;
                }
            }
        });

        // ---- 内存 Redis：SETNX 初始化 + INCRBY 原子扣减，返回真实余量 ----
        ConcurrentHashMap<String, AtomicLong> redisStore = new ConcurrentHashMap<>();
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(anyString(), anyString())).thenAnswer(invocation -> {
            String key = invocation.getArgument(0);
            long initial = Long.parseLong(invocation.getArgument(1));
            return redisStore.putIfAbsent(key, new AtomicLong(initial)) == null;
        });
        when(valueOperations.increment(anyString(), anyLong())).thenAnswer(invocation -> {
            String key = invocation.getArgument(0);
            long delta = invocation.getArgument(1);
            return redisStore.computeIfAbsent(key, ignored -> new AtomicLong()).addAndGet(delta);
        });

        // ---- 预占单：每个 userId 首次抢购，insert 回填递增主键 ----
        when(orderMapper.selectByUserAndItem(anyLong(), anyLong())).thenReturn(null);
        AtomicLong orderIdSequence = new AtomicLong(1000L);
        when(orderMapper.insert(any(SeckillOrder.class))).thenAnswer(invocation -> {
            ((SeckillOrder) invocation.getArgument(0)).setId(orderIdSequence.incrementAndGet());
            return 1;
        });
        // 首次抢购不会命中 status=2 的复用分支，保留桩但不参与本次断言
        lenient().when(orderMapper.reactivate(anyLong(), anyInt())).thenReturn(1);

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch doneGate = new CountDownLatch(threads);
        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger outOfStockCount = new AtomicInteger();
        ConcurrentLinkedQueue<Throwable> unexpected = new ConcurrentLinkedQueue<>();

        try {
            for (int i = 0; i < threads; i++) {
                long userId = USER_ID + i;
                pool.execute(() -> {
                    try {
                        startGate.await();
                        seckillService.reserve(ITEM_ID, userId, 1);
                        successCount.incrementAndGet();
                    } catch (BizException ex) {
                        if (ex.getCode() == ErrorCode.SECKILL_OUT_OF_STOCK.getCode()) {
                            outOfStockCount.incrementAndGet();
                        } else {
                            unexpected.add(ex);
                        }
                    } catch (Throwable ex) {
                        unexpected.add(ex);
                    } finally {
                        doneGate.countDown();
                    }
                });
            }
            startGate.countDown();
            assertThat(doneGate.await(10, TimeUnit.SECONDS))
                    .as("200 个抢购线程应在 10 秒内全部结束")
                    .isTrue();
        } finally {
            pool.shutdownNow();
        }

        assertThat(unexpected)
                .as("除 40203 售罄外的意外异常：%s", unexpected)
                .isEmpty();
        assertThat(successCount.get()).as("成功预占数").isEqualTo(10);
        assertThat(sold.get()).as("DB sold_qty 最终值").isEqualTo(10);
        assertThat(sold.get()).as("DB 永不超卖").isLessThanOrEqualTo(stock);
        AtomicLong redisStock = redisStore.get(STOCK_KEY);
        assertThat(redisStock).as("Redis 库存键应已初始化").isNotNull();
        assertThat(redisStock.get()).as("Redis 余量应归零且无泄漏").isEqualTo(0);
        assertThat(outOfStockCount.get() + successCount.get())
                .as("剩余请求应全部按 40203 售罄处理")
                .isEqualTo(threads);
    }

    // ---------------------------------------------------------------
    // 测试数据
    // ---------------------------------------------------------------

    private static SeckillItem item(int seckillStock, int soldQty, int limitPerUser) {
        SeckillItem item = new SeckillItem();
        item.setId(ITEM_ID);
        item.setActivityId(ACTIVITY_ID);
        item.setShopId(1L);
        item.setSpuId(2L);
        item.setSkuId(3L);
        item.setSeckillPrice(9900L);
        item.setSeckillStock(seckillStock);
        item.setSoldQty(soldQty);
        item.setLimitPerUser(limitPerUser);
        item.setVersion(0);
        return item;
    }

    private static SeckillActivity ongoingActivity() {
        LocalDateTime now = LocalDateTime.now();
        SeckillActivity activity = new SeckillActivity();
        activity.setId(ACTIVITY_ID);
        activity.setActivityName("午市秒杀");
        activity.setSessionStart(now.minusHours(1));
        activity.setSessionEnd(now.plusHours(1));
        activity.setStatus(1);
        activity.setDeleted(0);
        return activity;
    }

    private static SeckillOrder order(int status, int qty) {
        SeckillOrder order = new SeckillOrder();
        order.setId(ORDER_ID);
        order.setActivityId(ACTIVITY_ID);
        order.setItemId(ITEM_ID);
        order.setUserId(USER_ID);
        order.setStatus(status);
        order.setQty(qty);
        return order;
    }
}
