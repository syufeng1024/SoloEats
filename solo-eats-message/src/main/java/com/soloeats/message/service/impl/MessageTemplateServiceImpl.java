package com.soloeats.message.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.soloeats.common.api.PageResult;
import com.soloeats.common.exception.BizException;
import com.soloeats.common.exception.ErrorCode;
import com.soloeats.message.domain.entity.MessageTemplate;
import com.soloeats.message.mapper.MessageTemplateMapper;
import com.soloeats.message.service.MessageTemplateService;
import com.soloeats.message.service.command.SaveMessageTemplateCommand;
import com.soloeats.message.service.query.MessageTemplateQuery;
import com.soloeats.message.service.view.MessageTemplateView;
import com.soloeats.message.service.view.RenderedMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 模板维护 + 占位符渲染。渲染只做 {key} 字面替换，
 * 未提供的变量保留原文并 warn，避免悄悄吞掉运营配错的占位符。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MessageTemplateServiceImpl implements MessageTemplateService {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{([A-Za-z0-9_]+)}");
    private static final Set<Integer> TEMPLATE_TYPES = Set.of(1, 2, 3, 4);
    private static final Set<String> CHANNELS = Set.of("SITE", "WS", "PUSH");
    private static final long DEFAULT_PAGE_SIZE = 20L;
    private static final long MAX_PAGE_SIZE = 100L;
    private static final int MAX_CODE_LENGTH = 64;
    private static final int MAX_NAME_LENGTH = 100;
    private static final int MAX_TITLE_LENGTH = 200;
    private static final int MAX_CONTENT_LENGTH = 1000;

    private final MessageTemplateMapper messageTemplateMapper;

    @Override
    public PageResult<MessageTemplateView> page(MessageTemplateQuery query, long pageNum, long pageSize) {
        MessageTemplateQuery condition = query == null ? MessageTemplateQuery.empty() : query;
        String keyword = trimToNull(condition.keyword());

        LambdaQueryWrapper<MessageTemplate> wrapper = new LambdaQueryWrapper<>();
        if (keyword != null) {
            wrapper.and(w -> w.like(MessageTemplate::getTemplateCode, keyword)
                    .or()
                    .like(MessageTemplate::getTemplateName, keyword));
        }
        wrapper.eq(condition.templateType() != null, MessageTemplate::getTemplateType, condition.templateType())
                .eq(condition.status() != null, MessageTemplate::getStatus, condition.status())
                .orderByDesc(MessageTemplate::getId);

        long normalizedPageNum = pageNum < 1L ? 1L : pageNum;
        long normalizedPageSize = pageSize < 1L ? DEFAULT_PAGE_SIZE : Math.min(pageSize, MAX_PAGE_SIZE);
        Page<MessageTemplate> page = messageTemplateMapper.selectPage(
                new Page<>(normalizedPageNum, normalizedPageSize), wrapper);
        List<MessageTemplateView> views = page.getRecords().stream().map(MessageTemplateView::from).toList();
        return PageResult.of(views, page.getTotal(), normalizedPageNum, normalizedPageSize);
    }

    @Override
    public MessageTemplateView save(SaveMessageTemplateCommand command) {
        if (command == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请求不能为空");
        }
        String code = requireTemplateCode(command.templateCode());
        if (existsByCode(code, null)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "模板编码已存在: " + code);
        }

        MessageTemplate template = new MessageTemplate();
        template.setTemplateCode(code);
        template.setTemplateName(requireName(command.templateName()));
        template.setTemplateType(requireType(command.templateType()));
        template.setTitleTpl(requireTitle(command.titleTpl()));
        template.setContentTpl(requireContent(command.contentTpl()));
        template.setChannels(normalizeChannels(command.channels(), "SITE,WS"));
        template.setStatus(normalizeStatus(command.status(), 0));
        if (messageTemplateMapper.insert(template) != 1) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "消息模板保存失败");
        }
        log.info("message template created id={} code={}", template.getId(), code);
        return MessageTemplateView.from(requireById(template.getId()));
    }

    @Override
    public MessageTemplateView update(Long id, SaveMessageTemplateCommand command) {
        if (id == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "模板ID不能为空");
        }
        if (command == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请求不能为空");
        }
        MessageTemplate template = requireById(id);

        if (StringUtils.hasText(command.templateCode())) {
            String code = requireTemplateCode(command.templateCode());
            if (!code.equals(template.getTemplateCode()) && existsByCode(code, id)) {
                throw new BizException(ErrorCode.PARAM_INVALID, "模板编码已存在: " + code);
            }
            template.setTemplateCode(code);
        }
        if (StringUtils.hasText(command.templateName())) {
            template.setTemplateName(requireName(command.templateName()));
        }
        if (command.templateType() != null) {
            template.setTemplateType(requireType(command.templateType()));
        }
        if (StringUtils.hasText(command.titleTpl())) {
            template.setTitleTpl(requireTitle(command.titleTpl()));
        }
        if (StringUtils.hasText(command.contentTpl())) {
            template.setContentTpl(requireContent(command.contentTpl()));
        }
        if (StringUtils.hasText(command.channels())) {
            template.setChannels(normalizeChannels(command.channels(), "SITE,WS"));
        }
        if (command.status() != null) {
            template.setStatus(normalizeStatus(command.status(), 0));
        }

        if (messageTemplateMapper.updateById(template) != 1) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "消息模板更新失败");
        }
        log.info("message template updated id={} code={}", id, template.getTemplateCode());
        return MessageTemplateView.from(requireById(id));
    }

    @Override
    public RenderedMessage render(String templateCode, Map<String, String> params) {
        String code = trimToNull(templateCode);
        if (code == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "模板编码不能为空");
        }
        // 保存时统一转大写，查询侧保持一致，避免 order_paid / ORDER_PAID 两套写法
        code = code.toUpperCase(Locale.ROOT);
        MessageTemplate template = messageTemplateMapper.selectOne(
                new LambdaQueryWrapper<MessageTemplate>()
                        .eq(MessageTemplate::getTemplateCode, code)
                        .last("LIMIT 1"));
        if (template == null) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND, "消息模板不存在: " + code);
        }
        if (!Objects.equals(template.getStatus(), 0)) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND, "消息模板已停用: " + code);
        }

        String title = renderText(template.getTitleTpl(), params, code, "title");
        String content = renderText(template.getContentTpl(), params, code, "content");
        return new RenderedMessage(code, toMsgType(template.getTemplateType()), title, content);
    }

    private String renderText(String template, Map<String, String> params, String code, String field) {
        if (template == null) {
            return "";
        }
        Matcher matcher = PLACEHOLDER.matcher(template);
        StringBuilder rendered = new StringBuilder(template.length() + 32);
        while (matcher.find()) {
            String key = matcher.group(1);
            String value = params == null ? null : params.get(key);
            if (value == null) {
                log.warn("message template placeholder missing code={} field={} key={}", code, field, key);
                value = matcher.group(0);
            }
            matcher.appendReplacement(rendered, Matcher.quoteReplacement(value));
        }
        matcher.appendTail(rendered);
        return rendered.toString();
    }

    /** template_type 1订单/2营销/3系统/4客服 → msg_type 1订单/2营销/3系统（客服归入系统）。 */
    private Integer toMsgType(Integer templateType) {
        if (templateType == null) {
            return 3;
        }
        return switch (templateType) {
            case 1 -> 1;
            case 2 -> 2;
            default -> 3;
        };
    }

    private MessageTemplate requireById(Long id) {
        MessageTemplate template = messageTemplateMapper.selectById(id);
        if (template == null) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND, "消息模板不存在");
        }
        return template;
    }

    private boolean existsByCode(String code, Long excludeId) {
        LambdaQueryWrapper<MessageTemplate> wrapper = new LambdaQueryWrapper<MessageTemplate>()
                .eq(MessageTemplate::getTemplateCode, code)
                .ne(excludeId != null, MessageTemplate::getId, excludeId);
        return messageTemplateMapper.selectCount(wrapper) > 0;
    }

    private String requireTemplateCode(String value) {
        String code = trimToNull(value);
        if (code == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "模板编码不能为空");
        }
        if (code.length() > MAX_CODE_LENGTH) {
            throw new BizException(ErrorCode.PARAM_INVALID, "模板编码最长 64 个字符");
        }
        return code.toUpperCase(Locale.ROOT);
    }

    private String requireName(String value) {
        return requireLength(value, "模板名称不能为空", MAX_NAME_LENGTH, "模板名称最长 100 个字符");
    }

    private String requireTitle(String value) {
        return requireLength(value, "标题模板不能为空", MAX_TITLE_LENGTH, "标题模板最长 200 个字符");
    }

    private String requireContent(String value) {
        return requireLength(value, "内容模板不能为空", MAX_CONTENT_LENGTH, "内容模板最长 1000 个字符");
    }

    private String requireLength(String value, String emptyMessage, int maxLength, String tooLongMessage) {
        String text = trimToNull(value);
        if (text == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, emptyMessage);
        }
        if (text.length() > maxLength) {
            throw new BizException(ErrorCode.PARAM_INVALID, tooLongMessage);
        }
        return text;
    }

    private Integer requireType(Integer templateType) {
        if (templateType == null || !TEMPLATE_TYPES.contains(templateType)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "模板类型只能是 1订单 2营销 3系统 4客服");
        }
        return templateType;
    }

    private Integer normalizeStatus(Integer status, int defaultValue) {
        if (status == null) {
            return defaultValue;
        }
        if (status != 0 && status != 1) {
            throw new BizException(ErrorCode.PARAM_INVALID, "状态只能是 0启用 或 1停用");
        }
        return status;
    }

    /** 通道去重并按 SITE,WS,PUSH 归一；非法通道直接拒绝，不静默丢弃。 */
    private String normalizeChannels(String value, String defaultValue) {
        String text = trimToNull(value);
        if (text == null) {
            return defaultValue;
        }
        Set<String> normalized = new LinkedHashSet<>();
        for (String part : text.split(",")) {
            String channel = part.trim().toUpperCase(Locale.ROOT);
            if (channel.isEmpty()) {
                continue;
            }
            if (!CHANNELS.contains(channel)) {
                throw new BizException(ErrorCode.PARAM_INVALID, "不支持的通道: " + part.trim());
            }
            normalized.add(channel);
        }
        if (normalized.isEmpty()) {
            return defaultValue;
        }
        return String.join(",", normalized);
    }

    private static String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String trimmed = value.strip();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
