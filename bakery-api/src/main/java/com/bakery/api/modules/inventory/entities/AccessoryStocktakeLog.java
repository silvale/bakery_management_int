package com.bakery.api.modules.inventory.entities;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;
import com.bakery.api.modules.masterdata.entities.Branch;
import com.bakery.api.modules.masterdata.entities.Ingredient;

/**
 * Lịch sử kiểm đếm phụ kiện tại Cửa hàng (SHOP branch).
 *
 * Mỗi lần nhân viên nhập số đếm thực tế, hệ thống:
 *   1. Tính theoretical = qty_on_hand_before - qty_pos_sold (POS trong kỳ)
 *   2. loss = theoretical - qty_actual (nếu > 0 → hao hụt)
 *   3. FEFO deduct loss từ ingredient_stock_lot
 *   4. Force-set ingredient_stock.qty_on_hand = qty_actual
 *   5. Lưu log này để audit và làm period_from cho lần sau
 */
@Entity
@Table(name = "accessory_stocktake_log")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class AccessoryStocktakeLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "branch_id", nullable = false)
    private Branch branch;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ingredient_id", nullable = false)
    private Ingredient ingredient;

    @Column(name = "stocktake_date", nullable = false)
    private LocalDate stocktakeDate;

    /** NULL = lần kiểm đầu tiên (tính POS từ đầu thời gian) */
    @Column(name = "period_from")
    private LocalDate periodFrom;

    @Column(name = "period_to", nullable = false)
    private LocalDate periodTo;

    /** qty_on_hand tại thời điểm TRƯỚC khi kiểm (chưa deduct POS trong kỳ) */
    @Column(name = "qty_on_hand_before", nullable = false, precision = 18, scale = 4)
    private BigDecimal qtyOnHandBefore;

    /** Tổng POS bán trong kỳ (period_from → period_to) */
    @Column(name = "qty_pos_sold", nullable = false, precision = 18, scale = 4)
    @Builder.Default
    private BigDecimal qtyPosSold = BigDecimal.ZERO;

    /** = qty_on_hand_before - qty_pos_sold */
    @Column(name = "qty_theoretical", nullable = false, precision = 18, scale = 4)
    private BigDecimal qtyTheoretical;

    /** Số nhân viên đếm thực tế trên kệ */
    @Column(name = "qty_actual", nullable = false, precision = 18, scale = 4)
    private BigDecimal qtyActual;

    /** = max(0, theoretical - actual) → hao hụt, thất thoát */
    @Column(name = "qty_loss", nullable = false, precision = 18, scale = 4)
    @Builder.Default
    private BigDecimal qtyLoss = BigDecimal.ZERO;

    /** = max(0, actual - theoretical) → thừa (có thể do đếm sai hoặc nhập trùng) */
    @Column(name = "qty_overage", nullable = false, precision = 18, scale = 4)
    @Builder.Default
    private BigDecimal qtyOverage = BigDecimal.ZERO;

    /** Code của goods_transfer ADJUSTMENT tạo tự động cho hao hụt (nếu có) */
    @Column(name = "adjustment_ref", length = 30)
    private String adjustmentRef;

    @Column(name = "note")
    private String note;

    @Column(name = "created_by", nullable = false, length = 100)
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private OffsetDateTime createdAt = OffsetDateTime.now();
}
