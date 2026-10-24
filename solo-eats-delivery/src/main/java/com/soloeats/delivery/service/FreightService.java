package com.soloeats.delivery.service;

import com.soloeats.common.api.PageResult;
import com.soloeats.delivery.service.command.FreightEstimateCommand;
import com.soloeats.delivery.service.command.SaveFreightTemplateCommand;
import com.soloeats.delivery.service.view.FreightEstimateView;
import com.soloeats.delivery.service.view.FreightTemplateView;

import java.util.List;

/**
 * 运费模板维护与试算。金额一律 long 分；模板与规则从数据库读取，不写死在 Java 里。
 */
public interface FreightService {

    /**
     * 试算运费：起步价 → 距离阶梯 → 时段加价 → 恶劣天气 → 重量/楼层 → 满额减免。
     * 自取单（fulfillmentType=2）直接返回 0；超出模板 max_distance_m 抛 50011。
     */
    FreightEstimateView estimate(FreightEstimateCommand command);

    List<FreightTemplateView> listTemplates();

    PageResult<FreightTemplateView> page(long pageNum, long pageSize);

    FreightTemplateView saveTemplate(SaveFreightTemplateCommand command);

    /** 更新模板并全量覆盖规则列表。 */
    FreightTemplateView updateTemplate(Long id, SaveFreightTemplateCommand command);

    /** 门店绑定模板；未绑定的门店试算时回落到 id=1 默认模板。 */
    void bindShop(Long shopId, Long templateId);
}
