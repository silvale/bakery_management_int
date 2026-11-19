package com.bakery.api.cake.entity;

import java.math.BigDecimal;
import java.util.UUID;

import com.bakery.api.production.entity.DeliveryRecord;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** Số bánh theo mẫu của một phiếu giao nhận: bếp làm ra và cửa hàng thực nhận. */
@Getter
@Setter
@Entity
@Table(name = "delivery_record_design")
public class DeliveryRecordDesign {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "delivery_record_id", nullable = false)
    private DeliveryRecord deliveryRecord;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cake_design_id", nullable = false)
    private CakeDesign cakeDesign;

    @Column(name = "qty_produced", nullable = false, precision = 15, scale = 4)
    private BigDecimal qtyProduced = BigDecimal.ZERO;

    @Column(name = "qty_received", precision = 15, scale = 4)
    private BigDecimal qtyReceived;
}
