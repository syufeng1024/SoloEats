package com.soloeats.app.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 套餐新增/修改请求。price 单位为分；menuIds 传 null 表示不限制菜单范围。
 */
public record SavePackageRequest(
        @NotBlank(message = "packageCode 不能为空")
        @Size(max = 64, message = "packageCode 长度不能超过 64")
        String packageCode,

        @NotBlank(message = "packageName 不能为空")
        @Size(max = 64, message = "packageName 长度不能超过 64")
        String packageName,

        List<Long> menuIds,

        @Min(value = 0, message = "maxShop 不能为负数")
        Integer maxShop,

        @Min(value = 0, message = "maxUser 不能为负数")
        Integer maxUser,

        @Min(value = 0, message = "price 不能为负数")
        Long price,

        @Min(value = 0, message = "validDays 不能为负数")
        Integer validDays,

        @Min(value = 0, message = "status 只能是 0启用 1停用")
        @Max(value = 1, message = "status 只能是 0启用 1停用")
        Integer status,

        @Size(max = 255, message = "remark 长度不能超过 255")
        String remark
) {
}
