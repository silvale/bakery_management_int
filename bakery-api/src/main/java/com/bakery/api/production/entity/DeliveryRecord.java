package com.bakery.api.production.entity;

import java.math.BigDecimal;
import java.time.Instant;

import com.bakery.framework.entity.BaseEntity;
import com.bakery.framework.entity.DeliveryStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Phiếu giao nhận bánh thành phẩm từ bếp → cửa hàng.
 * Tạo tự động khi bếp bấm "Completed" trên ProductionRequestLine.
 * Shop bấm "Xác nhận nhận" để confirm qtyReceived.
 */
@Getter
@Setter
@Entity
@Table(name = "delivery_record")
public class DeliveryRecord extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "production_request_line_id", nullable = false, unique = true)
    private ProductionRequestLine productionRequestLine;

    /** Số lượng bếp thực tế làm ra */
    @Column(name = "qty_produced", nullable = false, precision = 15, scale = 4)
    private BigDecimal qtyProduced;

    /** Số lượng shop xác nhận nhận được */
    @Column(name = "qty_received", precision = 15, scale = 4)
    private BigDecimal qtyReceived;

    /** qtyProduced - qtyReceived (tính khi shop confirm) */
    @Column(name = "discrepancy", precision = 15, scale = 4)
    private BigDecimal discrepancy;

    @Enumerated(EnumType.STRING)
    @Column(name = "delivery_status", nullable = false, length = 20)
    private DeliveryStatus deliveryStatus = DeliveryStatus.READY;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    @Column(name = "confirmed_by", length = 100)
    private String confirmedBy;

    @Column(name = "note", length = 500)
    private String note;
}
