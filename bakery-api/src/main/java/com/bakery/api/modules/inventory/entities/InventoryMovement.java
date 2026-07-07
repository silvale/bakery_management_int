package com.bakery.api.modules.inventory.entities;

import com.bakery.api.framework.enums.ReferenceType;
import com.bakery.api.framework.enums.TransactionType;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;
import com.bakery.api.modules.masterdata.entities.Branch;
import com.bakery.api.modules.masterdata.entities.Ingredient;
import com.bakery.api.modules.masterdata.entities.Product;

/**
 * Ledger ghi nhận toàn bộ giao dịch tồn kho (NL + accessories + thành phẩm).
 *
 * Mọi thay đổi tồn kho đều phải có ít nhất 1 record InventoryMovement tương ứng.
 * Đây là nguồn truth để reconstruct lịch sử tồn kho bất kỳ ngày nào.
 *
 * ── transactionType + referenceType ─────────────────────────────────────────
 *   IMPORT  + DAILY               — nhập hàng ngày từ NCC
 *   IMPORT  + PURCHASE_ORDER      — nhập theo đơn PO
 *   IMPORT  + DAMAGE_REPLACEMENT  — NCC bù hàng hỏng
 *   IMPORT  + TRANSFER_IN         — nhận từ kho khác (goods_transfer COMPLETED)
 *   EXPORT  + TO_KITCHEN          — xuất sang KHO_BEP
 *   EXPORT  + TO_STORE            — xuất accessories sang cửa hàng
 *   EXPORT  + TRANSFER_OUT        — xuất sang kho khác (goods_transfer COMPLETED)
 *   RETURN  + TO_STORAGE          — trả về kho tổng
 *   RETURN  + TO_SUPPLIER         — trả NCC
 *   RETURN  + INVALID_LIST/QUALITY/QUANTITY — trả do sai
 *   DISCARD + DAMAGED             — huỷ hàng hư
 *   DISCARD + EXPIRED             — huỷ hàng hết hạn
 *   ADJUSTMENT + INCREASE/DECREASE — điều chỉnh kiểm kê
 *   STOCK_COUNT + END_OF_DAY      — kiểm đếm cuối ngày từ POS/báo cáo
 *   STOCK_COUNT + SPOT_CHECK      — kiểm đột xuất
 *
 * ── sourceType + sourceId ────────────────────────────────────────────────────
 *   Trỏ về document gốc sinh ra giao dịch này:
 *   PURCHASE_ORDER | GOODS_TRANSFER | WRITE_OFF | ADJUSTMENT | RETURN | PRODUCTION | STOCK_COUNT_REPORT
 *
 * ── referenceCode ─────────────────────────────────────────────────────────────
 *   Mã human-readable của document gốc (VD: TRF-20260701-001) để hiển thị UI
 *   mà không cần join thêm bảng.
 */
@Entity
@Table(name = "inventory_movement")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InventoryMovement {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "branch_id", nullable = false)
    private Branch branch;

    /** INGREDIENT | PRODUCT */
    @Column(name = "item_type", nullable = false, length = 20)
    private String itemType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ingredient_id")
    private Ingredient ingredient;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id")
    private Product product;

    /** ID của lô bị ảnh hưởng (IngredientStockLot) */
    @Column(name = "lot_id")
    private UUID lotId;

    /** Loại giao dịch — xem Javadoc trên class */
    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type", nullable = false, length = 20)
    private TransactionType transactionType;

    /** Sub-type của giao dịch — xem Javadoc trên class */
    @Enumerated(EnumType.STRING)
    @Column(name = "reference_type", nullable = false, length = 30)
    private ReferenceType referenceType;

    /** Số lượng (dương = nhập, âm = xuất) */
    @Column(name = "qty", nullable = false, precision = 18, scale = 4)
    private BigDecimal qty;

    @Column(name = "unit", nullable = false, length = 20)
    private String unit;

    /** Loại document gốc: PURCHASE_ORDER | GOODS_TRANSFER | WRITE_OFF | ADJUSTMENT | RETURN | PRODUCTION | STOCK_COUNT_REPORT */
    @Column(name = "source_type", length = 50)
    private String sourceType;

    /** ID của document gốc (UUID) */
    @Column(name = "source_id")
    private UUID sourceId;

    /** Mã human-readable của document gốc — hiển thị trực tiếp trên UI (VD: TRF-20260701-001) */
    @Column(name = "reference_code", length = 50)
    private String referenceCode;

    /** Ghi chú tự do — optional */
    @Column(name = "note", columnDefinition = "TEXT")
    private String note;

    @Column(name = "created_by", nullable = false, length = 100)
    private String createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private OffsetDateTime createdAt = OffsetDateTime.now();
}
