package com.bakery.api.framework.controller;

import com.bakery.api.framework.dto.BakeryBaseResponse;
import com.bakery.api.framework.dto.PageResult;
import com.bakery.api.framework.service.TransactionCommandService;
import com.bakery.common.entity.InventoryTransaction;
import com.bakery.common.entity.enums.TransactionStatus;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;
import java.util.Optional;

/**
 * Abstract REST controller cho inventory_transaction modules.
 * Mirror AdminBaseResource nhưng dùng TransactionCommandService (direct status transition).
 *
 * 3 tab UI:
 *   GET /active   → status = ACTIVE
 *   GET /pending  → status = PENDING  (+ READY cho TRANSFER)
 *   GET /rejected → status = REJECTED
 *
 * Lifecycle:
 *   POST /            → create phiếu (status = PENDING)
 *   PUT  /{id}        → update phiếu (chỉ khi PENDING)
 *   DELETE /{id}      → cancel phiếu (PENDING → REJECTED)
 *   POST /{id}/approve → approve (PENDING→READY→ACTIVE)
 *   POST /{id}/reject  → reject  (PENDING|READY → REJECTED)
 *
 * Concrete controller chỉ cần:
 *   1. @RestController + @RequestMapping("/api/v1/...")
 *   2. Implement abstractCommand() trả về concrete service
 */
@RequiredArgsConstructor
public abstract class TransactionBaseResource<REQ, RES extends BakeryBaseResponse> {

    protected abstract TransactionCommandService<REQ, RES> abstractCommand();

    // ── Tab Active ────────────────────────────────────────────────────────────

    @GetMapping("/active")
    @Operation(summary = "Danh sách phiếu ACTIVE (tuỳ chọn filter theo branchId)")
    public PageResult<RES> listActive(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) UUID branchId) {
        Page<RES> result = abstractCommand().listByStatusAndBranch(TransactionStatus.ACTIVE, branchId, page, size);
        return PageResult.of(result);
    }

    // ── Tab Pending ───────────────────────────────────────────────────────────

    @GetMapping("/pending")
    @Operation(summary = "Danh sách phiếu PENDING (tuỳ chọn filter theo branchId)")
    public PageResult<RES> listPending(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) UUID branchId) {
        Page<RES> result = abstractCommand().listByStatusAndBranch(TransactionStatus.PENDING, branchId, page, size);
        return PageResult.of(result);
    }

    // ── Tab Rejected ──────────────────────────────────────────────────────────

    @GetMapping("/rejected")
    @Operation(summary = "Danh sách phiếu bị từ chối / đã hủy (tuỳ chọn filter theo branchId)")
    public PageResult<RES> listRejected(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) UUID branchId) {
        Page<RES> result = abstractCommand().listByStatusAndBranch(TransactionStatus.REJECTED, branchId, page, size);
        return PageResult.of(result);
    }

    // ── Detail ────────────────────────────────────────────────────────────────

    @GetMapping("/{id}")
    @Operation(summary = "Chi tiết phiếu")
    public ResponseEntity<RES> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(abstractCommand().getById(id));
    }

    // ── Create ────────────────────────────────────────────────────────────────

    @PostMapping
    @Operation(summary = "Tạo phiếu mới (status = PENDING)")
    public ResponseEntity<Map<String, Object>> create(
            @Valid @RequestBody REQ request,
            @AuthenticationPrincipal UserDetails actor) {

        String actorName = actor != null ? actor.getUsername() : "system";
        InventoryTransaction tx = abstractCommand().create(request, actorName);
        return ResponseEntity.accepted().body(Map.of(
                "id",      tx.getId(),
                "code",    tx.getCode(),
                "status",  tx.getStatus(),
                "message", "Phiếu đã được tạo, chờ duyệt"
        ));
    }

    // ── Update ────────────────────────────────────────────────────────────────

    @PutMapping("/{id}")
    @Operation(summary = "Cập nhật phiếu (chỉ khi PENDING)")
    public ResponseEntity<RES> update(
            @PathVariable UUID id,
            @Valid @RequestBody REQ request,
            @AuthenticationPrincipal UserDetails actor) {

        String actorName = actor != null ? actor.getUsername() : "system";
        InventoryTransaction tx = abstractCommand().update(id, request, actorName);
        return ResponseEntity.ok(abstractCommand().toResponse(tx));
    }

    // ── Cancel ────────────────────────────────────────────────────────────────

    @DeleteMapping("/{id}")
    @Operation(summary = "Hủy phiếu (chỉ khi PENDING — không xóa khỏi DB)")
    public ResponseEntity<Map<String, Object>> cancel(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserDetails actor) {

        String actorName = actor != null ? actor.getUsername() : "system";
        abstractCommand().cancel(id, actorName);
        return ResponseEntity.ok(Map.of(
                "id",      id,
                "status",  TransactionStatus.REJECTED,
                "message", "Phiếu đã bị hủy"
        ));
    }

    // ── Approve ───────────────────────────────────────────────────────────────

    @PostMapping("/{id}/approve")
    @Operation(summary = "Duyệt phiếu (PENDING→READY→ACTIVE). TRANSFER cần 2 lần.")
    public ResponseEntity<RES> approve(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserDetails actor) {

        String actorName = actor != null ? actor.getUsername() : "system";
        InventoryTransaction tx = abstractCommand().approve(id, actorName);
        return ResponseEntity.ok(abstractCommand().toResponse(tx));
    }

    // ── Reject ────────────────────────────────────────────────────────────────

    @PostMapping("/{id}/reject")
    @Operation(summary = "Từ chối phiếu (PENDING|READY → REJECTED)")
    public ResponseEntity<Map<String, Object>> reject(
            @PathVariable UUID id,
            @RequestParam(required = false) String reason,
            @AuthenticationPrincipal UserDetails actor) {

        String actorName = actor != null ? actor.getUsername() : "system";
        InventoryTransaction tx = abstractCommand().reject(id, reason, actorName);
        return ResponseEntity.ok(Map.of(
                "id",      id,
                "status",  tx.getStatus(),
                "reason",  reason != null ? reason : ""
        ));
    }

    // ── Clone ─────────────────────────────────────────────────────────────────

    @PostMapping("/{id}/clone")
    @Operation(summary = "Clone phiếu REJECTED → phiếu PENDING mới (dùng khi bị từ chối cần làm lại)")
    public ResponseEntity<Map<String, Object>> clone(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserDetails actor) {

        String actorName = actor != null ? actor.getUsername() : "system";
        InventoryTransaction tx = abstractCommand().clone(id, actorName);
        return ResponseEntity.ok(Map.of(
                "id",            tx.getId(),
                "code",          tx.getCode(),
                "status",        tx.getStatus(),
                "clonedFromId",  id,
                "message",       "Phiếu mới đã tạo từ phiếu bị từ chối — đang chờ duyệt"
        ));
    }
}
