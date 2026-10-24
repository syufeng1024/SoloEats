package com.soloeats.app.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 改派请求。riderId 为原骑手，用于校验释放对象；释放后配送单回到待派单。
 */
public record ReassignDeliveryRequest(
        @NotNull(message = "riderId 不能为空")
        Long riderId,

        @Size(max = 128, message = "reason 长度不能超过 128")
        String reason
) {
}
