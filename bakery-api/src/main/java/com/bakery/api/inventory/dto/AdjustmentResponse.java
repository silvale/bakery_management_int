package com.bakery.api.inventory.dto;

import com.bakery.api.framework.dto.BakeryBaseResponse;
import com.bakery.common.entity.enums.TransactionReason;
import com.bakery.common.entity.enums.TransactionStatus;
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
