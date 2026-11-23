package com.bakery.api.production.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.bakery.api.inventory.entity.StockLot;
import com.bakery.api.master.entity.Item;
import com.bakery.api.master.entity.Warehouse;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * 20/D3: bảng tạm "Đang sản xuất" của phiếu SX bán thành phẩm. Mỗi dòng = một lô nguồn đã lấy
 * (giữ giá vốn lô, trả lại đúng lô). Còn trong bảng tạm = {@link #remaining()}.
 */
@Getter
@Setter
@Entity
@Table(name = "production_material")
public class ProductionMaterial {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "production_request_id", nullable = false, updatable = false)
    private UUID productionRequestId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "item_id", nullable = false, updatable = false)
    private Item item;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_lot_id", nullable = false, updatable = false)
    private StockLot sourceLot;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_warehouse_id", nullable = false, updatable = false)
    private Warehouse sourceWarehouse;

    @Column(name = "unit_cost", precision = 15, scale = 4)
    private BigDecimal unitCost;

    @Column(name = "qty_picked", nullable = false, precision = 15, scale = 4)
    private BigDecimal qtyPicked;

    @Column(name = "qty_consumed", nullable = false, precision = 15, scale = 4)
    private BigDecimal qtyConsumed = BigDecimal.ZERO;

    @Column(name = "qty_returned", nullable = false, precision = 15, scale = 4)
    private BigDecimal qtyReturned = BigDecimal.ZERO;

    @Column(name = "qty_written_off", nullable = false, precision = 15, scale = 4)
    private BigDecimal qtyWrittenOff = BigDecimal.ZERO;

    @Column(name = "picked_by", nullable = false, length = 100)
    private String pickedBy;

    @Column(name = "picked_at", nullable = false)
    private Instant pickedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "created_by", length = 100, updatable = false)
    private String createdBy;

    @Column(name = "updated_by", length = 100)
    private String updatedBy;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() { updatedAt = Instant.now(); }

    /** Còn trong bảng tạm = lấy − đã dùng − đã trả − hao hụt. */
    public BigDecimal remaining() {
        return qtyPicked.subtract(qtyConsumed).subtract(qtyReturned).subtract(qtyWrittenOff);
    }
}
