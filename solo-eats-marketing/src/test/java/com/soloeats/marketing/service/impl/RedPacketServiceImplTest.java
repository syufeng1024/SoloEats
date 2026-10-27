package com.soloeats.marketing.service.impl;

import com.soloeats.common.exception.BizException;
import com.soloeats.common.exception.ErrorCode;
import com.soloeats.marketing.domain.entity.RedPacket;
import com.soloeats.marketing.domain.entity.UserRedPacket;
import com.soloeats.marketing.mapper.RedPacketMapper;
import com.soloeats.marketing.mapper.UserRedPacketMapper;
import com.soloeats.marketing.service.view.RedPacketView;
import com.soloeats.marketing.support.RedPacketSplitter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 红包单测。split 是纯函数，直接断言总额守恒；grab 的判重/抢完/最后一人分支用 Mockito。
 */
@ExtendWith(MockitoExtension.class)
class RedPacketServiceImplTest {

    private static final long USER_ID = 5001L;
    private static final long PACKET_ID = 6001L;
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    @Mock
    private RedPacketMapper redPacketMapper;

    @Mock
    private UserRedPacketMapper userRedPacketMapper;

    @InjectMocks
    private RedPacketServiceImpl redPacketService;

    // ---------------------------------------------------------------
    // 二倍均值法纯函数
    // ---------------------------------------------------------------

    @Test
    @DisplayName("split：10000 分拆 7 人，sum==10000 且每人 ≥1，最后一人取剩余")
    void splitConservesTotal() {
        for (long seed = 0; seed < 100; seed++) {
            List<Long> parts = RedPacketSplitter.split(10000L, 7, new Random(seed));

            assertThat(parts).hasSize(7);
            assertThat(parts).allSatisfy(part -> assertThat(part).isGreaterThanOrEqualTo(1L));
            assertThat(parts.stream().mapToLong(Long::longValue).sum()).isEqualTo(10000L);

            long firstSix = parts.subList(0, 6).stream().mapToLong(Long::longValue).sum();
            assertThat(parts.get(6)).isEqualTo(10000L - firstSix);
        }
    }

    @Test
    @DisplayName("split：单人取全额；总额小于份数时拒绝")
    void splitSingleAndInvalidTotal() {
        assertThat(RedPacketSplitter.split(999L, 1, new Random(1))).containsExactly(999L);

        assertThatThrownBy(() -> RedPacketSplitter.split(9L, 10, new Random(1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ---------------------------------------------------------------
    // grab
    // ---------------------------------------------------------------

    @Test
    @DisplayName("最后一人：remainQty=1 时直接取 total - 已发金额")
    void lastPersonReceivesExactRemainder() {
        RedPacket packet = packet(1000L, 1);
        when(redPacketMapper.selectForUpdate(PACKET_ID)).thenReturn(packet);
        when(userRedPacketMapper.selectByPacketAndUser(PACKET_ID, USER_ID)).thenReturn(null);
        when(userRedPacketMapper.sumAmountByPacket(PACKET_ID)).thenReturn(400L);
        when(redPacketMapper.reserveOne(PACKET_ID)).thenReturn(1);
        when(userRedPacketMapper.insert(any(UserRedPacket.class))).thenReturn(1);

        Long amount = redPacketService.grab(PACKET_ID, USER_ID);

        assertThat(amount).isEqualTo(600L);
        ArgumentCaptor<UserRedPacket> captor = ArgumentCaptor.forClass(UserRedPacket.class);
        verify(userRedPacketMapper).insert(captor.capture());
        UserRedPacket saved = captor.getValue();
        assertThat(saved.getAmount()).isEqualTo(600L);
        assertThat(saved.getStatus()).isZero();
        assertThat(saved.getExpireAt()).isEqualTo(packet.getEndAt());
    }

    @Test
    @DisplayName("remainQty=0：抢失败，不扣减、不写用户红包")
    void grabRejectedWhenEmpty() {
        when(redPacketMapper.selectForUpdate(PACKET_ID)).thenReturn(packet(1000L, 0));

        assertThatThrownBy(() -> redPacketService.grab(PACKET_ID, USER_ID))
                .isInstanceOfSatisfying(BizException.class,
                        ex -> assertThat(ex.getCode())
                                .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND.getCode()));

        verify(redPacketMapper, never()).reserveOne(any());
        verify(userRedPacketMapper, never()).insert(any(UserRedPacket.class));
    }

    @Test
    @DisplayName("非最后一人：金额落在 [1, 2*剩余/份数] 内，先扣份数再写红包")
    void grabNonLastAmountWithinBound() {
        RedPacket packet = packet(1000L, 4);
        when(redPacketMapper.selectForUpdate(PACKET_ID)).thenReturn(packet);
        when(userRedPacketMapper.selectByPacketAndUser(PACKET_ID, USER_ID)).thenReturn(null);
        when(userRedPacketMapper.sumAmountByPacket(PACKET_ID)).thenReturn(0L);
        when(redPacketMapper.reserveOne(PACKET_ID)).thenReturn(1);
        when(userRedPacketMapper.insert(any(UserRedPacket.class))).thenReturn(1);

        Long amount = redPacketService.grab(PACKET_ID, USER_ID);

        assertThat(amount).isBetween(1L, 500L);
        verify(redPacketMapper).reserveOne(PACKET_ID);
        verify(userRedPacketMapper).insert(any(UserRedPacket.class));
    }

    @Test
    @DisplayName("同一用户重复领取：返回原金额，不扣份数、不重复写")
    void grabReturnsExistingForDuplicateUser() {
        RedPacket packet = packet(1000L, 4);
        UserRedPacket existing = new UserRedPacket();
        existing.setAmount(123L);
        when(redPacketMapper.selectForUpdate(PACKET_ID)).thenReturn(packet);
        when(userRedPacketMapper.selectByPacketAndUser(PACKET_ID, USER_ID))
                .thenReturn(existing);

        Long amount = redPacketService.grab(PACKET_ID, USER_ID);

        assertThat(amount).isEqualTo(123L);
        verify(redPacketMapper, never()).reserveOne(any());
        verify(userRedPacketMapper, never()).insert(any(UserRedPacket.class));
    }

    @Test
    @DisplayName("列表：已领取的红包标记 grabbed=true")
    void listAvailableMarksGrabbed() {
        RedPacket first = packet(100L, 5);
        first.setId(1L);
        RedPacket second = packet(200L, 5);
        second.setId(2L);
        when(redPacketMapper.selectAvailable(any(LocalDateTime.class)))
                .thenReturn(List.of(first, second));
        when(userRedPacketMapper.selectPacketIdsByUser(USER_ID)).thenReturn(List.of(1L));

        List<RedPacketView> views = redPacketService.listAvailable(USER_ID);

        assertThat(views).hasSize(2);
        assertThat(views.get(0).grabbed()).isTrue();
        assertThat(views.get(1).grabbed()).isFalse();
    }

    private static RedPacket packet(long totalAmount, int remainQty) {
        LocalDateTime now = LocalDateTime.now(ZONE);
        RedPacket packet = new RedPacket();
        packet.setId(PACKET_ID);
        packet.setPacketType(1);
        packet.setTotalAmount(totalAmount);
        packet.setTotalQty(remainQty);
        packet.setRemainQty(remainQty);
        packet.setStartAt(now.minusHours(1));
        packet.setEndAt(now.plusHours(1));
        packet.setStatus(1);
        packet.setDeleted(0);
        return packet;
    }
}
