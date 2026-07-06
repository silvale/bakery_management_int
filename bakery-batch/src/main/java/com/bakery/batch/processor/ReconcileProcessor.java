package com.bakery.batch.processor;

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

/**
 * Tính toán và lưu DailyReconcile từ dữ liệu đã import.
 *
 * Chạy SAU khi cả 4 file đã được import thành công.
 *
 * Logic 3 tầng:
 *   Tầng 1: qtyRequested (BanhRaNgay) vs qtyProduced (XuatRa)
 *   Tầng 2: qtySent (XuatRa) vs qtyReceived (BaoCaoNgay)
 *   Tầng 3: qtySoldPos (POS) vs qtySoldDerived (BaoCaoNgay kiểm kê)
 *
 * Cost:
 *   cost_per_unit = lookup từ Recipe + IngredientPrice (SemiProductCost đã xóa V15)
 *   gross_profit  = revenue - sales_cost - cancelled_cost
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReconcileProcessor {

    private final BranchRepository           branchRepository;
    private final ProductionOrderRepository  productionOrderRepository;
    private final StockTransferRepository    stockTransferRepository;
    private final DailyInventoryRepository   dailyInventoryRepository;
    private final PosTransactionRepository   posTransactionRepository;
    private final DailyReconcileRepository   dailyReconcileRepository;
    private final RecipeRepository           recipeRepository;
    private final IngredientPriceRepository  ingredientPriceRepository;

    /**
     * Chạy reconcile cho 1 ngày cụ thể.
     * Kết quả ghi vào DAILY_RECONCILE (overwrite nếu đã tồn tại).
     */
    @Transactional
    public int reconcile(LocalDate reconDate, Branch kitchenBranch, Branch shopBranch) {
        log.info("=== Bắt đầu Reconcile ngày {} ===", reconDate);

        // Overwrite: xóa reconcile cũ nếu re-run
        int deleted = dailyReconcileRepository
            .deleteByBranchIdAndReconDate(shopBranch.getId(), reconDate);
        if (deleted > 0) {
            log.info("Đã xóa {} dòng reconcile cũ (re-run)", deleted);
        }

        // Load tất cả data đã import
        Optional<ProductionOrder> orderOpt = productionOrderRepository
            .findWithLinesByBranchIdAndOrderDate(kitchenBranch.getId(), reconDate);

        List<StockTransfer> transfers = stockTransferRepository
            .findAllByBranchesAndDate(kitchenBranch.getId(), shopBranch.getId(), reconDate);

        List<DailyInventory> inventories = dailyInventoryRepository
            .findAllWithProductByBranchIdAndDate(shopBranch.getId(), reconDate);

        List<PosTransaction> posTxs = posTransactionRepository
            .findAllWithProductByBranchAndDate(shopBranch.getId(), reconDate);

        // Build lookup maps
        Map<String, ProductionOrderLine> orderLineMap = buildOrderLineMap(orderOpt);
        Map<String, StockTransfer>       transferMap  = buildTransferMap(transfers);
        Map<String, DailyInventory>      inventoryMap = buildInventoryMap(inventories);
        Map<String, PosTransaction>      posMap       = buildPosMap(posTxs);

        // Debug log
        log.info("DEBUG kitchenBranch: {} ({})", kitchenBranch.getCode(), kitchenBranch.getId());
        log.info("DEBUG shopBranch: {} ({})", shopBranch.getCode(), shopBranch.getId());
        log.info("DEBUG orderOpt present: {}", orderOpt.isPresent());
        log.info("DEBUG transfers count: {}", transfers.size());
        log.info("DEBUG inventories count: {}", inventories.size());
        log.info("DEBUG posTxs count: {}", posTxs.size());
        log.info("DEBUG orderLineMap keys: {}", orderLineMap.keySet());
        log.info("DEBUG inventoryMap keys: {}", inventoryMap.keySet());
        log.info("DEBUG posMap keys: {}", posMap.keySet());

        // Union tất cả product codes từ tất cả nguồn
        Set<String> allProductCodes = new HashSet<>();
        allProductCodes.addAll(orderLineMap.keySet());
        allProductCodes.addAll(transferMap.keySet());
        allProductCodes.addAll(inventoryMap.keySet());
        allProductCodes.addAll(posMap.keySet());

        log.info("Tổng {} sản phẩm cần reconcile: {}", allProductCodes.size(), allProductCodes);

        List<DailyReconcile> reconciles = new ArrayList<>();

        for (String productCode : allProductCodes) {
            try {
                DailyReconcile recon = buildReconcile(
                    reconDate, shopBranch,
                    productCode,
                    orderLineMap.get(productCode),
                    transferMap.get(productCode),
                    inventoryMap.get(productCode),
                    posMap.get(productCode)
                );
                reconciles.add(recon);
            } catch (Exception e) {
                log.error("Lỗi reconcile SP {}: {}", productCode, e.getMessage(), e);
            }
        }

        dailyReconcileRepository.saveAll(reconciles);
        log.info("=== Reconcile xong: {} dòng ===", reconciles.size());
        return reconciles.size();
    }

    // -------------------------------------------------------
    // Build DailyReconcile cho 1 sản phẩm
    // -------------------------------------------------------

    private DailyReconcile buildReconcile(
            LocalDate reconDate,
            Branch shopBranch,
            String productCode,
            ProductionOrderLine orderLine,
            StockTransfer transfer,
            DailyInventory inventory,
            PosTransaction posTx) {

        Product product = resolveProduct(orderLine, transfer, inventory, posTx);

        // === Tầng 1: Sản xuất ===
        BigDecimal qtyRequested = getOrZero(orderLine, ProductionOrderLine::getQtyRequested);
        BigDecimal qtyProduced  = getOrZero(orderLine, ProductionOrderLine::getQtyActual);
        BigDecimal prodDiff     = qtyProduced.subtract(qtyRequested);
        ReconcileStatus prodStatus = reconcileProductionStatus(qtyRequested, qtyProduced, product);

        // === Tầng 2: Vận chuyển ===
        BigDecimal qtySent     = transfer != null ? transfer.getQtySent()     : BigDecimal.ZERO;
        BigDecimal qtyReceived = transfer != null ? transfer.getQtyReceived()  : null;
        BigDecimal delivDiff   = qtyReceived != null
            ? qtySent.subtract(qtyReceived)
            : null;
        ReconcileStatus delivStatus = transfer != null
            ? ReconcileStatus.valueOf(transfer.getStatus())
            : ReconcileStatus.PENDING;

        // === Tầng 3: Bán hàng ===
        BigDecimal qtyOpening         = inventory != null ? inventory.getQtyOpening()      : BigDecimal.ZERO;
        BigDecimal qtyReceivedInv     = inventory != null ? inventory.getQtyReceived()     : BigDecimal.ZERO;
        BigDecimal qtyCancelled       = inventory != null ? inventory.getQtyCancelled()    : BigDecimal.ZERO;
        BigDecimal qtyClosing         = inventory != null ? inventory.getQtyClosing()      : BigDecimal.ZERO;
        BigDecimal qtySoldReported    = inventory != null ? inventory.getQtySoldReported() : BigDecimal.ZERO;
        // Nhân viên ghi: H (tổng bán)

        // Validate tồn tối: F phải = (D+E) - (H+I)
        // Đây là cross-check data nhân viên nhập, lưu vào qty_sold_derived để reference
        BigDecimal qtySoldDerived  = qtyOpening.add(qtyReceivedInv)
                                               .subtract(qtySoldReported)
                                               .subtract(qtyCancelled);
        // qty_sold_derived = tồn tối lý thuyết, so với qty_closing thực tế
        // Nếu qtySoldDerived != qtyClosing → nhân viên nhập sai

        // So sánh Tầng 3: H (nhân viên ghi) vs POS
        BigDecimal qtySoldPos   = posTx != null ? posTx.getQtySold() : BigDecimal.ZERO;
        BigDecimal posDiff      = qtySoldPos.subtract(qtySoldReported);
        ReconcileStatus posStatus = reconcilePosStatus(qtySoldPos, qtySoldReported, product);

        // === Cost & Lợi nhuận ===
        Recipe recipe = product != null
            ? recipeRepository.findActiveRecipe(product.getId(), reconDate).orElse(null)
            : null;

        BigDecimal costPerUnit  = recipe != null
            ? calculateCostPerUnit(recipe, reconDate)
            : BigDecimal.ZERO;

        // Revenue từ POS đã = qty_sold * giá, dùng thẳng
        BigDecimal unitPrice     = posTx != null ? posTx.getUnitPrice() : BigDecimal.ZERO;
        BigDecimal revenue       = posTx != null ? posTx.getRevenue()   : BigDecimal.ZERO;
        // Cost tính trên số thực bán (qtySoldReported từ BaoCaoNgay)
        BigDecimal salesCost     = qtySoldReported.multiply(costPerUnit).setScale(4, RoundingMode.HALF_UP);
        BigDecimal cancelledCost = qtyCancelled.multiply(costPerUnit).setScale(4, RoundingMode.HALF_UP);
        // Gross profit = Doanh thu POS - Chi phí nguyên liệu bán - Chi phí bánh hủy
        BigDecimal grossProfit   = revenue.subtract(salesCost).subtract(cancelledCost);

        // === Overall status ===
        ReconcileStatus overall = determineOverallStatus(prodStatus, delivStatus, posStatus);

        return DailyReconcile.builder()
            .branch(shopBranch)
            .product(product)
            .reconDate(reconDate)
            // Tầng 1
            .qtyRequested(qtyRequested)
            .qtyProduced(qtyProduced)
            .productionVsOrderDiff(prodDiff)
            .productionVsOrderStatus(prodStatus)
            // Tầng 2
            .qtySent(qtySent)
            .qtyReceived(qtyReceived)
            .deliveryVsReceiptDiff(delivDiff)
            .deliveryVsReceiptStatus(delivStatus)
            // Tầng 3
            .qtySoldPos(qtySoldPos)
            .qtySoldReported(qtySoldReported)
            .qtyOpening(qtyOpening)
            .qtyCancelled(qtyCancelled)
            .qtyClosing(qtyClosing)
            .qtySoldDerived(qtySoldDerived)
            .posVsInventoryDiff(posDiff)
            .posVsInventoryStatus(posStatus)
            // Snapshot
            .recipe(recipe)
            // Cost
            .costPerUnit(costPerUnit)
            .unitPrice(unitPrice)
            .revenue(revenue)
            .salesCost(salesCost)
            .cancelledCost(cancelledCost)
            .grossProfit(grossProfit)
            // Overall
            .overallStatus(overall)
            .discrepancyNote(buildDiscrepancyNote(prodStatus, delivStatus, posStatus))
            .build();
    }

    // -------------------------------------------------------
    // Cost calculation
    // -------------------------------------------------------

    /**
     * Tính cost/unit từ Recipe.
     * cost = SUM của tất cả recipe_line:
     *   - semi_product: (quantity_gram / 1000) * semi_product_cost.cost_per_kg
     *   - ingredient:   (quantity_gram * price_per_kg / 1_000_000)
     */
    private BigDecimal calculateCostPerUnit(Recipe recipe, LocalDate reconDate) {
        BigDecimal total = BigDecimal.ZERO;

        for (RecipeLine line : recipe.getLines()) {
            BigDecimal contribution = BigDecimal.ZERO;

            if (line.getSemiProduct() != null) {
                // Semi product: (gram/1000) * cost_per_kg


            } else if (line.getIngredient() != null) {
                // Ingredient: gram * price_per_kg / 1,000,000
                Optional<IngredientPrice> priceOpt = ingredientPriceRepository
                    .findActivePrice(line.getIngredient().getId(), reconDate);

                if (priceOpt.isPresent()) {
                    contribution = line.getQuantityGram()
                        .multiply(priceOpt.get().getPricePerKg())
                        .divide(BigDecimal.valueOf(1_000_000), 10, RoundingMode.HALF_UP);
                } else {
                    log.warn("Không tìm thấy giá cho Ingredient: {} tại ngày {}",
                        line.getIngredient().getCode(), reconDate);
                }
            }

            total = total.add(contribution);
        }

        return total.setScale(4, RoundingMode.HALF_UP);
    }

    // -------------------------------------------------------
    // Status determination
    // -------------------------------------------------------

    private ReconcileStatus reconcileProductionStatus(
            BigDecimal requested, BigDecimal produced, Product product) {

        if (requested.compareTo(BigDecimal.ZERO) == 0) return ReconcileStatus.PENDING;

        BigDecimal diff = produced.subtract(requested).abs();
        BigDecimal threshold = product != null
            ? requested.multiply(product.getToleranceRate())
            : BigDecimal.ZERO;

        if (diff.compareTo(threshold) <= 0) return ReconcileStatus.OK;
        return produced.compareTo(requested) > 0
            ? ReconcileStatus.OVER
            : ReconcileStatus.UNDER;
    }

    private ReconcileStatus reconcilePosStatus(
            BigDecimal qtySoldPos, BigDecimal qtySoldDerived, Product product) {

        if (qtySoldPos.compareTo(BigDecimal.ZERO) == 0
                && qtySoldDerived.compareTo(BigDecimal.ZERO) == 0) {
            return ReconcileStatus.PENDING;
        }

        BigDecimal diff = qtySoldPos.subtract(qtySoldDerived).abs();
        BigDecimal base = qtySoldDerived.compareTo(BigDecimal.ZERO) > 0
            ? qtySoldDerived
            : qtySoldPos;
        BigDecimal threshold = product != null
            ? base.multiply(product.getToleranceRate())
            : BigDecimal.ZERO;

        return diff.compareTo(threshold) <= 0
            ? ReconcileStatus.OK
            : ReconcileStatus.DISCREPANCY;
    }

    private ReconcileStatus determineOverallStatus(
            ReconcileStatus prod, ReconcileStatus deliv, ReconcileStatus pos) {

        boolean hasDiscrepancy =
            prod == ReconcileStatus.OVER   ||
            prod == ReconcileStatus.UNDER  ||
            deliv == ReconcileStatus.DISCREPANCY ||
            pos   == ReconcileStatus.DISCREPANCY;

        boolean allPending =
            prod  == ReconcileStatus.PENDING &&
            deliv == ReconcileStatus.PENDING &&
            pos   == ReconcileStatus.PENDING;

        if (allPending)      return ReconcileStatus.PENDING;
        if (hasDiscrepancy)  return ReconcileStatus.DISCREPANCY;
        return ReconcileStatus.OK;
    }

    private String buildDiscrepancyNote(
            ReconcileStatus prod, ReconcileStatus deliv, ReconcileStatus pos) {
        List<String> issues = new ArrayList<>();
        if (prod == ReconcileStatus.OVER)          issues.add("Sản xuất dư");
        if (prod == ReconcileStatus.UNDER)          issues.add("Sản xuất thiếu");
        if (deliv == ReconcileStatus.DISCREPANCY)   issues.add("Chênh lệch vận chuyển");
        if (pos == ReconcileStatus.DISCREPANCY)     issues.add("Chênh lệch bán hàng vs kiểm kê");
        return issues.isEmpty() ? null : String.join(" | ", issues);
    }

    // -------------------------------------------------------
    // Helpers
    // -------------------------------------------------------

    private Map<String, ProductionOrderLine> buildOrderLineMap(Optional<ProductionOrder> orderOpt) {
        if (orderOpt.isEmpty()) return Map.of();
        return orderOpt.get().getLines().stream()
            .collect(java.util.stream.Collectors.toMap(
                l -> l.getProduct().getCode(),
                l -> l
            ));
    }

    private Map<String, StockTransfer> buildTransferMap(List<StockTransfer> transfers) {
        return transfers.stream()
            .collect(java.util.stream.Collectors.toMap(
                t -> t.getProduct().getCode(),
                t -> t,
                (a, b) -> a // giữ cái đầu nếu trùng
            ));
    }

    private Map<String, DailyInventory> buildInventoryMap(List<DailyInventory> inventories) {
        return inventories.stream()
            .collect(java.util.stream.Collectors.toMap(
                i -> i.getProduct().getCode(),
                i -> i
            ));
    }

    private Map<String, PosTransaction> buildPosMap(List<PosTransaction> txs) {
        return txs.stream()
            .collect(java.util.stream.Collectors.toMap(
                t -> t.getProduct().getCode(),
                t -> t
            ));
    }

    private Product resolveProduct(
            ProductionOrderLine ol, StockTransfer st,
            DailyInventory di, PosTransaction pt) {
        if (ol != null) return ol.getProduct();
        if (st != null) return st.getProduct();
        if (di != null) return di.getProduct();
        if (pt != null) return pt.getProduct();
        return null;
    }

    private <T> BigDecimal getOrZero(T obj, java.util.function.Function<T, BigDecimal> getter) {
        if (obj == null) return BigDecimal.ZERO;
        BigDecimal val = getter.apply(obj);
        return val != null ? val : BigDecimal.ZERO;
    }
}
