/*
 * Copyright (c) 2024 Bakery Management System
 */
package com.bakery.api.master.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Đơn vị tính — master table cho tất cả đơn vị (độc lập + có tỉ lệ).
 *
 * <p>Đơn vị độc lập (Ly, Cái, Hộp, ...): chỉ tồn tại trong bảng này,
 * không có entry trong unit_conversion.
 *
 * <p>Đơn vị có tỉ lệ (G, KG, ML, L, ...): tồn tại trong bảng này
 * VÀ có các cặp tương ứng trong unit_conversion.
 */
@Getter
@Setter
@Entity
@Table(name = "unit")
public class Unit {

    @Id
    @Column(name = "code", nullable = false, length = 20)
    private String code;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "note", length = 200)
    private String note;
}
