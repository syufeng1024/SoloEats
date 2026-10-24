package com.soloeats.app.controller;

import com.soloeats.app.dto.UpdateNotifySettingRequest;
import com.soloeats.app.security.CurrentReceiver;
import com.soloeats.common.api.ApiResponse;
import com.soloeats.common.auth.UserPrincipal;
import com.soloeats.message.service.NotifySettingService;
import com.soloeats.message.service.view.NotifySettingView;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/notify/settings")
@RequiredArgsConstructor
public class NotifySettingController {

    private final NotifySettingService notifySettingService;
    private final CurrentReceiver currentReceiver;

    @GetMapping
    public ApiResponse<List<NotifySettingView>> get() {
        UserPrincipal principal = currentReceiver.principal();
        return ApiResponse.ok(notifySettingService.get(
                currentReceiver.receiverType(principal), currentReceiver.receiverId(principal)));
    }

    @PutMapping
    public ApiResponse<NotifySettingView> update(@Valid @RequestBody UpdateNotifySettingRequest request) {
        UserPrincipal principal = currentReceiver.principal();
        return ApiResponse.ok(notifySettingService.update(
                currentReceiver.receiverType(principal),
                currentReceiver.receiverId(principal),
                request.notifyType(),
                Boolean.TRUE.equals(request.enabled())));
    }
}
