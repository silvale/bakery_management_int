package com.bakery.api.inventory.dto;

import com.bakery.api.framework.dto.BakeryBaseResponse;
import com.bakery.common.entity.enums.PaymentStatus;
import com.bakery.common.entity.enums.TransactionReason;
import com.bakery.common.entity.enums.TransactionStatus;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
public class ImportResponse extends BakeryBaseResponse {

    private String code;
    private TransactionStatus status;
    private LocalDate transactionDate;
    private TransactionReason transactionReason;

    private UUID toBranchId;
    private String toBranchName;

    private UUID supplierId;
    private String supplierName;

    private BigDecimal totalAmount;
    private BigDecimal totalPaid;       // sum of payments
    private BigDecimal debtRemaining;   // totalAmount - totalPaid
    private PaymentStatus paymentStatus;

    private String note;
    private String rejectionReason;

    private List<TransactionLineResponse> lines;
}
