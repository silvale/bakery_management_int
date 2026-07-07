package com.bakery.api.modules.inventory.services;

import com.bakery.api.framework.*;
import com.bakery.api.framework.enums.BranchType;
import com.bakery.api.framework.enums.ReferenceType;
import com.bakery.api.framework.enums.TransactionType;
import com.bakery.api.framework.repositories.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;
import com.bakery.api.modules.inventory.entities.GoodsTransfer;
import com.bakery.api.modules.inventory.entities.GoodsTransferLine;
import com.bakery.api.modules.inventory.entities.IngredientStock;
import com.bakery.api.modules.inventory.entities.IngredientStockLot;
import com.bakery.api.modules.inventory.entities.InventoryMovement;
import com.bakery.api.modules.inventory.repositories.GoodsTransferRepository;
import com.bakery.api.modules.inventory.repositories.IngredientStockLotRepository;
import com.bakery.api.modules.inventory.repositories.IngredientStockRepository;
import com.bakery.api.modules.inventory.repositories.InventoryMovementRepository;
import com.bakery.api.modules.masterdata.entities.Branch;
import com.bakery.api.modules.masterdata.entities.Ingredient;
import com.bakery.api.modules.masterdata.entities.Recipe;
import com.bakery.api.modules.masterdata.entities.RecipeLine;
import com.bakery.api.modules.masterdata.entities.RecipeLineSemi;
import com.bakery.api.modules.masterdata.entities.SemiProduct;
import com.bakery.api.modules.masterdata.repositories.BranchRepository;
import com.bakery.api.modules.masterdata.repositories.IngredientRepository;
import com.bakery.api.modules.masterdata.repositories.RecipeLineSemiRepository;
import com.bakery.api.modules.masterdata.repositories.RecipeRepository;
import com.bakery.api.modules.masterdata.repositories.SemiProductRepository;
import com.bakery.api.modules.production.entities.ProductionPlan;
import com.bakery.api.modules.production.entities.ProductionPlanLine;
import com.bakery.api.modules.production.repositories.ProductionPlanRepository;

/**
 * GoodsTransfer service — flow V13.
 *
 * Normal flow:
 *   createTransfer() → PENDING (Cường thấy ở KHO_TONG)
 *   markReady()      → READY   (BEP thấy ở KHO_BEP)
 *   completeByBep()  → COMPLETED: atomic FEFO -from_branch +to_branch
 *   rejectByBep()    → REJECTED (lý do bắt buộc, Cường thấy ở KHO_TONG)
 *   cloneTransfer()  → PENDING mới từ phiếu REJECTED
 *
 * ADJUSTMENT flow:
 *   createAdjustment() → PENDING (Chính thấy trên dashboard)
 *   approveAdjustment()→ COMPLETED: FEFO -from_branch (không có +)
 *   rejectAdjustment() → REJECTED
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GoodsTransferService {

    private final BranchRepository              branchRepository;
    private final IngredientRepository          ingredientRepository;
    private final IngredientStockRepository     ingredientStockRepository;
    private final IngredientStockLotRepository  ingredientStockLotRepository;
    private final GoodsTransferRepository       goodsTransferRepository;
    private final InventoryMovementRepository   inventoryMovementRepository;
    private final ActivityLogRepository         activityLogRepository;

    // ── BOM expansion (autoGenerateFromPlan) ──────────────────
    private final RecipeRepository              recipeRepository;
    private final RecipeLineSemiRepository      recipeLineSemiRepository;
    private final SemiProductRepository         semiProductRepository;
    private final ProductionPlanRepository      productionPlanRepository;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    // ── DTOs ──────────────────────────────────────────────────

    public record TransferLineRequest(
        String     ingredientCode,
        BigDecimal qty,           // qty thực tế muốn xuất (hệ thống sẽ round nếu whole-unit)
        String     unit
    ) {}

    public record CreateTransferRequest(
        LocalDate  transferDate,
        String     transferReason,
        String     note,
        String     createdBy,
        List<TransferLineRequest> lines
    ) {}

    public record CreateTransferResult(UUID id, String code, LocalDate transferDate, String status) {}

    // ── 1. Create (PENDING) ────────────────────────────────────

    @Transactional
    public CreateTransferResult createTransfer(CreateTransferRequest req) {
        String reason = req.transferReason() != null
            ? req.transferReason().toUpperCase() : "PRODUCTION";

        if ("ADJUSTMENT".equals(reason)) {
            throw new IllegalArgumentException(
                "Dùng createAdjustment() cho ADJUSTMENT");
        }

        Branch fromBranch = resolveBranch(reason, true);
        Branch toBranch   = resolveBranch(reason, false);
        LocalDate date    = req.transferDate() != null ? req.transferDate() : LocalDate.now();
        String code       = generateCode(date);

        GoodsTransfer transfer = GoodsTransfer.builder()
            .code(code)
            .fromBranch(fromBranch)
            .toBranch(toBranch)
            .transferDate(date)
            .transferReason(reason)
            .note(req.note())
            .status("PENDING")
            .createdBy(req.createdBy() != null ? req.createdBy() : "system")
            .build();

        for (TransferLineRequest lr : req.lines()) {
            Ingredient ing = ingredientRepository.findByCode(lr.ingredientCode())
                .orElseThrow(() -> new IllegalArgumentException(
                    "Không tìm thấy NL: " + lr.ingredientCode()));

            String unit          = lr.unit() != null ? lr.unit() : ing.getBaseUnit().name();
            BigDecimal qtyNeeded = lr.qty();
            BigDecimal qtyActual = applyWholeUnitRounding(ing, qtyNeeded);

            GoodsTransferLine line = GoodsTransferLine.builder()
                .transfer(transfer)
                .ingredient(ing)
                .unit(unit)
                .qtyFromRecipe(qtyNeeded)       // lượng công thức cần
                .qtyRequested(qtyActual)        // lượng thực xuất (sau rounding)
                .build();
            transfer.getLines().add(line);
        }

        goodsTransferRepository.save(transfer);
        log.info("Tạo phiếu {} | {} | {} lines", code, reason, transfer.getLines().size());
        return new CreateTransferResult(transfer.getId(), code, date, "PENDING");
    }

    // ── 2. Mark READY (Cường chuẩn bị xong) ──────────────────

    @Transactional
    public void markReady(UUID id, String readyBy) {
        GoodsTransfer t = findOrThrow(id);
        requireStatus(t, "PENDING");

        t.setStatus("READY");
        t.setReadyBy(readyBy);
        t.setReadyAt(OffsetDateTime.now());
        goodsTransferRepository.save(t);

        log(t, readyBy, "MARK_READY", "PENDING", "READY", null);
        log.info("Phiếu {} READY bởi {}", t.getCode(), readyBy);
    }

    // ── 3. Complete (BEP approve — atomic inventory) ──────────

    /**
     * BEP kiểm tra đủ hàng → approve.
     * Atomic: FEFO deduct from_branch + credit to_branch trong 1 transaction.
     */
    @Transactional
    public void completeByBep(UUID id, String confirmedBy) {
        GoodsTransfer t = goodsTransferRepository.findByIdWithLines(id)
            .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy phiếu: " + id));
        requireStatus(t, "READY");

        processInventory(t, confirmedBy);   // atomic FEFO

        t.setStatus("COMPLETED");
        t.setConfirmedBy(confirmedBy);
        t.setConfirmedAt(OffsetDateTime.now());
        goodsTransferRepository.save(t);

        log(t, confirmedBy, "COMPLETE_BY_BEP", "READY", "COMPLETED", null);
        log.info("Phiếu {} COMPLETED bởi BEP {}", t.getCode(), confirmedBy);
    }

    // ── 3b. Complete by SHOP (nhân viên CH Accept phiếu RESTOCK) ──

    /**
     * Nhân viên Cửa hàng bấm "Xác nhận nhận hàng" cho phiếu RESTOCK phụ kiện.
     * Atomic: FEFO deduct KHO_TONG + credit ingredient_stock[SHOP].
     *
     * Điều kiện: phiếu phải ở status READY và transfer_reason = RESTOCK.
     */
    @Transactional
    public void completeByShop(UUID id, String confirmedBy) {
        GoodsTransfer t = goodsTransferRepository.findByIdWithLines(id)
            .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy phiếu: " + id));
        requireStatus(t, "READY");

        if (!"RESTOCK".equals(t.getTransferReason())) {
            throw new IllegalStateException(
                "completeByShop chỉ dùng cho phiếu RESTOCK, phiếu này là: " + t.getTransferReason());
        }

        processInventory(t, confirmedBy);   // atomic FEFO deduct + credit SHOP

        t.setStatus("COMPLETED");
        t.setConfirmedBy(confirmedBy);
        t.setConfirmedAt(OffsetDateTime.now());
        goodsTransferRepository.save(t);

        log(t, confirmedBy, "COMPLETE_BY_SHOP", "READY", "COMPLETED", null);
        log.info("Phiếu RESTOCK {} COMPLETED bởi SHOP {}", t.getCode(), confirmedBy);
    }

    // ── 4. Reject (BEP từ chối — lý do bắt buộc) ─────────────

    @Transactional
    public void rejectByBep(UUID id, String rejectedBy, String reason) {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("Lý do reject bắt buộc phải có");
        }
        GoodsTransfer t = findOrThrow(id);
        requireStatus(t, "READY");

        t.setStatus("REJECTED");
        t.setRejectedBy(rejectedBy);
        t.setRejectedAt(OffsetDateTime.now());
        t.setRejectionReason(reason);
        goodsTransferRepository.save(t);

        log(t, rejectedBy, "REJECT_BY_BEP", "READY", "REJECTED", reason);
        log.info("Phiếu {} REJECTED bởi BEP {} | lý do: {}", t.getCode(), rejectedBy, reason);
    }

    // ── 5. Clone từ REJECTED ──────────────────────────────────

    @Transactional
    public CreateTransferResult cloneTransfer(UUID rejectedId, String createdBy) {
        GoodsTransfer src = goodsTransferRepository.findByIdWithLines(rejectedId)
            .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy phiếu: " + rejectedId));

        if (!"REJECTED".equals(src.getStatus())) {
            throw new IllegalStateException("Chỉ clone được phiếu REJECTED");
        }

        LocalDate date = LocalDate.now();
        String code    = generateCode(date);

        GoodsTransfer clone = GoodsTransfer.builder()
            .code(code)
            .fromBranch(src.getFromBranch())
            .toBranch(src.getToBranch())
            .transferDate(date)
            .transferReason(src.getTransferReason())
            .note("Clone từ " + src.getCode() + " | " +
                  (src.getRejectionReason() != null ? src.getRejectionReason() : ""))
            .status("PENDING")
            .createdBy(createdBy != null ? createdBy : "system")
            .clonedFromId(src.getId())
            .build();

        for (GoodsTransferLine srcLine : src.getLines()) {
            clone.getLines().add(GoodsTransferLine.builder()
                .transfer(clone)
                .ingredient(srcLine.getIngredient())
                .product(srcLine.getProduct())
                .unit(srcLine.getUnit())
                .qtyFromRecipe(srcLine.getQtyFromRecipe())
                .qtyRequested(srcLine.getQtyRequested())   // giữ qty cũ
                .note(srcLine.getNote())
                .build());
        }

        goodsTransferRepository.save(clone);
        log.info("Clone phiếu {} → {} bởi {}", src.getCode(), code, createdBy);
        return new CreateTransferResult(clone.getId(), code, date, "PENDING");
    }

    // ── 6. Auto-generate từ Production Plan ───────────────────

    /**
     * Tự động tạo phiếu chuyển kho KHO_TONG → KHO_BEP từ production plan.
     *
     * BOM Level 2 expansion:
     *   plan_line có product → recipe_line → ingredient (trực tiếp)
     *                        → semi_product → recipe_line_semi → ingredient
     *   plan_line không có product (GROUP_SUBTRACT / LAN_MAM) → parse note lấy semi_product_code
     *                        → recipe_line_semi → ingredient (treat qtyPlanned = số mẻ)
     *
     * Tất cả NL được aggregate theo ingredient.code, sau đó apply whole-unit rounding.
     * Phiếu được tạo với transferSource = AUTO_PLAN, status = PENDING.
     *
     * @param planDate   ngày kế hoạch sản xuất
     * @param createdBy  người tạo (system nếu tự động)
     */
    @Transactional
    public CreateTransferResult autoGenerateFromPlan(LocalDate planDate, String createdBy) {
        ProductionPlan plan = productionPlanRepository.findByPlanDate(planDate)
            .orElseThrow(() -> new IllegalStateException(
                "Chưa có production plan cho ngày " + planDate + " — hãy generateDailyPlan() trước"));

        ProductionPlan planWithLines = productionPlanRepository.findByIdWithLines(plan.getId())
            .orElseThrow();

        // ingredient.code → accumulated qty (KG hoặc unit gốc)
        Map<String, BigDecimal> qtyByCode  = new LinkedHashMap<>();
        Map<String, Ingredient> ingByCode  = new LinkedHashMap<>();

        for (ProductionPlanLine line : planWithLines.getLines()) {
            BigDecimal qty = line.getEffectiveQty();
            if (qty == null || qty.compareTo(BigDecimal.ZERO) <= 0) continue;

            if (line.getProduct() != null) {
                expandProductBom(line.getProduct().getId(), qty, qtyByCode, ingByCode, planDate);
            } else {
                // GROUP_SUBTRACT / LAN_MAM: note = "GROUP_SUBTRACT | semi: SF-xxx"
                expandSemiFromNote(line.getNote(), qty, qtyByCode, ingByCode);
            }
        }

        if (ingByCode.isEmpty()) {
            throw new IllegalStateException(
                "Không tính ra NL nào từ plan " + planDate + " — kiểm tra recipe và plan lines");
        }

        Branch fromBranch = branchRepository.findByBranchType(BranchType.KHO_TONG)
            .orElseThrow(() -> new IllegalStateException("Không tìm thấy kho KHO_TONG"));
        Branch toBranch = branchRepository.findByBranchType(BranchType.KHO_BEP)
            .orElseThrow(() -> new IllegalStateException("Không tìm thấy kho KHO_BEP"));

        String code = generateCode(planDate);

        GoodsTransfer transfer = GoodsTransfer.builder()
            .code(code)
            .fromBranch(fromBranch)
            .toBranch(toBranch)
            .transferDate(planDate)
            .transferReason("PRODUCTION")
            .transferSource("AUTO_PLAN")
            .status("PENDING")
            .note("Auto-generated từ production plan " + planDate)
            .createdBy(createdBy != null ? createdBy : "system")
            .build();

        for (Map.Entry<String, BigDecimal> entry : qtyByCode.entrySet()) {
            Ingredient ing = ingByCode.get(entry.getKey());
            BigDecimal qtyNeeded = entry.getValue();
            BigDecimal qtyActual = applyWholeUnitRounding(ing, qtyNeeded);

            transfer.getLines().add(GoodsTransferLine.builder()
                .transfer(transfer)
                .ingredient(ing)
                .unit(ing.getBaseUnit().name())
                .qtyFromRecipe(qtyNeeded)
                .qtyRequested(qtyActual)
                .note("plan " + planDate)
                .build());
        }

        goodsTransferRepository.save(transfer);
        log.info("AUTO_PLAN: tạo phiếu {} | plan {} | {} NL",
            code, planDate, transfer.getLines().size());
        return new CreateTransferResult(transfer.getId(), code, planDate, "PENDING");
    }

    /**
     * BOM expansion Level 2 cho sản phẩm có công thức.
     *
     * Với mỗi recipe_line:
     *   - ingredient trực tiếp: qty_gram * qtyPlanned / 1000 (→ KG)
     *   - semi_product: tính số mẻ = (qty_gram * qtyPlanned / 1000) / totalYieldKg
     *                  rồi nhân qtyInBatch của từng NL trong mẻ
     */
    private void expandProductBom(UUID productId, BigDecimal qtyPlanned,
            Map<String, BigDecimal> qtyMap, Map<String, Ingredient> ingMap, LocalDate planDate) {

        Optional<Recipe> recipeOpt = recipeRepository.findActiveWithLines(productId);
        if (recipeOpt.isEmpty()) {
            log.warn("Không có recipe cho product {} — bỏ qua khi tính BOM", productId);
            return;
        }

        for (RecipeLine rl : recipeOpt.get().getLines()) {
            if (rl.getIngredient() != null) {
                // Level 1: ingredient trực tiếp
                BigDecimal qtyKg = qtyPlanned
                    .multiply(rl.getQuantityGram())
                    .divide(BigDecimal.valueOf(1_000), 6, RoundingMode.HALF_UP);
                accumulateIngredient(rl.getIngredient(), qtyKg, qtyMap, ingMap);

            } else if (rl.getSemiProduct() != null) {
                // Level 2: bán thành phẩm → expand sang NL thô
                SemiProduct sp = rl.getSemiProduct();
                BigDecimal totalSemiKg = qtyPlanned
                    .multiply(rl.getQuantityGram())
                    .divide(BigDecimal.valueOf(1_000), 6, RoundingMode.HALF_UP);

                if (sp.getTotalYieldKg() == null
                        || sp.getTotalYieldKg().compareTo(BigDecimal.ZERO) == 0) {
                    log.warn("SemiProduct {} có total_yield_kg = 0 — bỏ qua", sp.getCode());
                    continue;
                }

                BigDecimal numBatches = totalSemiKg.divide(
                    sp.getTotalYieldKg(), 6, RoundingMode.CEILING);

                List<RecipeLineSemi> semiLines =
                    recipeLineSemiRepository.findAllBySemiProductId(sp.getId());

                for (RecipeLineSemi srl : semiLines) {
                    BigDecimal ingQty = numBatches.multiply(srl.getQtyInBatch());
                    accumulateIngredient(srl.getIngredient(), ingQty, qtyMap, ingMap);
                }
            }
        }
    }

    /**
     * Mở rộng BOM cho plan_line không có product (GROUP_SUBTRACT / LAN_MAM).
     *
     * Note format: "GROUP_SUBTRACT | semi: SF-PANA-PHOI"
     *              "LAN_MAM | formula: FORMULA_BENTO | ..."
     *
     * Với GROUP_SUBTRACT: parse semi_product_code, treat qtyPlanned = số mẻ.
     * LAN_XUAT / LAN_MAM không có semi_product_code rõ → log WARN, bỏ qua.
     *
     * TODO: khi recipe có đủ product link cho LAN_XUAT, chuyển sang expandProductBom.
     */
    private void expandSemiFromNote(String note, BigDecimal numBatches,
            Map<String, BigDecimal> qtyMap, Map<String, Ingredient> ingMap) {

        if (note == null) return;

        if (note.contains("semi: ")) {
            String semiCode = note.substring(note.indexOf("semi: ") + 6).trim();
            // Nếu có pipe tiếp theo, cắt bỏ
            if (semiCode.contains("|")) {
                semiCode = semiCode.substring(0, semiCode.indexOf("|")).trim();
            }

            String finalSemiCode = semiCode;
            semiProductRepository.findByCode(finalSemiCode).ifPresentOrElse(sp -> {
                List<RecipeLineSemi> semiLines =
                    recipeLineSemiRepository.findAllBySemiProductId(sp.getId());

                if (semiLines.isEmpty()) {
                    log.warn("SemiProduct {} không có recipe_line_semi — bỏ qua BOM", finalSemiCode);
                    return;
                }

                for (RecipeLineSemi srl : semiLines) {
                    BigDecimal ingQty = numBatches.multiply(srl.getQtyInBatch());
                    accumulateIngredient(srl.getIngredient(), ingQty, qtyMap, ingMap);
                }
            }, () -> log.warn("Không tìm thấy semi_product code='{}' trong DB", finalSemiCode));

        } else if (note.startsWith("LAN_XUAT")) {
            // LAN_XUAT không link semi_product trực tiếp trong plan line
            // TODO: khi có product code trong output_yield_mapping thì gọi expandProductBom
            log.debug("Bỏ qua BOM expansion cho LAN_XUAT line: {}", note);
        }
    }

    /** Cộng dồn qty của 1 ingredient vào map. */
    private void accumulateIngredient(Ingredient ing, BigDecimal qty,
            Map<String, BigDecimal> qtyMap, Map<String, Ingredient> ingMap) {
        if (ing == null || qty == null || qty.compareTo(BigDecimal.ZERO) <= 0) return;
        qtyMap.merge(ing.getCode(), qty, BigDecimal::add);
        ingMap.putIfAbsent(ing.getCode(), ing);
    }

    // ── ADJUSTMENT flow ────────────────────────────────────────

    public record CreateAdjustmentRequest(
        String     branchType,    // KHO_TONG hoặc KHO_BEP
        LocalDate  date,
        String     note,
        String     createdBy,
        List<TransferLineRequest> lines
    ) {}

    /**
     * Tạo phiếu ADJUSTMENT (hàng mất/hư).
     * to_branch = NULL — chỉ trừ from_branch.
     * Chính phải duyệt trước khi inventory thay đổi.
     */
    @Transactional
    public CreateTransferResult createAdjustment(CreateAdjustmentRequest req) {
        BranchType bt = BranchType.valueOf(req.branchType().toUpperCase());
        Branch fromBranch = branchRepository.findByBranchType(bt)
            .orElseThrow(() -> new IllegalStateException("Không tìm thấy kho: " + req.branchType()));

        LocalDate date = req.date() != null ? req.date() : LocalDate.now();
        String code    = generateCode(date);

        GoodsTransfer transfer = GoodsTransfer.builder()
            .code(code)
            .fromBranch(fromBranch)
            .toBranch(null)         // ADJUSTMENT không có kho nhận
            .transferDate(date)
            .transferReason("ADJUSTMENT")
            .note(req.note())
            .status("PENDING")
            .createdBy(req.createdBy() != null ? req.createdBy() : "system")
            .build();

        for (TransferLineRequest lr : req.lines()) {
            Ingredient ing = ingredientRepository.findByCode(lr.ingredientCode())
                .orElseThrow(() -> new IllegalArgumentException(
                    "Không tìm thấy NL: " + lr.ingredientCode()));
            transfer.getLines().add(GoodsTransferLine.builder()
                .transfer(transfer)
                .ingredient(ing)
                .unit(lr.unit() != null ? lr.unit() : ing.getBaseUnit().name())
                .qtyRequested(lr.qty())
                .build());
        }

        goodsTransferRepository.save(transfer);
        log.info("Tạo ADJUSTMENT {} | kho: {} | {} lines", code, req.branchType(), transfer.getLines().size());
        return new CreateTransferResult(transfer.getId(), code, date, "PENDING");
    }

    /** Chính duyệt ADJUSTMENT → FEFO deduct from_branch (không có kho nhận) */
    @Transactional
    public void approveAdjustment(UUID id, String approvedBy) {
        GoodsTransfer t = goodsTransferRepository.findByIdWithLines(id)
            .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy phiếu: " + id));

        if (!"ADJUSTMENT".equals(t.getTransferReason())) {
            throw new IllegalStateException("Phiếu không phải ADJUSTMENT");
        }
        requireStatus(t, "PENDING");

        // Chỉ deduct from_branch, không credit to_branch
        for (GoodsTransferLine line : t.getLines()) {
            if (line.getIngredient() == null) continue;
            deductStock(line.getIngredient(), t.getFromBranch(),
                line.getQtyRequested(), line.getUnit(), t.getId(), t.getCode(), approvedBy);
        }

        t.setStatus("COMPLETED");
        t.setConfirmedBy(approvedBy);
        t.setConfirmedAt(OffsetDateTime.now());
        goodsTransferRepository.save(t);

        log(t, approvedBy, "APPROVE_ADJUSTMENT", "PENDING", "COMPLETED", null);
        log.info("ADJUSTMENT {} approved bởi Chính {}", t.getCode(), approvedBy);
    }

    @Transactional
    public void rejectAdjustment(UUID id, String rejectedBy, String reason) {
        GoodsTransfer t = findOrThrow(id);
        if (!"ADJUSTMENT".equals(t.getTransferReason())) {
            throw new IllegalStateException("Phiếu không phải ADJUSTMENT");
        }
        requireStatus(t, "PENDING");

        t.setStatus("REJECTED");
        t.setRejectedBy(rejectedBy);
        t.setRejectedAt(OffsetDateTime.now());
        t.setRejectionReason(reason);
        goodsTransferRepository.save(t);

        log(t, rejectedBy, "REJECT_ADJUSTMENT", "PENDING", "REJECTED", reason);
    }

    // ── Query ──────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public GoodsTransfer getTransferWithLines(UUID id) {
        return goodsTransferRepository.findByIdWithLines(id)
            .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy phiếu: " + id));
    }

    /**
     * Lấy danh sách phiếu theo status và màn hình.
     *
     * @param status        PENDING | READY | COMPLETED | REJECTED
     * @param branchType    KHO_TONG | KHO_BEP | null
     * @param scopedBranchId UUID branch của user đang login (null = không giới hạn scope).
     *                       Khi khác null (BEP/SHOP role), chỉ trả phiếu có to_branch_id = scopedBranchId.
     */
    @Transactional(readOnly = true)
    public List<GoodsTransfer> listByStatus(String status, String branchType, UUID scopedBranchId) {
        if (branchType != null) {
            boolean isFromBranch = "KHO_TONG".equalsIgnoreCase(branchType);

            // Data-scope: BEP/SHOP user chỉ thấy phiếu của branch mình
            UUID branchId;
            if (scopedBranchId != null) {
                // Dùng branchId của user — bỏ qua branchType param (ngăn user tự đổi sang branch khác)
                branchId = scopedBranchId;
                isFromBranch = false;   // BEP/SHOP luôn là to_branch
            } else {
                BranchType bt = BranchType.valueOf(branchType.toUpperCase());
                branchId = branchRepository.findByBranchType(bt)
                    .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy branch: " + branchType))
                    .getId();
            }

            if (isFromBranch) {
                return goodsTransferRepository.findAllByFromBranchIdAndStatusOrderByTransferDateDesc(
                    branchId, status);
            } else {
                return goodsTransferRepository.findAllByToBranchIdAndStatusOrderByTransferDateDesc(
                    branchId, status);
            }
        }
        return goodsTransferRepository.findAllByStatusOrderByTransferDateDesc(status);
    }

    /** Overload backward-compatible — không có scope (dùng cho SUPER_ADMIN / KHO_TRUONG). */
    @Transactional(readOnly = true)
    public List<GoodsTransfer> listByStatus(String status, String branchType) {
        return listByStatus(status, branchType, null);
    }

    @Transactional(readOnly = true)
    public List<GoodsTransfer> listPendingAdjustments() {
        return goodsTransferRepository
            .findAllByTransferReasonAndStatusOrderByTransferDateDesc("ADJUSTMENT", "PENDING");
    }

    // ── Internal: FEFO inventory ───────────────────────────────

    private void processInventory(GoodsTransfer t, String by) {
        for (GoodsTransferLine line : t.getLines()) {
            if (line.getIngredient() == null) continue;

            BigDecimal avgPrice = deductStock(
                line.getIngredient(), t.getFromBranch(),
                line.getQtyRequested(), line.getUnit(), t.getId(), t.getCode(), by);

            // Credit to_branch
            if (t.getToBranch() != null) {
                IngredientStockLot newLot = IngredientStockLot.builder()
                    .ingredient(line.getIngredient())
                    .branch(t.getToBranch())
                    .importDate(t.getTransferDate())
                    .qtyImported(line.getQtyRequested())
                    .qtyRemaining(line.getQtyRequested())
                    .unitPrice(avgPrice)
                    .isDepleted(false)
                    .sourceTransferId(t.getId())
                    .note("TRF/" + t.getCode())
                    .build();
                ingredientStockLotRepository.save(newLot);

                inventoryMovementRepository.save(InventoryMovement.builder()
                    .branch(t.getToBranch())
                    .itemType("INGREDIENT")
                    .ingredient(line.getIngredient())
                    .lotId(newLot.getId())
                    .transactionType(TransactionType.IMPORT)
                    .referenceType(ReferenceType.TRANSFER_IN)
                    .qty(line.getQtyRequested())
                    .unit(line.getUnit())
                    .sourceType("GOODS_TRANSFER")
                    .sourceId(t.getId())
                    .referenceCode(t.getCode())
                    .createdBy(by)
                    .build());

                updateStock(line.getIngredient(), t.getToBranch(), line.getQtyRequested());
            }

            line.setAvgUnitPrice(avgPrice);
        }
    }

    /** FEFO deduct từ branch, trả về avg unit price. */
    private BigDecimal deductStock(Ingredient ing, Branch branch,
                                   BigDecimal qty, String unit,
                                   UUID transferId, String transferCode, String by) {
        List<IngredientStockLot> lots = ingredientStockLotRepository
            .findAvailableLotsForFifo(ing.getId(), branch.getId());

        BigDecimal remaining  = qty;
        BigDecimal totalCost  = BigDecimal.ZERO;
        BigDecimal totalUsed  = BigDecimal.ZERO;

        for (IngredientStockLot lot : lots) {
            if (remaining.compareTo(BigDecimal.ZERO) <= 0) break;
            BigDecimal consumed = lot.consume(remaining);
            remaining  = remaining.subtract(consumed);
            totalCost  = totalCost.add(lot.getUnitPrice().multiply(consumed));
            totalUsed  = totalUsed.add(consumed);
            ingredientStockLotRepository.save(lot);

            inventoryMovementRepository.save(InventoryMovement.builder()
                .branch(branch)
                .itemType("INGREDIENT")
                .ingredient(ing)
                .lotId(lot.getId())
                .transactionType(TransactionType.EXPORT)
                .referenceType(ReferenceType.TRANSFER_OUT)
                .qty(consumed)
                .unit(unit)
                .sourceType("GOODS_TRANSFER")
                .sourceId(transferId)
                .referenceCode(transferCode)
                .createdBy(by)
                .build());
        }

        BigDecimal actualUsed = qty.subtract(remaining);
        updateStock(ing, branch, actualUsed.negate());

        if (remaining.compareTo(BigDecimal.ZERO) > 0) {
            log.warn("Thiếu kho {}/{}: còn thiếu {}", ing.getCode(), branch.getCode(), remaining);
        }

        return totalUsed.compareTo(BigDecimal.ZERO) > 0
            ? totalCost.divide(totalUsed, 6, RoundingMode.HALF_UP)
            : BigDecimal.ZERO;
    }

    // ── Whole-unit rounding ────────────────────────────────────

    /**
     * Nếu NL chỉ xuất nguyên đơn vị đóng gói (is_whole_unit_only = true):
     * round up qty lên bội số của packaging_qty.
     * VD: cần 4000g, packaging=5000g → xuất 5000g.
     */
    private BigDecimal applyWholeUnitRounding(Ingredient ing, BigDecimal qty) {
        if (Boolean.TRUE.equals(ing.getIsWholeUnitOnly())
                && ing.getPackagingQty() != null
                && ing.getPackagingQty().compareTo(BigDecimal.ZERO) > 0) {

            BigDecimal pkg = ing.getPackagingQty();
            // ceil(qty / pkg) * pkg
            BigDecimal units = qty.divide(pkg, 0, RoundingMode.CEILING);
            return units.multiply(pkg);
        }
        return qty;
    }

    // ── Helpers ────────────────────────────────────────────────

    private Branch resolveBranch(String reason, boolean isFrom) {
        BranchType bt = switch (reason) {
            // Hàng hư / mất → chỉ trừ KHO_BEP, không có kho nhận
            case "RETURN"   -> isFrom ? BranchType.KHO_BEP  : BranchType.KHO_TONG;
            // Xuất phụ kiện từ KHO_TONG → Cửa hàng
            case "RESTOCK"  -> isFrom ? BranchType.KHO_TONG : BranchType.SHOP;
            // Mặc định: KHO_TONG → KHO_BEP (PRODUCTION, WASTE_DISPOSAL, ...)
            default         -> isFrom ? BranchType.KHO_TONG : BranchType.KHO_BEP;
        };
        return branchRepository.findByBranchType(bt)
            .orElseThrow(() -> new IllegalStateException("Không tìm thấy kho: " + bt));
    }

    private String generateCode(LocalDate date) {
        long seq = goodsTransferRepository.countByTransferDate(date) + 1;
        return "TRF-" + date.format(DATE_FMT) + "-" + String.format("%03d", seq);
    }

    private GoodsTransfer findOrThrow(UUID id) {
        return goodsTransferRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy phiếu: " + id));
    }

    private void requireStatus(GoodsTransfer t, String expected) {
        if (!expected.equals(t.getStatus())) {
            throw new IllegalStateException(
                "Phiếu " + t.getCode() + " đang ở " + t.getStatus() + ", cần " + expected);
        }
    }

    private void updateStock(Ingredient ing, Branch branch, BigDecimal delta) {
        int updated = ingredientStockRepository.updateStock(ing.getId(), branch.getId(), delta);
        if (updated == 0) {
            BigDecimal init = delta.compareTo(BigDecimal.ZERO) > 0 ? delta : BigDecimal.ZERO;
            ingredientStockRepository.save(IngredientStock.builder()
                .ingredient(ing).branch(branch).qtyOnHand(init).build());
        }
    }

    private void log(GoodsTransfer t, String by, String action,
                     String oldStatus, String newStatus, String note) {
        activityLogRepository.save(ActivityLog.builder()
            .performedBy(by)
            .action(action)
            .entityType("GoodsTransfer")
            .entityId(t.getId())
            .entityCode(t.getCode())
            .oldStatus(oldStatus)
            .newStatus(newStatus)
            .note(note)
            .build());
    }
}
