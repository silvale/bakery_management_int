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
 * Map EX_CODE từ POS → item nội bộ.
 * 1 item (PRODUCT hoặc phụ kiện) có thể có nhiều EX_CODE.
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

    /**
     * Ngày SX cố định của SKU này.
     * 0=mỗi ngày, 2=T2, 3=T3, 4=T4, 5=T5, 6=T6, 7=T7, 8=CN, null=không ràng buộc.
     * Dùng trong ExCodeDecoderService khi dayChar không đủ để xác định ngày SX.
     */
    @Column(name = "production_day")
    private Integer productionDay;

    @Column(name = "note", length = 200)
    private String note;
}
