package com.soloeats.app.controller;

import com.soloeats.app.dto.FilePresignRequest;
import com.soloeats.common.api.ApiResponse;
import com.soloeats.common.auth.AuthContext;
import com.soloeats.common.auth.Roles;
import com.soloeats.common.auth.UserPrincipal;
import com.soloeats.common.exception.BizException;
import com.soloeats.common.exception.ErrorCode;
import com.soloeats.merchant.service.FileService;
import com.soloeats.merchant.service.view.FileView;
import com.soloeats.merchant.service.view.PresignView;
import com.soloeats.merchant.support.ApplyTokenService;
import com.soloeats.merchant.support.FileActor;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;

/**
 * 全站公共文件入口。
 *
 * <p>鉴权二选一：登录用户 token 或入驻申请 applyToken（头部 X-Apply-Token，
 * 兼容 Authorization: Bearer &lt;applyToken&gt;）。申请阶段没有账号，必须放行 applyToken，
 * 否则「上传资质 → 凭 fileId 绑定申请」链路无法完成。
 */
@RestController
@RequestMapping("/api/v1/files")
@RequiredArgsConstructor
public class FileController {

    private static final String APPLY_TOKEN_HEADER = "X-Apply-Token";

    private final FileService fileService;
    private final ApplyTokenService applyTokenService;

    @PostMapping
    public ApiResponse<FileView> upload(@RequestParam("file") MultipartFile file,
                                        @RequestParam("bizType") String bizType,
                                        HttpServletRequest request) {
        return ApiResponse.ok(fileService.upload(file, bizType, resolveActor(request, false)));
    }

    @PostMapping("/presign")
    public ApiResponse<PresignView> presign(@Valid @RequestBody FilePresignRequest request,
                                            HttpServletRequest httpRequest) {
        return ApiResponse.ok(fileService.presign(
                request.fileName(), request.contentType(), request.bizType(),
                resolveActor(httpRequest, false)));
    }

    @DeleteMapping("/{fileId}")
    public ApiResponse<Void> delete(@PathVariable Long fileId, HttpServletRequest request) {
        fileService.delete(fileId, resolveActor(request, false));
        return ApiResponse.ok();
    }

    /**
     * 后端代理读取，避免暴露 MinIO 端口。浏览器 img 标签无法带请求头，
     * 因此额外接受 {@code ?token=} 查询参数；该 URL 不写入日志。
     */
    @GetMapping("/{fileId}/raw")
    public ResponseEntity<InputStreamResource> raw(@PathVariable Long fileId,
                                                   HttpServletRequest request) {
        FileService.FileDownload download = fileService.download(fileId,
                resolveActor(request, true));
        String fileName = StringUtils.hasText(download.originalName())
                ? download.originalName() : "file";
        ContentDisposition disposition = ContentDisposition.inline()
                .filename(fileName, StandardCharsets.UTF_8)
                .build();

        ResponseEntity.BodyBuilder builder = ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(download.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .cacheControl(CacheControl.noStore());
        if (download.sizeBytes() > 0L) {
            builder.contentLength(download.sizeBytes());
        }
        return builder.body(new InputStreamResource(download.stream()));
    }

    private FileActor resolveActor(HttpServletRequest request, boolean allowQueryToken) {
        UserPrincipal principal = AuthContext.current();
        if (principal != null && principal.userId() != null) {
            boolean admin = principal.roles() != null && principal.roles().stream()
                    .anyMatch(role -> role != null && role.startsWith(Roles.ADMIN));
            return FileActor.user(principal.userId(), principal.username(), admin,
                    principal.merchantId());
        }

        String token = request.getHeader(APPLY_TOKEN_HEADER);
        if (!StringUtils.hasText(token)) {
            String authorization = request.getHeader("Authorization");
            if (StringUtils.hasText(authorization) && authorization.startsWith("Bearer ")) {
                token = authorization.substring(7);
            }
        }
        if (!StringUtils.hasText(token) && allowQueryToken) {
            token = request.getParameter("token");
        }
        if (StringUtils.hasText(token)) {
            return FileActor.apply(applyTokenService.verify(token));
        }
        throw new BizException(ErrorCode.AUTH_UNAUTHORIZED);
    }
}
