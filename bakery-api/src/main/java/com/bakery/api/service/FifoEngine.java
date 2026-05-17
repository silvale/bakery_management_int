package com.bakery.api.service;

import com.bakery.common.entity.*;
import com.bakery.common.entity.enums.LotCostStatus;
import com.bakery.common.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * FIFO Engine — Phân bổ nguyên liệu theo lô cũ nhất trước.
 *
 * Các method chính:
 *   allocate()      → trừ kho khi sản xuất, trả về cost đã tính
 *   recalculate()   → tính lại sau backdate nhập kho
 *
 * Hỗ trợ tồn kho âm (PENDING):
 *   Nếu kho không đủ → vẫn cho sản xuất,
 *   đánh dấu lot cost_status = PENDING,
 *   khi có lô nhập backdate → tự recalculate.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FifoEngine {

    private final IngredientStockLotRepository stockLotRepository;
    private final FifoAllocationLogRepository  allocationLogRepository;
    private final ProductionLotRepository      productionLotRepository;
    private final RecipeRepository             recipeRepository;
    private final SemiProductCostRepository    semiProductCostRepository;

    // -------------------------------------------------------
    //  Phân bổ FIFO cho 1 production lot
    // -------------------------------------------------------

    /**
     * Tính cost và trừ kho nguyên liệu khi bếp sản xuất 1 lô bánh.
     *
     * @param productionLot  Lô bánh vừa sản xuất
     * @param branch         Chi nhánh (kho tổng)
     * @return FifoResult chứa cost_per_unit và trạng thái
     */
    @Transactional
    public FifoResult allocate(ProductionLot productionLot, Branch branch) {
        Product product = productionLot.getProduct();
        LocalDate prodDate = productionLot.getProductionDate();

        log.info("FIFO allocate | Lô: {} | SP: {} | Ngày: {}",
            productionLot.getLotNumber(), product.getCode(), prodDate);

        // Lookup công thức bánh
        Recipe recipe = recipeRepository
            .findActiveRecipe(product.getId(), prodDate)
            .orElse(null);

        if (recipe == null) {
            log.warn("Không tìm thấy công thức cho {} tại {}", product.getCode(), prodDate);
            return FifoResult.noCost(productionLot.getLotNumber());
        }

        BigDecimal totalCostPerUnit = BigDecimal.ZERO;
        boolean hasPending = false;
        List<String> pendingIngredients = new ArrayList<>();

        // Duyệt từng dòng công thức
        for (RecipeLine line : recipe.getLines()) {
            BigDecimal qty = productionLot.getQtyProduced();

            if (line.getSemiProduct() != null) {
                // Semi product: tính cost từ bảng semi_product_cost
                BigDecimal semiCost = allocateSemiProduct(
                    line, qty, prodDate
                );
                totalCostPerUnit = totalCostPerUnit.add(semiCost);

            } else if (line.getIngredient() != null) {
                // Ingredient: trừ kho FIFO
                AllocationDetail detail = allocateIngredient(
                    line, qty, branch, productionLot, prodDate
                );
                totalCostPerUnit = totalCostPerUnit.add(detail.costPerUnit());

                if (detail.isPending()) {
                    hasPending = true;
                    pendingIngredients.add(line.getIngredient().getCode());
                }
            }
        }

        totalCostPerUnit = totalCostPerUnit.setScale(6, RoundingMode.HALF_UP);

        // Cập nhật cost vào production_lot
        LotCostStatus costStatus = hasPending
            ? LotCostStatus.PENDING
            : LotCostStatus.CONFIRMED;

        productionLot.setCostPerUnit(totalCostPerUnit);
        productionLot.setCostStatus(costStatus);
        productionLotRepository.save(productionLot);

        if (hasPending) {
            log.warn("⚠ Lô {} có tồn kho âm cho: {}. Cost tạm tính.",
                productionLot.getLotNumber(), pendingIngredients);
        } else {
            log.info("✓ FIFO allocate xong | Lô: {} | Cost/unit: {}đ",
                productionLot.getLotNumber(), totalCostPerUnit);
        }

        return new FifoResult(
            productionLot.getLotNumber(),
            totalCostPerUnit,
            costStatus,
            hasPending,
            pendingIngredients
        );
    }

    // -------------------------------------------------------
    //  Recalculate sau backdate nhập kho
    // -------------------------------------------------------

    /**
     * Tính lại cost cho các lô PENDING sau khi có backdate nhập kho.
     * Gọi sau khi insert IngredientStockLot với is_backdate = true.
     *
     * @param ingredientId Nguyên liệu vừa được nhập backdate
     * @param branch       Chi nhánh
     */
    @Transactional
    public int recalculatePendingLots(UUID ingredientId, Branch branch) {
        log.info("Recalculate PENDING lots cho nguyên liệu: {}", ingredientId);

        List<ProductionLot> pendingLots = productionLotRepository.findPendingCostLots();
        int recalcCount = 0;

        for (ProductionLot lot : pendingLots) {
            boolean stillPending = false;
            BigDecimal newCostPerUnit = BigDecimal.ZERO;

            // Lấy tất cả allocation log của lô này
            List<FifoAllocationLog> logs = allocationLogRepository
                .findAllByProductionLotIdAndIsRecalculatedFalse(lot.getId());

            for (FifoAllocationLog alloc : logs) {
                // Chỉ recalculate dòng liên quan đến ingredient vừa backdate
                if (!alloc.getIngredient().getId().equals(ingredientId)) {
                    newCostPerUnit = newCostPerUnit.add(alloc.getCostContribution());
                    continue;
                }

                // Tìm lô nguyên liệu mới (backdate) để tính lại
                List<IngredientStockLot> availableLots = stockLotRepository
                    .findAvailableLotsForFifo(ingredientId, branch.getId());

                if (!availableLots.isEmpty()) {
                    BigDecimal newUnitPrice = availableLots.get(0).getUnitPrice();
                    BigDecimal newContribution = alloc.getQtyAllocated()
                        .multiply(newUnitPrice)
                        .setScale(6, RoundingMode.HALF_UP);

                    newCostPerUnit = newCostPerUnit.add(newContribution);
                    alloc.setIsRecalculated(true);
                    allocationLogRepository.save(alloc);
                } else {
                    stillPending = true;
                    newCostPerUnit = newCostPerUnit.add(alloc.getCostContribution());
                }
            }

            if (!stillPending) {
                productionLotRepository.updateCost(lot.getId(), newCostPerUnit);
                log.info("✓ Recalculate lô {} | Cost mới: {}đ", lot.getLotNumber(), newCostPerUnit);
                recalcCount++;
            }
        }

        log.info("Recalculate xong: {} lô", recalcCount);
        return recalcCount;
    }

    // -------------------------------------------------------
    //  Private helpers
    // -------------------------------------------------------

    /**
     * Phân bổ FIFO cho 1 ingredient trong công thức.
     * Trừ qty_remaining từ các lô cũ nhất trước.
     */
    private AllocationDetail allocateIngredient(
            RecipeLine line,
            BigDecimal qtyProduced,
            Branch branch,
            ProductionLot productionLot,
            LocalDate prodDate) {

        Ingredient ingredient = line.getIngredient();

        // Tổng gram cần dùng = qty_per_unit (gram) * số cái sản xuất
        // Với SHEET_CAKE (kg): qty_produced là kg, quantity_gram là gram/kg → cần convert
        BigDecimal totalGramNeeded = line.getQuantityGram().multiply(qtyProduced);

        log.debug("FIFO | {} | cần {}g", ingredient.getCode(), totalGramNeeded);

        List<IngredientStockLot> fifoLots = stockLotRepository
            .findAvailableLotsForFifo(ingredient.getId(), branch.getId());

        BigDecimal remaining = totalGramNeeded;
        BigDecimal totalCost = BigDecimal.ZERO;
        boolean isPending = false;

        for (IngredientStockLot stockLot : fifoLots) {
            if (remaining.compareTo(BigDecimal.ZERO) <= 0) break;

            BigDecimal consumed = stockLot.consume(remaining);
            remaining = remaining.subtract(consumed);

            BigDecimal costContrib = consumed
                .multiply(stockLot.getUnitPrice())
                .setScale(6, RoundingMode.HALF_UP);
            totalCost = totalCost.add(costContrib);

            // Lưu allocation log
            FifoAllocationLog log_ = FifoAllocationLog.builder()
                .productionLot(productionLot)
                .ingredientStockLot(stockLot)
                .ingredient(ingredient)
                .qtyAllocated(consumed)
                .unitPrice(stockLot.getUnitPrice())
                .costContribution(costContrib)
                .build();
            allocationLogRepository.save(log_);

            // Cập nhật lô nguyên liệu
            stockLotRepository.updateQtyRemaining(
                stockLot.getId(),
                stockLot.getQtyRemaining(),
                stockLot.getIsDepleted()
            );

            log.debug("FIFO | dùng {}g từ lô {} ({}đ/g)",
                consumed, stockLot.getId(), stockLot.getUnitPrice());
        }

        // Tồn kho âm — tạo allocation log với cost = 0 (PENDING)
        if (remaining.compareTo(BigDecimal.ZERO) > 0) {
            log.warn("⚠ FIFO âm | {} | thiếu {}g", ingredient.getCode(), remaining);
            isPending = true;

            FifoAllocationLog pendingLog = FifoAllocationLog.builder()
                .productionLot(productionLot)
                .ingredientStockLot(fifoLots.isEmpty() ? null : fifoLots.get(fifoLots.size() - 1))
                .ingredient(ingredient)
                .qtyAllocated(remaining)
                .unitPrice(BigDecimal.ZERO)
                .costContribution(BigDecimal.ZERO)
                .build();
            allocationLogRepository.save(pendingLog);
        }

        // Cost per unit = tổng cost / số cái sản xuất
        BigDecimal costPerUnit = qtyProduced.compareTo(BigDecimal.ZERO) > 0
            ? totalCost.divide(qtyProduced, 6, RoundingMode.HALF_UP)
            : BigDecimal.ZERO;

        return new AllocationDetail(costPerUnit, isPending);
    }

    /**
     * Tính cost cho semi product (phôi/nhân) từ bảng semi_product_cost.
     * Không trừ kho trực tiếp — kho phôi/nhân được tính riêng.
     */
    private BigDecimal allocateSemiProduct(
            RecipeLine line,
            BigDecimal qtyProduced,
            LocalDate prodDate) {

        SemiProduct sp = line.getSemiProduct();

        return semiProductCostRepository
            .findActiveCost(sp.getId(), prodDate)
            .map(cost -> line.getQuantityGram()
                .divide(BigDecimal.valueOf(1000), 10, RoundingMode.HALF_UP)
                .multiply(cost.getCostPerKg())
                .divide(qtyProduced.compareTo(BigDecimal.ZERO) > 0 ? qtyProduced : BigDecimal.ONE,
                    6, RoundingMode.HALF_UP))
            .orElseGet(() -> {
                log.warn("Không tìm thấy cost cho SemiProduct: {} tại {}", sp.getCode(), prodDate);
                return BigDecimal.ZERO;
            });
    }

    // -------------------------------------------------------
    //  Result records
    // -------------------------------------------------------

    public record FifoResult(
        String lotNumber,
        BigDecimal costPerUnit,
        LotCostStatus costStatus,
        boolean hasPending,
        List<String> pendingIngredients
    ) {
        static FifoResult noCost(String lotNumber) {
            return new FifoResult(lotNumber, BigDecimal.ZERO,
                LotCostStatus.PENDING, true, List.of("NO_RECIPE"));
        }
    }

    private record AllocationDetail(BigDecimal costPerUnit, boolean isPending) {}
}
