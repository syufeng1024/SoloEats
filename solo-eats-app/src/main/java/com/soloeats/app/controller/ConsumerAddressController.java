package com.soloeats.app.controller;

import com.soloeats.app.dto.SaveAddressRequest;
import com.soloeats.common.api.ApiResponse;
import com.soloeats.common.auth.AuthContext;
import com.soloeats.common.auth.RequireRole;
import com.soloeats.common.auth.Roles;
import com.soloeats.member.service.MemberAddressService;
import com.soloeats.member.service.command.SaveAddressCommand;
import com.soloeats.member.service.view.AddressView;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * C 端收货地址簿。所有按 id 的操作在 service 层校验归属，非本人返回 10004。
 */
@RestController
@RequestMapping("/api/v1/c/addresses")
@RequireRole(Roles.C_USER)
@RequiredArgsConstructor
public class ConsumerAddressController {

    private final MemberAddressService memberAddressService;

    @GetMapping
    public ApiResponse<List<AddressView>> list() {
        return ApiResponse.ok(memberAddressService.list(currentUserId()));
    }

    @PostMapping
    public ApiResponse<AddressView> create(@Valid @RequestBody SaveAddressRequest request) {
        return ApiResponse.ok(memberAddressService.create(currentUserId(), toCommand(request)));
    }

    @PutMapping("/{id}")
    public ApiResponse<AddressView> update(@PathVariable("id") Long id,
                                           @Valid @RequestBody SaveAddressRequest request) {
        return ApiResponse.ok(memberAddressService.update(currentUserId(), id, toCommand(request)));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable("id") Long id) {
        memberAddressService.delete(currentUserId(), id);
        return ApiResponse.ok();
    }

    @PutMapping("/{id}/default")
    public ApiResponse<Void> setDefault(@PathVariable("id") Long id) {
        memberAddressService.setDefault(currentUserId(), id);
        return ApiResponse.ok();
    }

    private SaveAddressCommand toCommand(SaveAddressRequest request) {
        return new SaveAddressCommand(
                request.receiverName(),
                request.receiverPhone(),
                request.province(),
                request.city(),
                request.district(),
                request.address(),
                request.houseNumber(),
                request.lng(),
                request.lat(),
                request.tag(),
                request.isDefault());
    }

    /** userId 只取令牌，不接受请求体传入。 */
    private Long currentUserId() {
        return AuthContext.require().userId();
    }
}
