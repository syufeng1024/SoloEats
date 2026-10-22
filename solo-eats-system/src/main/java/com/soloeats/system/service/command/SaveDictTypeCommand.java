package com.soloeats.system.service.command;

/**
 * 字典类型新增/修改入参。修改时 dictType 不可变更，传值将被忽略。
 */
public record SaveDictTypeCommand(
        String dictType,
        String dictName,
        Integer status,
        String remark
) {
}
