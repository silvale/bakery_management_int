package com.bakery.api.master.entity;

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
 * Cấu hình hạn sử dụng cho từng sản phẩm.
 * Mỗi sản phẩm có đúng 1 config (item_id UNIQUE).
 *
 * <p>shelf_days = 0 → bánh tươi trong ngày, hủy cuối ngày.
 * <p>shelf_days > 0 → số ngày kể từ ngày sản xuất.
 *
 * <p>Điều kiện bảo quản được quản lý qua EX_CODE (Intelligent Barcode),
 * không lưu trong bảng này để tránh làm phức tạp query.
 */
@Getter
@Setter
@Entity
@Table(name = "product_expiry_config")
public class ProductExpiryConfig extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "item_id", nullable = false, unique = true)
    private Item item;

    /** Số ngày HSD kể từ ngày sản xuất. 0 = hủy trong ngày. */
    @Column(name = "shelf_days", nullable = false)
    private Integer shelfDays;
}
