package com.soloeats.app.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

/**
 * 首页楼层新增/编辑。config 由服务层清洗并递归校验 categoryId/shopId/spuId 引用。
 */
public record SaveHomeFloorRequest(
        @Size(max = 32, message = "pageCode 长度不能超过 32")
        String pageCode,

        @NotNull(message = "floorType 不能为空")
        @Min(value = 1, message = "floorType 只能是 1~7")
        @Max(value = 7, message = "floorType 只能是 1~7")
        Integer floorType,

        @NotBlank(message = "title 不能为空")
        @Size(max = 64, message = "title 长度不能超过 64")
        String title,

        JsonNode config,

        Integer sort,

        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        LocalDateTime effectiveFrom,

        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        LocalDateTime effectiveTo,

        @Min(value = 0, message = "audience 只能是 0~3")
        @Max(value = 3, message = "audience 只能是 0~3")
        Integer audience,

        JsonNode audienceConfig,

        @Min(value = 0, message = "status 只能是 0停用 / 1启用")
        @Max(value = 1, message = "status 只能是 0停用 / 1启用")
        Integer status
) {
}
