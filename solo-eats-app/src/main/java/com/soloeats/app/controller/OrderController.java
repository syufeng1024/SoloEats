package com.soloeats.app.controller;

import com.soloeats.app.dto.CancelOrderRequest;
import com.soloeats.app.dto.CreateOrderRequest;
import com.soloeats.app.dto.MerchantRejectRequest;
import com.soloeats.app.dto.RefundRequest;
import com.soloeats.app.dto.RiderActionRequest;
import com.soloeats.app.security.PermissionGuard;
import com.soloeats.app.vo.CancelVO;
import com.soloeats.app.vo.OrderStatusVO;
import com.soloeats.app.vo.OrderVO;
import com.soloeats.app.vo.RefundVO;
import com.soloeats.common.api.ApiResponse;
import com.soloeats.common.api.PageResult;
import com.soloeats.common.auth.AuthContext;
import com.soloeats.common.auth.RequireRole;
import com.soloeats.common.auth.Roles;
import com.soloeats.common.ratelimit.RateLimit;
import com.soloeats.common.ratelimit.RateLimitDimension;
import com.soloeats.order.domain.enums.OperatorType;
import com.soloeats.order.domain.enums.OrderEvent;
import com.soloeats.order.domain.enums.OrderStatus;
import com.soloeats.order.service.DeliveryFulfillmentApplicationService;
import com.soloeats.order.service.OrderApplicationService;
import com.soloeats.order.service.OrderQueryService;
import com.soloeats.order.service.RefundService;
import com.soloeats.order.service.RiderDispatchResult;
import com.soloeats.order.service.command.CreateOrderCommand;
import com.soloeats.order.service.view.UserOrderDetailView;
import com.soloeats.order.service.view.UserOrderView;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderApplicationService orderApplicationService;
    private final DeliveryFulfillmentApplicationService deliveryFulfillmentApplicationService;
    private final RefundService refundService;
    private final PermissionGuard permissionGuard;
    private final OrderQueryService orderQueryService;

    @RequireRole(Roles.C_USER)
    @RateLimit(key = "order-create", limit = 20, windowSeconds = 60, dimension = RateLimitDimension.USER)
    @PostMapping
    public ApiResponse<OrderVO> create(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody CreateOrderRequest request) {

        permissionGuard.assertUser(request.userId());

        CreateOrderCommand command = new CreateOrderCommand(
                request.userId(),
                request.merchantId(),
                request.shopId(),
                request.fulfillmentType(),
                request.couponId(),
                request.packagingFee(),
                request.deliveryFee(),
                request.receiverName(),
                request.receiverPhone(),
                request.receiverAddress(),
                request.pickupPhone(),
                request.remark(),
                request.items().stream()
                        .map(item -> new CreateOrderCommand.ItemCommand(item.skuId(), item.quantity()))
                        .toList()
        );

        return ApiResponse.ok(
                OrderVO.from(orderApplicationService.createOrder(command, idempotencyKey))
        );
    }

    @RequireRole(Roles.MERCHANT)
    @PostMapping("/{orderId}/accept")
    public ApiResponse<OrderStatusVO> accept(@PathVariable Long orderId) {
        permissionGuard.assertMerchantOrder(orderId);
        OrderStatus status = orderApplicationService.transit(
                orderId,
                OrderEvent.MERCHANT_ACCEPT,
                null,
                OperatorType.MERCHANT,
                permissionGuard.principal().merchantId(),
                "MERCHANT_ACCEPT:" + orderId
        );
        return ApiResponse.ok(OrderStatusVO.of(orderId, status));
    }

    @RequireRole(Roles.MERCHANT)
    @PostMapping("/{orderId}/prepared")
    public ApiResponse<OrderStatusVO> prepared(@PathVariable Long orderId) {
        permissionGuard.assertMerchantOrder(orderId);
        OrderStatus status = orderApplicationService.transit(
                orderId,
                OrderEvent.PREPARE_DONE,
                null,
                OperatorType.MERCHANT,
                permissionGuard.principal().merchantId(),
                "PREPARE_DONE:" + orderId
        );
        return ApiResponse.ok(OrderStatusVO.of(orderId, status));
    }

    /**
     * 商家呼叫骑手。与下方的骑手接单接口分离：
     * 商家只推进配送单派单窗口，骑手仍需调用 /rider/accept 绑定 riderId。
     */
    @RequireRole(Roles.MERCHANT)
    @PostMapping("/{orderId}/merchant/call-rider")
    public ApiResponse<RiderDispatchResult> callRider(@PathVariable Long orderId) {
        permissionGuard.assertMerchantOrder(orderId);
        return ApiResponse.ok(deliveryFulfillmentApplicationService.callRider(orderId));
    }

    @RequireRole(Roles.RIDER)
    @PostMapping("/{orderId}/rider/accept")
    public ApiResponse<OrderStatusVO> riderAccept(
            @PathVariable Long orderId,
            @Valid @RequestBody RiderActionRequest request) {
        permissionGuard.assertRider(request.riderId());
        OrderStatus status = deliveryFulfillmentApplicationService.acceptRider(orderId, request.riderId());
        return ApiResponse.ok(OrderStatusVO.of(orderId, status));
    }

    @RequireRole(Roles.RIDER)
    @PostMapping("/{orderId}/rider/pickup")
    public ApiResponse<OrderStatusVO> riderPickup(
            @PathVariable Long orderId,
            @Valid @RequestBody RiderActionRequest request) {
        permissionGuard.assertRider(request.riderId());
        OrderStatus status = deliveryFulfillmentApplicationService.pickup(orderId, request.riderId());
        return ApiResponse.ok(OrderStatusVO.of(orderId, status));
    }

    @RequireRole(Roles.RIDER)
    @PostMapping("/{orderId}/rider/delivered")
    public ApiResponse<OrderStatusVO> riderDelivered(
            @PathVariable Long orderId,
            @Valid @RequestBody RiderActionRequest request) {
        permissionGuard.assertRider(request.riderId());
        OrderStatus status = deliveryFulfillmentApplicationService.delivered(orderId, request.riderId());
        return ApiResponse.ok(OrderStatusVO.of(orderId, status));
    }

    @RequireRole({Roles.C_USER, Roles.MERCHANT})
    @PostMapping("/{orderId}/cancel")
    public ApiResponse<CancelVO> cancel(
            @PathVariable Long orderId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody CancelOrderRequest request) {
        if (request.operatorType() == 1) {
            permissionGuard.assertUserOrder(orderId);
        } else {
            permissionGuard.assertMerchantOrder(orderId);
        }
        return ApiResponse.ok(CancelVO.from(
                refundService.cancel(orderId, request.reason(), request.operatorType(), idempotencyKey)
        ));
    }

    @RequireRole(Roles.C_USER)
    @PostMapping("/{orderId}/refund")
    public ApiResponse<RefundVO> refund(
            @PathVariable Long orderId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody RefundRequest request) {
        permissionGuard.assertUserOrder(orderId);
        return ApiResponse.ok(RefundVO.from(
                refundService.refund(orderId, request.reason(), idempotencyKey)
        ));
    }

    @RequireRole(Roles.MERCHANT)
    @PostMapping("/{orderId}/reject")
    public ApiResponse<RefundVO> reject(
            @PathVariable Long orderId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody MerchantRejectRequest request) {
        permissionGuard.assertMerchantOrder(orderId);
        return ApiResponse.ok(RefundVO.from(
                refundService.merchantReject(
                        orderId,
                        permissionGuard.principal().merchantId(),
                        request.reason(),
                        idempotencyKey
                )
        ));
    }

    @RequireRole(Roles.C_USER)
    @GetMapping
    public ApiResponse<PageResult<UserOrderView>> list(
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize) {
        return ApiResponse.ok(orderQueryService.pageUserOrders(
                AuthContext.require().userId(), status, pageNum, pageSize));
    }

    @RequireRole(Roles.C_USER)
    @GetMapping("/{orderId}")
    public ApiResponse<UserOrderDetailView> detail(@PathVariable Long orderId) {
        return ApiResponse.ok(orderQueryService.getUserOrder(
                AuthContext.require().userId(), orderId));
    }
}
