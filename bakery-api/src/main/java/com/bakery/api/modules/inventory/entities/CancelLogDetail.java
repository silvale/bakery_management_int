package com.bakery.api.modules.inventory.entities;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import com.bakery.api.framework.BaseLogEntity;
import com.bakery.api.modules.production.entities.ProductionLot;

/**
 * Chi tiết từng lô bị trừ trong 1 lần hủy.
 * Dùng để tính cancelled_cost chính xác theo giá lô thực tế.
 */
@Entity
@Table(name = "cancel_log_detail")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class CancelLogDetail extends BaseLogEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cancel_log_id", nullable = false)
    private CancelLog cancelLog;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "production_lot_id", nullable = false)
    private ProductionLot productionLot;

    /** Số lượng trừ từ lô này */
    @Column(name = "qty_cancelled", nullable = false, precision = 12, scale = 3)
    private BigDecimal qtyCancelled;

    /** Cost/cái của lô này — copy từ production_lot.cost_per_unit */
    @Column(name = "cost_per_unit", nullable = false, precision = 18, scale = 6)
    private BigDecimal costPerUnit;

    /**
     * Computed: qty_cancelled × cost_per_unit
     * insertable/updatable = false
     */
    @Column(name = "cancelled_cost", insertable = false, updatable = false, precision = 18, scale = 4)
    private BigDecimal cancelledCost;

    /** Copy từ production_lot.expiry_date để dễ audit */
    @Column(name = "lot_expiry_date", nullable = false)
    private LocalDate lotExpiryDate;
}
