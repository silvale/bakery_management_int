package com.bakery.api.inventory.entity;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.bakery.api.master.entity.Item;
import com.bakery.api.master.entity.Supplier;
import com.bakery.api.master.entity.Warehouse;
import com.bakery.framework.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "stock_lot")
public class StockLot extends BaseEntity {

    /**
     * Vật tư của lot này — Ingredient hoặc SemiProduct.
     * FK duy nhất trỏ về bảng item.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "item_id", nullable = false)
    private Item item;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "supplier_id")
    private Supplier supplier;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "warehouse_id")
    private Warehouse warehouse;

    @Column(name = "qty_initial", nullable = false, precision = 15, scale = 4)
    private BigDecimal qtyInitial;

    @Column(name = "qty_remaining", nullable = false, precision = 15, scale = 4)
    private BigDecimal qtyRemaining;

    @Column(name = "unit_cost", nullable = false, precision = 15, scale = 4)
    private BigDecimal unitCost;

    @Column(name = "received_date", nullable = false)
    private LocalDate receivedDate;

    @Column(name = "expiry_date")
    private LocalDate expiryDate;
}
