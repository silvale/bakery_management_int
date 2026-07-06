package com.bakery.common.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Trả hàng hỏng về nhà cung cấp (Case 1: NCC giao hàng lỗi).
 *
 * Code: SRT-{yyyyMMdd}-{seq}  VD: SRT-20260701-001
 *
 * Quy trình Case 1 — NCC lỗi:
 *   1. Phát hiện hàng hỏng → tạo InventoryWriteOff (PENDING)
 *   2. Chính duyệt WriteOff → tạo SupplierReturn liên kết write_off_id
 *   3. Gửi hàng về NCC (status = SENT_TO_SUPPLIER)
 *   4. NCC giao hàng thay thế → tạo PurchaseOrder mới (replacement_po_id)
 *      → status = REPLACEMENT_RECEIVED
 *
 * (Case 2 — kho bảo quản sai → chỉ WriteOff, không có SupplierReturn)
 */
@Entity
@Table(name = "supplier_return")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SupplierReturn {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "code", nullable = false, unique = true, length = 30)
    private String code;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "supplier_id", nullable = false)
    private Supplier supplier;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "original_po_id", nullable = false)
    private PurchaseOrder originalPo;

    /** WriteOff tương ứng đã được duyệt */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "write_off_id")
    private InventoryWriteOff writeOff;

    @Column(name = "return_date", nullable = false)
    private LocalDate returnDate;

    @Column(name = "reason", nullable = false, columnDefinition = "TEXT")
    private String reason;

    /** PENDING | SENT_TO_SUPPLIER | REPLACEMENT_RECEIVED | WRITTEN_OFF */
    @Column(name = "status", nullable = false, length = 30)
    @Builder.Default
    private String status = "PENDING";

    /** PO bù hàng từ NCC (có sau khi NCC giao hàng thay thế) */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "replacement_po_id")
    private PurchaseOrder replacementPo;

    @Column(name = "note", columnDefinition = "TEXT")
    private String note;

    @Column(name = "created_by", nullable = false, length = 100)
    private String createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private OffsetDateTime createdAt = OffsetDateTime.now();
}
