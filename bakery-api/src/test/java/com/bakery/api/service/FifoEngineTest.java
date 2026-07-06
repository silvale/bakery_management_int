package com.bakery.api.service;

import com.bakery.common.entity.*;
import com.bakery.common.entity.enums.*;
import com.bakery.common.repository.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit test cho FifoEngine — flow hoàn chỉnh từ sản xuất → chốt cost.
 *
 * Các scenario được test:
 *
 *   Scenario 1 — Happy path (kho đủ, 1 lô nguyên liệu)
 *     → Allocate đúng qty, cost_per_unit tính chính xác, status = CONFIRMED
 *
 *   Scenario 2 — Kho đủ, nhiều lô FIFO (oldest first)
 *     → Lô cũ được dùng hết trước, lô mới dùng phần còn lại
 *     → Cost = weighted average theo qty từ mỗi lô
 *
 *   Scenario 3 — Tồn kho âm (không đủ nguyên liệu)
 *     → Vẫn cho sản xuất, cost_status = PENDING
 *     → Lưu allocation log với unitPrice = 0 cho phần thiếu
 *
 *   Scenario 4 — Không tìm thấy công thức (recipe = null)
 *     → Trả FifoResult.noCost(), lot vẫn được lưu với cost = 0
 *
 *   Scenario 5 — Recalculate sau backdate (PENDING → CONFIRMED)
 *     → Sau khi có lô nguyên liệu mới, cost được tính lại chính xác
 *
 *   Scenario 6 — Semi-product trong công thức
 *     → Cost tính on-the-fly qua CostCalculationService, không trừ kho NL trực tiếp
 *
 *   Scenario 7 — Nhiều recipe line (ingredient + semi-product)
 *     → Tổng cost = sum đóng góp từ tất cả các dòng công thức
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("FifoEngine — Production Cost Flow")
class FifoEngineTest {

    // ── Mocks ─────────────────────────────────────────────────────────────────

    @Mock IngredientStockLotRepository  stockLotRepository;
    @Mock FifoAllocationLogRepository   allocationLogRepository;
    @Mock ProductionLotRepository       productionLotRepository;
    @Mock RecipeRepository              recipeRepository;
    @Mock CostCalculationService        costCalculationService;

    @InjectMocks FifoEngine fifoEngine;

    // ── Shared dummy data ─────────────────────────────────────────────────────

    private Branch       branch;
    private Product      product;
    private Ingredient   flour;       // bột mì
    private Ingredient   butter;      // bơ
    private SemiProduct  doughBase;   // phôi bột viên trắng

    @BeforeEach
    void setUp() {
        branch = branch("MAIN", "Kho Tổng");

        product = Product.builder()
                .code("BMM-BANH-TIEU")
                .name("Bánh Tiêu")
                .productType(ProductType.STANDARD)
                .unit("PCS")
                .toleranceRate(new BigDecimal("0.03"))
                .build();
        setId(product, uuid("product-1"));

        flour = ingredient("BM-BOT-XE-AP", "Bột xe đạp");
        butter = ingredient("BM-BO-ANCHOR", "Bơ Anchor");
        doughBase = semiProduct("SP-BOT-VIEN-TRANG", "Bột Viên Trắng", SemiProductType.PHOI);
    }

    // =========================================================================
    //  Scenario 1 — Happy path: 1 lô nguyên liệu, đủ kho
    // =========================================================================

    @Test
    @DisplayName("S1: Đủ kho — 1 ingredient, 1 lot → CONFIRMED cost")
    void allocate_singleLot_sufficientStock_returnsConfirmedCost() {
        // Công thức: 1 cái bánh tiêu dùng 50g bột xe đạp
        //            Giá bột: 20,000đ/kg = 0.020 VNĐ/gram
        // Cost/unit = 50g × 0.020 = 1,000 VNĐ/cái
        // Sản xuất:  10 cái → tổng cần 500g

        Recipe recipe = recipeWith(product,
                recipeLine(flour, new BigDecimal("50"), RecipeLineType.TRANG_TRI));

        ProductionLot lot = productionLot("LOT-20260530-BMM-001", product, 10,
                LocalDate.of(2026, 5, 30));

        // Kho: 1 lô 1000g, giá 0.020 VNĐ/gram (= 20,000đ/kg)
        IngredientStockLot stockLot = stockLot(flour, new BigDecimal("1000"),
                new BigDecimal("0.020000"), LocalDate.of(2026, 5, 28));

        when(recipeRepository.findActiveRecipe(product.getId(), lot.getProductionDate()))
                .thenReturn(Optional.of(recipe));
        when(stockLotRepository.findAvailableLotsForFifo(flour.getId(), branch.getId()))
                .thenReturn(List.of(stockLot));
        when(productionLotRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(allocationLogRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // Act
        FifoEngine.FifoResult result = fifoEngine.allocate(lot, branch);

        // Assert — kết quả
        assertThat(result.hasPending()).isFalse();
        assertThat(result.costStatus()).isEqualTo(LotCostStatus.CONFIRMED);
        assertThat(result.costPerUnit())
                .isEqualByComparingTo(new BigDecimal("1.000000")); // 50g × 0.02 = 1đ/cái

        // Assert — kho bị trừ đúng 500g
        assertThat(stockLot.getQtyRemaining())
                .isEqualByComparingTo(new BigDecimal("500")); // 1000 - 500
        assertThat(stockLot.getIsDepleted()).isFalse();

        // Assert — production lot được save với cost
        ArgumentCaptor<ProductionLot> lotCaptor = ArgumentCaptor.forClass(ProductionLot.class);
        verify(productionLotRepository).save(lotCaptor.capture());
        assertThat(lotCaptor.getValue().getCostStatus()).isEqualTo(LotCostStatus.CONFIRMED);
        assertThat(lotCaptor.getValue().getCostPerUnit())
                .isEqualByComparingTo(new BigDecimal("1.000000"));

        // Assert — allocation log được ghi đúng 1 lần
        verify(allocationLogRepository, times(1)).save(any(FifoAllocationLog.class));
    }

    // =========================================================================
    //  Scenario 2 — Nhiều lô FIFO, cũ nhất dùng trước
    // =========================================================================

    @Test
    @DisplayName("S2: Nhiều lô FIFO — lô cũ dùng hết trước, cost = weighted avg")
    void allocate_multipleStockLots_fifoOrder_correctWeightedCost() {
        // Công thức: 80g bơ / cái. Sản xuất 10 cái → cần 800g
        //
        // Kho:
        //   Lô A (cũ hơn): 300g @ 0.030 VNĐ/g  (= 30đ/kg) → dùng hết
        //   Lô B (mới hơn): 600g @ 0.040 VNĐ/g  (= 40đ/kg) → dùng 500g
        //
        // Cost = (300×0.030 + 500×0.040) / 10 = (9 + 20) / 10 = 2.9 VNĐ/cái

        Recipe recipe = recipeWith(product,
                recipeLine(butter, new BigDecimal("80"), RecipeLineType.TRANG_TRI));

        ProductionLot lot = productionLot("LOT-20260530-BMM-002", product, 10,
                LocalDate.of(2026, 5, 30));

        IngredientStockLot lotA = stockLot(butter, new BigDecimal("300"),
                new BigDecimal("0.030000"), LocalDate.of(2026, 5, 20)); // cũ hơn
        IngredientStockLot lotB = stockLot(butter, new BigDecimal("600"),
                new BigDecimal("0.040000"), LocalDate.of(2026, 5, 25)); // mới hơn

        when(recipeRepository.findActiveRecipe(product.getId(), lot.getProductionDate()))
                .thenReturn(Optional.of(recipe));
        when(stockLotRepository.findAvailableLotsForFifo(butter.getId(), branch.getId()))
                .thenReturn(List.of(lotA, lotB)); // FIFO order: A trước B
        when(productionLotRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(allocationLogRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // Act
        FifoEngine.FifoResult result = fifoEngine.allocate(lot, branch);

        // Assert — cost
        assertThat(result.hasPending()).isFalse();
        assertThat(result.costPerUnit())
                .isEqualByComparingTo(new BigDecimal("2.900000")); // (300×0.03 + 500×0.04)/10

        // Assert — lô A bị dùng hết (300g → 0)
        assertThat(lotA.getQtyRemaining()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(lotA.getIsDepleted()).isTrue();

        // Assert — lô B còn lại 100g (600 - 500)
        assertThat(lotB.getQtyRemaining()).isEqualByComparingTo(new BigDecimal("100"));
        assertThat(lotB.getIsDepleted()).isFalse();

        // Assert — ghi log 2 lần (1 per lot)
        verify(allocationLogRepository, times(2)).save(any(FifoAllocationLog.class));
    }

    // =========================================================================
    //  Scenario 3 — Tồn kho âm (thiếu nguyên liệu) → PENDING
    // =========================================================================

    @Test
    @DisplayName("S3: Kho không đủ → cost_status = PENDING, vẫn cho sản xuất")
    void allocate_insufficientStock_returnsPendingCost() {
        // Cần 500g bột, kho chỉ có 200g → thiếu 300g
        Recipe recipe = recipeWith(product,
                recipeLine(flour, new BigDecimal("50"), RecipeLineType.TRANG_TRI));

        ProductionLot lot = productionLot("LOT-20260530-BMM-003", product, 10,
                LocalDate.of(2026, 5, 30));

        // Kho chỉ có 200g (thiếu 300g so với cần 500g)
        IngredientStockLot thinLot = stockLot(flour, new BigDecimal("200"),
                new BigDecimal("0.020000"), LocalDate.of(2026, 5, 29));

        when(recipeRepository.findActiveRecipe(product.getId(), lot.getProductionDate()))
                .thenReturn(Optional.of(recipe));
        when(stockLotRepository.findAvailableLotsForFifo(flour.getId(), branch.getId()))
                .thenReturn(List.of(thinLot));
        when(productionLotRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(allocationLogRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // Act
        FifoEngine.FifoResult result = fifoEngine.allocate(lot, branch);

        // Assert — PENDING (không throw exception — hệ thống cho phép âm kho)
        assertThat(result.hasPending()).isTrue();
        assertThat(result.costStatus()).isEqualTo(LotCostStatus.PENDING);
        assertThat(result.pendingIngredients()).contains(flour.getCode());

        // Cost vẫn có giá trị từ phần đã có kho (200g × 0.02 / 10 = 0.4)
        assertThat(result.costPerUnit())
                .isEqualByComparingTo(new BigDecimal("0.400000"));

        // Lô kho cũng bị trừ hết
        assertThat(thinLot.getQtyRemaining()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(thinLot.getIsDepleted()).isTrue();

        // Ghi log 2 lần: 1 cho phần đã dùng, 1 cho phần PENDING (unitPrice=0)
        verify(allocationLogRepository, times(2)).save(any(FifoAllocationLog.class));

        // Lot được save với PENDING
        ArgumentCaptor<ProductionLot> captor = ArgumentCaptor.forClass(ProductionLot.class);
        verify(productionLotRepository).save(captor.capture());
        assertThat(captor.getValue().getCostStatus()).isEqualTo(LotCostStatus.PENDING);
    }

    // =========================================================================
    //  Scenario 4 — Không tìm thấy công thức
    // =========================================================================

    @Test
    @DisplayName("S4: Không có recipe → FifoResult.noCost(), cost = 0, status PENDING")
    void allocate_noRecipeFound_returnsNoCost() {
        ProductionLot lot = productionLot("LOT-20260530-BMM-004", product, 5,
                LocalDate.of(2026, 5, 30));

        when(recipeRepository.findActiveRecipe(product.getId(), lot.getProductionDate()))
                .thenReturn(Optional.empty());
        when(productionLotRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // Act
        FifoEngine.FifoResult result = fifoEngine.allocate(lot, branch);

        // Assert
        assertThat(result.costPerUnit()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.hasPending()).isTrue();
        assertThat(result.pendingIngredients()).contains("NO_RECIPE");

        // Không được trừ kho
        verify(stockLotRepository, never()).findAvailableLotsForFifo(any(), any());
        verify(allocationLogRepository, never()).save(any());
    }

    // =========================================================================
    //  Scenario 5 — Recalculate PENDING → CONFIRMED sau backdate nhập kho
    // =========================================================================

    @Test
    @DisplayName("S5: Recalculate sau backdate — PENDING lot được cập nhật cost chính xác")
    void recalculate_afterBackdateImport_updatesPendingLotCost() {
        // Setup: 1 lô PENDING chờ recalculate
        ProductionLot pendingLot = productionLot("LOT-20260528-BMM-001", product, 10,
                LocalDate.of(2026, 5, 28));
        pendingLot.setCostPerUnit(BigDecimal.ZERO);
        pendingLot.setCostStatus(LotCostStatus.PENDING);

        // Allocation log của lô PENDING: 500g bột, unitPrice=0 (vì âm kho lúc đó)
        FifoAllocationLog pendingAlloc = FifoAllocationLog.builder()
                .productionLot(pendingLot)
                .ingredient(flour)
                .qtyAllocated(new BigDecimal("500"))
                .unitPrice(BigDecimal.ZERO)
                .costContribution(BigDecimal.ZERO)
                .isRecalculated(false)
                .build();
        setId(pendingAlloc, uuid("alloc-pending-1"));

        // Sau khi backdate nhập: có 1000g @ 0.018 VNĐ/g
        IngredientStockLot backdatedLot = stockLot(flour, new BigDecimal("1000"),
                new BigDecimal("0.018000"), LocalDate.of(2026, 5, 27)); // backdate

        when(productionLotRepository.findPendingCostLots())
                .thenReturn(List.of(pendingLot));
        when(allocationLogRepository.findAllByProductionLotIdAndIsRecalculatedFalse(pendingLot.getId()))
                .thenReturn(List.of(pendingAlloc));
        when(stockLotRepository.findAvailableLotsForFifo(flour.getId(), branch.getId()))
                .thenReturn(List.of(backdatedLot));
        when(allocationLogRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // Act
        int recalcCount = fifoEngine.recalculatePendingLots(flour.getId(), branch);

        // Assert — 1 lô được recalculate
        assertThat(recalcCount).isEqualTo(1);

        // Allocation log được đánh dấu isRecalculated=true
        assertThat(pendingAlloc.getIsRecalculated()).isTrue();

        // updateCost được gọi với cost mới: 500g × 0.018 / 10 cái = 0.9 VNĐ/cái
        verify(productionLotRepository).updateCost(
                eq(pendingLot.getId()),
                argThat(cost -> cost.compareTo(new BigDecimal("0.900000")) == 0)
        );
    }

    // =========================================================================
    //  Scenario 6 — Semi-product trong công thức
    // =========================================================================

    @Test
    @DisplayName("S6: Semi-product — cost on-the-fly qua CostCalculationService, không trừ kho NL")
    void allocate_withSemiProduct_usesCostCalculationService() {
        // Công thức: 1 cái dùng 30g phôi Bột Viên Trắng
        // CostCalculationService trả về 15,000đ/kg
        // Cost/unit = 30g / 1000 * 15000 / 20 cái = 0.45 VNĐ/cái

        Recipe recipe = recipeWith(product,
                recipeLineSemi(doughBase, new BigDecimal("30"), RecipeLineType.PHOI));

        ProductionLot lot = productionLot("LOT-20260530-BMM-006", product, 20,
                LocalDate.of(2026, 5, 30));

        when(recipeRepository.findActiveRecipe(product.getId(), lot.getProductionDate()))
                .thenReturn(Optional.of(recipe));
        when(costCalculationService.calculateCostPerKg(doughBase, branch))
                .thenReturn(new BigDecimal("15000.00"));
        when(productionLotRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // Act
        FifoEngine.FifoResult result = fifoEngine.allocate(lot, branch);

        // Assert — cost tính đúng on-the-fly
        // 30g / 1000 * 15000 / 20 cái = 0.45
        assertThat(result.hasPending()).isFalse();
        assertThat(result.costPerUnit())
                .isEqualByComparingTo(new BigDecimal("0.450000"));

        // Không được trừ kho nguyên liệu trực tiếp
        verify(stockLotRepository, never()).findAvailableLotsForFifo(any(), any());
    }

    // =========================================================================
    //  Scenario 7 — Nhiều recipe lines: ingredient + semi-product
    // =========================================================================

    @Test
    @DisplayName("S7: Mixed recipe — tổng cost = ingredient cost + semi-product cost")
    void allocate_mixedRecipeLines_sumsCostCorrectly() {
        // Công thức bánh kem:
        //   Line 1: 200g phôi Bột Viên Trắng @ 15,000đ/kg  → 200/1000 × 15000 = 3,000đ
        //   Line 2:  10g bơ Anchor (ingredient) @ 0.04đ/g   → 10 × 0.04 = 0.4đ
        // Sản xuất: 1 cái → cost/unit = 3000.4 VNĐ
        //
        // (giá trị VNĐ/gram thấp hơn thực tế để số test dễ đọc)

        Recipe recipe = recipeWith(product,
                recipeLineSemi(doughBase, new BigDecimal("200"), RecipeLineType.PHOI),
                recipeLine(butter, new BigDecimal("10"), RecipeLineType.TRANG_TRI));

        ProductionLot lot = productionLot("LOT-20260530-BMM-007", product, 1,
                LocalDate.of(2026, 5, 30));

        IngredientStockLot butterLot = stockLot(butter, new BigDecimal("500"),
                new BigDecimal("0.040000"), LocalDate.of(2026, 5, 28));

        when(recipeRepository.findActiveRecipe(product.getId(), lot.getProductionDate()))
                .thenReturn(Optional.of(recipe));
        when(costCalculationService.calculateCostPerKg(doughBase, branch))
                .thenReturn(new BigDecimal("15000.00"));
        when(stockLotRepository.findAvailableLotsForFifo(butter.getId(), branch.getId()))
                .thenReturn(List.of(butterLot));
        when(productionLotRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(allocationLogRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // Act
        FifoEngine.FifoResult result = fifoEngine.allocate(lot, branch);

        // Assert — tổng cost = 3000 (phôi) + 0.4 (bơ) = 3000.4
        assertThat(result.hasPending()).isFalse();
        assertThat(result.costPerUnit())
                .isEqualByComparingTo(new BigDecimal("3000.400000"));

        // Kho bơ bị trừ 10g
        assertThat(butterLot.getQtyRemaining())
                .isEqualByComparingTo(new BigDecimal("490"));

        // Log cho ingredient (bơ), không log semi-product
        verify(allocationLogRepository, times(1)).save(any(FifoAllocationLog.class));
    }

    // =========================================================================
    //  Scenario 8 — Kho trống hoàn toàn (empty list)
    // =========================================================================

    @Test
    @DisplayName("S8: Kho rỗng (empty lots) → toàn bộ PENDING, cost = 0")
    void allocate_emptyStock_allPending() {
        Recipe recipe = recipeWith(product,
                recipeLine(flour, new BigDecimal("50"), RecipeLineType.TRANG_TRI));

        ProductionLot lot = productionLot("LOT-20260530-BMM-008", product, 5,
                LocalDate.of(2026, 5, 30));

        when(recipeRepository.findActiveRecipe(product.getId(), lot.getProductionDate()))
                .thenReturn(Optional.of(recipe));
        when(stockLotRepository.findAvailableLotsForFifo(flour.getId(), branch.getId()))
                .thenReturn(Collections.emptyList()); // kho rỗng
        when(productionLotRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(allocationLogRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // Act
        FifoEngine.FifoResult result = fifoEngine.allocate(lot, branch);

        // Assert
        assertThat(result.hasPending()).isTrue();
        assertThat(result.costStatus()).isEqualTo(LotCostStatus.PENDING);
        assertThat(result.costPerUnit()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.pendingIngredients()).contains(flour.getCode());

        // Ghi 1 pending log
        verify(allocationLogRepository, times(1)).save(any(FifoAllocationLog.class));
    }

    // =========================================================================
    //  Scenario 9 — Lot consume() helper: đúng qty + isDepleted flag
    // =========================================================================

    @Test
    @DisplayName("S9: IngredientStockLot.consume() — depleted khi về đúng 0")
    void stockLot_consume_setsDepleted_whenExactlyZero() {
        IngredientStockLot lot = stockLot(flour, new BigDecimal("100"),
                new BigDecimal("0.01"), LocalDate.now());

        BigDecimal consumed = lot.consume(new BigDecimal("100"));

        assertThat(consumed).isEqualByComparingTo(new BigDecimal("100"));
        assertThat(lot.getQtyRemaining()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(lot.getIsDepleted()).isTrue();
    }

    @Test
    @DisplayName("S9b: IngredientStockLot.consume() — partial consume không set depleted")
    void stockLot_consume_partialConsume_notDepleted() {
        IngredientStockLot lot = stockLot(flour, new BigDecimal("100"),
                new BigDecimal("0.01"), LocalDate.now());

        BigDecimal consumed = lot.consume(new BigDecimal("60"));

        assertThat(consumed).isEqualByComparingTo(new BigDecimal("60"));
        assertThat(lot.getQtyRemaining()).isEqualByComparingTo(new BigDecimal("40"));
        assertThat(lot.getIsDepleted()).isFalse();
    }

    @Test
    @DisplayName("S9c: IngredientStockLot.consume() — cần nhiều hơn kho, chỉ trả tối đa qty_remaining")
    void stockLot_consume_moreThanAvailable_returnsMax() {
        IngredientStockLot lot = stockLot(flour, new BigDecimal("30"),
                new BigDecimal("0.02"), LocalDate.now());

        BigDecimal consumed = lot.consume(new BigDecimal("100")); // muốn 100, chỉ có 30

        assertThat(consumed).isEqualByComparingTo(new BigDecimal("30"));
        assertThat(lot.getQtyRemaining()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(lot.getIsDepleted()).isTrue();
    }

    // =========================================================================
    //  Builder helpers — dummy data factories
    // =========================================================================

    private Branch branch(String code, String name) {
        Branch b = new Branch();
        setId(b, uuid("branch-main"));
        b.setCode(code);
        b.setName(name);
        return b;
    }

    private Ingredient ingredient(String code, String name) {
        Ingredient i = Ingredient.builder()
                .code(code)
                .name(name)
                .baseUnit(BaseUnit.GRAM)
                .build();
        setId(i, uuid("ing-" + code));
        return i;
    }

    private SemiProduct semiProduct(String code, String name, SemiProductType type) {
        SemiProduct sp = SemiProduct.builder()
                .code(code)
                .name(name)
                .type(type)
                .totalYieldKg(new BigDecimal("14.201"))
                .build();
        setId(sp, uuid("sp-" + code));
        return sp;
    }

    private IngredientStockLot stockLot(Ingredient ing, BigDecimal qtyGram,
                                        BigDecimal unitPrice, LocalDate importDate) {
        IngredientStockLot sl = IngredientStockLot.builder()
                .ingredient(ing)
                .branch(branch)
                .importDate(importDate)
                .qtyImported(qtyGram)
                .qtyRemaining(qtyGram)
                .unitPrice(unitPrice)
                .isDepleted(false)
                .isBackdate(false)
                .build();
        setId(sl, uuid("sl-" + ing.getCode() + "-" + importDate));
        return sl;
    }

    private ProductionLot productionLot(String lotNumber, Product prod,
                                        int qty, LocalDate date) {
        ProductionLot pl = ProductionLot.builder()
                .lotNumber(lotNumber)
                .product(prod)
                .branch(branch)
                .productionDate(date)
                .expiryDate(date.plusDays(1))
                .qtyProduced(new BigDecimal(qty))
                .qtySold(BigDecimal.ZERO)
                .qtyCancelled(BigDecimal.ZERO)
                .costPerUnit(BigDecimal.ZERO)
                .costStatus(LotCostStatus.PENDING)
                .status(LotStatus.ACTIVE)
                .build();
        setId(pl, uuid("lot-" + lotNumber));
        return pl;
    }

    @SafeVarargs
    private Recipe recipeWith(Product prod, RecipeLine... lines) {
        Recipe r = Recipe.builder()
                .product(prod)
                .version(1)
                .effectiveDate(LocalDate.of(2026, 1, 1))
                .build();
        setId(r, uuid("recipe-" + prod.getCode()));
        r.setLines(new ArrayList<>(Arrays.asList(lines)));
        return r;
    }

    private RecipeLine recipeLine(Ingredient ing, BigDecimal gramPerUnit, RecipeLineType type) {
        RecipeLine rl = RecipeLine.builder()
                .ingredient(ing)
                .quantityGram(gramPerUnit)
                .lineType(type)
                .build();
        setId(rl, uuid("rl-" + ing.getCode()));
        return rl;
    }

    private RecipeLine recipeLineSemi(SemiProduct sp, BigDecimal gramPerUnit, RecipeLineType type) {
        RecipeLine rl = RecipeLine.builder()
                .semiProduct(sp)
                .quantityGram(gramPerUnit)
                .lineType(type)
                .build();
        setId(rl, uuid("rl-" + sp.getCode()));
        return rl;
    }

    // ── UUID + Reflection helpers ─────────────────────────────────────────────

    /** Tạo deterministic UUID từ string seed (dễ đọc trong logs). */
    private UUID uuid(String seed) {
        return UUID.nameUUIDFromBytes(seed.getBytes());
    }

    /**
     * Set id vào BaseEntity/BaseLogEntity dù field là private final.
     * Chỉ dùng trong test — không dùng trong production code.
     */
    private void setId(Object entity, UUID id) {
        try {
            var field = findIdField(entity.getClass());
            field.setAccessible(true);
            field.set(entity, id);
        } catch (Exception e) {
            throw new RuntimeException("Cannot set id on " + entity.getClass().getSimpleName(), e);
        }
    }

    private java.lang.reflect.Field findIdField(Class<?> clazz) {
        Class<?> current = clazz;
        while (current != null) {
            try {
                return current.getDeclaredField("id");
            } catch (NoSuchFieldException e) {
                current = current.getSuperclass();
            }
        }
        throw new RuntimeException("No id field found on " + clazz.getName());
    }
}
