package com.bakery.api.modules.production.controllers;

import com.bakery.api.framework.services.*;
import com.bakery.api.framework.enums.BranchType;
import com.bakery.api.framework.*;
import com.bakery.api.framework.enums.LotCostStatus;
import com.bakery.api.framework.enums.LotStatus;
import com.bakery.api.framework.repositories.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;
import com.bakery.api.modules.inventory.services.FifoEngine;
import com.bakery.api.modules.masterdata.entities.Branch;
import com.bakery.api.modules.masterdata.entities.Product;
import com.bakery.api.modules.masterdata.entities.ProductExpiryConfig;
import com.bakery.api.modules.masterdata.repositories.BranchRepository;
import com.bakery.api.modules.masterdata.repositories.ProductExpiryConfigRepository;
import com.bakery.api.modules.masterdata.repositories.ProductRepository;
import com.bakery.api.modules.production.entities.ProductionLot;
import com.bakery.api.modules.production.repositories.ProductionLotRepository;
import com.bakery.api.modules.production.repositories.ProductionTemplateRepository;

@Slf4j
@RestController
@RequestMapping("/phase3")
@RequiredArgsConstructor
@Tag(name = "Phase3", description = "File TXT, BanhRaNgay, ProductionLot, FIFO, Hủy bánh")
public class Phase3Controller {

    private final FifoEngine                    fifoEngine;
    private final ProductionLotRepository       productionLotRepository;
    private final ProductionTemplateRepository  templateRepository;
    private final ProductExpiryConfigRepository expiryConfigRepository;
    private final BranchRepository              branchRepository;
    private final ProductRepository             productRepository;

    // ── File TXT ─────────────────────────────────────────────

    @PostMapping("/txt/import")
    @Operation(summary = "[Deprecated V12] Import file TXT tồn kho")
    public ResponseEntity<Map<String, Object>> importTxt(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.status(410).body(Map.of(
            "status", "DEPRECATED",
            "message", "V12: Import TXT không còn dùng. Dùng InventoryAdjustment UI."
        ));
    }

    // ── BanhRaNgay Generator ──────────────────────────────────

    @PostMapping("/banh-ra-ngay/generate")
    @Operation(summary = "[Deprecated V12] Auto-gen BanhRaNgay.xlsx")
    public ResponseEntity<Map<String, Object>> generateBanhRaNgay(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate forDate) {
        return ResponseEntity.status(410).body(Map.of(
            "status", "DEPRECATED",
            "message", "V12: BanhRaNgay không còn xuất Excel. Dùng ProductionPlan UI."
        ));
    }

    // ── Production Lot ────────────────────────────────────────

    @PostMapping("/lots")
    @Operation(summary = "Bếp khai báo lô sản xuất → FIFO cost + barcode")
    public ResponseEntity<Map<String, Object>> createProductionLot(
            @RequestBody Map<String, Object> body) {

        String productCode   = body.get("productCode").toString();
        BigDecimal qtyProduced = new BigDecimal(body.get("qtyProduced").toString());
        LocalDate prodDate   = body.containsKey("productionDate")
                ? LocalDate.parse(body.get("productionDate").toString())
                : LocalDate.now();

        Product product = productRepository.findByCode(productCode)
                .orElseThrow(() -> new IllegalArgumentException("SP không tìm thấy: " + productCode));

        Branch kitchenBranch = branchRepository.findByBranchType(BranchType.KHO_BEP).orElseThrow();
        Branch shopBranch    = branchRepository.findByBranchType(BranchType.SHOP).orElseThrow();

        // Tính HSD
        int shelfDays = expiryConfigRepository.findByProductId(product.getId())
                .map(ProductExpiryConfig::getShelfDays).orElse(1);
        LocalDate expiryDate = prodDate.plusDays(shelfDays);

        // Sinh lot_number
        long seq = productionLotRepository.countByProductAndDate(product.getId(), prodDate) + 1;
        String lotNumber = String.format("LOT-%s-%s-%03d",
                prodDate.format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd")),
                productCode, seq);

        // Tạo ProductionLot tại Kho Bếp
        ProductionLot lot = ProductionLot.builder()
                .lotNumber(lotNumber)
                .product(product)
                .branch(kitchenBranch)
                .productionDate(prodDate)
                .expiryDate(expiryDate)
                .qtyProduced(qtyProduced)
                .costStatus(LotCostStatus.CONFIRMED)
                .status(LotStatus.ACTIVE)
                .build();
        productionLotRepository.save(lot);

        // Chạy FIFO
        FifoEngine.FifoResult fifoResult = fifoEngine.allocate(lot, kitchenBranch);

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("lotNumber",           lotNumber);
        resp.put("productCode",         productCode);
        resp.put("qtyProduced",         qtyProduced);
        resp.put("productionDate",      prodDate.toString());
        resp.put("expiryDate",          expiryDate.toString());
        resp.put("costPerUnit",         fifoResult.costPerUnit());
        resp.put("costStatus",          fifoResult.costStatus().name());
        resp.put("hasPending",          fifoResult.hasPending());
        resp.put("pendingIngredients",  fifoResult.pendingIngredients());
        return ResponseEntity.ok(resp);
    }

    @GetMapping("/lots/expiring")
    @Operation(summary = "Danh sách lô bánh sắp hết hạn — in để nhân viên hủy")
    public List<Map<String, Object>> getExpiringLots(
            @RequestParam(defaultValue = "1") int days) {

        Branch shopBranch = branchRepository.findByBranchType(BranchType.KHO_BEP).orElseThrow();
        LocalDate today = LocalDate.now();
        LocalDate warningDate = today.plusDays(days);

        return productionLotRepository
                .findExpiringLots(shopBranch.getId(), today, warningDate)
                .stream()
                .map(lot -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("lotNumber",      lot.getLotNumber());
                    m.put("productCode",    lot.getProduct().getCode());
                    m.put("productName",    lot.getProduct().getName());
                    m.put("qtyProduced",    lot.getQtyProduced());
                    m.put("qtyRemaining",   lot.getQtyRemaining() != null ? lot.getQtyRemaining() : BigDecimal.ZERO);
                    m.put("productionDate", lot.getProductionDate().toString());
                    m.put("expiryDate",     lot.getExpiryDate().toString());
                    m.put("isExpiredToday", lot.getExpiryDate().equals(LocalDate.now()));
                    m.put("costPerUnit",    lot.getCostPerUnit());
                    return m;
                })
                .collect(Collectors.toList());
    }

    @PostMapping("/lots/{lotNumber}/cancel")
    @Operation(summary = "Ghi nhận số lượng bánh đã hủy theo lô")
    public ResponseEntity<Map<String, Object>> cancelLot(
            @PathVariable String lotNumber,
            @RequestBody Map<String, Object> body) {

        BigDecimal qtyCancelled = new BigDecimal(body.get("qtyCancelled").toString());

        ProductionLot lot = productionLotRepository.findByLotNumber(lotNumber)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy lô: " + lotNumber));

        BigDecimal newCancelled = lot.getQtyCancelled().add(qtyCancelled);
        lot.setQtyCancelled(newCancelled);

        BigDecimal remaining = lot.getQtyProduced()
                .subtract(lot.getQtySold())
                .subtract(newCancelled);

        lot.setStatus(remaining.compareTo(BigDecimal.ZERO) <= 0
                ? LotStatus.CANCELLED : LotStatus.PARTIAL);

        productionLotRepository.save(lot);

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("lotNumber",    lot.getLotNumber());
        resp.put("qtyCancelled", lot.getQtyCancelled());
        resp.put("qtyRemaining", remaining.max(BigDecimal.ZERO));
        resp.put("status",       lot.getStatus().name());
        return ResponseEntity.ok(resp);
    }

    @GetMapping("/lots/pending-cost")
    @Operation(summary = "Lô bánh có cost PENDING (tồn kho âm khi sản xuất)")
    public List<Map<String, Object>> getPendingCostLots() {
        return productionLotRepository.findPendingCostLots().stream()
                .map(lot -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("lotNumber",      lot.getLotNumber());
                    m.put("productCode",    lot.getProduct().getCode());
                    m.put("productName",    lot.getProduct().getName());
                    m.put("productionDate", lot.getProductionDate().toString());
                    m.put("costStatus",     lot.getCostStatus().name());
                    m.put("costPerUnit",    lot.getCostPerUnit());
                    return m;
                })
                .collect(Collectors.toList());
    }

    // ── Templates ─────────────────────────────────────────────

    @GetMapping("/templates")
    @Operation(summary = "Danh sách template số lượng sản xuất")
    public List<Map<String, Object>> getTemplates() {
        return templateRepository.findAllByIsActiveTrue().stream()
                .map(t -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id",          t.getId());
                    m.put("productCode", t.getProduct().getCode());
                    m.put("productName", t.getProduct().getName());
                    m.put("defaultQty",  t.getDefaultQty());
                    return m;
                })
                .collect(Collectors.toList());
    }

    @PutMapping("/templates/{id}")
    @Operation(summary = "Cập nhật template số lượng")
    public ResponseEntity<Map<String, Object>> updateTemplate(
            @PathVariable UUID id,
            @RequestBody Map<String, Object> body) {

        var tmpl = templateRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy template: " + id));

        tmpl.setDefaultQty(new BigDecimal(body.get("defaultQty").toString()));
        templateRepository.save(tmpl);

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("id",          tmpl.getId());
        resp.put("productCode", tmpl.getProduct().getCode());
        resp.put("defaultQty",  tmpl.getDefaultQty());
        return ResponseEntity.ok(resp);
    }
}