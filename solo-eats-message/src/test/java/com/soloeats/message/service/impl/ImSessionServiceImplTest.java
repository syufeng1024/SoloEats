package com.soloeats.message.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.soloeats.common.exception.BizException;
import com.soloeats.common.exception.ErrorCode;
import com.soloeats.message.domain.entity.ImMessage;
import com.soloeats.message.domain.entity.ImSession;
import com.soloeats.message.mapper.ImMessageMapper;
import com.soloeats.message.mapper.ImSessionMapper;
import com.soloeats.message.service.PushService;
import com.soloeats.message.service.command.PushPayload;
import com.soloeats.message.service.view.ImMessageView;
import com.soloeats.message.service.view.ImSessionView;
import org.assertj.core.api.ThrowableAssert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ImSessionServiceImplTest {

    private static final int USER = 1;
    private static final int MERCHANT = 2;
    private static final long USER_ID = 7L;
    private static final long MERCHANT_ID = 21L;
    private static final long OTHER_ID = 999L;
    private static final long SESSION_ID = 101L;
    private static final long ORDER_ID = 88L;
    private static final long MESSAGE_ID = 9001L;

    @Mock
    private ImSessionMapper imSessionMapper;

    @Mock
    private ImMessageMapper imMessageMapper;

    @Mock
    private PushService pushService;

    private ImSessionServiceImpl imSessionService;

    @BeforeEach
    void setUp() {
        imSessionService = new ImSessionServiceImpl(
                imSessionMapper, imMessageMapper, pushService, new ObjectMapper());
    }

    @Test
    @DisplayName("同一 order+peer 重复创建复用原会话，并恢复本人可见")
    void getOrCreateReusesExistingSession() {
        ImSession existing = session(USER_ID, MERCHANT, MERCHANT_ID, 0);
        existing.setUserHidden(1);
        existing.setUserUnread(3);
        when(imSessionMapper.findByUnique(ORDER_ID, USER_ID, MERCHANT, MERCHANT_ID))
                .thenReturn(existing);

        ImSessionView view = imSessionService.getOrCreate(
                ORDER_ID, USER, USER_ID, MERCHANT, MERCHANT_ID);

        assertThat(view.id()).isEqualTo(SESSION_ID);
        assertThat(view.unread()).isEqualTo(3);
        verify(imSessionMapper, never()).insert(ArgumentMatchers.<ImSession>any());
        verify(imSessionMapper).clearUserHidden(SESSION_ID);
    }

    @Test
    @DisplayName("商家发起会话时消费者 ID 落 user_id，商家自身落 peer 侧")
    void merchantCreateStoresConsumerAsUserId() {
        when(imSessionMapper.findByUnique(ORDER_ID, USER_ID, MERCHANT, MERCHANT_ID)).thenReturn(null);
        when(imSessionMapper.insert(ArgumentMatchers.<ImSession>any())).thenAnswer(invocation -> {
            ImSession inserted = invocation.getArgument(0);
            inserted.setId(SESSION_ID);
            return 1;
        });

        ImSessionView view = imSessionService.getOrCreate(
                ORDER_ID, MERCHANT, MERCHANT_ID, USER, USER_ID);

        assertThat(view.id()).isEqualTo(SESSION_ID);
        verify(imSessionMapper).insert(ArgumentMatchers.<ImSession>argThat(inserted ->
                inserted.getUserId() == USER_ID
                        && inserted.getPeerType() == MERCHANT
                        && inserted.getPeerId() == MERCHANT_ID));
    }

    @Test
    @DisplayName("非参与者读消息抛 90003，且不查消息表")
    void nonParticipantCannotReadMessages() {
        when(imSessionMapper.selectById(SESSION_ID))
                .thenReturn(session(USER_ID, MERCHANT, MERCHANT_ID, 0));

        assertBizCode(() -> imSessionService.messages(SESSION_ID, USER, OTHER_ID, null, 20),
                ErrorCode.IM_NOT_PARTICIPANT);
        verify(imMessageMapper, never()).selectPage(anyLong(), any(), anyInt());
    }

    @Test
    @DisplayName("只读会话发送抛 90002，且不落消息")
    void sendToReadonlySessionRejected() {
        when(imSessionMapper.selectById(SESSION_ID))
                .thenReturn(session(USER_ID, MERCHANT, MERCHANT_ID, 2));

        assertBizCode(() -> imSessionService.send(SESSION_ID, USER, USER_ID, 1, "你好", null),
                ErrorCode.IM_SESSION_CLOSED);
        verify(imMessageMapper, never()).insert(ArgumentMatchers.<ImMessage>any());
    }

    @Test
    @DisplayName("用户发送后对方未读 +1、WS 载荷 notifyType=4；已读清零用户侧未读")
    void sendIncrementsPeerUnreadAndMarkReadClearsUserUnread() {
        when(imSessionMapper.selectById(SESSION_ID))
                .thenReturn(session(USER_ID, MERCHANT, MERCHANT_ID, 0));
        when(imMessageMapper.insert(ArgumentMatchers.<ImMessage>any())).thenAnswer(invocation -> {
            ImMessage inserted = invocation.getArgument(0);
            inserted.setId(MESSAGE_ID);
            return 1;
        });
        when(imSessionMapper.updateLastMessage(eq(SESSION_ID), anyString(), any(LocalDateTime.class)))
                .thenReturn(1);
        when(imSessionMapper.incPeerUnread(SESSION_ID)).thenReturn(1);
        when(imSessionMapper.clearUserUnread(SESSION_ID)).thenReturn(1);

        ImMessageView view = imSessionService.send(SESSION_ID, USER, USER_ID, 1, "你好", null);

        assertThat(view.id()).isEqualTo(MESSAGE_ID);
        assertThat(view.content()).isEqualTo("你好");
        verify(imSessionMapper).incPeerUnread(SESSION_ID);
        verify(imSessionMapper, never()).incUserUnread(anyLong());
        verify(pushService).push(eq(MERCHANT), eq(MERCHANT_ID), argThat(payload ->
                payload.notifyType() == 4
                        && "IM_MESSAGE".equals(payload.type())
                        && payload.messageId().equals(MESSAGE_ID)));

        imSessionService.markRead(SESSION_ID, USER, USER_ID);
        verify(imSessionMapper).clearUserUnread(SESSION_ID);
    }

    @Test
    @DisplayName("删除会话只置本方隐藏标记，不物理删除；隐藏后列表不再返回")
    void hideOnlyMarksOwnSide() {
        when(imSessionMapper.selectById(SESSION_ID))
                .thenReturn(session(USER_ID, MERCHANT, MERCHANT_ID, 0));
        when(imSessionMapper.hideUserSide(SESSION_ID)).thenReturn(1);
        when(imSessionMapper.selectMine(USER, USER_ID)).thenReturn(List.of());

        imSessionService.hide(SESSION_ID, USER, USER_ID);

        verify(imSessionMapper).hideUserSide(SESSION_ID);
        verify(imSessionMapper, never()).hidePeerSide(anyLong());
        verify(imSessionMapper, never()).deleteById(anyLong());
        assertThat(imSessionService.listMine(USER, USER_ID)).isEmpty();
    }

    @Test
    @DisplayName("超过 2000 字符或非法 JSON extra 抛 10001")
    void invalidContentOrExtraRejected() {
        when(imSessionMapper.selectById(SESSION_ID))
                .thenReturn(session(USER_ID, MERCHANT, MERCHANT_ID, 0));
        String tooLong = "x".repeat(2001);

        assertBizCode(() -> imSessionService.send(SESSION_ID, USER, USER_ID, 1, tooLong, null),
                ErrorCode.PARAM_INVALID);
        assertBizCode(() -> imSessionService.send(SESSION_ID, USER, USER_ID, 1, "hi", "{not-json"),
                ErrorCode.PARAM_INVALID);
        verify(imMessageMapper, never()).insert(ArgumentMatchers.<ImMessage>any());
    }

    // ------------------------------------------------------------
    // T09 阶段B 客服工作台坐席端
    // ------------------------------------------------------------

    @Test
    @DisplayName("接待并发：条件更新只成功一次，第二次抛 90005")
    void takeSessionOnlyOnce() {
        long agentId = 66L;
        when(imSessionMapper.takeSession(SESSION_ID, agentId)).thenReturn(1, 0);
        when(imSessionMapper.selectById(SESSION_ID)).thenReturn(csSession(0, agentId));

        imSessionService.takeSession(SESSION_ID, agentId);

        assertBizCode(() -> imSessionService.takeSession(SESSION_ID, agentId),
                ErrorCode.CS_SESSION_ALREADY_TAKEN);
        verify(imSessionMapper, times(2)).takeSession(SESSION_ID, agentId);
    }

    @Test
    @DisplayName("已结束会话接待抛 90002")
    void takeEndedSessionRejected() {
        long agentId = 66L;
        when(imSessionMapper.takeSession(SESSION_ID, agentId)).thenReturn(0);
        when(imSessionMapper.selectById(SESSION_ID)).thenReturn(csSession(1, null));

        assertBizCode(() -> imSessionService.takeSession(SESSION_ID, agentId),
                ErrorCode.IM_SESSION_CLOSED);
    }

    @Test
    @DisplayName("不存在的会话接待抛 90001")
    void takeMissingSessionRejected() {
        long agentId = 66L;
        when(imSessionMapper.takeSession(SESSION_ID, agentId)).thenReturn(0);
        when(imSessionMapper.selectById(SESSION_ID)).thenReturn(null);

        assertBizCode(() -> imSessionService.takeSession(SESSION_ID, agentId),
                ErrorCode.IM_SESSION_NOT_FOUND);
    }

    @Test
    @DisplayName("非客服会话（peer_type=2）接待抛 90001")
    void takeMerchantSessionRejected() {
        long agentId = 66L;
        when(imSessionMapper.takeSession(SESSION_ID, agentId)).thenReturn(0);
        when(imSessionMapper.selectById(SESSION_ID))
                .thenReturn(session(USER_ID, MERCHANT, MERCHANT_ID, 0));

        assertBizCode(() -> imSessionService.takeSession(SESSION_ID, agentId),
                ErrorCode.IM_SESSION_NOT_FOUND);
    }

    @Test
    @DisplayName("结束会话二次调用抛 90002")
    void closeSessionTwiceRejected() {
        long operatorId = 66L;
        when(imSessionMapper.closeSession(SESSION_ID)).thenReturn(1, 0);
        when(imSessionMapper.selectById(SESSION_ID)).thenReturn(csSession(1, operatorId));

        imSessionService.closeSessionByAgent(SESSION_ID, operatorId);

        assertBizCode(() -> imSessionService.closeSessionByAgent(SESSION_ID, operatorId),
                ErrorCode.IM_SESSION_CLOSED);
        verify(imSessionMapper, times(2)).closeSession(SESSION_ID);
    }

    @Test
    @DisplayName("待接待会话（agent_id 为空）任意坐席可读消息")
    void agentMessagesAllowedForWaitingSession() {
        long agentId = 66L;
        when(imSessionMapper.selectById(SESSION_ID)).thenReturn(csSession(0, null));
        when(imMessageMapper.selectPage(SESSION_ID, null, 20)).thenReturn(List.of(message()));

        List<ImMessageView> views = imSessionService.agentMessages(SESSION_ID, agentId, null, null);

        assertThat(views).hasSize(1);
        assertThat(views.get(0).content()).isEqualTo("你好");
    }

    @Test
    @DisplayName("已被 A 坐席接待的会话，B 坐席读消息抛 90003")
    void agentMessagesRejectedForOtherAgent() {
        long agentA = 66L;
        long agentB = 77L;
        when(imSessionMapper.selectById(SESSION_ID)).thenReturn(csSession(0, agentA));

        assertBizCode(() -> imSessionService.agentMessages(SESSION_ID, agentB, null, 20),
                ErrorCode.IM_NOT_PARTICIPANT);
        verify(imMessageMapper, never()).selectPage(anyLong(), any(), anyInt());
    }

    @Test
    @DisplayName("接待坐席本人可读消息")
    void agentMessagesAllowedForOwnSession() {
        long agentA = 66L;
        when(imSessionMapper.selectById(SESSION_ID)).thenReturn(csSession(0, agentA));
        when(imMessageMapper.selectPage(SESSION_ID, null, 20)).thenReturn(List.of(message()));

        List<ImMessageView> views = imSessionService.agentMessages(SESSION_ID, agentA, null, null);

        assertThat(views).hasSize(1);
        verify(imMessageMapper).selectPage(SESSION_ID, null, 20);
    }

    private ImMessage message() {
        ImMessage message = new ImMessage();
        message.setId(MESSAGE_ID);
        message.setSessionId(SESSION_ID);
        message.setSenderType(USER);
        message.setSenderId(USER_ID);
        message.setMsgType(1);
        message.setContent("你好");
        message.setReadFlag(0);
        return message;
    }

    private ImSession csSession(int status, Long agentId) {
        ImSession session = session(USER_ID, 4, 66L, status);
        session.setAgentId(agentId);
        return session;
    }

    private ImSession session(long userId, int peerType, long peerId, int status) {
        ImSession session = new ImSession();
        session.setId(SESSION_ID);
        session.setSessionNo("IM17000000000001234");
        session.setUserId(userId);
        session.setPeerType(peerType);
        session.setPeerId(peerId);
        session.setUserUnread(0);
        session.setPeerUnread(0);
        session.setUserHidden(0);
        session.setPeerHidden(0);
        session.setStatus(status);
        return session;
    }

    private void assertBizCode(ThrowableAssert.ThrowingCallable call, ErrorCode expected) {
        assertThatThrownBy(call)
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getCode())
                .isEqualTo(expected.getCode());
    }
}
