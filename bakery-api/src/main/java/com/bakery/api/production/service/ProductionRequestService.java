package com.bakery.api.production.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

import com.bakery.api.inventory.entity.StockLot;
import com.bakery.api.inventory.entity.StockMovement;
import com.bakery.api.inventory.repository.StockLotRepository;
import com.bakery.api.inventory.repository.StockMovementRepository;
import com.bakery.api.master.entity.Warehouse;
import com.bakery.api.master.repository.ItemLookupRepository;
import com.bakery.api.master.repository.WarehouseRepository;
import com.bakery.api.production.dto.DeliveryRecordResponse;
import com.bakery.api.production.dto.ProductionRequestLineRequest;
import com.bakery.api.production.dto.ProductionRequestLineResponse;
import com.bakery.api.production.dto.ProductionRequestRequest;
import com.bakery.api.production.dto.ProductionRequestResponse;
import com.bakery.api.production.entity.DeliveryRecord;
import com.bakery.api.production.entity.ProductionAdjustment;
import com.bakery.api.production.entity.ProductionRequest;
import com.bakery.api.production.entity.ProductionRequestLine;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service quản lý phiếu sản xuất bánh.
 *
 * <p>On APPROVE: FIFO deduct nguyên liệu từ kho KITCHEN theo recipe × plannedQty.
 * <p>completeLine: bếp bấm Completed → tạo DeliveryRecord(READY) + StockLot bánh thành phẩm.
 * <p>confirmDelivery: shop xác nhận nhận → cập nhật qtyReceived + discrepancy.
 */
@Service
@RequiredArgsConstructor
public class ProductionRequestService
        extends AbstractBakeryAdminService<ProductionRequest, ProductionRequestRequest, ProductionRequestResponse> {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final String KITCHEN_CODE = "KITCHEN";
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
    private final BakeryActorResolver actorResolver;

    // ── Framework wiring ─────────────────────────────────────────

    @Override protected BaseRepository<ProductionRequest> getRepository() { return repository; }
    @Override protected BakeryActorResolver getActorResolver() { return actorResolver; }
    @Override protected CommandRequestRepository getCommandRequestRepository() { return commandRequestRepository; }
    @Override protected String getEntityName() { return "ProductionRequest"; }

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
        return r;
    }

    // ── Lifecycle hooks ──────────────────────────────────────────

    @Override
    protected void beforeCreate(ProductionRequest e) {
        String prefix = e.getProductionType() == ProductionType.ORDER ? "PR-ORDER" : "PR-DAILY";
        String dateStr = LocalDate.now().format(DATE_FMT);
        String codePrefix = prefix + "-" + dateStr + "-";
        long count = repository.countByCodeStartingWith(codePrefix);
        e.setCode(codePrefix + String.format("%03d", count + 1));
    }

    /**
     * APPROVE: FIFO deduct nguyên liệu từ kho KITCHEN theo recipe × plannedQty mỗi line.
     */
    @Override
    protected void afterApprove(ProductionRequest e) {
        Warehouse kitchen = warehouseRepository.findByCode(KITCHEN_CODE)
                .orElseThrow(() -> new IllegalStateException("Không tìm thấy kho KITCHEN"));

        for (ProductionRequestLine line : e.getLines()) {
            Recipe recipe = resolveRecipe(line);
            if (recipe == null) continue;

            List<RecipeLine> recipeLines = recipeLineRepository.findByRecipeIdOrderBySortOrderAsc(recipe.getId());
            for (RecipeLine rl : recipeLines) {
                BigDecimal needed = rl.getQuantity().multiply(line.getPlannedQty());
                deductFromKitchen(needed, rl.getItem().getId(), kitchen, e);
            }
        }
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
     */
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

        // Validate: nếu lệch plannedQty phải có lý do
        BigDecimal plannedQty = line.getPlannedQty();
        boolean hasDeviation = qtyProduced.compareTo(plannedQty) != 0;
        if (hasDeviation && adjustmentType == null) {
            throw new IllegalArgumentException(
                    "qtyProduced (" + qtyProduced + ") ≠ plannedQty (" + plannedQty + "). Vui lòng chọn adjustmentType.");
        }

        // Tạo StockLot bánh thành phẩm tại KITCHEN
        Warehouse kitchen = warehouseRepository.findByCode(KITCHEN_CODE)
                .orElseThrow(() -> new IllegalStateException("Không tìm thấy kho KITCHEN"));

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
        return toResponse(repository.save(e));
    }

    /**
     * Shop bấm "Xác nhận nhận" → cập nhật qtyReceived + discrepancy.
     */
    @Transactional
    public DeliveryRecordResponse confirmDelivery(UUID deliveryRecordId, BigDecimal qtyReceived, String note) {
        DeliveryRecord dr = deliveryRecordRepository.findById(deliveryRecordId)
                .orElseThrow(() -> new ResourceNotFoundException("DeliveryRecord", deliveryRecordId));

        if (dr.getDeliveryStatus() == DeliveryStatus.CONFIRMED) {
            throw new IllegalStateException("DeliveryRecord đã được confirm rồi.");
        }

        dr.setQtyReceived(qtyReceived);
        dr.setDiscrepancy(dr.getQtyProduced().subtract(qtyReceived));
        dr.setDeliveryStatus(DeliveryStatus.CONFIRMED);
        dr.setConfirmedAt(Instant.now());
        dr.setConfirmedBy(actorResolver.currentUserId());
        dr.setNote(note);

        return toDeliveryResponse(deliveryRecordRepository.save(dr));
    }
}
