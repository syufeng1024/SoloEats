package com.soloeats.merchant.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.soloeats.common.exception.BizException;
import com.soloeats.common.exception.ErrorCode;
import com.soloeats.common.file.FileStoragePort;
import com.soloeats.common.file.FileTypeValidator;
import com.soloeats.common.file.MinioProperties;
import com.soloeats.merchant.domain.entity.FileRecord;
import com.soloeats.merchant.mapper.FileRecordMapper;
import com.soloeats.merchant.service.FileService;
import com.soloeats.merchant.service.view.FileView;
import com.soloeats.merchant.service.view.PresignView;
import com.soloeats.merchant.support.FileActor;
import com.soloeats.merchant.support.FileObjectRemover;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.UUID;

/**
 * 文件服务实现。对象存储调用全部在事务之外；t_file 只做单条插入/更新。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileServiceImpl implements FileService {

    private static final Duration PRESIGN_TTL = Duration.ofMinutes(10);
    private static final int MAGIC_HEAD_BYTES = 12;
    private static final DateTimeFormatter MONTH_FORMATTER = DateTimeFormatter.ofPattern("yyyyMM");
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    private final FileStoragePort fileStoragePort;
    private final FileRecordMapper fileRecordMapper;
    private final FileTypeValidator validator;
    private final MinioProperties minioProperties;
    private final FileObjectRemover fileObjectRemover;

    @Override
    public FileView upload(MultipartFile file, String bizType, FileActor actor) {
        requireActor(actor);
        if (file == null || file.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "file 不能为空");
        }
        validator.validateBizType(bizType);
        String originalName = validator.sanitizeFileName(file.getOriginalFilename());
        String contentType = validator.resolveMime(file.getContentType(), originalName);
        byte[] head = readHead(file);
        validator.validateUpload(originalName, contentType, file.getSize(), head);
        String extension = validator.extensionFor(contentType, originalName);
        String objectKey = buildObjectKey(bizType, extension);

        uploadObject(objectKey, file, contentType);

        FileRecord record = new FileRecord();
        record.setFileKey(objectKey);
        record.setBucket(minioProperties.getBucket());
        record.setOriginalName(originalName);
        record.setContentType(contentType);
        record.setSizeBytes(file.getSize());
        record.setBizType(bizType.trim());
        record.setBizId(actor.isApply() ? actor.applyId() : null);
        record.setUploaderId(actor.userId());
        record.setUploaderType(actor.uploaderType());
        record.setStatus(0);
        fileRecordMapper.insert(record);

        // 代理地址依赖自增主键，落库后回填；url 只暴露后端入口，不暴露 MinIO
        String url = "/api/v1/files/" + record.getId() + "/raw";
        fileRecordMapper.update(null, new LambdaUpdateWrapper<FileRecord>()
                .eq(FileRecord::getId, record.getId())
                .set(FileRecord::getUrl, url));
        record.setUrl(url);

        log.info("file uploaded fileId={} bizType={} size={} uploaderType={}",
                record.getId(), bizType, file.getSize(), actor.uploaderType());
        return new FileView(record.getId(), url, objectKey,
                file.getSize(), contentType, originalName);
    }

    @Override
    public PresignView presign(String fileName, String contentType, String bizType, FileActor actor) {
        requireActor(actor);
        validator.validateBizType(bizType);
        String originalName = validator.sanitizeFileName(fileName);
        String normalizedMime = validator.resolveMime(contentType, originalName);
        validator.validatePresign(originalName, normalizedMime);
        String extension = validator.extensionFor(normalizedMime, originalName);
        String objectKey = buildObjectKey(bizType, extension);
        String uploadUrl = fileStoragePort.presign(objectKey, normalizedMime, PRESIGN_TTL);
        return new PresignView(uploadUrl, objectKey, (int) PRESIGN_TTL.toSeconds());
    }

    @Override
    public void delete(Long fileId, FileActor actor) {
        requireActor(actor);
        FileRecord record = requireActiveRecord(fileId);
        if (actor.isApply()) {
            throw new BizException(ErrorCode.AUTH_FORBIDDEN);
        }
        if (!actor.admin() && !Objects.equals(record.getUploaderId(), actor.userId())) {
            throw new BizException(ErrorCode.AUTH_FORBIDDEN);
        }
        // 先逻辑删表，再异步删对象；对象删除失败只告警
        fileRecordMapper.update(null, new LambdaUpdateWrapper<FileRecord>()
                .eq(FileRecord::getId, record.getId())
                .eq(FileRecord::getStatus, 0)
                .set(FileRecord::getStatus, 1));
        fileObjectRemover.removeQuietly(record.getFileKey());
        log.info("file logically deleted fileId={}", record.getId());
    }

    @Override
    public FileDownload download(Long fileId, FileActor actor) {
        requireActor(actor);
        FileRecord record = requireActiveRecord(fileId);
        assertCanRead(record, actor);
        InputStream stream = fileStoragePort.download(record.getFileKey());
        return new FileDownload(stream, record.getContentType(),
                record.getOriginalName(),
                record.getSizeBytes() == null ? 0L : record.getSizeBytes());
    }

    private void assertCanRead(FileRecord record, FileActor actor) {
        if (actor.admin()) {
            return;
        }
        if (actor.isApply()) {
            if ("APPLY".equals(record.getUploaderType())
                    && Objects.equals(record.getBizId(), actor.applyId())) {
                return;
            }
            throw new BizException(ErrorCode.AUTH_FORBIDDEN);
        }
        if (Objects.equals(record.getUploaderId(), actor.userId())) {
            return;
        }
        // 商家可查看自己名下的资质附件（申请阶段由 APPLY 上传，biz_id=merchantId）
        if ("MERCHANT_QUAL".equals(record.getBizType())
                && Objects.equals(record.getBizId(), actor.merchantId())) {
            return;
        }
        throw new BizException(ErrorCode.AUTH_FORBIDDEN);
    }

    private FileRecord requireActiveRecord(Long fileId) {
        if (fileId == null || fileId <= 0L) {
            throw new BizException(ErrorCode.PARAM_INVALID, "fileId 必须是正整数");
        }
        FileRecord record = fileRecordMapper.selectById(fileId);
        if (record == null || !Objects.equals(record.getStatus(), 0)) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND, "文件不存在");
        }
        return record;
    }

    private void uploadObject(String objectKey, MultipartFile file, String contentType) {
        try (InputStream inputStream = file.getInputStream()) {
            fileStoragePort.upload(objectKey, inputStream, file.getSize(), contentType);
        } catch (IOException ex) {
            log.error("file read failed objectKey={} reason={}", objectKey, ex.getMessage());
            throw new BizException(ErrorCode.FILE_UPLOAD_FAILED);
        }
    }

    private byte[] readHead(MultipartFile file) {
        try (InputStream inputStream = file.getInputStream()) {
            return inputStream.readNBytes(MAGIC_HEAD_BYTES);
        } catch (IOException ex) {
            throw new BizException(ErrorCode.FILE_UPLOAD_FAILED, "读取文件失败");
        }
    }

    private String buildObjectKey(String bizType, String extension) {
        String month = ZonedDateTime.now(ZONE).format(MONTH_FORMATTER);
        String uuid = UUID.randomUUID().toString().replace("-", "");
        return bizType.trim() + "/" + month + "/" + uuid + "." + extension;
    }

    private void requireActor(FileActor actor) {
        if (actor == null) {
            throw new BizException(ErrorCode.AUTH_UNAUTHORIZED);
        }
        // 旧契约 mock 登录可能只有 userId（如 ADMIN userId=0），不能用 username 判空
        if (!actor.isApply() && actor.userId() == null && !StringUtils.hasText(actor.username())) {
            throw new BizException(ErrorCode.AUTH_UNAUTHORIZED);
        }
    }
}
