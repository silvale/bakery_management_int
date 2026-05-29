package com.bakery.common.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * Tiền tố sản phẩm (Prefix) — Admin tự định nghĩa.
 *
 * Mapping: Prefix → Master Product (IN_CODE)
 *
 * Ví dụ:
 *   BM   → Bánh Mì Thường
 *   BL   → Bông Lan Phú Sĩ
 *   BKST → Bánh Kem Sữa Tươi
 *   SU   → Su Kem
 *
 * EX_CODE từ máy POS:  Prefix + ThứSX(1 chữ số) + Random
 *   BM2546892  → prefix=BM, thứ=2 (T2), random=546892
 *   BKST5123   → prefix=BKST, thứ=5 (T5), random=123
 *
 * Thứ sản xuất: 2=T2, 3=T3, 4=T4, 5=T5, 6=T6, 7=T7, 8=CN
 *
 * Decode logic: ExCodeDecoderService.decode(exCode)
 *   1. Duyệt tất cả prefix theo độ dài giảm dần (longest match first)
 *   2. Khi match → lấy ký tự ngay sau prefix = thứ sản xuất
 *   3. Tra cứu ngày SX gần nhất trong HSD
 */
@Entity
@Table(
    name = "product_prefix",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_product_prefix_code",
        columnNames = {"prefix"}
    )
)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ProductPrefix extends BaseEntity {

    /** Tiền tố: BM, BL, BKST, SU... (chữ HOA, không dấu) */
    @Column(name = "prefix", nullable = false, length = 20)
    private String prefix;

    /** Mô tả admin: "Bánh Mì", "Bông Lan Phú Sĩ"... */
    @Column(name = "description", length = 200)
    private String description;

    /**
     * Master product tương ứng (IN_CODE).
     * Mỗi prefix map đúng 1 sản phẩm.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    @Column(name = "note", columnDefinition = "TEXT")
    private String note;
}
