package com.bakery.api.modules.inventory.controllers;

import com.bakery.api.framework.controllers.TransactionBaseResource;
import com.bakery.api.modules.inventory.dtos.TransferRequest;
import com.bakery.api.modules.inventory.dtos.TransferResponse;
import com.bakery.api.modules.inventory.services.TransferCommandService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * API quản lý phiếu ĐIỀU CHUYỂN (transaction_type = TRANSFER).
 *
 * 2-step approve:
 *   Lần 1 (Cường): PENDING → READY   — xác nhận yêu cầu, chưa động inventory
 *   Lần 2 (Shop/Bếp): READY → ACTIVE — xác nhận nhận hàng, FEFO deduct + tạo lot mới
 *
 * Endpoints kế thừa từ TransactionBaseResource:
 *   GET  /api/v1/transfers/active     — danh sách đã hoàn tất
 *   GET  /api/v1/transfers/pending    — chờ duyệt (PENDING) + đã xác nhận bước 1 (READY)
 *   GET  /api/v1/transfers/rejected   — từ chối / đã hủy
 *   GET  /api/v1/transfers/{id}       — chi tiết
 *   POST /api/v1/transfers            — tạo phiếu
 *   PUT  /api/v1/transfers/{id}       — cập nhật (PENDING only)
 *   DELETE /api/v1/transfers/{id}     — hủy (PENDING only)
 *   POST /api/v1/transfers/{id}/approve — duyệt (PENDING→READY hoặc READY→ACTIVE)
 *   POST /api/v1/transfers/{id}/reject  — từ chối (PENDING|READY → REJECTED)
 */
@RestController
@RequestMapping("/api/v1/transfers")
@Tag(name = "Transfers", description = "Quản lý phiếu điều chuyển hàng giữa các kho / chi nhánh")
public class TransferController extends TransactionBaseResource<TransferRequest, TransferResponse> {

    private final TransferCommandService transferCommandService;

    public TransferController(TransferCommandService transferCommandService) {
        this.transferCommandService = transferCommandService;
    }

    @Override
    protected TransferCommandService abstractCommand() {
        return transferCommandService;
    }
}
