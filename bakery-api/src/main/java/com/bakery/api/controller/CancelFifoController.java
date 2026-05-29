package com.bakery.api.controller;

import com.bakery.api.service.CancelFifoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * API quản lý hủy bánh FIFO.
 *
 * POST /cancel/process          — Xử lý hủy 1 sản phẩm
 * POST /cancel/process-batch    — Xử lý hủy nhiều sản phẩm cùng lúc
 */
@Slf4j
@RestController
@RequestMapping("/cancel")
@RequiredArgsConstructor
@Tag(name = "Cancel FIFO", description = "Hủy bánh FIFO theo thứ tự gần hết hạn trước")
public class CancelFifoController {

    private final CancelFifoService cancelFifoService;

    /**
     * POST /cancel/process
     * Xử lý hủy 1 sản phẩm.
     *
     * Body:
     * {
     *   "productCode": "SP022575",
     *   "qtyToCancel": 5,
     *   "cancelDate": "2026-05-29",   // optional, default = today
     *   "branchId": "uuid"
     * }
     */
    @PostMapping("/process")
    @Operation(summary = "Hủy bánh FIFO cho 1 sản phẩm",
               description = "Trừ lô cũ nhất (gần hết HSD) trước. Trả kết quả chi tiết + cảnh báo nếu thiếu.")
    public ResponseEntity<CancelFifoService.CancelResult> processCancellation(
            @RequestBody CancelRequest request) {

        LocalDate cancelDate = request.cancelDate() != null ? request.cancelDate() : LocalDate.now();

        log.info("POST /cancel/process | SP={} | qty={} | date={}",
            request.productCode(), request.qtyToCancel(), cancelDate);

        CancelFifoService.CancelResult result = cancelFifoService.processCancellation(
            request.productCode(),
            request.qtyToCancel(),
            cancelDate,
            request.branchId()
        );

        return ResponseEntity.ok(result);
    }

    /**
     * POST /cancel/process-batch
     * Hủy nhiều sản phẩm cùng lúc (từ danh sách NV nộp).
     *
     * Body: danh sách CancelRequest
     */
    @PostMapping("/process-batch")
    @Operation(summary = "Hủy bánh FIFO cho nhiều sản phẩm",
               description = "Batch: xử lý danh sách hủy từ NV. Trả tổng kết quả + chi tiết từng SP.")
    public ResponseEntity<BatchCancelResponse> processBatch(
            @RequestBody List<CancelRequest> requests) {

        log.info("POST /cancel/process-batch | {} sản phẩm", requests.size());

        List<CancelFifoService.CancelResult> results = requests.stream()
            .map(r -> {
                LocalDate cancelDate = r.cancelDate() != null ? r.cancelDate() : LocalDate.now();
                try {
                    return cancelFifoService.processCancellation(
                        r.productCode(), r.qtyToCancel(), cancelDate, r.branchId()
                    );
                } catch (Exception e) {
                    log.error("Lỗi hủy SP {}: {}", r.productCode(), e.getMessage());
                    return new CancelFifoService.CancelResult(
                        r.productCode(), r.qtyToCancel(),
                        BigDecimal.ZERO, r.qtyToCancel(),
                        BigDecimal.ZERO, "ERROR",
                        List.of("Lỗi: " + e.getMessage())
                    );
                }
            })
            .toList();

        long okCount      = results.stream().filter(r -> "OK".equals(r.status())).count();
        long warningCount = results.stream().filter(CancelFifoService.CancelResult::hasWarning).count();

        return ResponseEntity.ok(new BatchCancelResponse(
            results.size(), (int) okCount, (int) warningCount, results
        ));
    }

    // ── Request / Response records ─────────────────────────────

    public record CancelRequest(
        String     productCode,
        BigDecimal qtyToCancel,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate cancelDate,
        UUID       branchId
    ) {}

    public record BatchCancelResponse(
        int                              totalProducts,
        int                              okCount,
        int                              warningCount,
        List<CancelFifoService.CancelResult> details
    ) {}
}
