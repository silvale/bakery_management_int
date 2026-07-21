package com.bakery.api.auth.controller;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.bakery.api.auth.dto.RolePermissionRequest;
import com.bakery.api.auth.dto.ScreenResponse;
import com.bakery.api.auth.service.RolePermissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class PermissionController {

    private final RolePermissionService rolePermissionService;

    /** GET /api/v1/screens — danh sách màn hình + available actions */
    @GetMapping("/screens")
    public ResponseEntity<List<ScreenResponse>> getAllScreens() {
        return ResponseEntity.ok(rolePermissionService.getAllScreens());
    }

    /** GET /api/v1/user-roles/{id}/permissions — permissions hiện tại của role */
    @GetMapping("/user-roles/{id}/permissions")
    public ResponseEntity<Map<String, Set<String>>> getPermissions(@PathVariable UUID id) {
        return ResponseEntity.ok(rolePermissionService.getPermissions(id));
    }

    /**
     * PUT /api/v1/user-roles/{id}/permissions — bulk replace toàn bộ permissions.
     * Body: { "permissions": [{ "screenCode": "ITEMS", "actionCode": "VIEW" }, ...] }
     */
    @PutMapping("/user-roles/{id}/permissions")
    public ResponseEntity<Map<String, Set<String>>> setPermissions(
            @PathVariable UUID id,
            @RequestBody RolePermissionRequest req) {
        return ResponseEntity.ok(rolePermissionService.setPermissions(id, req));
    }
}
