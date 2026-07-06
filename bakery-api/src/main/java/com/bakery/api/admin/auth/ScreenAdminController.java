package com.bakery.api.admin.auth;

import com.bakery.common.entity.ScreenRegistry;
import com.bakery.common.repository.ScreenRegistryRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/admin/screens")
@RequiredArgsConstructor
@Tag(name = "Admin - Screen", description = "Danh sách màn hình (dùng để cấu hình phân quyền)")
public class ScreenAdminController {

    private final ScreenRegistryRepository screenRepo;

    @GetMapping
    @Operation(summary = "Danh sách tất cả màn hình (sorted by module + sortOrder)")
    public ResponseEntity<List<ScreenRegistry>> listAll() {
        return ResponseEntity.ok(screenRepo.findAllByOrderBySortOrderAsc());
    }
}
