package com.soloeats.app.controller;

import com.soloeats.app.dto.SaveMediaGroupRequest;
import com.soloeats.app.dto.SaveMediaItemRequest;
import com.soloeats.common.api.ApiResponse;
import com.soloeats.common.auth.RequirePermission;
import com.soloeats.common.auth.RequireRole;
import com.soloeats.common.auth.Roles;
import com.soloeats.common.idempotency.Idempotent;
import com.soloeats.common.log.OperationLog;
import com.soloeats.product.service.MediaLibraryService;
import com.soloeats.product.service.command.SaveMediaGroupCommand;
import com.soloeats.product.service.command.SaveMediaItemCommand;
import com.soloeats.product.service.view.MediaGroupView;
import com.soloeats.product.service.view.MediaItemView;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 素材库：分组 + 素材项。素材项只存 t_file.id，文件上传沿用 T03 FileController。
 */
@RequireRole(Roles.ADMIN)
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminMediaController {

    private final MediaLibraryService mediaLibraryService;

    @RequirePermission("product:media:list")
    @GetMapping("/media-groups")
    public ApiResponse<List<MediaGroupView>> listGroups() {
        return ApiResponse.ok(mediaLibraryService.listGroups());
    }

    @RequirePermission("product:media:create")
    @Idempotent(ttlSeconds = 5)
    @OperationLog(module = "素材库", action = "新增素材分组")
    @PostMapping("/media-groups")
    public ApiResponse<MediaGroupView> createGroup(
            @Valid @RequestBody SaveMediaGroupRequest request) {
        return ApiResponse.ok(mediaLibraryService.saveGroup(null, toCommand(request)));
    }

    @RequirePermission("product:media:update")
    @OperationLog(module = "素材库", action = "编辑素材分组")
    @PutMapping("/media-groups/{id}")
    public ApiResponse<MediaGroupView> updateGroup(
            @PathVariable Long id,
            @Valid @RequestBody SaveMediaGroupRequest request) {
        return ApiResponse.ok(mediaLibraryService.saveGroup(id, toCommand(request)));
    }

    @RequirePermission("product:media:delete")
    @OperationLog(module = "素材库", action = "删除素材分组")
    @DeleteMapping("/media-groups/{id}")
    public ApiResponse<Void> deleteGroup(@PathVariable Long id) {
        mediaLibraryService.deleteGroup(id);
        return ApiResponse.ok();
    }

    @RequirePermission("product:media:list")
    @GetMapping("/media-groups/{id}/items")
    public ApiResponse<List<MediaItemView>> listItems(@PathVariable Long id) {
        return ApiResponse.ok(mediaLibraryService.listItems(id));
    }

    @RequirePermission("product:media:create")
    @Idempotent(ttlSeconds = 5)
    @OperationLog(module = "素材库", action = "新增素材")
    @PostMapping("/media-groups/{id}/items")
    public ApiResponse<MediaItemView> addItem(@PathVariable Long id,
                                              @Valid @RequestBody SaveMediaItemRequest request) {
        return ApiResponse.ok(mediaLibraryService.addItem(
                id,
                new SaveMediaItemCommand(
                        request.fileId(), request.mediaType(),
                        request.sort(), request.status())
        ));
    }

    @RequirePermission("product:media:delete")
    @OperationLog(module = "素材库", action = "删除素材")
    @DeleteMapping("/media-items/{id}")
    public ApiResponse<Void> deleteItem(@PathVariable Long id) {
        mediaLibraryService.deleteItem(id);
        return ApiResponse.ok();
    }

    private SaveMediaGroupCommand toCommand(SaveMediaGroupRequest request) {
        return new SaveMediaGroupCommand(
                request.groupName(), request.parentId(), request.sort(), request.status());
    }
}
