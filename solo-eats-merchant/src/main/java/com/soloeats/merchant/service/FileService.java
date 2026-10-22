package com.soloeats.merchant.service;

import com.soloeats.merchant.service.view.FileView;
import com.soloeats.merchant.service.view.PresignView;
import com.soloeats.merchant.support.FileActor;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;

/**
 * 公共文件服务。上传/下载不包事务：对象存储是外部调用，事务只包 t_file 落库。
 */
public interface FileService {

    FileView upload(MultipartFile file, String bizType, FileActor actor);

    PresignView presign(String fileName, String contentType, String bizType, FileActor actor);

    void delete(Long fileId, FileActor actor);

    FileDownload download(Long fileId, FileActor actor);

    /**
     * 调用方必须关闭 {@code stream}。
     */
    record FileDownload(InputStream stream, String contentType, String originalName, long sizeBytes) {
    }
}
