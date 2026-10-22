package com.soloeats.app.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 字典类型与字典项的请求体。修改时编码类字段不参与变更。
 */
public record SaveDictRequest(
        @NotBlank(message = "dictType 不能为空")
        @Size(max = 64, message = "dictType 长度不能超过 64")
        String dictType,

        @NotBlank(message = "dictName 不能为空")
        @Size(max = 64, message = "dictName 长度不能超过 64")
        String dictName,

        @Min(value = 0, message = "status 只能是 0启用 1停用")
        @Max(value = 1, message = "status 只能是 0启用 1停用")
        Integer status,

        @Size(max = 255, message = "remark 长度不能超过 255")
        String remark
) {

    /** 字典项新增/修改请求。 */
    public record DictItemRequest(
            @Size(max = 64, message = "dictType 长度不能超过 64")
            String dictType,

            @NotBlank(message = "dictLabel 不能为空")
            @Size(max = 128, message = "dictLabel 长度不能超过 128")
            String dictLabel,

            @NotBlank(message = "dictValue 不能为空")
            @Size(max = 128, message = "dictValue 长度不能超过 128")
            String dictValue,

            Integer dictSort,

            @Size(max = 64, message = "cssClass 长度不能超过 64")
            String cssClass,

            @Min(value = 0, message = "isDefault 只能是 0 或 1")
            @Max(value = 1, message = "isDefault 只能是 0 或 1")
            Integer isDefault,

            @Min(value = 0, message = "status 只能是 0启用 1停用")
            @Max(value = 1, message = "status 只能是 0启用 1停用")
            Integer status,

            @Size(max = 255, message = "remark 长度不能超过 255")
            String remark
    ) {
    }
}
