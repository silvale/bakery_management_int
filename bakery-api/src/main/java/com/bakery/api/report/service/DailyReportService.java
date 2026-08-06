package com.bakery.api.report.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

import com.bakery.api.master.entity.ProductExpiryConfig;
import com.bakery.api.master.entity.ProductMapping;
import com.bakery.api.master.repository.ItemLookupRepository;
import com.bakery.api.master.repository.ProductExpiryConfigRepository;
import com.bakery.api.master.repository.ProductMappingRepository;
import com.bakery.api.master.util.ExCodeDecoder;
import com.bakery.api.pricing.entity.ProductPrice;
import com.bakery.api.pricing.repository.ProductPriceRepository;
import com.bakery.api.inventory.entity.StockLot;
import com.bakery.api.inventory.entity.StockMovement;
import com.bakery.api.inventory.repository.StockLotRepository;
import com.bakery.api.inventory.repository.StockMovementRepository;
import com.bakery.api.master.entity.Warehouse;
import com.bakery.api.master.repository.WarehouseRepository;
import com.bakery.api.production.entity.DeliveryRecord;
import com.bakery.api.production.repository.DeliveryRecordRepository;
import com.bakery.api.production.service.ProductionPlannerService;
import com.bakery.api.report.entity.DailyReport;
import com.bakery.api.report.entity.DailyReportLine;
import com.bakery.api.report.entity.PosDailySale;
import com.bakery.api.report.repository.DailyReportLineRepository;
import com.bakery.api.report.repository.DailyReportRepository;
import com.bakery.api.report.repository.PosDailySaleRepository;
import com.bakery.framework.entity.DailyReportStatus;
import com.bakery.framework.entity.DeliveryStatus;
import com.bakery.framework.entity.MovementType;
import com.bakery.framework.entity.WarehouseType;
import com.bakery.framework.exception.ResourceNotFoundException;
import com.bakery.framework.security.BakeryActorResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Quản lý báo cáo cuối ngày.
 *
 * <p>Flow:
 * 1. getOrCreateDraft(date) → tạo DRAFT nếu chưa có
 * 2. updateRemainingQty(reportId, itemId, qty) → nhân viên nhập tay qty_remaining_actual
 * 3. finalize(reportId) → Admin chốt: tổng hợp toàn bộ số liệu + snapshot giá
 *    → Tự động trigger ProductionPlannerService.generateDraft() cho ngày hôm sau
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DailyReportService {

    private final DailyReportRepository reportRepository;
    private final DailyReportLineRepository lineRepository;
    private final DeliveryRecordRepository deliveryRecordRepository;
    private final PosDailySaleRepository posSaleRepository;
    private final ProductMappingRepository productMappingRepository;
    private final ProductPriceRepository productPriceRepository;
    private final ItemLookupRepository itemRepository;
    private final ProductExpiryConfigRepository expiryConfigRepository;
    private final StockLotRepository stockLotRepository;
    private final StockMovementRepository stockMovementRepository;
    private final WarehouseRepository warehouseRepository;
    private final BakeryActorResolver actorResolver;
    private final ProductionPlannerService productionPlannerService;
    private final com.bakery.framework.repository.CommandRequestRepository commandRequestRepository;
    private final CancelRecordService cancelRecordService;

    // ── Get / Create ─────────────────────────────────────────────

    @Transactional
    public DailyReport getOrCreateDraft(LocalDate date) {
        boolean[] isNew = {false};
        DailyReport report = reportRepository.findByReportDate(date).orElseGet(() -> {
            isNew[0] = true;
            DailyReport r = new DailyReport();
            r.setReportDate(date);
            r.setStatus(DailyReportStatus.DRAFT);
            r.setCreatedBy(actorResolver.currentUserId());
            return reportRepository.save(r);
        });
        // Refresh cancel records mỗi lần init (cập nhật qty_opening/received nếu có delivery mới)
        cancelRecordService.generateForReport(report);
        return report;
    }

    public DailyReport getById(UUID id) {
        return reportRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("DailyReport", id));
    }

    /** Tìm report theo ngày — không tạo mới nếu chưa có. */
    public Optional<DailyReport> findByDate(LocalDate date) {
        return reportRepository.findByReportDate(date);
    }

    public List<DailyReportLine> getLines(UUID reportId) {
        return lineRepository.findByDailyReportIdWithItem(reportId);
    }

    // ── Nhân viên nhập qty_remaining_actual + qty_cancelled ─────

    /**
     * Nhân viên nhập 2 ô cuối ngày: số còn lại + số đã hủy.
     * Có thể gọi bất kỳ lúc nào trước khi FINALIZED.
     * Tự upsert: tạo line nếu chưa có.
     * qty_sold_implied sẽ được tính chính xác trong finalize() (cần opening stock).
     */
    @Transactional
    public DailyReportLine updateRemainingQty(UUID reportId, UUID itemId,
            BigDecimal qtyRemainingActual, BigDecimal qtyCancelled, String note) {
        DailyReport report = getById(reportId);
        assertNotFinalized(report);

        DailyReportLine line = lineRepository
                .findByDailyReportIdAndItemId(reportId, itemId)
                .orElseGet(() -> {
                    DailyReportLine l = new DailyReportLine();
                    l.setDailyReport(report);
                    l.setItem(itemRepository.getReferenceById(itemId));
                    return l;
                });

        line.setQtyRemainingActual(qtyRemainingActual);
        if (qtyCancelled != null) {
            line.setQtyCancelled(qtyCancelled);
        }
        line.setNote(note);
        line.setUpdatedAt(Instant.now());

        return lineRepository.save(line);
    }

    // ── Admin FINALIZE ───────────────────────────────────────────

    /**
     * Admin chốt báo cáo ngày:
     * 1. Tổng hợp qtyProduced + qtyReceived từ DeliveryRecord
     * 2. Tổng hợp qtySoldPos từ pos_daily_sale (qua product_mapping)
     * 3. Tính discrepancy 2 chiều
     * 4. Snapshot giá bán tại thời điểm chốt
     * 5. Status → FINALIZED
     */
    @Transactional
    public DailyReport finalize(UUID reportId) {
        DailyReport report = getById(reportId);
        assertNotFinalized(report);

        LocalDate date = report.getReportDate();

        // ── Bước 1: Tổng hợp DeliveryRecord theo ngày ───────────
        List<DeliveryRecord> deliveries = deliveryRecordRepository
                .findByProductionRequestLine_ProductionRequest_ProductionDate(date);

        // group by item → sum qtyProduced, sum qtyReceived
        Map<UUID, BigDecimal[]> byItem = deliveries.stream()
                .filter(dr -> dr.getProductionRequestLine() != null
                        && dr.getProductionRequestLine().getProduct() != null)
                .collect(Collectors.groupingBy(
                        dr -> dr.getProductionRequestLine().getProduct().getId(),
                        Collectors.collectingAndThen(Collectors.toList(), list -> {
                            BigDecimal produced = list.stream()
                                    .map(DeliveryRecord::getQtyProduced)
                                    .filter(q -> q != null)
                                    .reduce(BigDecimal.ZERO, BigDecimal::add);
                            BigDecimal received = list.stream()
                                    .map(DeliveryRecord::getQtyReceived)
                                    .filter(q -> q != null)
                                    .reduce(BigDecimal.ZERO, BigDecimal::add);
                            return new BigDecimal[]{produced, received};
                        })
                ));

        // ── Bước 2: Tổng hợp POS theo ngày ──────────────────────
        List<PosDailySale> posSales = posSaleRepository.findBySaleDate(date);
        // Map EX_CODE → item_id (qua product_mapping)
        Map<UUID, BigDecimal> posByItem = posSales.stream()
                .map(ps -> {
                    Optional<ProductMapping> mapping = productMappingRepository.findByExCode(ps.getExCode());
                    return mapping.map(m -> Map.entry(m.getItem().getId(), ps.getQtySold())).orElse(null);
                })
                .filter(e -> e != null)
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        BigDecimal::add
                ));

        // ── Bước 3: Load expiry config + tồn SHOP ───────────────────
        Map<UUID, Integer> shelfDaysByItem = expiryConfigRepository.findAll().stream()
                .filter(c -> c.getShelfDays() != null && c.getItem() != null)
                .collect(Collectors.toMap(
                        c -> c.getItem().getId(),
                        ProductExpiryConfig::getShelfDays,
                        (a, b) -> a));

        // Tồn SHOP trước khi NV huỷ = qty_system_cancel
        List<Warehouse> finShopWarehouses = warehouseRepository.findByWarehouseType(WarehouseType.SHOP);
        final UUID finShopWarehouseId = finShopWarehouses.isEmpty() ? null : finShopWarehouses.get(0).getId();
        final Map<UUID, BigDecimal> shopStockByItem = finShopWarehouseId == null
                ? Collections.emptyMap()
                : stockLotRepository.findAll().stream()
                        .filter(l -> finShopWarehouseId.equals(
                                l.getWarehouse() != null ? l.getWarehouse().getId() : null)
                                && l.getQtyRemaining() != null
                                && l.getQtyRemaining().compareTo(BigDecimal.ZERO) > 0
                                && l.getItem() != null)
                        .collect(Collectors.groupingBy(
                                l -> l.getItem().getId(),
                                Collectors.reducing(BigDecimal.ZERO,
                                        l -> l.getQtyRemaining(), BigDecimal::add)));

        // ── Bước 3b: Opening stock từ ngày hôm trước ─────────────
        // qty_remaining_actual của ngày trước = qty_remaining_opening hôm nay
        Map<UUID, BigDecimal> openingByItem = reportRepository.findByReportDate(date.minusDays(1))
                .map(prevReport -> lineRepository.findByDailyReportIdWithItem(prevReport.getId())
                        .stream()
                        .filter(l -> l.getItem() != null && l.getQtyRemainingActual() != null)
                        .collect(Collectors.toMap(
                                l -> l.getItem().getId(),
                                DailyReportLine::getQtyRemainingActual,
                                (a, b) -> a)))
                .orElse(Collections.emptyMap());

        // ── Bước 4: Upsert DailyReportLine cho từng item ─────────
        for (Map.Entry<UUID, BigDecimal[]> entry : byItem.entrySet()) {
            UUID itemId = entry.getKey();
            BigDecimal qtyProduced = entry.getValue()[0];
            BigDecimal qtyReceived = entry.getValue()[1];

            DailyReportLine line = lineRepository
                    .findByDailyReportIdAndItemId(reportId, itemId)
                    .orElseGet(() -> {
                        DailyReportLine l = new DailyReportLine();
                        l.setDailyReport(report);
                        l.setItem(itemRepository.getReferenceById(itemId));
                        return l;
                    });

            line.setQtyProduced(qtyProduced);
            line.setQtyReceived(qtyReceived);

            // Opening stock (tồn đầu ngày từ ngày hôm trước)
            BigDecimal qtyRemainingOpening = openingByItem.getOrDefault(itemId, BigDecimal.ZERO);
            line.setQtyRemainingOpening(qtyRemainingOpening);

            // POS qty
            BigDecimal qtySoldPos = posByItem.getOrDefault(itemId, BigDecimal.ZERO);
            line.setQtySoldPos(qtySoldPos);

            // System cancel: tồn SHOP của items hết HSD hôm nay
            BigDecimal qtySystemCancel = BigDecimal.ZERO;
            Integer shelfDays = shelfDaysByItem.get(itemId);
            if (shelfDays != null) {
                boolean hasExpiringBatch = deliveries.stream()
                        .filter(dr -> dr.getProductionRequestLine() != null
                                && dr.getProductionRequestLine().getProduct() != null
                                && itemId.equals(dr.getProductionRequestLine().getProduct().getId()))
                        .anyMatch(dr -> {
                            LocalDate pd = dr.getProductionRequestLine()
                                    .getProductionRequest().getProductionDate();
                            return !pd.plusDays(shelfDays).isAfter(date);
                        });
                if (hasExpiringBatch) {
                    qtySystemCancel = shopStockByItem.getOrDefault(itemId, BigDecimal.ZERO);
                }
            }
            line.setQtySystemCancel(qtySystemCancel);

            // Còn lại HT = opening + received - sold_pos - system_cancel
            BigDecimal qtySystemRemaining = qtyRemainingOpening.add(qtyReceived)
                    .subtract(qtySoldPos).subtract(qtySystemCancel);
            line.setQtySystemRemaining(qtySystemRemaining);

            // Chênh lệch bếp (qty_produced - qty_received)
            line.setDiscrepancyKitchen(qtyProduced.subtract(qtyReceived));

            // qty_sold_implied = opening + received - remaining (công thức mới)
            // discrepancy_pos = sold_implied - sold_pos
            if (line.getQtyRemainingActual() != null) {
                BigDecimal qtySoldImplied = qtyRemainingOpening.add(qtyReceived)
                        .subtract(line.getQtyRemainingActual());
                line.setQtySoldImplied(qtySoldImplied);
                line.setDiscrepancyPos(qtySoldImplied.subtract(qtySoldPos));
            }

            // discrepancy_remaining = remaining_actual - system_remaining
            if (line.getQtyRemainingActual() != null) {
                line.setDiscrepancyRemaining(line.getQtyRemainingActual().subtract(qtySystemRemaining));
            }

            // discrepancy_cancel = cancelled_nv - system_cancel
            if (line.getQtyCancelled() != null && qtySystemCancel.compareTo(BigDecimal.ZERO) > 0) {
                line.setDiscrepancyCancel(line.getQtyCancelled().subtract(qtySystemCancel));
            }

            // Snapshot giá bán mới nhất
            List<ProductPrice> prices = productPriceRepository.findByItemIdOrderByEffectiveDateDesc(itemId);
            if (!prices.isEmpty()) {
                line.setSellingPrice(prices.get(0).getPrice());
            }

            line.setUpdatedAt(Instant.now());
            lineRepository.save(line);
        }

        // ── Bước 4: FINALIZE ─────────────────────────────────────
        report.setStatus(DailyReportStatus.FINALIZED);
        report.setFinalizedAt(Instant.now());
        report.setFinalizedBy(actorResolver.currentUserId());
        report.setUpdatedAt(Instant.now());
        DailyReport saved = reportRepository.save(report);

        // Log finalize activity
        try {
            commandRequestRepository.save(com.bakery.framework.entity.CommandRequest.builder()
                    .entityName("DailyReport")
                    .entityId(saved.getId())
                    .action(com.bakery.framework.entity.CommandAction.FINALIZE)
                    .actor(actorResolver.currentUserId())
                    .actorName(actorResolver.currentUsername())
                    .entityLabel("Báo cáo ngày " + date)
                    .status(com.bakery.framework.entity.CommandStatus.SUCCESS)
                    .createdAt(Instant.now())
                    .build());
        } catch (Exception ex) {
            log.warn("Lỗi log FINALIZE: {}", ex.getMessage());
        }

        // ── Bước 5: Cập nhật tồn kho SHOP ────────────────────────
        try {
            updateShopStock(saved, date);
        } catch (Exception ex) {
            log.error("Lỗi cập nhật tồn kho SHOP sau finalize {}: {}", saved.getId(), ex.getMessage(), ex);
        }

        // ── Bước 6: Tự động tạo kế hoạch SX ngày mai ────────────
        try {
            productionPlannerService.generateDraft(saved);
            log.info("Đã tạo kế hoạch SX cho ngày {}", saved.getReportDate().plusDays(1));
        } catch (Exception ex) {
            // Không để lỗi planner rollback finalize — log và tiếp tục
            log.error("Lỗi tạo kế hoạch SX sau finalize báo cáo {}: {}", saved.getId(), ex.getMessage(), ex);
        }

        return saved;
    }

    /**
     * Danh sách bánh cần hủy cho ngày {@code reportDate}.
     *
     * <p>Logic:
     * <ol>
     *   <li>Lấy tất cả delivery records trong vòng 14 ngày trước reportDate
     *       (bao gồm cả hôm nay) — giới hạn bởi max shelf_days thực tế.</li>
     *   <li>Với mỗi item, lấy các production_date thực tế từ ProductionRequest.</li>
     *   <li>Lấy shelf_days từ ProductExpiryConfig. Nếu item không có config → bỏ qua.</li>
     *   <li>Nếu production_date + shelf_days ≤ reportDate → hết hạn, cần hủy.</li>
     *   <li>Attach qty_remaining_actual + qty_cancelled từ DailyReportLine hôm nay.</li>
     * </ol>
     *
     * <p>Ví dụ: bánh sản xuất thứ 7 (production_date = Sat), shelf_days = 2
     * → expiry = Mon → xuất hiện trong cancel list thứ 2.
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getCancelList(UUID reportId) {
        DailyReport report = getById(reportId);
        LocalDate reportDate = report.getReportDate();

        // Cửa sổ tìm kiếm: 14 ngày (đủ bao trùm mọi shelf_days thực tế)
        LocalDate lookback = reportDate.minusDays(14);

        // Tải tồn kho SHOP một lần — dùng để tính qtySystemCancel (có ngay sau POS upload)
        List<Warehouse> shopWarehouses = warehouseRepository.findByWarehouseType(WarehouseType.SHOP);
        final UUID shopWarehouseId = shopWarehouses.isEmpty() ? null : shopWarehouses.get(0).getId();
        // itemId → tổng qty_remaining trong SHOP
        Map<UUID, BigDecimal> shopStockByItem = shopWarehouseId == null
                ? Collections.emptyMap()
                : stockLotRepository.findAll().stream()
                        .filter(l -> shopWarehouseId.equals(
                                l.getWarehouse() != null ? l.getWarehouse().getId() : null)
                                && l.getQtyRemaining() != null
                                && l.getQtyRemaining().compareTo(BigDecimal.ZERO) > 0
                                && l.getItem() != null)
                        .collect(Collectors.groupingBy(
                                l -> l.getItem().getId(),
                                Collectors.reducing(BigDecimal.ZERO,
                                        l -> l.getQtyRemaining(), BigDecimal::add)));

        // Tất cả delivery records trong 14 ngày → map itemId → set(productionDate)
        List<DeliveryRecord> deliveries = deliveryRecordRepository
                .findByProductionRequestLine_ProductionRequest_ProductionDateBetween(lookback, reportDate);

        // group: itemId → set production dates (chỉ lấy records có product)
        Map<UUID, Set<LocalDate>> prodDatesByItem = deliveries.stream()
                .filter(dr -> dr.getProductionRequestLine() != null
                        && dr.getProductionRequestLine().getProduct() != null)
                .collect(Collectors.groupingBy(
                        dr -> dr.getProductionRequestLine().getProduct().getId(),
                        Collectors.mapping(
                                dr -> dr.getProductionRequestLine()
                                        .getProductionRequest().getProductionDate(),
                                Collectors.toSet())));

        if (prodDatesByItem.isEmpty()) return List.of();

        // Load expiry configs cho tất cả items một lần
        Map<UUID, Integer> shelfDaysByItem = expiryConfigRepository.findAll().stream()
                .filter(c -> c.getShelfDays() != null && c.getItem() != null)
                .collect(Collectors.toMap(
                        c -> c.getItem().getId(),
                        ProductExpiryConfig::getShelfDays,
                        (a, b) -> a));

        // Load today's report lines một lần
        Map<UUID, DailyReportLine> lineByItem = lineRepository
                .findByDailyReportIdWithItem(reportId).stream()
                .filter(l -> l.getItem() != null)
                .collect(Collectors.toMap(l -> l.getItem().getId(), l -> l, (a, b) -> a));

        List<Map<String, Object>> result = new ArrayList<>();

        for (Map.Entry<UUID, Set<LocalDate>> entry : prodDatesByItem.entrySet()) {
            UUID itemId = entry.getKey();
            Integer shelfDays = shelfDaysByItem.get(itemId);
            if (shelfDays == null) continue; // không có config → không quản lý HSD

            // Kiểm tra xem có production batch nào hết hạn vào/trước reportDate không
            boolean expiring = entry.getValue().stream()
                    .anyMatch(pd -> !pd.plusDays(shelfDays).isAfter(reportDate));
            if (!expiring) continue;

            // Lấy production dates đang hết hạn (để hiển thị thông tin)
            List<LocalDate> expiringDates = entry.getValue().stream()
                    .filter(pd -> !pd.plusDays(shelfDays).isAfter(reportDate))
                    .sorted()
                    .toList();

            // Lấy item info từ line hoặc repository
            DailyReportLine line = lineByItem.get(itemId);

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("itemId", itemId);

            // EX_CODEs hết hạn: lọc theo dayChar khớp ngày SX đang hết hạn
            // Lấy groupCode từ itemGroup của item (prefix EX_CODE)
            String groupCode = null;
            if (line != null && line.getItem() != null
                    && line.getItem().getItemGroup() != null) {
                groupCode = line.getItem().getItemGroup().getCode();
            }
            if (groupCode == null) {
                // fallback: lấy từ delivery record
                groupCode = deliveries.stream()
                        .filter(dr -> dr.getProductionRequestLine() != null
                                && dr.getProductionRequestLine().getProduct() != null
                                && itemId.equals(dr.getProductionRequestLine().getProduct().getId()))
                        .findFirst()
                        .map(dr -> dr.getProductionRequestLine().getProduct().getItemGroup())
                        .filter(g -> g != null)
                        .map(g -> g.getCode())
                        .orElse(null);
            }
            final String finalGroupCode = groupCode;
            List<String> allMappings = productMappingRepository.findByItemId(itemId).stream()
                    .map(ProductMapping::getExCode)
                    .sorted()
                    .toList();
            row.put("allExCodes", allMappings);

            List<String> expiringExCodes = allMappings.stream()
                    .filter(ec -> {
                        if (finalGroupCode == null) return false;
                        return expiringDates.stream().anyMatch(pd ->
                                ExCodeDecoder.matchesProductionDate(ec, finalGroupCode, pd));
                    })
                    .toList();
            row.put("expiringExCodes", expiringExCodes);

            // Item name/code từ line nếu có, fallback sang getReferenceById
            if (line != null && line.getItem() != null) {
                row.put("itemCode", line.getItem().getCode() != null ? line.getItem().getCode() : "");
                row.put("itemName", line.getItem().getName() != null ? line.getItem().getName() : "");
            } else {
                // Lấy từ delivery records (product đã load từ query)
                deliveries.stream()
                        .filter(dr -> dr.getProductionRequestLine() != null
                                && dr.getProductionRequestLine().getProduct() != null
                                && itemId.equals(dr.getProductionRequestLine().getProduct().getId()))
                        .findFirst()
                        .ifPresent(dr -> {
                            var p = dr.getProductionRequestLine().getProduct();
                            row.put("itemCode", p.getCode() != null ? p.getCode() : "");
                            row.put("itemName", p.getName() != null ? p.getName() : "");
                        });
            }

            row.put("shelfDays", shelfDays);
            row.put("expiringProductionDates", expiringDates);

            // qtySystemCancel = tồn kho SHOP hiện tại cho item này
            // Có ngay sau POS upload, KHÔNG cần NV nhập tồn cuối ngày trước
            BigDecimal qtySystemCancel = shopStockByItem.getOrDefault(itemId, BigDecimal.ZERO);
            row.put("qtySystemCancel", qtySystemCancel.compareTo(BigDecimal.ZERO) > 0 ? qtySystemCancel : null);

            if (line != null) {
                BigDecimal qtyRemainingActual = line.getQtyRemainingActual();
                BigDecimal qtyCancelled       = line.getQtyCancelled();
                row.put("qtyReceived",      line.getQtyReceived());
                row.put("qtyCancelled",     qtyCancelled);
                // qtyRemainingActual: NV nhập SAU khi đã huỷ — dùng để đối chiếu
                row.put("qtyRemainingActual", qtyRemainingActual);
                // discrepancy: NV huỷ (qtyCancelled) so với HT dự kiến (qtySystemCancel)
                BigDecimal discrepancy = (qtyCancelled != null && qtySystemCancel.compareTo(BigDecimal.ZERO) > 0)
                        ? qtyCancelled.subtract(qtySystemCancel) : null;
                row.put("discrepancyCancelQty", discrepancy);
            } else {
                row.put("qtyReceived",          null);
                row.put("qtyCancelled",         null);
                row.put("qtyRemainingActual",   null);
                row.put("discrepancyCancelQty", null);
            }
            result.add(row);
        }

        // Sort: shelf_days tăng dần (bánh tươi trước), rồi theo tên
        result.sort(Comparator
                .<Map<String, Object>, Integer>comparing(r -> (Integer) r.get("shelfDays"))
                .thenComparing(r -> String.valueOf(r.getOrDefault("itemName", ""))));

        return result;
    }

    // ── Private ──────────────────────────────────────────────────

    /**
     * Sau khi finalize, cập nhật tồn kho SHOP = qty_remaining_actual của từng item.
     *
     * <p>Logic:
     * - Tìm kho SHOP (WarehouseType.SHOP). Nếu chưa có → bỏ qua.
     * - Với mỗi DailyReportLine có qty_remaining_actual:
     *   1. Zero out toàn bộ lot cũ của item tại SHOP (ghi StockMovement OUT)
     *   2. Tạo lot mới với qty = qty_remaining_actual (ghi StockMovement IN)
     *
     * <p>Idempotent: gọi lại sẽ reset về trạng thái mới nhất.
     */
    private void updateShopStock(DailyReport report, LocalDate reportDate) {
        List<Warehouse> shopWarehouses = warehouseRepository.findByWarehouseType(WarehouseType.SHOP);
        if (shopWarehouses.isEmpty()) {
            log.warn("Không tìm thấy kho SHOP, bỏ qua cập nhật tồn kho cửa hàng.");
            return;
        }
        Warehouse shopWarehouse = shopWarehouses.get(0);

        List<DailyReportLine> lines = lineRepository.findByDailyReportIdWithItem(report.getId());
        for (DailyReportLine line : lines) {
            if (line.getQtyRemainingActual() == null || line.getItem() == null) continue;

            UUID itemId = line.getItem().getId();

            // Bước 1: Zero out các lot cũ còn hàng tại SHOP
            List<StockLot> oldLots = stockLotRepository
                    .findByItemIdAndWarehouseId(itemId, shopWarehouse.getId())
                    .stream()
                    .filter(l -> l.getQtyRemaining().compareTo(BigDecimal.ZERO) > 0)
                    .toList();
            for (StockLot old : oldLots) {
                StockMovement out = new StockMovement();
                out.setLot(old);
                out.setMovementType(MovementType.OUT);
                out.setQty(old.getQtyRemaining());
                out.setRefType("DAILY_REPORT_FINALIZE");
                out.setRefId(report.getId());
                out.setNote("Chốt cuối ngày " + reportDate + " — reset tồn kho SHOP");
                stockMovementRepository.save(out);
                old.setQtyRemaining(BigDecimal.ZERO);
                stockLotRepository.save(old);
            }

            BigDecimal qtyRemaining = line.getQtyRemainingActual();
            if (qtyRemaining.compareTo(BigDecimal.ZERO) <= 0) continue;

            // Bước 2: Tạo lot mới với tồn cuối ngày
            BigDecimal unitCost = line.getUnitCost() != null ? line.getUnitCost() : BigDecimal.ZERO;
            StockLot newLot = new StockLot();
            newLot.setItem(line.getItem());
            newLot.setWarehouse(shopWarehouse);
            newLot.setQtyInitial(qtyRemaining);
            newLot.setQtyRemaining(qtyRemaining);
            newLot.setUnitCost(unitCost);
            newLot.setReceivedDate(reportDate);
            StockLot savedLot = stockLotRepository.save(newLot);

            StockMovement in = new StockMovement();
            in.setLot(savedLot);
            in.setMovementType(MovementType.IN);
            in.setQty(qtyRemaining);
            in.setRefType("DAILY_REPORT_FINALIZE");
            in.setRefId(report.getId());
            in.setNote("Tồn kho SHOP cuối ngày " + reportDate);
            stockMovementRepository.save(in);

            log.debug("Cập nhật SHOP stock: item={}, qty={}", itemId, qtyRemaining);
        }
        log.info("Đã cập nhật tồn kho SHOP cho {} items sau finalize ngày {}", lines.size(), reportDate);
    }

    private void assertNotFinalized(DailyReport report) {
        if (report.getStatus() == DailyReportStatus.FINALIZED) {
            throw new IllegalStateException(
                    "Báo cáo ngày " + report.getReportDate() + " đã FINALIZED, không thể chỉnh sửa.");
        }
    }
}
