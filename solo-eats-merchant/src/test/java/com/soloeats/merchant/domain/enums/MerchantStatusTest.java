package com.soloeats.merchant.domain.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MerchantStatusTest {

    @Test
    @DisplayName("草稿与已驳回可提交，待审核/已通过/已停用不可提交")
    void submitTransitions() {
        assertThat(MerchantStatus.canSubmit(MerchantStatus.DRAFT.code())).isTrue();
        assertThat(MerchantStatus.canSubmit(MerchantStatus.REJECTED.code())).isTrue();
        assertThat(MerchantStatus.canSubmit(MerchantStatus.PENDING.code())).isFalse();
        assertThat(MerchantStatus.canSubmit(MerchantStatus.APPROVED.code())).isFalse();
        assertThat(MerchantStatus.canSubmit(MerchantStatus.DISABLED.code())).isFalse();
        assertThat(MerchantStatus.canSubmit(null)).isFalse();
    }

    @Test
    @DisplayName("只有待审核可以审核通过/驳回：禁止草稿直接通过")
    void auditTransitions() {
        assertThat(MerchantStatus.canAudit(MerchantStatus.PENDING.code())).isTrue();
        assertThat(MerchantStatus.canAudit(MerchantStatus.DRAFT.code())).isFalse();
        assertThat(MerchantStatus.canAudit(MerchantStatus.REJECTED.code())).isFalse();
        assertThat(MerchantStatus.canAudit(MerchantStatus.APPROVED.code())).isFalse();
    }

    @Test
    @DisplayName("启停只允许 2↔4")
    void toggleTransitions() {
        assertThat(MerchantStatus.canToggle(
                MerchantStatus.APPROVED.code(), MerchantStatus.DISABLED.code())).isTrue();
        assertThat(MerchantStatus.canToggle(
                MerchantStatus.DISABLED.code(), MerchantStatus.APPROVED.code())).isTrue();
        assertThat(MerchantStatus.canToggle(
                MerchantStatus.PENDING.code(), MerchantStatus.DISABLED.code())).isFalse();
        assertThat(MerchantStatus.canToggle(
                MerchantStatus.APPROVED.code(), MerchantStatus.PENDING.code())).isFalse();
    }
}
