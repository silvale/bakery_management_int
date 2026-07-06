package com.bakery.api.inventory.dto;

import com.bakery.common.entity.enums.TransactionReason;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Request tạo phiếu ĐIỀU CHỈNH (transaction_type = ADJUSTMENT).
 * transaction_reason: LOSS | STOCKTAKE | SUPPLIER_RETURN | WRITE_OFF
 *
 * qty_requested dương = tăng tồn kho (stocktake thực tế cao hơn sổ sách).
 * qty_requested âm  = giảm tồn kho (hủy, mất, trả NCC).
 */
public record AdjustmentRequest(

        LocalDate transactionDate,

        /** Kho bị điều chỉnh */
        @NotNull UUID branchId,

        @NotNull TransactionReason transactionReason,

        @Valid
        @NotEmpty
        List<TransactionLineRequest> lines,

        String note
) {}
