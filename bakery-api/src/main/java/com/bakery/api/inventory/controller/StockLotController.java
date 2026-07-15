package com.bakery.api.inventory.controller;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import com.bakery.api.inventory.dto.StockLotResponse;
import com.bakery.api.inventory.repository.StockLotRepository;
import com.bakery.api.inventory.service.StockLotService;
import com.bakery.framework.controller.BakeryAdminResource;
import com.bakery.framework.dto.RejectRequest;
import com.bakery.framework.metadata.ReferenceValue;
import com.bakery.framework.service.BakeryAdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read-only API xem tồn kho.
 *
 * GET /api/v1/stock-lots                        → tất cả lots (phân trang)
 * GET /api/v1/stock-lots/all                    → tất cả lots (không phân trang)
 * GET /api/v1/stock-lots/{id}                   → chi tiết 1 lot
 * GET /api/v1/stock-lots?warehouse.code=MAIN    → lọc theo kho
 * GET /api/v1/stock-lots?item.code=ING-BOT-555 → lọc theo nguyên liệu
 * GET /api/v1/stock-lots/summary                → tổng tồn kho theo item × warehouse
 * GET /api/v1/stock-lots/summary?warehouseCode=MAIN → lọc theo kho cụ thể
 */
@RestController
@RequestMapping("/api/v1/stock-lots")
@RequiredArgsConstructor
public class StockLotController extends BakeryAdminResource<Void, StockLotResponse> {

    private final StockLotService service;
    private final StockLotRepository stockLotRepository;

    @Override
    protected BakeryAdminService<Void, StockLotResponse> getService() {
        return service;
    }

    // ── Block inherited mutating endpoints (stock lots are managed internally) ──

    @Override
    @PostMapping
    public ResponseEntity<StockLotResponse> create(@RequestBody Void body) {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).build();
    }

    @Override
    @PutMapping("/{id}")
    public ResponseEntity<StockLotResponse> update(@PathVariable UUID id, @RequestBody Void body) {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).build();
    }

    @Override
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).build();
    }

    @Override
    @PostMapping("/{id}/approve")
    public ResponseEntity<StockLotResponse> approve(@PathVariable UUID id) {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).build();
    }

    @Override
    @PostMapping("/{id}/reject")
    public ResponseEntity<StockLotResponse> reject(@PathVariable UUID id, @RequestBody RejectRequest request) {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).build();
    }

    /**
     * Tổng tồn kho theo item × warehouse, gộp tất cả lot.
     * Chỉ trả: item (code + name), warehouse, totalQtyRemaining.
     * Query param: warehouseCode (optional) — lọc theo kho cụ thể.
     */
    @GetMapping("/summary")
    public List<StockLotResponse> getSummary(
            @RequestParam(required = false) String warehouseCode) {
        return stockLotRepository.findStockSummaryRaw(warehouseCode).stream()
                .map(row -> {
                    StockLotResponse r = new StockLotResponse();
                    r.setItem(new ReferenceValue((String) row[0], (String) row[1]));
                    r.setWarehouse(new ReferenceValue((String) row[2], (String) row[3]));
                    r.setTotalQtyRemaining((BigDecimal) row[4]);
                    return r;
                })
                .toList();
    }
}
