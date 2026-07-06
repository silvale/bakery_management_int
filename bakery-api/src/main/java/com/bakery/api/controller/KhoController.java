package com.bakery.api.controller;

import com.bakery.api.config.UserPrincipal;
import com.bakery.api.service.GoodsTransferService;
import com.bakery.api.service.GoodsTransferService.*;
import com.bakery.api.service.PermissionCheckerService;
import com.bakery.api.service.StocktakeService;
import com.bakery.api.service.StocktakeService.*;
import com.bakery.common.entity.*;
import com.bakery.common.entity.enums.BranchType;
import com.bakery.common.repository.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/admin/kho")
@RequiredArgsConstructor
@Tag(name = "Kho", description = "Quản lý kho nguyên liệu")
public class KhoController {

    private final GoodsTransferService        goodsTransferService;
    private final PermissionCheckerService    permissionChecker;
    private final StocktakeService            stocktakeService;
    private final BranchRepository             branchRepository;
    private final IngredientStockRepository    ingredientStockRepository;
    private final IngredientStockLotRepository ingredientStockLotRepository;

    // ════════════════════════════════════════════════════════
    // NORMAL FLOW
    // ════════════════════════════════════════════════════════

    /** Tạo phiếu xuất (PENDING). Hệ thống tự round whole-unit. */
    @PostMapping("/transfers")
    @Operation(summary = "Tạo phiếu xuất NL (PENDING) — Chính/BEP/Plan tạo")
    public ResponseEntity<Map<String, Object>> createTransfer(@RequestBody Map<String, Object> body) {
        String reason    = getString(body, "transferReason", "PRODUCTION");
        String note      = getString(body, "note", null);
        String createdBy = getString(body, "createdBy", "system");
        LocalDate date   = body.containsKey("transferDate")
            ? LocalDate.parse(body.get("transferDate").toString()) : LocalDate.now();

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rawLines = (List<Map<String, Object>>) body.get("lines");
        if (rawLines == null || rawLines.isEmpty())
            return ResponseEntity.badRequest().body(Map.of("error", "lines không được trống"));

        List<TransferLineRequest> lines = rawLines.stream()
            .map(l -> new TransferLineRequest(
                l.get("ingredientCode").toString(),
                new BigDecimal(l.get("qty").toString()),
                l.containsKey("unit") ? l.get("unit").toString() : null))
            .collect(Collectors.toList());

        CreateTransferResult r = goodsTransferService.createTransfer(
            new CreateTransferRequest(date, reason, note, createdBy, lines));

        return ResponseEntity.ok(Map.of(
            "id", r.id(), "code", r.code(),
            "status", r.status(),
            "message", "Phiếu đã tạo — chờ Cường chuẩn bị."
        ));
    }

    /** Cường chuẩn bị xong → READY. BEP sẽ thấy trên màn hình KHO_BEP. */
    @PostMapping("/transfers/{id}/ready")
    @Operation(summary = "Cường mark READY — BEP sẽ thấy để đến lấy")
    public ResponseEntity<Map<String, Object>> markReady(
            @PathVariable UUID id, @RequestBody Map<String, Object> body) {
        String readyBy = getString(body, "readyBy", "cuong");
        goodsTransferService.markReady(id, readyBy);
        return ResponseEntity.ok(Map.of(
            "message", "Phiếu đã READY — BEP có thể đến lấy hàng."
        ));
    }

    /** BEP kiểm tra đủ hàng → COMPLETED (atomic -KHO_TONG +KHO_BEP). */
    @PostMapping("/transfers/{id}/complete")
    @Operation(summary = "BEP xác nhận nhận đủ → COMPLETED (inventory cập nhật atomic)")
    public ResponseEntity<Map<String, Object>> completeByBep(
            @PathVariable UUID id, @RequestBody Map<String, Object> body) {
        String confirmedBy = getString(body, "confirmedBy", "bep");
        goodsTransferService.completeByBep(id, confirmedBy);
        return ResponseEntity.ok(Map.of(
            "message", "Hoàn thành — kho đã được cập nhật."
        ));
    }

    /** BEP từ chối — lý do bắt buộc. Phiếu về REJECTED ở KHO_TONG. */
    @PostMapping("/transfers/{id}/reject")
    @Operation(summary = "BEP từ chối nhận hàng (lý do bắt buộc)")
    public ResponseEntity<Map<String, Object>> rejectByBep(
            @PathVariable UUID id, @RequestBody Map<String, Object> body) {
        String rejectedBy = getString(body, "rejectedBy", "bep");
        String reason     = getString(body, "reason", null);
        if (reason == null || reason.isBlank())
            return ResponseEntity.badRequest().body(Map.of("error", "Lý do reject bắt buộc phải có"));
        goodsTransferService.rejectByBep(id, rejectedBy, reason);
        return ResponseEntity.ok(Map.of("message", "Đã từ chối — phiếu hiện ở KHO_TONG rejected."));
    }

    /**
     * Tự động sinh phiếu xuất NL từ production plan.
     * BOM Level 2: plan → recipe_line → (ingredient | semi_product → recipe_line_semi → ingredient)
     * Phiếu được tạo với transferSource = AUTO_PLAN, status = PENDING.
     */
    @PostMapping("/transfers/auto-generate")
    @Operation(summary = "Tạo phiếu xuất NL AUTO_PLAN từ production plan (BOM Level 2)")
    public ResponseEntity<Map<String, Object>> autoGenerateFromPlan(@RequestBody Map<String, Object> body) {
        LocalDate planDate = body.containsKey("planDate")
            ? LocalDate.parse(body.get("planDate").toString()) : LocalDate.now().plusDays(1);
        String createdBy = getString(body, "createdBy", "system");

        try {
            CreateTransferResult r = goodsTransferService.autoGenerateFromPlan(planDate, createdBy);
            return ResponseEntity.ok(Map.of(
                "id", r.id(), "code", r.code(),
                "transferDate", r.transferDate().toString(),
                "status", r.status(),
                "transferSource", "AUTO_PLAN",
                "message", "Phiếu AUTO_PLAN đã tạo — Cường thấy ở màn hình KHO_TONG."
            ));
        } catch (IllegalStateException | IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Cửa hàng xác nhận nhận đủ hàng → COMPLETED (dành cho phiếu RESTOCK).
     * 1 phiếu RESTOCK có thể gồm cả ACC-* lẫn ING-* trong cùng 1 phiếu.
     * Atomic: -KHO_TONG +SHOP cho toàn bộ lines.
     */
    @PostMapping("/transfers/{id}/complete-by-shop")
    @Operation(summary = "Cửa hàng xác nhận nhận đủ hàng RESTOCK → COMPLETED (atomic -KHO_TONG +SHOP)")
    public ResponseEntity<Map<String, Object>> completeByShop(
            @PathVariable UUID id, @RequestBody Map<String, Object> body) {
        String confirmedBy = getString(body, "confirmedBy", "shop");
        goodsTransferService.completeByShop(id, confirmedBy);
        return ResponseEntity.ok(Map.of(
            "message", "Hoàn thành — tồn kho Cửa hàng đã được cập nhật."
        ));
    }

    /** Clone phiếu REJECTED → PENDING mới (Cường bù hàng). */
    @PostMapping("/transfers/{id}/clone")
    @Operation(summary = "Clone phiếu REJECTED → PENDING mới")
    public ResponseEntity<Map<String, Object>> cloneTransfer(
            @PathVariable UUID id, @RequestBody Map<String, Object> body) {
        String createdBy = getString(body, "createdBy", "cuong");
        CreateTransferResult r = goodsTransferService.cloneTransfer(id, createdBy);
        return ResponseEntity.ok(Map.of(
            "id", r.id(), "code", r.code(),
            "status", r.status(),
            "message", "Đã clone — phiếu mới PENDING chờ Cường chuẩn bị lại."
        ));
    }

    // ════════════════════════════════════════════════════════
    // ADJUSTMENT FLOW (Chính duyệt)
    // ════════════════════════════════════════════════════════

    @PostMapping("/adjustments")
    @Operation(summary = "Tạo phiếu điều chỉnh hàng mất/hư — Chính duyệt")
    public ResponseEntity<Map<String, Object>> createAdjustment(@RequestBody Map<String, Object> body) {
        String branchType = getString(body, "branchType", "KHO_TONG");
        String note       = getString(body, "note", null);
        String createdBy  = getString(body, "createdBy", "system");
        LocalDate date    = body.containsKey("date")
            ? LocalDate.parse(body.get("date").toString()) : LocalDate.now();

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rawLines = (List<Map<String, Object>>) body.get("lines");
        if (rawLines == null || rawLines.isEmpty())
            return ResponseEntity.badRequest().body(Map.of("error", "lines không được trống"));

        List<TransferLineRequest> lines = rawLines.stream()
            .map(l -> new TransferLineRequest(
                l.get("ingredientCode").toString(),
                new BigDecimal(l.get("qty").toString()),
                l.containsKey("unit") ? l.get("unit").toString() : null))
            .collect(Collectors.toList());

        CreateTransferResult r = goodsTransferService.createAdjustment(
            new CreateAdjustmentRequest(branchType, date, note, createdBy, lines));

        return ResponseEntity.ok(Map.of(
            "id", r.id(), "code", r.code(),
            "status", r.status(),
            "message", "Phiếu điều chỉnh đã tạo — chờ Chính duyệt."
        ));
    }

    /**
     * ADJUST_SUPER_APPROVE — chỉ tài khoản có can_approve trên SCREEN_INVENTORY_ADJUSTMENT
     * (hiện tại: anh Chính / SUPER_ADMIN) mới được duyệt.
     * Backend chặn cứng bất kể FE có ẩn nút hay không.
     */
    @PostMapping("/adjustments/{id}/approve")
    @Operation(summary = "Chính duyệt ADJUSTMENT → trừ kho (FEFO) — chỉ SUPER_APPROVE")
    public ResponseEntity<Map<String, Object>> approveAdjustment(
            @PathVariable UUID id,
            @RequestBody Map<String, Object> body,
            @AuthenticationPrincipal UserPrincipal principal) {

        String currentUser = principal != null ? principal.username() : "system";
        // Guard: kiểm tra can_approve tại màn hình điều chỉnh kho
        // Throws 403 nếu không đủ quyền
        permissionChecker.requireApprove(currentUser, "SCREEN_INVENTORY_ADJUSTMENT");

        String approvedBy = getString(body, "approvedBy", currentUser);
        goodsTransferService.approveAdjustment(id, approvedBy);
        return ResponseEntity.ok(Map.of("message", "Điều chỉnh đã được áp dụng vào kho."));
    }

    @PostMapping("/adjustments/{id}/reject")
    @Operation(summary = "Chính từ chối ADJUSTMENT")
    public ResponseEntity<Map<String, Object>> rejectAdjustment(
            @PathVariable UUID id, @RequestBody Map<String, Object> body) {
        String rejectedBy = getString(body, "rejectedBy", "chinh");
        String reason     = getString(body, "reason", "");
        goodsTransferService.rejectAdjustment(id, rejectedBy, reason);
        return ResponseEntity.ok(Map.of("message", "Điều chỉnh đã bị từ chối."));
    }

    /** Dashboard Chính: danh sách ADJUSTMENT cần duyệt */
    @GetMapping("/adjustments/pending")
    @Operation(summary = "Danh sách ADJUSTMENT chờ Chính duyệt (dashboard)")
    public List<Map<String, Object>> pendingAdjustments() {
        return goodsTransferService.listPendingAdjustments().stream()
            .map(this::toSummary)
            .collect(Collectors.toList());
    }

    // ════════════════════════════════════════════════════════
    // QUERIES
    // ════════════════════════════════════════════════════════

    /**
     * Màn hình KHO_TONG: PENDING (Cường chuẩn bị)
     * Màn hình KHO_BEP:  READY   (BEP đến lấy)
     *
     * Data-scope: BEP_TRUONG/BEP_VIEN/NHAN_VIEN_BH chỉ thấy phiếu của branch mình.
     * SUPER_ADMIN/KHO_TRUONG thấy tất cả.
     */
    @GetMapping("/transfers")
    @Operation(summary = "Danh sách phiếu theo status + screen (có data-scope theo branch)")
    public List<Map<String, Object>> listTransfers(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String screen,
            @AuthenticationPrincipal UserPrincipal principal) {
        String st = status != null ? status.toUpperCase() : "PENDING";
        // Scope: nếu user bị giới hạn branch → chỉ thấy phiếu of their branch
        java.util.UUID scopedBranchId = (principal != null && principal.isScopeLimited())
            ? principal.branchId() : null;
        return goodsTransferService.listByStatus(st, screen, scopedBranchId).stream()
            .map(this::toSummary)
            .collect(Collectors.toList());
    }

    @GetMapping("/transfers/{id}")
    @Operation(summary = "Chi tiết phiếu")
    public ResponseEntity<Map<String, Object>> getTransfer(@PathVariable UUID id) {
        GoodsTransfer t = goodsTransferService.getTransferWithLines(id);
        Map<String, Object> resp = new LinkedHashMap<>(toSummary(t));
        resp.put("fromBranch",      t.getFromBranch().getName());
        resp.put("toBranch",        t.getToBranch() != null ? t.getToBranch().getName() : null);
        resp.put("readyBy",         t.getReadyBy());
        resp.put("readyAt",         t.getReadyAt() != null ? t.getReadyAt().toString() : null);
        resp.put("confirmedBy",     t.getConfirmedBy());
        resp.put("confirmedAt",     t.getConfirmedAt() != null ? t.getConfirmedAt().toString() : null);
        resp.put("rejectedBy",      t.getRejectedBy());
        resp.put("rejectedAt",      t.getRejectedAt() != null ? t.getRejectedAt().toString() : null);
        resp.put("rejectionReason", t.getRejectionReason());
        resp.put("clonedFromId",    t.getClonedFromId());
        resp.put("lines", t.getLines().stream().map(l -> {
            Map<String, Object> m = new LinkedHashMap<>();
            if (l.getIngredient() != null) {
                m.put("ingredientCode", l.getIngredient().getCode());
                m.put("ingredientName", l.getIngredient().getName());
            }
            m.put("unit",           l.getUnit());
            m.put("qtyFromRecipe",  l.getQtyFromRecipe());
            m.put("qtyRequested",   l.getQtyRequested());
            m.put("avgUnitPrice",   l.getAvgUnitPrice());
            return m;
        }).collect(Collectors.toList()));
        return ResponseEntity.ok(resp);
    }

    // ════════════════════════════════════════════════════════
    // TỒN KHO
    // ════════════════════════════════════════════════════════

    @GetMapping("/stock/{type}")
    @Operation(summary = "Tồn kho NL theo kho (KHO_TONG | KHO_BEP | SHOP)")
    public ResponseEntity<Map<String, Object>> getStock(@PathVariable String type) {
        BranchType bt;
        try { bt = BranchType.valueOf(type.toUpperCase()); }
        catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "type không hợp lệ: " + type)); }

        Branch branch = branchRepository.findByBranchType(bt)
            .orElseThrow(() -> new IllegalStateException("Không tìm thấy kho: " + type));

        List<Map<String, Object>> items = ingredientStockRepository
            .findAllWithIngredientByBranchId(branch.getId()).stream()
            .map(s -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("ingredientCode", s.getIngredient().getCode());
                m.put("ingredientName", s.getIngredient().getName());
                m.put("unit",           s.getIngredient().getBaseUnit().name());
                m.put("qtyOnHand",      s.getQtyOnHand());
                m.put("qtyAvailable",   s.getQtyAvailable() != null
                    ? s.getQtyAvailable() : s.getQtyOnHand().subtract(s.getQtyReserved()));
                m.put("lastUpdated",    s.getLastUpdated().toString());
                return m;
            }).collect(Collectors.toList());

        return ResponseEntity.ok(Map.of(
            "branchType", bt.name(), "branchName", branch.getName(),
            "totalItems", items.size(), "items", items
        ));
    }

    @GetMapping("/stock/{type}/lots/{ingredientCode}")
    @Operation(summary = "FEFO detail — lô NL còn tồn")
    public ResponseEntity<Object> getLotDetail(
            @PathVariable String type, @PathVariable String ingredientCode) {
        BranchType bt;
        try { bt = BranchType.valueOf(type.toUpperCase()); }
        catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "type không hợp lệ: " + type)); }

        Branch branch = branchRepository.findByBranchType(bt).orElseThrow();
        IngredientStock stock = ingredientStockRepository
            .findAllWithIngredientByBranchId(branch.getId()).stream()
            .filter(s -> s.getIngredient().getCode().equalsIgnoreCase(ingredientCode))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException(
                "Không tìm thấy NL " + ingredientCode + " tại " + type));

        List<IngredientStockLot> lots = ingredientStockLotRepository
            .findAvailableLotsForFifo(stock.getIngredient().getId(), branch.getId());

        BigDecimal totalQty = lots.stream()
            .map(IngredientStockLot::getQtyRemaining)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        return ResponseEntity.ok(Map.of(
            "ingredientCode", stock.getIngredient().getCode(),
            "ingredientName", stock.getIngredient().getName(),
            "branchType", bt.name(), "totalQtyRemaining", totalQty,
            "lots", lots.stream().map(l -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id",               l.getId());
                m.put("importDate",       l.getImportDate().toString());
                m.put("qtyRemaining",     l.getQtyRemaining());
                m.put("unitPrice",        l.getUnitPrice());
                m.put("sourceTransferId", l.getSourceTransferId());
                return m;
            }).collect(Collectors.toList())
        ));
    }

    // ════════════════════════════════════════════════════════
    // KIỂM ĐẾM (STOCKTAKE) — áp dụng cho mọi loại kho
    // ════════════════════════════════════════════════════════

    /**
     * Nhân viên nhập số đếm thực tế → hệ thống tính hao hụt và điều chỉnh tồn kho.
     * Áp dụng cho mọi branch type: KHO_TONG, KHO_BEP, SHOP.
     * Phụ kiện (ACC-*) hay nguyên liệu thông thường đều dùng chung endpoint này.
     *
     * Body: { "stocktakeDate": "2026-07-03", "performedBy": "nv01",
     *         "items": [{"ingredientCode": "ACC-NON-HBD", "actualCount": 12}] }
     */
    @PostMapping("/stock/{type}/stocktake")
    @Operation(summary = "Kiểm đếm tồn kho thực tế — áp dụng cho KHO_TONG | KHO_BEP | SHOP")
    public ResponseEntity<Map<String, Object>> performStocktake(
            @PathVariable String type, @RequestBody Map<String, Object> body) {

        BranchType bt;
        try { bt = BranchType.valueOf(type.toUpperCase()); }
        catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "type không hợp lệ: " + type)); }

        Branch branch = branchRepository.findByBranchType(bt)
            .orElseThrow(() -> new IllegalStateException("Không tìm thấy kho: " + type));

        LocalDate stocktakeDate = body.containsKey("stocktakeDate")
            ? LocalDate.parse(body.get("stocktakeDate").toString()) : LocalDate.now();
        String performedBy = getString(body, "performedBy", "system");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rawItems = (List<Map<String, Object>>) body.get("items");
        if (rawItems == null || rawItems.isEmpty())
            return ResponseEntity.badRequest().body(Map.of("error", "items không được trống"));

        List<StocktakeLineRequest> items = rawItems.stream()
            .map(i -> new StocktakeLineRequest(
                i.get("ingredientCode").toString(),
                new BigDecimal(i.get("actualCount").toString())))
            .collect(Collectors.toList());

        StocktakeResult result = stocktakeService.reconcileStock(
            branch.getId(), stocktakeDate, items, performedBy);

        List<Map<String, Object>> lines = result.lines().stream().map(r -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("ingredientCode",  r.ingredientCode());
            m.put("ingredientName",  r.ingredientName());
            m.put("qtyOnHandBefore", r.qtyOnHandBefore());
            m.put("qtyPosSold",      r.qtyPosSold());
            m.put("qtyTheoretical",  r.qtyTheoretical());
            m.put("qtyActual",       r.qtyActual());
            m.put("qtyLoss",         r.qtyLoss());
            m.put("qtyOverage",      r.qtyOverage());
            m.put("status",          r.qtyLoss().compareTo(BigDecimal.ZERO) > 0 ? "HAS_LOSS" : "OK");
            return m;
        }).collect(Collectors.toList());

        return ResponseEntity.ok(Map.of(
            "stocktakeDate", result.stocktakeDate().toString(),
            "branchType",    bt.name(),
            "branchCode",    result.branchCode(),
            "totalItems",    result.totalItems(),
            "itemsWithLoss", result.itemsWithLoss(),
            "lines",         lines
        ));
    }

    @GetMapping("/stock/{type}/stocktake/history")
    @Operation(summary = "Lịch sử kiểm đếm N ngày gần nhất — KHO_TONG | KHO_BEP | SHOP")
    public ResponseEntity<Object> getStocktakeHistory(
            @PathVariable String type,
            @RequestParam(defaultValue = "30") int days) {

        BranchType bt;
        try { bt = BranchType.valueOf(type.toUpperCase()); }
        catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "type không hợp lệ: " + type)); }

        Branch branch = branchRepository.findByBranchType(bt)
            .orElseThrow(() -> new IllegalStateException("Không tìm thấy kho: " + type));

        List<Map<String, Object>> result = stocktakeService.getStocktakeHistory(branch.getId(), days)
            .stream().map(log -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("stocktakeDate",  log.getStocktakeDate().toString());
                m.put("ingredientCode", log.getIngredient().getCode());
                m.put("ingredientName", log.getIngredient().getName());
                m.put("periodFrom",     log.getPeriodFrom() != null ? log.getPeriodFrom().toString() : "—");
                m.put("periodTo",       log.getPeriodTo().toString());
                m.put("qtyTheoretical", log.getQtyTheoretical());
                m.put("qtyActual",      log.getQtyActual());
                m.put("qtyLoss",        log.getQtyLoss());
                m.put("qtyOverage",     log.getQtyOverage());
                m.put("createdBy",      log.getCreatedBy());
                return m;
            }).collect(Collectors.toList());

        return ResponseEntity.ok(result);
    }

    // ── Helpers ───────────────────────────────────────────────

    private Map<String, Object> toSummary(GoodsTransfer t) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id",             t.getId());
        m.put("code",           t.getCode());
        m.put("transferDate",   t.getTransferDate().toString());
        m.put("transferReason", t.getTransferReason());
        m.put("transferSource", t.getTransferSource());
        m.put("status",         t.getStatus());
        m.put("lineCount",      t.getLines().size());
        m.put("note",           t.getNote());
        m.put("createdBy",      t.getCreatedBy());
        m.put("createdAt",      t.getCreatedAt().toString());
        return m;
    }

    private String getString(Map<String, Object> body, String key, String def) {
        return body.containsKey(key) && body.get(key) != null
            ? body.get(key).toString() : def;
    }
}
