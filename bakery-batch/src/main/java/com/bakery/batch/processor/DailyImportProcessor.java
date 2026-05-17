package com.bakery.batch.processor;

import com.bakery.batch.dto.*;
import com.bakery.batch.util.ErrorRowCollector;
import com.bakery.common.entity.*;
import com.bakery.common.entity.enums.ReconcileStatus;
import com.bakery.common.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Xử lý toàn bộ nghiệp vụ import 4 file vào DB.
 *
 * Thứ tự xử lý trong 1 lần chạy:
 *   1. processProductionRequest  → ProductionOrder + lines (BanhRaNgay)
 *   2. processProductionActual   → cập nhật qty_actual (XuatRa)
 *   3. processStockTransfer      → StockTransfer từ XuatRa
 *   4. processDailyInventory     → DailyInventory (BaoCaoNgay)
 *   5. processPosExport          → PosTransaction (BigProductBySaleByCat)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DailyImportProcessor {

    private final BranchRepository              branchRepository;
    private final ProductRepository             productRepository;
    private final ProductionOrderRepository     productionOrderRepository;
    private final ProductionOrderLineRepository productionOrderLineRepository;
    private final StockTransferRepository       stockTransferRepository;
    private final DailyInventoryRepository      dailyInventoryRepository;
    private final PosTransactionRepository      posTransactionRepository;

    // -------------------------------------------------------
    //  1. BanhRaNgay → ProductionOrder + Lines
    // -------------------------------------------------------

    @Transactional
    public int processProductionRequest(
            List<ProductionRequestRow> rows,
            Branch kitchenBranch,
            ErrorRowCollector collector) {

        if (rows.isEmpty()) return 0;

        LocalDate orderDate = rows.get(0).getOrderDate();
        log.info("processProductionRequest | Ngày: {} | {} dòng", orderDate, rows.size());

        // Idempotency: xóa order cũ nếu đã tồn tại (overwrite)
        productionOrderRepository
            .findByBranchIdAndOrderDate(kitchenBranch.getId(), orderDate)
            .ifPresent(existing -> {
                log.info("Overwrite ProductionOrder ngày {} (re-run)", orderDate);
                productionOrderRepository.delete(existing);
            });

        // Load tất cả product 1 lần (tránh N+1)
        Set<String> codes = rows.stream()
            .map(ProductionRequestRow::getProductCode)
            .collect(Collectors.toSet());
        Map<String, Product> productMap = productRepository.findAllByCodeIn(codes)
            .stream()
            .collect(Collectors.toMap(Product::getCode, p -> p));

        // Tạo ProductionOrder
        ProductionOrder order = ProductionOrder.builder()
            .branch(kitchenBranch)
            .orderDate(orderDate)
            .status(ReconcileStatus.PENDING)
            .build();

        List<ProductionOrderLine> lines = new ArrayList<>();
        int saved = 0;

        for (ProductionRequestRow row : rows) {
            Product product = productMap.get(row.getProductCode());
            if (product == null) {
                log.warn("Không tìm thấy sản phẩm: {}", row.getProductCode());
                collector.addError(row.getRowIndex(), "Ma_SP",
                    "Không tìm thấy SP: " + row.getProductCode());
                continue;
            }

            lines.add(ProductionOrderLine.builder()
                .order(order)
                .product(product)
                .qtyRequested(row.getQtyRequested())
                .unit(row.getUnit())
                .build());
            saved++;
        }

        order.setLines(lines);
        productionOrderRepository.save(order);

        log.info("Đã lưu ProductionOrder {} lines", saved);
        return saved;
    }

    // -------------------------------------------------------
    //  2. XuatRa → cập nhật qty_actual + tạo StockTransfer
    // -------------------------------------------------------

    @Transactional
    public int processProductionActual(
            List<ProductionActualRow> rows,
            Branch kitchenBranch,
            Branch shopBranch,
            ErrorRowCollector collector) {

        if (rows.isEmpty()) return 0;

        LocalDate orderDate = rows.get(0).getOrderDate();
        log.info("processProductionActual | Ngày: {} | {} dòng", orderDate, rows.size());

        // Load ProductionOrder đã tạo ở bước 1
        Optional<ProductionOrder> orderOpt = productionOrderRepository
            .findWithLinesByBranchIdAndOrderDate(kitchenBranch.getId(), orderDate);

        if (orderOpt.isEmpty()) {
            log.warn("Không tìm thấy ProductionOrder ngày {} — tạo mới", orderDate);
        }

        // Load product map
        Set<String> codes = rows.stream()
            .map(ProductionActualRow::getProductCode)
            .collect(Collectors.toSet());
        Map<String, Product> productMap = productRepository.findAllByCodeIn(codes)
            .stream()
            .collect(Collectors.toMap(Product::getCode, p -> p));

        int updated = 0;

        for (ProductionActualRow row : rows) {
            Product product = productMap.get(row.getProductCode());
            if (product == null) {
                collector.addError(row.getRowIndex(), "Ma_SP",
                    "Không tìm thấy SP: " + row.getProductCode());
                continue;
            }

            // 2a. Cập nhật qty_actual trong ProductionOrderLine
            if (orderOpt.isPresent() && row.getQtyActual() != null) {
                productionOrderLineRepository.updateQtyActual(
                    orderOpt.get().getId(),
                    product.getId(),
                    row.getQtyActual()
                );
            }

            // 2b. Tạo StockTransfer (bếp → cửa hàng)
            BigDecimal qtySent = row.getQtyActual() != null
                ? row.getQtyActual()
                : BigDecimal.ZERO;

            if (qtySent.compareTo(BigDecimal.ZERO) > 0) {
                // Overwrite nếu đã tồn tại
                stockTransferRepository
                    .findByFromBranchIdAndToBranchIdAndProductIdAndTransferDate(
                        kitchenBranch.getId(), shopBranch.getId(), product.getId(), orderDate)
                    .ifPresent(stockTransferRepository::delete);

                StockTransfer transfer = StockTransfer.builder()
                    .fromBranch(kitchenBranch)
                    .toBranch(shopBranch)
                    .product(product)
                    .transferDate(orderDate)
                    .qtySent(qtySent)
                    .unit(row.getUnit())
                    .status(ReconcileStatus.PENDING)
                    .build();

                stockTransferRepository.save(transfer);
            }
            updated++;
        }

        log.info("processProductionActual: cập nhật {} dòng", updated);
        return updated;
    }

    // -------------------------------------------------------
    //  3. BaoCaoNgay → DailyInventory + cập nhật StockTransfer.qty_received
    // -------------------------------------------------------

    @Transactional
    public int processDailyInventory(
            List<DailyInventoryRow> rows,
            Branch shopBranch,
            Branch kitchenBranch,
            ErrorRowCollector collector) {

        if (rows.isEmpty()) return 0;

        LocalDate inventoryDate = rows.get(0).getInventoryDate();
        log.info("processDailyInventory | Ngày: {} | {} dòng", inventoryDate, rows.size());

        Set<String> codes = rows.stream()
            .map(DailyInventoryRow::getProductCode)
            .collect(Collectors.toSet());
        Map<String, Product> productMap = productRepository.findAllByCodeIn(codes)
            .stream()
            .collect(Collectors.toMap(Product::getCode, p -> p));

        int saved = 0;

        for (DailyInventoryRow row : rows) {
            Product product = productMap.get(row.getProductCode());
            if (product == null) {
                collector.addError(row.getRowIndex(), "Ma_SP",
                    "Không tìm thấy SP: " + row.getProductCode());
                continue;
            }

            // Overwrite nếu đã tồn tại
            dailyInventoryRepository
                .findByBranchIdAndProductIdAndInventoryDate(
                    shopBranch.getId(), product.getId(), inventoryDate)
                .ifPresent(dailyInventoryRepository::delete);

            DailyInventory inventory = DailyInventory.builder()
                .branch(shopBranch)
                .product(product)
                .inventoryDate(inventoryDate)
                .qtyOpening(row.getQtyOpening())
                .qtyReceived(row.getQtyReceived())
                .qtyCancelled(row.getQtyCancelled())
                .qtyClosing(row.getQtyClosing())
                .qtySoldReported(row.getQtySoldReported())
                .build();

            dailyInventoryRepository.save(inventory);

            // Cập nhật qty_received vào StockTransfer (tầng 2)
            stockTransferRepository
                .findByFromBranchIdAndToBranchIdAndProductIdAndTransferDate(
                    kitchenBranch.getId(), shopBranch.getId(), product.getId(), inventoryDate)
                .ifPresent(transfer -> {
                    transfer.setQtyReceived(row.getQtyReceived());

                    // Cập nhật status tầng 2
                    BigDecimal diff = transfer.getQtySent().subtract(row.getQtyReceived()).abs();
                    BigDecimal threshold = transfer.getQtySent()
                        .multiply(product.getToleranceRate());

                    transfer.setStatus(
                        diff.compareTo(threshold) <= 0
                            ? ReconcileStatus.OK
                            : ReconcileStatus.DISCREPANCY
                    );
                    stockTransferRepository.save(transfer);
                });

            saved++;
        }

        log.info("processDailyInventory: lưu {} DailyInventory", saved);
        return saved;
    }

    // -------------------------------------------------------
    //  4. POS Export → PosTransaction (aggregate trước)
    // -------------------------------------------------------

    @Transactional
    public int processPosExport(
            List<PosTransactionRow> rawRows,
            Branch shopBranch,
            ErrorRowCollector collector) {

        if (rawRows.isEmpty()) return 0;

        LocalDate txDate = rawRows.get(0).getTransactionDate();
        log.info("processPosExport | Ngày: {} | {} raw rows (trước aggregate)", txDate, rawRows.size());

        // Aggregate: SUM qty_sold và revenue theo productCode
        Map<String, PosTransactionRow> aggregated = new LinkedHashMap<>();
        for (PosTransactionRow row : rawRows) {
            aggregated.merge(
                row.getProductCode(),
                row,
                (existing, newRow) -> PosTransactionRow.builder()
                    .transactionDate(existing.getTransactionDate())
                    .branchName(existing.getBranchName())
                    .productCode(existing.getProductCode())
                    .productName(existing.getProductName())
                    .qtySold(existing.getQtySold().add(newRow.getQtySold()))
                    .revenue(existing.getRevenue().add(newRow.getRevenue()))
                    .qtyReturned(existing.getQtyReturned().add(newRow.getQtyReturned()))
                    .netRevenue(existing.getNetRevenue().add(newRow.getNetRevenue()))
                    .rowIndex(existing.getRowIndex())
                    .build()
            );
        }

        log.info("Sau aggregate: {} sản phẩm unique", aggregated.size());

        // Load product map
        Map<String, Product> productMap = productRepository
            .findAllByCodeIn(aggregated.keySet())
            .stream()
            .collect(Collectors.toMap(Product::getCode, p -> p));

        int saved = 0;

        for (PosTransactionRow row : aggregated.values()) {
            Product product = productMap.get(row.getProductCode());
            if (product == null) {
                log.warn("POS: Không tìm thấy SP {}", row.getProductCode());
                collector.addError(row.getRowIndex(), "Ma_SP",
                    "Không tìm thấy SP: " + row.getProductCode());
                continue;
            }

            // Overwrite
            posTransactionRepository
                .findByBranchIdAndProductIdAndTransactionDate(
                    shopBranch.getId(), product.getId(), txDate)
                .ifPresent(posTransactionRepository::delete);

            // Tính unit_price = revenue / qty_sold
            BigDecimal unitPrice = row.getQtySold().compareTo(BigDecimal.ZERO) > 0
                ? row.getRevenue().divide(row.getQtySold(), 4, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

            PosTransaction tx = PosTransaction.builder()
                .branch(shopBranch)
                .product(product)
                .transactionDate(txDate)
                .qtySold(row.getQtySold())
                .unitPrice(unitPrice)
                .revenue(row.getNetRevenue()) // dùng DT thuần
                .build();

            posTransactionRepository.save(tx);
            saved++;
        }

        log.info("processPosExport: lưu {} PosTransaction", saved);
        return saved;
    }
}
