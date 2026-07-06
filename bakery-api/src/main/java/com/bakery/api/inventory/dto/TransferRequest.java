package com.bakery.api.inventory.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Request tạo phiếu ĐIỀU CHUYỂN (transaction_type = TRANSFER).
 * transaction_reason mặc định = RESTOCK.
 * Luồng 2 bước: PENDING → READY (Cường) → ACTIVE (KHO_BEP/SHOP).
 */
public record TransferRequest(

        LocalDate transactionDate,

        @NotNull UUID fromBranchId,
        @NotNull UUID toBranchId,

        @Valid
        @NotEmpty
        List<TransactionLineRequest> lines,

        String note
) {}
