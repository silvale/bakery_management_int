package com.bakery.api.cake.controller;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.bakery.api.cake.dto.CakeDesignReportRow;
import com.bakery.api.cake.service.CakeDesignReportService;
import com.bakery.framework.security.RequirePermission;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** {@code GET /api/v1/reports/cake-designs} — quyền xem theo màn báo cáo ngày. */
@RestController
@RequestMapping("/api/v1/reports/cake-designs")
@RequiredArgsConstructor
@com.bakery.framework.security.RequirePermission(screen = "CAKE_DESIGN_REPORT", action = "VIEW")
public class CakeDesignReportController {

    private final CakeDesignReportService service;

    @GetMapping
    public List<CakeDesignReportRow> report(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) UUID productId,
            @RequestParam(required = false) String groupCode) {
        return service.report(from, to, productId, groupCode == null || groupCode.isBlank() ? null : groupCode);
    }
}
