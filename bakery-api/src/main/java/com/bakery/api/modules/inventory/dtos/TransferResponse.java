package com.bakery.api.modules.inventory.dtos;

import com.bakery.api.framework.dtos.BakeryBaseResponse;
import com.bakery.api.framework.enums.TransactionStatus;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
public class TransferResponse extends BakeryBaseResponse {

    private String code;
    private TransactionStatus status;
    private LocalDate transactionDate;

    private UUID fromBranchId;
    private String fromBranchName;

    private UUID toBranchId;
    private String toBranchName;

    private String note;
    private String rejectionReason;

    private List<TransactionLineResponse> lines;
}
