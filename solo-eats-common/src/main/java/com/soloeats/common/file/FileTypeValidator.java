package com.soloeats.common.file;

import com.soloeats.common.exception.BizException;
import com.soloeats.common.exception.ErrorCode;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 上传白名单校验：MIME、后缀、大小与文件头魔数。四者必须同时通过。
 *
 * <p>只允许 jpg / png / webp / pdf；后缀与 MIME 不匹配、文件头不符一律拒绝，
 * 防止把可执行文件或 HTML 伪装成图片写入对象存储后被浏览器解析。
 */
@Component
public class FileTypeValidator {

    public static final long MAX_SIZE_BYTES = 10L * 1024 * 1024;

    /** bizType → 是否允许。锁定取值可避免 objectKey 前缀被注入 {@code ../}。 */
    private static final Set<String> ALLOWED_BIZ_TYPES = Set.of(
            "MERCHANT_QUAL", "PRODUCT", "AVATAR", "TICKET", "CONTENT", "RECEIPT");

    private static final Map<String, Set<String>> EXTENSIONS_BY_MIME = Map.of(
            "image/jpeg", Set.of("jpg", "jpeg"),
            "image/png", Set.of("png"),
            "image/webp", Set.of("webp"),
            "application/pdf", Set.of("pdf")
    );

    private static final Map<String, String> MIME_BY_EXTENSION = Map.of(
            "jpg", "image/jpeg",
            "jpeg", "image/jpeg",
            "png", "image/png",
            "webp", "image/webp",
            "pdf", "application/pdf"
    );

    public void validateBizType(String bizType) {
        if (!StringUtils.hasText(bizType) || !ALLOWED_BIZ_TYPES.contains(bizType.trim())) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "bizType 只能是 MERCHANT_QUAL/PRODUCT/AVATAR/TICKET/CONTENT/RECEIPT");
        }
    }

    /**
     * 预签名场景拿不到文件字节，只校验 MIME 与后缀；对象内容由下游使用方抽查。
     */
    public void validatePresign(String fileName, String contentType) {
        String extension = extensionOf(fileName);
        assertMimeAndExtension(resolveMime(contentType, fileName), extension);
    }

    /**
     * 服务端中转上传：在 MIME/后缀之外再校验文件头。
     */
    public void validateUpload(String fileName, String contentType, long size, byte[] head) {
        if (size <= 0L) {
            throw new BizException(ErrorCode.PARAM_INVALID, "文件不能为空");
        }
        if (size > MAX_SIZE_BYTES) {
            throw new BizException(ErrorCode.PARAM_INVALID, "单个文件不能超过 10MB");
        }
        String extension = extensionOf(fileName);
        String normalizedMime = resolveMime(contentType, fileName);
        assertMimeAndExtension(normalizedMime, extension);
        if (!matchesMagic(normalizedMime, head)) {
            throw new BizException(ErrorCode.FILE_TYPE_NOT_ALLOWED, "文件内容与声明的类型不一致");
        }
    }

    /**
     * curl / 部分客户端对 multipart 文件统一送 application/octet-stream。
     * 此时按扩展名推断 MIME，再走白名单；扩展名不在白名单仍拒绝。
     */
    public String resolveMime(String contentType, String fileName) {
        String extension = extensionOf(fileName);
        String mime = StringUtils.hasText(contentType) ? normalizeMime(contentType) : null;
        if (mime == null
                || "application/octet-stream".equals(mime)
                || "binary/octet-stream".equals(mime)) {
            mime = MIME_BY_EXTENSION.get(extension);
        }
        if (mime == null) {
            throw new BizException(ErrorCode.FILE_TYPE_NOT_ALLOWED,
                    "只支持 image/jpeg、image/png、image/webp、application/pdf");
        }
        return mime;
    }

    public String normalizeMime(String contentType) {
        if (!StringUtils.hasText(contentType)) {
            throw new BizException(ErrorCode.FILE_TYPE_NOT_ALLOWED, "contentType 不能为空");
        }
        String mime = contentType.trim().toLowerCase(Locale.ROOT);
        if ("image/jpg".equals(mime)) {
            return "image/jpeg";
        }
        return mime;
    }

    /**
     * 去掉客户端可能带入的目录部分，避免 {@code ../} 路径穿越；空名按非法处理。
     */
    public String sanitizeFileName(String originalName) {
        if (!StringUtils.hasText(originalName)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "文件名不能为空");
        }
        String normalized = originalName.replace('\\', '/');
        int slash = normalized.lastIndexOf('/');
        String name = slash >= 0 ? normalized.substring(slash + 1) : normalized;
        name = name.trim();
        if (name.isEmpty() || ".".equals(name) || "..".equals(name) || name.contains("..")) {
            throw new BizException(ErrorCode.PARAM_INVALID, "文件名非法");
        }
        if (name.length() > 200) {
            throw new BizException(ErrorCode.PARAM_INVALID, "文件名不能超过 200 字符");
        }
        return name;
    }

    public String extensionOf(String fileName) {
        String safeName = sanitizeFileName(fileName);
        int dot = safeName.lastIndexOf('.');
        if (dot < 0 || dot == safeName.length() - 1) {
            throw new BizException(ErrorCode.FILE_TYPE_NOT_ALLOWED, "文件缺少扩展名");
        }
        return safeName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    /** 优先按 MIME 决定扩展名；MIME 已通过白名单校验。 */
    public String extensionFor(String contentType, String fileName) {
        String extension = extensionOf(fileName);
        String mime = normalizeMime(contentType);
        Set<String> allowed = EXTENSIONS_BY_MIME.get(mime);
        if (allowed == null || !allowed.contains(extension)) {
            throw new BizException(ErrorCode.FILE_TYPE_NOT_ALLOWED, "文件扩展名与 MIME 不一致");
        }
        return extension;
    }

    private void assertMimeAndExtension(String mime, String extension) {
        Set<String> allowedExtensions = EXTENSIONS_BY_MIME.get(mime);
        if (allowedExtensions == null) {
            throw new BizException(ErrorCode.FILE_TYPE_NOT_ALLOWED,
                    "只支持 image/jpeg、image/png、image/webp、application/pdf");
        }
        if (!allowedExtensions.contains(extension)) {
            throw new BizException(ErrorCode.FILE_TYPE_NOT_ALLOWED, "文件扩展名与 MIME 不一致");
        }
    }

    private boolean matchesMagic(String mime, byte[] head) {
        if (head == null || head.length < 4) {
            return false;
        }
        return switch (mime) {
            case "image/jpeg" -> (head[0] & 0xFF) == 0xFF
                    && (head[1] & 0xFF) == 0xD8
                    && (head[2] & 0xFF) == 0xFF;
            case "image/png" -> (head[0] & 0xFF) == 0x89
                    && head[1] == 'P' && head[2] == 'N' && head[3] == 'G';
            case "image/webp" -> head.length >= 12
                    && head[0] == 'R' && head[1] == 'I' && head[2] == 'F' && head[3] == 'F'
                    && head[8] == 'W' && head[9] == 'E' && head[10] == 'B' && head[11] == 'P';
            case "application/pdf" -> head[0] == '%' && head[1] == 'P'
                    && head[2] == 'D' && head[3] == 'F';
            default -> false;
        };
    }
}
