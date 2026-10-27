package com.soloeats.marketing.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.soloeats.common.exception.BizException;
import com.soloeats.common.exception.ErrorCode;
import com.soloeats.marketing.domain.entity.PointAccount;
import com.soloeats.marketing.domain.entity.PointLog;
import com.soloeats.marketing.mapper.PointAccountMapper;
import com.soloeats.marketing.mapper.PointLogMapper;
import com.soloeats.marketing.service.view.PointAccountView;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 积分账户单测。不连真实 DB/Redis，全部通过 Mockito 校验
 * 「先插流水占 uk_biz，再条件更新余额」的顺序与幂等分支。
 */
@ExtendWith(MockitoExtension.class)
class PointServiceImplTest {

    private static final long USER_ID = 1001L;
    private static final long BIZ_ID = 9001L;
    private static final LocalDate EXPIRE_DATE = LocalDate.of(2026, 10, 27);

    @Mock
    private PointAccountMapper pointAccountMapper;

    @Mock
    private PointLogMapper pointLogMapper;

    @InjectMocks
    private PointServiceImpl pointService;

    @BeforeAll
    static void initMybatisPlusMetadata() {
        // requireAccount 用 LambdaQueryWrapper 解析列名，纯单测需注册实体列缓存
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, PointAccount.class);
    }

    // ---------------------------------------------------------------
    // 发放 / 消耗
    // ---------------------------------------------------------------

    @Test
    @DisplayName("earn：条件更新返回 1，写正向流水并成功返回")
    void earnWritesPositiveLog() {
        when(pointLogMapper.countByBiz(1, BIZ_ID)).thenReturn(0);
        when(pointAccountMapper.selectOne(any())).thenReturn(account(100));
        when(pointLogMapper.insert(any(PointLog.class))).thenReturn(1);
        when(pointAccountMapper.changeBalance(USER_ID, 50)).thenReturn(1);

        pointService.earn(USER_ID, 1, BIZ_ID, 50, "下单奖励");

        ArgumentCaptor<PointLog> captor = ArgumentCaptor.forClass(PointLog.class);
        verify(pointLogMapper).insert(captor.capture());
        PointLog saved = captor.getValue();
        assertThat(saved.getSource()).isEqualTo(1);
        assertThat(saved.getBizId()).isEqualTo(BIZ_ID);
        assertThat(saved.getPoint()).isEqualTo(50);
        assertThat(saved.getBeforeBalance()).isEqualTo(100);
        assertThat(saved.getAfterBalance()).isEqualTo(150);
        assertThat(saved.getExpireAt()).isNotNull();

        verify(pointAccountMapper).changeBalance(USER_ID, 50);
    }

    @Test
    @DisplayName("consume：余额不足时快速失败，不写流水、不改余额")
    void consumeThrowsWhenBalanceNotEnough() {
        when(pointLogMapper.countByBiz(5, BIZ_ID)).thenReturn(0);
        when(pointAccountMapper.selectOne(any())).thenReturn(account(30));

        assertThatThrownBy(() -> pointService.consume(USER_ID, 5, BIZ_ID, 50, "积分兑换"))
                .isInstanceOfSatisfying(BizException.class,
                        ex -> assertThat(ex.getCode())
                                .isEqualTo(ErrorCode.POINT_NOT_ENOUGH.getCode()));

        verify(pointLogMapper, never()).insert(any(PointLog.class));
        verify(pointAccountMapper, never()).changeBalance(anyLong(), anyInt());
    }

    @Test
    @DisplayName("consume：条件更新返回 0 行（并发扣减）→ 40206")
    void consumeThrowsWhenConditionalUpdateReturnsZero() {
        when(pointLogMapper.countByBiz(5, BIZ_ID)).thenReturn(0);
        when(pointAccountMapper.selectOne(any())).thenReturn(account(100));
        when(pointLogMapper.insert(any(PointLog.class))).thenReturn(1);
        when(pointAccountMapper.changeBalance(USER_ID, -50)).thenReturn(0);

        assertThatThrownBy(() -> pointService.consume(USER_ID, 5, BIZ_ID, 50, "积分兑换"))
                .isInstanceOfSatisfying(BizException.class,
                        ex -> assertThat(ex.getCode())
                                .isEqualTo(ErrorCode.POINT_NOT_ENOUGH.getCode()));

        verify(pointLogMapper).insert(any(PointLog.class));
        verify(pointAccountMapper).changeBalance(USER_ID, -50);
    }

    // ---------------------------------------------------------------
    // 幂等
    // ---------------------------------------------------------------

    @Test
    @DisplayName("earn 幂等：第二次预检查命中已有流水，不再加减余额")
    void earnIsIdempotentWhenBizAlreadyExists() {
        when(pointLogMapper.countByBiz(1, BIZ_ID)).thenReturn(0, 1);
        when(pointAccountMapper.selectOne(any())).thenReturn(account(100));
        when(pointLogMapper.insert(any(PointLog.class))).thenReturn(1);
        when(pointAccountMapper.changeBalance(USER_ID, 50)).thenReturn(1);

        pointService.earn(USER_ID, 1, BIZ_ID, 50, "下单奖励");
        pointService.earn(USER_ID, 1, BIZ_ID, 50, "下单奖励");

        verify(pointLogMapper, times(1)).insert(any(PointLog.class));
        verify(pointAccountMapper, times(1)).changeBalance(USER_ID, 50);
    }

    @Test
    @DisplayName("earn 幂等：并发下第二次插入撞 uk_biz → 不抛错且不再加减余额")
    void earnIsIdempotentWhenInsertHitsDuplicateKey() {
        when(pointLogMapper.countByBiz(anyInt(), anyLong())).thenReturn(0);
        when(pointAccountMapper.selectOne(any())).thenReturn(account(100));
        when(pointLogMapper.insert(any(PointLog.class)))
                .thenReturn(1)
                .thenThrow(new DuplicateKeyException("uk_biz"));
        when(pointAccountMapper.changeBalance(USER_ID, 50)).thenReturn(1);

        assertThatCode(() -> {
            pointService.earn(USER_ID, 1, BIZ_ID, 50, "下单奖励");
            pointService.earn(USER_ID, 1, BIZ_ID, 50, "下单奖励");
        }).doesNotThrowAnyException();

        verify(pointLogMapper, times(2)).insert(any(PointLog.class));
        verify(pointAccountMapper, times(1)).changeBalance(USER_ID, 50);
    }

    // ---------------------------------------------------------------
    // 过期
    // ---------------------------------------------------------------

    @Test
    @DisplayName("expirePoints：过期积分大于余额时只扣到 0，delta=-min(积分,余额)")
    void expirePointsClampsToCurrentBalance() {
        PointLog batch = expiredBatch(7001L, 100);
        when(pointLogMapper.selectExpiredBatches(any(), eq(0L), eq(500)))
                .thenReturn(List.of(batch));
        when(pointLogMapper.countByBiz(6, 7001L)).thenReturn(0);
        when(pointAccountMapper.selectOne(any())).thenReturn(account(30));
        when(pointLogMapper.insert(any(PointLog.class))).thenReturn(1);
        when(pointAccountMapper.changeBalance(USER_ID, -30)).thenReturn(1);

        int expired = pointService.expirePoints(EXPIRE_DATE);

        assertThat(expired).isEqualTo(1);
        ArgumentCaptor<PointLog> captor = ArgumentCaptor.forClass(PointLog.class);
        verify(pointLogMapper).insert(captor.capture());
        PointLog saved = captor.getValue();
        assertThat(saved.getSource()).isEqualTo(6);
        assertThat(saved.getBizId()).isEqualTo(7001L);
        assertThat(saved.getPoint()).isEqualTo(-30);
        assertThat(saved.getBeforeBalance()).isEqualTo(30);
        assertThat(saved.getAfterBalance()).isZero();

        verify(pointAccountMapper).changeBalance(USER_ID, -30);
        verify(pointAccountMapper, never()).changeBalance(USER_ID, -100);
    }

    @Test
    @DisplayName("expirePoints 幂等：同一原流水重复执行只生成一条负数流水")
    void expirePointsIsIdempotent() {
        PointLog batch = expiredBatch(7001L, 100);
        when(pointLogMapper.selectExpiredBatches(any(), eq(0L), eq(500)))
                .thenReturn(List.of(batch));
        when(pointLogMapper.countByBiz(6, 7001L)).thenReturn(0, 1);
        when(pointAccountMapper.selectOne(any())).thenReturn(account(100));
        when(pointLogMapper.insert(any(PointLog.class))).thenReturn(1);
        when(pointAccountMapper.changeBalance(USER_ID, -100)).thenReturn(1);

        assertThat(pointService.expirePoints(EXPIRE_DATE)).isEqualTo(1);
        assertThat(pointService.expirePoints(EXPIRE_DATE)).isZero();

        verify(pointLogMapper, times(1)).insert(any(PointLog.class));
        verify(pointAccountMapper, times(1)).changeBalance(USER_ID, -100);
    }

    // ---------------------------------------------------------------
    // 账户初始化
    // ---------------------------------------------------------------

    @Test
    @DisplayName("account：账户不存在时由 ensureAccount 初始化，返回余额 0 而非 null/异常")
    void accountInitializesMissingAccountToZero() {
        AtomicBoolean created = new AtomicBoolean(false);
        when(pointAccountMapper.ensureAccount(USER_ID)).thenAnswer(invocation -> {
            created.set(true);
            return 1;
        });
        when(pointAccountMapper.selectOne(any()))
                .thenAnswer(invocation -> created.get() ? account(0) : null);

        PointAccountView view = pointService.account(USER_ID);

        assertThat(view).isNotNull();
        assertThat(view.userId()).isEqualTo(USER_ID);
        assertThat(view.balance()).isZero();
        assertThat(view.totalEarned()).isZero();
        assertThat(view.totalConsumed()).isZero();
        verify(pointAccountMapper).ensureAccount(USER_ID);
    }

    // ---------------------------------------------------------------
    // 辅助
    // ---------------------------------------------------------------

    private static PointAccount account(int balance) {
        PointAccount account = new PointAccount();
        account.setId(1L);
        account.setUserId(USER_ID);
        account.setBalance(balance);
        account.setTotalEarned((long) Math.max(balance, 0));
        account.setTotalConsumed(0L);
        return account;
    }

    private static PointLog expiredBatch(long id, int point) {
        PointLog log = new PointLog();
        log.setId(id);
        log.setUserId(USER_ID);
        log.setSource(1);
        log.setBizId(5000L + id);
        log.setPoint(point);
        log.setBeforeBalance(0);
        log.setAfterBalance(point);
        log.setExpireAt(LocalDateTime.now().minusDays(1));
        return log;
    }
}
