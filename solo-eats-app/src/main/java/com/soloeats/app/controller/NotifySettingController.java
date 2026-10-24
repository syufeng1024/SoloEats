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

    @GetMapping
    public ApiResponse<List<NotifySettingView>> get() {
        UserPrincipal principal = CurrentReceiver.principal();
        return ApiResponse.ok(notifySettingService.get(
                CurrentReceiver.receiverType(principal), principal.userId()));
    }

    @PutMapping
    public ApiResponse<NotifySettingView> update(@Valid @RequestBody UpdateNotifySettingRequest request) {
        UserPrincipal principal = CurrentReceiver.principal();
        return ApiResponse.ok(notifySettingService.update(
                CurrentReceiver.receiverType(principal),
                principal.userId(),
                request.notifyType(),
                Boolean.TRUE.equals(request.enabled())));
    }
}
