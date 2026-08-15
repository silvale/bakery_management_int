/*
 * Copyright (c) 2024 Bakery Management System
 */
package com.bakery.api.master.entity;

import java.math.BigDecimal;
import java.time.Instant;

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
import org.hibernate.annotations.UpdateTimestamp;

/**
 * Cấu hình đóng gói của một Item.
 *
 * <p>Một Item có thể có nhiều packaging (Bao 10kg, Bao 20kg, Bao 50kg).
 * {@code qtyPerPack} luôn tính theo {@code item.unit} (đơn vị canonical).
 *
 * <p>Ví dụ: Bột Tarta (unit=KG)
 * <ul>
 *   <li>BAO_10 / Bao 10kg / qtyPerPack=10</li>
 *   <li>BAO_20 / Bao 20kg / qtyPerPack=20 / isDefault=true</li>
 * </ul>
 */
@Getter
@Setter
@Entity
@Table(name = "item_packaging")
public class ItemPackaging {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private java.util.UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "item_id", nullable = false)
    private Item item;

    /** Mã đóng gói — unique per item. Ví dụ: BAO_10, BAO_20, THUNG, CHAI */
    @Column(name = "code", nullable = false, length = 50)
    private String code;

    /** Tên hiển thị. Ví dụ: Bao 10kg, Thùng 12 chai */
    @Column(name = "name", nullable = false, length = 100)
    private String name;

    /**
     * Số lượng tính theo {@code item.unit} (đơn vị canonical) trong 1 pack.
     * Ví dụ: BAO_10 của Bột Tarta (unit=KG) → qtyPerPack = 10.
     */
    @Column(name = "qty_per_pack", nullable = false, precision = 15, scale = 4)
    private BigDecimal qtyPerPack;

    /** Packaging mặc định khi tạo phiếu mua hàng. */
    @Column(name = "is_default", nullable = false)
    private boolean isDefault = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
