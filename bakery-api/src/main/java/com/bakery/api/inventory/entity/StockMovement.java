package com.bakery.api.inventory.entity;

import java.math.BigDecimal;
import java.util.UUID;

import com.bakery.framework.entity.BaseEntity;
import com.bakery.framework.entity.MovementType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "stock_movement")
public class StockMovement extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lot_id", nullable = false)
    private StockLot lot;

    @Enumerated(EnumType.STRING)
    @Column(name = "movement_type", nullable = false, length = 20)
    private MovementType movementType;

    @Column(name = "qty", nullable = false, precision = 15, scale = 4)
    private BigDecimal qty;

    /** ID của document gốc phát sinh movement (production_request, PO...) */
    @Column(name = "ref_id")
    private UUID refId;

    /** Loại document gốc: PRODUCTION_REQUEST, PURCHASE_ORDER... */
    @Column(name = "ref_type", length = 50)
    private String refType;

    @Column(name = "note", length = 500)
    private String note;
}
