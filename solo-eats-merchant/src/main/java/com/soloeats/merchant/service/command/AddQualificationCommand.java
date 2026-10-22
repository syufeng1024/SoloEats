package com.soloeats.merchant.service.command;

/**
 * 新增/覆盖某类资质。同一商家同一 qualType 只保留最新文件。
 */
public record AddQualificationCommand(
        Integer qualType,
        Long fileId
) {
}
