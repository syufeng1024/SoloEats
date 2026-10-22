package com.soloeats.product.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.soloeats.common.exception.BizException;
import com.soloeats.common.exception.ErrorCode;
import com.soloeats.product.domain.entity.MediaGroup;
import com.soloeats.product.domain.entity.MediaItem;
import com.soloeats.product.mapper.MediaGroupMapper;
import com.soloeats.product.mapper.MediaItemMapper;
import com.soloeats.product.service.MediaLibraryService;
import com.soloeats.product.service.command.SaveMediaGroupCommand;
import com.soloeats.product.service.command.SaveMediaItemCommand;
import com.soloeats.product.service.view.MediaGroupView;
import com.soloeats.product.service.view.MediaItemView;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class MediaLibraryServiceImpl implements MediaLibraryService {

    private static final int MAX_GROUP_NAME = 64;
    private static final int MEDIA_IMAGE = 1;
    private static final int MEDIA_VIDEO = 2;

    private final MediaGroupMapper mediaGroupMapper;
    private final MediaItemMapper mediaItemMapper;

    @Override
    public List<MediaGroupView> listGroups() {
        return mediaGroupMapper.selectList(new LambdaQueryWrapper<MediaGroup>()
                        .orderByAsc(MediaGroup::getParentId)
                        .orderByAsc(MediaGroup::getSort)
                        .orderByAsc(MediaGroup::getId))
                .stream()
                .map(this::toView)
                .toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MediaGroupView saveGroup(Long id, SaveMediaGroupCommand command) {
        if (command == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请求不能为空");
        }
        String name = command.groupName() == null ? "" : command.groupName().trim();
        if (!StringUtils.hasText(name)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "groupName 不能为空");
        }
        if (name.length() > MAX_GROUP_NAME) {
            throw new BizException(ErrorCode.PARAM_INVALID, "groupName 长度不能超过 64");
        }
        long parentId = command.parentId() == null ? 0L : command.parentId();
        if (parentId > 0L) {
            if (Objects.equals(parentId, id)) {
                throw new BizException(ErrorCode.PARAM_INVALID, "父分组不能是自身");
            }
            if (mediaGroupMapper.selectById(parentId) == null) {
                throw new BizException(ErrorCode.RESOURCE_NOT_FOUND, "父分组不存在");
            }
        }
        int status = command.status() == null ? 0 : command.status();
        if (status != 0 && status != 1) {
            throw new BizException(ErrorCode.PARAM_INVALID, "status 只能是 0 或 1");
        }

        boolean creating = id == null;
        MediaGroup group;
        if (creating) {
            group = new MediaGroup();
        } else {
            group = mediaGroupMapper.selectById(id);
            if (group == null) {
                throw new BizException(ErrorCode.RESOURCE_NOT_FOUND, "素材分组不存在");
            }
        }
        group.setGroupName(name);
        group.setParentId(parentId);
        group.setSort(command.sort() == null ? 0 : command.sort());
        group.setStatus(status);
        if (creating) {
            mediaGroupMapper.insert(group);
        } else {
            mediaGroupMapper.updateById(group);
        }
        return toView(mediaGroupMapper.selectById(group.getId()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteGroup(Long id) {
        MediaGroup group = requireGroup(id);
        long children = mediaGroupMapper.selectCount(new LambdaQueryWrapper<MediaGroup>()
                .eq(MediaGroup::getParentId, group.getId()));
        if (children > 0L) {
            throw new BizException(ErrorCode.PARAM_INVALID, "存在子分组，不能删除");
        }
        long items = mediaItemMapper.selectCount(new LambdaQueryWrapper<MediaItem>()
                .eq(MediaItem::getGroupId, group.getId()));
        if (items > 0L) {
            throw new BizException(ErrorCode.PARAM_INVALID, "分组下存在素材，不能删除");
        }
        mediaGroupMapper.deleteById(id);
    }

    @Override
    public List<MediaItemView> listItems(Long groupId) {
        requireGroup(groupId);
        return mediaItemMapper.selectList(new LambdaQueryWrapper<MediaItem>()
                        .eq(MediaItem::getGroupId, groupId)
                        .orderByAsc(MediaItem::getSort)
                        .orderByAsc(MediaItem::getId))
                .stream()
                .map(this::toView)
                .toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MediaItemView addItem(Long groupId, SaveMediaItemCommand command) {
        requireGroup(groupId);
        if (command == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请求不能为空");
        }
        if (command.fileId() == null || command.fileId() <= 0L) {
            throw new BizException(ErrorCode.PARAM_INVALID, "fileId 必须是正整数");
        }
        int mediaType = command.mediaType() == null ? MEDIA_IMAGE : command.mediaType();
        if (mediaType != MEDIA_IMAGE && mediaType != MEDIA_VIDEO) {
            throw new BizException(ErrorCode.PARAM_INVALID, "mediaType 只能是 1图片 / 2视频");
        }
        int status = command.status() == null ? 0 : command.status();
        if (status != 0 && status != 1) {
            throw new BizException(ErrorCode.PARAM_INVALID, "status 只能是 0 或 1");
        }

        MediaItem item = new MediaItem();
        item.setGroupId(groupId);
        item.setFileId(command.fileId());
        item.setMediaType(mediaType);
        item.setSort(command.sort() == null ? 0 : command.sort());
        item.setStatus(status);
        mediaItemMapper.insert(item);
        return toView(mediaItemMapper.selectById(item.getId()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteItem(Long id) {
        if (id == null || id <= 0L) {
            throw new BizException(ErrorCode.PARAM_INVALID, "素材ID必须是正整数");
        }
        if (mediaItemMapper.selectById(id) == null) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND, "素材不存在");
        }
        mediaItemMapper.deleteById(id);
    }

    private MediaGroup requireGroup(Long id) {
        if (id == null || id <= 0L) {
            throw new BizException(ErrorCode.PARAM_INVALID, "素材分组ID必须是正整数");
        }
        MediaGroup group = mediaGroupMapper.selectById(id);
        if (group == null) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND, "素材分组不存在");
        }
        return group;
    }

    private MediaGroupView toView(MediaGroup group) {
        return new MediaGroupView(
                group.getId(), group.getGroupName(), group.getParentId(),
                group.getSort(), group.getStatus());
    }

    private MediaItemView toView(MediaItem item) {
        return new MediaItemView(
                item.getId(), item.getGroupId(), item.getFileId(),
                item.getMediaType(), item.getSort(), item.getStatus());
    }
}
