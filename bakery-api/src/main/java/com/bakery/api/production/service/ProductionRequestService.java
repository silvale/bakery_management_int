package com.bakery.api.production.service;

import java.math.BigDecimal;

import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

import com.bakery.api.inventory.entity.StockLot;
import com.bakery.api.inventory.entity.StockMovement;
import com.bakery.api.inventory.repository.StockLotRepository;
import com.bakery.api.inventory.repository.StockMovementRepository;
import com.bakery.api.master.entity.Warehouse;
import com.bakery.api.master.repository.ItemLookupRepository;
import com.bakery.api.master.repository.WarehouseRepository;
import com.bakery.api.production.dto.DeliveryRecordResponse;
import com.bakery.api.production.dto.CompleteLineRequest;
import com.bakery.api.production.entity.ItemGroup;
import com.bakery.api.production.entity.ProductionGroup;
import com.bakery.api.production.entity.ProductionPlan;
import com.bakery.api.production.entity.ProductionPlanLine;
import com.bakery.api.production.dto.ProductionRequestLineRequest;
import com.bakery.api.production.dto.ProductionRequestLineResponse;
import com.bakery.api.production.dto.ProductionRequestRequest;
import com.bakery.api.production.dto.ProductionRequestResponse;
import com.bakery.api.production.entity.DeliveryRecord;
import com.bakery.api.production.entity.ProductionAdjustment;
import com.bakery.api.production.entity.ProductionRequest;
import com.bakery.api.production.entity.ProductionRequestLine;
import com.bakery.api.master.repository.UnitConversionRepository;
import com.bakery.api.production.repository.DeliveryRecordRepository;
import com.bakery.api.production.repository.ProductionAdjustmentRepository;
import com.bakery.api.production.repository.ProductionRequestRepository;
import com.bakery.api.recipe.entity.Recipe;
import com.bakery.api.recipe.entity.RecipeLine;
import com.bakery.api.recipe.repository.RecipeLineRepository;
import com.bakery.api.recipe.repository.RecipeRepository;
import com.bakery.framework.entity.AdjustmentSource;
import com.bakery.framework.entity.AdjustmentType;
import com.bakery.framework.entity.ApprovalStatus;
import com.bakery.framework.entity.DayType;
import com.bakery.framework.entity.DeliveryStatus;
import com.bakery.framework.entity.MovementType;
import com.bakery.framework.entity.ProductionLineStatus;
import com.bakery.framework.entity.ProductionType;
import com.bakery.framework.exception.ResourceNotFoundException;
import com.bakery.framework.metadata.ReferenceValue;
import com.bakery.framework.repository.BaseRepository;
import com.bakery.framework.repository.CommandRequestRepository;
import com.bakery.framework.security.BakeryActorResolver;
import com.bakery.framework.service.AbstractBakeryAdminService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service quản lý phiếu sản xuất bánh.
 *
 * <p>On APPROVE: FIFO deduct nguyên liệu từ kho KITCHEN theo recipe × plannedQty.
 * <p>completeLine: bếp bấm Completed → tạo DeliveryRecord(READY) + StockLot bánh thành phẩm.
 * <p>confirmDelivery: shop xác nhận nhận → cập nhật qtyReceived + discrepancy.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductionRequestService
        extends AbstractBakeryAdminService<ProductionRequest, ProductionRequestRequest, ProductionRequestResponse> {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final String KITCHEN_CODE = "KITCHEN";
    private static final String SHOP_CODE = "SHOP";
    private static final String REF_TYPE = "PRODUCTION_REQUEST";

    private final ProductionRequestRepository repository;
    private final ItemLookupRepository itemRepository;
    private final WarehouseRepository warehouseRepository;
    private final RecipeRepository recipeRepository;
    private final RecipeLineRepository recipeLineRepository;
    private final StockLotRepository stockLotRepository;
    private final StockMovementRepository stockMovementRepository;
    private final DeliveryRecordRepository deliveryRecordRepository;
    private final ProductionAdjustmentRepository adjustmentRepository;
    private final CommandRequestRepository commandRequestRepository;
    private final UnitConversionRepository unitConversionRepository;
    private final BakeryActorResolver actorResolver;

    // ── Framework wiring ─────────────────────────────────────────

    @Override protected BaseRepository<ProductionRequest> getRepository() { return repository; }
    @Override public Class<ProductionRequest> getEntityClass() { return ProductionRequest.class; }
    @Override protected BakeryActorResolver getActorResolver() { return actorResolver; }
    @Override protected CommandRequestRepository getCommandRequestRepository() { return commandRequestRepository; }
    @Override protected String getEntityName() { return "ProductionRequest"; }

    /**
     * PR sinh từ plan được lưu với DRAFT (default BaseEntity).
     * Override để chấp nhận cả DRAFT và PENDING_APPROVAL → APPROVED,
     * sau đó gọi afterApprove() để trừ NL từ kho KITCHEN theo recipe.
     *
     * <p>Lưu ý flow chuẩn:
     * 1. Approve TRANSFER MAIN→KITCHEN trước (MAIN giảm, KITCHEN tăng NL)
     * 2. Approve phiếu SX (bước này) → KITCHEN giảm NL, bếp bắt đầu làm
     */
    @Override
    @org.springframework.transaction.annotation.Transactional
    public ProductionRequestResponse approve(java.util.UUID id) {
        ProductionRequest e = repository.findById(id)
                .orElseThrow(() -> new com.bakery.framework.exception.ResourceNotFoundException("ProductionRequest", id));
        if (e.getApprovalStatus() != ApprovalStatus.DRAFT
                && e.getApprovalStatus() != ApprovalStatus.PENDING_APPROVAL) {
            throw new IllegalStateException("Phiếu SX không ở trạng thái có thể duyệt (hiện tại: " + e.getApprovalStatus() + ")");
        }
        e.setApprovalStatus(ApprovalStatus.APPROVED);
        e.setApprovedAt(java.time.Instant.now());
        e.setApprovedBy(actorResolver.currentUserId());
        ProductionRequest saved = repository.save(e);
        // Trừ NL kho KITCHEN theo recipe × qty — PHẢI gọi sau khi save để có ID
        afterApprove(saved);
        return toResponse(saved);
    }

    /**
     * Approve toàn bộ phiếu SX trong ngày một lần.
     * Chỉ approve phiếu mà TẤT CẢ lines đã COMPLETED (bếp đã điền số thực tế).
     * Bỏ qua các phiếu đã APPROVED/REJECTED hoặc còn line chưa xong.
     */
    @org.springframework.transaction.annotation.Transactional
    public List<ProductionRequestResponse> approveAll(java.time.LocalDate date) {
        java.time.Instant now = java.time.Instant.now();
        String actor = actorResolver.currentUserId();
        return repository.findByProductionDate(date).stream()
                .filter(e -> e.getApprovalStatus() == ApprovalStatus.DRAFT
                        || e.getApprovalStatus() == ApprovalStatus.PENDING_APPROVAL)
                .filter(e -> !e.getLines().isEmpty()
                        && e.getLines().stream()
                                .allMatch(l -> l.getLineStatus() == ProductionLineStatus.COMPLETED))
                .map(e -> {
                    e.setApprovalStatus(ApprovalStatus.APPROVED);
                    e.setApprovedAt(now);
                    e.setApprovedBy(actor);
                    return toResponse(repository.save(e));
                })
                .toList();
    }

    // ── Mapping ──────────────────────────────────────────────────

    @Override
    protected ProductionRequest toEntity(ProductionRequestRequest req) {
        ProductionRequest e = new ProductionRequest();
        e.setProductionType(req.productionType() != null ? req.productionType() : ProductionType.DAILY);
        e.setProductionDate(req.productionDate() != null ? req.productionDate() : LocalDate.now());
        e.setNote(req.note());
        if (req.lines() != null) {
            for (int i = 0; i < req.lines().size(); i++) {
                e.getLines().add(buildLine(e, req.lines().get(i), i + 1));
            }
        }
        return e;
    }

    @Override
    protected void applyUpdate(ProductionRequest e, ProductionRequestRequest req) {
        e.setProductionDate(req.productionDate() != null ? req.productionDate() : e.getProductionDate());
        e.setNote(req.note());
        e.getLines().clear();
        if (req.lines() != null) {
            for (int i = 0; i < req.lines().size(); i++) {
                e.getLines().add(buildLine(e, req.lines().get(i), i + 1));
            }
        }
    }

    private ProductionRequestLine buildLine(ProductionRequest e, ProductionRequestLineRequest lr, int order) {
        ProductionRequestLine line = new ProductionRequestLine();
        line.setProductionRequest(e);
        line.setProduct(itemRepository.findById(lr.productId())
                .orElseThrow(() -> new ResourceNotFoundException("Item", lr.productId())));
        if (lr.recipeId() != null) {
            line.setRecipe(recipeRepository.findById(lr.recipeId())
                    .orElseThrow(() -> new ResourceNotFoundException("Recipe", lr.recipeId())));
        }
        line.setPlannedQty(lr.plannedQty());
        line.setSortOrder(lr.sortOrder() != null ? lr.sortOrder() : order);
        line.setNote(lr.note());
        return line;
    }

    @Override
    protected ProductionRequestResponse toResponse(ProductionRequest e) {
        ProductionRequestResponse r = new ProductionRequestResponse();
        r.applyFrom(e);
        r.setCode(e.getCode());
        r.setProductionType(e.getProductionType());
        r.setProductionDate(e.getProductionDate());
        r.setNote(e.getNote());
        r.setLines(e.getLines().stream().map(this::toLineResponse).toList());
        return r;
    }

    private ProductionRequestLineResponse toLineResponse(ProductionRequestLine line) {
        ProductionRequestLineResponse lr = new ProductionRequestLineResponse();
        lr.applyFrom(line);
        if (line.getProduct() != null) {
            lr.setProduct(new ReferenceValue(line.getProduct().getCode(), line.getProduct().getName()));
        }
        if (line.getRecipe() != null) {
            lr.setRecipe(new ReferenceValue(line.getRecipe().getId().toString(),
                    "v" + line.getRecipe().getVersion()));
        }
        lr.setPlannedQty(line.getPlannedQty());
        lr.setLineStatus(line.getLineStatus() != null ? line.getLineStatus().name() : null);
        lr.setSortOrder(line.getSortOrder());
        lr.setNote(line.getNote());
        if (line.getDeliveryRecord() != null) {
            lr.setDeliveryRecord(toDeliveryResponse(line.getDeliveryRecord()));
        }
        return lr;
    }

    /** Lấy danh sách giao nhận theo ngày sản xuất. */
    @Transactional(readOnly = true)
    public List<DeliveryRecordResponse> findDeliveryRecordsByDate(java.time.LocalDate date) {
        return deliveryRecordRepository
                .findByProductionRequestLine_ProductionRequest_ProductionDate(date)
                .stream()
                .map(this::toDeliveryResponse)
                .toList();
    }

    private DeliveryRecordResponse toDeliveryResponse(DeliveryRecord dr) {
        DeliveryRecordResponse r = new DeliveryRecordResponse();
        r.setId(dr.getId());
        r.setQtyProduced(dr.getQtyProduced());
        r.setQtyReceived(dr.getQtyReceived());
        r.setDiscrepancy(dr.getDiscrepancy());
        r.setDeliveryStatus(dr.getDeliveryStatus() != null ? dr.getDeliveryStatus().name() : null);
        r.setConfirmedAt(dr.getConfirmedAt());
        r.setConfirmedBy(dr.getConfirmedBy());
        r.setNote(dr.getNote());
        // Enrich thông tin sản phẩm từ ProductionRequestLine
        if (dr.getProductionRequestLine() != null) {
            var line = dr.getProductionRequestLine();
            r.setPlannedQty(line.getPlannedQty());
            if (line.getProduct() != null) {
                r.setProductName(line.getProduct().getName());
                r.setProductCode(line.getProduct().getCode());
            }
        }
        return r;
    }

    // ── Lifecycle hooks ──────────────────────────────────────────

    @Override
    protected void beforeCreate(ProductionRequest e) {
        String prefix = e.getProductionType() == ProductionType.ORDER ? "PR-ORDER" : "PR-DAILY";
        LocalDate date = e.getProductionDate() != null ? e.getProductionDate() : LocalDate.now();
        String codePrefix = prefix + "-" + date.format(DATE_FMT) + "-";
        long count = repository.countByCodeStartingWith(codePrefix);
        e.setCode(codePrefix + String.format("%03d", count + 1));
    }

    /**
     * Tạo phiếu SX DAILY từ ProductionPlan đã APPROVED.
     * Gom lines theo ItemGroup → mỗi nhóm 1 phiếu.
     *
     * <p>FREE_GROUP: chỉ tạo nếu group có target WD/WE; plannedQty = 0 (bếp tự fill).
     * <p>SIMPLE/BATCH_FORMULA: plannedQty = effectiveQty.
     * <p>Items không có itemGroup → skip.
     */
    @Transactional
    public List<ProductionRequest> generateFromPlan(ProductionPlan plan) {
        List<ProductionPlanLine> lines = plan.getLines();
        if (lines.isEmpty()) return List.of();

        boolean isWeekend = DayType.WEEKEND == plan.getDayType();

        // Group lines by itemGroup.id — preserve insertion order
        Map<UUID, List<ProductionPlanLine>> byGroupId = new LinkedHashMap<>();
        Map<UUID, ItemGroup> groupMap = new LinkedHashMap<>();

        for (ProductionPlanLine l : lines) {
            if (l.getItem() == null) continue;
            ItemGroup ig = l.getItem().getItemGroup();
            if (ig == null) continue;

            // FREE_GROUP: skip nếu group không có target cho dayType này
            if ("FREE_GROUP".equals(l.getPlanType())) {
                ProductionGroup pg = l.getGroup();
                if (pg != null) {
                    // WE null → fallback về WD
                    Integer target = isWeekend
                            ? (pg.getTargetWeekend() != null ? pg.getTargetWeekend() : pg.getTargetWeekday())
                            : pg.getTargetWeekday();
                    if (target == null) continue; // không setup → không sản xuất
                }
            }

            byGroupId.computeIfAbsent(ig.getId(), k -> new ArrayList<>()).add(l);
            groupMap.put(ig.getId(), ig);
        }

        // Idempotency: nếu đã có DAILY PR cho ngày này → không tạo thêm
        List<ProductionRequest> existing = repository.findByProductionDate(plan.getPlanDate());
        Set<String> existingNotes = existing.stream()
                .map(ProductionRequest::getNote)
                .filter(java.util.Objects::nonNull)
                .collect(java.util.stream.Collectors.toSet());

        List<ProductionRequest> created = new ArrayList<>();
        for (UUID igId : byGroupId.keySet()) {
            ItemGroup ig = groupMap.get(igId);
            List<ProductionPlanLine> groupLines = byGroupId.get(igId);

            String note = "Kế hoạch SX " + plan.getPlanDate() + " — " + ig.getName();
            if (existingNotes.contains(note)) {
                log.info("generateFromPlan: skip '{}' — PR đã tồn tại", note);
                continue;
            }

            ProductionRequest req = new ProductionRequest();
            req.setProductionType(ProductionType.DAILY);
            req.setProductionDate(plan.getPlanDate());
            req.setNote(note);

            int sortOrder = 1;
            for (ProductionPlanLine pl : groupLines) {
                // FREE_GROUP: plannedQty = 0 (open-qty, bếp fill khi complete)
                // SIMPLE/BATCH_FORMULA: plannedQty = effectiveQty
                BigDecimal plannedQty = "FREE_GROUP".equals(pl.getPlanType())
                        ? BigDecimal.ZERO
                        : BigDecimal.valueOf(pl.getEffectiveQty());

                ProductionRequestLine rl = new ProductionRequestLine();
                rl.setProductionRequest(req);
                rl.setProduct(pl.getItem());
                rl.setPlannedQty(plannedQty);
                rl.setSortOrder(sortOrder++);
                req.getLines().add(rl);
            }

            beforeCreate(req);
            created.add(repository.save(req));
        }

        return created;
    }

    /**
     * Approve phiếu SX: chỉ đổi trạng thái — KHÔNG trừ NL ở đây.
     *
     * <p>NL sẽ bị trừ tại {@link #completeLine} khi bếp hoàn thành sản xuất
     * và {@code qtyProduced} đã rõ. Lúc đó TRANSFER MAIN→KITCHEN đã được duyệt
     * nên KITCHEN có đủ hàng để trừ.
     */
    @Override
    protected void afterApprove(ProductionRequest e) {
        // No-op: NL deduct moved to completeLine()
    }

    /**
     * Tra hệ số quy đổi từ {@code lineUnit} (đvt công thức) sang {@code itemUnit} (đvt kho).
     * G→KG = 0.001. Cùng đvt hoặc không tìm thấy → 1.
     */
    private BigDecimal resolveConversionFactor(String lineUnit, String itemUnit) {
        if (lineUnit == null || itemUnit == null) return BigDecimal.ONE;
        if (lineUnit.equalsIgnoreCase(itemUnit)) return BigDecimal.ONE;
        return unitConversionRepository.findConversion(lineUnit, itemUnit)
                .map(uc -> uc.getFactor())
                .orElseGet(() -> {
                    log.warn("Không tìm thấy unit conversion: {} → {} — dùng factor=1", lineUnit, itemUnit);
                    return BigDecimal.ONE;
                });
    }

    private Recipe resolveRecipe(ProductionRequestLine line) {
        if (line.getRecipe() != null) return line.getRecipe();
        return recipeRepository.findByProductIdAndActiveTrue(line.getProduct().getId()).orElse(null);
    }

    private void deductFromKitchen(BigDecimal needed, UUID itemId, Warehouse kitchen, ProductionRequest e) {
        List<StockLot> lots = stockLotRepository
                .findByItemIdAndQtyRemainingGreaterThanOrderByReceivedDateAscCreatedAtAsc(itemId, BigDecimal.ZERO)
                .stream()
                .filter(l -> l.getWarehouse() != null && l.getWarehouse().getId().equals(kitchen.getId()))
                .toList();

        BigDecimal remaining = needed;
        for (StockLot lot : lots) {
            if (remaining.compareTo(BigDecimal.ZERO) <= 0) break;
            BigDecimal deduct = remaining.min(lot.getQtyRemaining());
            lot.setQtyRemaining(lot.getQtyRemaining().subtract(deduct));
            stockLotRepository.save(lot);

            StockMovement mv = new StockMovement();
            mv.setLot(lot);
            mv.setMovementType(MovementType.OUT);
            mv.setQty(deduct);
            mv.setRefId(e.getId());
            mv.setRefType(REF_TYPE);
            mv.setNote("Xuất NL sản xuất từ phiếu " + e.getCode());
            stockMovementRepository.save(mv);

            remaining = remaining.subtract(deduct);
        }

        if (remaining.compareTo(BigDecimal.ZERO) > 0) {
            throw new IllegalStateException(
                    "Kho bếp không đủ nguyên liệu: itemId=" + itemId + " (thiếu " + remaining + ")");
        }
    }

    // ── Business actions ─────────────────────────────────────────

    /**
     * Bếp bấm "Completed" trên 1 line → tạo DeliveryRecord(READY) + StockLot bánh thành phẩm.
     *
     * <p>Nếu qtyProduced ≠ plannedQty, bắt buộc phải cung cấp adjustmentType + reason.
     * Hệ thống tự tạo ProductionAdjustment(PENDING_APPROVAL) để bếp trưởng duyệt.
     *
     * @param adjustmentType INGREDIENT_VARIANCE | PRODUCTION_WASTAGE (null nếu qty bằng plannedQty)
     */
    @Transactional
    public ProductionRequestResponse completeLine(UUID requestId, UUID lineId,
            BigDecimal qtyProduced, AdjustmentType adjustmentType, String reason, String note) {
        ProductionRequest e = repository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("ProductionRequest", requestId));

        ProductionRequestLine line = e.getLines().stream()
                .filter(l -> l.getId().equals(lineId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("ProductionRequestLine", lineId));

        if (line.getLineStatus() == ProductionLineStatus.COMPLETED) {
            throw new IllegalStateException("Line đã Completed rồi.");
        }

        // plannedQty = 0 nghĩa là open-qty (FREE_GROUP) — bếp fill tự do, không check deviation
        BigDecimal plannedQty = line.getPlannedQty();
        boolean isOpenQty = plannedQty.compareTo(BigDecimal.ZERO) == 0;
        boolean hasDeviation = !isOpenQty && qtyProduced.compareTo(plannedQty) != 0;
        if (hasDeviation && adjustmentType == null) {
            throw new IllegalArgumentException(
                    "qtyProduced (" + qtyProduced + ") ≠ plannedQty (" + plannedQty + "). Vui lòng chọn adjustmentType.");
        }

        Warehouse kitchen = warehouseRepository.findByCode(KITCHEN_CODE)
                .orElseThrow(() -> new IllegalStateException("Không tìm thấy kho KITCHEN"));

        // Trừ NL kho KITCHEN theo recipe × qtyProduced (đơn vị được quy đổi)
        Recipe recipe = resolveRecipe(line);
        if (recipe != null) {
            List<RecipeLine> recipeLines = recipeLineRepository.findByRecipeIdOrderBySortOrderAsc(recipe.getId());
            for (RecipeLine rl : recipeLines) {
                if (rl.getItem() == null) continue;
                BigDecimal convFactor = resolveConversionFactor(
                        rl.getUnit(), rl.getItem().getUnit());
                BigDecimal needed = rl.getQuantity().multiply(qtyProduced).multiply(convFactor);
                deductFromKitchen(needed, rl.getItem().getId(), kitchen, e);
            }
        } else {
            log.warn("completeLine: không tìm thấy recipe cho sản phẩm {} — bỏ qua NL deduct",
                    line.getProduct() != null ? line.getProduct().getCode() : "unknown");
        }

        // Tạo StockLot bánh thành phẩm tại KITCHEN

        StockLot productLot = new StockLot();
        productLot.setItem(line.getProduct());
        productLot.setWarehouse(kitchen);
        productLot.setQtyInitial(qtyProduced);
        productLot.setQtyRemaining(qtyProduced);
        productLot.setUnitCost(BigDecimal.ZERO);
        productLot.setReceivedDate(e.getProductionDate());
        StockLot savedLot = stockLotRepository.save(productLot);

        StockMovement inMv = new StockMovement();
        inMv.setLot(savedLot);
        inMv.setMovementType(MovementType.IN);
        inMv.setQty(qtyProduced);
        inMv.setRefId(e.getId());
        inMv.setRefType(REF_TYPE);
        inMv.setNote("Bánh thành phẩm từ phiếu " + e.getCode());
        stockMovementRepository.save(inMv);

        // Tạo DeliveryRecord
        DeliveryRecord dr = new DeliveryRecord();
        dr.setProductionRequestLine(line);
        dr.setQtyProduced(qtyProduced);
        dr.setDeliveryStatus(DeliveryStatus.READY);
        dr.setNote(note);
        DeliveryRecord savedDr = deliveryRecordRepository.save(dr);

        // Nếu lệch → tạo ProductionAdjustment chờ bếp trưởng duyệt
        if (hasDeviation) {
            ProductionAdjustment adj = new ProductionAdjustment();
            adj.setDeliveryRecord(savedDr);
            adj.setAdjustmentType(adjustmentType);
            adj.setSource(AdjustmentSource.KITCHEN_COMPLETE);
            adj.setOriginalQty(plannedQty);
            adj.setAdjustedQty(qtyProduced);
            adj.setDelta(qtyProduced.subtract(plannedQty));
            adj.setReason(reason);
            adj.setApprovalStatus(ApprovalStatus.PENDING_APPROVAL);
            adj.setCreatedBy(actorResolver.currentUserId());
            adjustmentRepository.save(adj);
        }

        line.setLineStatus(ProductionLineStatus.COMPLETED);
        line.setQtyProduced(qtyProduced);
        return toResponse(repository.save(e));
    }

    /**
     * Batch complete nhiều line cùng lúc — wrapper gọi lại completeLine() cho từng item.
     * Toàn bộ xử lý trong 1 transaction: nếu 1 line lỗi thì rollback tất cả.
     *
     * @param requestId ID phiếu sản xuất
     * @param items     danh sách line cần complete
     * @return ProductionRequestResponse sau khi xử lý xong tất cả line
     */
    @Transactional
    public ProductionRequestResponse completeLines(UUID requestId, List<CompleteLineRequest> items) {
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("Danh sách line không được để trống.");
        }
        ProductionRequestResponse response = null;
        for (CompleteLineRequest item : items) {
            response = completeLine(
                    requestId,
                    item.lineId(),
                    item.qtyProduced(),
                    item.adjustmentType(),
                    item.reason(),
                    item.note());
        }
        return response;
    }

    /**
     * Shop bấm "Xác nhận nhận" → cập nhật qtyReceived + discrepancy
     * + chuyển tồn kho từ KITCHEN → SHOP.
     *
     * <p>Inventory flow:
     * <ol>
     *   <li>OUT khỏi StockLot của sản phẩm tại KITCHEN (qtyReceived)</li>
     *   <li>IN vào StockLot mới tại SHOP (qtyReceived)</li>
     *   <li>Nếu discrepancy > 0 (bếp làm nhiều hơn shop nhận): phần dư vẫn nằm ở KITCHEN</li>
     * </ol>
     */
    @Transactional
    public DeliveryRecordResponse confirmDelivery(UUID deliveryRecordId, BigDecimal qtyReceived, String note) {
        DeliveryRecord dr = deliveryRecordRepository.findById(deliveryRecordId)
                .orElseThrow(() -> new ResourceNotFoundException("DeliveryRecord", deliveryRecordId));

        if (dr.getDeliveryStatus() == DeliveryStatus.CONFIRMED) {
            throw new IllegalStateException("DeliveryRecord đã được confirm rồi.");
        }

        BigDecimal discrepancy = dr.getQtyProduced().subtract(qtyReceived);
        dr.setQtyReceived(qtyReceived);
        dr.setDiscrepancy(discrepancy);
        dr.setDeliveryStatus(DeliveryStatus.CONFIRMED);
        dr.setConfirmedAt(Instant.now());
        dr.setConfirmedBy(actorResolver.currentUserId());
        dr.setNote(note);
        deliveryRecordRepository.save(dr);

        // ── Chuyển kho KITCHEN → SHOP ─────────────────────────────────────
        if (qtyReceived.compareTo(BigDecimal.ZERO) > 0) {
            var line = dr.getProductionRequestLine();
            var item = line.getProduct();
            var productionDate = line.getProductionRequest().getProductionDate();

            // Lấy lot tại KITCHEN (lot được tạo khi completeLine)
            Warehouse kitchen = warehouseRepository.findByCode(KITCHEN_CODE)
                    .orElseThrow(() -> new IllegalStateException("Không tìm thấy kho KITCHEN"));
            Warehouse shop = warehouseRepository.findByCode(SHOP_CODE)
                    .orElseThrow(() -> new IllegalStateException("Không tìm thấy kho SHOP"));

            // FEFO: lấy lot KITCHEN của item này, trừ dần theo qtyReceived
            List<StockLot> kitchenLots = stockLotRepository
                    .findByItemIdAndQtyRemainingGreaterThanOrderByReceivedDateAscCreatedAtAsc(
                            item.getId(), BigDecimal.ZERO)
                    .stream()
                    .filter(l -> kitchen.getId().equals(l.getWarehouse().getId()))
                    .toList();

            BigDecimal toTransfer = qtyReceived;
            for (StockLot kLot : kitchenLots) {
                if (toTransfer.compareTo(BigDecimal.ZERO) <= 0) break;

                BigDecimal take = kLot.getQtyRemaining().min(toTransfer);

                // OUT từ KITCHEN lot
                kLot.setQtyRemaining(kLot.getQtyRemaining().subtract(take));
                stockLotRepository.save(kLot);

                StockMovement outMv = new StockMovement();
                outMv.setLot(kLot);
                outMv.setMovementType(MovementType.OUT);
                outMv.setQty(take);
                outMv.setRefId(deliveryRecordId);
                outMv.setRefType("DELIVERY_CONFIRM");
                outMv.setNote("Giao shop xác nhận — DR " + deliveryRecordId);
                stockMovementRepository.save(outMv);

                // IN vào SHOP lot mới
                StockLot shopLot = new StockLot();
                shopLot.setItem(item);
                shopLot.setWarehouse(shop);
                shopLot.setQtyInitial(take);
                shopLot.setQtyRemaining(take);
                shopLot.setUnitCost(kLot.getUnitCost());
                shopLot.setReceivedDate(productionDate);
                if (kLot.getExpiryDate() != null) shopLot.setExpiryDate(kLot.getExpiryDate());
                StockLot savedShopLot = stockLotRepository.save(shopLot);

                StockMovement inMv = new StockMovement();
                inMv.setLot(savedShopLot);
                inMv.setMovementType(MovementType.IN);
                inMv.setQty(take);
                inMv.setRefId(deliveryRecordId);
                inMv.setRefType("DELIVERY_CONFIRM");
                inMv.setNote("Nhận từ bếp — DR " + deliveryRecordId);
                stockMovementRepository.save(inMv);

                toTransfer = toTransfer.subtract(take);
            }

            if (toTransfer.compareTo(BigDecimal.ZERO) > 0) {
                log.warn("confirmDelivery: không đủ tồn kho KITCHEN để chuyển, còn thiếu {}", toTransfer);
            }
        }

        return toDeliveryResponse(deliveryRecordRepository.findById(deliveryRecordId).orElseThrow());
    }
}
