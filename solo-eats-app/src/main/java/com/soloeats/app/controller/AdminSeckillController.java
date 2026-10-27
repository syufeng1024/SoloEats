package com.soloeats.app.controller;

import com.soloeats.app.dto.SeckillActivitySaveRequest;
import com.soloeats.common.api.ApiResponse;
import com.soloeats.common.api.PageResult;
import com.soloeats.common.auth.RequirePermission;
import com.soloeats.common.auth.RequireRole;
import com.soloeats.common.auth.Roles;
import com.soloeats.common.idempotency.Idempotent;
import com.soloeats.common.log.OperationLog;
import com.soloeats.marketing.service.SeckillService;
import com.soloeats.marketing.service.view.SeckillActivityView;
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

/**
 * 平台秒杀场次后台管理。权限码由 V29 种入 t_sys_menu（id 148/149），
 * 挂在「营销中心」parent_id=6 下。
 */
@RequireRole(Roles.ADMIN)
@RestController
@RequestMapping("/api/v1/admin/seckill/activities")
@RequiredArgsConstructor
public class AdminSeckillController {

    private final SeckillService seckillService;

    @RequirePermission("marketing:seckill:list")
    @GetMapping
    public ApiResponse<PageResult<SeckillActivityView>> list(
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "20") long pageSize) {
        return ApiResponse.ok(seckillService.pageActivities(status, pageNum, pageSize));
    }

    @RequirePermission("marketing:seckill:list")
    @GetMapping("/{id}")
    public ApiResponse<SeckillActivityView> detail(@PathVariable("id") Long id) {
        return ApiResponse.ok(seckillService.detailActivity(id));
    }

    @RequirePermission("marketing:seckill:update")
    @Idempotent(ttlSeconds = 5)
    @OperationLog(module = "marketing", action = "createSeckillActivity")
    @PostMapping
    public ApiResponse<Long> create(@Valid @RequestBody SeckillActivitySaveRequest request) {
        return ApiResponse.ok(seckillService.createActivity(request.toCommand()));
    }

    @RequirePermission("marketing:seckill:update")
    @OperationLog(module = "marketing", action = "updateSeckillActivity")
    @PutMapping("/{id}")
    public ApiResponse<Void> update(@PathVariable("id") Long id,
                                    @Valid @RequestBody SeckillActivitySaveRequest request) {
        seckillService.updateActivity(id, request.toCommand());
        return ApiResponse.ok();
    }
}
