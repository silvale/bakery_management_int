package com.bakery.api.inventory.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

import com.bakery.api.inventory.dto.InventoryRequestLineRequest;
import com.bakery.api.inventory.dto.InventoryRequestLineResponse;
import com.bakery.api.inventory.dto.InventoryRequestRequest;
import com.bakery.api.inventory.dto.InventoryRequestResponse;
import com.bakery.api.inventory.entity.InventoryRequest;
import com.bakery.api.inventory.entity.InventoryRequestLine;
import com.bakery.api.inventory.entity.StockLot;
import com.bakery.api.inventory.entity.StockMovement;
import com.bakery.api.inventory.repository.InventoryRequestRepository;
import com.bakery.api.inventory.repository.StockLotRepository;
import com.bakery.api.inventory.repository.StockMovementRepository;
import com.bakery.api.master.entity.Item;
import com.bakery.api.master.repository.ItemLookupRepository;
import com.bakery.api.master.repository.ProductExpiryConfigRepository;
import com.bakery.api.master.repository.SupplierRepository;
import com.bakery.api.master.repository.UnitConversionRepository;
import com.bakery.api.master.repository.WarehouseRepository;
import com.bakery.framework.entity.InventoryRequestType;
import com.bakery.framework.entity.MovementType;
import com.bakery.framework.exception.ResourceNotFoundException;
import com.bakery.framework.metadata.ReferenceValue;
import com.bakery.framework.repository.BaseRepository;
import com.bakery.framework.repository.CommandRequestRepository;
import com.bakery.framework.security.BakeryActorResolver;
import com.bakery.framework.service.AbstractBakeryAdminService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Service quản lý phiếu nhập/điều chuyển kho.
 *
 * <p>On APPROVE:
 *   PURCHASE → tạo StockLot + StockMovement(IN) cho mỗi line
 *   TRANSFER → (Phase 2) tạo StockMovement(OUT) + StockMovement(IN)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryRequestService
        extends AbstractBakeryAdminService<InventoryRequest, InventoryRequestRequest, InventoryRequestResponse> {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final String REF_TYPE = "INVENTORY_REQUEST";

    private final InventoryRequestRepository repository;
    private final WarehouseRepository warehouseRepository;
    private final SupplierRepository supplierRepository;
    private final ItemLookupRepository itemRepository;
    private final StockLotRepository stockLotRepository;
    private final StockMovementRepository stockMovementRepository;
    private final ProductExpiryConfigRepository expiryConfigRepository;
    private final UnitConversionRepository unitConversionRepository;
    private final CommandRequestRepository commandRequestRepository;
    private final BakeryActorResolver actorResolver;

    // ── Framework wiring ─────────────────────────────────────────

    @Override
    protected BaseRepository<InventoryRequest> getRepository() {
        return repository;
    }

    @Override
    public Class<InventoryRequest> getEntityClass() { return InventoryRequest.class; }

    @Override
    protected BakeryActorResolver getActorResolver() {
        return actorResolver;
    }

    @Override
    protected CommandRequestRepository getCommandRequestRepository() {
        return commandRequestRepository;
    }

    @Override
    protected String getEntityName() {
        return "InventoryRequest";
    }

    // ── Mapping ──────────────────────────────────────────────────

    @Override
    protected InventoryRequest toEntity(InventoryRequestRequest req) {
        InventoryRequest e = new InventoryRequest();
        e.setRequestType(req.requestType());
        e.setRequestDate(req.requestDate() != null ? req.requestDate() : LocalDate.now());
        e.setExpectedDeliveryDate(req.expectedDeliveryDate());
        e.setNote(req.note());

        if (req.sourceWarehouseId() != null) {
            e.setSourceWarehouse(warehouseRepository.findById(req.sourceWarehouseId())
                    .orElseThrow(() -> new ResourceNotFoundException("Warehouse", req.sourceWarehouseId())));
        }
        if (req.targetWarehouseId() != null) {
            e.setTargetWarehouse(warehouseRepository.findById(req.targetWarehouseId())
                    .orElseThrow(() -> new ResourceNotFoundException("Warehouse", req.targetWarehouseId())));
        }
        if (req.supplierId() != null) {
            e.setSupplier(supplierRepository.findById(req.supplierId())
                    .orElseThrow(() -> new ResourceNotFoundException("Supplier", req.supplierId())));
        }

        // Build lines (parent ID not yet assigned — set after beforeCreate/save via cascade)
        buildAndAddLines(e, req.lines());
        return e;
    }

    @Override
    protected void applyUpdate(InventoryRequest e, InventoryRequestRequest req) {
        e.setRequestDate(req.requestDate() != null ? req.requestDate() : e.getRequestDate());
        e.setExpectedDeliveryDate(req.expectedDeliveryDate());
        e.setNote(req.note());

        if (req.sourceWarehouseId() != null) {
            e.setSourceWarehouse(warehouseRepository.findById(req.sourceWarehouseId())
                    .orElseThrow(() -> new ResourceNotFoundException("Warehouse", req.sourceWarehouseId())));
        }
        if (req.targetWarehouseId() != null) {
            e.setTargetWarehouse(warehouseRepository.findById(req.targetWarehouseId())
                    .orElseThrow(() -> new ResourceNotFoundException("Warehouse", req.targetWarehouseId())));
        }
        if (req.supplierId() != null) {
            e.setSupplier(supplierRepository.findById(req.supplierId())
                    .orElseThrow(() -> new ResourceNotFoundException("Supplier", req.supplierId())));
        }

        // Replace all lines (orphanRemoval cleans up removed ones)
        e.getLines().clear();
        buildAndAddLines(e, req.lines());
    }

    @Override
    protected InventoryRequestResponse toResponse(InventoryRequest e) {
        InventoryRequestResponse r = new InventoryRequestResponse();
        r.applyFrom(e);
        r.setCode(e.getCode());
        r.setRequestType(e.getRequestType());
        r.setRequestDate(e.getRequestDate());
        r.setExpectedDeliveryDate(e.getExpectedDeliveryDate());
        r.setNote(e.getNote());

        if (e.getSourceWarehouse() != null) {
            r.setSourceWarehouse(new ReferenceValue(
                    e.getSourceWarehouse().getCode(), e.getSourceWarehouse().getName()));
        }
        if (e.getTargetWarehouse() != null) {
            r.setTargetWarehouse(new ReferenceValue(
                    e.getTargetWarehouse().getCode(), e.getTargetWarehouse().getName()));
        }
        if (e.getSupplier() != null) {
            r.setSupplier(new ReferenceValue(
                    e.getSupplier().getCode(), e.getSupplier().getName()));
        }

        List<InventoryRequestLineResponse> lineResponses = e.getLines().stream()
                .map(line -> {
                    InventoryRequestLineResponse lr = new InventoryRequestLineResponse();
                    lr.applyFrom(line);
                    if (line.getItem() != null) {
                        lr.setItem(new ReferenceValue(
                                line.getItem().getCode(), line.getItem().getName()));
                    }
                    lr.setQuantity(line.getQuantity());
                    lr.setUnit(line.getUnit());
                    lr.setUnitCost(line.getUnitCost());
                    lr.setSortOrder(line.getSortOrder());
                    lr.setNote(line.getNote());
                    return lr;
                })
                .toList();
        r.setLines(lineResponses);
        return r;
    }

    // ── Lifecycle hooks ──────────────────────────────────────────

    /**
     * Auto-generate code: PO-YYYYMMDD-NNN hoặc TR-YYYYMMDD-NNN
     */
    @Override
    protected void beforeCreate(InventoryRequest e) {
        String prefix = switch (e.getRequestType()) {
            case PURCHASE -> "PO";
            case TRANSFER -> "TR";
            case ADJUSTMENT -> "ADJ";
        };
        String dateStr = LocalDate.now().format(DATE_FMT);
        String codePrefix = prefix + "-" + dateStr + "-";

        long count = repository.countByCodeStartingWith(codePrefix);
        String seq = String.format("%03d", count + 1);
        e.setCode(codePrefix + seq);
    }

    /**
     * On APPROVE (PURCHASE only):
     * Mỗi line → tạo StockLot + StockMovement(IN)
     */
    @Override
    protected void afterApprove(InventoryRequest e) {
        if (e.getRequestType() == InventoryRequestType.PURCHASE) {
            approvePurchase(e);
        } else if (e.getRequestType() == InventoryRequestType.TRANSFER) {
            approveTransfer(e);
        } else if (e.getRequestType() == InventoryRequestType.ADJUSTMENT) {
            approveAdjustment(e);
        }
    }

    /**
     * Quy đổi số lượng từ đơn vị nhập liệu (line.unit) sang đơn vị lưu kho (item.unit).
     *
     * <p>Ví dụ: nhập 10 KG, item.unit = G → trả về 10000 G.
     * Nếu cùng đơn vị hoặc không tìm thấy conversion → giữ nguyên và log warn.
     */
    private BigDecimal convertToItemUnit(BigDecimal qty, String fromUnit, Item item) {
        String toUnit = item.getUnit();
        if (fromUnit == null || toUnit == null) return qty;
        if (fromUnit.equalsIgnoreCase(toUnit)) return qty;
        return unitConversionRepository.findConversion(fromUnit, toUnit)
                .map(uc -> qty.multiply(uc.getFactor()))
                .orElseGet(() -> {
                    log.warn("convertToItemUnit: không tìm thấy {} → {} cho item {} — giữ nguyên qty",
                            fromUnit, toUnit, item.getCode());
                    return qty;
                });
    }

    /** PURCHASE: tạo StockLot + StockMovement(IN) cho mỗi line */
    private void approvePurchase(InventoryRequest e) {

        LocalDate receivedDate = e.getRequestDate();

        for (InventoryRequestLine line : e.getLines()) {
            Item item = line.getItem();

            // Convert qty từ line.unit → item.unit trước khi lưu vào StockLot
            // VD: nhập 10 KG, item.unit = G → lưu 10000 trong lot
            BigDecimal qtyInItemUnit = convertToItemUnit(line.getQuantity(), line.getUnit(), item);

            // Tính expiry date nếu có config
            LocalDate expiryDate = expiryConfigRepository.findByItemId(item.getId())
                    .map(cfg -> receivedDate.plusDays(cfg.getShelfDays()))
                    .orElse(null);

            // Tạo StockLot — qty luôn ở item.unit
            StockLot lot = new StockLot();
            lot.setItem(item);
            lot.setSupplier(e.getSupplier());
            lot.setWarehouse(e.getTargetWarehouse());
            lot.setQtyInitial(qtyInItemUnit);
            lot.setQtyRemaining(qtyInItemUnit);
            lot.setUnitCost(line.getUnitCost() != null ? line.getUnitCost() : BigDecimal.ZERO);
            lot.setReceivedDate(receivedDate);
            lot.setExpiryDate(expiryDate);
            StockLot savedLot = stockLotRepository.save(lot);

            // Tạo StockMovement(IN)
            StockMovement movement = new StockMovement();
            movement.setLot(savedLot);
            movement.setMovementType(MovementType.IN);
            movement.setQty(qtyInItemUnit);
            movement.setRefId(e.getId());
            movement.setRefType(REF_TYPE);
            movement.setNote("Nhập hàng từ phiếu " + e.getCode());
            stockMovementRepository.save(movement);
        }
    }

    /**
     * TRANSFER: FIFO deduct từ source warehouse → tạo StockMovement(OUT) mỗi lô.
     * Nếu tồn kho không đủ → throw IllegalStateException.
     */
    private void approveTransfer(InventoryRequest e) {
        UUID sourceWarehouseId = e.getSourceWarehouse() != null ? e.getSourceWarehouse().getId() : null;

        for (InventoryRequestLine line : e.getLines()) {
            // Convert qty từ line.unit → item.unit (StockLot luôn lưu theo item.unit)
            BigDecimal convertedQty = convertToItemUnit(line.getQuantity(), line.getUnit(), line.getItem());
            // Fixed unit: nếu item không tách lẻ → làm tròn lên bội số của unitSize
            BigDecimal remaining = roundUpToUnitSize(convertedQty, line.getItem());

            // FIFO: lấy các lot còn hàng của item này trong source warehouse, cũ nhất trước
            List<StockLot> lots = stockLotRepository
                    .findByItemIdAndQtyRemainingGreaterThanOrderByReceivedDateAscCreatedAtAsc(
                            line.getItem().getId(), BigDecimal.ZERO);

            // Filter theo source warehouse nếu có
            if (sourceWarehouseId != null) {
                lots = lots.stream()
                        .filter(l -> l.getWarehouse() != null
                                && l.getWarehouse().getId().equals(sourceWarehouseId))
                        .toList();
            }

            for (StockLot lot : lots) {
                if (remaining.compareTo(BigDecimal.ZERO) <= 0) break;

                BigDecimal deduct = remaining.min(lot.getQtyRemaining());
                lot.setQtyRemaining(lot.getQtyRemaining().subtract(deduct));
                stockLotRepository.save(lot);

                // OUT tại source
                StockMovement outMovement = new StockMovement();
                outMovement.setLot(lot);
                outMovement.setMovementType(MovementType.OUT);
                outMovement.setQty(deduct);
                outMovement.setRefId(e.getId());
                outMovement.setRefType(REF_TYPE);
                outMovement.setNote("Xuất hàng từ phiếu " + e.getCode());
                stockMovementRepository.save(outMovement);

                // IN tại target — tạo StockLot mới tại kho đích, giữ nguyên unitCost của lot gốc
                StockLot inLot = new StockLot();
                inLot.setItem(lot.getItem());
                inLot.setWarehouse(e.getTargetWarehouse());
                inLot.setSupplier(lot.getSupplier());
                inLot.setQtyInitial(deduct);
                inLot.setQtyRemaining(deduct);
                inLot.setUnitCost(lot.getUnitCost());
                inLot.setReceivedDate(e.getRequestDate());
                inLot.setExpiryDate(lot.getExpiryDate());
                StockLot savedInLot = stockLotRepository.save(inLot);

                StockMovement inMovement = new StockMovement();
                inMovement.setLot(savedInLot);
                inMovement.setMovementType(MovementType.IN);
                inMovement.setQty(deduct);
                inMovement.setRefId(e.getId());
                inMovement.setRefType(REF_TYPE);
                inMovement.setNote("Nhận hàng từ phiếu " + e.getCode());
                stockMovementRepository.save(inMovement);

                remaining = remaining.subtract(deduct);
            }

            if (remaining.compareTo(BigDecimal.ZERO) > 0) {
                throw new IllegalStateException(
                        "Không đủ tồn kho cho sản phẩm: " + line.getItem().getName()
                        + " (còn thiếu " + remaining + " " + line.getUnit() + ")");
            }
        }
    }

    /**
     * ADJUSTMENT: điều chỉnh tồn kho trong targetWarehouse.
     *   quantity > 0 → tăng kho: tạo StockLot mới + StockMovement(IN)
     *   quantity < 0 → giảm kho: FIFO deduct + StockMovement(OUT)
     */
    private void approveAdjustment(InventoryRequest e) {
        UUID warehouseId = e.getTargetWarehouse() != null ? e.getTargetWarehouse().getId() : null;

        for (InventoryRequestLine line : e.getLines()) {
            BigDecimal qty = line.getQuantity();
            if (qty == null || qty.compareTo(BigDecimal.ZERO) == 0) continue;

            if (qty.compareTo(BigDecimal.ZERO) > 0) {
                // Tăng kho — tạo StockLot mới
                StockLot lot = new StockLot();
                lot.setItem(line.getItem());
                lot.setWarehouse(e.getTargetWarehouse());
                lot.setQtyInitial(qty);
                lot.setQtyRemaining(qty);
                lot.setUnitCost(line.getUnitCost() != null ? line.getUnitCost() : BigDecimal.ZERO);
                lot.setReceivedDate(e.getRequestDate());
                StockLot savedLot = stockLotRepository.save(lot);

                StockMovement movement = new StockMovement();
                movement.setLot(savedLot);
                movement.setMovementType(MovementType.IN);
                movement.setQty(qty);
                movement.setRefId(e.getId());
                movement.setRefType(REF_TYPE);
                movement.setNote("Điều chỉnh tăng từ phiếu " + e.getCode());
                stockMovementRepository.save(movement);

            } else {
                // Giảm kho — FIFO deduct
                BigDecimal remaining = qty.abs();

                List<StockLot> lots = stockLotRepository
                        .findByItemIdAndQtyRemainingGreaterThanOrderByReceivedDateAscCreatedAtAsc(
                                line.getItem().getId(), BigDecimal.ZERO);

                if (warehouseId != null) {
                    lots = lots.stream()
                            .filter(l -> l.getWarehouse() != null
                                    && l.getWarehouse().getId().equals(warehouseId))
                            .toList();
                }

                for (StockLot lot : lots) {
                    if (remaining.compareTo(BigDecimal.ZERO) <= 0) break;

                    BigDecimal deduct = remaining.min(lot.getQtyRemaining());
                    lot.setQtyRemaining(lot.getQtyRemaining().subtract(deduct));
                    stockLotRepository.save(lot);

                    StockMovement movement = new StockMovement();
                    movement.setLot(lot);
                    movement.setMovementType(MovementType.OUT);
                    movement.setQty(deduct);
                    movement.setRefId(e.getId());
                    movement.setRefType(REF_TYPE);
                    movement.setNote("Điều chỉnh giảm từ phiếu " + e.getCode());
                    stockMovementRepository.save(movement);

                    remaining = remaining.subtract(deduct);
                }

                if (remaining.compareTo(BigDecimal.ZERO) > 0) {
                    throw new IllegalStateException(
                            "Không đủ tồn kho để điều chỉnh giảm: " + line.getItem().getName()
                            + " (còn thiếu " + remaining + " " + line.getUnit() + ")");
                }
            }
        }
    }

    /**
     * Tất cả phiếu liên quan đến 1 kho (source OR target) theo approvalStatus.
     * Dùng cho tab pending/approved của từng kho trên UI.
     */
    public List<InventoryRequestResponse> findByWarehouse(String warehouseCode, String approvalStatusStr) {
        return repository.findByWarehouseAndStatus(warehouseCode, approvalStatusStr.toUpperCase()).stream()
                .map(this::toResponse)
                .toList();
    }

    /** Build lines từ request và gắn vào entity — dùng chung trong toEntity() và applyUpdate(). */
    private void buildAndAddLines(InventoryRequest e, List<InventoryRequestLineRequest> lines) {
        if (lines == null) return;
        for (int i = 0; i < lines.size(); i++) {
            InventoryRequestLineRequest lr = lines.get(i);
            InventoryRequestLine line = new InventoryRequestLine();
            line.setInventoryRequest(e);
            line.setItem(itemRepository.findById(lr.itemId())
                    .orElseThrow(() -> new ResourceNotFoundException("Item", lr.itemId())));
            line.setQuantity(lr.quantity());
            line.setUnit(lr.unit());
            line.setUnitCost(lr.unitCost());
            line.setSortOrder(lr.sortOrder() != null ? lr.sortOrder() : i + 1);
            line.setNote(lr.note());
            e.getLines().add(line);
        }
    }

    private BigDecimal roundUpToUnitSize(BigDecimal qty, com.bakery.api.master.entity.Item item) {
        if (item.isSplittable() || item.getUnitSize() == null
                || item.getUnitSize().compareTo(BigDecimal.ZERO) <= 0) {
            return qty;
        }
        BigDecimal unitSize = item.getUnitSize();
        // Math.ceil(qty / unitSize) * unitSize
        BigDecimal units = qty.divide(unitSize, 0, java.math.RoundingMode.CEILING);
        return units.multiply(unitSize);
    }
}
