package com.soloeats.marketing.service;

import com.soloeats.common.api.PageResult;
import com.soloeats.marketing.service.command.PromotionSaveCommand;
import com.soloeats.marketing.service.view.PromotionView;

/**
 * 后台活动管理：分页、详情、新建、编辑、状态变更。
 * 用户端可见活动走 {@link PromotionService#listAvailable}，不在本接口内。
 */
public interface PromotionAdminService {

    /**
     * 活动分页。列表不展开 rules/scopes（避免逐行查子表），需要档位与范围项时调 {@link #detail}。
     */
    PageResult<PromotionView> page(Integer status, Integer promotionType, Integer bearer,
                                   String keyword, long pageNum, long pageSize);

    PromotionView detail(Long id);

    /**
     * 新建活动，返回主键。子表（档位/范围）在同一事务内写入。
     */
    Long create(PromotionSaveCommand command);

    /**
     * 编辑活动。主表字段整体覆盖，档位与范围项按 promotion_id 物理删除后重新插入。
     */
    void update(Long id, PromotionSaveCommand command);

    /**
     * 状态变更。合法值：0草稿 1进行中 2已结束 3暂停。
     */
    void changeStatus(Long id, Integer status);
}
