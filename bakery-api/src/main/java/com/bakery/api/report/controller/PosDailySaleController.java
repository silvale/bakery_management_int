package com.bakery.api.report.controller;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import com.bakery.api.report.service.PosDailySaleService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import com.bakery.framework.security.RequirePermission;

/**
 * API upload và xem dữ liệu POS.
 *
 * POST /api/v1/pos-sales/upload?saleDate=2026-07-10  → upload file Excel POS
 * GET  /api/v1/pos-sales?saleDate=2026-07-10         → xem data POS theo ngày
 */
@RestController
@RequestMapping("/api/v1/pos-sales")
@RequiredArgsConstructor
@RequirePermission(screen = "POS_SALES", action = "VIEW")
public class PosDailySaleController {

    private final PosDailySaleService service;

    @GetMapping
    public List<Map<String, Object>> getBySaleDate(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate saleDate) {
        return service.findBySaleDateMapped(saleDate);
    }

    @PostMapping("/upload")
    @RequirePermission(screen = "POS_SALES", action = "CREATE")
    public PosDailySaleService.UploadResult upload(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate saleDate,
            @RequestParam MultipartFile file) {
        return service.upload(saleDate, file);
    }
}
