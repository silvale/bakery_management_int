package com.bakery.api.service;

import com.bakery.common.entity.*;
import com.bakery.common.entity.enums.ReferenceType;
import com.bakery.common.entity.enums.TransactionType;
import com.bakery.common.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.*;

/**
 * Kiểm đếm nguyên liệu / phụ kiện định kỳ cho bất kỳ kho nào (KHO_TONG, KHO_BEP, SHOP).
 *
 * Flow:
 *   1. Nhân viên đếm thực tế → nhập qua API
 *   2. Service tính:
 *        tồn_lý_thuyết = qty_on_hand_before - pos_sold_trong_kỳ
 *        hao_hụt       = max(0, theoretical - actual)
 *   3. Nếu có hao hụt: FEFO deduct từ ingredient_stock_lot
 *      → tạo InventoryMovement EXPORT / DECREASE
 *   4. Force-set ingredient_stock.qty_on_hand = actual
 *   5. Ghi accessory_stocktake_log (audit trail)
 *
 * "Kỳ kiểm đếm" = từ ingredient_stock.last_reconcile_date (exclusive)
 *                 đến stocktake_date (inclusive).
 *                 Nếu last_reconcile_date = NULL → tính POS từ 2020-01-01.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StocktakeService {

    private final BranchRepository                branchRepository;
    private final IngredientRepository            ingredientRepository;
    private final IngredientStockRepository       ingredientStockRepository;
    private final IngredientStockLotRepository    ingredientStockLotRepository;
    private final PosTransactionRepository        posTransactionRepository;
    private final AccessoryStocktakeLogRepository stocktakeLogRepository;
    private final InventoryMovementRepository     inventoryMovementRepository;

    // ── DTOs ──────────────────────────────────────────────────

    public record StocktakeLineRequest(
        String     ingredientCode,
        BigDecimal actualCount
    ) {}

    public record StocktakeLineResult(
        String     ingredientCode,
        String     ingredientName,
        BigDecimal qtyOnHandBefore,
        BigDecimal qtyPosSold,
        BigDecimal qtyTheoretical,
        BigDecimal qtyActual,
        BigDecimal qtyLoss,
        BigDecimal qtyOverage
    ) {}

    public record StocktakeResult(
        LocalDate                 stocktakeDate,
        String                    branchCode,
        List<StocktakeLineResult> lines,
        int                       totalItems,
        int                       itemsWithLoss
    ) {}

    // ── Main entry ────────────────────────────────────────────

    @Transactional
    public StocktakeResult reconcileStock(
            UUID      branchId,
            LocalDate stocktakeDate,
            List<StocktakeLineRequest> items,
            String    performedBy) {

        Branch branch = branchRepository.findById(branchId)
            .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy branch: " + branchId));

        log.info("=== Kiểm đếm | Branch: {} | Ngày: {} | {} items ===",
            branch.getCode(), stocktakeDate, items.size());

        List<StocktakeLineResult> results = new ArrayList<>();
        int itemsWithLoss = 0;

        for (StocktakeLineRequest req : items) {
            try {
                StocktakeLineResult r = processLine(branch, stocktakeDate, req, performedBy);
                results.add(r);
                if (r.qtyLoss().compareTo(BigDecimal.ZERO) > 0) itemsWithLoss++;
            } catch (Exception e) {
                log.error("Lỗi kiểm đếm {}: {}", req.ingredientCode(), e.getMessage(), e);
                results.add(new StocktakeLineResult(
                    req.ingredientCode(), "ERROR: " + e.getMessage(),
                    BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                    req.actualCount(), BigDecimal.ZERO, BigDecimal.ZERO));
            }
        }

        log.info("=== Kiểm đếm xong: {} items, {} có hao hụt ===", results.size(), itemsWithLoss);
        return new StocktakeResult(stocktakeDate, branch.getCode(), results,
            results.size(), itemsWithLoss);
    }

    // ── Per-line processing ───────────────────────────────────

    private StocktakeLineResult processLine(Branch branch, LocalDate stocktakeDate,
                                            StocktakeLineRequest req, String performedBy) {

        Ingredient ing = ingredientRepository.findByCode(req.ingredientCode())
            .orElseThrow(() -> new IllegalArgumentException(
                "Không tìm thấy nguyên liệu: " + req.ingredientCode()));

        IngredientStock stock = ingredientStockRepository
            .findByIngredientIdAndBranchId(ing.getId(), branch.getId())
            .orElseGet(() -> {
                log.warn("Chưa có tồn kho cho {} tại {} — tạo mới qty=0",
                    ing.getCode(), branch.getCode());
                IngredientStock newStock = IngredientStock.builder()
                    .ingredient(ing).branch(branch)
                    .qtyOnHand(BigDecimal.ZERO)
                    .qtyReserved(BigDecimal.ZERO)
                    .build();
                newStock.setCreatedBy(performedBy);
                return ingredientStockRepository.save(newStock);
            });

        BigDecimal qtyOnHandBefore = stock.getQtyOnHand();

        // POS sold trong kỳ — product.code = ingredient.code cho các sản phẩm bán lẻ
        LocalDate periodFrom = stock.getLastReconcileDate();
        LocalDate fromDate   = periodFrom != null ? periodFrom : LocalDate.of(2020, 1, 1);
        BigDecimal qtyPosSold = posTransactionRepository
            .sumQtySoldByProductCodeAndBranchAndPeriod(
                ing.getCode(), branch.getId(), fromDate, stocktakeDate);

        BigDecimal qtyTheoretical = qtyOnHandBefore.subtract(qtyPosSold).max(BigDecimal.ZERO);
        BigDecimal qtyActual      = req.actualCount();
        BigDecimal diff           = qtyTheoretical.subtract(qtyActual);
        BigDecimal qtyLoss        = diff.compareTo(BigDecimal.ZERO) > 0 ? diff : BigDecimal.ZERO;
        BigDecimal qtyOverage     = diff.compareTo(BigDecimal.ZERO) < 0 ? diff.negate() : BigDecimal.ZERO;

        log.info("[{}] on_hand_before={} pos_sold={} theoretical={} actual={} loss={} overage={}",
            ing.getCode(), qtyOnHandBefore, qtyPosSold, qtyTheoretical, qtyActual, qtyLoss, qtyOverage);

        if (qtyLoss.compareTo(BigDecimal.ZERO) > 0) {
            deductLoss(ing, branch, qtyLoss, stocktakeDate, performedBy);
        }

        // Force-set qty_on_hand = actual + cập nhật last_reconcile_date
        stock.setQtyOnHand(qtyActual);
        stock.setLastReconcileDate(stocktakeDate);
        stock.setLastUpdated(OffsetDateTime.now());
        ingredientStockRepository.save(stock);

        stocktakeLogRepository.save(AccessoryStocktakeLog.builder()
            .branch(branch)
            .ingredient(ing)
            .stocktakeDate(stocktakeDate)
            .periodFrom(periodFrom)
            .periodTo(stocktakeDate)
            .qtyOnHandBefore(qtyOnHandBefore)
            .qtyPosSold(qtyPosSold)
            .qtyTheoretical(qtyTheoretical)
            .qtyActual(qtyActual)
            .qtyLoss(qtyLoss)
            .qtyOverage(qtyOverage)
            .createdBy(performedBy)
            .build());

        return new StocktakeLineResult(
            ing.getCode(), ing.getName(),
            qtyOnHandBefore, qtyPosSold, qtyTheoretical, qtyActual, qtyLoss, qtyOverage);
    }

    // ── FEFO deduct loss ──────────────────────────────────────

    private void deductLoss(Ingredient ing, Branch branch, BigDecimal lossQty,
                            LocalDate date, String by) {

        List<IngredientStockLot> lots = ingredientStockLotRepository
            .findAvailableLotsForFifo(ing.getId(), branch.getId());

        BigDecimal remaining = lossQty;

        for (IngredientStockLot lot : lots) {
            if (remaining.compareTo(BigDecimal.ZERO) <= 0) break;
            BigDecimal consumed = lot.consume(remaining);
            remaining = remaining.subtract(consumed);
            ingredientStockLotRepository.save(lot);

            inventoryMovementRepository.save(InventoryMovement.builder()
                .branch(branch)
                .itemType("INGREDIENT")
                .ingredient(ing)
                .lotId(lot.getId())
                .transactionType(TransactionType.EXPORT)
                .referenceType(ReferenceType.DECREASE)
                .qty(consumed)
                .unit(ing.getBaseUnit().name())
                .sourceType("STOCKTAKE")
                .sourceId(null)
                .referenceCode("STOCKTAKE-" + date)
                .note("Hao hụt kiểm đếm " + date + " tại " + branch.getCode())
                .createdBy(by)
                .build());
        }

        if (remaining.compareTo(BigDecimal.ZERO) > 0) {
            log.warn("Thiếu lô để deduct {}/{}: còn thiếu {}", ing.getCode(), branch.getCode(), remaining);
        }

        int updated = ingredientStockRepository.updateStock(ing.getId(), branch.getId(), lossQty.negate());
        if (updated == 0) {
            log.warn("Không cập nhật được ingredient_stock cho {}/{}", ing.getCode(), branch.getCode());
        }
    }

    // ── Query helpers ─────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<AccessoryStocktakeLog> getStocktakeHistory(UUID branchId, int days) {
        return stocktakeLogRepository.findAllByBranchAndDateAfter(branchId,
            LocalDate.now().minusDays(days));
    }
}
