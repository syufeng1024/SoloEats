package com.soloeats.app.controller;

import com.soloeats.app.dto.ReassignDeliveryRequest;
import com.soloeats.common.api.ApiResponse;
import com.soloeats.common.api.PageResult;
import com.soloeats.common.auth.AuthContext;
import com.soloeats.common.auth.RequirePermission;
import com.soloeats.common.auth.RequireRole;
import com.soloeats.common.auth.Roles;
import com.soloeats.common.log.OperationLog;
import com.soloeats.delivery.service.DeliveryAdminService;
import com.soloeats.delivery.service.DeliveryTrackService;
import com.soloeats.delivery.service.command.DeliveryQuery;
import com.soloeats.delivery.service.view.DeliveryOrderView;
import com.soloeats.delivery.service.view.TrackPointView;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * 平台侧配送单管理：列表、轨迹回放、改派。
 * 权限码由 V15 种入 t_sys_menu（id 98~100）并授予超管。
 */
@RequireRole(Roles.ADMIN)
@RestController
@RequestMapping("/api/v1/admin/deliveries")
@RequiredArgsConstructor
public class AdminDeliveryController {

    private final DeliveryAdminService deliveryAdminService;
    private final DeliveryTrackService deliveryTrackService;

    @RequirePermission("delivery:order:list")
    @GetMapping
    public ApiResponse<PageResult<DeliveryOrderView>> list(
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) Long shopId,
            @RequestParam(required = false) Long riderId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "20") long pageSize) {
        return ApiResponse.ok(deliveryAdminService.page(
                new DeliveryQuery(status, shopId, riderId, dateFrom, dateTo),
                pageNum, pageSize));
    }

    @RequirePermission("delivery:track:view")
    @GetMapping("/{id}/track")
    public ApiResponse<List<TrackPointView>> track(@PathVariable("id") Long id) {
        return ApiResponse.ok(deliveryTrackService.replay(id));
    }

    @RequirePermission("delivery:order:dispatch")
    @OperationLog(module = "delivery", action = "reassign")
    @PostMapping("/{id}/reassign")
    public ApiResponse<Void> reassign(@PathVariable("id") Long id,
                                      @Valid @RequestBody ReassignDeliveryRequest request) {
        deliveryAdminService.reassign(id, request.riderId(), request.reason(),
                AuthContext.require().userId());
        return ApiResponse.ok();
    }
}
