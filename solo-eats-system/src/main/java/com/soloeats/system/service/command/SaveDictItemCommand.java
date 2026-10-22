package com.soloeats.system.service.command;

/**
 * 字典项新增/修改入参。isDefault=1 时会自动清除同字典类型下的其他默认项。
 */
public record SaveDictItemCommand(
        String dictType,
        String dictLabel,
        String dictValue,
        Integer dictSort,
        String cssClass,
        Integer isDefault,
        Integer status,
        String remark
) {
}
