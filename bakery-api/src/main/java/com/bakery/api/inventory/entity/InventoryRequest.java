package com.bakery.api.inventory.entity;

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
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_warehouse_id")
    private Warehouse sourceWarehouse;

    /** Kho nhận hàng */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "target_warehouse_id")
    private Warehouse targetWarehouse;

    /** Nhà cung cấp (chỉ có khi PURCHASE) */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "supplier_id")
    private Supplier supplier;

    @Column(name = "note", length = 500)
    private String note;

    /**
     * Lines của phiếu. Cascade ALL + orphanRemoval cho phép save toàn bộ graph
     * chỉ qua repository.save(inventoryRequest).
     */
    @OneToMany(mappedBy = "inventoryRequest", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("sortOrder ASC")
    private List<InventoryRequestLine> lines = new ArrayList<>();
}
