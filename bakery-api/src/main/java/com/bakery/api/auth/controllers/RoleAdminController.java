package com.bakery.api.auth.controllers;

import com.bakery.api.auth.dtos.PermissionRow;
import com.bakery.api.auth.dtos.RoleRequest;
import com.bakery.api.auth.dtos.RoleResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;
import com.bakery.api.auth.services.RoleAdminService;

@RestController
@RequestMapping("/admin/roles")
@RequiredArgsConstructor
@Tag(name = "Admin - Role", description = "Quản lý Role")
public class RoleAdminController {

    private final RoleAdminService roleService;

    @GetMapping
    @Operation(summary = "Danh sách tất cả role")
    public ResponseEntity<List<RoleResponse>> listAll() {
        return ResponseEntity.ok(roleService.listAll());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Chi tiết role")
    public ResponseEntity<RoleResponse> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(roleService.getById(id));
    }

    @PostMapping
    @Operation(summary = "Tạo role mới")
    public ResponseEntity<RoleResponse> create(@Valid @RequestBody RoleRequest request) {
        return ResponseEntity.status(201).body(roleService.create(request));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Cập nhật role")
    public ResponseEntity<RoleResponse> update(
            @PathVariable UUID id,
            @Valid @RequestBody RoleRequest request) {
        return ResponseEntity.ok(roleService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Vô hiệu hóa role (soft)")
    public ResponseEntity<Void> deactivate(@PathVariable UUID id) {
        roleService.deactivate(id);
        return ResponseEntity.noContent().build();
    }

    // ── Permissions ────────────────────────────────────────────────

    @GetMapping("/{roleId}/permissions")
    @Operation(summary = "Xem quyền của role trên từng màn hình")
    public ResponseEntity<List<PermissionRow>> getPermissions(@PathVariable UUID roleId) {
        return ResponseEntity.ok(roleService.getPermissions(roleId));
    }

    @PutMapping("/{roleId}/permissions")
    @Operation(summary = "Lưu toàn bộ quyền của role (ghi đè)")
    public ResponseEntity<List<PermissionRow>> savePermissions(
            @PathVariable UUID roleId,
            @RequestBody List<PermissionRow> rows) {
        return ResponseEntity.ok(roleService.savePermissions(roleId, rows));
    }
}
