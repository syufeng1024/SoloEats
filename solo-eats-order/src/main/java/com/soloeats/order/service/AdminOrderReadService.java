package com.soloeats.order.service;

import com.soloeats.common.api.PageResult;
import com.soloeats.order.service.query.AdminExceptionQuery;
import com.soloeats.order.service.query.AdminRefundQuery;
import com.soloeats.order.service.view.AdminExceptionOrderView;
import com.soloeats.order.service.view.AdminOrderDetailView;
import com.soloeats.order.service.view.AdminRefundDetailView;
import com.soloeats.order.service.view.AdminRefundView;

/**
 * 运营后台订单中心只读查询：订单详情、售后列表/详情、异常订单池。
 * 不修改订单状态机，也不做审核动作。
 */
public interface AdminOrderReadService {

    /**
     * 平台订单详情。订单不存在抛 ORDER_NOT_FOUND；平台超管可见全量，不校验归属。
     */
    AdminOrderDetailView getOrderDetail(Long orderId);

    /**
     * 售后列表。status 为空时不过滤；keyword 同时匹配退款单号与订单号。
     */
    PageResult<AdminRefundView> pageRefunds(AdminRefundQuery query);

    /**
     * 售后详情。退款单不存在抛 REFUND_NOT_FOUND；明细按 id 升序。
     */
    AdminRefundDetailView getRefundDetail(Long refundId);

    /**
     * 异常订单池：t_order.exception_type != 0，按创建时间倒序。
     */
    PageResult<AdminExceptionOrderView> pageExceptionOrders(AdminExceptionQuery query);
}
