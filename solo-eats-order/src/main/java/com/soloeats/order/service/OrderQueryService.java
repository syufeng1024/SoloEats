package com.soloeats.order.service;

import com.soloeats.common.api.PageResult;
import com.soloeats.order.service.query.AdminOrderQuery;
import com.soloeats.order.service.query.MerchantOrderQuery;
import com.soloeats.order.service.view.AdminOrderMonitorResult;
import com.soloeats.order.service.view.MerchantOrderView;
import com.soloeats.order.service.view.UserOrderDetailView;
import com.soloeats.order.service.view.UserOrderView;
import org.apache.ibatis.annotations.Param;

/**
 * 只读查询服务。商家端按 merchantId 约束，平台端可按商家/门店/状态/日期筛选。
 */
public interface OrderQueryService {

    PageResult<MerchantOrderView> pageMerchantOrders(MerchantOrderQuery query);

    AdminOrderMonitorResult monitor(AdminOrderQuery query);

    /**
     * 订单归属校验：订单存在且 user_id 与当前登录用户一致才返回 true。
     * 供 app 层做 C 端越权检查，避免 delivery 反向依赖 order。
     */
    boolean belongsToUser(@Param("orderId") Long orderId, @Param("userId") Long userId);

    /**
     * C 端订单列表：只返回 userId 本人的订单，status 非空时精确过滤。
     * pageNum 默认 1，pageSize 默认 10、上限 50。
     */
    PageResult<UserOrderView> pageUserOrders(Long userId, Integer status, int pageNum, int pageSize);

    /**
     * C 端订单详情：订单不存在抛 ORDER_NOT_FOUND，归属不符抛 AUTH_FORBIDDEN。
     */
    UserOrderDetailView getUserOrder(Long userId, Long orderId);
}
