package com.soloeats.app.controller;

import com.soloeats.app.dto.PickupVerifyRequest;
import com.soloeats.app.security.PermissionGuard;
import com.soloeats.app.vo.OrderStatusVO;
import com.soloeats.common.api.ApiResponse;
import com.soloeats.common.api.PageResult;
import com.soloeats.common.auth.RequireRole;
import com.soloeats.common.auth.Roles;
import com.soloeats.common.log.OperationLog;
import com.soloeats.order.domain.enums.OrderStatus;
import com.soloeats.order.service.MerchantPickupService;
import com.soloeats.order.service.OrderQueryService;
import com.soloeats.order.service.query.MerchantOrderQuery;
import com.soloeats.order.service.view.MerchantOrderDetailView;
import com.soloeats.order.service.view.MerchantOrderView;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RequireRole(Roles.MERCHANT)
@RestController
@RequestMapping("/api/v1/merchant/orders")
@RequiredArgsConstructor
public class MerchantOrderController {

    private final OrderQueryService orderQueryService;
    private final MerchantPickupService merchantPickupService;
    private final PermissionGuard permissionGuard;

    @GetMapping
    public ApiResponse<PageResult<MerchantOrderView>> list(
            @RequestParam(required = false) Long shopId,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "20") long pageSize) {
        MerchantOrderQuery query = new MerchantOrderQuery(
                permissionGuard.requireMerchantId(),
                permissionGuard.resolveOptionalShopId(shopId),
                status,
                keyword,
                pageNum,
                pageSize
        );
        return ApiResponse.ok(orderQueryService.pageMerchantOrders(query));
    }

    @RequireRole(Roles.MERCHANT)
    @GetMapping("/{orderId}")
    public ApiResponse<MerchantOrderDetailView> detail(@PathVariable Long orderId) {
        permissionGuard.assertMerchantOrder(orderId);
        return ApiResponse.ok(orderQueryService.getMerchantOrder(orderId));
    }

    /**
     * 自取核销：WAIT_USER_PICKUP(31) --PICKUP_VERIFY--> PICKED_UP(51)。
     * 重复核销时订单已到 51，返回 ORDER_STATUS_INVALID，这是期望行为，不另做幂等表。
     */
    @RequireRole(Roles.MERCHANT)
    @OperationLog(module = "order", action = "pickupVerify")
    @PostMapping("/{orderId}/pickup/verify")
    public ApiResponse<OrderStatusVO> pickupVerify(@PathVariable Long orderId,
                                                   @Valid @RequestBody PickupVerifyRequest request) {
        permissionGuard.assertMerchantOrder(orderId);
        OrderStatus status = merchantPickupService.verifyPickup(
                orderId,
                permissionGuard.requireMerchantId(),
                request.pickupCode()
        );
        return ApiResponse.ok(OrderStatusVO.of(orderId, status));
    }
}
