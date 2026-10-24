package com.soloeats.message.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.soloeats.common.api.PageResult;
import com.soloeats.common.exception.BizException;
import com.soloeats.common.exception.ErrorCode;
import com.soloeats.message.domain.entity.SiteMessage;
import com.soloeats.message.domain.enums.ReceiverType;
import com.soloeats.message.mapper.SiteMessageMapper;
import com.soloeats.message.mapper.UnreadCountRow;
import com.soloeats.message.service.MessageTemplateService;
import com.soloeats.message.service.PushService;
import com.soloeats.message.service.SiteMessageService;
import com.soloeats.message.service.command.PushPayload;
import com.soloeats.message.service.command.SendMessageCommand;
import com.soloeats.message.service.query.SiteMessageQuery;
import com.soloeats.message.service.view.RenderedMessage;
import com.soloeats.message.service.view.SiteMessageView;
import com.soloeats.message.service.view.UnreadCountView;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 站内信。发送顺序固定为「渲染 → 落库 → 推送」：
 * 先落库保证 WS 离线也能在消息中心看到，推送异常不回滚已保存的站内信。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SiteMessageServiceImpl implements SiteMessageService {

    private static final long DEFAULT_PAGE_SIZE = 20L;
    private static final long MAX_PAGE_SIZE = 100L;
    private static final int MAX_TITLE_LENGTH = 200;
    private static final int MAX_CONTENT_LENGTH = 1000;
    private static final int MAX_BIZ_TYPE_LENGTH = 32;
    private static final Set<Integer> MSG_TYPES = Set.of(1, 2, 3);

    private final SiteMessageMapper siteMessageMapper;
    private final MessageTemplateService messageTemplateService;
    private final PushService pushService;

    @Override
    public Long send(SendMessageCommand command) {
        validate(command);

        RenderedMessage rendered = messageTemplateService.render(command.templateCode(), command.params());
        String title = truncate(rendered.title(), MAX_TITLE_LENGTH, "title");
        String content = truncate(rendered.content(), MAX_CONTENT_LENGTH, "content");

        SiteMessage message = new SiteMessage();
        message.setReceiverType(command.receiverType());
        message.setReceiverId(command.receiverId());
        message.setTemplateCode(rendered.templateCode());
        message.setMsgType(rendered.msgType());
        message.setTitle(title);
        message.setContent(content);
        message.setBizType(normalizeBizType(command.bizType()));
        message.setBizId(command.bizId());
        message.setReadFlag(0);
        siteMessageMapper.insert(message);

        // WS 是加速通道，失败不能影响已落库的站内信，也不能影响订单事务
        try {
            pushService.push(command.receiverType(), command.receiverId(),
                    toPayload(rendered, message.getId(), command.pushTarget()));
        } catch (Exception ex) {
            log.error("push failed messageId={} receiver={}:{}",
                    message.getId(), command.receiverType(), command.receiverId(), ex);
        }
        return message.getId();
    }

    @Override
    public PageResult<SiteMessageView> page(Integer receiverType, Long receiverId,
                                            SiteMessageQuery query, long pageNum, long pageSize) {
        requireReceiver(receiverType, receiverId);
        SiteMessageQuery condition = query == null ? SiteMessageQuery.empty() : query;
        if (condition.msgType() != null && !MSG_TYPES.contains(condition.msgType())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "消息类型只能是 1订单 2营销 3系统");
        }
        if (condition.readFlag() != null && condition.readFlag() != 0 && condition.readFlag() != 1) {
            throw new BizException(ErrorCode.PARAM_INVALID, "readFlag 只能是 0未读 或 1已读");
        }

        LambdaQueryWrapper<SiteMessage> wrapper = new LambdaQueryWrapper<SiteMessage>()
                .eq(SiteMessage::getReceiverType, receiverType)
                .eq(SiteMessage::getReceiverId, receiverId)
                .eq(condition.msgType() != null, SiteMessage::getMsgType, condition.msgType())
                .eq(condition.readFlag() != null, SiteMessage::getReadFlag, condition.readFlag())
                .orderByDesc(SiteMessage::getCreatedAt)
                .orderByDesc(SiteMessage::getId);

        long normalizedPageNum = pageNum < 1L ? 1L : pageNum;
        long normalizedPageSize = pageSize < 1L ? DEFAULT_PAGE_SIZE : Math.min(pageSize, MAX_PAGE_SIZE);
        Page<SiteMessage> page = siteMessageMapper.selectPage(
                new Page<>(normalizedPageNum, normalizedPageSize), wrapper);
        List<SiteMessageView> views = page.getRecords().stream().map(SiteMessageView::from).toList();
        return PageResult.of(views, page.getTotal(), normalizedPageNum, normalizedPageSize);
    }

    @Override
    public UnreadCountView unreadCount(Integer receiverType, Long receiverId) {
        requireReceiver(receiverType, receiverId);
        List<UnreadCountRow> rows = siteMessageMapper.countUnreadByType(receiverType, receiverId);
        long order = 0L;
        long marketing = 0L;
        long system = 0L;
        if (rows != null) {
            for (UnreadCountRow row : rows) {
                if (row == null || row.getMsgType() == null) {
                    continue;
                }
                long count = row.getCnt() == null ? 0L : row.getCnt();
                switch (row.getMsgType()) {
                    case 1 -> order += count;
                    case 2 -> marketing += count;
                    case 3 -> system += count;
                    default -> {
                        // 未知类型不计入任何角标，保留在库供后续扩展
                    }
                }
            }
        }
        return new UnreadCountView(order + marketing + system,
                new UnreadCountView.ByType(order, marketing, system));
    }

    @Override
    public void markRead(Long id, Integer receiverType, Long receiverId) {
        requireReceiver(receiverType, receiverId);
        if (id == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "站内信ID不能为空");
        }
        SiteMessage message = siteMessageMapper.selectById(id);
        if (message == null) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND, "站内信不存在");
        }
        if (!Objects.equals(message.getReceiverType(), receiverType)
                || !Objects.equals(message.getReceiverId(), receiverId)) {
            throw new BizException(ErrorCode.AUTH_FORBIDDEN);
        }
        if (Objects.equals(message.getReadFlag(), 1)) {
            return;
        }
        int rows = siteMessageMapper.markRead(id, receiverType, receiverId, LocalDateTime.now());
        if (rows != 1) {
            log.info("site message mark read skipped id={} receiver={}:{}",
                    id, receiverType, receiverId);
        }
    }

    @Override
    public int markAllRead(Integer receiverType, Long receiverId, Integer msgType) {
        requireReceiver(receiverType, receiverId);
        if (msgType != null && !MSG_TYPES.contains(msgType)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "消息类型只能是 1订单 2营销 3系统");
        }
        int rows = siteMessageMapper.markAllRead(
                receiverType, receiverId, msgType, LocalDateTime.now());
        log.info("site message mark all read receiver={}:{} msgType={} rows={}",
                receiverType, receiverId, msgType, rows);
        return rows;
    }

    private PushPayload toPayload(RenderedMessage rendered, Long messageId,
                                  SendMessageCommand.PushTarget target) {
        return new PushPayload(
                rendered.msgType(),
                target == null ? "SITE_MESSAGE" : target.type(),
                messageId,
                target == null ? null : target.orderId(),
                target == null ? null : target.orderNo(),
                target == null ? null : target.status(),
                rendered.title(),
                rendered.content());
    }

    private void validate(SendMessageCommand command) {
        if (command == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请求不能为空");
        }
        requireReceiver(command.receiverType(), command.receiverId());
        if (!StringUtils.hasText(command.templateCode())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "模板编码不能为空");
        }
        String bizType = normalizeBizType(command.bizType());
        if (bizType != null && bizType.length() > MAX_BIZ_TYPE_LENGTH) {
            throw new BizException(ErrorCode.PARAM_INVALID, "bizType 最长 32 个字符");
        }
    }

    private void requireReceiver(Integer receiverType, Long receiverId) {
        if (!ReceiverType.isValid(receiverType)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "接收者类型只能是 1用户 2商家 3骑手 4平台");
        }
        if (receiverId == null || receiverId <= 0L) {
            throw new BizException(ErrorCode.PARAM_INVALID, "接收者ID不能为空");
        }
    }

    private String normalizeBizType(String bizType) {
        return StringUtils.hasText(bizType) ? bizType.strip() : null;
    }

    /** 渲染后超长按列宽截断，避免单条长参数导致整条订单通知落库失败。 */
    private String truncate(String value, int maxLength, String field) {
        if (value == null) {
            return "";
        }
        if (value.length() <= maxLength) {
            return value;
        }
        log.warn("site message {} truncated from {} to {} chars", field, value.length(), maxLength);
        return value.substring(0, maxLength);
    }
}
