package com.bakery.framework.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

import com.bakery.api.auth.service.PermissionService;
import com.bakery.framework.dto.PageResult;
import com.bakery.framework.dto.RejectRequest;
import com.bakery.framework.dto.RevisionResponse;
import com.bakery.framework.security.BakeryUserPrincipal;
import com.bakery.framework.service.BakeryAdminService;
import com.bakery.framework.service.EntityHistoryService;
import com.bakery.framework.service.EntityHistoryService.EntityRevision;

import jakarta.validation.Valid;

/**
 * Base controller cho tất cả admin endpoints.
 * Cung cấp CRUD + approval + history out of the box.
 *
 * <p>Override {@link #screenCode()} để bật kiểm tra quyền.
 * Trả về {@code null} để bỏ qua kiểm tra (dùng cho các endpoint public).
 */
public abstract class BakeryAdminResource<REQ, RES> {

    protected abstract BakeryAdminService<REQ, RES> getService();

    /**
     * Mã màn hình cho permission check — khớp với screen_registry.code.
     * Override trong concrete controller. Trả về null → không check quyền.
     */
    protected String screenCode() {
        return null;
    }

    @Autowired
    private EntityHistoryService historyService;

    @Autowired(required = false)
    private PermissionService permissionService;

    // ── Permission helper ─────────────────────────────────────

    protected void checkPermission(String action) {
        if (screenCode() == null || permissionService == null) return;

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof BakeryUserPrincipal principal)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Chưa đăng nhập.");
        }

        if (!permissionService.hasPermission(principal.getRole(), screenCode(), action)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Không có quyền [" + screenCode() + ":" + action + "]");
        }
    }

    // ── Listing ──────────────────────────────────────────────

    @GetMapping
    public ResponseEntity<PageResult<RES>> list(
            @RequestParam MultiValueMap<String, String> params, Pageable pageable) {
        checkPermission("VIEW");
        Page<RES> page = getService().findAll(params, pageable);
        return ResponseEntity.ok(PageResult.of(page));
    }

    @GetMapping("/all")
    public ResponseEntity<List<RES>> listAll() {
        checkPermission("VIEW");
        return ResponseEntity.ok(getService().findAll());
    }

    // ── Detail ───────────────────────────────────────────────

    @GetMapping("/{id}")
    public ResponseEntity<RES> getById(@PathVariable UUID id) {
        checkPermission("VIEW");
        return getService().findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // ── Create ───────────────────────────────────────────────

    @PostMapping
    public ResponseEntity<RES> create(@Valid @RequestBody REQ request) {
        checkPermission("CREATE");
        return ResponseEntity.status(201).body(getService().create(request));
    }

    // ── Update ───────────────────────────────────────────────

    @PutMapping("/{id}")
    public ResponseEntity<RES> update(@PathVariable UUID id, @Valid @RequestBody REQ request) {
        checkPermission("UPDATE");
        return ResponseEntity.ok(getService().update(id, request));
    }

    // ── Delete ───────────────────────────────────────────────

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        checkPermission("DELETE");
        getService().delete(id);
        return ResponseEntity.noContent().build();
    }

    // ── Approval ─────────────────────────────────────────────

    @PostMapping("/{id}/approve")
    public ResponseEntity<RES> approve(@PathVariable UUID id) {
        checkPermission("APPROVE");
        return ResponseEntity.ok(getService().approve(id));
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<RES> reject(@PathVariable UUID id, @Valid @RequestBody RejectRequest request) {
        checkPermission("REJECT");
        return ResponseEntity.ok(getService().reject(id, request.reason()));
    }

    // ── History ───────────────────────────────────────────────

    @GetMapping("/{id}/history")
    @Transactional(readOnly = true)
    public ResponseEntity<List<RevisionResponse<RES>>> history(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        checkPermission("HISTORY");
        Class<?> entityClass = getService().getEntityClass();
        if (entityClass == null) return ResponseEntity.notFound().build();

        @SuppressWarnings("unchecked")
        List<EntityRevision<Object>> revisions = (List<EntityRevision<Object>>) (List<?>) historyService.getHistory(entityClass, id, page, size);
        List<RevisionResponse<RES>> result = revisions.stream()
                .map(r -> new RevisionResponse<>(
                        getService().mapToResponse(r.entity()),
                        r.revision(),
                        r.revisionDate(),
                        r.revisionType(),
                        r.versionNumber(),
                        r.actor()))
                .toList();
        return ResponseEntity.ok(result);
    }

    @GetMapping("/{id}/history/{revision}")
    @Transactional(readOnly = true)
    public ResponseEntity<RES> historyAt(
            @PathVariable UUID id,
            @PathVariable long revision) {
        checkPermission("HISTORY");
        Class<?> entityClass = getService().getEntityClass();
        if (entityClass == null) return ResponseEntity.notFound().build();

        Object entity = historyService.getRevision(entityClass, id, revision);
        if (entity == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(getService().mapToResponse(entity));
    }

    @GetMapping("/{id}/diff")
    @Transactional(readOnly = true)
    public ResponseEntity<List<RES>> diff(
            @PathVariable UUID id,
            @RequestParam long revA,
            @RequestParam long revB) {
        checkPermission("HISTORY");
        Class<?> entityClass = getService().getEntityClass();
        if (entityClass == null) return ResponseEntity.notFound().build();

        List<?> pair = historyService.loadRevisionPair(entityClass, id, revA, revB);
        return ResponseEntity.ok(pair.stream().map(getService()::mapToResponse).toList());
    }

    @GetMapping("/{id}/diff/{revision}")
    @Transactional(readOnly = true)
    public ResponseEntity<List<RES>> diffWithPrevious(
            @PathVariable UUID id,
            @PathVariable long revision) {
        checkPermission("HISTORY");
        Class<?> entityClass = getService().getEntityClass();
        if (entityClass == null) return ResponseEntity.notFound().build();

        List<?> pair = historyService.loadRevisionPair(entityClass, id, revision - 1, revision);
        return ResponseEntity.ok(pair.stream().map(getService()::mapToResponse).toList());
    }
}
