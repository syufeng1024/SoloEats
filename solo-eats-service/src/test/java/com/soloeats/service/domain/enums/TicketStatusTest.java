package com.soloeats.service.domain.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 锁定工单状态机：0待受理 → 1处理中 → 2待回访 → 3已办结；0/1/2 → 4已关闭。
 * 跳级与逆向回退必须为 false，否则条件更新会放行非法流转。
 */
class TicketStatusTest {

    @Test
    @DisplayName("正向链路 0→1→2→3 合法")
    void happyPath() {
        assertThat(TicketStatus.canTransit(0, 1)).isTrue();
        assertThat(TicketStatus.canTransit(1, 2)).isTrue();
        assertThat(TicketStatus.canTransit(2, 3)).isTrue();
    }

    @Test
    @DisplayName("0/1/2 均可关闭到 4")
    void closeFromActiveStatuses() {
        assertThat(TicketStatus.canTransit(0, 4)).isTrue();
        assertThat(TicketStatus.canTransit(1, 4)).isTrue();
        assertThat(TicketStatus.canTransit(2, 4)).isTrue();
    }

    @Test
    @DisplayName("跳级流转被拒绝：0→2、0→3、1→3")
    void skipLevelRejected() {
        assertThat(TicketStatus.canTransit(0, 2)).isFalse();
        assertThat(TicketStatus.canTransit(0, 3)).isFalse();
        assertThat(TicketStatus.canTransit(1, 3)).isFalse();
    }

    @Test
    @DisplayName("逆向回退被拒绝：1→0、2→1、3→2")
    void reverseRejected() {
        assertThat(TicketStatus.canTransit(1, 0)).isFalse();
        assertThat(TicketStatus.canTransit(2, 1)).isFalse();
        assertThat(TicketStatus.canTransit(3, 2)).isFalse();
    }

    @Test
    @DisplayName("终态 3/4 不再接受任何流转")
    void terminalRejectsAll() {
        for (int target = 0; target <= 4; target++) {
            assertThat(TicketStatus.canTransit(3, target)).isFalse();
            assertThat(TicketStatus.canTransit(4, target)).isFalse();
        }
    }

    @Test
    @DisplayName("同状态与非法入参一律拒绝")
    void invalidArgumentsRejected() {
        assertThat(TicketStatus.canTransit(0, 0)).isFalse();
        assertThat(TicketStatus.canTransit(1, 1)).isFalse();
        assertThat(TicketStatus.canTransit(null, 1)).isFalse();
        assertThat(TicketStatus.canTransit(0, null)).isFalse();
        assertThat(TicketStatus.canTransit(-1, 1)).isFalse();
        assertThat(TicketStatus.canTransit(0, 99)).isFalse();
    }

    @Test
    @DisplayName("状态码映射")
    void codeMapping() {
        assertThat(TicketStatus.fromCode(0)).isEqualTo(TicketStatus.PENDING);
        assertThat(TicketStatus.fromCode(4)).isEqualTo(TicketStatus.CLOSED);
        assertThat(TicketStatus.isValid(3)).isTrue();
        assertThat(TicketStatus.isValid(9)).isFalse();
    }
}
