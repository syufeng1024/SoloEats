package com.soloeats.app.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/**
 * 菜单新增/修改共用请求。修改时字段为 null 表示不改。
 */
public record SaveMenuRequest(
        Long parentId,

        @Size(max = 64, message = "menuName 长度不能超过 64")
        String menuName,

        @Min(value = 1, message = "menuType 只能是 1目录 2菜单 3按钮")
        @Max(value = 3, message = "menuType 只能是 1目录 2菜单 3按钮")
        Integer menuType,

        @Size(max = 200, message = "path 长度不能超过 200")
        String path,

        @Size(max = 200, message = "component 长度不能超过 200")
        String component,

        @Size(max = 128, message = "perms 长度不能超过 128")
        String perms,

        @Size(max = 64, message = "icon 长度不能超过 64")
        String icon,

        Integer sort,

        @Min(value = 0, message = "visible 只能是 0显示 1隐藏")
        @Max(value = 1, message = "visible 只能是 0显示 1隐藏")
        Integer visible,

        @Min(value = 0, message = "status 只能是 0启用 1停用")
        @Max(value = 1, message = "status 只能是 0启用 1停用")
        Integer status
) {
}
