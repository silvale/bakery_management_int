package com.bakery.api.inventory.controller;

import java.util.List;
import java.util.UUID;

import com.bakery.api.inventory.dto.AdjustmentReportResponse;
import com.bakery.api.inventory.service.AdjustmentReportService;
import com.bakery.framework.security.RequirePermission;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 08/BE-3: thống kê điều chỉnh tồn. Quyền: màn INVENTORY_REQUESTS (VIEW). */
@RestController
@RequestMapping("/api/v1/reports/adjustments")
@RequiredArgsConstructor
@com.bakery.framework.security.RequirePermission(screen = "ADJUSTMENT_REPORT", action = "VIEW")
public class AdjustmentReportController {

    private final AdjustmentReportService service;

    @GetMapping("/summary")
    public AdjustmentReportResponse summary(@RequestParam(required = false) String month,
                                             @RequestParam(required = false) UUID warehouseId,
                                             @RequestParam(required = false) UUID itemId,
                                             @RequestParam(required = false) String reasonCode) {
        return service.summary(month, warehouseId, itemId, reasonCode);
    }

}
