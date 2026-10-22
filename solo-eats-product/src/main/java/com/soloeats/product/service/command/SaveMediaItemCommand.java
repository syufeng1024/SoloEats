package com.soloeats.product.service.command;

/**
 * 素材项只保存 t_file 引用，不复制文件元数据。
 */
public record SaveMediaItemCommand(
        Long fileId,
        Integer mediaType,
        Integer sort,
        Integer status
) {
}
