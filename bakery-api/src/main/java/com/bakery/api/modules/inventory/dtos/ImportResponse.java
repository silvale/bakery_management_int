package com.bakery.api.modules.inventory.dtos;

import com.bakery.api.framework.dtos.BakeryBaseResponse;
import com.bakery.api.framework.enums.PaymentStatus;
import com.bakery.api.framework.enums.TransactionReason;
import com.bakery.api.framework.enums.TransactionStatus;
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
