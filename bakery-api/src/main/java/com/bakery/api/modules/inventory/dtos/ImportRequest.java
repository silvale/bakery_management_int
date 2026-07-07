package com.bakery.api.modules.inventory.dtos;

import com.bakery.api.framework.enums.PaymentStatus;
import com.bakery.api.framework.enums.TransactionReason;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Request tạo / cập nhật phiếu nhập hàng (IMPORT).
 * transaction_reason: PURCHASE | PRODUCTION | RESTOCK
 */
public record ImportRequest(

        /** Ngày nhập kho (nghiệp vụ) — mặc định hôm nay nếu null */
        LocalDate transactionDate,

        /** Kho nhận hàng */
        @NotNull UUID toBranchId,

        /** Nhà cung cấp — required khi reason = PURCHASE */
        UUID supplierId,

        @NotNull TransactionReason transactionReason,

        BigDecimal totalAmount,

        PaymentStatus paymentStatus,

        @Valid
        @NotEmpty
        List<TransactionLineRequest> lines,

        String note
) {}
