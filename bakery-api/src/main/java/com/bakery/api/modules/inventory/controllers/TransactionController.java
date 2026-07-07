package com.bakery.api.modules.inventory.controllers;

import com.bakery.api.framework.dtos.PageResult;
import com.bakery.api.modules.inventory.dtos.*;
import com.bakery.api.modules.inventory.services.AdjustmentCommandService;
import com.bakery.api.modules.inventory.services.ImportCommandService;
import com.bakery.api.modules.inventory.services.TransferCommandService;
import com.bakery.api.modules.inventory.entities.InventoryTransaction;
import com.bakery.api.framework.enums.TransactionStatus;
import com.bakery.api.framework.enums.TransactionType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

/**
 * Unified Transaction Controller — một điểm vào duy nhất cho tất cả phiếu kho.
 *
 * POST   /api/v1/transactions              — tạo phiếu (type trong body)
 * GET    /api/v1/transactions              — danh sách (filter type + status + branchId)
 * GET    /api/v1/transactions/{id}         — chi tiết
 * POST   /api/v1/transactions/{id}/approve — duyệt (PENDING→READY→ACTIVE)
 * POST   /api/v1/transactions/{id}/reject  — từ chối
 * POST   /api/v1/transactions/{id}/clone   — clone phiếu REJECTED → PENDING mới
 * DELETE /api/v1/transactions/{id}         — hủy (PENDING only)
 *
 * Routing theo TransactionType:
 *   IMPORT     → ImportCommandService     (PENDING → ACTIVE, 1 bước)
 *   TRANSFER   → TransferCommandService   (PENDING → READY → ACTIVE, 2 bước)
 *   ADJUSTMENT → AdjustmentCommandService (PENDING → ACTIVE, 1 bước)
 *
 * NOTE: Các controller riêng (/purchase-orders, /transfers, /adjustments) vẫn giữ
 * để backward-compat. Controller này là entry-point chuẩn cho FE mới.
 */
@RestController
@RequestMapping("/api/v1/transactions")
@RequiredArgsConstructor
@Tag(name = "Transactions", description = "Unified API quản lý tất cả phiếu kho (IMPORT / TRANSFER / ADJUSTMENT)")
public class TransactionController {

    private final ImportCommandService     importService;
    private final TransferCommandService   transferService;
    private final AdjustmentCommandService adjustmentService;

    // ── Create ────────────────────────────────────────────────────────────────

    @PostMapping
    @Operation(summary = "Tạo phiếu kho — type: IMPORT | TRANSFER | ADJUSTMENT")
    public ResponseEntity<Map<String, Object>> create(
            @Valid @RequestBody UnifiedTransactionRequest req,
            @AuthenticationPrincipal UserDetails actor) {

        String actorName = actor != null ? actor.getUsername() : "system";
        InventoryTransaction tx = switch (req.type()) {
            case IMPORT     -> importService.create(toImportRequest(req), actorName);
            case TRANSFER   -> transferService.create(toTransferRequest(req), actorName);
            case ADJUSTMENT -> adjustmentService.create(toAdjustmentRequest(req), actorName);
            default -> throw new IllegalArgumentException("TransactionType không được hỗ trợ: " + req.type());
        };

        return ResponseEntity.accepted().body(Map.of(
                "id",      tx.getId(),
                "code",    tx.getCode(),
                "type",    tx.getTransactionType(),
                "status",  tx.getStatus(),
                "message", "Phiếu đã được tạo — chờ duyệt"
        ));
    }

    // ── List ──────────────────────────────────────────────────────────────────

    @GetMapping
    @Operation(summary = "Danh sách phiếu — filter: type (bắt buộc), status, branchId, page, size")
    public ResponseEntity<?> list(
            @RequestParam TransactionType type,
            @RequestParam(defaultValue = "PENDING") TransactionStatus status,
            @RequestParam(required = false) UUID branchId,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size) {

        return ResponseEntity.ok(switch (type) {
            case IMPORT     -> PageResult.of(importService.listByStatusAndBranch(status, branchId, page, size));
            case TRANSFER   -> PageResult.of(transferService.listByStatusAndBranch(status, branchId, page, size));
            case ADJUSTMENT -> PageResult.of(adjustmentService.listByStatusAndBranch(status, branchId, page, size));
            default -> throw new IllegalArgumentException("TransactionType không được hỗ trợ: " + type);
        });
    }

    // ── Detail ────────────────────────────────────────────────────────────────

    @GetMapping("/{id}")
    @Operation(summary = "Chi tiết phiếu — tự động resolve service theo transactionType")
    public ResponseEntity<?> getById(
            @PathVariable UUID id,
            @RequestParam TransactionType type) {

        return ResponseEntity.ok(switch (type) {
            case IMPORT     -> importService.getById(id);
            case TRANSFER   -> transferService.getById(id);
            case ADJUSTMENT -> adjustmentService.getById(id);
            default -> throw new IllegalArgumentException("TransactionType không được hỗ trợ: " + type);
        });
    }

    // ── Approve ───────────────────────────────────────────────────────────────

    /**
     * Approve phiếu — tự động rẽ nhánh theo transactionType lưu trong DB.
     *
     * IMPORT:     PENDING → ACTIVE   (1 bước) — tạo Inventory lots
     * TRANSFER:   PENDING → READY    (bước 1, Cường xác nhận chuẩn bị hàng)
     *             READY   → ACTIVE   (bước 2, Bếp/Shop xác nhận nhận hàng)
     * ADJUSTMENT: PENDING → ACTIVE   (1 bước) — deduct hoặc add inventory
     *
     * Client không cần biết đang ở bước mấy — backend tự resolve.
     */
    @PostMapping("/{id}/approve")
    @Operation(summary = "Duyệt phiếu (backend tự resolve: IMPORT 1-bước, TRANSFER 2-bước)")
    public ResponseEntity<?> approve(
            @PathVariable UUID id,
            @RequestParam TransactionType type,
            @AuthenticationPrincipal UserDetails actor) {

        String actorName = actor != null ? actor.getUsername() : "system";
        InventoryTransaction tx = switch (type) {
            case IMPORT     -> importService.approve(id, actorName);
            case TRANSFER   -> transferService.approve(id, actorName);
            case ADJUSTMENT -> adjustmentService.approve(id, actorName);
            default -> throw new IllegalArgumentException("TransactionType không được hỗ trợ: " + type);
        };

        return ResponseEntity.ok(switch (type) {
            case IMPORT     -> importService.toResponse(tx);
            case TRANSFER   -> transferService.toResponse(tx);
            case ADJUSTMENT -> adjustmentService.toResponse(tx);
            default -> tx;
        });
    }

    // ── Reject ────────────────────────────────────────────────────────────────

    @PostMapping("/{id}/reject")
    @Operation(summary = "Từ chối phiếu (PENDING | READY → REJECTED)")
    public ResponseEntity<Map<String, Object>> reject(
            @PathVariable UUID id,
            @RequestParam TransactionType type,
            @RequestParam(required = false) String reason,
            @AuthenticationPrincipal UserDetails actor) {

        String actorName = actor != null ? actor.getUsername() : "system";
        InventoryTransaction tx = switch (type) {
            case IMPORT     -> importService.reject(id, reason, actorName);
            case TRANSFER   -> transferService.reject(id, reason, actorName);
            case ADJUSTMENT -> adjustmentService.reject(id, reason, actorName);
            default -> throw new IllegalArgumentException("TransactionType không được hỗ trợ: " + type);
        };

        return ResponseEntity.ok(Map.of(
                "id",     tx.getId(),
                "status", tx.getStatus(),
                "reason", reason != null ? reason : ""
        ));
    }

    // ── Clone ─────────────────────────────────────────────────────────────────

    /**
     * Clone phiếu REJECTED → tạo phiếu PENDING mới với cùng lines.
     * Dùng khi Cường bị Bếp từ chối và cần gửi lại phiếu điều chuyển.
     */
    @PostMapping("/{id}/clone")
    @Operation(summary = "Clone phiếu REJECTED → phiếu PENDING mới (lines giữ nguyên, qty_approved reset)")
    public ResponseEntity<Map<String, Object>> clone(
            @PathVariable UUID id,
            @RequestParam TransactionType type,
            @AuthenticationPrincipal UserDetails actor) {

        String actorName = actor != null ? actor.getUsername() : "system";
        InventoryTransaction tx = switch (type) {
            case IMPORT     -> importService.clone(id, actorName);
            case TRANSFER   -> transferService.clone(id, actorName);
            case ADJUSTMENT -> adjustmentService.clone(id, actorName);
            default -> throw new IllegalArgumentException("TransactionType không được hỗ trợ: " + type);
        };

        return ResponseEntity.ok(Map.of(
                "id",           tx.getId(),
                "code",         tx.getCode(),
                "type",         tx.getTransactionType(),
                "status",       tx.getStatus(),
                "clonedFromId", id,
                "message",      "Phiếu mới đã tạo từ phiếu bị từ chối — đang chờ duyệt"
        ));
    }

    // ── Cancel ────────────────────────────────────────────────────────────────

    @DeleteMapping("/{id}")
    @Operation(summary = "Hủy phiếu (PENDING only — không xóa khỏi DB, chuyển về REJECTED)")
    public ResponseEntity<Map<String, Object>> cancel(
            @PathVariable UUID id,
            @RequestParam TransactionType type,
            @AuthenticationPrincipal UserDetails actor) {

        String actorName = actor != null ? actor.getUsername() : "system";
        switch (type) {
            case IMPORT     -> importService.cancel(id, actorName);
            case TRANSFER   -> transferService.cancel(id, actorName);
            case ADJUSTMENT -> adjustmentService.cancel(id, actorName);
            default -> throw new IllegalArgumentException("TransactionType không được hỗ trợ: " + type);
        }

        return ResponseEntity.ok(Map.of(
                "id",      id,
                "status",  TransactionStatus.REJECTED,
                "message", "Phiếu đã bị hủy"
        ));
    }

    // ── Converters ────────────────────────────────────────────────────────────

    private ImportRequest toImportRequest(UnifiedTransactionRequest r) {
        return new ImportRequest(
                r.transactionDate(),
                r.resolvedToBranchId(),
                r.supplierId(),
                r.transactionReason(),
                r.totalAmount(),
                r.paymentStatus(),
                r.lines(),
                r.note()
        );
    }

    private TransferRequest toTransferRequest(UnifiedTransactionRequest r) {
        return new TransferRequest(
                r.transactionDate(),
                r.fromBranchId(),
                r.resolvedToBranchId(),
                r.lines(),
                r.note()
        );
    }

    private AdjustmentRequest toAdjustmentRequest(UnifiedTransactionRequest r) {
        return new AdjustmentRequest(
                r.transactionDate(),
                r.resolvedToBranchId(),
                r.transactionReason(),
                r.lines(),
                r.note()
        );
    }
}
