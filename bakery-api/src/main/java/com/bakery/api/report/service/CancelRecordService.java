package com.bakery.api.report.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import com.bakery.api.inventory.entity.StockLot;
import com.bakery.api.inventory.repository.StockLotRepository;
import com.bakery.api.master.entity.Item;
import com.bakery.api.master.entity.ProductExpiryConfig;
import com.bakery.api.master.entity.ProductMapping;
import com.bakery.api.master.entity.Warehouse;
import com.bakery.api.master.repository.ItemLookupRepository;
import com.bakery.api.master.repository.ProductExpiryConfigRepository;
import com.bakery.api.master.repository.ProductMappingRepository;
import com.bakery.api.master.repository.WarehouseRepository;
import com.bakery.api.master.util.ExCodeDecoder;
import com.bakery.api.production.entity.DeliveryRecord;
import com.bakery.api.production.repository.DeliveryRecordRepository;
import com.bakery.api.report.entity.CancelRecord;
import com.bakery.api.report.entity.DailyReport;
import com.bakery.api.report.repository.CancelRecordRepository;
import com.bakery.api.report.repository.PosDailySaleRepository;
import com.bakery.framework.exception.ResourceNotFoundException;
import com.bakery.framework.security.BakeryActorResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Quản lý danh sách hủy bánh theo EX_CODE.
 *
 * <p>Flow:
 * 1. generateForReport() — gọi khi init DailyReport: tạo CancelRecord per SHOP EX_CODE,
 *    snapshot qty_opening, qty_received, đánh dấu EXPIRED nếu hết HSD.
 * 2. getCancelList()     — NV xem danh sách hủy + còn lại tính toán.
 * 3. confirm()           — NV tick / nhập qty thực tế.
 * 4. addExtra()          — NV thêm hủy vượt (DAMAGED / OTHER).
 * 5. remove()            — NV xóa hủy vượt (chỉ DAMAGED/OTHER).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CancelRecordService {

    private static final String SHOP_CODE = "SHOP";

    private final CancelRecordRepository cancelRecordRepository;
    private final StockLotRepository stockLotRepository;
    private final WarehouseRepository warehouseRepository;
    private final DeliveryRecordRepository deliveryRecordRepository;
    private final PosDailySaleRepository posSaleRepository;
    private final ProductMappingRepository productMappingRepository;
    private final ProductExpiryConfigRepository expiryConfigRepository;
    private final ItemLookupRepository itemRepository;
    private final BakeryActorResolver actorResolver;

    // ── 1. Generate khi init report ──────────────────────────────────────────

    @Transactional
    public void generateForReport(DailyReport report) {
        LocalDate reportDate = report.getReportDate();
        UUID reportId = report.getId();

        Warehouse shop = warehouseRepository.findByCode(SHOP_CODE).orElse(null);
        if (shop == null) {
            log.warn("generateForReport: không tìm thấy kho SHOP");
            return;
        }

        // Load tất cả SHOP lots còn hàng, group by ex_code
        List<StockLot> shopLots = stockLotRepository.findByWarehouseCode(SHOP_CODE)
                .stream()
                .filter(l -> l.getExCode() != null && l.getQtyRemaining().compareTo(BigDecimal.ZERO) > 0)
                .toList();

        // group by exCode → sum qty_remaining (= qty_opening trước khi nhận hôm nay)
        Map<String, BigDecimal> openingByExCode = shopLots.stream()
                .collect(Collectors.groupingBy(StockLot::getExCode,
                        Collectors.reducing(BigDecimal.ZERO, StockLot::getQtyRemaining, BigDecimal::add)));

        // map exCode → item
        Map<String, Item> itemByExCode = shopLots.stream()
                .filter(l -> l.getItem() != null)
                .collect(Collectors.toMap(StockLot::getExCode, StockLot::getItem, (a, b) -> a));

        // Load delivery records hôm nay confirmed → qty_received per ex_code
        List<DeliveryRecord> todayDeliveries = deliveryRecordRepository
                .findByProductionRequestLine_ProductionRequest_ProductionDate(reportDate);
        Map<String, BigDecimal> receivedByExCode = todayDeliveries.stream()
                .filter(dr -> dr.getExCode() != null && dr.getQtyReceived() != null)
                .collect(Collectors.groupingBy(
                        DeliveryRecord::getExCode,
                        Collectors.reducing(BigDecimal.ZERO, DeliveryRecord::getQtyReceived, BigDecimal::add)));

        // Also add EX_CODEs from today's deliveries that may not be in SHOP lots yet (just confirmed)
        for (DeliveryRecord dr : todayDeliveries) {
            if (dr.getExCode() != null && !openingByExCode.containsKey(dr.getExCode())) {
                openingByExCode.put(dr.getExCode(), BigDecimal.ZERO);
                if (dr.getProductionRequestLine() != null && dr.getProductionRequestLine().getProduct() != null) {
                    itemByExCode.put(dr.getExCode(), dr.getProductionRequestLine().getProduct());
                }
            }
        }

        // Load expiry configs
        Map<UUID, Integer> shelfDaysByItem = expiryConfigRepository.findAll().stream()
                .filter(c -> c.getShelfDays() != null && c.getItem() != null)
                .collect(Collectors.toMap(
                        c -> c.getItem().getId(),
                        ProductExpiryConfig::getShelfDays,
                        (a, b) -> a));

        // Load product_mapping để biết production_date của từng EX_CODE (reverse decode)
        // exCode → groupCode từ item.itemGroup.code
        for (Map.Entry<String, BigDecimal> entry : openingByExCode.entrySet()) {
            String exCode = entry.getKey();
            BigDecimal qtyOpening = entry.getValue();
            BigDecimal qtyReceived = receivedByExCode.getOrDefault(exCode, BigDecimal.ZERO);
            Item item = itemByExCode.get(exCode);

            // Tính production date từ EX_CODE + groupCode
            LocalDate productionDate = resolveProductionDate(exCode, item, reportDate);

            // Tính qty_cancel_expected: nếu hết HSD
            BigDecimal qtyCancelExpected = BigDecimal.ZERO;
            String cancelType = "EXPIRED";
            if (item != null && productionDate != null) {
                Integer shelfDays = shelfDaysByItem.get(item.getId());
                if (shelfDays != null && !productionDate.plusDays(shelfDays).isAfter(reportDate)) {
                    // Hết HSD → expected cancel = tồn đầu + nhận hôm nay (toàn bộ lô này cần hủy)
                    qtyCancelExpected = qtyOpening.add(qtyReceived);
                }
            }

            // Upsert: chỉ tạo nếu chưa có record EXPIRED cho exCode này
            CancelRecord existing = cancelRecordRepository
                    .findExpiredByReportAndExCode(reportId, exCode)
                    .orElse(null);

            if (existing == null) {
                CancelRecord cr = new CancelRecord();
                cr.setDailyReport(report);
                cr.setExCode(exCode);
                cr.setItem(item);
                cr.setProductionDate(productionDate);
                cr.setCancelType(cancelType);
                cr.setQtyOpening(qtyOpening);
                cr.setQtyReceived(qtyReceived);
                cr.setQtyCancelExpected(qtyCancelExpected);
                cr.setCreatedBy(actorResolver.currentUserId());
                cancelRecordRepository.save(cr);
                log.info("generateForReport: tạo CancelRecord exCode={} expected={}", exCode, qtyCancelExpected);
            } else {
                // Cập nhật qty_opening + qty_received (có thể delivery mới được confirm)
                existing.setQtyOpening(qtyOpening);
                existing.setQtyReceived(qtyReceived);
                existing.setQtyCancelExpected(qtyCancelExpected);
                existing.setUpdatedAt(Instant.now());
                cancelRecordRepository.save(existing);
            }
        }
    }

    // ── 2. Get cancel list cho NV ─────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getCancelList(UUID reportId, LocalDate reportDate) {
        List<CancelRecord> records = cancelRecordRepository.findByDailyReportIdOrderByExCodeAsc(reportId);

        // Load POS sales per ex_code cho ngày này
        Map<String, BigDecimal> soldByExCode = posSaleRepository
                .findBySaleDate(reportDate).stream()
                .collect(Collectors.groupingBy(
                        s -> s.getExCode(),
                        Collectors.reducing(BigDecimal.ZERO, s -> s.getQtySold(), BigDecimal::add)));

        List<Map<String, Object>> result = new ArrayList<>();
        for (CancelRecord cr : records) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", cr.getId());
            row.put("exCode", cr.getExCode());
            Item item = cr.getItem();
            row.put("itemId",        item != null ? item.getId() : null);
            row.put("itemCode",      item != null ? item.getCode() : null);
            row.put("itemName",      item != null ? item.getName() : null);
            row.put("itemGroupCode", item != null && item.getItemGroup() != null ? item.getItemGroup().getCode() : null);
            row.put("itemGroupName", item != null && item.getItemGroup() != null ? item.getItemGroup().getName() : null);
            row.put("productionDate", cr.getProductionDate());
            row.put("cancelType", cr.getCancelType());
            row.put("qtyOpening", cr.getQtyOpening());
            row.put("qtyReceived", cr.getQtyReceived());
            row.put("qtyCancelExpected", cr.getQtyCancelExpected());
            row.put("qtyCancelActual", cr.getQtyCancelActual());
            row.put("confirmed", cr.isConfirmed());
            row.put("note", cr.getNote());

            // Tính còn lại = (opening + received) - (cancel_actual ?? cancel_expected) - bán POS
            BigDecimal qtyCancel = cr.getQtyCancelActual() != null
                    ? cr.getQtyCancelActual()
                    : cr.getQtyCancelExpected();
            BigDecimal qtySoldPos = soldByExCode.getOrDefault(cr.getExCode(), BigDecimal.ZERO);
            BigDecimal qtyRemaining = cr.getQtyOpening()
                    .add(cr.getQtyReceived())
                    .subtract(qtyCancel)
                    .subtract(qtySoldPos);
            row.put("qtySoldPos", qtySoldPos);
            row.put("qtyRemaining", qtyRemaining.max(BigDecimal.ZERO));

            result.add(row);
        }
        return result;
    }

    // ── 3. NV xác nhận hủy ───────────────────────────────────────────────────

    @Transactional
    public CancelRecord confirm(UUID id, BigDecimal qtyCancelActual, String note) {
        CancelRecord cr = cancelRecordRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("CancelRecord", id));
        cr.setQtyCancelActual(qtyCancelActual != null ? qtyCancelActual : cr.getQtyCancelExpected());
        cr.setConfirmed(true);
        if (note != null) cr.setNote(note);
        cr.setUpdatedAt(Instant.now());
        cr.setUpdatedBy(actorResolver.currentUserId());
        return cancelRecordRepository.save(cr);
    }

    // ── 4. NV thêm hủy vượt (DAMAGED / OTHER) ────────────────────────────────

    @Transactional
    public CancelRecord addExtra(DailyReport report, String exCode,
                                  BigDecimal qtyCancelActual, String cancelType, String note) {
        // Lấy thông tin item từ product_mapping
        Item item = productMappingRepository.findByExCode(exCode)
                .map(pm -> pm.getItem())
                .orElse(null);

        // Tính qty_opening + qty_received tương tự generateForReport
        BigDecimal qtyOpening = getShopQtyForExCode(exCode);
        BigDecimal qtyReceived = deliveryRecordRepository
                .findByProductionRequestLine_ProductionRequest_ProductionDate(report.getReportDate())
                .stream()
                .filter(dr -> exCode.equals(dr.getExCode()) && dr.getQtyReceived() != null)
                .map(DeliveryRecord::getQtyReceived)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        LocalDate productionDate = resolveProductionDate(exCode, item, report.getReportDate());

        CancelRecord cr = new CancelRecord();
        cr.setDailyReport(report);
        cr.setExCode(exCode);
        cr.setItem(item);
        cr.setProductionDate(productionDate);
        cr.setCancelType(cancelType != null ? cancelType : "DAMAGED");
        cr.setQtyOpening(qtyOpening);
        cr.setQtyReceived(qtyReceived);
        cr.setQtyCancelExpected(BigDecimal.ZERO);
        cr.setQtyCancelActual(qtyCancelActual);
        cr.setConfirmed(true);
        cr.setNote(note);
        cr.setCreatedBy(actorResolver.currentUserId());
        return cancelRecordRepository.save(cr);
    }

    // ── 5. Xóa hủy vượt ──────────────────────────────────────────────────────

    @Transactional
    public void remove(UUID id) {
        CancelRecord cr = cancelRecordRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("CancelRecord", id));
        if ("EXPIRED".equals(cr.getCancelType())) {
            throw new IllegalStateException("Không thể xóa record hủy do hết HSD");
        }
        cancelRecordRepository.delete(cr);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private BigDecimal getShopQtyForExCode(String exCode) {
        return stockLotRepository.findByWarehouseCode(SHOP_CODE).stream()
                .filter(l -> exCode.equals(l.getExCode())
                        && l.getQtyRemaining().compareTo(BigDecimal.ZERO) > 0)
                .map(StockLot::getQtyRemaining)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Giải mã production date từ EX_CODE + groupCode.
     * Tìm ngày gần nhất trong 14 ngày gần đây khớp với dayChar.
     */
    private LocalDate resolveProductionDate(String exCode, Item item, LocalDate reportDate) {
        if (exCode == null || item == null || item.getItemGroup() == null) return null;
        String groupCode = item.getItemGroup().getCode();
        if (groupCode == null) return null;

        Character dayChar = ExCodeDecoder.extractDayChar(exCode, groupCode);
        if (dayChar == null) return null;
        if (dayChar == '0') return reportDate; // sản xuất mỗi ngày

        // Tìm ngày SX gần nhất trong 14 ngày
        for (int i = 0; i <= 14; i++) {
            LocalDate candidate = reportDate.minusDays(i);
            if (ExCodeDecoder.matchesProductionDate(exCode, groupCode, candidate)) {
                return candidate;
            }
        }
        return null;
    }
}
