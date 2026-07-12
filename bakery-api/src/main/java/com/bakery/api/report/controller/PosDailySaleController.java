package com.bakery.api.report.controller;

import java.time.LocalDate;
import java.util.List;

import com.bakery.api.report.entity.PosDailySale;
import com.bakery.api.report.service.PosDailySaleService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * API upload và xem dữ liệu POS.
 *
 * POST /api/v1/pos-sales/upload?saleDate=2026-07-10  → upload file Excel POS
 * GET  /api/v1/pos-sales?saleDate=2026-07-10         → xem data POS theo ngày
 */
@RestController
@RequestMapping("/api/v1/pos-sales")
@RequiredArgsConstructor
public class PosDailySaleController {

    private final PosDailySaleService service;

    @GetMapping
    public List<PosDailySale> getBySaleDate(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate saleDate) {
        return service.findBySaleDate(saleDate);
    }

    @PostMapping("/upload")
    public PosDailySaleService.UploadResult upload(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate saleDate,
            @RequestParam MultipartFile file) {
        return service.upload(saleDate, file);
    }
}
