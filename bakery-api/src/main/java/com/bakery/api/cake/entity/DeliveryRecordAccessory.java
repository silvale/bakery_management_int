package com.bakery.api.cake.entity;

import java.math.BigDecimal;
import java.util.UUID;

import com.bakery.api.master.entity.Item;
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

/** Phụ kiện bếp thực dùng cho một mẫu của phiếu giao nhận (02/BE-13). Đã trừ kho lúc bếp Hoàn thành. */
@Getter
@Setter
@Entity
@Table(name = "delivery_record_accessory")
public class DeliveryRecordAccessory {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "delivery_record_id", nullable = false)
    private DeliveryRecord deliveryRecord;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cake_design_id")
    private CakeDesign cakeDesign;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "item_id", nullable = false)
    private Item item;

    /** Bộ phụ kiện của mẫu × số bánh; 0 nếu bếp thêm ngoài bộ. */
    @Column(name = "qty_default", nullable = false, precision = 15, scale = 4)
    private BigDecimal qtyDefault = BigDecimal.ZERO;

    @Column(name = "qty_used", nullable = false, precision = 15, scale = 4)
    private BigDecimal qtyUsed;

    @Column(name = "unit", length = 30)
    private String unit;
}
