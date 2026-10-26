package com.soloeats.content.service.command;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.LocalDateTime;

/**
 * 首页楼层新增/编辑。
 *
 * <p>pageCode 为空时：新增默认 USER_HOME，编辑沿用楼层原页面。
 * config / audienceConfig 为 JSON 对象，服务层做白名单清洗。
 */
public record SaveFloorCommand(
        String pageCode,
        Integer floorType,
        String title,
        JsonNode config,
        Integer sort,
        LocalDateTime effectiveFrom,
        LocalDateTime effectiveTo,
        Integer audience,
        JsonNode audienceConfig,
        Integer status
) {
}
