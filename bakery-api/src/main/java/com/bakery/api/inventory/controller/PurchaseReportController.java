package com.bakery.api.inventory.controller;

import java.util.List;
import java.util.UUID;

import com.bakery.api.inventory.dto.PurchaseReportRows.Shortage;
import com.bakery.api.inventory.dto.PurchaseReportRows.Summary;
import com.bakery.api.inventory.service.PurchaseReportService;
import com.bakery.framework.security.RequirePermission;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 06: báo cáo nhập hàng theo thực nhận. Quyền: màn INVENTORY_REQUESTS (VIEW). */
@RestController
@RequestMapping("/api/v1/reports/purchases")
@RequiredArgsConstructor
@com.bakery.framework.security.RequirePermission(screen = "PURCHASE_REPORT", action = "VIEW")
public class PurchaseReportController {

    private final PurchaseReportService service;

    @GetMapping("/summary")
    public List<Summary> summary(@RequestParam(required = false) String month,
                                 @RequestParam(required = false) UUID supplierId,
                                 @RequestParam(required = false) UUID itemId,
                                 @RequestParam(required = false) UUID warehouseId) {
        return service.summary(month, supplierId, itemId, warehouseId);
    }

    @GetMapping("/shortages")
    public List<Shortage> shortages(@RequestParam(required = false) String month,
                                    @RequestParam(required = false) UUID supplierId,
                                    @RequestParam(required = false) UUID itemId) {
        return service.shortages(month, supplierId, itemId);
    }
}
