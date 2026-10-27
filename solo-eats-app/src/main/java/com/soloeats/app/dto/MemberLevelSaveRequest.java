package com.soloeats.app.dto;

import com.soloeats.marketing.service.command.MemberLevelCommand;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 后台会员等级新建/编辑请求。
 *
 * <p>跨字段校验（编码唯一、权益 JSON 合法性）落在
 * {@code MemberLevelServiceImpl}，这里只做字段级防呆。
 */
public record MemberLevelSaveRequest(
        @NotBlank(message = "levelCode 不能为空")
        @Size(max = 16, message = "levelCode 不能超过 16 个字符")
        String levelCode,

        @NotBlank(message = "levelName 不能为空")
        @Size(max = 32, message = "levelName 不能超过 32 个字符")
        String levelName,

        @NotNull(message = "levelNo 不能为空")
        @Min(value = 1, message = "levelNo 只能是 1~6")
        @Max(value = 6, message = "levelNo 只能是 1~6")
        Integer levelNo,

        @NotNull(message = "growthThreshold 不能为空")
        @Min(value = 0, message = "growthThreshold 不能为负")
        Integer growthThreshold,

        @NotNull(message = "star 不能为空")
        @Min(value = 1, message = "star 只能是 1~3")
        @Max(value = 3, message = "star 只能是 1~3")
        Integer star,

        String benefitsJson,

        @Size(max = 255, message = "iconUrl 不能超过 255 个字符")
        String iconUrl,

        Integer sort,

        Integer status
) {

    public MemberLevelCommand toCommand() {
        return new MemberLevelCommand(
                levelCode, levelName, levelNo, growthThreshold, star,
                benefitsJson, iconUrl, sort, status);
    }
}
