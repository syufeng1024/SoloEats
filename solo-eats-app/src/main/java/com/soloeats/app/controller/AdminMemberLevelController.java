package com.soloeats.app.controller;

import com.soloeats.app.dto.MemberLevelSaveRequest;
import com.soloeats.common.api.ApiResponse;
import com.soloeats.common.auth.RequirePermission;
import com.soloeats.common.auth.RequireRole;
import com.soloeats.common.auth.Roles;
import com.soloeats.common.idempotency.Idempotent;
import com.soloeats.common.log.OperationLog;
import com.soloeats.marketing.service.MemberLevelService;
import com.soloeats.marketing.service.view.MemberLevelView;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 平台会员等级后台管理。权限码由 V33 种入 t_sys_menu（id 159/160），
 * 挂在「营销中心」parent_id=6 下。
 */
@RequireRole(Roles.ADMIN)
@RestController
@RequestMapping("/api/v1/admin/member-levels")
@RequiredArgsConstructor
public class AdminMemberLevelController {

    private final MemberLevelService memberLevelService;

    @RequirePermission("marketing:member:list")
    @GetMapping
    public ApiResponse<List<MemberLevelView>> list() {
        return ApiResponse.ok(memberLevelService.listLevels());
    }

    @RequirePermission("marketing:member:update")
    @Idempotent(ttlSeconds = 5)
    @OperationLog(module = "marketing", action = "createMemberLevel")
    @PostMapping
    public ApiResponse<Long> create(@Valid @RequestBody MemberLevelSaveRequest request) {
        return ApiResponse.ok(memberLevelService.createLevel(request.toCommand()));
    }

    @RequirePermission("marketing:member:update")
    @OperationLog(module = "marketing", action = "updateMemberLevel")
    @PutMapping("/{id}")
    public ApiResponse<Void> update(@PathVariable("id") Long id,
                                    @Valid @RequestBody MemberLevelSaveRequest request) {
        memberLevelService.updateLevel(id, request.toCommand());
        return ApiResponse.ok();
    }

    /**
     * 手动触发季度重算。返回实际发生变更的人数，不是全量用户数。
     */
    @RequirePermission("marketing:member:update")
    @Idempotent(ttlSeconds = 5)
    @OperationLog(module = "marketing", action = "recalcMemberLevel")
    @PostMapping("/recalc")
    public ApiResponse<Integer> recalc() {
        return ApiResponse.ok(memberLevelService.recalcAll());
    }
}
