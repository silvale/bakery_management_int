package com.bakery.api.cake.entity;

import java.math.BigDecimal;
import java.util.UUID;

import com.bakery.api.master.entity.Item;
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

/** Phụ kiện (nến, hình trang trí...) cần cho 1 bánh của mẫu, theo đơn vị gốc của item. */
@Getter
@Setter
@Entity
@Table(name = "cake_design_accessory")
public class CakeDesignAccessory {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cake_design_id", nullable = false)
    private CakeDesign cakeDesign;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "item_id", nullable = false)
    private Item item;

    @Column(name = "qty", nullable = false, precision = 15, scale = 4)
    private BigDecimal qty;
}
