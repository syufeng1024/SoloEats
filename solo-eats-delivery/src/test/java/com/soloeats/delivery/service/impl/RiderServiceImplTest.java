package com.soloeats.delivery.service.impl;

import com.soloeats.common.exception.BizException;
import com.soloeats.common.exception.ErrorCode;
import com.soloeats.delivery.domain.entity.Rider;
import com.soloeats.delivery.domain.entity.RiderCertification;
import com.soloeats.delivery.mapper.RiderCertificationMapper;
import com.soloeats.delivery.mapper.RiderMapper;
import com.soloeats.delivery.mapper.RiderOnlineLogMapper;
import com.soloeats.delivery.service.RiderOnlineService;
import com.soloeats.delivery.service.command.SubmitCertificationCommand;
import com.soloeats.delivery.support.IdCardCipher;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RiderServiceImplTest {

    private static final long USER_ID = 5L;
    private static final String ID_CARD = "110101199001011234";
    private static final String ID_CARD_MASK = "110101********1234";

    @Mock
    private RiderMapper riderMapper;
    @Mock
    private RiderCertificationMapper riderCertificationMapper;
    @Mock
    private RiderOnlineLogMapper riderOnlineLogMapper;
    @Mock
    private RiderOnlineService riderOnlineService;
    @Spy
    private IdCardCipher idCardCipher = new IdCardCipher("test-id-card-aes-key-32-bytes");

    @InjectMocks
    private RiderServiceImpl riderService;

    @Test
    @DisplayName("首次提交认证：创建骑手档案并插入待审记录，身份证只落密文与掩码")
    void submitCreatesRiderAndPendingCertification() {
        when(riderMapper.selectOne(any())).thenReturn(null);
        when(riderMapper.findMaxRiderNo()).thenReturn(null);
        doAnswer(invocation -> {
            Rider rider = invocation.getArgument(0);
            rider.setId(11L);
            return 1;
        }).when(riderMapper).insert(any(Rider.class));

        Long riderId = riderService.submitCertification(USER_ID, command());

        assertThat(riderId).isEqualTo(11L);
        ArgumentCaptor<Rider> riderCaptor = ArgumentCaptor.forClass(Rider.class);
        verify(riderMapper).insert(riderCaptor.capture());
        Rider saved = riderCaptor.getValue();
        assertThat(saved.getUserId()).isEqualTo(USER_ID);
        assertThat(saved.getRiderNo()).isEqualTo("R000001");
        assertThat(saved.getCertStatus()).isEqualTo(1);
        assertThat(saved.getHealthCertExpireAt()).isEqualTo(command().healthCertExpireAt());
        assertThat(saved.getIdCardMask()).isEqualTo(ID_CARD_MASK);
        assertThat(saved.getIdCardCipher()).isNotBlank().isNotEqualTo(ID_CARD);
        assertThat(idCardCipher.decrypt(saved.getIdCardCipher())).isEqualTo(ID_CARD);

        ArgumentCaptor<RiderCertification> certCaptor =
                ArgumentCaptor.forClass(RiderCertification.class);
        verify(riderCertificationMapper).insert(certCaptor.capture());
        RiderCertification certification = certCaptor.getValue();
        assertThat(certification.getRiderId()).isEqualTo(11L);
        assertThat(certification.getStatus()).isZero();
        assertThat(certification.getIdCardCipher()).isEqualTo(saved.getIdCardCipher());
    }

    @Test
    @DisplayName("驳回后可重新提交：更新原档案、清空驳回原因并新增待审记录")
    void resubmitAfterRejected() {
        Rider existing = new Rider();
        existing.setId(9L);
        existing.setUserId(USER_ID);
        existing.setCertStatus(3);
        existing.setCertRemark("证件模糊");
        existing.setVersion(0);
        when(riderMapper.selectOne(any())).thenReturn(existing);
        when(riderMapper.updateCertSubmission(eq(9L), eq("王五"), anyString(),
                eq(ID_CARD_MASK), eq(1), eq(command().healthCertExpireAt()))).thenReturn(1);

        Long riderId = riderService.submitCertification(USER_ID, command());

        assertThat(riderId).isEqualTo(9L);
        assertThat(existing.getCertStatus()).isEqualTo(1);
        assertThat(existing.getCertRemark()).isNull();
        verify(riderMapper, never()).insert(any(Rider.class));
        ArgumentCaptor<RiderCertification> certCaptor =
                ArgumentCaptor.forClass(RiderCertification.class);
        verify(riderCertificationMapper).insert(certCaptor.capture());
        assertThat(certCaptor.getValue().getStatus()).isZero();
    }

    @Test
    @DisplayName("认证已通过时重复提交抛 PARAM_INVALID")
    void submitAfterApprovedIsRejected() {
        Rider existing = new Rider();
        existing.setId(9L);
        existing.setUserId(USER_ID);
        existing.setCertStatus(2);
        when(riderMapper.selectOne(any())).thenReturn(existing);

        assertThatThrownBy(() -> riderService.submitCertification(USER_ID, command()))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getCode())
                .isEqualTo(ErrorCode.PARAM_INVALID.getCode());
        verify(riderMapper, never()).updateCertSubmission(any(), anyString(), anyString(),
                anyString(), any(), any());
    }

    @Test
    @DisplayName("健康证到期日早于今天或为空时判定无效")
    void healthCertExpiryJudgement() {
        LocalDate today = LocalDate.now();

        assertThat(riderService.isHealthCertValid(today, today)).isTrue();
        assertThat(riderService.isHealthCertValid(today.plusDays(1), today)).isTrue();
        assertThat(riderService.isHealthCertValid(today.minusDays(1), today)).isFalse();
        assertThat(riderService.isHealthCertValid(null, today)).isFalse();
    }

    @Test
    @DisplayName("健康证已过期时提交认证直接拒绝")
    void submitWithExpiredHealthCertIsRejected() {
        SubmitCertificationCommand expired = new SubmitCertificationCommand(
                "王五", ID_CARD, null, null, null,
                LocalDate.now().minusDays(1), 1);

        assertThatThrownBy(() -> riderService.submitCertification(USER_ID, expired))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getCode())
                .isEqualTo(ErrorCode.PARAM_INVALID.getCode());
    }

    private static SubmitCertificationCommand command() {
        return new SubmitCertificationCommand(
                "王五", ID_CARD, 101L, 102L, 103L,
                LocalDate.now().plusYears(1), 1);
    }
}
