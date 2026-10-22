package com.soloeats.app.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

/**
 * 租户新增/修改请求。修改时 tenantCode 不参与变更。
 */
public record SaveTenantRequest(
        @NotBlank(message = "tenantCode 不能为空")
        @Size(max = 64, message = "tenantCode 长度不能超过 64")
        String tenantCode,

        @NotBlank(message = "tenantName 不能为空")
        @Size(max = 128, message = "tenantName 长度不能超过 128")
        String tenantName,

        @NotNull(message = "tenantType 不能为空")
        @Min(value = 1, message = "tenantType 只能是 1平台 2商家")
        @Max(value = 2, message = "tenantType 只能是 1平台 2商家")
        Integer tenantType,

        Long merchantId,

        Long packageId,

        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        LocalDateTime expireAt,

        @Size(max = 64, message = "contactName 长度不能超过 64")
        String contactName,

        @Size(max = 32, message = "contactPhone 长度不能超过 32")
        String contactPhone
) {

    /** PUT /admin/tenants/{id}/status 的请求体；2 为派生状态，不允许人工写入。 */
    public record TenantStatusRequest(
            @NotNull(message = "status 不能为空")
            @Min(value = 0, message = "status 只能是 0启用 1停用")
            @Max(value = 1, message = "status 只能是 0启用 1停用")
            Integer status
    ) {
    }
}
