package com.soloeats.app.controller;

import com.soloeats.app.dto.GoOnlineRequest;
import com.soloeats.app.dto.RejectDispatchRequest;
import com.soloeats.app.dto.SubmitCertificationRequest;
import com.soloeats.common.api.ApiResponse;
import com.soloeats.common.auth.AuthContext;
import com.soloeats.common.auth.RequireRole;
import com.soloeats.common.auth.Roles;
import com.soloeats.common.exception.BizException;
import com.soloeats.common.exception.ErrorCode;
import com.soloeats.common.idempotency.Idempotent;
import com.soloeats.delivery.service.RiderDispatchService;
import com.soloeats.delivery.service.RiderOnlineService;
import com.soloeats.delivery.service.RiderService;
import com.soloeats.delivery.service.command.SubmitCertificationCommand;
import com.soloeats.delivery.service.view.DeliveryActionView;
import com.soloeats.delivery.service.view.HallOrderView;
import com.soloeats.delivery.service.view.MyCertificationView;
import com.soloeats.delivery.service.view.PendingDispatchView;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

/**
 * 骑手端：认证、上下线、听单、抢单/拒单。
 * riderId 一律由登录账号 userId 反查 t_rider，从不从请求体读取。
 */
@RequireRole(Roles.RIDER)
@RestController
@RequestMapping("/api/v1/rider")
@RequiredArgsConstructor
public class RiderDispatchController {

    private final RiderService riderService;
    private final RiderOnlineService riderOnlineService;
    private final RiderDispatchService riderDispatchService;

    @Idempotent(ttlSeconds = 10)
    @PostMapping("/certification")
    public ApiResponse<Long> submitCertification(
            @Valid @RequestBody SubmitCertificationRequest request) {
        Long userId = AuthContext.require().userId();
        return ApiResponse.ok(riderService.submitCertification(userId,
                new SubmitCertificationCommand(
                        request.realName(),
                        request.idCard(),
                        request.idCardFrontFileId(),
                        request.idCardBackFileId(),
                        request.healthCertFileId(),
                        request.healthCertExpireAt(),
                        request.vehicleType())));
    }

    @GetMapping("/certification")
    public ApiResponse<MyCertificationView> myCertification() {
        Long userId = AuthContext.require().userId();
        return ApiResponse.ok(riderService.myCertification(userId));
    }

    @PutMapping("/online")
    public ApiResponse<Void> online(@Valid @RequestBody GoOnlineRequest request) {
        Long riderId = currentRiderId();
        if (Boolean.TRUE.equals(request.online())) {
            if (request.lng() == null || request.lat() == null) {
                throw new BizException(ErrorCode.PARAM_INVALID, "上线必须携带经纬度");
            }
            riderOnlineService.goOnline(riderId, request.lng(), request.lat());
        } else {
            riderOnlineService.goOffline(riderId, request.reason());
        }
        return ApiResponse.ok();
    }

    @GetMapping("/orders/pending")
    public ApiResponse<List<PendingDispatchView>> pendingDispatches() {
        return ApiResponse.ok(riderDispatchService.pendingDispatches(currentRiderId()));
    }

    @GetMapping("/hall")
    public ApiResponse<List<HallOrderView>> hall(
            @RequestParam(required = false) BigDecimal lng,
            @RequestParam(required = false) BigDecimal lat,
            @RequestParam(required = false, defaultValue = "distance") String sortBy) {
        return ApiResponse.ok(riderDispatchService.hall(currentRiderId(), lng, lat, sortBy));
    }

    @PostMapping("/dispatch/{deliveryId}/accept")
    public ApiResponse<DeliveryActionView> accept(@PathVariable("deliveryId") Long deliveryId) {
        return ApiResponse.ok(riderDispatchService.accept(deliveryId, currentRiderId()));
    }

    @Idempotent(ttlSeconds = 5)
    @PostMapping("/dispatch/{deliveryId}/reject")
    public ApiResponse<Void> reject(@PathVariable("deliveryId") Long deliveryId,
                                    @Valid @RequestBody RejectDispatchRequest request) {
        riderDispatchService.reject(deliveryId, currentRiderId(), request.reason());
        return ApiResponse.ok();
    }

    @PostMapping("/deliveries/{id}/grab")
    public ApiResponse<DeliveryActionView> grab(@PathVariable("id") Long deliveryId) {
        return ApiResponse.ok(riderDispatchService.grab(deliveryId, currentRiderId()));
    }

    private Long currentRiderId() {
        return riderService.requireRiderIdByUserId(AuthContext.require().userId());
    }
}
