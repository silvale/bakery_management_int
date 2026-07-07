package com.bakery.api.modules.inventory.dtos;

import com.bakery.api.framework.enums.TransactionReason;
import com.bakery.api.framework.enums.TransactionType;
import com.bakery.api.framework.enums.PaymentStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Request thống nhất cho tất cả phiếu kho tại POST /api/v1/transactions.
 *
 * Trường bắt buộc chung:
 *   - type: IMPORT | TRANSFER | ADJUSTMENT
 *   - lines: ít nhất 1 dòng
 *
 * Trường theo type:
 *   IMPORT:     toBranchId (bắt buộc), supplierId, transactionReason, totalAmount, paymentStatus
 *   TRANSFER:   fromBranchId (bắt buộc), toBranchId (bắt buộc)
 *   ADJUSTMENT: branchId (bắt buộc), transactionReason
 *
 * Validation chi tiết theo type được thực hiện tại tầng Service (validateCreate).
 */
public record UnifiedTransactionRequest(

        /** IMPORT | TRANSFER | ADJUSTMENT */
        @NotNull TransactionType type,

        LocalDate transactionDate,

        // ── IMPORT / ADJUSTMENT: kho nhận / kho bị điều chỉnh ─────────────
        UUID toBranchId,

        // ── IMPORT only ────────────────────────────────────────────────────
        UUID supplierId,
        BigDecimal totalAmount,
        PaymentStatus paymentStatus,

        // ── TRANSFER only ──────────────────────────────────────────────────
        UUID fromBranchId,

        // ── ADJUSTMENT / IMPORT ────────────────────────────────────────────
        TransactionReason transactionReason,

        // ── ADJUSTMENT shorthand: branchId = toBranchId ────────────────────
        UUID branchId,

        @Valid
        @NotEmpty
        List<TransactionLineRequest> lines,

        String note
) {
    /**
     * Resolve toBranchId theo type:
     *   ADJUSTMENT: ưu tiên branchId, fallback toBranchId
     *   IMPORT/TRANSFER: dùng toBranchId trực tiếp
     */
    public UUID resolvedToBranchId() {
        return (type == TransactionType.ADJUSTMENT && branchId != null) ? branchId : toBranchId;
    }
}
