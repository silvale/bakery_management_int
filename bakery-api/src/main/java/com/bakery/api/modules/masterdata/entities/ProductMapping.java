package com.bakery.api.modules.masterdata.entities;

import jakarta.persistence.*;
import lombok.*;
import com.bakery.api.framework.BaseAdminEntity;

/**
 * Mapping 1 Master Product → nhiều SKU POS.
 *
 * Master Code (nội bộ, không dấu): BANH_KEM_BAP
 *   → SKU BKBAP2 (Thứ 2) + add-on recipe A
 *   → SKU BKBAP3 (Thứ 3) + add-on recipe B
 *   → SKU BKBAP4 (Thứ 4) + base recipe only
 *
 * production_day: parse từ ký tự cuối sku_code
 *   2=T2, 3=T3...7=T7, 0=CN, NULL=làm mỗi ngày
 */
@Entity
@Table(name = "product_mapping")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ProductMapping extends BaseAdminEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    /** Mã SKU từ POS. Dùng để map khi đọc file POS export. */
    @Column(name = "sku_code", nullable = false, unique = true, length = 50)
    private String skuCode;

    /** POS | LEGACY | MANUAL */
    @Column(name = "sku_source", nullable = false, length = 50)
    @Builder.Default
    private String skuSource = "POS";

    /**
     * Thứ sản xuất — parse từ ký tự cuối sku_code.
     * 2=T2, 3=T3...7=T7, 0=CN, NULL=không giới hạn.
     * DB trigger tự set nếu null.
     */
    @Column(name = "production_day")
    private Short productionDay;

    /**
     * Add-on recipe riêng cho SKU này (trang trí/phụ gia).
     * NULL = chỉ dùng base recipe của master product.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recipe_addon_id")
    private Recipe recipeAddon;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    @Column(name = "note", columnDefinition = "TEXT")
    private String note;

    // ── Helpers ───────────────────────────────────────────────

    /**
     * Parse production_day từ ký tự cuối sku_code.
     * Gọi khi insert nếu production_day chưa được set.
     */
    public static Short extractProductionDay(String skuCode) {
        if (skuCode == null || skuCode.isBlank()) return null;
        char lastChar = skuCode.charAt(skuCode.length() - 1);
        if (lastChar >= '0' && lastChar <= '7') {
            return (short) Character.getNumericValue(lastChar);
        }
        return null;
    }

    /** Kiểm tra SKU này có sản xuất vào ngày trong tuần không */
    public boolean isProducedOnDay(java.time.DayOfWeek dayOfWeek) {
        if (productionDay == null) return true; // làm mỗi ngày
        int day = dayOfWeek.getValue(); // 1=T2...7=CN
        if (day == 7) day = 0;         // CN = 0
        else day++;                     // T2=2...T7=7
        return productionDay == day;
    }
}
