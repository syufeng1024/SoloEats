package com.soloeats.service.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.soloeats.common.exception.BizException;
import com.soloeats.common.exception.ErrorCode;
import com.soloeats.service.domain.entity.QuickReply;
import com.soloeats.service.mapper.QuickReplyMapper;
import com.soloeats.service.service.QuickReplyService;
import com.soloeats.service.service.command.QuickReplyCommand;
import com.soloeats.service.service.view.QuickReplyView;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class QuickReplyServiceImpl implements QuickReplyService {

    private static final int STATUS_ENABLED = 0;
    private static final int STATUS_DISABLED = 1;
    private static final Set<Integer> STATUSES = Set.of(STATUS_ENABLED, STATUS_DISABLED);

    private static final int MAX_CATEGORY_LENGTH = 64;
    private static final int MAX_CONTENT_LENGTH = 500;

    private final QuickReplyMapper quickReplyMapper;

    @Override
    public List<QuickReplyView> list(String category) {
        LambdaQueryWrapper<QuickReply> wrapper = new LambdaQueryWrapper<QuickReply>()
                .eq(StringUtils.hasText(category), QuickReply::getCategory, trim(category))
                .orderByAsc(QuickReply::getSort)
                .orderByDesc(QuickReply::getId);

        return quickReplyMapper.selectList(wrapper).stream()
                .map(QuickReplyView::from)
                .toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(QuickReplyCommand command) {
        QuickReply reply = new QuickReply();
        applyCommand(reply, command);
        quickReplyMapper.insert(reply);
        log.info("quick reply created id={} category={}", reply.getId(), reply.getCategory());
        return reply.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, QuickReplyCommand command) {
        QuickReply reply = requireQuickReply(id);
        applyCommand(reply, command);
        quickReplyMapper.updateById(reply);
        log.info("quick reply updated id={}", id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        requireQuickReply(id);
        // @TableLogic 把 deleteById 改写为 UPDATE t_quick_reply SET deleted = 1
        if (quickReplyMapper.deleteById(id) == 0) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND, "快捷话术不存在");
        }
        log.info("quick reply deleted id={}", id);
    }

    private void applyCommand(QuickReply reply, QuickReplyCommand command) {
        if (command == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请求参数不能为空");
        }
        reply.setCategory(normalizeText(command.category(), "category", MAX_CATEGORY_LENGTH));
        reply.setContent(normalizeText(command.content(), "content", MAX_CONTENT_LENGTH));
        reply.setSort(command.sort() == null ? 0 : command.sort());
        int status = command.status() == null ? STATUS_ENABLED : command.status();
        if (!STATUSES.contains(status)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "status 只能是 0启用 / 1停用");
        }
        reply.setStatus(status);
    }

    private QuickReply requireQuickReply(Long id) {
        if (id == null || id <= 0L) {
            throw new BizException(ErrorCode.PARAM_INVALID, "话术ID不能为空");
        }
        QuickReply reply = quickReplyMapper.selectById(id);
        if (reply == null) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND, "快捷话术不存在");
        }
        return reply;
    }

    /** 统一清洗：换行符归一、去首尾空白，空串与超长直接拒绝。 */
    private String normalizeText(String value, String field, int maxLength) {
        if (value == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, field + " 不能为空");
        }
        String text = value.replace("\r\n", "\n").replace('\r', '\n').strip();
        if (text.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_INVALID, field + " 不能为空");
        }
        if (text.length() > maxLength) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    field + " 最长 " + maxLength + " 个字符");
        }
        return text;
    }

    private static String trim(String value) {
        return value == null ? null : value.strip();
    }
}
