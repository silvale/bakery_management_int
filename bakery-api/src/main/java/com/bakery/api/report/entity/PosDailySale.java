package com.bakery.api.report.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Dữ liệu bán hàng raw từ máy POS, parse từ file Excel cuối ngày.
 * unit_price = total_amount / qty_sold.
 */
@Getter
@Setter
@Entity
@Table(name = "pos_daily_sale")
public class PosDailySale {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "sale_date", nullable = false)
    private LocalDate saleDate;

    /** Mã EX từ máy POS — map sang IN_CODE qua product_mapping */
    @Column(name = "ex_code", nullable = false, length = 100)
    private String exCode;

    @Column(name = "item_name", length = 255)
    private String itemName;

    @Column(name = "qty_sold", nullable = false, precision = 10, scale = 3)
    private BigDecimal qtySold;

    /** Giá bán tại thời điểm bán = total_amount / qty_sold */
    @Column(name = "unit_price", precision = 15, scale = 2)
    private BigDecimal unitPrice;

    @Column(name = "total_amount", precision = 15, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "created_by", updatable = false, length = 100)
    private String createdBy;
}
