package com.bakery.api.inventory.entity;

import org.hibernate.envers.Audited;
import org.hibernate.envers.RelationTargetAuditMode;
import org.hibernate.envers.NotAudited;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import com.bakery.api.master.entity.Supplier;
import com.bakery.api.master.entity.Warehouse;
import com.bakery.framework.entity.BaseEntity;
import com.bakery.framework.entity.InventoryRequestType;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Phiếu yêu cầu nhập hàng hoặc điều chuyển kho.
 *
 * <p>PURCHASE flow: Tạo phiếu → PENDING_APPROVAL → Duyệt → APPROVED
 *   → sinh StockLot + StockMovement(IN)
 *
 * <p>TRANSFER flow: Tạo phiếu → PENDING_APPROVAL → Kho nguồn xác nhận gửi → READY
 *   → Kho đích bấm nhận → APPROVED
 *   → sinh StockMovement(OUT) tại nguồn + StockMovement(IN) tại đích
 */
@Getter
@Setter
@Audited
@Entity
@Table(name = "inventory_request")
public class InventoryRequest extends BaseEntity {

    /** Mã phiếu tự sinh, e.g., PO-20260708-001 hoặc TR-20260708-001 */
    @Column(name = "code", nullable = false, unique = true, length = 50)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(name = "request_type", nullable = false, length = 20)
    private InventoryRequestType requestType;

    @Column(name = "request_date", nullable = false)
    private LocalDate requestDate;

    @Column(name = "expected_delivery_date")
    private LocalDate expectedDeliveryDate;

    /** Kho xuất (null nếu PURCHASE — hàng đến từ NCC) */
    @Audited(targetAuditMode = RelationTargetAuditMode.NOT_AUDITED)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_warehouse_id")
    private Warehouse sourceWarehouse;

    /** Kho nhận hàng */
    @Audited(targetAuditMode = RelationTargetAuditMode.NOT_AUDITED)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "target_warehouse_id")
    private Warehouse targetWarehouse;

    /** Nhà cung cấp (chỉ có khi PURCHASE) */
    @Audited(targetAuditMode = RelationTargetAuditMode.NOT_AUDITED)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "supplier_id")
    private Supplier supplier;

    @Column(name = "note", length = 500)
    private String note;

    /** Chỉ có ở PURCHASE: UNPAID | PAID. Phiếu loại khác để null. */
    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status", length = 10)
    private PaymentStatus paymentStatus;

    @Column(name = "paid_at")
    private Instant paidAt;

    @Column(name = "paid_by", length = 100)
    private String paidBy;

    /** Ngày thực nhận (giờ Asia/Ho_Chi_Minh), chỉ PURCHASE đã nhận. Tháng của báo cáo nhập hàng tính theo ngày này. */
    @Column(name = "received_date")
    private LocalDate receivedDate;

    @Column(name = "received_by", length = 100)
    private String receivedBy;

    /** 24: phiếu SX liên quan (PICK/WRITE_OFF gắn phiếu); lô lấy về được giữ cho phiếu này. */
    @NotAudited
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "production_request_id")
    private com.bakery.api.production.entity.ProductionRequest productionRequest;

    /** 24: code_value PICK_REASON (chỉ PICK). */
    @NotAudited
    @Column(name = "pick_reason", length = 30)
    private String pickReason;

    /** 24: phiên ngày đã ghi nhận phiếu bảng tạm. */
    @NotAudited
    @Column(name = "session_id")
    private java.util.UUID sessionId;

    /** Đặt ở @PrePersist vì phiếu sinh từ generate-purchase không đi qua beforeCreate. */
    @PrePersist
    void initPaymentStatus() {
        if (requestType == InventoryRequestType.PURCHASE && paymentStatus == null) {
            paymentStatus = PaymentStatus.UNPAID;
        }
    }

    /**
     * Lines của phiếu. Cascade ALL + orphanRemoval cho phép save toàn bộ graph
     * chỉ qua repository.save(inventoryRequest).
     */
    @NotAudited
    @OneToMany(mappedBy = "inventoryRequest", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("sortOrder ASC")
    private List<InventoryRequestLine> lines = new ArrayList<>();
}
