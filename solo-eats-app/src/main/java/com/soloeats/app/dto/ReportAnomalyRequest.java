package com.soloeats.app.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 骑手异常上报。type：1超时 2联系不上用户 3地址错误 4餐品破损 5商家出餐慢 6用户拒收。
 */
public record ReportAnomalyRequest(
        @NotNull(message = "type 不能为空")
        @Min(value = 1, message = "type 取值 1~6")
        @Max(value = 6, message = "type 取值 1~6")
        Integer type,

        @Size(max = 255, message = "remark 长度不能超过 255")
        String remark,

        List<String> images
) {
}
