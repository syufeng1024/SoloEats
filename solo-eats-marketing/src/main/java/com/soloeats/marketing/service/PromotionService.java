package com.soloeats.marketing.service;

import com.soloeats.marketing.service.view.MarketingCalculateResult;

/**
 * 满减活动试算。order 模块通过本接口拿结果，自己写 t_order_discount；
 * marketing 不依赖 order。
 */
public interface PromotionService {

    MarketingCalculateResult calculate(MarketingCalculateCommand command);
}
