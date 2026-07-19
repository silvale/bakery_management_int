package com.bakery.framework.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import com.bakery.framework.dto.PageResult;
import com.bakery.framework.dto.RejectRequest;
import com.bakery.framework.dto.RevisionResponse;
import com.bakery.framework.service.BakeryAdminService;
import com.bakery.framework.service.EntityHistoryService;
import com.bakery.framework.service.EntityHistoryService.EntityRevision;

import jakarta.validation.Valid;

/**
 * Base controller cho tất cả admin endpoints.
 * Cung cấp CRUD + approval + history out of the box.
 * Controller cụ thể chỉ cần extend và cung cấp service.
 *
 * <p>History tự động available nếu service override {@code getEntityClass()}.
 */
public abstract class BakeryAdminResource<REQ, RES> {

    protected abstract BakeryAdminService<REQ, RES> getService();

    @Autowired
    private EntityHistoryService historyService;

    // ── Listing ──────────────────────────────────────────────

    @GetMapping
    public ResponseEntity<PageResult<RES>> list(
            @RequestParam MultiValueMap<String, String> params, Pageable pageable) {
        Page<RES> page = getService().findAll(params, pageable);
        return ResponseEntity.ok(PageResult.of(page));
    }

    @GetMapping("/all")
    public ResponseEntity<List<RES>> listAll() {
        return ResponseEntity.ok(getService().findAll());
    }

    // ── Detail ───────────────────────────────────────────────

    @GetMapping("/{id}")
    public ResponseEntity<RES> getById(@PathVariable UUID id) {
        return getService().findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // ── Create ───────────────────────────────────────────────

    @PostMapping
    public ResponseEntity<RES> create(@Valid @RequestBody REQ request) {
        return ResponseEntity.status(201).body(getService().create(request));
    }

    // ── Update ───────────────────────────────────────────────

    @PutMapping("/{id}")
    public ResponseEntity<RES> update(@PathVariable UUID id, @Valid @RequestBody REQ request) {
        return ResponseEntity.ok(getService().update(id, request));
    }

    // ── Delete ───────────────────────────────────────────────

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        getService().delete(id);
        return ResponseEntity.noContent().build();
    }

    // ── Approval ─────────────────────────────────────────────

    @PostMapping("/{id}/approve")
    public ResponseEntity<RES> approve(@PathVariable UUID id) {
        return ResponseEntity.ok(getService().approve(id));
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<RES> reject(@PathVariable UUID id, @Valid @RequestBody RejectRequest request) {
        return ResponseEntity.ok(getService().reject(id, request.reason()));
    }

    // ── History ───────────────────────────────────────────────
    // Tự động available nếu service.getEntityClass() != null.

    /**
     * Danh sách lịch sử thay đổi, mới nhất trước.
     * Bỏ qua revision ADD đầu tiên (khởi tạo không có diff ý nghĩa).
     */
    @GetMapping("/{id}/history")
    @Transactional(readOnly = true)
    public ResponseEntity<List<RevisionResponse<RES>>> history(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
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

    /**
     * Snapshot của entity tại một revision cụ thể.
     */
    @GetMapping("/{id}/history/{revision}")
    @Transactional(readOnly = true)
    public ResponseEntity<RES> historyAt(
            @PathVariable UUID id,
            @PathVariable long revision) {
        Class<?> entityClass = getService().getEntityClass();
        if (entityClass == null) return ResponseEntity.notFound().build();

        Object entity = historyService.getRevision(entityClass, id, revision);
        if (entity == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(getService().mapToResponse(entity));
    }

    /**
     * So sánh 2 revision bất kỳ — trả về [before, after].
     * FE dùng để cho user chọn version rồi compare.
     */
    @GetMapping("/{id}/diff")
    @Transactional(readOnly = true)
    public ResponseEntity<List<RES>> diff(
            @PathVariable UUID id,
            @RequestParam long revA,
            @RequestParam long revB) {
        Class<?> entityClass = getService().getEntityClass();
        if (entityClass == null) return ResponseEntity.notFound().build();

        List<?> pair = historyService.loadRevisionPair(entityClass, id, revA, revB);
        return ResponseEntity.ok(pair.stream().map(getService()::mapToResponse).toList());
    }

    /**
     * So sánh một revision với revision liền trước.
     * FE dùng cho button "2 phiên bản gần nhất".
     */
    @GetMapping("/{id}/diff/{revision}")
    @Transactional(readOnly = true)
    public ResponseEntity<List<RES>> diffWithPrevious(
            @PathVariable UUID id,
            @PathVariable long revision) {
        Class<?> entityClass = getService().getEntityClass();
        if (entityClass == null) return ResponseEntity.notFound().build();

        List<?> pair = historyService.loadRevisionPair(entityClass, id, revision - 1, revision);
        return ResponseEntity.ok(pair.stream().map(getService()::mapToResponse).toList());
    }
}
