package com.soloeats.message.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.soloeats.common.api.PageResult;
import com.soloeats.common.exception.BizException;
import com.soloeats.common.exception.ErrorCode;
import com.soloeats.message.domain.entity.ImMessage;
import com.soloeats.message.domain.entity.ImSession;
import com.soloeats.message.mapper.ImMessageMapper;
import com.soloeats.message.mapper.ImSessionMapper;
import com.soloeats.message.service.ImSessionService;
import com.soloeats.message.service.PushService;
import com.soloeats.message.service.command.PushPayload;
import com.soloeats.message.service.view.AgentSessionView;
import com.soloeats.message.service.view.ImMessageView;
import com.soloeats.message.service.view.ImSessionView;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 三端共用 IM 会话。
 *
 * <p>存储语义：{@code t_im_session.user_id} 恒为消费者侧，{@code peer_type/peer_id} 恒为被咨询方
 * （2商家 3骑手 4客服）。当登录主体是商家/骑手/客服时，调用方传入的 peerId 是消费者 ID，
 * 服务层把两者对调后再落库，因此“当前主体”在库里可能出现在 peer 侧。
 *
 * <p>未读数只用 user_unread/peer_unread 计数列自增与清零，不做 COUNT(*)。
 * WS 推送在事务提交后执行：事务内推送会让对方先看到消息、再因回滚看到消息消失。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ImSessionServiceImpl implements ImSessionService {

    private static final int SUBJECT_USER = 1;
    private static final Set<Integer> PEER_TYPES = Set.of(2, 3, 4);
    private static final Set<Integer> MSG_TYPES = Set.of(1, 2, 3, 4, 5);

    private static final int MAX_CONTENT_LENGTH = 2000;
    private static final int MAX_LAST_MESSAGE_LENGTH = 200;
    private static final int MAX_EXTRA_LENGTH = 4000;
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 50;

    /** 会话状态：0进行中 1已结束 2只读。 */
    private static final int SESSION_ACTIVE = 0;
    private static final int SESSION_READONLY = 2;
    private static final int HIDDEN = 1;

    /** t_im_session.peer_type 中「平台客服」的取值。 */
    private static final int PEER_TYPE_CS = 4;
    private static final long AGENT_DEFAULT_PAGE_SIZE = 20L;
    private static final long AGENT_MAX_PAGE_SIZE = 100L;

    /** t_notify_setting.notify_type：1订单 2营销 3系统 4会话。 */
    private static final int NOTIFY_TYPE_IM = 4;
    private static final String PUSH_TYPE_IM = "IM_MESSAGE";
    private static final String PUSH_TITLE = "新消息";

    private static final int MAX_SESSION_NO_RANDOM = 10_000;

    private final ImSessionMapper imSessionMapper;
    private final ImMessageMapper imMessageMapper;
    private final PushService pushService;
    private final ObjectMapper objectMapper;

    @Override
    public ImSessionView getOrCreate(Long orderId, Integer subjectType, Long subjectId,
                                     Integer peerType, Long peerId) {
        requireSubject(subjectType, subjectId);
        if (peerId == null || peerId <= 0L) {
            throw new BizException(ErrorCode.PARAM_INVALID, "对方ID不能为空");
        }
        if (subjectType == SUBJECT_USER) {
            if (peerType == null || !PEER_TYPES.contains(peerType)) {
                throw new BizException(ErrorCode.PARAM_INVALID, "对方类型只能是 2商家 3骑手 4客服");
            }
        } else if (!Objects.equals(peerType, SUBJECT_USER)) {
            // 商家/骑手/客服只能与消费者建会话；库里 peer_type 只存 2/3/4，
            // 因此当前主体就是 peer 侧，前端传的对端必须是用户（1）
            throw new BizException(ErrorCode.PARAM_INVALID, "商家/骑手/客服只能与用户建立会话，peerType 必须为 1");
        }
        if (Objects.equals(subjectType, peerType) && Objects.equals(subjectId, peerId)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "不能和自己建立会话");
        }

        boolean userSide = subjectType == SUBJECT_USER;
        long userId = userSide ? subjectId : peerId;
        int storedPeerType = userSide ? peerType : subjectType;
        long storedPeerId = userSide ? peerId : subjectId;

        ImSession existing = imSessionMapper.findByUnique(orderId, userId, storedPeerType, storedPeerId);
        if (existing != null) {
            restoreVisible(existing, userSide);
            return ImSessionView.from(existing, userSide);
        }

        ImSession created = new ImSession();
        created.setSessionNo(nextSessionNo());
        created.setOrderId(orderId);
        created.setUserId(userId);
        created.setPeerType(storedPeerType);
        created.setPeerId(storedPeerId);
        created.setUserUnread(0);
        created.setPeerUnread(0);
        created.setStatus(SESSION_ACTIVE);
        // 显式写入而非依赖列默认值：创建接口要回显 createdAt，MyBatis-Plus 不会回读默认值
        created.setCreatedAt(LocalDateTime.now());
        try {
            imSessionMapper.insert(created);
        } catch (DuplicateKeyException ex) {
            // 两个端同时首建：uk_order_peer 兜底，冲突后重查视为成功
            log.info("im session concurrent insert, requery orderId={} user={} peer={}:{}",
                    orderId, userId, storedPeerType, storedPeerId);
            ImSession concurrent = imSessionMapper.findByUnique(
                    orderId, userId, storedPeerType, storedPeerId);
            if (concurrent == null) {
                log.error("im session duplicate key but requery empty orderId={} user={} peer={}:{}",
                        orderId, userId, storedPeerType, storedPeerId);
                throw new BizException(ErrorCode.SYSTEM_ERROR, "会话创建冲突，请重试");
            }
            restoreVisible(concurrent, userSide);
            return ImSessionView.from(concurrent, userSide);
        }
        log.info("im session created id={} orderId={} user={} peer={}:{}",
                created.getId(), orderId, userId, storedPeerType, storedPeerId);
        return ImSessionView.from(created, userSide);
    }

    @Override
    public List<ImSessionView> listMine(Integer subjectType, Long subjectId) {
        requireSubject(subjectType, subjectId);
        boolean userSide = subjectType == SUBJECT_USER;
        List<ImSession> sessions = imSessionMapper.selectMine(subjectType, subjectId);
        if (sessions == null || sessions.isEmpty()) {
            return List.of();
        }
        return sessions.stream()
                .filter(Objects::nonNull)
                .map(session -> ImSessionView.from(session, userSide))
                .toList();
    }

    @Override
    public List<ImMessageView> messages(Long sessionId, Integer subjectType, Long subjectId,
                                        Long beforeMessageId, Integer pageSize) {
        ImSession session = requireParticipantSession(sessionId, subjectType, subjectId);
        return loadMessages(session.getId(), beforeMessageId, pageSize);
    }

    @Override
    public List<ImMessageView> agentMessages(Long sessionId, Long agentId,
                                             Long beforeMessageId, Integer pageSize) {
        requireSessionId(sessionId);
        requireAgentId(agentId);
        ImSession session = imSessionMapper.selectById(sessionId);
        if (session == null || !Objects.equals(session.getPeerType(), PEER_TYPE_CS)) {
            throw new BizException(ErrorCode.IM_SESSION_NOT_FOUND);
        }
        // agent_id 为空 = 待接待池，坐席需要先看内容再决定是否接待
        if (session.getAgentId() != null && !session.getAgentId().equals(agentId)) {
            throw new BizException(ErrorCode.IM_NOT_PARTICIPANT);
        }
        return loadMessages(session.getId(), beforeMessageId, pageSize);
    }

    @Transactional
    @Override
    public ImMessageView send(Long sessionId, Integer subjectType, Long subjectId,
                              Integer msgType, String content, String extraJson) {
        ImSession session = requireParticipantSession(sessionId, subjectType, subjectId);
        if (!Objects.equals(session.getStatus(), SESSION_ACTIVE)) {
            throw new BizException(ErrorCode.IM_SESSION_CLOSED);
        }
        if (msgType == null || !MSG_TYPES.contains(msgType)) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "消息类型只能是 1文字 2图片 3订单卡片 4位置 5常用语");
        }
        String text = content == null ? "" : content.strip();
        if (text.length() > MAX_CONTENT_LENGTH) {
            throw new BizException(ErrorCode.PARAM_INVALID, "消息内容最长 2000 个字符");
        }
        JsonNode extraNode = normalizeExtra(extraJson);
        if ((msgType == 1 || msgType == 5) && text.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "文字与常用语消息内容不能为空");
        }
        if (msgType == 2 && (extraNode == null || extraNode.path("fileId").asText("").isBlank())) {
            // 图片只接受 t_file 的 fileId，不接受外链，避免前端把任意外链当图片加载
            throw new BizException(ErrorCode.PARAM_INVALID, "图片消息必须在 extra 中提供 fileId");
        }
        String extra = extraNode == null ? null : extraNode.toString();

        LocalDateTime now = LocalDateTime.now();
        ImMessage message = new ImMessage();
        message.setSessionId(session.getId());
        message.setSenderType(subjectType);
        message.setSenderId(subjectId);
        message.setMsgType(msgType);
        message.setContent(text);
        message.setExtraJson(extra);
        message.setReadFlag(0);
        // 发送接口直接回显该消息，时间不能等数据库默认值回读
        message.setCreatedAt(now);
        imMessageMapper.insert(message);

        String summary = summarize(msgType, text);
        imSessionMapper.updateLastMessage(session.getId(), summary, now);

        boolean userSide = subjectType == SUBJECT_USER;
        if (userSide) {
            imSessionMapper.incPeerUnread(session.getId());
        } else {
            imSessionMapper.incUserUnread(session.getId());
        }

        Integer receiverType = userSide ? session.getPeerType() : SUBJECT_USER;
        Long receiverId = userSide ? session.getPeerId() : session.getUserId();
        ImMessageView view = ImMessageView.from(message, parseExtra(extra));
        pushAfterCommit(session.getId(), receiverType, receiverId, new PushPayload(
                NOTIFY_TYPE_IM,
                PUSH_TYPE_IM,
                message.getId(),
                null,
                null,
                null,
                PUSH_TITLE,
                summary));
        return view;
    }

    @Override
    public void markRead(Long sessionId, Integer subjectType, Long subjectId) {
        ImSession session = requireParticipantSession(sessionId, subjectType, subjectId);
        if (subjectType == SUBJECT_USER) {
            imSessionMapper.clearUserUnread(session.getId());
        } else {
            imSessionMapper.clearPeerUnread(session.getId());
        }
    }

    @Override
    public void hide(Long sessionId, Integer subjectType, Long subjectId) {
        ImSession session = requireParticipantSession(sessionId, subjectType, subjectId);
        if (subjectType == SUBJECT_USER) {
            imSessionMapper.hideUserSide(session.getId());
        } else {
            imSessionMapper.hidePeerSide(session.getId());
        }
        log.info("im session hidden id={} by={}:{}", session.getId(), subjectType, subjectId);
    }

    // ------------------------------------------------------------
    // T09 阶段B 客服工作台坐席端
    // ------------------------------------------------------------

    @Override
    public PageResult<AgentSessionView> listAgentSessions(Integer status, Long agentId,
                                                          long pageNum, long pageSize) {
        if (status != null && (status < SESSION_ACTIVE || status > SESSION_READONLY)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "status 只能是 0进行中 1已结束 2只读");
        }
        if (agentId != null && agentId <= 0L) {
            throw new BizException(ErrorCode.PARAM_INVALID, "agentId 必须为正整数");
        }
        long normalizedPageNum = pageNum < 1 ? 1 : pageNum;
        long normalizedPageSize = pageSize < 1
                ? AGENT_DEFAULT_PAGE_SIZE
                : Math.min(pageSize, AGENT_MAX_PAGE_SIZE);

        LambdaQueryWrapper<ImSession> wrapper = new LambdaQueryWrapper<ImSession>()
                .eq(ImSession::getPeerType, PEER_TYPE_CS)
                .eq(status != null, ImSession::getStatus, status)
                .eq(agentId != null, ImSession::getAgentId, agentId)
                .orderByAsc(ImSession::getStatus)
                .orderByDesc(ImSession::getLastMessageAt)
                .orderByDesc(ImSession::getId);

        Page<ImSession> page = imSessionMapper.selectPage(
                new Page<>(normalizedPageNum, normalizedPageSize), wrapper);
        List<ImSession> records = page.getRecords() == null ? List.of() : page.getRecords();
        List<AgentSessionView> views = records.stream()
                .filter(Objects::nonNull)
                .map(AgentSessionView::from)
                .toList();
        return PageResult.of(views, page.getTotal(), normalizedPageNum, normalizedPageSize);
    }

    @Override
    public void takeSession(Long sessionId, Long agentId) {
        requireSessionId(sessionId);
        requireAgentId(agentId);
        // 单条条件更新即完成「抢占」：WHERE 带 agent_id IS NULL，并发下只有一行能命中
        if (imSessionMapper.takeSession(sessionId, agentId) > 0) {
            log.info("cs session taken id={} agent={}", sessionId, agentId);
            return;
        }
        ImSession session = requireCsSession(sessionId);
        if (!Objects.equals(session.getStatus(), SESSION_ACTIVE)) {
            throw new BizException(ErrorCode.IM_SESSION_CLOSED);
        }
        // status=0 且 peer_type=4 仍未命中，只可能是 agent_id 已有值
        throw new BizException(ErrorCode.CS_SESSION_ALREADY_TAKEN);
    }

    @Override
    public void transferSession(Long sessionId, Long toAgentId, Long operatorId) {
        requireSessionId(sessionId);
        requireAgentId(toAgentId);
        if (imSessionMapper.transferAgent(sessionId, toAgentId) > 0) {
            log.info("cs session transferred id={} toAgent={} operator={}",
                    sessionId, toAgentId, operatorId);
            return;
        }
        requireCsSession(sessionId);
        throw new BizException(ErrorCode.IM_SESSION_CLOSED);
    }

    @Override
    public void closeSessionByAgent(Long sessionId, Long operatorId) {
        requireSessionId(sessionId);
        if (imSessionMapper.closeSession(sessionId) > 0) {
            log.info("cs session closed id={} operator={}", sessionId, operatorId);
            return;
        }
        requireCsSession(sessionId);
        throw new BizException(ErrorCode.IM_SESSION_CLOSED);
    }

    @Override
    public long countServedSessions(Long agentId, LocalDate dateFrom, LocalDate dateTo) {
        if (agentId != null && agentId <= 0L) {
            throw new BizException(ErrorCode.PARAM_INVALID, "agentId 必须为正整数");
        }
        if (dateFrom != null && dateTo != null && dateFrom.isAfter(dateTo)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "dateFrom 不能晚于 dateTo");
        }
        LocalDateTime start = dateFrom == null ? null : dateFrom.atStartOfDay();
        LocalDateTime end = dateTo == null ? null : dateTo.plusDays(1).atStartOfDay();
        return imSessionMapper.countServedSessions(agentId, start, end);
    }

    @Override
    public long countActiveSessions(Long agentId) {
        if (agentId != null && agentId <= 0L) {
            throw new BizException(ErrorCode.PARAM_INVALID, "agentId 必须为正整数");
        }
        return imSessionMapper.countActiveSessions(agentId);
    }

    @Override
    public long countWaitingSessions() {
        return imSessionMapper.countWaitingSessions();
    }

    /**
     * 消息分页的唯一实现：C 端与坐席端共用。调用方必须已完成会话存在性与参与者校验。
     */
    private List<ImMessageView> loadMessages(Long sessionId, Long beforeMessageId, Integer pageSize) {
        if (beforeMessageId != null && beforeMessageId <= 0L) {
            throw new BizException(ErrorCode.PARAM_INVALID, "beforeMessageId 必须为正整数");
        }
        int limit = normalizePageSize(pageSize);
        List<ImMessage> rows = imMessageMapper.selectPage(sessionId, beforeMessageId, limit);
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        // 查询按 id DESC，接口返回时间正序，前端直接 append
        List<ImMessage> ascending = new ArrayList<>(rows);
        Collections.reverse(ascending);
        return ascending.stream()
                .filter(Objects::nonNull)
                .map(message -> ImMessageView.from(message, parseExtra(message.getExtraJson())))
                .toList();
    }

    private void requireSessionId(Long sessionId) {
        if (sessionId == null || sessionId <= 0L) {
            throw new BizException(ErrorCode.PARAM_INVALID, "会话ID不能为空");
        }
    }

    private void requireAgentId(Long agentId) {
        if (agentId == null || agentId <= 0L) {
            throw new BizException(ErrorCode.PARAM_INVALID, "坐席ID不能为空");
        }
    }

    /** 条件更新未命中时的区分：不存在或不是客服会话统一 90001，其余由调用方判状态。 */
    private ImSession requireCsSession(Long sessionId) {
        ImSession session = imSessionMapper.selectById(sessionId);
        if (session == null || !Objects.equals(session.getPeerType(), PEER_TYPE_CS)) {
            throw new BizException(ErrorCode.IM_SESSION_NOT_FOUND);
        }
        return session;
    }

    private ImSession requireParticipantSession(Long sessionId, Integer subjectType, Long subjectId) {
        requireSubject(subjectType, subjectId);
        if (sessionId == null || sessionId <= 0L) {
            throw new BizException(ErrorCode.PARAM_INVALID, "会话ID不能为空");
        }
        ImSession session = imSessionMapper.selectById(sessionId);
        if (session == null) {
            throw new BizException(ErrorCode.IM_SESSION_NOT_FOUND);
        }
        // 第三个分支只对客服会话生效：坐席经 agent_id 关联会话（take/transfer 后改写），
        // peer_id 是建会话时的客服占位值，与坐席 ID 无关，不能作为坐席参与者的判据。
        // agent_id 为 NULL 的待接待会话仍不成立，坐席必须先接待才能发言。
        boolean participant = (subjectType == SUBJECT_USER
                && Objects.equals(session.getUserId(), subjectId))
                || (Objects.equals(session.getPeerType(), subjectType)
                && Objects.equals(session.getPeerId(), subjectId))
                || (Objects.equals(session.getPeerType(), PEER_TYPE_CS)
                && session.getAgentId() != null
                && Objects.equals(session.getAgentId(), subjectId));
        if (!participant) {
            throw new BizException(ErrorCode.IM_NOT_PARTICIPANT);
        }
        return session;
    }

    private void requireSubject(Integer subjectType, Long subjectId) {
        if (subjectType == null || subjectType < 1 || subjectType > 4) {
            throw new BizException(ErrorCode.PARAM_INVALID, "主体类型只能是 1用户 2商家 3骑手 4平台");
        }
        if (subjectId == null || subjectId <= 0L) {
            throw new BizException(ErrorCode.PARAM_INVALID, "主体ID不能为空");
        }
    }

    private void restoreVisible(ImSession session, boolean userSide) {
        if (userSide) {
            if (Objects.equals(session.getUserHidden(), HIDDEN)) {
                imSessionMapper.clearUserHidden(session.getId());
                session.setUserHidden(0);
            }
            return;
        }
        if (Objects.equals(session.getPeerHidden(), HIDDEN)) {
            imSessionMapper.clearPeerHidden(session.getId());
            session.setPeerHidden(0);
        }
    }

    private int normalizePageSize(Integer pageSize) {
        if (pageSize == null || pageSize < 1) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(pageSize, MAX_PAGE_SIZE);
    }

    /**
     * extra 必须是合法 JSON 对象才落库：MySQL json 列遇到非法文本会直接报错，
     * 在服务层转成 10001 更可控。超长同样拒绝，避免单条消息把行撑爆。
     */
    private JsonNode normalizeExtra(String extraJson) {
        if (!StringUtils.hasText(extraJson)) {
            return null;
        }
        String trimmed = extraJson.strip();
        if (trimmed.length() > MAX_EXTRA_LENGTH) {
            throw new BizException(ErrorCode.PARAM_INVALID, "extra 最长 4000 个字符");
        }
        try {
            return objectMapper.readTree(trimmed);
        } catch (Exception ex) {
            throw new BizException(ErrorCode.PARAM_INVALID, "extra 必须是合法 JSON");
        }
    }

    /** 历史数据可能被人工改坏，解析失败只丢 extra，不让整页消息接口失败。 */
    private Object parseExtra(String extraJson) {
        if (!StringUtils.hasText(extraJson)) {
            return null;
        }
        try {
            return objectMapper.readValue(extraJson, Object.class);
        } catch (Exception ex) {
            log.warn("im message extra_json parse failed, extra dropped");
            return null;
        }
    }

    /** last_message 列宽 200，非文字消息给一个可读占位，避免会话列表出现空白。 */
    private String summarize(Integer msgType, String text) {
        String summary = switch (msgType) {
            case 2 -> "[图片]";
            case 3 -> "[订单卡片]";
            case 4 -> "[位置]";
            default -> text;
        };
        if (summary.isEmpty()) {
            summary = "[消息]";
        }
        return summary.length() <= MAX_LAST_MESSAGE_LENGTH
                ? summary
                : summary.substring(0, MAX_LAST_MESSAGE_LENGTH);
    }

    /**
     * 事务内只注册回调，提交后再推送；没有事务上下文（如单元测试直调）时立即推送。
     * 推送失败只记日志，消息已落库，接口仍返回成功。
     */
    private void pushAfterCommit(Long sessionId, Integer receiverType, Long receiverId,
                                 PushPayload payload) {
        Runnable task = () -> {
            try {
                pushService.push(receiverType, receiverId, payload);
            } catch (Exception ex) {
                log.error("im push failed sessionId={} receiver={}:{}",
                        sessionId, receiverType, receiverId, ex);
            }
        };
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            task.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                task.run();
            }
        });
    }

    private String nextSessionNo() {
        return "IM" + System.currentTimeMillis()
                + String.format("%04d", ThreadLocalRandom.current().nextInt(MAX_SESSION_NO_RANDOM));
    }
}
