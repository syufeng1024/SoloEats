package com.soloeats.order.service;

import com.soloeats.common.api.PageResult;
import com.soloeats.order.service.query.AdminOrderQuery;
import com.soloeats.order.service.query.MerchantOrderQuery;
import com.soloeats.order.service.view.AdminOrderMonitorResult;
import com.soloeats.order.service.view.MerchantOrderView;

/**
 * 只读查询服务。商家端按 merchantId 约束，平台端可按商家/门店/状态/日期筛选。
 */
public interface OrderQueryService {

    PageResult<MerchantOrderView> pageMerchantOrders(MerchantOrderQuery query);

    AdminOrderMonitorResult monitor(AdminOrderQuery query);
}
