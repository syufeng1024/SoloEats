package com.soloeats.content.service;

import com.soloeats.common.api.PageResult;
import com.soloeats.content.service.command.SaveBannerCommand;
import com.soloeats.content.service.view.BannerView;

import java.util.List;

public interface BannerService {

    PageResult<BannerView> page(String slotCode, Integer status, long pageNum, long pageSize);

    BannerView detail(Long id);

    Long create(SaveBannerCommand command);

    void update(Long id, SaveBannerCommand command);

    void delete(Long id);

    /** 用户端：按广告位返回生效中的 Banner，并异步累加曝光数。 */
    List<BannerView> listClient(String slotCode);

    /** 定时任务调用：把 Redis 累加的曝光数刷回数据库。 */
    void flushViewCounts();
}
