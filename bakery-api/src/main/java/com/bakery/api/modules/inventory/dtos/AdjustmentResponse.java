package com.bakery.api.modules.inventory.dtos;

import com.bakery.api.framework.dtos.BakeryBaseResponse;
import com.bakery.api.framework.enums.TransactionReason;
import com.bakery.api.framework.enums.TransactionStatus;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
public class AdjustmentResponse extends BakeryBaseResponse {

    private String code;
    private TransactionStatus status;
    private LocalDate transactionDate;
    private TransactionReason transactionReason;

    private UUID branchId;
    private String branchName;

    private String note;
    private String rejectionReason;

    private List<TransactionLineResponse> lines;
}
