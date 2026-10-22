package com.soloeats.app.controller;

import com.soloeats.app.dto.SaveAddonTemplateRequest;
import com.soloeats.app.dto.SaveSpecTemplateRequest;
import com.soloeats.common.api.ApiResponse;
import com.soloeats.common.auth.RequirePermission;
import com.soloeats.common.auth.RequireRole;
import com.soloeats.common.auth.Roles;
import com.soloeats.common.idempotency.Idempotent;
import com.soloeats.common.log.OperationLog;
import com.soloeats.product.service.SpecTemplateService;
import com.soloeats.product.service.command.SaveAddonTemplateCommand;
import com.soloeats.product.service.command.SaveSpecTemplateCommand;
import com.soloeats.product.service.view.AddonGroupView;
import com.soloeats.product.service.view.SpecGroupView;
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
 * 规格/加料模板维护：组 + 选项/加料项一体化提交。
 */
@RequireRole(Roles.ADMIN)
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminTemplateController {

    private final SpecTemplateService specTemplateService;

    @RequirePermission("product:template:list")
    @GetMapping("/spec-templates")
    public ApiResponse<List<SpecGroupView>> listSpecTemplates() {
        return ApiResponse.ok(specTemplateService.listSpecTemplates());
    }

    @RequirePermission("product:template:create")
    @Idempotent(ttlSeconds = 5)
    @OperationLog(module = "商品管理", action = "新增规格模板")
    @PostMapping("/spec-templates")
    public ApiResponse<SpecGroupView> createSpecTemplate(
            @Valid @RequestBody SaveSpecTemplateRequest request) {
        return ApiResponse.ok(specTemplateService.saveSpecTemplate(null, toCommand(request)));
    }

    @RequirePermission("product:template:update")
    @OperationLog(module = "商品管理", action = "编辑规格模板")
    @PutMapping("/spec-templates/{id}")
    public ApiResponse<SpecGroupView> updateSpecTemplate(
            @PathVariable Long id,
            @Valid @RequestBody SaveSpecTemplateRequest request) {
        return ApiResponse.ok(specTemplateService.saveSpecTemplate(id, toCommand(request)));
    }

    @RequirePermission("product:template:delete")
    @OperationLog(module = "商品管理", action = "删除规格模板")
    @DeleteMapping("/spec-templates/{id}")
    public ApiResponse<Void> deleteSpecTemplate(@PathVariable Long id) {
        specTemplateService.deleteSpecTemplate(id);
        return ApiResponse.ok();
    }

    @RequirePermission("product:template:list")
    @GetMapping("/addon-templates")
    public ApiResponse<List<AddonGroupView>> listAddonTemplates() {
        return ApiResponse.ok(specTemplateService.listAddonTemplates());
    }

    @RequirePermission("product:template:create")
    @Idempotent(ttlSeconds = 5)
    @OperationLog(module = "商品管理", action = "新增加料模板")
    @PostMapping("/addon-templates")
    public ApiResponse<AddonGroupView> createAddonTemplate(
            @Valid @RequestBody SaveAddonTemplateRequest request) {
        return ApiResponse.ok(specTemplateService.saveAddonTemplate(null, toCommand(request)));
    }

    @RequirePermission("product:template:update")
    @OperationLog(module = "商品管理", action = "编辑加料模板")
    @PutMapping("/addon-templates/{id}")
    public ApiResponse<AddonGroupView> updateAddonTemplate(
            @PathVariable Long id,
            @Valid @RequestBody SaveAddonTemplateRequest request) {
        return ApiResponse.ok(specTemplateService.saveAddonTemplate(id, toCommand(request)));
    }

    @RequirePermission("product:template:delete")
    @OperationLog(module = "商品管理", action = "删除加料模板")
    @DeleteMapping("/addon-templates/{id}")
    public ApiResponse<Void> deleteAddonTemplate(@PathVariable Long id) {
        specTemplateService.deleteAddonTemplate(id);
        return ApiResponse.ok();
    }

    private SaveSpecTemplateCommand toCommand(SaveSpecTemplateRequest request) {
        List<SaveSpecTemplateCommand.Option> options = request.options().stream()
                .map(option -> new SaveSpecTemplateCommand.Option(
                        option.optionName(), option.priceDelta(), option.sort(), option.status()))
                .toList();
        return new SaveSpecTemplateCommand(
                request.groupName(), request.groupType(), request.isRequired(),
                request.sort(), options);
    }

    private SaveAddonTemplateCommand toCommand(SaveAddonTemplateRequest request) {
        List<SaveAddonTemplateCommand.Item> items = request.items().stream()
                .map(item -> new SaveAddonTemplateCommand.Item(
                        item.itemName(), item.price(), item.stock(), item.sort(), item.status()))
                .toList();
        return new SaveAddonTemplateCommand(
                request.groupName(), request.minSelect(), request.maxSelect(),
                request.sort(), items);
    }
}
