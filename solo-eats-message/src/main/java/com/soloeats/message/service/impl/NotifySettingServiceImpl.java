package com.soloeats.message.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.soloeats.common.exception.BizException;
import com.soloeats.common.exception.ErrorCode;
import com.soloeats.message.domain.entity.NotifySetting;
import com.soloeats.message.mapper.NotifySettingMapper;
import com.soloeats.message.service.NotifySettingService;
import com.soloeats.message.service.view.NotifySettingView;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 通知开关。t_notify_setting 只存「关闭」或显式开启的行，
 * 没有记录一律视为开启，避免新用户首次推送前需要批量初始化。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotifySettingServiceImpl implements NotifySettingService {

    /** t_notify_setting.receiver_type 只覆盖 1用户 2商家 3骑手。 */
    private static final int MIN_RECEIVER_TYPE = 1;
    private static final int MAX_RECEIVER_TYPE = 3;

    private static final Map<Integer, String> NOTIFY_TYPE_NAMES = Map.of(
            1, "订单消息",
            2, "营销消息",
            3, "系统消息",
            4, "会话消息");

    private final NotifySettingMapper notifySettingMapper;

    @Override
    public List<NotifySettingView> get(Integer receiverType, Long receiverId) {
        requireReceiver(receiverType, receiverId);
        List<NotifySetting> rows = notifySettingMapper.selectList(
                new LambdaQueryWrapper<NotifySetting>()
                        .eq(NotifySetting::getReceiverType, receiverType)
                        .eq(NotifySetting::getReceiverId, receiverId));
        Map<Integer, Integer> enabledByType = rows.stream()
                .filter(row -> row.getNotifyType() != null)
                .collect(Collectors.toMap(
                        NotifySetting::getNotifyType,
                        row -> row.getEnabled() == null ? 1 : row.getEnabled(),
                        (left, right) -> right));

        return NOTIFY_TYPE_NAMES.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> new NotifySettingView(
                        entry.getKey(),
                        entry.getValue(),
                        enabledByType.getOrDefault(entry.getKey(), 1) == 1))
                .toList();
    }

    @Override
    public NotifySettingView update(Integer receiverType, Long receiverId,
                                    Integer notifyType, boolean enabled) {
        requireReceiver(receiverType, receiverId);
        if (notifyType == null || !NOTIFY_TYPE_NAMES.containsKey(notifyType)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "通知类型只能是 1订单 2营销 3系统 4会话");
        }

        NotifySetting existing = find(receiverType, receiverId, notifyType);
        if (existing == null) {
            NotifySetting record = new NotifySetting();
            record.setReceiverType(receiverType);
            record.setReceiverId(receiverId);
            record.setNotifyType(notifyType);
            record.setEnabled(enabled ? 1 : 0);
            try {
                notifySettingMapper.insert(record);
            } catch (DuplicateKeyException ex) {
                // 并发首次写入：uk_receiver_type 冲突后改为更新，语义与 upsert 一致
                log.info("notify setting concurrent insert receiver={}:{} type={}",
                        receiverType, receiverId, notifyType);
                updateEnabled(find(receiverType, receiverId, notifyType), enabled);
            }
        } else {
            updateEnabled(existing, enabled);
        }
        log.info("notify setting updated receiver={}:{} type={} enabled={}",
                receiverType, receiverId, notifyType, enabled);
        return new NotifySettingView(notifyType, NOTIFY_TYPE_NAMES.get(notifyType), enabled);
    }

    @Override
    public boolean isEnabled(Integer receiverType, Long receiverId, Integer notifyType) {
        if (receiverType == null || receiverId == null || notifyType == null
                || !NOTIFY_TYPE_NAMES.containsKey(notifyType)) {
            return true;
        }
        NotifySetting setting = find(receiverType, receiverId, notifyType);
        return setting == null || setting.getEnabled() == null || setting.getEnabled() == 1;
    }

    private NotifySetting find(Integer receiverType, Long receiverId, Integer notifyType) {
        return notifySettingMapper.selectOne(new LambdaQueryWrapper<NotifySetting>()
                .eq(NotifySetting::getReceiverType, receiverType)
                .eq(NotifySetting::getReceiverId, receiverId)
                .eq(NotifySetting::getNotifyType, notifyType)
                .last("LIMIT 1"));
    }

    private void updateEnabled(NotifySetting existing, boolean enabled) {
        if (existing == null) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "通知开关写入冲突，请重试");
        }
        notifySettingMapper.update(null, new LambdaUpdateWrapper<NotifySetting>()
                .eq(NotifySetting::getId, existing.getId())
                .set(NotifySetting::getEnabled, enabled ? 1 : 0));
    }

    private void requireReceiver(Integer receiverType, Long receiverId) {
        if (receiverType == null
                || receiverType < MIN_RECEIVER_TYPE || receiverType > MAX_RECEIVER_TYPE) {
            throw new BizException(ErrorCode.PARAM_INVALID, "接收者类型只能是 1用户 2商家 3骑手");
        }
        if (receiverId == null || receiverId <= 0L) {
            throw new BizException(ErrorCode.PARAM_INVALID, "接收者ID不能为空");
        }
    }
}
