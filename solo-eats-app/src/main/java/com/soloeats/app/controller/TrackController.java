package com.soloeats.app.controller;

import com.soloeats.app.dto.TrackBatchRequest;
import com.soloeats.common.api.ApiResponse;
import com.soloeats.common.auth.AuthContext;
import com.soloeats.common.auth.Roles;
import com.soloeats.common.auth.UserPrincipal;
import com.soloeats.report.service.TrackService;
import com.soloeats.report.service.command.TrackEventCommand;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * C 端埋点上报。白名单接口：无 token 可上报匿名事件；
 * 带 C_USER token 时写入 t_member_user.id，其他角色 token 不关联用户。
 */
@RestController
@RequestMapping("/api/v1/c/track")
@RequiredArgsConstructor
public class TrackController {

    private final TrackService trackService;

    @PostMapping
    public ApiResponse<Integer> track(@Valid @RequestBody TrackBatchRequest request) {
        UserPrincipal principal = AuthContext.current();
        Long userId = principal != null && principal.hasRole(Roles.C_USER)
                ? principal.userId() : null;

        List<TrackEventCommand> commands = request.events().stream()
                .map(item -> new TrackEventCommand(
                        item.eventType(),
                        item.deviceId(),
                        item.bizType(),
                        item.bizId(),
                        item.source(),
                        item.extra()))
                .toList();
        return ApiResponse.ok(trackService.track(userId, commands));
    }
}
