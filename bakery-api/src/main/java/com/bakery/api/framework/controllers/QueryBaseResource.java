package com.bakery.api.framework.controllers;

import com.bakery.api.framework.dtos.AdminFilter;
import com.bakery.api.framework.dtos.PageResult;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.Optional;
import java.util.UUID;

/**
 * Abstract base cho READ-ONLY aggregated/query views.
 * Dùng cho: inventory stock views, reporting, lookup-only resources.
 *
 * Khác AdminBaseResource:
 *   - Không có approval workflow (submit/approve/reject)
 *   - Không có CRUD operations
 *   - Chỉ có GET /active (list + filter) và GET /{id} (detail)
 *
 * Concrete controller cần:
 *   1. @RestController + @RequestMapping("/api/v1/...")
 *   2. Implement listData(FILTER) — trả về data đã map sang RES
 *   3. Implement findData(UUID) — optional, trả về Optional<RES>
 *   4. Thêm custom endpoints nếu cần (ví dụ /lots, /expiring)
 *
 * FILTER extends AdminFilter → Spring MVC tự bind query params (page, size, search, ...)
 */
public abstract class QueryBaseResource<FILTER extends AdminFilter, RES> {

    /**
     * Trả về list data theo filter.
     * Subclass implement logic lọc + mapping → RES.
     */
    protected abstract PageResult<RES> listData(FILTER filter);

    /**
     * Trả về detail theo id.
     * Override nếu resource hỗ trợ GET /{id}.
     * Mặc định: 404.
     */
    protected Optional<RES> findData(UUID id) {
        return Optional.empty();
    }

    // ── GET /active ───────────────────────────────────────────

    @GetMapping("/active")
    @Operation(summary = "Danh sách (read-only, có filter)")
    public PageResult<RES> listActive(FILTER filter) {
        return listData(filter);
    }

    // ── GET /{id} ─────────────────────────────────────────────

    @GetMapping("/{id}")
    @Operation(summary = "Chi tiết theo id")
    public ResponseEntity<RES> getById(@PathVariable UUID id) {
        return findData(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
