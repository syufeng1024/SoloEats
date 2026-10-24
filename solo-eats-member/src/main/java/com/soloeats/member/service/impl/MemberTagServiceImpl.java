package com.soloeats.member.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.soloeats.common.exception.BizException;
import com.soloeats.common.exception.ErrorCode;
import com.soloeats.member.domain.entity.MemberTag;
import com.soloeats.member.domain.entity.MemberTagRel;
import com.soloeats.member.domain.entity.MemberUser;
import com.soloeats.member.mapper.MemberTagMapper;
import com.soloeats.member.mapper.MemberTagRelMapper;
import com.soloeats.member.mapper.MemberUserMapper;
import com.soloeats.member.service.MemberTagService;
import com.soloeats.member.service.command.SaveTagCommand;
import com.soloeats.member.service.view.TagView;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 标签定义维护 + 手动打标。打标是全量覆盖语义：
 * 先按 user_id 物理删除关联，再批量插入本次提交的标签集合，整体同事务。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MemberTagServiceImpl implements MemberTagService {

    private static final int MAX_TAG_NAME_LENGTH = 64;
    private static final int MAX_RULE_LENGTH = 255;

    private final MemberTagMapper memberTagMapper;
    private final MemberTagRelMapper memberTagRelMapper;
    private final MemberUserMapper memberUserMapper;

    @Override
    public List<TagView> list() {
        Map<Long, Long> memberCounts = memberTagRelMapper.countMembersByTag().stream()
                .collect(Collectors.toMap(
                        MemberTagRelMapper.TagMemberCount::getTagId,
                        MemberTagRelMapper.TagMemberCount::getMemberCount,
                        (left, right) -> left));
        List<MemberTag> tags = memberTagMapper.selectList(new LambdaQueryWrapper<MemberTag>()
                .orderByAsc(MemberTag::getId));
        return tags.stream()
                .map(tag -> TagView.of(tag, memberCounts.getOrDefault(tag.getId(), 0L)))
                .toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TagView create(SaveTagCommand command) {
        SaveTagCommand cmd = requireCommand(command);

        MemberTag tag = new MemberTag();
        tag.setTagName(requireName(cmd.name()));
        tag.setTagRule(normalizeRule(cmd.ruleDesc()));
        tag.setAutoTag(requireFlag(cmd.autoFlag(), "autoFlag"));
        tag.setStatus(requireFlag(cmd.status(), "status"));

        try {
            memberTagMapper.insert(tag);
        } catch (DuplicateKeyException ex) {
            // uk_tag_name(tenant_id, tag_name) 兜底
            throw new BizException(ErrorCode.PARAM_INVALID, "标签名称已存在");
        }
        log.info("member tag created tagId={}", tag.getId());
        return TagView.of(tag, 0L);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TagView update(Long id, SaveTagCommand command) {
        SaveTagCommand cmd = requireCommand(command);
        MemberTag existing = id == null ? null : memberTagMapper.selectById(id);
        if (existing == null) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND, "标签不存在");
        }

        String name = requireName(cmd.name());
        int autoFlag = requireFlag(cmd.autoFlag(), "autoFlag");
        int status = requireFlag(cmd.status(), "status");

        int rows;
        try {
            // 条件更新带 id，@TableLogic 自动追加 deleted = 0
            rows = memberTagMapper.update(null, new LambdaUpdateWrapper<MemberTag>()
                    .eq(MemberTag::getId, id)
                    .set(MemberTag::getTagName, name)
                    .set(MemberTag::getTagRule, normalizeRule(cmd.ruleDesc()))
                    .set(MemberTag::getAutoTag, autoFlag)
                    .set(MemberTag::getStatus, status));
        } catch (DuplicateKeyException ex) {
            throw new BizException(ErrorCode.PARAM_INVALID, "标签名称已存在");
        }
        if (rows == 0) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND, "标签不存在");
        }

        MemberTag updated = memberTagMapper.selectById(id);
        long memberCount = memberTagRelMapper.selectCount(new LambdaQueryWrapper<MemberTagRel>()
                .eq(MemberTagRel::getTagId, id));
        log.info("member tag updated tagId={} status={}", id, status);
        return TagView.of(updated == null ? existing : updated, memberCount);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void assignTags(Long userId, List<Long> tagIds) {
        MemberUser user = userId == null ? null : memberUserMapper.selectById(userId);
        if (user == null) {
            throw new BizException(ErrorCode.MEMBER_NOT_FOUND);
        }
        if (tagIds == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "tagIds 不能为空");
        }
        if (tagIds.stream().anyMatch(Objects::isNull)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "tagIds 不能包含空值");
        }

        List<Long> distinctTagIds = tagIds.stream().distinct().toList();
        if (!distinctTagIds.isEmpty()) {
            long existing = memberTagMapper.selectCount(new LambdaQueryWrapper<MemberTag>()
                    .in(MemberTag::getId, distinctTagIds));
            if (existing != distinctTagIds.size()) {
                throw new BizException(ErrorCode.PARAM_INVALID, "标签不存在或已删除");
            }
        }

        memberTagRelMapper.delete(new LambdaQueryWrapper<MemberTagRel>()
                .eq(MemberTagRel::getUserId, userId));
        if (!distinctTagIds.isEmpty()) {
            List<MemberTagRel> rels = distinctTagIds.stream()
                    .map(tagId -> relation(userId, tagId))
                    .toList();
            memberTagRelMapper.insertBatch(rels);
        }
        log.info("member tags assigned userId={} tagCount={}", userId, distinctTagIds.size());
    }

    private static MemberTagRel relation(Long userId, Long tagId) {
        MemberTagRel rel = new MemberTagRel();
        rel.setUserId(userId);
        rel.setTagId(tagId);
        return rel;
    }

    private static SaveTagCommand requireCommand(SaveTagCommand command) {
        if (command == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "标签参数不能为空");
        }
        return command;
    }

    private static String requireName(String name) {
        String cleaned = clean(name);
        if (cleaned == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "标签名称不能为空");
        }
        if (cleaned.length() > MAX_TAG_NAME_LENGTH) {
            throw new BizException(ErrorCode.PARAM_INVALID, "标签名称最长 64 个字符");
        }
        return cleaned;
    }

    private static int requireFlag(Integer value, String field) {
        if (value == null || (value != 0 && value != 1)) {
            throw new BizException(ErrorCode.PARAM_INVALID, field + " 只能是 0 或 1");
        }
        return value;
    }

    private static String normalizeRule(String rule) {
        String cleaned = clean(rule);
        if (cleaned != null && cleaned.length() > MAX_RULE_LENGTH) {
            throw new BizException(ErrorCode.PARAM_INVALID, "分群规则最长 255 个字符");
        }
        return cleaned;
    }

    private static String clean(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String cleaned = value.replaceAll("[\\r\\n\\t]", " ").strip();
        return cleaned.isEmpty() ? null : cleaned;
    }
}
