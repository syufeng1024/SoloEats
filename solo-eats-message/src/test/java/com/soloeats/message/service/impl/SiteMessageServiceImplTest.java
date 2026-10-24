package com.soloeats.message.service.impl;

import com.soloeats.common.exception.BizException;
import com.soloeats.common.exception.ErrorCode;
import com.soloeats.message.domain.entity.SiteMessage;
import com.soloeats.message.mapper.SiteMessageMapper;
import com.soloeats.message.mapper.UnreadCountRow;
import com.soloeats.message.service.MessageTemplateService;
import com.soloeats.message.service.PushService;
import com.soloeats.message.service.view.UnreadCountView;
import org.assertj.core.api.ThrowableAssert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SiteMessageServiceImplTest {

    private static final int RECEIVER_TYPE_USER = 1;
    private static final long USER_ID = 7L;
    private static final long OTHER_USER_ID = 8L;
    private static final long MESSAGE_ID = 1001L;

    @Mock
    private SiteMessageMapper siteMessageMapper;

    @Mock
    private MessageTemplateService messageTemplateService;

    @Mock
    private PushService pushService;

    private SiteMessageServiceImpl siteMessageService;

    @BeforeEach
    void setUp() {
        siteMessageService = new SiteMessageServiceImpl(
                siteMessageMapper, messageTemplateService, pushService);
    }

    @Test
    @DisplayName("未读数按 msg_type 聚合，total 为三类之和")
    void unreadCountAggregatesByType() {
        when(siteMessageMapper.countUnreadByType(RECEIVER_TYPE_USER, USER_ID))
                .thenReturn(List.of(row(1, 3L), row(2, 1L)));

        UnreadCountView view = siteMessageService.unreadCount(RECEIVER_TYPE_USER, USER_ID);

        assertThat(view.total()).isEqualTo(4L);
        assertThat(view.byType().order()).isEqualTo(3L);
        assertThat(view.byType().marketing()).isEqualTo(1L);
        assertThat(view.byType().system()).isZero();
    }

    @Test
    @DisplayName("标记他人站内信已读抛 10004，且不落更新")
    void markReadOtherUsersMessageRejected() {
        when(siteMessageMapper.selectById(MESSAGE_ID))
                .thenReturn(message(OTHER_USER_ID, 0));

        assertBizCode(() -> siteMessageService.markRead(MESSAGE_ID, RECEIVER_TYPE_USER, USER_ID),
                ErrorCode.AUTH_FORBIDDEN);
        verify(siteMessageMapper, never()).markRead(anyLong(), anyInt(), anyLong(), any());
    }

    @Test
    @DisplayName("标记本人未读消息已读，走条件更新")
    void markReadOwnMessage() {
        when(siteMessageMapper.selectById(MESSAGE_ID)).thenReturn(message(USER_ID, 0));
        when(siteMessageMapper.markRead(eq(MESSAGE_ID), eq(RECEIVER_TYPE_USER), eq(USER_ID), any()))
                .thenReturn(1);

        siteMessageService.markRead(MESSAGE_ID, RECEIVER_TYPE_USER, USER_ID);

        verify(siteMessageMapper).markRead(eq(MESSAGE_ID), eq(RECEIVER_TYPE_USER), eq(USER_ID), any());
    }

    @Test
    @DisplayName("已读消息重复标记幂等返回，不再更新")
    void markReadAlreadyReadIsIdempotent() {
        when(siteMessageMapper.selectById(MESSAGE_ID)).thenReturn(message(USER_ID, 1));

        siteMessageService.markRead(MESSAGE_ID, RECEIVER_TYPE_USER, USER_ID);

        verify(siteMessageMapper, never()).markRead(anyLong(), anyInt(), anyLong(), any());
    }

    private SiteMessage message(long receiverId, int readFlag) {
        SiteMessage message = new SiteMessage();
        message.setId(MESSAGE_ID);
        message.setReceiverType(RECEIVER_TYPE_USER);
        message.setReceiverId(receiverId);
        message.setReadFlag(readFlag);
        return message;
    }

    private UnreadCountRow row(int msgType, long cnt) {
        UnreadCountRow row = new UnreadCountRow();
        row.setMsgType(msgType);
        row.setCnt(cnt);
        return row;
    }

    private void assertBizCode(ThrowableAssert.ThrowingCallable call, ErrorCode expected) {
        assertThatThrownBy(call)
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getCode())
                .isEqualTo(expected.getCode());
    }
}
