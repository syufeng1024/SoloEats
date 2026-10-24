package com.soloeats.app.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Map;

/**
 * 后台定向发送：一次请求最多 500 个接收者，避免单请求批量落库过大。
 */
public record SendSiteMessageRequest(
        @NotNull(message = "接收者类型不能为空") Integer receiverType,
        @NotEmpty(message = "接收者不能为空")
        @Size(max = 500, message = "单次最多发送 500 个接收者") List<@NotNull Long> receiverIds,
        @NotBlank(message = "模板编码不能为空")
        @Size(max = 64, message = "模板编码最长 64 个字符") String templateCode,
        Map<String, String> params
) {
}
