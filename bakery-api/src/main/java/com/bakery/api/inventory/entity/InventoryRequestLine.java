package com.bakery.api.inventory.entity;

import org.hibernate.envers.Audited;
import org.hibernate.envers.RelationTargetAuditMode;

import java.math.BigDecimal;

import com.bakery.api.master.entity.Item;
import com.bakery.api.master.entity.ItemPackaging;
import com.bakery.framework.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Audited
@Entity
@Table(name = "inventory_request_line")
public class InventoryRequestLine extends BaseEntity {

    @Audited(targetAuditMode = RelationTargetAuditMode.NOT_AUDITED)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "inventory_request_id", nullable = false)
    private InventoryRequest inventoryRequest;

    @Audited(targetAuditMode = RelationTargetAuditMode.NOT_AUDITED)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "item_id", nullable = false)
    private Item item;

    @Column(name = "quantity", nullable = false, precision = 15, scale = 4)
    private BigDecimal quantity;

    @Column(name = "unit", nullable = false, length = 30)
    private String unit;

    /** Giá mua thực tế — chỉ điền khi PURCHASE */
    @Column(name = "unit_cost", precision = 15, scale = 4)
    private BigDecimal unitCost;

    /**
     * Đóng gói sử dụng khi mua (Bao 10kg, Thùng 12 chai...).
     * null nếu không dùng packaging (xuất kho nội bộ, điều chỉnh...).
     */
    @Audited(targetAuditMode = RelationTargetAuditMode.NOT_AUDITED)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "packaging_id")
    private ItemPackaging packaging;

    /**
     * Số lượng mua theo đơn vị đóng gói (số bao, số thùng...).
     * quantity = purchaseQty × packaging.qtyPerPack (BE tự tính khi tạo/approve).
     */
    @Column(name = "purchase_qty", precision = 15, scale = 4)
    private BigDecimal purchaseQty;

    /**
     * Số thực nhận theo line.unit (PURCHASE). null cho tới khi phiếu được xác nhận;
     * 0 ≤ receivedQty ≤ quantity (không cho giao dư).
     */
    @Column(name = "received_qty", precision = 15, scale = 4)
    private BigDecimal receivedQty;

    // ── ADJUSTMENT (08): quantity = countedQty − systemQty ───────
    /** Tồn hệ thống của item tại kho lúc lưu phiếu. */
    @Column(name = "system_qty", precision = 15, scale = 4)
    private BigDecimal systemQty;

    /** Số đếm thực tế. */
    @Column(name = "counted_qty", precision = 15, scale = 4)
    private BigDecimal countedQty;

    /** code_value ADJUSTMENT_REASON. */
    @Column(name = "reason_code", length = 30)
    private String reasonCode;

    /** Giá trị điều chỉnh, tính và lưu lúc duyệt: tăng dương, giảm âm. */
    @Column(name = "amount", precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(name = "sort_order")
    private Integer sortOrder;

    @Column(name = "note", length = 500)
    private String note;
}
