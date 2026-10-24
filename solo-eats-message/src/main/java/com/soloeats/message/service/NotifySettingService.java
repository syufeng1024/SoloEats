package com.soloeats.message.service;

import com.soloeats.message.service.view.NotifySettingView;

import java.util.List;

public interface NotifySettingService {

    /** 返回 1~4 全部开关；无记录的类型按开启返回。 */
    List<NotifySettingView> get(Integer receiverType, Long receiverId);

    /** upsert 单个开关，返回最新状态。 */
    NotifySettingView update(Integer receiverType, Long receiverId, Integer notifyType, boolean enabled);

    /** 推送前置判断：无记录视为开启。 */
    boolean isEnabled(Integer receiverType, Long receiverId, Integer notifyType);
}
