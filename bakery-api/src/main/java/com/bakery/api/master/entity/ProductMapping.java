package com.bakery.api.master.entity;

import java.math.BigDecimal;

import com.bakery.framework.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Map EX_CODE từ POS → item nội bộ.
 * 1 item (PRODUCT hoặc phụ kiện) có thể có nhiều EX_CODE.
 *
 * <p>Giá bán lưu tại đây (không phải trên item) vì cùng 1 sản phẩm
 * có thể có nhiều mức giá khác nhau tùy EX_CODE (trang trí theo ngày).
 */
@Getter
@Setter
@Entity
@Table(name = "product_mapping")
public class ProductMapping extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "item_id", nullable = false)
    private Item item;

    /** Mã từ POS — unique toàn hệ thống */
    @Column(name = "ex_code", nullable = false, unique = true, length = 50)
    private String exCode;

    /** Giá bán theo EX_CODE — có thể khác nhau tùy mức trang trí/ngày */
    @Column(name = "selling_price", precision = 15, scale = 2)
    private BigDecimal sellingPrice;

    @Column(name = "note", length = 200)
    private String note;
}
