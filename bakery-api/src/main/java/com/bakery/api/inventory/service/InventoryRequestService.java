package com.bakery.api.inventory.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import com.bakery.api.inventory.dto.InventoryRequestLineRequest;
import com.bakery.api.inventory.dto.InventoryRequestLineResponse;
import com.bakery.api.inventory.dto.InventoryRequestRequest;
import com.bakery.api.inventory.dto.InventoryRequestResponse;
import com.bakery.api.inventory.dto.MarkPaidResponse;
import com.bakery.api.inventory.dto.ReceiveRequest;
import com.bakery.api.inventory.entity.InventoryRequest;
import com.bakery.api.inventory.entity.InventoryRequestLine;
import com.bakery.api.inventory.entity.PaymentStatus;
import com.bakery.api.inventory.entity.StockLot;
import com.bakery.api.inventory.entity.StockMovement;
import com.bakery.api.inventory.repository.InventoryRequestLineRepository;
import com.bakery.api.inventory.repository.InventoryRequestRepository;
import com.bakery.api.inventory.repository.StockLotRepository;
import com.bakery.api.inventory.repository.StockMovementRepository;
import com.bakery.api.pricing.entity.IngredientPrice;
import com.bakery.api.pricing.repository.IngredientPriceRepository;
import java.util.Optional;
import com.bakery.api.inventory.dto.InventoryRequestLineResponse.PackagingRef;
import com.bakery.api.master.entity.CodeValue;
import com.bakery.api.master.entity.Item;
import com.bakery.api.master.entity.Warehouse;
import com.bakery.framework.entity.EntityStatus;
import com.bakery.api.master.entity.ItemPackaging;
import com.bakery.api.master.repository.ItemLookupRepository;
import com.bakery.api.master.repository.ItemPackagingRepository;
import com.bakery.api.master.repository.ProductExpiryConfigRepository;
import com.bakery.api.master.repository.SupplierRepository;
import com.bakery.api.master.repository.UnitConversionRepository;
import com.bakery.api.master.repository.WarehouseRepository;
import com.bakery.framework.entity.ApprovalStatus;
import com.bakery.framework.entity.CommandAction;
import com.bakery.framework.entity.InventoryRequestType;
import com.bakery.framework.entity.MovementType;
import com.bakery.framework.exception.ResourceNotFoundException;
import com.bakery.framework.metadata.ReferenceValue;
import com.bakery.framework.repository.BaseRepository;
import com.bakery.framework.repository.CommandRequestRepository;
import com.bakery.framework.security.BakeryActorResolver;
import com.bakery.framework.service.AbstractBakeryAdminService;
import com.bakery.framework.util.SpecificationBuilder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

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
    private final InventoryRequestLineRepository lineRepository;
    private final WarehouseRepository warehouseRepository;
    private final SupplierRepository supplierRepository;
    private final ItemLookupRepository itemRepository;
    private final ItemPackagingRepository packagingRepository;
    private final StockLotRepository stockLotRepository;
    private final StockMovementRepository stockMovementRepository;
    private final StockTransfer stockTransfer;
    private final ProductExpiryConfigRepository expiryConfigRepository;
    private final UnitConversionRepository unitConversionRepository;
    private final IngredientPriceRepository ingredientPriceRepository;
    private final CommandRequestRepository commandRequestRepository;
    private final BakeryActorResolver actorResolver;
    private final com.bakery.api.master.repository.CodeValueRepository codeValueRepository;
    private final com.bakery.api.auth.service.AccessScope scope;

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

        // 22: sửa phiếu không được đổi sang kho EXTERNAL / kho không Nhận NCC (phiếu nhập)
        requireWarehouseRoles(e.getRequestType(), e.getSourceWarehouse(), e.getTargetWarehouse());
        // 11/D2: sau khi đổi kho, phiếu vẫn phải thuộc phạm vi của user
        scope.assertAnyWarehouse(e.getSourceWarehouse(), e.getTargetWarehouse());

        // Replace all lines (orphanRemoval cleans up removed ones)
        Map<UUID, BigDecimal> keepCosts = new HashMap<>();
        e.getLines().forEach(l -> { if (l.getUnitCost() != null && l.getItem() != null) keepCosts.put(l.getItem().getId(), l.getUnitCost()); });
        e.getLines().clear();
        buildAndAddLines(e, req.lines(), keepCosts);
    }

    @Override
    protected InventoryRequestResponse toResponse(InventoryRequest e) {
        return toResponse(e, exportSuppliers(List.of(e)), pickStock(List.of(e)));
    }

    /** Danh sách phiếu: một query NCC cho cả trang phiếu xuất (25), không query theo từng phiếu. */
    private List<InventoryRequestResponse> toResponses(Collection<InventoryRequest> es) {
        Map<UUID, Map<UUID, Set<UUID>>> sup = exportSuppliers(es);
        Map<String, BigDecimal> stock = pickStock(es);
        return es.stream().map(x -> toResponse(x, sup, stock)).toList();
    }

    /** 24/BE-5: PICK chờ lấy mà user xem được tồn kho nguồn: giữ phiếu cần tồn, một query cho cả trang. */
    private static boolean pendingPick(InventoryRequest x) {
        return x.getRequestType() == InventoryRequestType.PICK && x.getApprovalStatus() == ApprovalStatus.PENDING_APPROVAL
                && x.getSourceWarehouse() != null;
    }

    /** Tồn chung "kho|mặt hàng" của các phiếu PICK chờ lấy có kho nguồn user xem được tồn. */
    private Map<String, BigDecimal> pickStock(Collection<InventoryRequest> es) {
        Map<String, BigDecimal> out = new HashMap<>();
        List<InventoryRequest> picks = es.stream().filter(InventoryRequestService::pendingPick)
                .filter(x -> scope.canSeeStockOf(x.getSourceWarehouse())).toList();
        if (picks.isEmpty()) return out;
        Set<UUID> whs = picks.stream().map(x -> x.getSourceWarehouse().getId()).collect(java.util.stream.Collectors.toSet());
        Set<UUID> items = picks.stream().flatMap(x -> x.getLines().stream()).filter(l -> l.getItem() != null)
                .map(l -> l.getItem().getId()).collect(java.util.stream.Collectors.toSet());
        if (items.isEmpty()) return out;
        for (Object[] row : stockLotRepository.sumFreeByWarehouseItem(whs, items)) out.put(row[0] + "|" + row[1], (BigDecimal) row[2]);
        return out;
    }

    /** NCC của các lô bị trừ, theo phiếu xuất đã duyệt → mặt hàng → tập NCC (có thể chứa null = lô không NCC). */
    private Map<UUID, Map<UUID, Set<UUID>>> exportSuppliers(Collection<InventoryRequest> es) {
        List<UUID> ids = es.stream().filter(x -> x.getRequestType() == InventoryRequestType.EXPORT
                && x.getApprovalStatus() == ApprovalStatus.APPROVED).map(InventoryRequest::getId).toList();
        Map<UUID, Map<UUID, Set<UUID>>> out = new HashMap<>();
        if (ids.isEmpty()) return out;
        for (Object[] row : stockMovementRepository.exportSuppliers(ids)) {
            out.computeIfAbsent((UUID) row[0], k -> new HashMap<>())
               .computeIfAbsent((UUID) row[1], k -> new java.util.HashSet<>()).add((UUID) row[2]);
        }
        return out;
    }

    private InventoryRequestResponse toResponse(InventoryRequest e, Map<UUID, Map<UUID, Set<UUID>>> exportSup, Map<String, BigDecimal> pickStock) {
        InventoryRequestResponse r = new InventoryRequestResponse();
        r.applyFrom(e);
        r.setCode(e.getCode());
        r.setRequestType(e.getRequestType());
        r.setRequestDate(e.getRequestDate());
        r.setExpectedDeliveryDate(e.getExpectedDeliveryDate());
        r.setNote(e.getNote());
        r.setPickReason(e.getPickReason());
        r.setSessionId(e.getSessionId());
        if (e.getProductionRequest() != null) {
            r.setProductionRequest(new InventoryRequestResponse.ProductionRequestRef(
                    e.getProductionRequest().getId(), e.getProductionRequest().getCode()));
        }

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

        boolean purchase = e.getRequestType() == InventoryRequestType.PURCHASE;
        boolean export = e.getRequestType() == InventoryRequestType.EXPORT;
        boolean approved = e.getApprovalStatus() == ApprovalStatus.APPROVED;
        Map<UUID, Set<UUID>> supByItem = exportSup.getOrDefault(e.getId(), Map.of());
        // 11/D6: xem được giá theo NCC của phiếu (Admin: luôn; phiếu không NCC: chỉ Admin)
        boolean seePrice = scope.canSeePurchasePrice(e.getSupplier() != null ? e.getSupplier().getId() : null);
        boolean[] hidden = {false};
        // 24/BE-5: tồn kho nguồn ở phiếu lấy chỉ cho người xem được tồn; người khác không thấy số, không thấy tag Thiếu
        boolean pick = e.getRequestType() == InventoryRequestType.PICK;
        boolean seeStock = pick && scope.canSeeStockOf(e.getSourceWarehouse());
        boolean pendingPick = pendingPick(e);
        // 08: phiếu điều chỉnh — tên lý do, và tồn hiện tại (chỉ khi chưa duyệt) cho admin so với tồn lúc kiểm
        boolean adjustment = e.getRequestType() == InventoryRequestType.ADJUSTMENT;
        Map<String, String> reasonNames = adjustment ? reasonNames() : Map.of();
        Map<UUID, BigDecimal> currentQty = adjustment && e.getTargetWarehouse() != null
                && (e.getApprovalStatus() == ApprovalStatus.DRAFT || e.getApprovalStatus() == ApprovalStatus.PENDING_APPROVAL)
                ? stockAt(e.getTargetWarehouse().getId(),
                        e.getLines().stream().filter(l -> l.getItem() != null).map(l -> l.getItem().getId()).toList())
                : null;
        List<InventoryRequestLineResponse> lineResponses = e.getLines().stream()
                .map(line -> {
                    InventoryRequestLineResponse lr = new InventoryRequestLineResponse();
                    lr.applyFrom(line);
                    if (line.getItem() != null) {
                        lr.setItemId(line.getItem().getId());
                        lr.setItem(new ReferenceValue(
                                line.getItem().getCode(), line.getItem().getName()));
                    }
                    lr.setQuantity(line.getQuantity());
                    lr.setUnit(line.getUnit());
                    if (export) {
                        // 25/C3: dòng hiện giá khi xem được giá của MỌI NCC có lô bị trừ; chưa duyệt thì chưa có giá
                        boolean vis = approved && line.getItem() != null
                                && ExportCost.priceVisible(supByItem.get(line.getItem().getId()), scope::canSeePurchasePrice);
                        lr.setUnitCost(vis ? line.getUnitCost() : null);
                        lr.setAmount(vis ? line.getAmount() : null);
                        if (approved && !vis) {
                            lr.setPriceHidden(true);
                            hidden[0] = true;
                        }
                    } else {
                        lr.setUnitCost(seePrice ? line.getUnitCost() : null);
                        if (!seePrice && (line.getUnitCost() != null || line.getAmount() != null)) {
                            lr.setPriceHidden(true);
                            hidden[0] = true;
                        }
                    }
                    lr.setSortOrder(line.getSortOrder());
                    lr.setNote(line.getNote());
                    lr.setPurchaseQty(line.getPurchaseQty());
                    lr.setReceivedQty(line.getReceivedQty());
                    if (pick) {
                        BigDecimal have = pendingPick && seeStock && line.getItem() != null && e.getSourceWarehouse() != null
                                ? pickStock.getOrDefault(e.getSourceWarehouse().getId() + "|" + line.getItem().getId(), BigDecimal.ZERO) : null;
                        lr.setStock(have);
                        lr.setStockShort(seeStock && (have != null ? line.getQuantity().compareTo(have) > 0 : line.isStockShort()));
                    } else {
                        lr.setStockShort(line.isStockShort());
                    }
                    if (adjustment) {
                        lr.setSystemQty(line.getSystemQty());
                        lr.setCountedQty(line.getCountedQty());
                        lr.setReasonCode(line.getReasonCode());
                        if (line.getReasonCode() != null) {
                            lr.setReason(new ReferenceValue(line.getReasonCode(),
                                    reasonNames.getOrDefault(line.getReasonCode(), line.getReasonCode())));
                        }
                        lr.setAmount(seePrice ? line.getAmount() : null);
                        if (currentQty != null && line.getItem() != null) {
                            lr.setCurrentQty(currentQty.getOrDefault(line.getItem().getId(), BigDecimal.ZERO));
                        }
                    }
                    if (line.getPackaging() != null) {
                        PackagingRef pRef = new PackagingRef();
                        pRef.setId(line.getPackaging().getId());
                        pRef.setCode(line.getPackaging().getCode());
                        pRef.setName(line.getPackaging().getName());
                        pRef.setQtyPerPack(line.getPackaging().getQtyPerPack());
                        lr.setPackaging(pRef);
                    }
                    if (purchase) {
                        lr.setTotalCost(seePrice ? lineTotal(line, approved) : null);
                    }
                    return lr;
                })
                .toList();
        r.setLines(lineResponses);

        if (purchase) {
            r.setDeliveryStatus(deliveryStatus(e, LocalDate.now()));
            r.setPaymentStatus(e.getPaymentStatus() != null ? e.getPaymentStatus().name() : null);
            r.setPaidAt(e.getPaidAt());
            r.setPaidBy(e.getPaidBy());
            r.setCanUnpay(canUnpay(scope.isAdmin(), e.getPaymentStatus(), e.getPaidAt(), Instant.now()));
            r.setReceivedDate(e.getReceivedDate());
            r.setReceivedBy(e.getReceivedBy());
            r.setTotalAmount(!seePrice ? null : lineResponses.stream()
                    .map(InventoryRequestLineResponse::getTotalCost)
                    .filter(java.util.Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add));
            r.setPriceMissing(e.getLines().stream().anyMatch(l -> l.getUnitCost() == null));
        }
        if (export) {
            r.setReceivedDate(e.getReceivedDate());
            r.setReceivedBy(e.getReceivedBy());
            r.setTotalAmount(approved && !hidden[0] ? lineResponses.stream().map(InventoryRequestLineResponse::getAmount)
                    .filter(java.util.Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add) : null);
        }
        r.setPriceHidden(hidden[0] || (purchase && !seePrice));
        // 11/BE-4: để FE ẩn/hiện nút Sửa, Xóa theo quyền thật
        r.setCreatedByMe(createdByMe(e));
        r.setCanEdit(e.getApprovalStatus() != ApprovalStatus.APPROVED && e.getApprovalStatus() != ApprovalStatus.REJECTED
                && editDenied(e) == null);
        return r;
    }

    // ── Lifecycle hooks ──────────────────────────────────────────

    /**
     * Auto-generate code: PO-YYYYMMDD-NNN hoặc TR-YYYYMMDD-NNN
     */
    @Override
    protected void beforeCreate(InventoryRequest e) {
        requireNotPoolType(e.getRequestType());
        authorizeCreate(e);
        requireDeliveryDate(e.getRequestType(), e.getExpectedDeliveryDate());
        requireWarehouses(e.getRequestType(), e.getSourceWarehouse() != null, e.getTargetWarehouse() != null);
        requireWarehouseRoles(e.getRequestType(), e.getSourceWarehouse(), e.getTargetWarehouse());
        String prefix = switch (e.getRequestType()) {
            case PURCHASE -> "PO";
            case TRANSFER -> "TR";
            case ADJUSTMENT -> "ADJ";
            case PICK -> "PK";
            case RETURN -> "RT";
            case WRITE_OFF -> "WO";
            case EXPORT -> "XK";
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
            // POST /approve chung = nhận đủ; /receive đã gán receivedQty từng dòng trước khi vào đây
            e.getLines().stream()
                    .filter(l -> l.getReceivedQty() == null)
                    .forEach(l -> l.setReceivedQty(l.getQuantity()));
            e.setReceivedDate(LocalDate.now(VN));   // 06/BE-1: cả /approve lẫn /receive đều qua đây
            e.setReceivedBy(actorResolver.currentUsername());
            approvePurchase(e);
        } else if (e.getRequestType() == InventoryRequestType.TRANSFER) {
            approveTransfer(e);
        } else if (e.getRequestType() == InventoryRequestType.ADJUSTMENT) {
            approveAdjustment(e);
        } else if (e.getRequestType() == InventoryRequestType.EXPORT) {
            e.setReceivedDate(LocalDate.now(VN));   // 25: ngày xuất = ngày duyệt (giờ VN)
            e.setReceivedBy(actorResolver.currentUsername());
            approveExport(e);
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

    /**
     * PURCHASE: tạo StockLot + StockMovement(IN) cho mỗi line theo {@code receivedQty}.
     * Dòng nhận 0 → không tạo lô / movement / lịch sử giá.
     */
    private void approvePurchase(InventoryRequest e) {

        // Ngày nhập = ngày xác nhận hàng về (không phải ngày đặt) → HSD và FIFO tính đúng
        LocalDate receivedDate = LocalDate.now();

        for (InventoryRequestLine line : e.getLines()) {
            BigDecimal received = line.getReceivedQty();
            if (received == null || received.signum() <= 0) continue;
            Item item = line.getItem();

            // Convert qty từ line.unit → item.unit trước khi lưu vào StockLot
            // VD: nhập 10 KG, item.unit = G → lưu 10000 trong lot
            BigDecimal qtyInItemUnit = convertToItemUnit(received, line.getUnit(), item);

            // Tính expiry date nếu có config
            LocalDate expiryDate = expiryConfigRepository.findByItemId(item.getId())
                    .map(cfg -> receivedDate.plusDays(cfg.getShelfDays()))
                    .orElse(null);

            BigDecimal unitCostPerCanonical = unitCostPerCanonical(line);

            // Tạo StockLot — qty luôn ở item.unit
            StockLot lot = new StockLot();
            lot.setItem(item);
            lot.setSupplier(e.getSupplier());
            lot.setWarehouse(e.getTargetWarehouse());
            lot.setQtyInitial(qtyInItemUnit);
            lot.setQtyRemaining(qtyInItemUnit);
            lot.setUnitCost(unitCostPerCanonical);
            lot.setReceivedDate(receivedDate);
            lot.setExpiryDate(expiryDate);
            // Lưu packaging để hiển thị "X Bao 20kg" trên màn hình kho (số bao có thể lẻ)
            ItemPackaging pack = line.getPackaging();
            lot.setPackaging(pack);
            lot.setQtyReceivedPack(pack != null && pack.getQtyPerPack().signum() > 0
                    ? received.divide(pack.getQtyPerPack(), 4, RoundingMode.HALF_UP)
                    : null);
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

            // Lưu lịch sử giá theo item.unit (không phải giá/bao) — RecipeCostService, lastPrice đọc theo item.unit
            if (line.getUnitCost() != null && line.getUnitCost().signum() > 0) {
                IngredientPrice priceRecord = new IngredientPrice();
                priceRecord.setItem(item);
                priceRecord.setSupplier(e.getSupplier());
                priceRecord.setPrice(unitCostPerCanonical.setScale(2, RoundingMode.HALF_UP));
                priceRecord.setEffectiveDate(receivedDate);
                priceRecord.setLotRef(e.getCode());
                ingredientPriceRepository.save(priceRecord);
            }
        }
    }

    // ── Tính toán thuần (không phụ thuộc Spring — có test) ───────────────────

    /**
     * Giá trên 1 đơn vị item.unit. Có bao: unitCost là giá/bao → chia qtyPerPack;
     * không bao: unitCost đã theo item.unit. Thiếu giá → 0.
     */
    static BigDecimal unitCostPerCanonical(InventoryRequestLine line) {
        BigDecimal cost = line.getUnitCost();
        if (cost == null) return BigDecimal.ZERO;
        ItemPackaging pack = line.getPackaging();
        if (pack != null && pack.getQtyPerPack() != null && pack.getQtyPerPack().signum() > 0) {
            return cost.divide(pack.getQtyPerPack(), 4, RoundingMode.HALF_UP);
        }
        return cost;
    }

    /**
     * Thành tiền 1 dòng phiếu nhập (làm tròn 2 số lẻ). Nhân trước, chia sau để tránh sai số.
     * Đã duyệt → theo receivedQty; chưa duyệt → theo số đặt. null nếu dòng chưa có giá.
     */
    static BigDecimal lineTotal(InventoryRequestLine line, boolean approved) {
        BigDecimal cost = line.getUnitCost();
        if (cost == null) return null;
        ItemPackaging pack = line.getPackaging();
        BigDecimal perPack = pack != null && pack.getQtyPerPack() != null && pack.getQtyPerPack().signum() > 0
                ? pack.getQtyPerPack() : null;

        BigDecimal amount;
        if (approved) {
            BigDecimal qty = line.getReceivedQty() != null ? line.getReceivedQty() : line.getQuantity();
            amount = perPack != null ? cost.multiply(qty).divide(perPack, 2, RoundingMode.HALF_UP)
                                     : cost.multiply(qty);
        } else if (line.getPurchaseQty() != null) {
            amount = cost.multiply(line.getPurchaseQty());
        } else {
            amount = perPack != null ? cost.multiply(line.getQuantity()).divide(perPack, 2, RoundingMode.HALF_UP)
                                     : cost.multiply(line.getQuantity());
        }
        return amount.setScale(2, RoundingMode.HALF_UP);
    }

    /** DELIVERED | PENDING | OVERDUE; null nếu REJECTED hoặc không phải PURCHASE. */
    static String deliveryStatus(InventoryRequest e, LocalDate today) {
        if (e.getRequestType() != InventoryRequestType.PURCHASE || e.getApprovalStatus() == null) return null;
        return switch (e.getApprovalStatus()) {
            case APPROVED -> "DELIVERED";
            case DRAFT, PENDING_APPROVAL -> e.getExpectedDeliveryDate() != null
                    && e.getExpectedDeliveryDate().isBefore(today) ? "OVERDUE" : "PENDING";
            default -> null;
        };
    }

    /**
     * Kiểm số thực nhận theo từng dòng (D2, D4). Ném IllegalStateException (→ 422) nêu tên hàng.
     *
     * @param received lineId → số thực nhận (đã đủ mọi dòng)
     */
    static void validateReceived(Collection<InventoryRequestLine> lines, Map<UUID, BigDecimal> received) {
        List<String> errors = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        for (InventoryRequestLine line : lines) {
            BigDecimal qty = received.get(line.getId());
            String name = line.getItem().getName();
            if (qty.signum() < 0) {
                errors.add(name + ": số nhận không được âm");
            } else if (qty.compareTo(line.getQuantity()) > 0) {
                errors.add(name + ": giao dư (nhận " + qty.stripTrailingZeros().toPlainString()
                        + " > đặt " + line.getQuantity().stripTrailingZeros().toPlainString() + " " + line.getUnit()
                        + ") — hãy từ chối hoặc sửa phiếu");
            } else {
                total = total.add(qty);
            }
        }
        if (!errors.isEmpty()) {
            throw new IllegalStateException("Số nhận không hợp lệ: " + String.join("; ", errors));
        }
        if (total.signum() == 0) {
            throw new IllegalStateException("Tất cả số nhận bằng 0 — hãy từ chối phiếu");
        }
    }

    /** Lý do phiếu không thanh toán được (null = hợp lệ). D6, D7. */
    static String paymentBlocker(InventoryRequest e) {
        if (e.getRequestType() != InventoryRequestType.PURCHASE) return "không phải phiếu nhập";
        if (e.getApprovalStatus() != ApprovalStatus.APPROVED) return "chưa xác nhận";
        if (e.getPaymentStatus() == PaymentStatus.PAID) return "đã thanh toán";
        if (e.getLines().stream().anyMatch(l -> l.getUnitCost() == null)) return "thiếu đơn giá";
        return null;
    }

    /** Thông báo lỗi nếu phiếu thiếu kho bắt buộc, null nếu đủ. Không kiểm hàng có hợp kho hay không (D10). */
    static String warehouseBlocker(InventoryRequestType type, boolean hasSource, boolean hasTarget) {
        if (!hasTarget) return "Phiếu phải có kho nhận";
        if (type == InventoryRequestType.TRANSFER && !hasSource) return "Phiếu điều chuyển phải có kho xuất";
        if (type == InventoryRequestType.EXPORT && !hasSource) return "Phiếu xuất ngoài phải có kho xuất";
        return null;
    }

    private static void requireWarehouses(InventoryRequestType type, boolean hasSource, boolean hasTarget) {
        String msg = warehouseBlocker(type, hasSource, hasTarget);
        if (msg != null) throw new IllegalStateException(msg);
    }

    /** 24: ba loại phiếu bảng tạm chỉ thao tác ở màn Đang sản xuất, không đi qua /inventory-requests. */
    static boolean isPoolType(InventoryRequestType t) {
        return t == InventoryRequestType.PICK || t == InventoryRequestType.RETURN || t == InventoryRequestType.WRITE_OFF;
    }

    static final String POOL_ONLY = "Phiếu bảng tạm thao tác ở màn Đang sản xuất";

    static String poolTypeBlocker(InventoryRequestType t) {
        return isPoolType(t) ? POOL_ONLY : null;
    }

    private static void requireNotPoolType(InventoryRequestType t) {
        String m = poolTypeBlocker(t);
        if (m != null) throw new IllegalStateException(m);
    }

    /** 24: kho bảng tạm không làm kho xuất / nhận của PURCHASE, TRANSFER, ADJUSTMENT. */
    static String wipWarehouseBlocker(InventoryRequestType type, Warehouse... ws) {
        if (isPoolType(type)) return null;
        for (Warehouse w : ws) {
            if (w != null && w.isWorkInProgress()) {
                return "Kho " + w.getName() + " là kho bảng tạm, chỉ dùng cho phiếu Lấy / Trả / Hao hụt NL";
            }
        }
        return null;
    }

    /** 22: kho nhận của phiếu nhập phải có việc Nhận NCC (thay cho "chỉ MAIN" của 21/BE-2); mọi user kể cả Admin. Null = hợp lệ (thiếu kho do {@link #warehouseBlocker} báo). */
    static String purchaseWarehouseBlocker(InventoryRequestType type, Warehouse target) {
        if (type == InventoryRequestType.PURCHASE && target != null && !target.isCanReceivePurchase()) {
            return "Kho " + target.getName() + " không được nhập hàng từ nhà cung cấp";
        }
        return null;
    }

    /** 22: kho nơi nhận ngoài (EXTERNAL) không làm kho xuất / nhận của phiếu nào. */
    static String externalWarehouseBlocker(Warehouse... ws) {
        for (Warehouse w : ws) {
            if (w != null && w.isExternal()) return "Kho " + w.getName() + " là nơi nhận ngoài, không dùng cho phiếu kho";
        }
        return null;
    }

    /** 25: phiếu xuất ngoài: kho xuất INTERNAL có cờ Xuất ra ngoài, kho nhận là nơi nhận ngoài (EXTERNAL). Null = hợp lệ. */
    static String exportWarehouseBlocker(Warehouse source, Warehouse target) {
        if (source != null && (source.isExternal() || !source.isCanExportExternal())) {
            return "Kho " + source.getName() + " không được xuất ra ngoài";
        }
        if (target != null && !target.isExternal()) return "Phiếu xuất ngoài phải gửi tới nơi nhận ngoài";
        return null;
    }

    /** 25: không cho trùng mặt hàng trong một phiếu xuất ngoài (để tính NCC theo dòng không lẫn). */
    static String duplicateLineBlocker(List<InventoryRequestLine> lines) {
        Set<UUID> seen = new java.util.HashSet<>();
        for (InventoryRequestLine l : lines) {
            if (l.getItem() != null && !seen.add(l.getItem().getId())) return "Mặt hàng " + l.getItem().getName() + " bị trùng dòng";
        }
        return null;
    }

    /** Kiểm kho của phiếu theo cấu hình: không EXTERNAL (trừ nơi nhận của EXPORT), PURCHASE chỉ vào kho Nhận NCC. */
    private static void requireWarehouseRoles(InventoryRequestType type, Warehouse source, Warehouse target) {
        String msg = type == InventoryRequestType.EXPORT ? exportWarehouseBlocker(source, target) : externalWarehouseBlocker(source, target);
        if (msg == null) msg = wipWarehouseBlocker(type, source, target);
        if (msg == null) msg = purchaseWarehouseBlocker(type, target);
        if (msg != null) throw new IllegalStateException(msg);
    }

    /** D5: phiếu nhập bắt buộc có ngày giao dự kiến. */
    private static void requireDeliveryDate(InventoryRequestType type, LocalDate expected) {
        if (type == InventoryRequestType.PURCHASE && expected == null) {
            throw new IllegalStateException("Phiếu nhập phải có ngày giao dự kiến");
        }
    }

    /**
     * Cập nhật đơn giá cho 1 line trong phiếu nhập.
     * Chỉ cho phép khi phiếu chưa được duyệt (đã duyệt → 422).
     */
    @Transactional
    public void updateLineCost(UUID requestId, UUID lineId, BigDecimal unitCost) {
        InventoryRequestLine line = lineRepository.findById(lineId)
                .orElseThrow(() -> new ResourceNotFoundException("InventoryRequestLine", lineId));
        if (!line.getInventoryRequest().getId().equals(requestId)) {
            throw new IllegalArgumentException("Dòng không thuộc phiếu này");
        }
        InventoryRequest owner = line.getInventoryRequest();
        requireNotPoolType(owner.getRequestType());
        authorize(owner, "UPDATE");
        if (!scope.canSeePurchasePrice(owner.getSupplier() != null ? owner.getSupplier().getId() : null)) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN,
                    "Bạn không có quyền xem/nhập giá của NCC này");
        }
        if (line.getInventoryRequest().getApprovalStatus() == ApprovalStatus.APPROVED) {
            throw new IllegalStateException("Phiếu đã xác nhận, không thể sửa đơn giá");
        }
        line.setUnitCost(unitCost);
        lineRepository.save(line);
    }

    /**
     * TRANSFER: FIFO deduct từ source warehouse → tạo StockMovement(OUT) mỗi lô.
     * Nếu tồn kho không đủ → throw IllegalStateException.
     */
    private void approveTransfer(InventoryRequest e) {
        for (InventoryRequestLine line : e.getLines()) {
            // Convert qty từ line.unit → item.unit (StockLot luôn lưu theo item.unit)
            BigDecimal convertedQty = convertToItemUnit(line.getQuantity(), line.getUnit(), line.getItem());
            // Fixed unit: nếu item không tách lẻ → làm tròn lên bội số của unitSize
            BigDecimal qty = roundUpToUnitSize(convertedQty, line.getItem());

            // 24: phần chuyển lô FIFO dùng chung với PICK / RETURN (StockTransfer)
            BigDecimal remaining = stockTransfer.move(line.getItem(), qty, e.getSourceWarehouse(), e.getTargetWarehouse(),
                    null, null, e.getRequestDate(), REF_TYPE, e.getId(),
                    "Xuất hàng từ phiếu " + e.getCode(), "Nhận hàng từ phiếu " + e.getCode());
            if (remaining.compareTo(BigDecimal.ZERO) > 0) {
                throw new IllegalStateException(
                        "Không đủ tồn kho cho sản phẩm: " + line.getItem().getName()
                        + " (còn thiếu " + remaining + " " + line.getUnit() + ")");
            }
        }
    }

    /**
     * 25: EXPORT: trừ FIFO lô chung ở kho xuất, mỗi lô một movement OUT; KHÔNG tạo lô ở nơi nhận. Ghi giá nhập của chính
     * các lô bị trừ: {@code amount} = Σ số trừ × giá lô, {@code unitCost} = amount ÷ số lượng dòng. Thiếu tồn → 422.
     */
    private void approveExport(InventoryRequest e) {
        String dest = e.getTargetWarehouse() != null ? e.getTargetWarehouse().getName() : "nơi nhận ngoài";
        for (InventoryRequestLine line : e.getLines()) {
            BigDecimal qty = roundUpToUnitSize(convertToItemUnit(line.getQuantity(), line.getUnit(), line.getItem()), line.getItem());
            List<StockLot> lots = stockLotRepository
                    .findByItemIdAndWarehouseIdAndQtyRemainingGreaterThanOrderByReceivedDateAscCreatedAtAsc(
                            line.getItem().getId(), e.getSourceWarehouse().getId(), BigDecimal.ZERO).stream()
                    .filter(l -> l.getReservedForRequestId() == null).toList();
            ExportCost.Result r = ExportCost.deduct(lots, qty, (lot, taken) -> {
                stockLotRepository.save(lot);
                StockMovement out = new StockMovement();
                out.setLot(lot);
                out.setMovementType(MovementType.OUT);
                out.setQty(taken);
                out.setRefId(e.getId());
                out.setRefType(REF_TYPE);
                out.setNote("Xuất cho " + dest + ", phiếu " + e.getCode());
                stockMovementRepository.save(out);
            });
            if (r.remaining().signum() > 0) {
                throw new IllegalStateException("Không đủ tồn kho cho sản phẩm: " + line.getItem().getName()
                        + " (còn thiếu " + r.remaining() + " " + line.getUnit() + ")");
            }
            line.setAmount(r.amount());
            line.setUnitCost(ExportCost.unitCost(r.amount(), line.getQuantity()));
        }
    }

    /**
     * ADJUSTMENT: điều chỉnh tồn kho trong targetWarehouse.
     *   quantity > 0 → tăng kho: tạo StockLot mới + StockMovement(IN)
     *   quantity < 0 → giảm kho: FIFO deduct + StockMovement(OUT)
     */
    private void approveAdjustment(InventoryRequest e) {
        UUID warehouseId = e.getTargetWarehouse() != null ? e.getTargetWarehouse().getId() : null;
        Map<String, String> reasons = reasonNames();

        for (InventoryRequestLine line : e.getLines()) {
            BigDecimal qty = line.getQuantity();
            if (qty == null || qty.compareTo(BigDecimal.ZERO) == 0) continue;
            String reasonName = line.getReasonCode() == null ? null
                    : reasons.getOrDefault(line.getReasonCode(), line.getReasonCode());
            String suffix = reasonName == null ? "" : " – " + reasonName;

            if (qty.compareTo(BigDecimal.ZERO) > 0) {
                // Tăng kho — lô mới, giá = giá lô gần nhất (bỏ lô điều chỉnh và lô giá 0) của mặt hàng ở mọi kho (08/D6); không có → 0
                BigDecimal price = stockLotRepository.findLatestNonAdjustmentUnitCost(line.getItem().getId())
                        .orElse(BigDecimal.ZERO);
                StockLot lot = new StockLot();
                lot.setItem(line.getItem());
                lot.setWarehouse(e.getTargetWarehouse());
                lot.setQtyInitial(qty);
                lot.setQtyRemaining(qty);
                lot.setUnitCost(price);
                lot.setReceivedDate(LocalDate.now(VN));   // 08/BE-4: ngày duyệt (giờ VN), không phải ngày lập phiếu
                StockLot savedLot = stockLotRepository.save(lot);
                line.setAmount(qty.multiply(price).setScale(2, RoundingMode.HALF_UP));

                StockMovement movement = new StockMovement();
                movement.setLot(savedLot);
                movement.setMovementType(MovementType.IN);
                movement.setQty(qty);
                movement.setRefId(e.getId());
                movement.setRefType(REF_TYPE);
                movement.setNote("Điều chỉnh tăng từ phiếu " + e.getCode() + suffix);
                stockMovementRepository.save(movement);

            } else {
                // Giảm kho — FIFO deduct
                BigDecimal remaining = qty.abs();
                BigDecimal value = BigDecimal.ZERO;

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
                    if (lot.getUnitCost() != null) value = value.add(deduct.multiply(lot.getUnitCost()));

                    StockMovement movement = new StockMovement();
                    movement.setLot(lot);
                    movement.setMovementType(MovementType.OUT);
                    movement.setQty(deduct);
                    movement.setRefId(e.getId());
                    movement.setRefType(REF_TYPE);
                    movement.setNote("Điều chỉnh giảm từ phiếu " + e.getCode() + suffix);
                    stockMovementRepository.save(movement);

                    remaining = remaining.subtract(deduct);
                }

                if (remaining.compareTo(BigDecimal.ZERO) > 0) {
                    throw new IllegalStateException(
                            "Không đủ tồn kho để điều chỉnh giảm: " + line.getItem().getName()
                            + " (còn thiếu " + remaining.stripTrailingZeros().toPlainString() + " " + line.getUnit() + ")");
                }
                line.setAmount(value.negate().setScale(2, RoundingMode.HALF_UP));
            }
        }
    }

    // ── Khóa phiếu đã duyệt / đã từ chối ─────────────────────────

    /**
     * Phải kiểm trước applyUpdate: update() của base đổi trạng thái về PENDING_APPROVAL,
     * duyệt lại sẽ nhập kho lần hai. Phiếu đã từ chối cũng không được sống lại (D4).
     */
    @Override
    @Transactional
    public InventoryRequestResponse update(UUID id, InventoryRequestRequest request) {
        InventoryRequest e = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(getEntityName(), id));
        requireNotPoolType(e.getRequestType());
        authorize(e, "UPDATE");
        if (e.getApprovalStatus() == ApprovalStatus.APPROVED) {
            throw new IllegalStateException("Phiếu đã xác nhận, không thể sửa. Hãy tạo phiếu mới nếu cần bổ sung");
        }
        if (e.getApprovalStatus() == ApprovalStatus.REJECTED) {
            throw new IllegalStateException("Phiếu đã từ chối, không thể sửa. Hãy tạo phiếu mới");
        }
        requireDeliveryDate(e.getRequestType(), request.expectedDeliveryDate());
        // Không gửi kho thì giữ kho cũ; chỉ chặn nếu sau khi gộp vẫn thiếu
        requireWarehouses(e.getRequestType(),
                request.sourceWarehouseId() != null || e.getSourceWarehouse() != null,
                request.targetWarehouseId() != null || e.getTargetWarehouse() != null);
        return super.update(id, request);
    }

    /** Chốt chặn cho phiếu cũ thiếu kho: không duyệt, không tạo lô. Cả /approve và /receive đi qua đây. */
    @Override
    @Transactional
    public InventoryRequestResponse approve(UUID id) {
        InventoryRequest e = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(getEntityName(), id));
        requireNotPoolType(e.getRequestType());
        authorize(e, "APPROVE");
        requireWarehouses(e.getRequestType(), e.getSourceWarehouse() != null, e.getTargetWarehouse() != null);
        return super.approve(id);
    }

    @Override
    @Transactional
    public InventoryRequestResponse reject(UUID id, String reason) {
        InventoryRequest e = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(getEntityName(), id));
        requireNotPoolType(e.getRequestType());
        authorize(e, "REJECT");
        return super.reject(id, reason);
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        InventoryRequest e = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(getEntityName(), id));
        requireNotPoolType(e.getRequestType());
        authorize(e, "UPDATE");   // 11/D7: xóa phiếu cùng mức quyền với sửa
        if (e.getApprovalStatus() == ApprovalStatus.APPROVED) {
            throw new IllegalStateException("Phiếu đã xác nhận, không thể xóa");
        }
        super.delete(id);
    }

    // ── 11/BE-6: đơn giá khi user không xem được giá NCC ─────────

    /**
     * Phiếu PURCHASE mà user KHÔNG xem được giá NCC: bỏ qua {@code unitCost} client gửi — giữ giá Admin đã đặt (khi sửa)
     * hoặc lấy từ bảng giá hiện hành của NCC + mặt hàng (giá/bao nếu dòng có quy cách); chưa có giá → null
     * (phiếu vẫn tạo được, {@code priceMissing = true}). Ngược lại dùng giá client gửi như cũ.
     */
    private BigDecimal resolveUnitCost(InventoryRequest e, Item item, ItemPackaging packaging, BigDecimal sent,
                                       Map<UUID, BigDecimal> keepCosts) {
        if (e.getRequestType() == InventoryRequestType.EXPORT) return null;   // 25: phiếu chưa duyệt không có giá, giá ghi lúc duyệt
        if (e.getRequestType() != InventoryRequestType.PURCHASE) return sent;
        UUID supplierId = e.getSupplier() != null ? e.getSupplier().getId() : null;
        if (scope.canSeePurchasePrice(supplierId)) return sent;
        BigDecimal kept = keepCosts.get(item.getId());
        if (kept != null) return kept;
        if (supplierId == null) return null;
        List<com.bakery.api.pricing.entity.IngredientPrice> prices =
                ingredientPriceRepository.findByItemIdAndSupplierIdOrderByEffectiveDateDesc(item.getId(), supplierId);
        if (prices.isEmpty()) return null;
        BigDecimal perCanonical = prices.get(0).getPrice();
        if (packaging != null && packaging.getQtyPerPack() != null && packaging.getQtyPerPack().signum() > 0) {
            return perCanonical.multiply(packaging.getQtyPerPack()).setScale(4, RoundingMode.HALF_UP);
        }
        return perCanonical;
    }

    /**
     * 11/BE-6: Admin sửa đơn giá SAU khi nhận hàng, trước khi thanh toán. Cập nhật {@code unit_cost} của dòng, của lô
     * tồn sinh từ dòng đó và dòng lịch sử giá tương ứng; ghi nhật ký. Đã thanh toán → 422.
     */
    @Transactional
    public InventoryRequestResponse updateLinePrices(UUID id, List<LinePrice> prices) {
        scope.requireAdmin();
        InventoryRequest e = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(getEntityName(), id));
        if (e.getRequestType() != InventoryRequestType.PURCHASE || e.getApprovalStatus() != ApprovalStatus.APPROVED) {
            throw new IllegalStateException("Chỉ sửa giá sau nhận hàng cho phiếu nhập đã nhận. Phiếu chưa nhận: sửa trực tiếp phiếu");
        }
        if (e.getPaymentStatus() == PaymentStatus.PAID) {
            throw new IllegalStateException("Phiếu đã thanh toán, không sửa được đơn giá");
        }
        if (prices == null || prices.isEmpty()) throw new IllegalArgumentException("Chưa có dòng nào để sửa giá");
        Map<UUID, InventoryRequestLine> byId = new HashMap<>();
        e.getLines().forEach(l -> byId.put(l.getId(), l));
        List<StockMovement> inMovements = stockMovementRepository.findByRefIdAndRefType(e.getId(), REF_TYPE).stream()
                .filter(m -> m.getMovementType() == MovementType.IN).toList();
        Set<UUID> seen = new HashSet<>();
        List<String> changes = new ArrayList<>();
        for (LinePrice p : prices) {
            if (p == null || p.lineId() == null || p.unitCost() == null || p.unitCost().signum() < 0) {
                throw new IllegalArgumentException("Mỗi dòng phải có dòng phiếu và đơn giá không âm");
            }
            InventoryRequestLine line = byId.get(p.lineId());
            if (line == null) throw new IllegalArgumentException("Dòng không thuộc phiếu này: " + p.lineId());
            if (!seen.add(p.lineId())) throw new IllegalArgumentException("Dòng phiếu bị trùng: " + p.lineId());
            BigDecimal old = line.getUnitCost();
            line.setUnitCost(p.unitCost());
            BigDecimal perCanonical = unitCostPerCanonical(line);
            for (StockMovement m : inMovements) {
                StockLot lot = m.getLot();
                if (lot != null && lot.getItem().getId().equals(line.getItem().getId())) {
                    lot.setUnitCost(perCanonical);
                    stockLotRepository.save(lot);
                }
            }
            ingredientPriceRepository.findByLotRefAndItemId(e.getCode(), line.getItem().getId()).forEach(ip -> {
                ip.setPrice(perCanonical.setScale(2, RoundingMode.HALF_UP));
                ingredientPriceRepository.save(ip);
            });
            changes.add(line.getItem().getCode() + ": " + (old != null ? old.stripTrailingZeros().toPlainString() : "—")
                    + " → " + p.unitCost().stripTrailingZeros().toPlainString());
        }
        repository.save(e);
        log(CommandAction.UPDATE, e.getId(), e.getCode(), "Sửa đơn giá sau nhận hàng: " + String.join("; ", changes));
        return toResponse(e);
    }

    /** Một dòng sửa giá: {lineId, unitCost}. */
    public record LinePrice(UUID lineId, BigDecimal unitCost) {}

    // ── 11: quyền theo loại phiếu, kho được gán, người tạo ───────

    private static String screenOf(InventoryRequestType type) {
        return type == InventoryRequestType.ADJUSTMENT ? "STOCK_ADJUSTMENT" : "INVENTORY_REQUESTS";
    }

    private boolean createdByMe(InventoryRequest e) {
        return e.getCreatedBy() != null && e.getCreatedBy().equals(scope.currentUserId());
    }

    /** Phiếu thuộc loại mà user được xem (INVENTORY_REQUESTS:VIEW cho nhập/điều chuyển, STOCK_ADJUSTMENT:VIEW cho điều chỉnh). */
    private boolean viewableType(InventoryRequest e) {
        return scope.can(screenOf(e.getRequestType()), "VIEW")
                && (scope.isAdmin() || scope.hasWarehouse(e.getSourceWarehouse() != null ? e.getSourceWarehouse().getId() : null)
                || scope.hasWarehouse(e.getTargetWarehouse() != null ? e.getTargetWarehouse().getId() : null));
    }

    /** Lý do không được sửa/xóa (null nếu được). Chưa tính trạng thái phiếu. */
    private String editDenied(InventoryRequest e) {
        if (scope.isAdmin()) return null;
        String screen = screenOf(e.getRequestType());
        if (!scope.can(screen, "UPDATE")) return com.bakery.api.auth.service.PermissionLabels.denied(screen, "UPDATE");
        if (!scope.hasWarehouse(e.getSourceWarehouse() != null ? e.getSourceWarehouse().getId() : null)
                && !scope.hasWarehouse(e.getTargetWarehouse() != null ? e.getTargetWarehouse().getId() : null)) {
            return "Bạn không có quyền với kho của phiếu này";
        }
        if (!scope.can(screen, "UPDATE_ANY") && !createdByMe(e)) return "Chỉ sửa được phiếu do bạn tạo";
        return null;
    }

    private static org.springframework.web.server.ResponseStatusException forbidden(String msg) {
        return new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN, msg);
    }

    /**
     * Kiểm quyền thao tác trên một phiếu đã có: quyền theo loại phiếu + kho được gán (kho xuất HOẶC kho nhận) +
     * người tạo (D7). {@code action}: VIEW | UPDATE (sửa/xóa) | APPROVE | REJECT | RECEIVE. Duyệt/từ chối phiếu
     * điều chỉnh kiểm kê: chỉ Admin (D8). Lỗi → 403.
     */
    private void authorize(InventoryRequest e, String action) {
        if (scope.isAdmin()) return;
        String screen = screenOf(e.getRequestType());
        if (e.getRequestType() == InventoryRequestType.ADJUSTMENT
                && ("APPROVE".equals(action) || "REJECT".equals(action))) {
            throw forbidden("Chỉ Admin được duyệt phiếu điều chỉnh");
        }
        if ("UPDATE".equals(action)) {
            String denied = editDenied(e);
            if (denied != null) throw forbidden(denied);
            return;
        }
        if (!scope.can(screen, action)) throw forbidden(com.bakery.api.auth.service.PermissionLabels.denied(screen, action));
        scope.assertAnyWarehouse(e.getSourceWarehouse(), e.getTargetWarehouse());
    }

    /** Tạo phiếu: quyền CREATE theo loại + ít nhất một kho (xuất hoặc nhận) thuộc phạm vi (D2). */
    private void authorizeCreate(InventoryRequest e) {
        if (scope.isAdmin()) return;
        String screen = screenOf(e.getRequestType());
        if (!scope.can(screen, "CREATE")) throw forbidden(com.bakery.api.auth.service.PermissionLabels.denied(screen, "CREATE"));
        scope.assertAnyWarehouse(e.getSourceWarehouse(), e.getTargetWarehouse());
    }

    /** Specification giới hạn danh sách theo kho được gán và loại phiếu được xem. */
    private Specification<InventoryRequest> visibleSpec() {
        if (scope.isAdmin()) return (r, q, cb) -> cb.conjunction();
        boolean stock = scope.can("INVENTORY_REQUESTS", "VIEW");
        boolean adj = scope.can("STOCK_ADJUSTMENT", "VIEW");
        Set<UUID> ids = scope.warehouseIds();
        return (r, q, cb) -> {
            if ((!stock && !adj) || ids.isEmpty()) return cb.disjunction();
            var src = r.join("sourceWarehouse", jakarta.persistence.criteria.JoinType.LEFT);
            var tgt = r.join("targetWarehouse", jakarta.persistence.criteria.JoinType.LEFT);
            var inWarehouse = cb.or(src.get("id").in(ids), tgt.get("id").in(ids));
            var isAdj = cb.equal(r.get("requestType"), InventoryRequestType.ADJUSTMENT);
            var typeOk = stock && adj ? cb.conjunction() : stock ? cb.not(isAdj) : isAdj;
            return cb.and(inWarehouse, typeOk);
        };
    }

    @Override
    public List<InventoryRequestResponse> findAll() {
        return repository.findAll(visibleSpec().and((r, q, cb) -> cb.equal(r.get("status"),
                com.bakery.framework.entity.EntityStatus.ACTIVE))).stream().collect(java.util.stream.Collectors.collectingAndThen(
                java.util.stream.Collectors.toList(), this::toResponses));
    }

    @Override
    public Optional<InventoryRequestResponse> findById(UUID id) {
        return repository.findById(id).map(e -> {
            authorize(e, "VIEW");
            return toResponse(e);
        });
    }

    // ── Điều chỉnh theo số đếm (08/BE-1) ─────────────────────────

    static final String REASON_GROUP = "ADJUSTMENT_REASON";
    static final String REASON_OTHER = "OTHER";

    /** code → tên lý do điều chỉnh (code_value ADJUSTMENT_REASON). */
    private Map<String, String> reasonNames() {
        return codeValueRepository.findByGroupKeyAndStatusOrderBySortOrderAsc(REASON_GROUP, EntityStatus.ACTIVE).stream()
                .collect(Collectors.toMap(CodeValue::getCode, CodeValue::getName, (a, b) -> a, LinkedHashMap::new));
    }

    /** Tồn (tổng qty_remaining) của các item tại một kho; item không có lô → 0. */
    private Map<UUID, BigDecimal> stockAt(UUID warehouseId, Collection<UUID> itemIds) {
        Map<UUID, BigDecimal> out = new HashMap<>();
        if (itemIds.isEmpty()) return out;
        for (Object[] row : stockLotRepository.sumRemainingByItem(warehouseId, itemIds)) {
            out.put((UUID) row[0], (BigDecimal) row[1]);
        }
        return out;
    }

    /**
     * ADJUSTMENT: FE chỉ gửi số đếm; BE tính systemQty (tồn của item tại kho nhận lúc lưu/sửa phiếu)
     * và quantity = countedQty − systemQty. Mọi lỗi nhập liệu → 400, thiếu kho nhận → 422.
     */
    private void buildAdjustmentLines(InventoryRequest e, List<InventoryRequestLineRequest> lines) {
        if (e.getTargetWarehouse() == null) {
            throw new IllegalStateException(warehouseBlocker(InventoryRequestType.ADJUSTMENT, false, false));
        }
        if (lines == null || lines.isEmpty()) {
            throw new IllegalArgumentException("Phiếu điều chỉnh cần ít nhất 1 dòng");
        }
        Warehouse warehouse = e.getTargetWarehouse();
        Map<String, String> reasons = reasonNames();

        List<Item> items = new ArrayList<>();
        Set<UUID> seen = new HashSet<>();
        for (InventoryRequestLineRequest lr : lines) {
            if (lr.itemId() == null) throw new IllegalArgumentException("Mỗi dòng phải chọn mặt hàng");
            Item item = itemRepository.findById(lr.itemId())
                    .orElseThrow(() -> new ResourceNotFoundException("Item", lr.itemId()));
            if (!seen.add(item.getId())) {
                throw new IllegalArgumentException(item.getName() + ": mặt hàng bị trùng trong phiếu");
            }
            items.add(item);
        }
        Set<UUID> productIds = new HashSet<>(itemRepository.findProductIdsIn(seen));
        Map<UUID, BigDecimal> system = stockAt(warehouse.getId(), seen);

        for (int i = 0; i < lines.size(); i++) {
            InventoryRequestLineRequest lr = lines.get(i);
            Item item = items.get(i);
            String name = item.getName();
            if (lr.countedQty() == null || lr.countedQty().signum() < 0) {
                throw new IllegalArgumentException(name + ": số đếm phải có và không âm");
            }
            String reason = lr.reasonCode() == null ? null : lr.reasonCode().trim();
            if (reason == null || reason.isEmpty() || !reasons.containsKey(reason)) {
                throw new IllegalArgumentException(name + ": lý do không hợp lệ (" + String.join(", ", reasons.keySet()) + ")");
            }
            if (REASON_OTHER.equals(reason) && (lr.note() == null || lr.note().isBlank())) {
                throw new IllegalArgumentException(name + ": lý do \"Khác\" bắt buộc ghi chú");
            }
            if (warehouse.isSalesPoint() && productIds.contains(item.getId())) {
                throw new IllegalArgumentException(name + ": không điều chỉnh thành phẩm ở kho " + warehouse.getName() + " "
                        + "(tồn bánh chốt bằng báo cáo ngày)");
            }
            BigDecimal sys = system.getOrDefault(item.getId(), BigDecimal.ZERO);
            BigDecimal diff = lr.countedQty().subtract(sys);
            if (diff.signum() == 0) {
                throw new IllegalArgumentException(name + ": số đếm bằng tồn, không cần điều chỉnh");
            }

            InventoryRequestLine line = new InventoryRequestLine();
            line.setInventoryRequest(e);
            line.setItem(item);
            line.setSystemQty(sys);
            line.setCountedQty(lr.countedQty());
            line.setQuantity(diff);
            line.setReasonCode(reason);
            line.setUnit(item.getUnit());          // đơn vị gốc, không dùng quy cách
            line.setSortOrder(lr.sortOrder() != null ? lr.sortOrder() : i + 1);
            line.setNote(lr.note());
            e.getLines().add(line);
        }
    }

    // ── Xác nhận nhận hàng theo số thực nhận (BE-2) ──────────────

    /**
     * Ghi receivedQty từng dòng rồi chạy luồng approve sẵn có — cùng một transaction.
     * Body phải đủ mọi dòng của phiếu (thiếu/thừa/trùng/null → 400); vi phạm số nhận → 422.
     */
    @Transactional
    public InventoryRequestResponse receive(UUID id, ReceiveRequest req) {
        InventoryRequest e = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(getEntityName(), id));
        requireNotPoolType(e.getRequestType());
        authorize(e, "RECEIVE");
        if (e.getRequestType() != InventoryRequestType.PURCHASE
                || e.getApprovalStatus() != ApprovalStatus.PENDING_APPROVAL) {
            throw new IllegalStateException("Chỉ xác nhận nhận hàng cho phiếu nhập đang chờ duyệt");
        }
        if (req == null || req.lines() == null || req.lines().isEmpty()) {
            throw new IllegalArgumentException("Thiếu danh sách dòng nhận hàng");
        }

        Map<UUID, BigDecimal> received = new HashMap<>();
        for (ReceiveRequest.Line l : req.lines()) {
            if (l.lineId() == null || l.receivedQty() == null) {
                throw new IllegalArgumentException("Mỗi dòng phải có dòng phiếu và số thực nhận");
            }
            if (received.put(l.lineId(), l.receivedQty()) != null) {
                throw new IllegalArgumentException("Dòng phiếu bị trùng: " + l.lineId());
            }
        }
        Set<UUID> lineIds = new LinkedHashSet<>();
        e.getLines().forEach(l -> lineIds.add(l.getId()));
        if (!lineIds.equals(received.keySet())) {
            throw new IllegalArgumentException("Phải gửi đủ và đúng mọi dòng của phiếu (" + lineIds.size() + " dòng)");
        }

        validateReceived(e.getLines(), received);
        e.getLines().forEach(l -> l.setReceivedQty(received.get(l.getId())));
        return approve(id);
    }

    // ── Thanh toán (BE-3) ─────────────────────────────────────────

    /** Tất cả hoặc không gì cả: một phiếu sai là 422, không phiếu nào đổi. */
    @Transactional
    public MarkPaidResponse markPaid(List<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new IllegalArgumentException("Chưa chọn phiếu nào");
        }
        List<UUID> unique = new ArrayList<>(new LinkedHashSet<>(ids));
        Map<UUID, InventoryRequest> byId = new LinkedHashMap<>();
        repository.findAllById(unique).forEach(r -> byId.put(r.getId(), r));

        List<InventoryRequest> found = new ArrayList<>();
        List<String> blocked = new ArrayList<>();
        for (UUID id : unique) {
            InventoryRequest r = byId.get(id);
            if (r == null) throw new ResourceNotFoundException(getEntityName(), id);
            String reason = paymentBlocker(r);
            if (reason != null) blocked.add(r.getCode() + " (" + reason + ")");
            found.add(r);
        }
        if (!blocked.isEmpty()) {
            throw new IllegalStateException("Không thể thanh toán: " + String.join(", ", blocked));
        }

        Instant now = Instant.now();
        String actor = actorResolver.currentUsername();
        BigDecimal total = BigDecimal.ZERO;
        for (InventoryRequest r : found) {
            r.setPaymentStatus(PaymentStatus.PAID);
            r.setPaidAt(now);
            r.setPaidBy(actor);
            repository.save(r);
            total = total.add(totalAmount(r));
            log(CommandAction.MARK_PAID, r.getId(), r.getCode(), null);
        }
        return new MarkPaidResponse(found.size(), total, now);
    }

    /** 15/D1: chỉ hủy thanh toán trong 30 ngày kể từ lúc bấm thanh toán; quá hạn không ai hủy được, kể cả Admin. */
    static final int UNPAY_WINDOW_DAYS = 30;

    /** Còn trong hạn hủy? {@code paidAt} null (dữ liệu cũ thiếu) coi như quá hạn. {@code now} truyền vào để test mốc. */
    static boolean withinUnpayWindow(Instant paidAt, Instant now) {
        return paidAt != null && paidAt.plus(UNPAY_WINDOW_DAYS, java.time.temporal.ChronoUnit.DAYS).isAfter(now);
    }

    /** Cờ {@code canUnpay} của response và điều kiện chặn của {@link #markUnpaid} dùng chung hàm này (15/D2). */
    static boolean canUnpay(boolean admin, PaymentStatus status, Instant paidAt, Instant now) {
        return admin && status == PaymentStatus.PAID && withinUnpayWindow(paidAt, now);
    }

    static String unpayExpiredMessage(String code, Instant paidAt) {
        String date = paidAt == null ? "không rõ ngày"
                : "ngày " + paidAt.atZone(VN).toLocalDate().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        return "Phiếu " + code + " đã thanh toán quá " + UNPAY_WINDOW_DAYS + " ngày (" + date + "), không hủy được";
    }

    @Transactional
    public InventoryRequestResponse markUnpaid(UUID id) {
        InventoryRequest e = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(getEntityName(), id));
        if (e.getPaymentStatus() != PaymentStatus.PAID) {
            throw new IllegalStateException("Phiếu " + e.getCode() + " chưa thanh toán");
        }
        if (!withinUnpayWindow(e.getPaidAt(), Instant.now())) {
            throw new IllegalStateException(unpayExpiredMessage(e.getCode(), e.getPaidAt()));
        }
        e.setPaymentStatus(PaymentStatus.UNPAID);
        e.setPaidAt(null);
        e.setPaidBy(null);
        InventoryRequest saved = repository.save(e);
        log(CommandAction.MARK_UNPAID, id, saved.getCode(), null);
        return toResponse(saved);
    }

    private static BigDecimal totalAmount(InventoryRequest e) {
        boolean approved = e.getApprovalStatus() == ApprovalStatus.APPROVED;
        return e.getLines().stream()
                .map(l -> lineTotal(l, approved))
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    // ── Danh sách + lọc phiếu nhập (BE-4) ────────────────────────

    /** Ngày thực nhận tính theo giờ Việt Nam, không theo múi giờ máy chủ. */
    private static final java.time.ZoneId VN = java.time.ZoneId.of("Asia/Ho_Chi_Minh");

    private static final Set<String> DELIVERY_STATUSES = Set.of("DELIVERED", "PENDING", "OVERDUE");

    /**
     * Thêm 3 param tùy chọn, tách khỏi SpecificationBuilder (cách ItemService xử lý {@code q}):
     * {@code deliveryStatus}, {@code deliveryDateFrom}, {@code deliveryDateTo} (ISO, gồm hai đầu,
     * lọc theo ngày thực nhận nếu phiếu đã nhận, chưa nhận thì theo expectedDeliveryDate; ADJUSTMENT: ngày duyệt). Giá trị sai → 400.
     * Cũng kiểm giá trị enum của requestType / approvalStatus / paymentStatus: SpecificationBuilder
     * nuốt lỗi im lặng, nếu để lọt thì filter bị bỏ qua và trả cả phiếu không muốn thấy.
     */
    @Override
    public Page<InventoryRequestResponse> findAll(MultiValueMap<String, String> params, Pageable pageable) {
        MultiValueMap<String, String> rest = new LinkedMultiValueMap<>(params);
        String deliveryStatus = blankToNull(rest.getFirst("deliveryStatus"));
        LocalDate from = parseDate("deliveryDateFrom", rest.getFirst("deliveryDateFrom"));
        LocalDate to = parseDate("deliveryDateTo", rest.getFirst("deliveryDateTo"));
        UUID itemId = parseUuid("itemId", rest.getFirst("itemId"));
        rest.remove("itemId");
        rest.remove("deliveryStatus");
        rest.remove("deliveryDateFrom");
        rest.remove("deliveryDateTo");

        requireEnum("loại phiếu", rest.getFirst("requestType"), InventoryRequestType.class);
        requireEnum("trạng thái duyệt", rest.getFirst("approvalStatus"), ApprovalStatus.class);
        requireEnum("trạng thái thanh toán", rest.getFirst("paymentStatus"), PaymentStatus.class);
        if (deliveryStatus != null && !DELIVERY_STATUSES.contains(deliveryStatus)) {
            throw new IllegalArgumentException("Trạng thái giao hàng không hợp lệ: " + deliveryStatus);
        }

        Specification<InventoryRequest> spec = SpecificationBuilder.from(rest);
        spec = spec.and(visibleSpec());   // 11/D2
        LocalDate today = LocalDate.now();

        if (deliveryStatus != null) {
            spec = spec.and((root, q, cb) -> {
                var type = cb.equal(root.get("requestType"), InventoryRequestType.PURCHASE);
                var status = root.<ApprovalStatus>get("approvalStatus");
                var expected = root.<LocalDate>get("expectedDeliveryDate");
                var open = status.in(ApprovalStatus.DRAFT, ApprovalStatus.PENDING_APPROVAL);
                return switch (deliveryStatus) {
                    case "DELIVERED" -> cb.and(type, cb.equal(status, ApprovalStatus.APPROVED));
                    case "PENDING" -> cb.and(type, open,
                            cb.or(cb.isNull(expected), cb.greaterThanOrEqualTo(expected, today)));
                    default -> cb.and(type, open, cb.lessThan(expected, today)); // OVERDUE
                };
            });
        }
        // 08/BE-4: phiếu có ít nhất 1 dòng của mặt hàng (EXISTS, dùng idx_inv_req_line_item_id)
        if (itemId != null) {
            spec = spec.and((root, q, cb) -> {
                var sq = q.subquery(Integer.class);
                var l = sq.from(InventoryRequestLine.class);
                sq.select(cb.literal(1)).where(cb.equal(l.get("inventoryRequest"), root),
                        cb.equal(l.get("item").get("id"), itemId));
                return cb.exists(sq);
            });
        }
        // 06/D3: COALESCE(received_date, expected_delivery_date). 08/D7: ADJUSTMENT đã duyệt theo ngày duyệt
        // (giờ VN, so trên approved_at để dùng index), ADJUSTMENT chưa duyệt theo request_date.
        if (from != null) {
            Instant fromAt = from.atStartOfDay(VN).toInstant();
            spec = spec.and((root, q, cb) -> cb.or(
                    cb.and(adjustmentRequest(root, cb), cb.isNotNull(root.get("approvedAt")),
                            cb.greaterThanOrEqualTo(root.<Instant>get("approvedAt"), fromAt)),
                    cb.and(adjustmentRequest(root, cb), cb.isNull(root.get("approvedAt")),
                            cb.greaterThanOrEqualTo(root.<LocalDate>get("requestDate"), from)),
                    cb.and(cb.not(adjustmentRequest(root, cb)), cb.greaterThanOrEqualTo(deliveryDate(root, cb), from))));
        }
        if (to != null) {
            Instant toAt = to.plusDays(1).atStartOfDay(VN).toInstant();
            spec = spec.and((root, q, cb) -> cb.or(
                    cb.and(adjustmentRequest(root, cb), cb.isNotNull(root.get("approvedAt")),
                            cb.lessThan(root.<Instant>get("approvedAt"), toAt)),
                    cb.and(adjustmentRequest(root, cb), cb.isNull(root.get("approvedAt")),
                            cb.lessThanOrEqualTo(root.<LocalDate>get("requestDate"), to)),
                    cb.and(cb.not(adjustmentRequest(root, cb)), cb.lessThanOrEqualTo(deliveryDate(root, cb), to))));
        }

        Page<InventoryRequest> page = repository.findAll(spec, pageable);
        if (!page.isEmpty()) {
            // size=500 → tránh 1 query lines / phiếu
            repository.findWithLinesByIdIn(page.getContent().stream().map(InventoryRequest::getId).toList());
        }
        Map<UUID, Map<UUID, Set<UUID>>> sup = exportSuppliers(page.getContent());
        Map<String, BigDecimal> stock = pickStock(page.getContent());
        return page.map(x -> toResponse(x, sup, stock));
    }

    private static jakarta.persistence.criteria.Predicate adjustmentRequest(
            jakarta.persistence.criteria.Root<InventoryRequest> root, jakarta.persistence.criteria.CriteriaBuilder cb) {
        return cb.equal(root.get("requestType"), InventoryRequestType.ADJUSTMENT);
    }

    private static jakarta.persistence.criteria.Expression<LocalDate> deliveryDate(
            jakarta.persistence.criteria.Root<InventoryRequest> root, jakarta.persistence.criteria.CriteriaBuilder cb) {
        return cb.coalesce(root.<LocalDate>get("receivedDate"), root.<LocalDate>get("expectedDeliveryDate"));
    }

    private static String blankToNull(String v) {
        return v == null || v.isBlank() ? null : v;
    }

    private static UUID parseUuid(String name, String v) {
        if (blankToNull(v) == null) return null;
        try {
            return UUID.fromString(v.trim());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException(name + " không hợp lệ: " + v);
        }
    }

    private static LocalDate parseDate(String name, String v) {
        if (blankToNull(v) == null) return null;
        try {
            return LocalDate.parse(v);
        } catch (DateTimeParseException ex) {
            throw new IllegalArgumentException(name + " phải có dạng yyyy-MM-dd: " + v);
        }
    }

    private static <E extends Enum<E>> void requireEnum(String name, String v, Class<E> type) {
        if (blankToNull(v) == null) return;
        try {
            Enum.valueOf(type, v);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException(name + " không hợp lệ: " + v);
        }
    }

    /**
     * Tất cả phiếu liên quan đến 1 kho (source OR target) theo approvalStatus.
     * Dùng cho tab pending/approved của từng kho trên UI.
     */
    public List<InventoryRequestResponse> findByWarehouse(String warehouseCode, String approvalStatusStr) {
        scope.assertWarehouseCode(warehouseCode);   // 11/D2
        return toResponses(repository.findByWarehouseAndStatus(warehouseCode, approvalStatusStr.toUpperCase()).stream()
                .filter(this::viewableType).toList());
    }

    /** Build lines từ request và gắn vào entity — dùng chung trong toEntity() và applyUpdate(). */
    private void buildAndAddLines(InventoryRequest e, List<InventoryRequestLineRequest> lines) {
        buildAndAddLines(e, lines, Map.of());
    }

    /**
     * @param keepCosts itemId → đơn giá hiện có của phiếu (khi sửa): user không xem được giá NCC thì giữ giá Admin đã đặt
     *                  thay vì ghi đè bằng bảng giá (11/BE-6).
     */
    private void buildAndAddLines(InventoryRequest e, List<InventoryRequestLineRequest> lines, Map<UUID, BigDecimal> keepCosts) {
        if (e.getRequestType() == InventoryRequestType.ADJUSTMENT) {
            buildAdjustmentLines(e, lines);
            return;
        }
        if (lines == null) return;
        for (int i = 0; i < lines.size(); i++) {
            InventoryRequestLineRequest lr = lines.get(i);
            InventoryRequestLine line = new InventoryRequestLine();
            line.setInventoryRequest(e);
            line.setItem(itemRepository.findById(lr.itemId())
                    .orElseThrow(() -> new ResourceNotFoundException("Item", lr.itemId())));
            // Nếu có packaging: tính qty canonical = purchaseQty × qtyPerPack
            ItemPackaging packaging = null;
            if (lr.packagingId() != null) {
                packaging = packagingRepository.findById(lr.packagingId())
                        .orElseThrow(() -> new IllegalArgumentException(
                                "Quy cách đóng gói không tồn tại: " + lr.packagingId()));
                if (!packaging.getItem().getId().equals(lr.itemId())) {
                    throw new IllegalArgumentException("Quy cách đóng gói " + packaging.getCode()
                            + " không thuộc hàng " + line.getItem().getCode());
                }
            }
            line.setPackaging(packaging);
            line.setPurchaseQty(lr.purchaseQty());

            BigDecimal qty = lr.quantity();
            if (packaging != null && lr.purchaseQty() != null) {
                qty = lr.purchaseQty().multiply(packaging.getQtyPerPack());
            }
            line.setQuantity(qty != null ? qty : BigDecimal.ZERO);
            line.setUnit(packaging != null ? line.getItem().getUnit() : lr.unit());
            line.setUnitCost(resolveUnitCost(e, line.getItem(), packaging, lr.unitCost(), keepCosts));
            line.setSortOrder(lr.sortOrder() != null ? lr.sortOrder() : i + 1);
            line.setNote(lr.note());
            e.getLines().add(line);
        }
        if (e.getRequestType() == InventoryRequestType.EXPORT) {
            String dup = duplicateLineBlocker(e.getLines());
            if (dup != null) throw new IllegalStateException(dup);
        }
    }

    /**
     * Làm tròn qty lên nếu item không tách lẻ (splittable=false).
     * Với thiết kế packaging mới, "bội số tối thiểu" được xác định qua packaging default.
     * Nếu không có packaging → trả về qty nguyên gốc (không làm tròn).
     */
    private BigDecimal roundUpToUnitSize(BigDecimal qty, com.bakery.api.master.entity.Item item) {
        if (item.isSplittable()) return qty;
        // Lấy packaging default để biết bội số tối thiểu
        return packagingRepository.findByItemIdAndIsDefaultTrue(item.getId())
                .map(pkg -> {
                    BigDecimal unitSize = pkg.getQtyPerPack();
                    if (unitSize.compareTo(BigDecimal.ZERO) <= 0) return qty;
                    BigDecimal units = qty.divide(unitSize, 0, java.math.RoundingMode.CEILING);
                    return units.multiply(unitSize);
                })
                .orElse(qty);
    }
}
