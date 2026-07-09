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
import org.springframework.stereotype.Service;

/**
 * Service quản lý phiếu nhập/điều chuyển kho.
 *
 * <p>On APPROVE:
 *   PURCHASE → tạo StockLot + StockMovement(IN) cho mỗi line
 *   TRANSFER → (Phase 2) tạo StockMovement(OUT) + StockMovement(IN)
 */
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
    private final CommandRequestRepository commandRequestRepository;
    private final BakeryActorResolver actorResolver;

    // ── Framework wiring ─────────────────────────────────────────

    @Override
    protected BaseRepository<InventoryRequest> getRepository() {
        return repository;
    }

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

        if (req.targetWarehouseId() != null) {
            e.setTargetWarehouse(warehouseRepository.findById(req.targetWarehouseId())
                    .orElseThrow(() -> new ResourceNotFoundException("Warehouse", req.targetWarehouseId())));
        }
        if (req.supplierId() != null) {
            e.setSupplier(supplierRepository.findById(req.supplierId())
                    .orElseThrow(() -> new ResourceNotFoundException("Supplier", req.supplierId())));
        }

        // Build lines (parent ID not yet assigned — set after beforeCreate/save via cascade)
        if (req.lines() != null) {
            for (int i = 0; i < req.lines().size(); i++) {
                InventoryRequestLineRequest lr = req.lines().get(i);
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
        return e;
    }

    @Override
    protected void applyUpdate(InventoryRequest e, InventoryRequestRequest req) {
        e.setRequestDate(req.requestDate() != null ? req.requestDate() : e.getRequestDate());
        e.setExpectedDeliveryDate(req.expectedDeliveryDate());
        e.setNote(req.note());

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
        if (req.lines() != null) {
            for (int i = 0; i < req.lines().size(); i++) {
                InventoryRequestLineRequest lr = req.lines().get(i);
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
                    e.getSourceWarehouse().getId().toString(), e.getSourceWarehouse().getName()));
        }
        if (e.getTargetWarehouse() != null) {
            r.setTargetWarehouse(new ReferenceValue(
                    e.getTargetWarehouse().getId().toString(), e.getTargetWarehouse().getName()));
        }
        if (e.getSupplier() != null) {
            r.setSupplier(new ReferenceValue(
                    e.getSupplier().getId().toString(), e.getSupplier().getName()));
        }

        List<InventoryRequestLineResponse> lineResponses = e.getLines().stream()
                .map(line -> {
                    InventoryRequestLineResponse lr = new InventoryRequestLineResponse();
                    lr.applyFrom(line);
                    if (line.getItem() != null) {
                        lr.setItem(new ReferenceValue(
                                line.getItem().getId().toString(), line.getItem().getName()));
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
        String prefix = e.getRequestType() == InventoryRequestType.PURCHASE ? "PO" : "TR";
        String dateStr = LocalDate.now().format(DATE_FMT);
        String codePrefix = prefix + "-" + dateStr + "-";

        long todayCount = repository.findByRequestTypeAndRequestDate(
                        e.getRequestType(), LocalDate.now())
                .size();
        String seq = String.format("%03d", todayCount + 1);
        e.setCode(codePrefix + seq);
    }

    /**
     * On APPROVE (PURCHASE only):
     * Mỗi line → tạo StockLot + StockMovement(IN)
     */
    @Override
    protected void afterApprove(InventoryRequest e) {
        if (e.getRequestType() != InventoryRequestType.PURCHASE) {
            return; // TRANSFER handled separately
        }

        UUID warehouseId = e.getTargetWarehouse() != null ? e.getTargetWarehouse().getId() : null;
        LocalDate receivedDate = e.getRequestDate();

        for (InventoryRequestLine line : e.getLines()) {
            Item item = line.getItem();

            // Tính expiry date nếu có config
            LocalDate expiryDate = expiryConfigRepository.findByItemId(item.getId())
                    .map(cfg -> receivedDate.plusDays(cfg.getShelfDays()))
                    .orElse(null);

            // Tạo StockLot
            StockLot lot = new StockLot();
            lot.setItem(item);
            lot.setSupplier(e.getSupplier());
            lot.setWarehouseId(warehouseId);
            lot.setQtyInitial(line.getQuantity());
            lot.setQtyRemaining(line.getQuantity());
            lot.setUnitCost(line.getUnitCost() != null ? line.getUnitCost() : BigDecimal.ZERO);
            lot.setReceivedDate(receivedDate);
            lot.setExpiryDate(expiryDate);
            StockLot savedLot = stockLotRepository.save(lot);

            // Tạo StockMovement(IN)
            StockMovement movement = new StockMovement();
            movement.setLot(savedLot);
            movement.setMovementType(MovementType.IN);
            movement.setQty(line.getQuantity());
            movement.setRefId(e.getId());
            movement.setRefType(REF_TYPE);
            movement.setNote("Nhập hàng từ phiếu " + e.getCode());
            stockMovementRepository.save(movement);
        }
    }
}
