package com.bakery.api.framework.service;

import com.bakery.api.framework.dto.BakeryBaseResponse;
import com.bakery.api.framework.exception.AdminEntityNotFoundException;
import com.bakery.api.framework.exception.AdminValidationException;
import com.bakery.common.entity.InventoryTransaction;
import com.bakery.common.entity.enums.TransactionStatus;
import com.bakery.common.entity.enums.TransactionType;
import com.bakery.common.repository.InventoryTransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Abstract command service cho inventory_transaction — direct status transition.
 *
 * Không dùng command_request staging table. Phiếu tự nó là "command":
 *   - create()  → lưu InventoryTransaction với status = PENDING
 *   - approve() → cập nhật status trực tiếp trên record
 *   - reject()  → cập nhật status = REJECTED
 *
 * Subclass implement:
 *   - transactionType()                — IMPORT | TRANSFER | ADJUSTMENT
 *   - buildTransaction(req, actor)     — map REQ → InventoryTransaction
 *   - applyUpdate(tx, req)             — update fields khi PUT /{id}
 *   - toResponse(tx)                   — map → RES DTO
 *   - beforeApprove(tx)                — validate business rules trước khi approve
 *   - afterApprove(tx)                 — side-effects: deduct/add inventory lots
 *   - validateCreate(req)              — validate trước khi tạo phiếu
 *
 * Approve logic theo transactionType:
 *   IMPORT / ADJUSTMENT : PENDING → ACTIVE  (1 bước)
 *   TRANSFER            : PENDING → READY   (bước 1 — Cường)
 *                         READY   → ACTIVE  (bước 2 — KHO_BEP/SHOP confirm)
 */
@Slf4j
@RequiredArgsConstructor
public abstract class TransactionCommandService<REQ, RES extends BakeryBaseResponse> {

    protected final InventoryTransactionRepository txRepository;

    // ── Abstract contract ────────────────────────────────────────────────────

    public abstract TransactionType transactionType();

    protected abstract InventoryTransaction buildTransaction(REQ req, String actor);

    protected abstract void applyUpdate(InventoryTransaction tx, REQ req);

    public abstract RES toResponse(InventoryTransaction tx);

    // ── Hooks (no-op defaults, override per module) ──────────────────────────

    protected void validateCreate(REQ req) {}

    protected void validateUpdate(InventoryTransaction tx, REQ req) {}

    protected void beforeApprove(InventoryTransaction tx) {}

    /** Gọi sau khi status đã được update và save. Dùng để deduct/add inventory lots. */
    protected void afterApprove(InventoryTransaction tx) {}

    protected void beforeReject(InventoryTransaction tx) {}

    // ── Create ───────────────────────────────────────────────────────────────

    @Transactional
    public InventoryTransaction create(REQ req, String actor) {
        validateCreate(req);
        InventoryTransaction tx = buildTransaction(req, actor);
        tx.setStatus(TransactionStatus.PENDING);
        InventoryTransaction saved = txRepository.save(tx);
        log.info("[{}] Created tx={} actor={}", transactionType(), saved.getId(), actor);
        return saved;
    }

    // ── Update (chỉ cho phép khi PENDING) ───────────────────────────────────

    @Transactional
    public InventoryTransaction update(UUID id, REQ req, String actor) {
        InventoryTransaction tx = findOrThrow(id);
        guardEditable(tx);
        validateUpdate(tx, req);
        applyUpdate(tx, req);
        InventoryTransaction saved = txRepository.save(tx);
        log.info("[{}] Updated tx={} actor={}", transactionType(), id, actor);
        return saved;
    }

    // ── Cancel (soft — chỉ khi PENDING) ─────────────────────────────────────

    @Transactional
    public void cancel(UUID id, String actor) {
        InventoryTransaction tx = findOrThrow(id);
        guardEditable(tx);
        tx.setStatus(TransactionStatus.REJECTED);
        tx.setRejectionReason("Hủy bởi người tạo");
        txRepository.save(tx);
        log.info("[{}] Cancelled tx={} actor={}", transactionType(), id, actor);
    }

    // ── Approve ──────────────────────────────────────────────────────────────

    @Transactional
    public InventoryTransaction approve(UUID id, String approverActor) {
        InventoryTransaction tx = findOrThrow(id);
        guardApprovable(tx);
        beforeApprove(tx);

        TransactionStatus nextStatus = resolveNextStatus(tx);
        tx.setStatus(nextStatus);
        tx.setApprovedBy(approverActor);
        tx.setApprovedAt(OffsetDateTime.now());

        InventoryTransaction saved = txRepository.save(tx);
        afterApprove(saved);

        log.info("[{}] Approved tx={} {} → {} by={}", transactionType(), id,
                resolveCurrentBeforeApprove(nextStatus), nextStatus, approverActor);
        return saved;
    }

    // ── Reject ───────────────────────────────────────────────────────────────

    @Transactional
    public InventoryTransaction reject(UUID id, String reason, String actor) {
        InventoryTransaction tx = findOrThrow(id);
        guardApprovable(tx);
        beforeReject(tx);
        tx.setStatus(TransactionStatus.REJECTED);
        tx.setRejectionReason(reason);
        InventoryTransaction saved = txRepository.save(tx);
        log.info("[{}] Rejected tx={} by={}", transactionType(), id, actor);
        return saved;
    }

    // ── Clone (REJECTED → PENDING mới) ───────────────────────────────────────

    /**
     * Clone phiếu REJECTED → tạo phiếu PENDING mới với cùng lines.
     * qty_approved reset về null (chờ approve lại), lot reset về null.
     * Dùng khi Cường/Shop từ chối và cần làm lại phiếu.
     */
    @Transactional
    public InventoryTransaction clone(UUID id, String actor) {
        InventoryTransaction original = findOrThrow(id);
        if (original.getStatus() != TransactionStatus.REJECTED) {
            throw new AdminValidationException(
                    "Chỉ có thể clone phiếu REJECTED. Phiếu " + original.getCode()
                    + " đang ở trạng thái " + original.getStatus());
        }

        InventoryTransaction cloned = InventoryTransaction.builder()
                .code(generateCloneCode(original.getCode()))
                .transactionType(original.getTransactionType())
                .transactionReason(original.getTransactionReason())
                .transactionDate(java.time.LocalDate.now())
                .fromBranch(original.getFromBranch())
                .toBranch(original.getToBranch())
                .supplier(original.getSupplier())
                .totalAmount(original.getTotalAmount())
                .paymentStatus(original.getPaymentStatus())
                .note(original.getNote() != null
                        ? "[Clone từ " + original.getCode() + "] " + original.getNote()
                        : "[Clone từ " + original.getCode() + "]")
                .status(TransactionStatus.PENDING)
                .build();

        // Copy lines — reset qty_approved + lot (chờ approve mới)
        for (com.bakery.common.entity.InventoryTransactionLine line : original.getLines()) {
            com.bakery.common.entity.InventoryTransactionLine newLine =
                    com.bakery.common.entity.InventoryTransactionLine.builder()
                    .transaction(cloned)
                    .itemId(line.getItemId())
                    .itemType(line.getItemType())
                    .qtyRequested(line.getQtyRequested())
                    .qtyApproved(null)          // reset
                    .unit(line.getUnit())
                    .unitPrice(line.getUnitPrice())
                    .lot(null)                   // reset
                    .note(line.getNote())
                    .build();
            cloned.getLines().add(newLine);
        }

        InventoryTransaction saved = txRepository.save(cloned);
        log.info("[{}] Cloned tx={} → new tx={} by={}", transactionType(), id, saved.getId(), actor);
        return saved;
    }

    private String generateCloneCode(String originalCode) {
        String date   = java.time.LocalDate.now().toString().replace("-", "");
        String suffix = String.format("%04d", (int) (Math.random() * 9999) + 1);
        // Giữ prefix gốc (IMP/TRF/ADJ), thêm ngày + suffix mới
        String prefix = originalCode != null && originalCode.contains("-")
                ? originalCode.split("-")[0] : "TX";
        return prefix + "-" + date + "-" + suffix;
    }

    // ── List (tab-based) ─────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<RES> listByStatus(TransactionStatus status, int page, int size) {
        var pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "transactionDate", "createdAt"));
        return txRepository
                .findByTypeAndStatus(transactionType(), status, pageable)
                .map(this::toResponse);
    }

    /**
     * List theo status + branch filter — dùng cho màn hình kho của từng chi nhánh.
     *
     * TRANSFER: filter theo from_branch OR to_branch.
     * IMPORT / ADJUSTMENT: filter theo to_branch (kho nhận / kho bị điều chỉnh).
     *
     * Nếu branchId = null → trả về toàn bộ (admin view).
     */
    @Transactional(readOnly = true)
    public Page<RES> listByStatusAndBranch(TransactionStatus status, UUID branchId, int page, int size) {
        if (branchId == null) {
            return listByStatus(status, page, size);
        }

        List<InventoryTransaction> all = switch (transactionType()) {
            case TRANSFER   -> txRepository.findTransfersByBranchAndStatus(branchId, status);
            case IMPORT     -> txRepository.findImportsByBranchAndStatus(branchId, status);
            case ADJUSTMENT -> txRepository.findAdjustmentsByBranchAndStatus(branchId, status);
            default         -> txRepository.findByTypeAndStatusList(transactionType(), status);
        };

        List<RES> mapped = all.stream().map(this::toResponse).toList();
        int start  = Math.min(page * size, mapped.size());
        int end    = Math.min(start + size, mapped.size());
        return new PageImpl<>(mapped.subList(start, end),
                PageRequest.of(page, size), mapped.size());
    }

    @Transactional(readOnly = true)
    public RES getById(UUID id) {
        return toResponse(findOrThrow(id));
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    protected InventoryTransaction findOrThrow(UUID id) {
        return txRepository.findById(id)
                .orElseThrow(() -> new AdminEntityNotFoundException(
                        transactionType().name(), id));
    }

    private void guardEditable(InventoryTransaction tx) {
        if (tx.getStatus() != TransactionStatus.PENDING) {
            throw new AdminValidationException(
                    "Phiếu " + tx.getCode() + " không ở trạng thái PENDING — không thể sửa/hủy.");
        }
    }

    private void guardApprovable(InventoryTransaction tx) {
        if (tx.getStatus() == TransactionStatus.ACTIVE
                || tx.getStatus() == TransactionStatus.REJECTED) {
            throw new AdminValidationException(
                    "Phiếu " + tx.getCode() + " đã ở trạng thái "
                    + tx.getStatus() + " — không thể approve/reject.");
        }
    }

    /**
     * TRANSFER: PENDING → READY (bước 1), READY → ACTIVE (bước 2).
     * IMPORT / ADJUSTMENT: PENDING → ACTIVE (1 bước).
     */
    private TransactionStatus resolveNextStatus(InventoryTransaction tx) {
        if (tx.getTransactionType() == TransactionType.TRANSFER
                && tx.getStatus() == TransactionStatus.PENDING) {
            return TransactionStatus.READY;
        }
        return TransactionStatus.ACTIVE;
    }

    private TransactionStatus resolveCurrentBeforeApprove(TransactionStatus next) {
        return next == TransactionStatus.READY ? TransactionStatus.PENDING : TransactionStatus.READY;
    }
}
