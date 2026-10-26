package com.soloeats.content.service;

import com.soloeats.content.service.command.SaveFloorCommand;
import com.soloeats.content.service.view.HomePageView;

import java.util.List;

public interface HomePageService {

    /** 后台：页面 + 全部楼层（含停用/未生效）。 */
    HomePageView adminDetail(String pageCode);

    /** 所见即所得预览：返回聚合数据，不落库、不校验生效时间。 */
    HomePageView preview(String pageCode);

    /**
     * 用户端首页聚合。仅返回已发布页面、生效中且命中投放人群的楼层。
     *
     * @param userId   登录消费者 ID；未登录为 null
     * @param deviceId 未登录设备的稳定标识，可为 null
     */
    HomePageView clientHome(String pageCode, Long userId, String deviceId);

    Long createFloor(SaveFloorCommand command);

    void updateFloor(Long id, SaveFloorCommand command);

    /** 删除楼层；页面已发布时先置为已下线。 */
    void deleteFloor(Long id);

    void sortFloors(List<Long> ids);

    /** 发布前校验 config_json 引用，成功后 version+1 并清理缓存。 */
    void publish(String pageCode);
}
